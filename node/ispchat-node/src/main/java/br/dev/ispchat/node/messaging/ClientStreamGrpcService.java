package br.dev.ispchat.node.messaging;

import br.dev.ispchat.grpc.ClientEnvelope;
import br.dev.ispchat.grpc.ClientStreamServiceGrpc;
import br.dev.ispchat.grpc.DeviceDelegationCert;
import br.dev.ispchat.grpc.DeviceRole;
import br.dev.ispchat.grpc.MessageType;
import br.dev.ispchat.grpc.ServerEnvelope;
import br.dev.ispchat.grpc.VerificationCredential;
import br.dev.ispchat.node.security.CryptoService;
import br.dev.ispchat.node.security.MtlsFederationInterceptor;
import br.dev.ispchat.node.security.TierRateLimiterService;
import br.dev.ispchat.node.security.TierRateLimiterService.AccountTier;
import br.dev.ispchat.node.security.TierRateLimiterService.ValidatedSessionIdentity;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.SocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Servico gRPC de Stream Bidirecional (App Flutter / Desktop / ERP Bridge <-> No Local).
 *
 * Regras Dual-Tier V2:
 * 1. O primeiro pacote enviado deve ser MessageType.AUTH_HANDSHAKE auto-enderecado.
 * 2. Valida prefixo de rede (/32 IPv4 e /64 IPv6), VerificationCredential, DeviceDelegationCert,
 *    revogacoes e limite de streams concorrentes (1 Unverified, 5 CPF, 10 CNPJ).
 * 3. Vincula (userUuid, deviceId, tier, chave Ed25519 e certificados) ao contexto da stream para que
 *    quadros subsequentes (DELIVERY_ACK, ICE_CANDIDATE, TEXT) possam omitir certificados redundantes.
 * 4. Transmite mensagens pendentes sem exclusao destrutiva; a coleta ocorre somente via DELIVERY_ACK.
 */
