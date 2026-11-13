package br.dev.ispchat.node.messaging;

import br.dev.ispchat.common.crypto.CanonicalTranscripts;
import br.dev.ispchat.grpc.ClientEnvelope;
import br.dev.ispchat.grpc.DeliveryStatus;
import br.dev.ispchat.grpc.DeviceRole;
import br.dev.ispchat.grpc.DhtPeerServiceGrpc;
import br.dev.ispchat.grpc.FindValueRequest;
import br.dev.ispchat.grpc.FindValueResponse;
import br.dev.ispchat.grpc.HomeNodeClaim;
import br.dev.ispchat.grpc.IdentityReplacementNotice;
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
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Orquestrador deterministico de roteamento de mensagens E2E, sincronizacao multi-dispositivo,
 * encaminhamento ao No Casa (HomeNodeClaim) com redirecionamento limitado (max_hops = 2) e sinalizacao WebRTC.
 */
@Service
public class MessageRouterService {

    private static final Logger log = LoggerFactory.getLogger(MessageRouterService.class);
    public static final int MAX_REDIRECT_HOPS = 2;

    public record RoutingOutcome(
            boolean deliveredOrQueued,
            DeliveryStatus status,
            List<String> deliveredDeviceIds) {
    }

    private record RecipientTopology(
            Map<String, UserSessionInfo> sessionsByDevice,
            Set<String> foreignSessionNodes,
            HomeNodeClaim homeNodeClaim,
            IdentityReplacementNotice replacementNotice) {
    }

    private final LocalSessionRegistry localSessionRegistry;
    private final OfflineMessageQueueService offlineQueueService;
    private final DhtStoreService dhtStoreService;
    private final KademliaRoutingTable routingTable;
    private final GrpcChannelPool channelPool;
    private final CryptoService cryptoService;
    private final String localPublicAddress;
    private final Counter messagesRoutedCounter;

    public MessageRouterService(
            LocalSessionRegistry localSessionRegistry,
            OfflineMessageQueueService offlineQueueService,
            DhtStoreService dhtStoreService,
            KademliaRoutingTable routingTable,
            GrpcChannelPool channelPool,
            CryptoService cryptoService) {
        this(
                localSessionRegistry,
                offlineQueueService,
                dhtStoreService,
                routingTable,
                channelPool,
                cryptoService,
                "",
                new SimpleMeterRegistry());
    }

    public MessageRouterService(
            LocalSessionRegistry localSessionRegistry,
            OfflineMessageQueueService offlineQueueService,
            DhtStoreService dhtStoreService,
            KademliaRoutingTable routingTable,
            GrpcChannelPool channelPool,
            CryptoService cryptoService,
            String localPublicAddress) {
        this(
                localSessionRegistry,
                offlineQueueService,
                dhtStoreService,
                routingTable,
                channelPool,
                cryptoService,
                localPublicAddress,
                new SimpleMeterRegistry());
    }

    @Autowired
    public MessageRouterService(
            LocalSessionRegistry localSessionRegistry,
            OfflineMessageQueueService offlineQueueService,
            DhtStoreService dhtStoreService,
            KademliaRoutingTable routingTable,
            GrpcChannelPool channelPool,
            CryptoService cryptoService,
            @Value("${ispchat.node.public-address:}") String localPublicAddress,
            @Autowired(required = false) MeterRegistry meterRegistry) {
        this.localSessionRegistry = localSessionRegistry;
        this.offlineQueueService = offlineQueueService;
        this.dhtStoreService = dhtStoreService;
        this.routingTable = routingTable;
        this.channelPool = channelPool;
        this.cryptoService = cryptoService;
        this.localPublicAddress = localPublicAddress != null ? localPublicAddress.trim() : "";
        MeterRegistry registry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
        this.messagesRoutedCounter = Counter.builder("ispchat_messages_routed_total")
                .description("Total de envelopes E2E roteados ou persistidos com sucesso")
                .register(registry);
    }

