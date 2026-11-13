package br.dev.ispchat.node.messaging;

import br.dev.ispchat.grpc.DeliveryStatus;
import br.dev.ispchat.grpc.DeviceDelegationCert;
import br.dev.ispchat.grpc.DeviceRole;
import br.dev.ispchat.grpc.HomeNodeClaim;
import br.dev.ispchat.grpc.MessageType;
import br.dev.ispchat.grpc.NodeRoutingServiceGrpc;
import br.dev.ispchat.grpc.RoutePayloadRequest;
import br.dev.ispchat.grpc.RoutePayloadResponse;
import br.dev.ispchat.grpc.ServerEnvelope;
import br.dev.ispchat.grpc.UserSessionInfo;
import br.dev.ispchat.grpc.VerificationCredential;
import br.dev.ispchat.node.dht.DhtStoreService;
import br.dev.ispchat.node.security.CryptoService;
import br.dev.ispchat.node.security.TierRateLimiterService;
import br.dev.ispchat.node.security.TierRateLimiterService.AccountTier;
import br.dev.ispchat.node.security.TierRateLimiterService.ValidatedSessionIdentity;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Servico gRPC de Federacao (No <-> No via mTLS obrigatorio) para recepcao de payloads roteados
 * e persistencia no No Casa (Home Node) com protecao contra loops de redirecionamento (max_hops = 2).
 */