@Service
public class ClientStreamGrpcService extends ClientStreamServiceGrpc.ClientStreamServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(ClientStreamGrpcService.class);

    private record BoundStreamSession(
            String userUuid,
            String deviceId,
            AccountTier tier,
            DeviceRole role,
            byte[] deviceEd25519Pub,
            DeviceDelegationCert delegationCert,
            VerificationCredential verificationCredential) {
    }

    private final CryptoService cryptoService;
    private final LocalSessionRegistry localSessionRegistry;
    private final OfflineMessageQueueService offlineQueueService;
    private final MessageRouterService messageRouterService;
    private final TierRateLimiterService tierRateLimiterService;

    public ClientStreamGrpcService(
            CryptoService cryptoService,
            LocalSessionRegistry localSessionRegistry,
            OfflineMessageQueueService offlineQueueService,
            MessageRouterService messageRouterService) {
        this(
                cryptoService,
                localSessionRegistry,
                offlineQueueService,
                messageRouterService,
                new TierRateLimiterService(cryptoService));
    }

    @Autowired
    public ClientStreamGrpcService(
            CryptoService cryptoService,
            LocalSessionRegistry localSessionRegistry,
            OfflineMessageQueueService offlineQueueService,
            MessageRouterService messageRouterService,
            @Autowired(required = false) TierRateLimiterService tierRateLimiterService) {
        this.cryptoService = cryptoService;
        this.localSessionRegistry = localSessionRegistry;
        this.offlineQueueService = offlineQueueService;
        this.messageRouterService = messageRouterService;
        this.tierRateLimiterService = tierRateLimiterService != null
                ? tierRateLimiterService
                : new TierRateLimiterService(cryptoService);
    }

    @Override
    public StreamObserver<ClientEnvelope> connect(StreamObserver<ServerEnvelope> responseObserver) {
        AtomicReference<BoundStreamSession> boundSessionRef = new AtomicReference<>(null);
        SocketAddress remoteAddress = MtlsFederationInterceptor.REMOTE_ADDR_CONTEXT_KEY.get();

        return new StreamObserver<>() {
            @Override
            public void onNext(ClientEnvelope envelope) {
                BoundStreamSession currentSession = boundSessionRef.get();
                if (currentSession == null) {
                    handleInitialHandshake(envelope);
                } else {
                    handlePostHandshakeFrame(envelope, currentSession);
                }
            }

            private void handleInitialHandshake(ClientEnvelope envelope) {
                if (!tierRateLimiterService.checkHandshakePrefixAllowed(remoteAddress)) {
                    failAndCleanup(Status.RESOURCE_EXHAUSTED
                            .withDescription("Limite de tentativas de AUTH_HANDSHAKE por minuto excedido para este prefixo de rede"));
                    return;
                }

                String rawSender = envelope.getSenderUuid().trim().toLowerCase(Locale.ROOT);
                String rawReceiver = envelope.getReceiverUuid().trim().toLowerCase(Locale.ROOT);

                // Verifica primeiro formato do handshake ou assinatura
                DeviceDelegationCert delCert = envelope.hasSenderDelegationCert() ? envelope.getSenderDelegationCert() : null;
                VerificationCredential verCred = envelope.hasSenderVerificationCredential()
                        ? envelope.getSenderVerificationCredential()
                        : null;

                ValidatedSessionIdentity validated;
                try {
                    validated = tierRateLimiterService.validateSessionCredentials(
                            rawSender,
                            envelope.getSenderDeviceId(),
                            envelope.getSenderPublicKey().toByteArray(),
                            delCert,
                            verCred);
                } catch (StatusRuntimeException e) {
                    failAndCleanup(e.getStatus());
                    return;
                }

                boolean sigValid = verifyEnvelopeSignature(
                        envelope,
                        validated.userUuid(),
                        validated.deviceId(),
                        validated.deviceEd25519Pub(),
                        validated.tier());

                if (!sigValid) {
                    log.warn("AUTH_HANDSHAKE rejeitado por assinatura Ed25519 invalida (sender={})", rawSender);
                    failAndCleanup(Status.UNAUTHENTICATED
                            .withDescription("Assinatura Ed25519 invalida, timestamp expirado ou UUIDv7 incompativel com a PublicKey"));
                    return;
                }

                if (envelope.getType() != MessageType.AUTH_HANDSHAKE || !validated.userUuid().equals(rawReceiver)) {
                    log.warn("Primeiro pacote da stream deve ser AUTH_HANDSHAKE auto-enderecado (sender={}, receiver={}, type={})",
                            validated.userUuid(), rawReceiver, envelope.getType());
                    failAndCleanup(Status.UNAUTHENTICATED
                            .withDescription("O primeiro pacote da stream deve ser AUTH_HANDSHAKE com sender_uuid == receiver_uuid"));
                    return;
                }

                boolean registered = localSessionRegistry.tryRegisterDeviceStream(
                        validated.userUuid(),
                        validated.deviceId(),
                        validated.role(),
                        validated.tier(),
                        responseObserver);

                if (!registered) {
                    failAndCleanup(Status.RESOURCE_EXHAUSTED
                            .withDescription("Limite de streams concorrentes atingido para este nivel de conta ou dispositivo"));
                    return;
                }

                BoundStreamSession session = new BoundStreamSession(
                        validated.userUuid(),
                        validated.deviceId(),
                        validated.tier(),
                        validated.role(),
                        validated.deviceEd25519Pub(),
                        delCert != null && !delCert.getCertId().isBlank() ? delCert : null,
                        verCred != null && !verCred.getCredentialId().isBlank() ? verCred : null);

                if (!boundSessionRef.compareAndSet(null, session)) {
                    localSessionRegistry.unregisterDeviceStream(validated.userUuid(), validated.deviceId(), responseObserver);
                    return;
                }

                streamPendingOfflineMessages(session.userUuid(), session.deviceId(), responseObserver);
            }

            private void handlePostHandshakeFrame(ClientEnvelope envelope, BoundStreamSession session) {
                String normalizedSender = envelope.getSenderUuid().trim().toLowerCase(Locale.ROOT);
                if (!session.userUuid().equals(normalizedSender)) {
                    log.warn("Tentativa de troca de sender_uuid na mesma stream ({} -> {}) bloqueada",
                            session.userUuid(), normalizedSender);
                    failAndCleanup(Status.PERMISSION_DENIED
                            .withDescription("Nao e permitido alterar o sender_uuid de uma stream ja autenticada"));
                    return;
                }

                String frameDeviceId = envelope.getSenderDeviceId().isBlank()
                        ? session.deviceId()
                        : envelope.getSenderDeviceId().trim().toLowerCase(Locale.ROOT);
                if (!session.deviceId().equals(frameDeviceId)) {
                    failAndCleanup(Status.PERMISSION_DENIED
                            .withDescription("Nao e permitido alterar o sender_device_id de uma stream ja autenticada"));
                    return;
                }

                if (!envelope.getSenderPublicKey().isEmpty()
                        && !Arrays.equals(session.deviceEd25519Pub(), envelope.getSenderPublicKey().toByteArray())) {
                    failAndCleanup(Status.UNAUTHENTICATED
                            .withDescription("sender_public_key diverge da chave autenticada no handshake"));
                    return;
                }

                if (tierRateLimiterService.isRevoked(session.userUuid(), session.deviceId())) {
                    failAndCleanup(Status.PERMISSION_DENIED
                            .withDescription("Conta ou dispositivo revogado durante a sessao ativa"));
                    return;
                }

                boolean sigValid = verifyEnvelopeSignature(
                        envelope,
                        session.userUuid(),
                        session.deviceId(),
                        session.deviceEd25519Pub(),
                        session.tier());

                if (!sigValid) {
                    failAndCleanup(Status.UNAUTHENTICATED
                            .withDescription("Assinatura Ed25519 invalida ou timestamp expirado no quadro da stream"));
                    return;
                }

                if (envelope.getType() == MessageType.AUTH_HANDSHAKE) {
                    return;
                }

                if (envelope.getType() == MessageType.DELIVERY_ACK) {
                    if (!envelope.getRefMessageId().isBlank()) {
                        offlineQueueService.acknowledgeDelivery(
                                session.userUuid(),
                                session.deviceId(),
                                envelope.getRefMessageId());
                        ClientEnvelope enrichedAck = enrichForRouting(envelope, session);
                        messageRouterService.forwardDeliveryAckToHomeNodeIfRemote(enrichedAck, session.deviceId());
                    }
                    return;
                }

                if (!tierRateLimiterService.checkOutgoingEnvelopeAllowed(
                        session.userUuid(),
                        session.tier(),
                        envelope.getType(),
                        envelope.getReceiverUuid())) {
                    failAndCleanup(Status.RESOURCE_EXHAUSTED
                            .withDescription("Taxa de envio por minuto ou limite de 20 novos destinatarios em 24h excedido"));
                    return;
                }

                ClientEnvelope enriched = enrichForRouting(envelope, session);
                messageRouterService.routeClientEnvelope(enriched, responseObserver);
            }

            @Override
            public void onError(Throwable t) {
                cleanupStream();
            }

            @Override
            public void onCompleted() {
                cleanupStream();
                responseObserver.onCompleted();
            }

            private void failAndCleanup(Status status) {
                cleanupStream();
                responseObserver.onError(status.asRuntimeException());
            }

            private void cleanupStream() {
                BoundStreamSession bound = boundSessionRef.getAndSet(null);
                if (bound != null) {
                    localSessionRegistry.unregisterDeviceStream(bound.userUuid(), bound.deviceId(), responseObserver);
                }
            }
        };
    }

    private boolean verifyEnvelopeSignature(
            ClientEnvelope envelope,
            String userUuid,
            String deviceId,
            byte[] deviceEd25519Pub,
            AccountTier tier) {
        boolean v2Valid = cryptoService.verifyMessageEnvelopeV2(
                envelope.getMessageId(),
                userUuid,
                deviceId,
                envelope.getReceiverUuid(),
                envelope.getType().getNumber(),
                envelope.getRefMessageId(),
                envelope.getEncryptedPayload().toByteArray(),
                envelope.getTimestamp(),
                deviceEd25519Pub,
                envelope.getSignature().toByteArray());
        if (v2Valid) {
            return true;
        }

        // Fallback V1 estritamente para contas Unverified single-device sem metadados V2
        boolean hasV2OnlyFields = !envelope.getSenderDeviceId().isBlank()
                || !envelope.getRefMessageId().isBlank()
                || (envelope.hasSenderDelegationCert() && !envelope.getSenderDelegationCert().getCertId().isBlank())
                || (envelope.hasSenderVerificationCredential()
                && !envelope.getSenderVerificationCredential().getCredentialId().isBlank());
        if (tier == AccountTier.UNVERIFIED && !hasV2OnlyFields && userUuid.equals(deviceId)) {
            return cryptoService.verifyMessageEnvelope(
                    envelope.getMessageId(),
                    userUuid,
                    envelope.getReceiverUuid(),
                    envelope.getType().getNumber(),
                    envelope.getTimestamp(),
                    envelope.getEncryptedPayload().toByteArray(),
                    deviceEd25519Pub,
                    envelope.getSignature().toByteArray());
        }
        return false;
    }

    private static ClientEnvelope enrichForRouting(ClientEnvelope raw, BoundStreamSession session) {
        ClientEnvelope.Builder builder = raw.toBuilder();
        if (raw.getSenderDeviceId().isBlank()) {
            builder.setSenderDeviceId(session.deviceId());
        }
        if (raw.getSenderPublicKey().isEmpty()) {
            builder.setSenderPublicKey(ByteString.copyFrom(session.deviceEd25519Pub()));
        }
        if (!raw.hasSenderDelegationCert() && session.delegationCert() != null) {
            builder.setSenderDelegationCert(session.delegationCert());
        }
        if (!raw.hasSenderVerificationCredential() && session.verificationCredential() != null) {
            builder.setSenderVerificationCredential(session.verificationCredential());
        }
        return builder.build();
    }

    private void streamPendingOfflineMessages(
            String userUuid,
            String deviceId,
            StreamObserver<ServerEnvelope> responseObserver) {
        List<ServerEnvelope> pendingMessages = offlineQueueService.peekPendingMessages(userUuid, deviceId);
        for (ServerEnvelope msg : pendingMessages) {
            synchronized (responseObserver) {
                responseObserver.onNext(msg);
            }
        }
    }
}