    public boolean routeClientEnvelope(ClientEnvelope envelope) {
        return routeClientEnvelope(envelope, null).deliveredOrQueued();
    }

    public RoutingOutcome routeClientEnvelope(
            ClientEnvelope envelope,
            StreamObserver<ServerEnvelope> senderStream) {

        String senderUuid = envelope.getSenderUuid().trim().toLowerCase(Locale.ROOT);
        String receiverUuid = envelope.getReceiverUuid().trim().toLowerCase(Locale.ROOT);
        String senderDeviceId = envelope.getSenderDeviceId().isBlank()
                ? senderUuid
                : envelope.getSenderDeviceId().trim().toLowerCase(Locale.ROOT);

        // Verifica aviso local de substituicao de identidade (CPF Re-Bind)
        IdentityReplacementNotice localReplacement = dhtStoreService.getReplacementNotice(receiverUuid).orElse(null);
        if (localReplacement != null) {
            emitIdentityReplaced(senderStream, senderUuid, receiverUuid, envelope.getMessageId(), localReplacement);
            return new RoutingOutcome(false, DeliveryStatus.REJECTED, List.of());
        }

        ServerEnvelope serverEnvelope = toServerEnvelope(envelope, senderDeviceId);

        // Sincronizacao entre dispositivos irmaos do remetente (exceto envios automatizados ERP_API em fila offline)
        if (!senderUuid.equals(receiverUuid)
                && envelope.getType() != MessageType.AUTH_HANDSHAKE
                && envelope.getType() != MessageType.DELIVERY_ACK) {
            synchronizeSenderSiblings(envelope, senderUuid, senderDeviceId, serverEnvelope);
        }

        // Passo 1: Entrega imediata para todas as streams locais ativas do destinatario
        Set<String> deliveredDeviceIds = new LinkedHashSet<>(
                localSessionRegistry.deliverToLocalUserDevices(receiverUuid, serverEnvelope));

        // Passo 2: Descoberta na DHT e roteamento mTLS para outros Nos onde o destinatario possui sessoes ativas
        RecipientTopology topology = resolveRecipientTopology(receiverUuid);
        if (topology.replacementNotice() != null) {
            emitIdentityReplaced(senderStream, senderUuid, receiverUuid, envelope.getMessageId(), topology.replacementNotice());
            return new RoutingOutcome(false, DeliveryStatus.REJECTED, List.of());
        }

        RoutePayloadRequest baseRouteRequest = toRoutePayloadRequest(envelope, senderDeviceId, 0);
        boolean queuedAtForeignSessionNode = false;

        for (String foreignNode : topology.foreignSessionNodes()) {
            try {
                ManagedChannel channel = channelPool.getChannel(foreignNode);
                NodeRoutingServiceGrpc.NodeRoutingServiceBlockingStub stub =
                        NodeRoutingServiceGrpc.newBlockingStub(channel).withDeadlineAfter(3, TimeUnit.SECONDS);
                RoutePayloadResponse response = stub.routePayload(baseRouteRequest);
                if (response.getDelivered()) {
                    if (response.getDeliveredDeviceIdsCount() > 0) {
                        for (String devId : response.getDeliveredDeviceIdsList()) {
                            deliveredDeviceIds.add(devId.trim().toLowerCase(Locale.ROOT));
                        }
                    } else if (response.getStatus() == DeliveryStatus.HOME_QUEUED) {
                        queuedAtForeignSessionNode = true;
                    } else {
                        deliveredDeviceIds.add(receiverUuid);
                    }
                    if (response.getStatus() == DeliveryStatus.HOME_QUEUED) {
                        queuedAtForeignSessionNode = true;
                    }
                }
            } catch (Exception e) {
                log.debug("Falha ao rotear payload mTLS para no {}: {}", foreignNode, e.getMessage());
            }
        }

        // Pacotes efemeros de sinalizacao WebRTC nao sao enfileirados em Redis offline
        if (envelope.getType() != MessageType.TEXT) {
            boolean delivered = !deliveredDeviceIds.isEmpty();
            if (delivered) {
                messagesRoutedCounter.increment();
            }
            return new RoutingOutcome(
                    delivered,
                    delivered ? DeliveryStatus.LIVE_DELIVERED : DeliveryStatus.DELIVERY_UNKNOWN,
                    List.copyOf(deliveredDeviceIds));
        }

        // Passo 3: Fast-path para contas nao verificadas single-device ja entregues ao vivo (poupa o Fallback Node)
        boolean isVerifiedRecipient = (topology.homeNodeClaim() != null && topology.homeNodeClaim().getSignedByPrincipal())
                || topology.sessionsByDevice().values().stream()
                .anyMatch(s -> s.hasVerificationCredential() && !s.getVerificationCredential().getCredentialId().isBlank());
        boolean isSingleDeviceUnverified = !isVerifiedRecipient && topology.sessionsByDevice().size() <= 1;

        if (isSingleDeviceUnverified && !deliveredDeviceIds.isEmpty()) {
            messagesRoutedCounter.increment();
            return new RoutingOutcome(true, DeliveryStatus.LIVE_DELIVERED, List.copyOf(deliveredDeviceIds));
        }

        Set<String> expectedDevices = new LinkedHashSet<>();
        Set<String> postAckErpDevices = new LinkedHashSet<>();
        for (UserSessionInfo s : topology.sessionsByDevice().values()) {
            String devId = s.getDeviceId().isBlank() ? receiverUuid : s.getDeviceId().trim().toLowerCase(Locale.ROOT);
            expectedDevices.add(devId);
            if (s.hasDelegationCert() && s.getDelegationCert().getRole() == DeviceRole.ERP_API) {
                postAckErpDevices.add(devId);
            }
        }
        if (expectedDevices.isEmpty()) {
            expectedDevices.add(receiverUuid);
        }

        Set<String> undeliveredDevices = new LinkedHashSet<>(expectedDevices);
        undeliveredDevices.removeAll(deliveredDeviceIds);

        Set<String> queueTargetDevices = new LinkedHashSet<>(undeliveredDevices);
        queueTargetDevices.addAll(postAckErpDevices);

        if (queueTargetDevices.isEmpty()) {
            messagesRoutedCounter.increment();
            return new RoutingOutcome(true, DeliveryStatus.LIVE_DELIVERED, List.copyOf(deliveredDeviceIds));
        }

        if (queuedAtForeignSessionNode && postAckErpDevices.isEmpty()) {
            emitServerQueuedAck(senderStream, senderUuid, receiverUuid, envelope.getMessageId());
            messagesRoutedCounter.increment();
            return new RoutingOutcome(true, DeliveryStatus.HOME_QUEUED, List.copyOf(deliveredDeviceIds));
        }

        String redirectTarget = dhtStoreService.getRedirectTombstone(receiverUuid).orElse("");
        String homeNodeAddress = !redirectTarget.isBlank()
                ? redirectTarget
                : (topology.homeNodeClaim() != null ? topology.homeNodeClaim().getHomeNodeAddress() : "");

        if (homeNodeAddress.isBlank() || isLocalNodeAddress(homeNodeAddress)) {
            boolean queuedLocally = offlineQueueService.enqueueForDevices(receiverUuid, serverEnvelope, queueTargetDevices);
            if (queuedLocally && !undeliveredDevices.isEmpty()) {
                emitServerQueuedAck(senderStream, senderUuid, receiverUuid, envelope.getMessageId());
                messagesRoutedCounter.increment();
                return new RoutingOutcome(true, DeliveryStatus.HOME_QUEUED, List.copyOf(deliveredDeviceIds));
            }
            if (queuedLocally) {
                messagesRoutedCounter.increment();
                return new RoutingOutcome(true, DeliveryStatus.LIVE_DELIVERED, List.copyOf(deliveredDeviceIds));
            }
            if (deliveredDeviceIds.isEmpty()) {
                emitDeliveryError(senderStream, senderUuid, receiverUuid, envelope.getMessageId());
            }
            return new RoutingOutcome(
                    !deliveredDeviceIds.isEmpty(),
                    !deliveredDeviceIds.isEmpty() ? DeliveryStatus.LIVE_DELIVERED : DeliveryStatus.REJECTED,
                    List.copyOf(deliveredDeviceIds));
        }

        // Encaminha ao Home Node remoto seguindo ate MAX_REDIRECT_HOPS (2) tombstones de portabilidade
        RoutePayloadResponse homeResponse = forwardToRemoteHomeNodeWithRedirects(baseRouteRequest, homeNodeAddress);
        if (homeResponse != null && homeResponse.getDelivered()) {
            for (String devId : homeResponse.getDeliveredDeviceIdsList()) {
                deliveredDeviceIds.add(devId.trim().toLowerCase(Locale.ROOT));
            }
            DeliveryStatus effectiveStatus = homeResponse.getStatus() == DeliveryStatus.DELIVERY_UNKNOWN
                    ? DeliveryStatus.HOME_QUEUED
                    : homeResponse.getStatus();
            if (effectiveStatus == DeliveryStatus.HOME_QUEUED) {
                emitServerQueuedAck(senderStream, senderUuid, receiverUuid, envelope.getMessageId());
            }
            messagesRoutedCounter.increment();
            return new RoutingOutcome(true, effectiveStatus, List.copyOf(deliveredDeviceIds));
        }

        if (deliveredDeviceIds.isEmpty()) {
            emitDeliveryError(senderStream, senderUuid, receiverUuid, envelope.getMessageId());
        }
        return new RoutingOutcome(
                !deliveredDeviceIds.isEmpty(),
                !deliveredDeviceIds.isEmpty() ? DeliveryStatus.LIVE_DELIVERED : DeliveryStatus.REJECTED,
                List.copyOf(deliveredDeviceIds));
    }

