package br.dev.ispchat.node.messaging;

import br.dev.ispchat.grpc.ClientEnvelope;
import br.dev.ispchat.grpc.DhtPeerServiceGrpc;
import br.dev.ispchat.grpc.FindValueRequest;
import br.dev.ispchat.grpc.FindValueResponse;
import br.dev.ispchat.grpc.MessageType;
import br.dev.ispchat.grpc.NodeInfo;
import br.dev.ispchat.grpc.NodeRoutingServiceGrpc;
import br.dev.ispchat.grpc.RoutePayloadRequest;
import br.dev.ispchat.grpc.RoutePayloadResponse;
import br.dev.ispchat.grpc.ServerEnvelope;
import br.dev.ispchat.grpc.UserSessionInfo;
import br.dev.ispchat.node.dht.DhtStoreService;
import br.dev.ispchat.node.dht.KademliaRoutingTable;
import br.dev.ispchat.node.grpc.GrpcChannelPool;
import br.dev.ispchat.node.security.CryptoService;
import io.grpc.ManagedChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Orquestrador de roteamento de mensagens e sinalizacao WebRTC.
 *
 * Fluxo de entrega para um destinatario (receiver_uuid):
 * 1. Entrega imediatamente para todas as streams locais ativas (Multi-Device no mesmo No).
 * 2. Consulta a DHT (local e pares Kademlia) para descobrir outros Nos onde o destinatario
 *    possui sessoes ativas e encaminha via mTLS (NodeRoutingService.routePayload).
 * 3. Se nenhum dispositivo online receber em tempo real e for uma mensagem de texto (MessageType.TEXT),
 *    armazena na fila offline (Redis List Store-and-Forward).
 */
@Service
public class MessageRouterService {

    private static final Logger log = LoggerFactory.getLogger(MessageRouterService.class);

    private final LocalSessionRegistry localSessionRegistry;
    private final OfflineMessageQueueService offlineQueueService;
    private final DhtStoreService dhtStoreService;
    private final KademliaRoutingTable routingTable;
    private final GrpcChannelPool channelPool;
    private final CryptoService cryptoService;

    public MessageRouterService(
            LocalSessionRegistry localSessionRegistry,
            OfflineMessageQueueService offlineQueueService,
            DhtStoreService dhtStoreService,
            KademliaRoutingTable routingTable,
            GrpcChannelPool channelPool,
            CryptoService cryptoService) {
        this.localSessionRegistry = localSessionRegistry;
        this.offlineQueueService = offlineQueueService;
        this.dhtStoreService = dhtStoreService;
        this.routingTable = routingTable;
        this.channelPool = channelPool;
        this.cryptoService = cryptoService;
    }

    public boolean routeClientEnvelope(ClientEnvelope envelope) {
        String receiverUuid = envelope.getReceiverUuid();
        ServerEnvelope serverEnvelope = ServerEnvelope.newBuilder()
                .setMessageId(envelope.getMessageId())
                .setSenderUuid(envelope.getSenderUuid())
                .setType(envelope.getType())
                .setEncryptedPayload(envelope.getEncryptedPayload())
                .setSenderPublicKey(envelope.getSenderPublicKey())
                .setSignature(envelope.getSignature())
                .setTimestamp(envelope.getTimestamp())
                .build();

        // 1. Entrega local (dispositivos conectados neste mesmo No)
        int localCount = localSessionRegistry.deliverToLocalStreams(receiverUuid, serverEnvelope);
        boolean deliveredAnywhere = localCount > 0;

        // 2. Descoberta na DHT e roteamento mTLS para outros Nos onde o usuario esteja online
        Set<String> targetNodes = resolveRemoteNodeAddresses(receiverUuid);
        if (!targetNodes.isEmpty()) {
            RoutePayloadRequest routeRequest = RoutePayloadRequest.newBuilder()
                    .setMessageId(envelope.getMessageId())
                    .setSenderUuid(envelope.getSenderUuid())
                    .setReceiverUuid(receiverUuid)
                    .setType(envelope.getType())
                    .setEncryptedPayload(envelope.getEncryptedPayload())
                    .setSenderPublicKey(envelope.getSenderPublicKey())
                    .setSignature(envelope.getSignature())
                    .setTimestamp(envelope.getTimestamp())
                    .build();

            for (String nodeAddress : targetNodes) {
                try {
                    ManagedChannel channel = channelPool.getChannel(nodeAddress);
                    NodeRoutingServiceGrpc.NodeRoutingServiceBlockingStub stub =
                            NodeRoutingServiceGrpc.newBlockingStub(channel).withDeadlineAfter(3, TimeUnit.SECONDS);
                    RoutePayloadResponse response = stub.routePayload(routeRequest);
                    if (response.getDelivered()) {
                        deliveredAnywhere = true;
                    }
                } catch (Exception e) {
                    log.debug("Falha ao rotear payload mTLS para no {}: {}", nodeAddress, e.getMessage());
                }
            }
        }

        // 3. Fallback Store-and-Forward: se nenhum aparelho recebeu em tempo real, enfileira no Redis
        if (!deliveredAnywhere && envelope.getType() == MessageType.TEXT) {
            return offlineQueueService.enqueue(receiverUuid, serverEnvelope);
        }

        return deliveredAnywhere;
    }

    private Set<String> resolveRemoteNodeAddresses(String targetUuid) {
        Set<String> addresses = new LinkedHashSet<>();

        for (UserSessionInfo localInfo : dhtStoreService.getSessions(targetUuid)) {
            if (!localInfo.getNodeAddress().isBlank()) {
                addresses.add(localInfo.getNodeAddress());
            }
        }

        List<NodeInfo> closestPeers = routingTable.findClosestNodes(targetUuid, 3);
        for (NodeInfo peer : closestPeers) {
            try {
                ManagedChannel channel = channelPool.getChannel(peer.getNodeAddress());
                DhtPeerServiceGrpc.DhtPeerServiceBlockingStub dhtStub =
                        DhtPeerServiceGrpc.newBlockingStub(channel).withDeadlineAfter(2, TimeUnit.SECONDS);
                FindValueResponse resp = dhtStub.findValue(
                        FindValueRequest.newBuilder().setTargetUuid(targetUuid).build()
                );
                for (UserSessionInfo remoteInfo : resp.getSessionsList()) {
                    if (cryptoService.verifyIdentityBinding(targetUuid, remoteInfo.getEd25519PublicKey().toByteArray())
                            && !remoteInfo.getNodeAddress().isBlank()) {
                        addresses.add(remoteInfo.getNodeAddress());
                    }
                }
            } catch (Exception e) {
                log.debug("Falha ao buscar sessoes no par DHT {}: {}", peer.getNodeAddress(), e.getMessage());
            }
        }

        return addresses;
    }
}
