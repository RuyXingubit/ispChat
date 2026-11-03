package br.dev.ispchat.node.dht;

import br.dev.ispchat.grpc.DhtClientServiceGrpc;
import br.dev.ispchat.grpc.DhtPeerServiceGrpc;
import br.dev.ispchat.grpc.FindUserSessionsRequest;
import br.dev.ispchat.grpc.FindUserSessionsResponse;
import br.dev.ispchat.grpc.FindValueRequest;
import br.dev.ispchat.grpc.FindValueResponse;
import br.dev.ispchat.grpc.NodeInfo;
import br.dev.ispchat.grpc.RegisterSessionRequest;
import br.dev.ispchat.grpc.RegisterSessionResponse;
import br.dev.ispchat.grpc.StoreRequest;
import br.dev.ispchat.grpc.UserSessionInfo;
import br.dev.ispchat.node.grpc.GrpcChannelPool;
import br.dev.ispchat.node.security.CryptoService;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Servico gRPC de Borda (App Flutter <-> No Local) para registro e descoberta de sessoes na DHT.
 */
@Service
public class DhtClientGrpcService extends DhtClientServiceGrpc.DhtClientServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(DhtClientGrpcService.class);
    public static final int REPLICATION_FACTOR_ALPHA = 3;

    private final DhtStoreService dhtStoreService;
    private final KademliaRoutingTable routingTable;
    private final GrpcChannelPool channelPool;
    private final CryptoService cryptoService;

    public DhtClientGrpcService(
            DhtStoreService dhtStoreService,
            KademliaRoutingTable routingTable,
            GrpcChannelPool channelPool,
            CryptoService cryptoService) {
        this.dhtStoreService = dhtStoreService;
        this.routingTable = routingTable;
        this.channelPool = channelPool;
        this.cryptoService = cryptoService;
    }

    @Override
    public void registerSession(RegisterSessionRequest request, StreamObserver<RegisterSessionResponse> responseObserver) {
        boolean validAndStored = dhtStoreService.storeVerifiedSession(
                request.getUserUuid(),
                request.getNodeAddress(),
                request.getPublicKey().toByteArray(),
                request.getX25519PublicKey().toByteArray(),
                request.getSignature().toByteArray(),
                request.getTimestamp()
        );

        if (!validAndStored) {
            responseObserver.onNext(RegisterSessionResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorMessage("Assinatura digital Ed25519 invalida, timestamp expirado ou UUIDv7 incompativel com a PublicKey")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        // Propaga o registro autenticado via mTLS para os K nos mais proximos na DHT Kademlia
        replicateToClosestPeers(request);

        responseObserver.onNext(RegisterSessionResponse.newBuilder()
                .setSuccess(true)
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void findUserSessions(FindUserSessionsRequest request, StreamObserver<FindUserSessionsResponse> responseObserver) {
        String targetUuid = request.getTargetUuid();
        Map<String, UserSessionInfo> consolidatedByNode = new LinkedHashMap<>();

        // 1. Consulta armazenamento local
        for (UserSessionInfo session : dhtStoreService.getSessions(targetUuid)) {
            consolidatedByNode.put(session.getNodeAddress(), session);
        }

        // 2. Consulta os nos mais proximos na malha Kademlia via mTLS
        List<NodeInfo> closestPeers = routingTable.findClosestNodes(targetUuid, REPLICATION_FACTOR_ALPHA);
        for (NodeInfo peer : closestPeers) {
            try {
                ManagedChannel channel = channelPool.getChannel(peer.getNodeAddress());
                DhtPeerServiceGrpc.DhtPeerServiceBlockingStub stub = DhtPeerServiceGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(3, TimeUnit.SECONDS);

                FindValueResponse peerResponse = stub.findValue(
                        FindValueRequest.newBuilder().setTargetUuid(targetUuid).build()
                );
                for (UserSessionInfo remoteSession : peerResponse.getSessionsList()) {
                    // Protecao Anti-MITM: verifica matematicamente que a chave publica retornada gera o targetUuid
                    if (cryptoService.verifyIdentityBinding(targetUuid, remoteSession.getEd25519PublicKey().toByteArray())
                            && !remoteSession.getNodeAddress().isBlank()) {
                        consolidatedByNode.putIfAbsent(remoteSession.getNodeAddress(), remoteSession);
                    } else {
                        log.warn("Descartada sessao remota suspeita do par {} para UUID {}: chave publica nao corresponde ao UUIDv7",
                                peer.getNodeId(), targetUuid);
                    }
                }
            } catch (Exception e) {
                log.debug("Falha ao consultar par DHT {} ({}): {}", peer.getNodeId(), peer.getNodeAddress(), e.getMessage());
            }
        }

        FindUserSessionsResponse.Builder responseBuilder = FindUserSessionsResponse.newBuilder();
        for (UserSessionInfo session : consolidatedByNode.values()) {
            responseBuilder.addNodeAddresses(session.getNodeAddress());
            responseBuilder.addSessions(session);
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    private void replicateToClosestPeers(RegisterSessionRequest request) {
        List<NodeInfo> closestPeers = routingTable.findClosestNodes(request.getUserUuid(), REPLICATION_FACTOR_ALPHA);
        if (closestPeers.isEmpty()) {
            return;
        }

        StoreRequest storeRequest = StoreRequest.newBuilder()
                .setUserUuid(request.getUserUuid())
                .setNodeAddress(request.getNodeAddress())
                .setPublicKey(request.getPublicKey())
                .setX25519PublicKey(request.getX25519PublicKey())
                .setSignature(request.getSignature())
                .setTimestamp(request.getTimestamp())
                .build();

        for (NodeInfo peer : closestPeers) {
            try {
                ManagedChannel channel = channelPool.getChannel(peer.getNodeAddress());
                DhtPeerServiceGrpc.DhtPeerServiceBlockingStub stub = DhtPeerServiceGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(3, TimeUnit.SECONDS);
                stub.store(storeRequest);
            } catch (Exception e) {
                log.warn("Falha ao replicar Store DHT para no par {} ({}): {}",
                        peer.getNodeId(), peer.getNodeAddress(), e.getMessage());
            }
        }
    }
}