    /**
     * Se o usuario estiver conectado em roaming fora do seu No Casa, propaga tambem o DELIVERY_ACK ao No Casa.
     */
    public void forwardDeliveryAckToHomeNodeIfRemote(ClientEnvelope ackEnvelope, String deviceId) {
        String userUuid = ackEnvelope.getSenderUuid().trim().toLowerCase(Locale.ROOT);
        HomeNodeClaim claim = dhtStoreService.getHomeNodeClaim(userUuid).orElse(null);
        if (claim == null || claim.getHomeNodeAddress().isBlank() || isLocalNodeAddress(claim.getHomeNodeAddress())) {
            return;
        }
        RoutePayloadRequest ackRoute = toRoutePayloadRequest(ackEnvelope, deviceId, 1);
        try {
            ManagedChannel channel = channelPool.getChannel(claim.getHomeNodeAddress());
            NodeRoutingServiceGrpc.NodeRoutingServiceBlockingStub stub =
                    NodeRoutingServiceGrpc.newBlockingStub(channel).withDeadlineAfter(3, TimeUnit.SECONDS);
            stub.routePayload(ackRoute);
        } catch (Exception e) {
            log.debug("Falha ao encaminhar DELIVERY_ACK de roaming para Home Node {}: {}",
                    claim.getHomeNodeAddress(), e.getMessage());
        }
    }