@Service
public class NodeRoutingGrpcService extends NodeRoutingServiceGrpc.NodeRoutingServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(NodeRoutingGrpcService.class);
    public static final int MAX_REDIRECT_HOPS = 2;

    private final CryptoService cryptoService;
    private final LocalSessionRegistry localSessionRegistry;
    private final OfflineMessageQueueService offlineQueueService;
    private final DhtStoreService dhtStoreService;
    private final TierRateLimiterService tierRateLimiterService;
    private final String localPublicAddress;

    public NodeRoutingGrpcService(
            CryptoService cryptoService,
            LocalSessionRegistry localSessionRegistry,
            OfflineMessageQueueService offlineQueueService) {
        this(
                cryptoService,
                localSessionRegistry,
                offlineQueueService,
                new DhtStoreService(cryptoService),
                new TierRateLimiterService(cryptoService),
                "");
    }

    @Autowired
    public NodeRoutingGrpcService(
            CryptoService cryptoService,
            LocalSessionRegistry localSessionRegistry,
            OfflineMessageQueueService offlineQueueService,
            @Autowired(required = false) DhtStoreService dhtStoreService,
            @Autowired(required = false) TierRateLimiterService tierRateLimiterService,
            @Value("${ispchat.node.public-address:}") String localPublicAddress) {
        this.cryptoService = cryptoService;
        this.localSessionRegistry = localSessionRegistry;
        this.offlineQueueService = offlineQueueService;
        this.tierRateLimiterService = tierRateLimiterService != null
                ? tierRateLimiterService
                : new TierRateLimiterService(cryptoService);
        this.dhtStoreService = dhtStoreService != null
                ? dhtStoreService
                : new DhtStoreService(cryptoService, null, this.tierRateLimiterService);
        this.localPublicAddress = localPublicAddress != null ? localPublicAddress.trim() : "";
    }

    @Override
    public void routePayload(RoutePayloadRequest request, StreamObserver<RoutePayloadResponse> responseObserver) {
        if (request.getHopCount() > MAX_REDIRECT_HOPS) {
            responseObserver.onError(Status.ABORTED
                    .withDescription("Limite maximo de redirecionamentos de portabilidade excedido (max_hops = 2)")
                    .asRuntimeException());
            return;
        }

        DeviceDelegationCert delCert = request.hasSenderDelegationCert() ? request.getSenderDelegationCert() : null;
        VerificationCredential verCred = request.hasSenderVerificationCredential()
                ? request.getSenderVerificationCredential()
                : null;

        ValidatedSessionIdentity senderIdentity;
        try {
            senderIdentity = tierRateLimiterService.validateSessionCredentials(
                    request.getSenderUuid(),
                    request.getSenderDeviceId(),
                    request.getSenderPublicKey().toByteArray(),
                    delCert,
                    verCred);
        } catch (StatusRuntimeException e) {
            log.warn("Rejeitado RoutePayload de {} para {}: {}",
                    request.getSenderUuid(), request.getReceiverUuid(), e.getStatus().getDescription());
            responseObserver.onNext(RoutePayloadResponse.newBuilder()
                    .setDelivered(false)
                    .setStatus(DeliveryStatus.REJECTED)
                    .build());
            responseObserver.onCompleted();
            return;
        }

        boolean signatureValid = verifyRoutedSignature(request, senderIdentity);
        if (!signatureValid) {
            log.warn("Rejeitado RoutePayload de {} para {}: assinatura Ed25519 invalida",
                    request.getSenderUuid(), request.getReceiverUuid());
            responseObserver.onNext(RoutePayloadResponse.newBuilder()
                    .setDelivered(false)
                    .setStatus(DeliveryStatus.REJECTED)
                    .build());
            responseObserver.onCompleted();
            return;
        }

        String normalizedReceiver = request.getReceiverUuid().trim().toLowerCase(Locale.ROOT);

        // Processa DELIVERY_ACK encaminhado por dispositivo em roaming para seu No Casa
        if (request.getType() == MessageType.DELIVERY_ACK
                && senderIdentity.userUuid().equals(normalizedReceiver)
                && !request.getRefMessageId().isBlank()) {
            offlineQueueService.acknowledgeDelivery(
                    senderIdentity.userUuid(),
                    senderIdentity.deviceId(),
                    request.getRefMessageId());
            responseObserver.onNext(RoutePayloadResponse.newBuilder()
                    .setDelivered(true)
                    .setStatus(DeliveryStatus.LIVE_DELIVERED)
                    .build());
            responseObserver.onCompleted();
            return;
        }

        ServerEnvelope.Builder envBuilder = ServerEnvelope.newBuilder()
                .setMessageId(request.getMessageId())
                .setSenderUuid(request.getSenderUuid())
                .setReceiverUuid(request.getReceiverUuid())
                .setType(request.getType())
                .setEncryptedPayload(request.getEncryptedPayload())
                .setSenderPublicKey(request.getSenderPublicKey())
                .setSignature(request.getSignature())
                .setTimestamp(request.getTimestamp())
                .setSenderDeviceId(senderIdentity.deviceId())
                .setRefMessageId(request.getRefMessageId());
        if (delCert != null && !delCert.getCertId().isBlank()) {
            envBuilder.setSenderDelegationCert(delCert);
        }
        if (verCred != null && !verCred.getCredentialId().isBlank()) {
            envBuilder.setSenderVerificationCredential(verCred);
        }
        ServerEnvelope serverEnvelope = envBuilder.build();

        List<String> localDeliveredDevices = localSessionRegistry.deliverToLocalUserDevices(
                normalizedReceiver, serverEnvelope);

        // Verifica tombstone de redirecionamento de portabilidade (TTL 30 dias)
        Optional<String> redirectOpt = dhtStoreService.getRedirectTombstone(normalizedReceiver);
        if (redirectOpt.isPresent() && !isLocalNodeAddress(redirectOpt.get())) {
            if (request.getHopCount() >= MAX_REDIRECT_HOPS) {
                responseObserver.onError(Status.ABORTED
                        .withDescription("Redirecionamento excederia max_hops = 2")
                        .asRuntimeException());
                return;
            }
            responseObserver.onNext(RoutePayloadResponse.newBuilder()
                    .setDelivered(!localDeliveredDevices.isEmpty())
                    .setStatus(DeliveryStatus.REDIRECT_REQUIRED)
                    .setRedirectHomeNode(redirectOpt.get())
                    .addAllDeliveredDeviceIds(localDeliveredDevices)
                    .build());
            responseObserver.onCompleted();
            return;
        }

        if (request.getType() == MessageType.TEXT) {
            Set<String> expectedDevices = new LinkedHashSet<>();
            Set<String> postAckErpDevices = new LinkedHashSet<>();
            for (UserSessionInfo s : dhtStoreService.getSessions(normalizedReceiver)) {
                String devId = s.getDeviceId().isBlank()
                        ? normalizedReceiver
                        : s.getDeviceId().trim().toLowerCase(Locale.ROOT);
                expectedDevices.add(devId);
                if (s.hasDelegationCert() && s.getDelegationCert().getRole() == DeviceRole.ERP_API) {
                    postAckErpDevices.add(devId);
                }
            }
            if (expectedDevices.isEmpty()) {
                expectedDevices.add(normalizedReceiver);
            }
            Set<String> undeliveredDevices = new LinkedHashSet<>(expectedDevices);
            undeliveredDevices.removeAll(localDeliveredDevices);

            Set<String> queueTargetDevices = new LinkedHashSet<>(undeliveredDevices);
            queueTargetDevices.addAll(postAckErpDevices);

            boolean shouldPersistAtThisNode = !queueTargetDevices.isEmpty()
                    && (request.getHopCount() >= 1 || localDeliveredDevices.isEmpty() || isDeclaredHomeNode(normalizedReceiver) || !postAckErpDevices.isEmpty());

            if (shouldPersistAtThisNode) {
                boolean queued = offlineQueueService.enqueueForDevices(
                        normalizedReceiver, serverEnvelope, queueTargetDevices);
                boolean ok = queued || !localDeliveredDevices.isEmpty();
                DeliveryStatus status = (queued && !undeliveredDevices.isEmpty())
                        ? DeliveryStatus.HOME_QUEUED
                        : (!localDeliveredDevices.isEmpty() ? DeliveryStatus.LIVE_DELIVERED : DeliveryStatus.REJECTED);
                responseObserver.onNext(RoutePayloadResponse.newBuilder()
                        .setDelivered(ok)
                        .setStatus(status)
                        .addAllDeliveredDeviceIds(localDeliveredDevices)
                        .build());
                responseObserver.onCompleted();
                return;
            }
        }

        boolean delivered = !localDeliveredDevices.isEmpty();
        responseObserver.onNext(RoutePayloadResponse.newBuilder()
                .setDelivered(delivered)
                .setStatus(delivered ? DeliveryStatus.LIVE_DELIVERED : DeliveryStatus.DELIVERY_UNKNOWN)
                .addAllDeliveredDeviceIds(localDeliveredDevices)
                .build());
        responseObserver.onCompleted();
    }

    private boolean verifyRoutedSignature(RoutePayloadRequest request, ValidatedSessionIdentity identity) {
        boolean v2Valid = cryptoService.verifyMessageEnvelopeV2(
                request.getMessageId(),
                identity.userUuid(),
                identity.deviceId(),
                request.getReceiverUuid(),
                request.getType().getNumber(),
                request.getRefMessageId(),
                request.getEncryptedPayload().toByteArray(),
                request.getTimestamp(),
                identity.deviceEd25519Pub(),
                request.getSignature().toByteArray());
        if (v2Valid) {
            return true;
        }

        boolean hasV2OnlyFields = !request.getRefMessageId().isBlank()
                || (request.hasSenderDelegationCert() && !request.getSenderDelegationCert().getCertId().isBlank())
                || (request.hasSenderVerificationCredential()
                && !request.getSenderVerificationCredential().getCredentialId().isBlank())
                || (!request.getSenderDeviceId().isBlank()
                && !request.getSenderDeviceId().trim().equalsIgnoreCase(identity.userUuid()));

        if (identity.tier() == AccountTier.UNVERIFIED && !hasV2OnlyFields) {
            return cryptoService.verifyMessageEnvelope(
                    request.getMessageId(),
                    identity.userUuid(),
                    request.getReceiverUuid(),
                    request.getType().getNumber(),
                    request.getTimestamp(),
                    request.getEncryptedPayload().toByteArray(),
                    identity.deviceEd25519Pub(),
                    request.getSignature().toByteArray());
        }
        return false;
    }

    private boolean isDeclaredHomeNode(String normalizedReceiverUuid) {
        Optional<HomeNodeClaim> claimOpt = dhtStoreService.getHomeNodeClaim(normalizedReceiverUuid);
        return claimOpt.isPresent() && isLocalNodeAddress(claimOpt.get().getHomeNodeAddress());
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
}
