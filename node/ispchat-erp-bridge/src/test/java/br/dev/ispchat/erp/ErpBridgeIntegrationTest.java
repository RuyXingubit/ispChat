package br.dev.ispchat.erp;

import br.dev.ispchat.common.crypto.CanonicalTranscripts;
import br.dev.ispchat.common.crypto.CanonicalTranscripts.RawKeyPair;
import br.dev.ispchat.erp.service.ErpGatewayService;
import br.dev.ispchat.erp.web.ErpRestController;
import br.dev.ispchat.erp.webhook.WebhookDispatcher;
import br.dev.ispchat.grpc.ClientEnvelope;
import br.dev.ispchat.grpc.ClientStreamServiceGrpc;
import br.dev.ispchat.grpc.DeviceDelegationCert;
import br.dev.ispchat.grpc.DeviceRole;
import br.dev.ispchat.grpc.DhtClientServiceGrpc;
import br.dev.ispchat.grpc.DocumentType;
import br.dev.ispchat.grpc.HomeNodeClaim;
import br.dev.ispchat.grpc.IdentityReplacementNotice;
import br.dev.ispchat.grpc.MessageType;
import br.dev.ispchat.grpc.MultiDeviceEncryptedPayload;
import br.dev.ispchat.grpc.PrincipalSigningCert;
import br.dev.ispchat.grpc.RegisterSessionRequest;
import br.dev.ispchat.grpc.ServerEnvelope;
import br.dev.ispchat.grpc.VerificationCredential;
import br.dev.ispchat.node.NodeApplication;
import br.dev.ispchat.node.dht.DhtStoreService;
import br.dev.ispchat.node.grpc.GrpcServerRunner;
import br.dev.ispchat.node.messaging.OfflineMessageQueueService;
import br.dev.ispchat.node.security.CryptoService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.sun.net.httpserver.HttpServer;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ErpBridgeIntegrationTest {

    private static final String API_BEARER_SECRET = "isp-lan-secret-token-2026-ixc";
    private static final String WEBHOOK_HMAC_SECRET = "erp-webhook-hmac-sha256-secret-key";
    private static final String ISP_CNPJ = "11222333000181";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private RawKeyPair rootEd;
    private RawKeyPair escrowX;
    private RawKeyPair principalOnlineEd;
    private PrincipalSigningCert principalCert;

    private ConfigurableApplicationContext nodeContext;
    private String nodeAddress;
    private OfflineMessageQueueService nodeOfflineQueue;
    private DhtStoreService nodeDhtStore;

    private RawKeyPair corpRootEd;
    private RawKeyPair corpRootX;
    private String corpUuid;
    private VerificationCredential corpCred;

    private RawKeyPair erpDeviceEd;
    private RawKeyPair erpDeviceX;
    private DeviceDelegationCert erpDelegationCert;

    private RawKeyPair corpDesktopEd;
    private RawKeyPair corpDesktopX;
    private DeviceDelegationCert corpDesktopCert;

    private HttpServer mockErpWebhookServer;
    private String webhookUrl;
    private final Map<String, AtomicInteger> webhookAttemptCounts = new ConcurrentHashMap<>();
    private final BlockingQueue<Map<String, Object>> deliveredWebhooks = new LinkedBlockingQueue<>();
    private final AtomicReference<CountDownLatch> first503ObservedLatch = new AtomicReference<>(new CountDownLatch(1));

    private ConfigurableApplicationContext erpBridgeContext;
    private String erpBaseUrl;
    private ManagedChannel clientTlsChannel;

    @BeforeAll
    void setUpNodeWebhookAndErpBridge() throws Exception {
        long nowMs = System.currentTimeMillis();
        rootEd = CanonicalTranscripts.generateEd25519KeyPair();
        escrowX = CanonicalTranscripts.generateX25519KeyPair();
        principalOnlineEd = CanonicalTranscripts.generateEd25519KeyPair();

        PrincipalSigningCert unsignedPCert = PrincipalSigningCert.newBuilder()
                .setCertId(CanonicalTranscripts.generateTemporalUuidV7().toString())
                .setPrincipalEd25519Pub(ByteString.copyFrom(principalOnlineEd.publicKey()))
                .setEscrowX25519Pub(ByteString.copyFrom(escrowX.publicKey()))
                .setIssuedAtMs(nowMs - 5_000L)
                .setExpiresAtMs(nowMs + TimeUnit.DAYS.toMillis(365))
                .build();
        principalCert = unsignedPCert.toBuilder()
                .setRootSignature(ByteString.copyFrom(CanonicalTranscripts.signEd25519(
                        rootEd.privateKey(), CanonicalTranscripts.buildPrincipalSigningCertTranscript(unsignedPCert))))
                .build();

        // 1. Sobe No ISP real (TLS 1.3) em porta livre reservada com ispchat.node.public-address configurado
        int reservedNodePort;
        try (java.net.ServerSocket ss = new java.net.ServerSocket(0)) {
            reservedNodePort = ss.getLocalPort();
        }
        nodeAddress = "localhost:" + reservedNodePort;
        List<String> nodeArgs = new ArrayList<>(List.of(
                "--grpc.server.enabled=true",
                "--grpc.server.port=" + reservedNodePort,
                "--ispchat.node.public-address=" + nodeAddress,
                "--ispchat.dht.srv-bootstrap-enabled=false",
                "--ispchat.governance.revocation-sync-enabled=false",
                "--ispchat.security.dev-mode=true",
                "--ispchat.security.ca-cert-path=../ca/ca.crt",
                "--ispchat.security.node-cert-path=../ca/dev-node.crt",
                "--ispchat.security.node-key-path=../ca/dev-node.key",
                "--ispchat.root.public-key=" + Base64.getEncoder().encodeToString(rootEd.publicKey()),
                "--ispchat.turn.static-auth-secret=erp-test-turn-secret"));
        if (!isLocalRedisReachable()) {
            nodeArgs.add("--spring.autoconfigure.exclude="
                    + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");
        }

        nodeContext = new SpringApplicationBuilder(NodeApplication.class)
                .web(WebApplicationType.NONE)
                .run(nodeArgs.toArray(String[]::new));

        int nodePort = nodeContext.getBean(GrpcServerRunner.class).getPort();
        nodeOfflineQueue = nodeContext.getBean(OfflineMessageQueueService.class);
        nodeDhtStore = nodeContext.getBean(DhtStoreService.class);
        assertTrue(nodeContext.getBean(CryptoService.class).verifyPrincipalSigningCert(principalCert));

        // 2. Emite VerificationCredential CNPJ + HomeNodeClaim + Subchaves ERP_API e DESKTOP
        corpRootEd = CanonicalTranscripts.generateEd25519KeyPair();
        corpRootX = CanonicalTranscripts.generateX25519KeyPair();
        corpUuid = CanonicalTranscripts.deriveIdentityUuidV7(corpRootEd.publicKey()).toString();

        VerificationCredential unsignedCorpCred = VerificationCredential.newBuilder()
                .setCredentialId(CanonicalTranscripts.generateTemporalUuidV7().toString())
                .setUserUuid(corpUuid)
                .setRootEd25519Pub(ByteString.copyFrom(corpRootEd.publicKey()))
                .setRootX25519Pub(ByteString.copyFrom(corpRootX.publicKey()))
                .setDocType(DocumentType.CNPJ)
                .setPublicCnpj(ISP_CNPJ)
                .setPublicDisplayName("Fibra Alfa Telecom")
                .setVerifyingIspCnpj(ISP_CNPJ)
                .setVerifyingIspName("Fibra Alfa Telecom")
                .setHomeNodeAddress(nodeAddress)
                .setRoutingEpoch(1L)
                .setIssuedAtMs(nowMs - 1_000L)
                .setExpiresAtMs(nowMs + TimeUnit.DAYS.toMillis(90))
                .setPrincipalCert(principalCert)
                .build();
        corpCred = unsignedCorpCred.toBuilder()
                .setPrincipalSignature(ByteString.copyFrom(CanonicalTranscripts.signEd25519(
                        principalOnlineEd.privateKey(),
                        CanonicalTranscripts.buildVerificationCredentialTranscript(unsignedCorpCred))))
                .build();

        HomeNodeClaim corpHomeClaim = HomeNodeClaim.newBuilder()
                .setUserUuid(corpUuid)
                .setHomeNodeAddress(nodeAddress)
                .setRoutingEpoch(1L)
                .setIssuedAtMs(nowMs)
                .setSignedByPrincipal(true)
                .build();
        corpHomeClaim = corpHomeClaim.toBuilder()
                .setClaimSignature(ByteString.copyFrom(CanonicalTranscripts.signEd25519(
                        principalOnlineEd.privateKey(),
                        CanonicalTranscripts.buildHomeNodeClaimTranscript(corpHomeClaim))))
                .build();
        assertTrue(nodeDhtStore.storeHomeNodeClaim(corpHomeClaim, corpCred, corpRootEd.publicKey()));

        erpDeviceEd = CanonicalTranscripts.generateEd25519KeyPair();
        erpDeviceX = CanonicalTranscripts.generateX25519KeyPair();
        erpDelegationCert = signDelegation(corpRootEd, erpDeviceEd, erpDeviceX, DeviceRole.ERP_API, nowMs);

        corpDesktopEd = CanonicalTranscripts.generateEd25519KeyPair();
        corpDesktopX = CanonicalTranscripts.generateX25519KeyPair();
        corpDesktopCert = signDelegation(corpRootEd, corpDesktopEd, corpDesktopX, DeviceRole.DESKTOP, nowMs);

        // 3. Sobe servidor HTTP mock do ERP (IXC/SGP) em 127.0.0.1:0 (retorna 503 na 1a tentativa e 200 na 2a)
        mockErpWebhookServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockErpWebhookServer.createContext("/erp/webhook", exchange -> {
            byte[] rawBody = exchange.getRequestBody().readAllBytes();
            String msgIdHeader = exchange.getRequestHeaders().getFirst("X-IspChat-Message-Id");
            String sigHeader = exchange.getRequestHeaders().getFirst("X-IspChat-Signature");
            String expectedSig = WebhookDispatcher.computeSignatureHeader(
                    WEBHOOK_HMAC_SECRET.getBytes(StandardCharsets.UTF_8), rawBody);

            if (msgIdHeader == null || !expectedSig.equals(sigHeader)) {
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }

            int attempt = webhookAttemptCounts
                    .computeIfAbsent(msgIdHeader, k -> new AtomicInteger(0))
                    .incrementAndGet();
            if (attempt == 1) {
                first503ObservedLatch.get().countDown();
                byte[] errBytes = "{\"error\":\"ERP_TEMPORARILY_UNAVAILABLE\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(503, errBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errBytes);
                }
                return;
            }

            Map<String, Object> parsed = objectMapper.readValue(rawBody, new TypeReference<>() {});
            deliveredWebhooks.add(parsed);
            byte[] okBytes = "{\"received\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, okBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(okBytes);
            }
        });
        mockErpWebhookServer.start();
        webhookUrl = "http://127.0.0.1:" + mockErpWebhookServer.getAddress().getPort() + "/erp/webhook";

        // 4. Sobe ErpBridgeApplication real em 127.0.0.1:0 com rate-limit Unverified enxuto (2/min) para teste
        erpBridgeContext = new SpringApplicationBuilder(ErpBridgeApplication.class)
                .run(
                        "--server.address=127.0.0.1",
                        "--server.port=0",
                        "--ispchat.erp.node-address=" + nodeAddress,
                        "--ispchat.erp.ca-cert-path=../ca/ca.crt",
                        "--ispchat.root.public-key=" + Base64.getEncoder().encodeToString(rootEd.publicKey()),
                        "--ispchat.erp.api-bearer-secret=" + API_BEARER_SECRET,
                        "--ispchat.erp.webhook-url=" + webhookUrl,
                        "--ispchat.erp.webhook-hmac-secret=" + WEBHOOK_HMAC_SECRET,
                        "--ispchat.erp.webhook-initial-backoff-ms=120",
                        "--ispchat.erp.webhook-max-backoff-ms=500",
                        "--ispchat.erp.device-ed25519-private-key=" + Base64.getEncoder().encodeToString(erpDeviceEd.privateKey()),
                        "--ispchat.erp.device-x25519-private-key=" + Base64.getEncoder().encodeToString(erpDeviceX.privateKey()),
                        "--ispchat.erp.delegation-cert-base64=" + Base64.getEncoder().encodeToString(erpDelegationCert.toByteArray()),
                        "--ispchat.erp.verification-credential-base64=" + Base64.getEncoder().encodeToString(corpCred.toByteArray()),
                        "--ispchat.erp.unverified-per-sender-per-min=2",
                        "--ispchat.erp.unverified-global-per-min=50");

        int erpHttpPort = ((WebServerApplicationContext) erpBridgeContext).getWebServer().getPort();
        erpBaseUrl = "http://127.0.0.1:" + erpHttpPort;

        clientTlsChannel = NettyChannelBuilder.forAddress("localhost", nodePort)
                .sslContext(GrpcSslContexts.forClient()
                        .trustManager(resolveCert("ca/ca.crt"))
                        .protocols("TLSv1.3")
                        .build())
                .build();
    }

    @AfterAll
    void tearDownAll() throws Exception {
        if (clientTlsChannel != null) {
            clientTlsChannel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
        if (erpBridgeContext != null) {
            erpBridgeContext.close();
        }
        if (mockErpWebhookServer != null) {
            mockErpWebhookServer.stop(0);
        }
        if (nodeContext != null) {
            nodeContext.close();
        }
    }

    @Test
    @Order(1)
    void shouldEnforcePairingUriLanBindBoundaryBearerAuthAndRoleValidation(@TempDir Path tempDir) throws Exception {
        // 1. CLI --print-pairing-uri gera subchaves idempotentes com role=ERP_API
        Path keystoreDir = tempDir.resolve("erp-keys");
        ByteArrayOutputStream cliInfo = new ByteArrayOutputStream();
        String uri1 = ErpBridgeApplication.generateOrLoadPairingUri(keystoreDir, 1762900000000L, new PrintStream(cliInfo));
        assertTrue(uri1.startsWith("ispchat://pair?device_id="));
        assertTrue(uri1.contains("&role=ERP_API&ts=1762900000000"));

        String uri2 = ErpBridgeApplication.generateOrLoadPairingUri(keystoreDir, 1762900000000L, null);
        assertEquals(uri1, uri2, "Reexecucao em diretorio existente deve reutilizar o mesmo par de chaves ERP_API");
        assertDoesNotThrow(() -> ErpBridgeApplication.main(
                new String[]{"--print-pairing-uri", "--keystore-dir=" + keystoreDir}));

        // 2. Fronteira de rede RFC 1918 / loopback / ULA
        assertThrows(SecurityException.class,
                () -> ErpBridgeApplication.validateLanBindAddress("0.0.0.0", false, "none"));
        assertThrows(SecurityException.class,
                () -> ErpBridgeApplication.validateLanBindAddress("", false, "none"));
        assertThrows(SecurityException.class,
                () -> ErpBridgeApplication.validateLanBindAddress("8.8.8.8", false, "none"));
        assertThrows(SecurityException.class,
                () -> ErpBridgeApplication.validateLanBindAddress("8.8.8.8", true, "want"));

        assertDoesNotThrow(() -> ErpBridgeApplication.validateLanBindAddress("127.0.0.1", false, "none"));
        assertDoesNotThrow(() -> ErpBridgeApplication.validateLanBindAddress("192.168.10.25", false, "none"));
        assertDoesNotThrow(() -> ErpBridgeApplication.validateLanBindAddress("172.28.0.10", false, "none"));
        assertDoesNotThrow(() -> ErpBridgeApplication.validateLanBindAddress("fd00::10", false, "none"));
        assertDoesNotThrow(() -> ErpBridgeApplication.validateLanBindAddress("8.8.8.8", true, "need"));

        // 3. Rejeicao de segredo curto ou certificados incorretos (CPF ou DESKTOP no lugar de CNPJ + ERP_API)
        assertThrows(IllegalArgumentException.class,
                () -> new ErpRestController(erpBridgeContext.getBean(ErpGatewayService.class), "curto"));
        assertThrows(IllegalArgumentException.class, () -> new ErpGatewayService(
                erpBridgeContext.getBean(WebhookDispatcher.class),
                nodeAddress,
                "../ca/ca.crt",
                Base64.getEncoder().encodeToString(rootEd.publicKey()),
                Base64.getEncoder().encodeToString(corpDesktopEd.privateKey()),
                Base64.getEncoder().encodeToString(corpDesktopX.privateKey()),
                Base64.getEncoder().encodeToString(corpDesktopCert.toByteArray()),
                Base64.getEncoder().encodeToString(corpCred.toByteArray()),
                10,
                120));

        // 4. Autenticacao Bearer em tempo constante em /api/v1/health e /api/v1/messages
        HttpResponse<String> missingAuthHealth = httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(erpBaseUrl + "/api/v1/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, missingAuthHealth.statusCode());

        HttpResponse<String> wrongAuthPost = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(erpBaseUrl + "/api/v1/messages"))
                        .header("Authorization", "Bearer token-incorreto-1234567890")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, wrongAuthPost.statusCode());

        // Aguarda stream do ErpGatewayService ficar UP
        long deadline = System.currentTimeMillis() + 5_000L;
        int healthStatus = 503;
        Map<String, Object> healthBody = Map.of();
        while (System.currentTimeMillis() < deadline) {
            HttpResponse<String> okHealth = httpClient.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(erpBaseUrl + "/api/v1/health"))
                            .header("Authorization", "Bearer " + API_BEARER_SECRET)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            healthStatus = okHealth.statusCode();
            healthBody = objectMapper.readValue(okHealth.body(), new TypeReference<>() {});
            if (healthStatus == 200) {
                break;
            }
            Thread.sleep(50);
        }
        assertEquals(200, healthStatus);
        assertEquals("UP", healthBody.get("status"));
        assertEquals(corpUuid, healthBody.get("corporateUuid"));

        // 5. Validacoes de entrada HTTP 400 / 404
        HttpResponse<String> badUuidResp = postErpMessage(Map.of(
                "recipientUuid", "nao-eh-uuid",
                "text", "Teste",
                "erpAdapter", "IXC"));
        assertEquals(400, badUuidResp.statusCode());

        String unknownUuid = CanonicalTranscripts.generateTemporalUuidV7().toString();
        HttpResponse<String> badAdapterResp = postErpMessage(Map.of(
                "recipientUuid", unknownUuid,
                "text", "Teste",
                "erpAdapter", "DESCONHECIDO"));
        assertEquals(400, badAdapterResp.statusCode());

        HttpResponse<String> notFoundResp = postErpMessage(Map.of(
                "recipientUuid", unknownUuid,
                "text", "Boleto",
                "erpAdapter", "SGP"));
        assertEquals(404, notFoundResp.statusCode());
    }

    @Test
    @Order(2)
    void shouldDeliverEncryptedInvoiceToOfflineSubscriberSyncCorporateDesktopAndDeduplicateIdempotencyKey()
            throws Exception {
        long nowMs = System.currentTimeMillis();

        // 1. Conecta estacao DESKTOP corporativa de atendente para validar sincronizacao multi-dispositivo
        registerDeviceSession(
                corpUuid, corpDesktopCert.getDeviceId(), corpRootEd.publicKey(),
                corpDesktopEd, corpDesktopX, corpDesktopCert, corpCred, nowMs);

        BlockingQueue<ServerEnvelope> corpDesktopInbox = new LinkedBlockingQueue<>();
        StreamObserver<ClientEnvelope> corpDesktopStream = ClientStreamServiceGrpc.newStub(clientTlsChannel)
                .connect(new StreamObserver<>() {
                    @Override public void onNext(ServerEnvelope value) { corpDesktopInbox.add(value); }
                    @Override public void onError(Throwable t) {}
                    @Override public void onCompleted() {}
                });
        corpDesktopStream.onNext(buildSignedV2Envelope(
                CanonicalTranscripts.generateTemporalUuidV7().toString(),
                corpUuid, corpDesktopCert.getDeviceId(), corpUuid,
                MessageType.AUTH_HANDSHAKE, "", new byte[0],
                corpDesktopEd, corpDesktopCert, corpCred, nowMs));

        // 2. Assinante registra chaves na DHT, porem esta OFFLINE no momento do faturamento
        RawKeyPair subscriberEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair subscriberX = CanonicalTranscripts.generateX25519KeyPair();
        String subscriberUuid = CanonicalTranscripts.deriveIdentityUuidV7(subscriberEd.publicKey()).toString();
        registerDeviceSession(
                subscriberUuid, subscriberUuid, subscriberEd.publicKey(),
                subscriberEd, subscriberX, null, null, nowMs);

        // 3. IXC dispara POST /api/v1/messages com idempotencyKey
        String invoiceText = "Fatura IXC #9001 - Vencimento 15/11 - PIX Copia e Cola: 00020126580014BR.GOV.BCB.PIX";
        HttpResponse<String> firstPost = postErpMessage(Map.of(
                "recipientUuid", subscriberUuid,
                "text", invoiceText,
                "idempotencyKey", "ixc-fatura-9001",
                "erpAdapter", "IXC"));
        assertEquals(202, firstPost.statusCode(), firstPost::body);
        Map<String, Object> firstJson = objectMapper.readValue(firstPost.body(), new TypeReference<>() {});
        String messageId = (String) firstJson.get("messageId");
        assertNotNull(messageId);
        assertEquals("HOME_QUEUED", firstJson.get("status"));

        // Reenvio com mesma idempotencyKey retorna exatamente o mesmo messageId sem duplicar na fila
        HttpResponse<String> duplicatePost = postErpMessage(Map.of(
                "recipientUuid", subscriberUuid,
                "text", invoiceText,
                "idempotencyKey", "ixc-fatura-9001",
                "erpAdapter", "IXC"));
        assertEquals(202, duplicatePost.statusCode());
        Map<String, Object> duplicateJson = objectMapper.readValue(duplicatePost.body(), new TypeReference<>() {});
        assertEquals(messageId, duplicateJson.get("messageId"));
        assertEquals(1, nodeOfflineQueue.getStoredMessageCount(subscriberUuid));

        // 4. Desktop corporativo recebe copia ao vivo (receiver_uuid == subscriberUuid) e descriptografa E2E
        ServerEnvelope syncedDesktopCopy = corpDesktopInbox.poll(3, TimeUnit.SECONDS);
        assertNotNull(syncedDesktopCopy, "Desktop corporativo pareado deve receber copia sincronizada ao vivo");
        assertEquals(messageId, syncedDesktopCopy.getMessageId());
        byte[] deskPlaintext = CanonicalTranscripts.decryptMultiDevicePayload(
                messageId,
                corpUuid,
                subscriberUuid,
                corpDesktopCert.getDeviceId(),
                corpDesktopX.privateKey(),
                MultiDeviceEncryptedPayload.parseFrom(syncedDesktopCopy.getEncryptedPayload()));
        assertEquals(invoiceText, new String(deskPlaintext, StandardCharsets.UTF_8));

        // Fila offline corporativa NAO foi poluida pelo disparo automatizado ERP_API
        assertEquals(0, nodeOfflineQueue.getStoredMessageCount(corpUuid));

        // 5. Assinante fica online, recebe a fatura pendente, descriptografa e envia DELIVERY_ACK
        BlockingQueue<ServerEnvelope> subscriberInbox = new LinkedBlockingQueue<>();
        StreamObserver<ClientEnvelope> subscriberStream = ClientStreamServiceGrpc.newStub(clientTlsChannel)
                .connect(new StreamObserver<>() {
                    @Override public void onNext(ServerEnvelope value) { subscriberInbox.add(value); }
                    @Override public void onError(Throwable t) {}
                    @Override public void onCompleted() {}
                });
        subscriberStream.onNext(buildSignedV2Envelope(
                CanonicalTranscripts.generateTemporalUuidV7().toString(),
                subscriberUuid, subscriberUuid, subscriberUuid,
                MessageType.AUTH_HANDSHAKE, "", new byte[0],
                subscriberEd, null, null, System.currentTimeMillis()));

        ServerEnvelope queuedInvoice = subscriberInbox.poll(3, TimeUnit.SECONDS);
        assertNotNull(queuedInvoice);
        assertEquals(messageId, queuedInvoice.getMessageId());
        byte[] subPlaintext = CanonicalTranscripts.decryptMultiDevicePayload(
                messageId,
                corpUuid,
                subscriberUuid,
                subscriberUuid,
                subscriberX.privateKey(),
                MultiDeviceEncryptedPayload.parseFrom(queuedInvoice.getEncryptedPayload()));
        assertEquals(invoiceText, new String(subPlaintext, StandardCharsets.UTF_8));

        subscriberStream.onNext(buildSignedV2Envelope(
                CanonicalTranscripts.generateTemporalUuidV7().toString(),
                subscriberUuid, subscriberUuid, subscriberUuid,
                MessageType.DELIVERY_ACK, messageId, new byte[0],
                subscriberEd, null, null, System.currentTimeMillis()));

        long ackDeadline = System.currentTimeMillis() + 2_000L;
        while (nodeOfflineQueue.getStoredMessageCount(subscriberUuid) > 0 && System.currentTimeMillis() < ackDeadline) {
            Thread.sleep(25);
        }
        assertEquals(0, nodeOfflineQueue.getStoredMessageCount(subscriberUuid));

        // 6. Testa tambem retorno 409 IDENTITY_REPLACED quando o CPF do destinatario sofreu Re-Bind
        String replacedOldUuid = CanonicalTranscripts.generateTemporalUuidV7().toString();
        String replacementNewUuid = CanonicalTranscripts.generateTemporalUuidV7().toString();
        IdentityReplacementNotice notice = IdentityReplacementNotice.newBuilder()
                .setRevocationId(CanonicalTranscripts.generateTemporalUuidV7().toString())
                .setOldUserUuid(replacedOldUuid)
                .setNewUserUuid(replacementNewUuid)
                .setVerifyingIspCnpj(ISP_CNPJ)
                .setReplacedAtMs(System.currentTimeMillis())
                .build();
        notice = notice.toBuilder()
                .setPrincipalSignature(ByteString.copyFrom(CanonicalTranscripts.signEd25519(
                        principalOnlineEd.privateKey(),
                        CanonicalTranscripts.buildIdentityReplacementTranscript(notice))))
                .build();
        assertTrue(nodeDhtStore.storeReplacementNotice(notice));

        HttpResponse<String> replacedResp = postErpMessage(Map.of(
                "recipientUuid", replacedOldUuid,
                "text", "Boleto apos troca de aparelho",
                "erpAdapter", "IXC"));
        assertEquals(409, replacedResp.statusCode());
        Map<String, Object> replacedBody = objectMapper.readValue(replacedResp.body(), new TypeReference<>() {});
        assertEquals("IDENTITY_REPLACED", replacedBody.get("error"));
        assertEquals(replacementNewUuid, replacedBody.get("replacedByUuid"));

        subscriberStream.onCompleted();
        corpDesktopStream.onCompleted();
    }

    @Test
    @Order(3)
    void shouldWithholdDeliveryAckOnWebhook503RetryUntil200OkAndRateLimitUnverifiedInboundFlood() throws Exception {
        deliveredWebhooks.clear();
        CountDownLatch latch503 = new CountDownLatch(1);
        first503ObservedLatch.set(latch503);

        RawKeyPair callerEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair callerX = CanonicalTranscripts.generateX25519KeyPair();
        String callerUuid = CanonicalTranscripts.deriveIdentityUuidV7(callerEd.publicKey()).toString();
        registerDeviceSession(
                callerUuid, callerUuid, callerEd.publicKey(),
                callerEd, callerX, null, null, System.currentTimeMillis());

        StreamObserver<ClientEnvelope> callerStream = ClientStreamServiceGrpc.newStub(clientTlsChannel)
                .connect(new StreamObserver<>() {
                    @Override public void onNext(ServerEnvelope value) {}
                    @Override public void onError(Throwable t) {}
                    @Override public void onCompleted() {}
                });
        callerStream.onNext(buildSignedV2Envelope(
                CanonicalTranscripts.generateTemporalUuidV7().toString(),
                callerUuid, callerUuid, callerUuid,
                MessageType.AUTH_HANDSHAKE, "", new byte[0],
                callerEd, null, null, System.currentTimeMillis()));

        // Assinante envia resposta E2E criptografada para a subchave ERP_API da empresa
        String replyMsgId = CanonicalTranscripts.generateTemporalUuidV7().toString();
        String replyText = "Ja paguei via PIX, segue comprovante de desbloqueio!";
        Map<String, byte[]> corpDeviceKeys = new LinkedHashMap<>();
        corpDeviceKeys.put(erpDelegationCert.getDeviceId(), erpDeviceX.publicKey());
        MultiDeviceEncryptedPayload encryptedReply = CanonicalTranscripts.encryptMultiDevicePayload(
                replyMsgId, callerUuid, corpUuid, replyText.getBytes(StandardCharsets.UTF_8), corpDeviceKeys);

        callerStream.onNext(buildSignedV2Envelope(
                replyMsgId, callerUuid, callerUuid, corpUuid,
                MessageType.TEXT, "", encryptedReply.toByteArray(),
                callerEd, null, null, System.currentTimeMillis()));

        // 1. Aguarda a 1a tentativa do Webhook receber HTTP 503 -> DELIVERY_ACK DEVE permanecer retido!
        assertTrue(latch503.await(3, TimeUnit.SECONDS), "Webhook deve tentar a 1a entrega e receber HTTP 503");
        assertEquals(1, nodeOfflineQueue.peekPendingMessages(corpUuid, erpDelegationCert.getDeviceId()).size(),
                "Enquanto o ERP retorna 503, a mensagem DEVE continuar retida em pending:<corpUuid>:<erpDeviceId>");

        // 2. Aguarda retentativa automatica (120ms backoff) receber HTTP 200 OK com HMAC-SHA256 valido
        Map<String, Object> webhookJson = deliveredWebhooks.poll(5, TimeUnit.SECONDS);
        assertNotNull(webhookJson, "Retentativa automatica do WebhookDispatcher deve entregar com HTTP 200");
        assertEquals(replyMsgId, webhookJson.get("messageId"));
        assertEquals(callerUuid, webhookJson.get("senderUuid"));
        assertEquals(Boolean.FALSE, webhookJson.get("senderVerified"));
        assertEquals(corpUuid, webhookJson.get("corporateUuid"));
        assertEquals(replyText, webhookJson.get("text"));
        assertEquals(2, webhookAttemptCounts.get(replyMsgId).get());

        // 3. Apos HTTP 200 OK, ErpGatewayService envia DELIVERY_ACK e esvazia a fila pendente no No ISP!
        long clearDeadline = System.currentTimeMillis() + 3_000L;
        while (!nodeOfflineQueue.peekPendingMessages(corpUuid, erpDelegationCert.getDeviceId()).isEmpty()
                && System.currentTimeMillis() < clearDeadline) {
            Thread.sleep(25);
        }
        assertTrue(nodeOfflineQueue.peekPendingMessages(corpUuid, erpDelegationCert.getDeviceId()).isEmpty(),
                "Fila pendente da subchave ERP_API deve ser esvaziada somente apos confirmacao HTTP 200 do webhook");

        // 4. Envia mais 2 mensagens seguidas do mesmo remetente nao verificado (limite = 2/min):
        //    a 2a passa e a 3a e descartada pelo rate-limit de webhook antes de acionar o ERP!
        for (int i = 2; i <= 3; i++) {
            String extraId = CanonicalTranscripts.generateTemporalUuidV7().toString();
            MultiDeviceEncryptedPayload extraPayload = CanonicalTranscripts.encryptMultiDevicePayload(
                    extraId, callerUuid, corpUuid, ("Msg #" + i).getBytes(StandardCharsets.UTF_8), corpDeviceKeys);
            callerStream.onNext(buildSignedV2Envelope(
                    extraId, callerUuid, callerUuid, corpUuid,
                    MessageType.TEXT, "", extraPayload.toByteArray(),
                    callerEd, null, null, System.currentTimeMillis()));
        }

        Map<String, Object> secondAcceptedWebhook = deliveredWebhooks.poll(5, TimeUnit.SECONDS);
        assertNotNull(secondAcceptedWebhook, "2a mensagem dentro de 2/min deve chegar ao ERP");
        assertEquals("Msg #2", secondAcceptedWebhook.get("text"));

        Map<String, Object> thirdDroppedWebhook = deliveredWebhooks.poll(400, TimeUnit.MILLISECONDS);
        assertEquals(null, thirdDroppedWebhook, "3a mensagem no mesmo minuto deve ser bloqueada pelo rate-limit Unverified");

        callerStream.onCompleted();
    }

    private HttpResponse<String> postErpMessage(Map<String, Object> payload) throws Exception {
        String json = objectMapper.writeValueAsString(payload);
        return httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(erpBaseUrl + "/api/v1/messages"))
                        .header("Authorization", "Bearer " + API_BEARER_SECRET)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void registerDeviceSession(
            String userUuid,
            String deviceId,
            byte[] rootEdPubBytes,
            RawKeyPair activeDeviceEd,
            RawKeyPair activeDeviceX,
            DeviceDelegationCert delCert,
            VerificationCredential verCred,
            long timestampMs) {
        byte[] sig = CanonicalTranscripts.signEd25519(
                activeDeviceEd.privateKey(),
                CanonicalTranscripts.buildDhtSessionV2Transcript(
                        userUuid,
                        deviceId,
                        nodeAddress,
                        rootEdPubBytes,
                        activeDeviceEd.publicKey(),
                        activeDeviceX.publicKey(),
                        timestampMs));

        RegisterSessionRequest.Builder req = RegisterSessionRequest.newBuilder()
                .setUserUuid(userUuid)
                .setDeviceId(deviceId)
                .setNodeAddress(nodeAddress)
                .setRootEd25519Pub(ByteString.copyFrom(rootEdPubBytes))
                .setPublicKey(ByteString.copyFrom(activeDeviceEd.publicKey()))
                .setX25519PublicKey(ByteString.copyFrom(activeDeviceX.publicKey()))
                .setSignature(ByteString.copyFrom(sig))
                .setTimestamp(timestampMs);
        if (delCert != null) {
            req.setDelegationCert(delCert);
        }
        if (verCred != null) {
            req.setVerificationCredential(verCred);
        }
        assertTrue(DhtClientServiceGrpc.newBlockingStub(clientTlsChannel).registerSession(req.build()).getSuccess());
    }

    private static DeviceDelegationCert signDelegation(
            RawKeyPair accountRootEd,
            RawKeyPair deviceEd,
            RawKeyPair deviceX,
            DeviceRole role,
            long nowMs) {
        String userUuid = CanonicalTranscripts.deriveIdentityUuidV7(accountRootEd.publicKey()).toString();
        String devId = CanonicalTranscripts.deriveIdentityUuidV7(deviceEd.publicKey()).toString();
        DeviceDelegationCert unsigned = DeviceDelegationCert.newBuilder()
                .setCertId(CanonicalTranscripts.generateTemporalUuidV7().toString())
                .setUserUuid(userUuid)
                .setDeviceId(devId)
                .setRole(role)
                .setDeviceEd25519Pub(ByteString.copyFrom(deviceEd.publicKey()))
                .setDeviceX25519Pub(ByteString.copyFrom(deviceX.publicKey()))
                .setIssuedAtMs(nowMs - 1_000L)
                .setExpiresAtMs(nowMs + TimeUnit.DAYS.toMillis(90))
                .build();
        return unsigned.toBuilder()
                .setRootSignature(ByteString.copyFrom(CanonicalTranscripts.signEd25519(
                        accountRootEd.privateKey(),
                        CanonicalTranscripts.buildDeviceDelegationTranscript(unsigned))))
                .build();
    }

    private static ClientEnvelope buildSignedV2Envelope(
            String messageId,
            String senderUuid,
            String senderDeviceId,
            String receiverUuid,
            MessageType type,
            String refMessageId,
            byte[] encryptedPayload,
            RawKeyPair signerEd,
            DeviceDelegationCert delCert,
            VerificationCredential verCred,
            long timestampMs) {
        byte[] sig = CanonicalTranscripts.signEd25519(
                signerEd.privateKey(),
                CanonicalTranscripts.buildMessageEnvelopeV2Transcript(
                        messageId,
                        senderUuid,
                        senderDeviceId,
                        receiverUuid,
                        type.getNumber(),
                        refMessageId,
                        encryptedPayload,
                        timestampMs));
        ClientEnvelope.Builder builder = ClientEnvelope.newBuilder()
                .setMessageId(messageId)
                .setSenderUuid(senderUuid)
                .setSenderDeviceId(senderDeviceId)
                .setReceiverUuid(receiverUuid)
                .setType(type)
                .setRefMessageId(refMessageId)
                .setEncryptedPayload(ByteString.copyFrom(encryptedPayload))
                .setSenderPublicKey(ByteString.copyFrom(signerEd.publicKey()))
                .setSignature(ByteString.copyFrom(sig))
                .setTimestamp(timestampMs);
        if (delCert != null) {
            builder.setSenderDelegationCert(delCert);
        }
        if (verCred != null) {
            builder.setSenderVerificationCredential(verCred);
        }
        return builder.build();
    }

    private static File resolveCert(String relativePath) {
        for (String prefix : new String[]{"", "../", "../../"}) {
            File f = new File(prefix + relativePath);
            if (f.exists()) {
                return f;
            }
        }
        throw new IllegalStateException("Certificado de teste nao encontrado: " + relativePath);
    }

    private static boolean isLocalRedisReachable() {
        String host = System.getenv().getOrDefault("ISPCHAT_TEST_REDIS_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("ISPCHAT_TEST_REDIS_PORT", "6379"));
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 150);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