    private void synchronizeSenderSiblings(
            ClientEnvelope envelope,
            String senderUuid,
            String senderDeviceId,
            ServerEnvelope serverEnvelope) {
        List<String> localSiblings = localSessionRegistry.deliverToSiblingDevices(senderUuid, senderDeviceId, serverEnvelope);
        if (envelope.getType() != MessageType.TEXT) {
            return;
        }
        boolean isErpSender = envelope.hasSenderDelegationCert()
                && envelope.getSenderDelegationCert().getRole() == DeviceRole.ERP_API;
        if (isErpSender) {
            // Regra de engenharia: disparos automatizados do ERP Bridge nao enchem filas offline de Desktops irmaos
            return;
        }

        List<UserSessionInfo> senderSessions = dhtStoreService.getSessions(senderUuid);
        if (senderSessions.size() <= 1) {
            return;
        }
        Set<String> offlineSiblings = new LinkedHashSet<>();
        for (UserSessionInfo s : senderSessions) {
            String devId = s.getDeviceId().isBlank() ? senderUuid : s.getDeviceId().trim().toLowerCase(Locale.ROOT);
            if (devId.equals(senderDeviceId) || localSiblings.contains(devId)) {
                continue;
            }
            if (s.hasDelegationCert() && s.getDelegationCert().getRole() == DeviceRole.ERP_API) {
                continue;
            }
            offlineSiblings.add(devId);
        }
        if (!offlineSiblings.isEmpty()) {
            HomeNodeClaim senderHome = dhtStoreService.getHomeNodeClaim(senderUuid).orElse(null);
            if (senderHome == null || senderHome.getHomeNodeAddress().isBlank()
                    || isLocalNodeAddress(senderHome.getHomeNodeAddress())) {
                offlineQueueService.enqueueForDevices(senderUuid, serverEnvelope, offlineSiblings);
            }
        }
    }

