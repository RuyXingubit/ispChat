package br.dev.ispchat.erp.service;

import br.dev.ispchat.common.crypto.CanonicalTranscripts;
import br.dev.ispchat.erp.webhook.WebhookDispatcher;
import br.dev.ispchat.erp.webhook.WebhookDispatcher.InboundWebhookPayload;
import br.dev.ispchat.grpc.ClientEnvelope;
import br.dev.ispchat.grpc.ClientStreamServiceGrpc;
import br.dev.ispchat.grpc.DeviceDelegationCert;
import br.dev.ispchat.grpc.DeviceRole;
import br.dev.ispchat.grpc.DhtClientServiceGrpc;
import br.dev.ispchat.grpc.DocumentType;
import br.dev.ispchat.grpc.FindUserSessionsRequest;
import br.dev.ispchat.grpc.FindUserSessionsResponse;
import br.dev.ispchat.grpc.GetCredentialRequest;
import br.dev.ispchat.grpc.MessageType;
import br.dev.ispchat.grpc.MultiDeviceEncryptedPayload;
import br.dev.ispchat.grpc.PrincipalGovernanceServiceGrpc;
import br.dev.ispchat.grpc.PrincipalSigningCert;
import br.dev.ispchat.grpc.RegisterSessionRequest;
import br.dev.ispchat.grpc.RegisterSessionResponse;
import br.dev.ispchat.grpc.ServerEnvelope;
import br.dev.ispchat.grpc.UserSessionInfo;
import br.dev.ispchat.grpc.VerificationBundle;
import br.dev.ispchat.grpc.VerificationCredential;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Base64;
import java.util.Deque;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Servico E2E HTTP-to-gRPC do sidecar corporativo ispchat-erp-bridge:
 * - Conecta ao No ISP local via canal TLS 1.3 dedicado confiando em ca.crt;
 * - Opera exclusivamente sob subchave delegada ERP_API de uma conta CNPJ verificada;
 * - Criptografa mensagens de cobranca/atendimento para os dispositivos do assinante + DESKTOPs
 *   corporativos pareados (sem enfileirar copias offline redundantes de irmaos ERP_API);
 * - Descriptografa respostas de clientes, aplica rate-limit para nao verificados e despacha
 *   ao WebhookDispatcher, enviando DELIVERY_ACK somente apos confirmacao HTTP 2xx do ERP.
 */
@Service
@ConditionalOnProperty(name = "ispchat.erp.enabled", havingValue = "true", matchIfMissing = true)
public class ErpGatewayService {

    private static final Logger log = LoggerFactory.getLogger(ErpGatewayService.class);
    private static final long WINDOW_ONE_MINUTE_MS = 60_000L;
    private static final long FRESHNESS_TOLERANCE_MS = 300_000L;
    private static final long CREDENTIAL_RENEW_WINDOW_MS = TimeUnit.DAYS.toMillis(30);
    private static final Set<String> VALID_ADAPTERS = Set.of("IXC", "SGP", "MKAUTH", "GENERIC");

    public record SendMessageResult(String messageId, String status) {
    }

    public static final class ErpDeliveryException extends RuntimeException {
        private final int httpStatus;
        private final String errorCode;
        private final String replacedByUuid;

        public ErpDeliveryException(int httpStatus, String errorCode, String message, String replacedByUuid) {
            super(message);
            this.httpStatus = httpStatus;
            this.errorCode = errorCode;
            this.replacedByUuid = replacedByUuid;
        }

        public int httpStatus() {
            return httpStatus;
        }

        public String errorCode() {
            return errorCode;
        }

        public String replacedByUuid() {
            return replacedByUuid;
        }
    }

    private final WebhookDispatcher webhookDispatcher;
    private final String nodeAddress;
    private final String caCertPath;
    private final byte[] rootEd25519Pub;
    private final byte[] deviceEd25519Priv;
    private final byte[] deviceEd25519Pub;
    private final byte[] deviceX25519Priv;
    private final byte[] deviceX25519Pub;
    private final DeviceDelegationCert delegationCert;
    private volatile VerificationCredential verificationCredential;
    private final String corporateUuid;
    private final String deviceId;
    private final int unverifiedSenderRpm;
    private final int unverifiedGlobalRpm;

    private ManagedChannel channel;
    private volatile StreamObserver<ClientEnvelope> outboundStream;
    private final Object streamWriteLock = new Object();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean streamConnected = new AtomicBoolean(false);
    private volatile CountDownLatch initialStreamLatch = new CountDownLatch(1);
    private Thread supervisorThread;

    private final Map<String, CompletableFuture<String>> pendingOutboundAcks = new ConcurrentHashMap<>();
    private final Map<String, SendMessageResult> idempotencyCache = new ConcurrentHashMap<>();
    private final Set<String> seenInboundMessageIds = ConcurrentHashMap.newKeySet();
    private final Map<String, Deque<Long>> unverifiedSenderWindows = new ConcurrentHashMap<>();
    private final Deque<Long> unverifiedGlobalWindow = new ArrayDeque<>();