    private RoutePayloadResponse forwardToRemoteHomeNodeWithRedirects(
            RoutePayloadRequest baseRequest,
            String initialHomeAddress) {
        String currentTarget = initialHomeAddress;
        for (int hop = 1; hop <= MAX_REDIRECT_HOPS; hop++) {
            if (currentTarget == null || currentTarget.isBlank()) {
                return null;
            }
            RoutePayloadRequest hopRequest = baseRequest.toBuilder()
                    .setHopCount(hop)
                    .build();
            try {
                ManagedChannel channel = channelPool.getChannel(currentTarget);
                NodeRoutingServiceGrpc.NodeRoutingServiceBlockingStub stub =
                        NodeRoutingServiceGrpc.newBlockingStub(channel).withDeadlineAfter(3, TimeUnit.SECONDS);
                RoutePayloadResponse resp = stub.routePayload(hopRequest);
                if (resp.getStatus() == DeliveryStatus.REDIRECT_REQUIRED
                        && !resp.getRedirectHomeNode().isBlank()) {
                    currentTarget = resp.getRedirectHomeNode().trim();
                    continue;
                }
                return resp;
            } catch (Exception e) {
                log.debug("Falha ao encaminhar para Home Node {} (hop={}): {}", currentTarget, hop, e.getMessage());
                return null;
            }
        }
        log.warn("Cadeia de redirecionamento de Home Node excedeu max_hops={} para destinatario {}",
                MAX_REDIRECT_HOPS, baseRequest.getReceiverUuid());
        return null;
    }

    private RecipientTopology resolveRecipientTopology(String targetUuid) {
        Map<String, UserSessionInfo> sessionsByDevice = new LinkedHashMap<>();
        Set<String> foreignNodes = new LinkedHashSet<>();

        for (UserSessionInfo localInfo : dhtStoreService.getSessions(targetUuid)) {
            String key = localInfo.getDeviceId().isBlank()
                    ? targetUuid + "@" + localInfo.getNodeAddress()
                    : localInfo.getDeviceId().trim().toLowerCase(Locale.ROOT);
            sessionsByDevice.put(key, localInfo);
            if (!localInfo.getNodeAddress().isBlank() && !isLocalNodeAddress(localInfo.getNodeAddress())) {
                foreignNodes.add(localInfo.getNodeAddress());
            }
        }

        HomeNodeClaim bestHomeClaim = dhtStoreService.getHomeNodeClaim(targetUuid).orElse(null);
        IdentityReplacementNotice replacementNotice = dhtStoreService.getReplacementNotice(targetUuid).orElse(null);

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
                    if (cryptoService.verifyRemoteSessionInfo(targetUuid, remoteInfo)
                            && !remoteInfo.getNodeAddress().isBlank()) {
                        String key = remoteInfo.getDeviceId().isBlank()
                                ? targetUuid + "@" + remoteInfo.getNodeAddress()
                                : remoteInfo.getDeviceId().trim().toLowerCase(Locale.ROOT);
                        sessionsByDevice.merge(key, remoteInfo,
                                (a, b) -> b.getTimestamp() >= a.getTimestamp() ? b : a);
                        if (!isLocalNodeAddress(remoteInfo.getNodeAddress())) {
                            foreignNodes.add(remoteInfo.getNodeAddress());
                        }
                    }
                }
                if (resp.hasHomeNodeClaim() && !resp.getHomeNodeClaim().getUserUuid().isBlank()) {
                    HomeNodeClaim candidate = resp.getHomeNodeClaim();
                    byte[] rootPubHint = extractRootPub(sessionsByDevice);
                    if (candidate.getUserUuid().trim().equalsIgnoreCase(targetUuid)
                            && cryptoService.verifyHomeNodeClaim(candidate, null, rootPubHint)) {
                        if (bestHomeClaim == null || candidate.getRoutingEpoch() > bestHomeClaim.getRoutingEpoch()) {
                            bestHomeClaim = candidate;
                            dhtStoreService.storeHomeNodeClaim(candidate, null, rootPubHint);
                        }
                    }
                }
                if (replacementNotice == null
                        && resp.hasReplacementNotice()
                        && !resp.getReplacementNotice().getRevocationId().isBlank()) {
                    IdentityReplacementNotice candidateNotice = resp.getReplacementNotice();
                    if (!cryptoService.hasTrustedPrincipalKeys()
                            || cryptoService.verifyIdentityReplacementNotice(candidateNotice)) {
                        replacementNotice = candidateNotice;
                    }
                }
            } catch (Exception e) {
                log.debug("Falha ao buscar sessoes no par DHT {}: {}", peer.getNodeAddress(), e.getMessage());
            }
        }

        return new RecipientTopology(sessionsByDevice, foreignNodes, bestHomeClaim, replacementNotice);
    }

    private static byte[] extractRootPub(Map<String, UserSessionInfo> sessions) {
        for (UserSessionInfo s : sessions.values()) {
            if (!s.getRootEd25519Pub().isEmpty()) {
                return s.getRootEd25519Pub().toByteArray();
            }
            if (s.getDeviceId().isBlank() && !s.getEd25519PublicKey().isEmpty()) {
                return s.getEd25519PublicKey().toByteArray();
            }
        }
        return null;
    }

    private boolean isLocalNodeAddress(String candidateAddress) {
        if (localPublicAddress.isBlank() || candidateAddress == null || candidateAddress.isBlank()) {
            return false;
        }
        String cand = candidateAddress.trim();
        if (localPublicAddress.equalsIgnoreCase(cand)) {
            return true;
        }
        if (!localPublicAddress.contains(":") && (localPublicAddress + ":443").equalsIgnoreCase(cand)) {
            return true;
        }
        if (!cand.contains(":") && localPublicAddress.equalsIgnoreCase(cand + ":443")) {
            return true;
        }
        return false;
    }

    private static ServerEnvelope toServerEnvelope(ClientEnvelope envelope, String effectiveDeviceId) {
        ServerEnvelope.Builder builder = ServerEnvelope.newBuilder()
                .setMessageId(envelope.getMessageId())
                .setSenderUuid(envelope.getSenderUuid())
                .setReceiverUuid(envelope.getReceiverUuid())
                .setType(envelope.getType())
                .setEncryptedPayload(envelope.getEncryptedPayload())
                .setSenderPublicKey(envelope.getSenderPublicKey())
                .setSignature(envelope.getSignature())
                .setTimestamp(envelope.getTimestamp())
                .setSenderDeviceId(effectiveDeviceId)
                .setRefMessageId(envelope.getRefMessageId());
        if (envelope.hasSenderDelegationCert()) {
            builder.setSenderDelegationCert(envelope.getSenderDelegationCert());
        }
        if (envelope.hasSenderVerificationCredential()) {
            builder.setSenderVerificationCredential(envelope.getSenderVerificationCredential());
        }
        return builder.build();
    }

    private static RoutePayloadRequest toRoutePayloadRequest(
            ClientEnvelope envelope,
            String effectiveDeviceId,
            int hopCount) {
        RoutePayloadRequest.Builder builder = RoutePayloadRequest.newBuilder()
                .setMessageId(envelope.getMessageId())
                .setSenderUuid(envelope.getSenderUuid())
                .setReceiverUuid(envelope.getReceiverUuid())
                .setType(envelope.getType())
                .setEncryptedPayload(envelope.getEncryptedPayload())
                .setSenderPublicKey(envelope.getSenderPublicKey())
                .setSignature(envelope.getSignature())
                .setTimestamp(envelope.getTimestamp())
                .setSenderDeviceId(effectiveDeviceId)
                .setRefMessageId(envelope.getRefMessageId())
                .setHopCount(hopCount);
        if (envelope.hasSenderDelegationCert()) {
            builder.setSenderDelegationCert(envelope.getSenderDelegationCert());
        }
        if (envelope.hasSenderVerificationCredential()) {
            builder.setSenderVerificationCredential(envelope.getSenderVerificationCredential());
        }
        return builder.build();
    }

    private static void emitServerQueuedAck(
            StreamObserver<ServerEnvelope> senderStream,
            String senderUuid,
            String receiverUuid,
            String originalMessageId) {
        if (senderStream == null) {
            return;
        }
        ServerEnvelope ack = ServerEnvelope.newBuilder()
                .setMessageId(CanonicalTranscripts.generateTemporalUuidV7().toString())
                .setSenderUuid(receiverUuid)
                .setReceiverUuid(senderUuid)
                .setType(MessageType.SERVER_QUEUED_ACK)
                .setRefMessageId(originalMessageId)
                .setTimestamp(System.currentTimeMillis())
                .build();
        sendSafely(senderStream, ack);
    }

    private static void emitDeliveryError(
            StreamObserver<ServerEnvelope> senderStream,
            String senderUuid,
            String receiverUuid,
            String originalMessageId) {
        if (senderStream == null) {
            return;
        }
        ServerEnvelope err = ServerEnvelope.newBuilder()
                .setMessageId(CanonicalTranscripts.generateTemporalUuidV7().toString())
                .setSenderUuid(receiverUuid)
                .setReceiverUuid(senderUuid)
                .setType(MessageType.DELIVERY_ERROR)
                .setRefMessageId(originalMessageId)
                .setTimestamp(System.currentTimeMillis())
                .build();
        sendSafely(senderStream, err);
    }

    private static void emitIdentityReplaced(
            StreamObserver<ServerEnvelope> senderStream,
            String senderUuid,
            String oldReceiverUuid,
            String originalMessageId,
            IdentityReplacementNotice notice) {
        if (senderStream == null) {
            return;
        }
        ServerEnvelope replaced = ServerEnvelope.newBuilder()
                .setMessageId(CanonicalTranscripts.generateTemporalUuidV7().toString())
                .setSenderUuid(oldReceiverUuid)
                .setReceiverUuid(senderUuid)
                .setType(MessageType.IDENTITY_REPLACED)
                .setRefMessageId(originalMessageId)
                .setReplacementNotice(notice)
                .setTimestamp(System.currentTimeMillis())
                .build();
        sendSafely(senderStream, replaced);
    }

    private static void sendSafely(StreamObserver<ServerEnvelope> observer, ServerEnvelope frame) {
        try {
            synchronized (observer) {
                observer.onNext(frame);
            }
        } catch (Exception ignored) {
        }
    }
}