    public ErpGatewayService(
            WebhookDispatcher webhookDispatcher,
            @Value("${ispchat.erp.node-address}") String nodeAddress,
            @Value("${ispchat.erp.ca-cert-path:../ca/ca.crt}") String caCertPath,
            @Value("${ispchat.root.public-key}") String rootPublicKeyEncoded,
            @Value("${ispchat.erp.device-ed25519-private-key}") String deviceEdPrivEncoded,
            @Value("${ispchat.erp.device-x25519-private-key}") String deviceXPrivEncoded,
            @Value("${ispchat.erp.delegation-cert-base64}") String delegationCertBase64,
            @Value("${ispchat.erp.verification-credential-base64}") String credentialBase64,
            @Value("${ispchat.erp.unverified-per-sender-per-min:10}") int unverifiedSenderRpm,
            @Value("${ispchat.erp.unverified-global-per-min:120}") int unverifiedGlobalRpm) {
        this.webhookDispatcher = webhookDispatcher;
        this.nodeAddress = nodeAddress != null ? nodeAddress.trim() : "";
        this.caCertPath = caCertPath;
        this.unverifiedSenderRpm = Math.max(1, unverifiedSenderRpm);
        this.unverifiedGlobalRpm = Math.max(1, unverifiedGlobalRpm);

        this.rootEd25519Pub = decodeFlexibleBytes(rootPublicKeyEncoded, "ispchat.root.public-key");
        this.deviceEd25519Priv = decodeFlexibleBytes(deviceEdPrivEncoded, "ispchat.erp.device-ed25519-private-key");
        this.deviceX25519Priv = decodeFlexibleBytes(deviceXPrivEncoded, "ispchat.erp.device-x25519-private-key");

        try {
            this.delegationCert = DeviceDelegationCert.parseFrom(
                    decodeFlexibleBytes(delegationCertBase64, "ispchat.erp.delegation-cert-base64"));
            this.verificationCredential = VerificationCredential.parseFrom(
                    decodeFlexibleBytes(credentialBase64, "ispchat.erp.verification-credential-base64"));
        } catch (Exception e) {
            throw new IllegalArgumentException("Falha ao decodificar certificados ERP_API em Base64", e);
        }

        validatePairedCorporateCredentials();
        this.corporateUuid = CanonicalTranscripts.canonicalUuid(verificationCredential.getUserUuid());
        this.deviceId = CanonicalTranscripts.canonicalUuid(delegationCert.getDeviceId());
        this.deviceEd25519Pub = delegationCert.getDeviceEd25519Pub().toByteArray();
        this.deviceX25519Pub = delegationCert.getDeviceX25519Pub().toByteArray();
    }

    @PostConstruct
    public void start() throws Exception {
        HostAndPort target = parseHostAndPort(nodeAddress, 443);
        File caFile = resolveCertFile(caCertPath);
        this.channel = NettyChannelBuilder.forAddress(target.host(), target.port())
                .sslContext(GrpcSslContexts.forClient()
                        .trustManager(caFile)
                        .protocols("TLSv1.3")
                        .build())
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .build();

        running.set(true);
        supervisorThread = Thread.ofVirtual().name("erp-stream-supervisor").start(this::superviseConnectionLoop);
        initialStreamLatch.await(5, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        streamConnected.set(false);
        if (supervisorThread != null) {
            supervisorThread.interrupt();
        }
        synchronized (streamWriteLock) {
            if (outboundStream != null) {
                try {
                    outboundStream.onCompleted();
                } catch (Exception ignored) {
                }
                outboundStream = null;
            }
        }
        if (channel != null) {
            channel.shutdownNow();
        }
        Arrays.fill(deviceEd25519Priv, (byte) 0);
        Arrays.fill(deviceX25519Priv, (byte) 0);
    }

    public boolean isStreamConnected() {
        return streamConnected.get();
    }

    public String getCorporateUuid() {
        return corporateUuid;
    }

    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Criptografa E2E e envia uma mensagem corporativa (fatura, alerta ou suporte) para o assinante.
     */
    public SendMessageResult sendCorporateMessage(
            String rawRecipientUuid,
            String text,
            String idempotencyKey,
            String erpAdapter) {
        String recipientUuid;
        try {
            recipientUuid = CanonicalTranscripts.canonicalUuid(rawRecipientUuid);
        } catch (IllegalArgumentException e) {
            throw new ErpDeliveryException(400, "INVALID_RECIPIENT_UUID", "recipientUuid deve ser UUIDv7 valido", null);
        }
        if (text == null || text.isBlank()) {
            throw new ErpDeliveryException(400, "EMPTY_TEXT", "Campo text nao pode ser vazio", null);
        }
        byte[] plaintextUtf8 = text.getBytes(StandardCharsets.UTF_8);
        if (plaintextUtf8.length > 16_384) {
            throw new ErpDeliveryException(400, "PAYLOAD_TOO_LARGE", "Texto excede o limite de 16 KiB", null);
        }

        String normalizedAdapter = (erpAdapter == null || erpAdapter.isBlank())
                ? "GENERIC"
                : erpAdapter.trim().toUpperCase(Locale.ROOT);
        if (!VALID_ADAPTERS.contains(normalizedAdapter)) {
            throw new ErpDeliveryException(
                    400, "INVALID_ERP_ADAPTER", "erpAdapter deve ser IXC, SGP, MKAUTH ou GENERIC", null);
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            SendMessageResult cached = idempotencyCache.get(idempotencyKey.trim());
            if (cached != null) {
                return cached;
            }
        }

        if (!streamConnected.get() || outboundStream == null) {
            throw new ErpDeliveryException(503, "NODE_STREAM_DISCONNECTED", "Stream gRPC com No ISP desconectada", null);
        }

        DhtClientServiceGrpc.DhtClientServiceBlockingStub dhtStub =
                DhtClientServiceGrpc.newBlockingStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS);

        FindUserSessionsResponse recipientLookup = dhtStub.findUserSessions(
                FindUserSessionsRequest.newBuilder().setTargetUuid(recipientUuid).build());

        if (recipientLookup.hasReplacementNotice()
                && !recipientLookup.getReplacementNotice().getNewUserUuid().isBlank()
                && verifyReplacementNotice(recipientLookup.getReplacementNotice())) {
            String newUuid = recipientLookup.getReplacementNotice().getNewUserUuid();
            throw new ErpDeliveryException(
                    409,
                    "IDENTITY_REPLACED",
                    "O CPF deste assinante foi re-vinculado para um novo UUIDv7",
                    newUuid);
        }

        Map<String, byte[]> targetDeviceX25519Keys = new LinkedHashMap<>();
        for (UserSessionInfo session : recipientLookup.getSessionsList()) {
            VerifiedSessionKey verified = verifySessionInfoKey(recipientUuid, session);
            if (verified != null) {
                targetDeviceX25519Keys.put(verified.deviceId(), verified.x25519Pub());
            }
        }

        if (targetDeviceX25519Keys.isEmpty()) {
            throw new ErpDeliveryException(
                    404,
                    "RECIPIENT_KEYS_NOT_FOUND",
                    "Nenhuma chave publica X25519 verificada encontrada na DHT para " + recipientUuid,
                    null);
        }

        // Inclui chaves X25519 apenas de estacoes DESKTOP corporativas pareadas (sincronia de tela para atendentes)
        FindUserSessionsResponse siblingLookup = dhtStub.findUserSessions(
                FindUserSessionsRequest.newBuilder().setTargetUuid(corporateUuid).build());
        for (UserSessionInfo sibling : siblingLookup.getSessionsList()) {
            if (sibling.hasDelegationCert() && sibling.getDelegationCert().getRole() == DeviceRole.DESKTOP) {
                VerifiedSessionKey verifiedDesk = verifySessionInfoKey(corporateUuid, sibling);
                if (verifiedDesk != null && !verifiedDesk.deviceId().equals(deviceId)) {
                    targetDeviceX25519Keys.put(verifiedDesk.deviceId(), verifiedDesk.x25519Pub());
                }
            }
        }

        String messageId = CanonicalTranscripts.generateTemporalUuidV7().toString();
        long nowMs = System.currentTimeMillis();

        final byte[] encryptedPayloadBytes;
        try {
            MultiDeviceEncryptedPayload multiPayload = CanonicalTranscripts.encryptMultiDevicePayload(
                    messageId, corporateUuid, recipientUuid, plaintextUtf8, targetDeviceX25519Keys);
            encryptedPayloadBytes = multiPayload.toByteArray();
        } catch (Exception e) {
            throw new ErpDeliveryException(500, "E2E_ENCRYPTION_FAILED", "Falha ao criptografar carga E2E", null);
        }

        VerificationCredential currentCred = this.verificationCredential;
        byte[] sig = CanonicalTranscripts.signEd25519(
                deviceEd25519Priv,
                CanonicalTranscripts.buildMessageEnvelopeV2Transcript(
                        messageId,
                        corporateUuid,
                        deviceId,
                        recipientUuid,
                        MessageType.TEXT.getNumber(),
                        "",
                        encryptedPayloadBytes,
                        nowMs));

        ClientEnvelope envelope = ClientEnvelope.newBuilder()
                .setMessageId(messageId)
                .setSenderUuid(corporateUuid)
                .setSenderDeviceId(deviceId)
                .setReceiverUuid(recipientUuid)
                .setType(MessageType.TEXT)
                .setEncryptedPayload(ByteString.copyFrom(encryptedPayloadBytes))
                .setSenderPublicKey(ByteString.copyFrom(deviceEd25519Pub))
                .setSignature(ByteString.copyFrom(sig))
                .setTimestamp(nowMs)
                .setSenderDelegationCert(delegationCert)
                .setSenderVerificationCredential(currentCred)
                .build();

        CompletableFuture<String> ackFuture = new CompletableFuture<>();
        pendingOutboundAcks.put(messageId, ackFuture);

        try {
            synchronized (streamWriteLock) {
                if (outboundStream == null) {
                    throw new ErpDeliveryException(503, "NODE_STREAM_DISCONNECTED", "Stream indisponivel", null);
                }
                outboundStream.onNext(envelope);
            }

            String resolvedStatus;
            try {
                resolvedStatus = ackFuture.get(150, TimeUnit.MILLISECONDS);
            } catch (TimeoutException te) {
                resolvedStatus = "LIVE_DELIVERED";
            }

            SendMessageResult result = new SendMessageResult(messageId, resolvedStatus);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                if (idempotencyCache.size() >= 10_000) {
                    idempotencyCache.clear();
                }
                idempotencyCache.put(idempotencyKey.trim(), result);
            }
            return result;
        } catch (ErpDeliveryException e) {
            throw e;
        } catch (Exception e) {
            if (e.getCause() instanceof ErpDeliveryException ede) {
                throw ede;
            }
            throw new ErpDeliveryException(502, "DELIVERY_FAILED", "Falha ao rotear mensagem: " + e.getMessage(), null);
        } finally {
            pendingOutboundAcks.remove(messageId);
        }
    }

    /**
     * Envia DELIVERY_ACK assinado pela subchave ERP_API somente apos o ERP confirmar HTTP 2xx no webhook.
     */
    public void sendDeliveryAck(String originalSenderUuid, String messageId) {
        if (!running.get()) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        String ackFrameId = CanonicalTranscripts.generateTemporalUuidV7().toString();
        byte[] sig = CanonicalTranscripts.signEd25519(
                deviceEd25519Priv,
                CanonicalTranscripts.buildMessageEnvelopeV2Transcript(
                        ackFrameId,
                        corporateUuid,
                        deviceId,
                        corporateUuid,
                        MessageType.DELIVERY_ACK.getNumber(),
                        messageId,
                        new byte[0],
                        nowMs));

        ClientEnvelope ackEnvelope = ClientEnvelope.newBuilder()
                .setMessageId(ackFrameId)
                .setSenderUuid(corporateUuid)
                .setSenderDeviceId(deviceId)
                .setReceiverUuid(corporateUuid)
                .setType(MessageType.DELIVERY_ACK)
                .setRefMessageId(messageId)
                .setSignature(ByteString.copyFrom(sig))
                .setTimestamp(nowMs)
                .build();

        synchronized (streamWriteLock) {
            if (outboundStream != null && streamConnected.get()) {
                try {
                    outboundStream.onNext(ackEnvelope);
                } catch (Exception e) {
                    log.warn("Falha ao enviar DELIVERY_ACK para messageId={}: {}", messageId, e.getMessage());
                }
            }
        }
    }

    // =========================================================================
    // LOOP DE CONEXAO PERSISTENTE, REGISTRO DHT E PROCESSAMENTO DE ENVELOPES
    // =========================================================================

    private void superviseConnectionLoop() {
        long backoffMs = 1_000L;
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            CountDownLatch disconnectLatch = new CountDownLatch(1);
            try {
                maybeRenewCredential();
                registerDeviceSessionInDht();
                openAuthenticatedStream(disconnectLatch);
                backoffMs = 1_000L;
                disconnectLatch.await();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                streamConnected.set(false);
                if (!running.get()) {
                    break;
                }
                log.warn("Conexao gRPC do ERP Bridge interrompida ({}). Reconectando em {} ms...",
                        e.getMessage(), backoffMs);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                backoffMs = Math.min(30_000L, backoffMs * 2L);
            }
        }
    }

    private void registerDeviceSessionInDht() {
        long nowMs = System.currentTimeMillis();
        VerificationCredential cred = this.verificationCredential;
        String effectiveNodeAddr = !cred.getHomeNodeAddress().isBlank()
                ? cred.getHomeNodeAddress()
                : nodeAddress;

        byte[] dhtSig = CanonicalTranscripts.signEd25519(
                deviceEd25519Priv,
                CanonicalTranscripts.buildDhtSessionV2Transcript(
                        corporateUuid,
                        deviceId,
                        effectiveNodeAddr,
                        cred.getRootEd25519Pub().toByteArray(),
                        deviceEd25519Pub,
                        deviceX25519Pub,
                        nowMs));

        RegisterSessionResponse resp = DhtClientServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(5, TimeUnit.SECONDS)
                .registerSession(RegisterSessionRequest.newBuilder()
                        .setUserUuid(corporateUuid)
                        .setDeviceId(deviceId)
                        .setNodeAddress(effectiveNodeAddr)
                        .setRootEd25519Pub(cred.getRootEd25519Pub())
                        .setPublicKey(ByteString.copyFrom(deviceEd25519Pub))
                        .setX25519PublicKey(ByteString.copyFrom(deviceX25519Pub))
                        .setSignature(ByteString.copyFrom(dhtSig))
                        .setTimestamp(nowMs)
                        .setDelegationCert(delegationCert)
                        .setVerificationCredential(cred)
                        .build());

        if (!resp.getSuccess()) {
            throw new IllegalStateException("Falha ao registrar sessao ERP_API na DHT: " + resp.getErrorMessage());
        }
    }

    private void openAuthenticatedStream(CountDownLatch disconnectLatch) {
        StreamObserver<ClientEnvelope> reqStream = ClientStreamServiceGrpc.newStub(channel)
                .connect(new StreamObserver<>() {
                    @Override
                    public void onNext(ServerEnvelope value) {
                        handleInboundServerEnvelope(value);
                    }

                    @Override
                    public void onError(Throwable t) {
                        streamConnected.set(false);
                        disconnectLatch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        streamConnected.set(false);
                        disconnectLatch.countDown();
                    }
                });

        long nowMs = System.currentTimeMillis();
        String handshakeId = CanonicalTranscripts.generateTemporalUuidV7().toString();
        byte[] sig = CanonicalTranscripts.signEd25519(
                deviceEd25519Priv,
                CanonicalTranscripts.buildMessageEnvelopeV2Transcript(
                        handshakeId,
                        corporateUuid,
                        deviceId,
                        corporateUuid,
                        MessageType.AUTH_HANDSHAKE.getNumber(),
                        "",
                        new byte[0],
                        nowMs));

        ClientEnvelope handshake = ClientEnvelope.newBuilder()
                .setMessageId(handshakeId)
                .setSenderUuid(corporateUuid)
                .setSenderDeviceId(deviceId)
                .setReceiverUuid(corporateUuid)
                .setType(MessageType.AUTH_HANDSHAKE)
                .setSenderPublicKey(ByteString.copyFrom(deviceEd25519Pub))
                .setSignature(ByteString.copyFrom(sig))
                .setTimestamp(nowMs)
                .setSenderDelegationCert(delegationCert)
                .setSenderVerificationCredential(verificationCredential)
                .build();

        synchronized (streamWriteLock) {
            this.outboundStream = reqStream;
            reqStream.onNext(handshake);
        }
        streamConnected.set(true);
        initialStreamLatch.countDown();
    }

    private void handleInboundServerEnvelope(ServerEnvelope envelope) {
        switch (envelope.getType()) {
            case SERVER_QUEUED_ACK -> {
                CompletableFuture<String> fut = pendingOutboundAcks.get(envelope.getRefMessageId());
                if (fut != null) {
                    fut.complete("HOME_QUEUED");
                }
            }
            case DELIVERY_ERROR -> {
                CompletableFuture<String> fut = pendingOutboundAcks.get(envelope.getRefMessageId());
                if (fut != null) {
                    String errText = envelope.getEncryptedPayload().toStringUtf8();
                    fut.completeExceptionally(new ErpDeliveryException(
                            502, "NODE_DELIVERY_ERROR", errText.isBlank() ? "Falha de entrega" : errText, null));
                }
            }
            case IDENTITY_REPLACED -> {
                CompletableFuture<String> fut = pendingOutboundAcks.get(envelope.getRefMessageId());
                if (fut != null && envelope.hasReplacementNotice()
                        && verifyReplacementNotice(envelope.getReplacementNotice())) {
                    fut.completeExceptionally(new ErpDeliveryException(
                            409,
                            "IDENTITY_REPLACED",
                            "CPF re-vinculado para novo UUIDv7",
                            envelope.getReplacementNotice().getNewUserUuid()));
                }
            }
            case REVOCATION_NOTICE -> {
                log.error("Recebido REVOCATION_NOTICE na stream ERP_API (encerrando operacao): {}",
                        envelope.hasRevocationEvent() ? envelope.getRevocationEvent().getReason() : "REVOKED");
                running.set(false);
                streamConnected.set(false);
            }
            case TEXT -> processInboundTextEnvelope(envelope);
            default -> {
                // Ignora quadros de sinalizacao WebRTC em gateway ERP puramente textual
            }
        }
    }

    private void processInboundTextEnvelope(ServerEnvelope envelope) {
        String senderUuid = envelope.getSenderUuid().trim().toLowerCase(Locale.ROOT);
        String senderDevId = envelope.getSenderDeviceId().isBlank()
                ? senderUuid
                : envelope.getSenderDeviceId().trim().toLowerCase(Locale.ROOT);
        String receiverUuid = envelope.getReceiverUuid().isBlank()
                ? corporateUuid
                : envelope.getReceiverUuid().trim().toLowerCase(Locale.ROOT);

        // Ignora ecos da propria conta corporativa (envios originados neste ERP ou em DESKTOP de atendentes)
        if (senderUuid.equals(corporateUuid) || !receiverUuid.equals(corporateUuid)) {
            return;
        }

        if (!verifyInboundSenderSignature(envelope, senderUuid, senderDevId, receiverUuid)) {
            log.warn("Descartado ServerEnvelope messageId={} com assinatura Ed25519 invalida de {}",
                    envelope.getMessageId(), senderUuid);
            return;
        }

        if (seenInboundMessageIds.size() >= 10_000) {
            seenInboundMessageIds.clear();
        }
        if (!seenInboundMessageIds.add(envelope.getMessageId())) {
            return;
        }

        boolean senderVerified = envelope.hasSenderVerificationCredential()
                && verifyCredentialAgainstPinnedRoot(envelope.getSenderVerificationCredential(), senderUuid);

        if (!senderVerified && !allowUnverifiedInboundWebhook(senderUuid)) {
            log.warn("Rate-limit de webhook atingido para remetente nao verificado {}", senderUuid);
            return;
        }

        try {
            MultiDeviceEncryptedPayload payload = MultiDeviceEncryptedPayload.parseFrom(envelope.getEncryptedPayload());
            byte[] decryptedUtf8 = CanonicalTranscripts.decryptMultiDevicePayload(
                    envelope.getMessageId(),
                    senderUuid,
                    receiverUuid,
                    deviceId,
                    deviceX25519Priv,
                    payload);
            String text = new String(decryptedUtf8, StandardCharsets.UTF_8);

            InboundWebhookPayload webhookPayload = new InboundWebhookPayload(
                    envelope.getMessageId(),
                    senderUuid,
                    senderVerified,
                    corporateUuid,
                    text,
                    envelope.getTimestamp());

            webhookDispatcher.dispatchWithRetry(webhookPayload, this::sendDeliveryAck);
        } catch (Exception e) {
            log.warn("Falha ao descriptografar mensagem E2E {} de {} na subchave ERP_API: {}",
                    envelope.getMessageId(), senderUuid, e.getMessage());
        }
    }

    private boolean verifyInboundSenderSignature(
            ServerEnvelope env,
            String senderUuid,
            String senderDevId,
            String receiverUuid) {
        byte[] senderPub = env.getSenderPublicKey().toByteArray();
        if (senderPub.length != CanonicalTranscripts.RAW_KEY_LENGTH) {
            return false;
        }

        if (!senderDevId.equals(senderUuid)) {
            if (!env.hasSenderDelegationCert()) {
                return false;
            }
            DeviceDelegationCert del = env.getSenderDelegationCert();
            byte[] rootPub = env.hasSenderVerificationCredential()
                    ? env.getSenderVerificationCredential().getRootEd25519Pub().toByteArray()
                    : new byte[0];
            if (rootPub.length != CanonicalTranscripts.RAW_KEY_LENGTH
                    || !CanonicalTranscripts.verifyIdentityBinding(senderUuid, rootPub)
                    || !CanonicalTranscripts.verifyIdentityBinding(senderDevId, senderPub)
                    || !del.getUserUuid().trim().equalsIgnoreCase(senderUuid)
                    || !del.getDeviceId().trim().equalsIgnoreCase(senderDevId)
                    || !Arrays.equals(del.getDeviceEd25519Pub().toByteArray(), senderPub)
                    || !CanonicalTranscripts.verifyEd25519(
                            rootPub,
                            CanonicalTranscripts.buildDeviceDelegationTranscript(del),
                            del.getRootSignature().toByteArray())) {
                return false;
            }
        } else if (!CanonicalTranscripts.verifyIdentityBinding(senderUuid, senderPub)) {
            return false;
        }

        byte[] v2Transcript = CanonicalTranscripts.buildMessageEnvelopeV2Transcript(
                env.getMessageId(),
                senderUuid,
                senderDevId,
                receiverUuid,
                env.getTypeValue(),
                env.getRefMessageId(),
                env.getEncryptedPayload().toByteArray(),
                env.getTimestamp());

        return CanonicalTranscripts.verifyEd25519(senderPub, v2Transcript, env.getSignature().toByteArray());
    }

    private synchronized boolean allowUnverifiedInboundWebhook(String senderUuid) {
        long now = System.currentTimeMillis();
        pruneWindow(unverifiedGlobalWindow, now);
        if (unverifiedGlobalWindow.size() >= unverifiedGlobalRpm) {
            return false;
        }
        Deque<Long> senderDeque = unverifiedSenderWindows.computeIfAbsent(senderUuid, k -> new ArrayDeque<>());
        pruneWindow(senderDeque, now);
        if (senderDeque.size() >= unverifiedSenderRpm) {
            return false;
        }
        unverifiedGlobalWindow.addLast(now);
        senderDeque.addLast(now);
        return true;
    }

    private static void pruneWindow(Deque<Long> window, long nowMs) {
        while (!window.isEmpty() && (nowMs - window.peekFirst()) >= WINDOW_ONE_MINUTE_MS) {
            window.pollFirst();
        }
    }

    private void maybeRenewCredential() {
        long nowMs = System.currentTimeMillis();
        VerificationCredential current = this.verificationCredential;
        if ((current.getExpiresAtMs() - nowMs) > CREDENTIAL_RENEW_WINDOW_MS) {
            return;
        }
        try {
            byte[] sig = CanonicalTranscripts.signEd25519(
                    deviceEd25519Priv,
                    CanonicalTranscripts.buildGetOrRenewCredentialTranscript(corporateUuid, deviceId, nowMs));
            VerificationBundle renewed = PrincipalGovernanceServiceGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .getOrRenewCredential(GetCredentialRequest.newBuilder()
                            .setUserUuid(corporateUuid)
                            .setDeviceId(deviceId)
                            .setTimestampMs(nowMs)
                            .setCallerPublicKey(ByteString.copyFrom(deviceEd25519Pub))
                            .setSignature(ByteString.copyFrom(sig))
                            .setDelegationCert(delegationCert)
                            .build());
            if (renewed.hasCredential() && verifyCredentialAgainstPinnedRoot(renewed.getCredential(), corporateUuid)) {
                this.verificationCredential = renewed.getCredential();
            }
        } catch (Exception e) {
            log.debug("Nao foi possivel renovar preventivamente a VerificationCredential: {}", e.getMessage());
        }
    }

    private record VerifiedSessionKey(String deviceId, byte[] x25519Pub) {
    }

    private VerifiedSessionKey verifySessionInfoKey(String targetUuid, UserSessionInfo session) {
        byte[] x25519Pub = session.getX25519PublicKey().toByteArray();
        byte[] activeEdPub = !session.getDeviceEd25519Pub().isEmpty()
                ? session.getDeviceEd25519Pub().toByteArray()
                : session.getEd25519PublicKey().toByteArray();
        if (x25519Pub.length != CanonicalTranscripts.RAW_KEY_LENGTH
                || activeEdPub.length != CanonicalTranscripts.RAW_KEY_LENGTH
                || session.getSessionSignature().isEmpty()) {
            return null;
        }

        String devId = session.getDeviceId().isBlank()
                ? targetUuid
                : session.getDeviceId().trim().toLowerCase(Locale.ROOT);
        byte[] rootPub = !session.getRootEd25519Pub().isEmpty()
                ? session.getRootEd25519Pub().toByteArray()
                : activeEdPub;

        if (!CanonicalTranscripts.verifyIdentityBinding(targetUuid, rootPub)) {
            return null;
        }
        if (!devId.equals(targetUuid)) {
            if (!session.hasDelegationCert()) {
                return null;
            }
            DeviceDelegationCert del = session.getDelegationCert();
            if (!del.getUserUuid().trim().equalsIgnoreCase(targetUuid)
                    || !del.getDeviceId().trim().equalsIgnoreCase(devId)
                    || !Arrays.equals(del.getDeviceEd25519Pub().toByteArray(), activeEdPub)
                    || !Arrays.equals(del.getDeviceX25519Pub().toByteArray(), x25519Pub)
                    || !CanonicalTranscripts.verifyEd25519(
                            rootPub,
                            CanonicalTranscripts.buildDeviceDelegationTranscript(del),
                            del.getRootSignature().toByteArray())) {
                return null;
            }
        }

        byte[] v2Dht = CanonicalTranscripts.buildDhtSessionV2Transcript(
                targetUuid,
                devId,
                session.getNodeAddress(),
                rootPub,
                activeEdPub,
                x25519Pub,
                session.getTimestamp());
        if (CanonicalTranscripts.verifyEd25519(
                activeEdPub, v2Dht, session.getSessionSignature().toByteArray())) {
            return new VerifiedSessionKey(devId, x25519Pub);
        }
        return null;
    }

    private boolean verifyReplacementNotice(br.dev.ispchat.grpc.IdentityReplacementNotice notice) {
        PrincipalSigningCert pCert = verificationCredential.getPrincipalCert();
        return CanonicalTranscripts.verifyEd25519(
                pCert.getPrincipalEd25519Pub().toByteArray(),
                CanonicalTranscripts.buildIdentityReplacementTranscript(notice),
                notice.getPrincipalSignature().toByteArray());
    }

    private boolean verifyCredentialAgainstPinnedRoot(VerificationCredential cred, String expectedUuid) {
        try {
            if (!cred.hasPrincipalCert()
                    || !cred.getUserUuid().trim().equalsIgnoreCase(expectedUuid)) {
                return false;
            }
            long nowMs = System.currentTimeMillis();
            PrincipalSigningCert pCert = cred.getPrincipalCert();
            if (pCert.getExpiresAtMs() <= nowMs || cred.getExpiresAtMs() <= nowMs
                    || pCert.getIssuedAtMs() > nowMs + FRESHNESS_TOLERANCE_MS) {
                return false;
            }
            if (!CanonicalTranscripts.verifyEd25519(
                    rootEd25519Pub,
                    CanonicalTranscripts.buildPrincipalSigningCertTranscript(pCert),
                    pCert.getRootSignature().toByteArray())) {
                return false;
            }
            return CanonicalTranscripts.verifyEd25519(
                    pCert.getPrincipalEd25519Pub().toByteArray(),
                    CanonicalTranscripts.buildVerificationCredentialTranscript(cred),
                    cred.getPrincipalSignature().toByteArray());
        } catch (Exception e) {
            return false;
        }
    }

    private void validatePairedCorporateCredentials() {
        if (verificationCredential.getDocType() != DocumentType.CNPJ) {
            throw new IllegalArgumentException(
                    "ispchat-erp-bridge exige uma VerificationCredential corporativa do tipo CNPJ");
        }
        if (!verifyCredentialAgainstPinnedRoot(verificationCredential, verificationCredential.getUserUuid())) {
            throw new IllegalArgumentException(
                    "VerificationCredential corporativa invalida, expirada ou nao assinada pela Raiz Nivel 0");
        }
        if (delegationCert.getRole() != DeviceRole.ERP_API) {
            throw new IllegalArgumentException("DeviceDelegationCert do bridge deve possuir role = ERP_API");
        }
        if (!delegationCert.getUserUuid().trim().equalsIgnoreCase(verificationCredential.getUserUuid().trim())
                || delegationCert.getExpiresAtMs() <= System.currentTimeMillis()) {
            throw new IllegalArgumentException("DeviceDelegationCert expirado ou de outro corporateUuid");
        }
        if (!CanonicalTranscripts.verifyEd25519(
                verificationCredential.getRootEd25519Pub().toByteArray(),
                CanonicalTranscripts.buildDeviceDelegationTranscript(delegationCert),
                delegationCert.getRootSignature().toByteArray())) {
            throw new IllegalArgumentException(
                    "Assinatura da chave raiz corporativa invalida no DeviceDelegationCert ERP_API");
        }

        byte[] selfTest = "ISPCHAT-ERP-BRIDGE-KEY-CHECK".getBytes(StandardCharsets.UTF_8);
        byte[] testSig = CanonicalTranscripts.signEd25519(deviceEd25519Priv, selfTest);
        if (!CanonicalTranscripts.verifyEd25519(
                delegationCert.getDeviceEd25519Pub().toByteArray(), selfTest, testSig)) {
            throw new IllegalArgumentException("deviceEd25519Priv nao corresponde a DeviceDelegationCert.device_ed25519_pub");
        }
        try {
            byte[] derivedXPub = CanonicalTranscripts.deriveX25519PublicKeyFromPrivate(deviceX25519Priv);
            if (!Arrays.equals(derivedXPub, delegationCert.getDeviceX25519Pub().toByteArray())) {
                throw new IllegalArgumentException("deviceX25519Priv nao corresponde a DeviceDelegationCert.device_x25519_pub");
            }
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalArgumentException("Chave privada X25519 invalida", e);
        }
    }

    private record HostAndPort(String host, int port) {
    }

    private static HostAndPort parseHostAndPort(String rawAddress, int defaultPort) {
        if (rawAddress == null || rawAddress.isBlank()) {
            throw new IllegalArgumentException("ispchat.erp.node-address nao pode ser vazio");
        }
        String trimmed = rawAddress.trim();
        if (trimmed.startsWith("[")) {
            int close = trimmed.indexOf(']');
            String host = trimmed.substring(1, close);
            int port = (close + 2 <= trimmed.length() && trimmed.charAt(close + 1) == ':')
                    ? Integer.parseInt(trimmed.substring(close + 2))
                    : defaultPort;
            return new HostAndPort(host, port);
        }
        int colon = trimmed.lastIndexOf(':');
        if (colon > 0 && colon == trimmed.indexOf(':')) {
            return new HostAndPort(trimmed.substring(0, colon), Integer.parseInt(trimmed.substring(colon + 1)));
        }
        return new HostAndPort(trimmed, defaultPort);
    }

    private static File resolveCertFile(String configuredPath) {
        String normalized = configuredPath.startsWith("../")
                ? configuredPath.substring(3)
                : configuredPath;
        for (String candidate : new String[]{
                configuredPath,
                "../" + configuredPath,
                normalized,
                "../" + normalized,
                "../../" + normalized}) {
            File f = new File(candidate);
            if (f.exists()) {
                return f;
            }
        }
        throw new IllegalStateException("Certificado CA nao encontrado: " + configuredPath);
    }

    private static byte[] decodeFlexibleBytes(String raw, String name) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Propriedade obrigatoria ausente: " + name);
        }
        String trimmed = raw.trim();
        if (trimmed.length() % 2 == 0 && trimmed.matches("^[0-9a-fA-F]+$")) {
            return HexFormat.of().parseHex(trimmed);
        }
        try {
            return Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException ignored) {
            return Base64.getUrlDecoder().decode(trimmed);
        }
    }
}
