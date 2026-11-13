package br.dev.ispchat.principal;

import br.dev.ispchat.common.crypto.CanonicalTranscripts;
import br.dev.ispchat.common.crypto.CanonicalTranscripts.RawKeyPair;
import br.dev.ispchat.erp.ErpBridgeApplication;
import br.dev.ispchat.erp.webhook.WebhookDispatcher;
import br.dev.ispchat.grpc.AuthorizeCollaboratorRequest;
import br.dev.ispchat.grpc.ClientEnvelope;
import br.dev.ispchat.grpc.ClientStreamServiceGrpc;
import br.dev.ispchat.grpc.CollaboratorAuthCert;
import br.dev.ispchat.grpc.DeviceDelegationCert;
import br.dev.ispchat.grpc.DeviceRole;
import br.dev.ispchat.grpc.DhtClientServiceGrpc;
import br.dev.ispchat.grpc.DirectoryServiceGrpc;
import br.dev.ispchat.grpc.DocumentType;
import br.dev.ispchat.grpc.GetCredentialRequest;
import br.dev.ispchat.grpc.GovernanceAck;
import br.dev.ispchat.grpc.HomeNodeClaim;
import br.dev.ispchat.grpc.MessageType;
import br.dev.ispchat.grpc.MultiDeviceEncryptedPayload;
import br.dev.ispchat.grpc.PortAccountRequest;
import br.dev.ispchat.grpc.PrincipalGovernanceServiceGrpc;
import br.dev.ispchat.grpc.RegisterPartnerIspRequest;
import br.dev.ispchat.grpc.RegisterSessionRequest;
import br.dev.ispchat.grpc.SearchCnpjRequest;
import br.dev.ispchat.grpc.SearchCnpjResponse;
import br.dev.ispchat.grpc.ServerEnvelope;
import br.dev.ispchat.grpc.SubmitCpfRebindRequest;
import br.dev.ispchat.grpc.SubmitVerificationRequest;
import br.dev.ispchat.grpc.TurnCredentialRequest;
import br.dev.ispchat.grpc.TurnCredentialResponse;
import br.dev.ispchat.grpc.TurnCredentialServiceGrpc;
import br.dev.ispchat.grpc.UserVerificationConsent;
import br.dev.ispchat.grpc.VerificationBundle;
import br.dev.ispchat.grpc.VerificationCredential;
import br.dev.ispchat.node.NodeApplication;
import br.dev.ispchat.node.dht.DhtStoreService;
import br.dev.ispchat.node.dht.KademliaRoutingTable;
import br.dev.ispchat.node.grpc.GrpcChannelPool;
import br.dev.ispchat.node.messaging.OfflineMessageQueueService;
import br.dev.ispchat.principal.cli.OfflineGovernanceCli;
import br.dev.ispchat.principal.cli.OfflineGovernanceCli.PartnerBootstrapInput;
import br.dev.ispchat.principal.ledger.AuditLedgerService;
import br.dev.ispchat.principal.ledger.AuditLedgerService.AuditAccountabilityPayload;
import br.dev.ispchat.principal.ledger.AuditLedgerService.LedgerEntryRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.sun.net.httpserver.HttpServer;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite End-to-End Multi-Modulo exercitando CUJ 1 ate CUJ 6 integrando simultaneamente:
 * - :ispchat-common (Protocolo V2, AEAD Multi-Dispositivo, Argon2id, Escrow)
 * - :ispchat-principal (Governanca Raiz + PostgreSQL 17 + Fallback Home Node)
 * - 2 instancias federadas mTLS de :ispchat-node (ISP Alpha em localhost:<pA> e ISP Beta em 127.0.0.1:<pB>)
 * - :ispchat-erp-bridge (Sidecar HTTP REST + Webhook assinado HMAC-SHA256 com pos-2xx DELIVERY_ACK)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CrossModuleCujEndToEndTest {

    private static final String ISP_ALPHA_CNPJ = "11222333000181";
    private static final String ISP_BETA_CNPJ = "12ABC34501DE35";
    private static final String PARTNER_ALPHA_CPF = "52998224725";
    private static final String PARTNER_BETA_CPF = "39053344705";
    private static final String COLLAB_ANA_CPF = "11144477735";
    private static final String COLLAB_BRUNO_CPF = "12345678909";
    private static final String CARLOS_CPF = "93541134780";

    private static final String ERP_BEARER_SECRET = "alpha-isp-erp-secret-token-2026";
    private static final String ERP_WEBHOOK_HMAC = "alpha-isp-webhook-hmac-secret-2026";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private EmbeddedPostgres embeddedPostgres;
    private Path coldVaultDir;
    private byte[] rootEdPub;
    private byte[] rootEdPriv;
    private byte[] escrowXPriv;
    private RawKeyPair principalOnlineEd;

    private ConfigurableApplicationContext principalCtx;
    private ConfigurableApplicationContext nodeAlphaCtx;
    private ConfigurableApplicationContext nodeBetaCtx;
    private ConfigurableApplicationContext erpBridgeCtx;
    private HttpServer mockErpWebhookServer;

    private String principalAddress;
    private String alphaAddress;
    private String betaAddress;
    private String erpBaseUrl;
    private final BlockingQueue<Map<String, Object>> erpWebhookInbox = new LinkedBlockingQueue<>();

    private GrpcChannelPool adminMtlsPool;
    private ManagedChannel edgeToAlphaChannel;
    private ManagedChannel edgeToBetaChannel;
    private ManagedChannel edgeToPrincipalChannel;

    // Chaves e identidades compartilhadas ao longo das jornadas CUJ 1..6
    private RawKeyPair alphaCorpEd;
    private RawKeyPair alphaCorpX;
    private String alphaCorpUuid;
    private VerificationCredential alphaCorpCred;

    private RawKeyPair betaCorpEd;
    private RawKeyPair betaCorpX;

    private RawKeyPair partnerAlphaEd;
    private RawKeyPair partnerAlphaX;
    private String partnerAlphaUuid;

    private RawKeyPair partnerBetaEd;
    private RawKeyPair partnerBetaX;
    private String partnerBetaUuid;

    private RawKeyPair collabAnaEd;
    private String collabAnaUuid;
    private CollaboratorAuthCert collabAnaCert;

    private RawKeyPair collabBrunoEd;
    private String collabBrunoUuid;
    private CollaboratorAuthCert collabBrunoCert;

    // CUJ 1: Familiar nao verificado
    private RawKeyPair householdUnvEd;
    private RawKeyPair householdUnvX;
    private String householdUnvUuid;

    // CUJ 2..6: Assinante Carlos (Celular antigo -> Desktop pareado -> Celular novo em Re-Bind)
    private RawKeyPair carlosOldEd;
    private RawKeyPair carlosOldX;
    private String carlosOldUuid;
    private VerificationCredential carlosCredV1;
    private HomeNodeClaim carlosHomeClaimV1;

    private RawKeyPair carlosDesktopEd;
    private RawKeyPair carlosDesktopX;
    private DeviceDelegationCert carlosDesktopCert;

    // CUJ 5..6: Alice no Provedor Beta
    private RawKeyPair aliceBetaEd;
    private RawKeyPair aliceBetaX;
    private String aliceBetaUuid;

    @BeforeAll
    void bootFederatedCluster(@TempDir Path tempDir) throws Exception {
        this.coldVaultDir = tempDir.resolve("cold-root");
        ByteArrayOutputStream cliOut = new ByteArrayOutputStream();
        ByteArrayOutputStream cliErr = new ByteArrayOutputStream();

        assertEquals(0, OfflineGovernanceCli.runCli(
                new String[]{"--init-root", "--out-dir", coldVaultDir.toString()},
                new PrintStream(cliOut), new PrintStream(cliErr)));

        rootEdPub = Base64.getDecoder().decode(Files.readString(coldVaultDir.resolve("root_ed25519.pub")).trim());
        rootEdPriv = Base64.getDecoder().decode(Files.readString(coldVaultDir.resolve("root_ed25519.key")).trim());
        escrowXPriv = Base64.getDecoder().decode(Files.readString(coldVaultDir.resolve("escrow_x25519.key")).trim());
        principalOnlineEd = CanonicalTranscripts.generateEd25519KeyPair();

        Path pCertFile = tempDir.resolve("principal-cert.b64");
        assertEquals(0, OfflineGovernanceCli.runCli(
                new String[]{
                        "--sign-principal-cert",
                        "--root-key", coldVaultDir.resolve("root_ed25519.key").toString(),
                        "--principal-pub", Base64.getEncoder().encodeToString(principalOnlineEd.publicKey()),
                        "--escrow-pub", coldVaultDir.resolve("escrow_x25519.pub").toString(),
                        "--ttl-days", "365",
                        "--out", pCertFile.toString()
                },
                new PrintStream(cliOut), new PrintStream(cliErr)));
        String principalCertB64 = Files.readString(pCertFile).trim();

        // PostgreSQL 17 real
        String envJdbcUrl = System.getenv("ISPCHAT_TEST_JDBC_URL");
        String jdbcUrl;
        String dbUser;
        String dbPassword;
        if (envJdbcUrl != null && !envJdbcUrl.isBlank()) {
            jdbcUrl = envJdbcUrl.trim();
            dbUser = System.getenv().getOrDefault("ISPCHAT_TEST_DB_USER", "ispchat");
            dbPassword = System.getenv().getOrDefault("ISPCHAT_TEST_DB_PASSWORD", "ispchat");
            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setUrl(jdbcUrl);
            ds.setUser(dbUser);
            ds.setPassword(dbPassword);
            cleanSchema(ds);
        } else {
            embeddedPostgres = EmbeddedPostgres.builder().start();
            jdbcUrl = embeddedPostgres.getJdbcUrl("postgres", "postgres");
            dbUser = "postgres";
            dbPassword = "postgres";
        }

        boolean redisAvailable = isLocalRedisReachable();
        String redisHost = System.getenv().getOrDefault("ISPCHAT_TEST_REDIS_HOST", "127.0.0.1");
        String redisPort = System.getenv().getOrDefault("ISPCHAT_TEST_REDIS_PORT", "6379");

        int principalPort = reserveFreePort();
        int alphaPort = reserveFreePort();
        int betaPort = reserveFreePort();
        principalAddress = "localhost:" + principalPort;
        alphaAddress = "localhost:" + alphaPort;
        betaAddress = "127.0.0.1:" + betaPort; // Ambos localhost e 127.0.0.1 constam no SAN de dev-node.crt

        byte[] pepper = new byte[32];
        new SecureRandom().nextBytes(pepper);

        // 1. Sobe ispchat-principal (Governanca + Fallback Home Node)
        List<String> principalArgs = new ArrayList<>(List.of(
                "--grpc.server.enabled=true",
                "--grpc.server.port=" + principalPort,
                "--spring.datasource.url=" + jdbcUrl,
                "--spring.datasource.username=" + dbUser,
                "--spring.datasource.password=" + dbPassword,
                "--spring.flyway.enabled=true",
                "--ispchat.dht.node-id=01950000-0000-7000-8000-000000000000",
                "--ispchat.dht.srv-bootstrap-enabled=false",
                "--ispchat.governance.revocation-sync-enabled=false",
                "--ispchat.security.dev-mode=true",
                "--ispchat.security.ca-cert-path=../ca/ca.crt",
                "--ispchat.security.node-cert-path=../ca/dev-node.crt",
                "--ispchat.security.node-key-path=../ca/dev-node.key",
                "--ispchat.node.public-address=" + principalAddress,
                "--ispchat.principal.address=" + principalAddress,
                "--ispchat.principal.fallback-address=" + principalAddress,
                "--ispchat.root.public-key=" + Base64.getEncoder().encodeToString(rootEdPub),
                "--ispchat.principal.signing-private-key=" + Base64.getEncoder().encodeToString(principalOnlineEd.privateKey()),
                "--ispchat.principal.signing-cert-base64=" + principalCertB64,
                "--ispchat.principal.blind-index-pepper=" + Base64.getEncoder().encodeToString(pepper),
                "--ispchat.principal.argon2-memory-kib=65536",
                "--ispchat.principal.argon2-iterations=3",
                "--ispchat.principal.argon2-concurrency=4",
                "--ispchat.turn.static-auth-secret=principal-turn-secret"));
        appendRedisArgs(principalArgs, redisAvailable, redisHost, redisPort, 0);

        principalCtx = new SpringApplicationBuilder(PrincipalApplication.class)
                .web(WebApplicationType.NONE)
                .run(principalArgs.toArray(String[]::new));

        // 2. Sobe No ISP Alpha (localhost:<alphaPort>) e No ISP Beta (127.0.0.1:<betaPort>)
        nodeAlphaCtx = bootPartnerIspNode(
                alphaPort, alphaAddress, "01950000-0000-7000-8000-00000000000a",
                redisAvailable, redisHost, redisPort, 1);
        nodeBetaCtx = bootPartnerIspNode(
                betaPort, betaAddress, "01950000-0000-7000-8000-00000000000b",
                redisAvailable, redisHost, redisPort, 2);

        // Interliga tabelas de roteamento Kademlia entre Principal, ISP Alpha e ISP Beta
        KademliaRoutingTable pTable = principalCtx.getBean(KademliaRoutingTable.class);
        KademliaRoutingTable aTable = nodeAlphaCtx.getBean(KademliaRoutingTable.class);
        KademliaRoutingTable bTable = nodeBetaCtx.getBean(KademliaRoutingTable.class);

        pTable.registerOrUpdateNode(aTable.getLocalNodeId(), alphaAddress);
        pTable.registerOrUpdateNode(bTable.getLocalNodeId(), betaAddress);
        aTable.registerOrUpdateNode(pTable.getLocalNodeId(), principalAddress);
        aTable.registerOrUpdateNode(bTable.getLocalNodeId(), betaAddress);
        bTable.registerOrUpdateNode(pTable.getLocalNodeId(), principalAddress);
        bTable.registerOrUpdateNode(aTable.getLocalNodeId(), alphaAddress);

        // Canais gRPC (mTLS administrativo e TLS 1.3 de borda simulando apps moveis/desktops)
        adminMtlsPool = new GrpcChannelPool(true, "../ca/ca.crt", "../ca/dev-node.crt", "../ca/dev-node.key");
        edgeToAlphaChannel = buildEdgeTlsChannel("localhost", alphaPort);
        edgeToBetaChannel = buildEdgeTlsChannel("127.0.0.1", betaPort);
        edgeToPrincipalChannel = buildEdgeTlsChannel("localhost", principalPort);

        // 3. Registra Provedor Alpha e Provedor Beta no Principal via Raiz Nivel 0 Offline
        long nowMs = System.currentTimeMillis();
        alphaCorpEd = CanonicalTranscripts.generateEd25519KeyPair();
        alphaCorpX = CanonicalTranscripts.generateX25519KeyPair();
        alphaCorpUuid = CanonicalTranscripts.deriveIdentityUuidV7(alphaCorpEd.publicKey()).toString();
        partnerAlphaEd = CanonicalTranscripts.generateEd25519KeyPair();
        partnerAlphaX = CanonicalTranscripts.generateX25519KeyPair();
        partnerAlphaUuid = CanonicalTranscripts.deriveIdentityUuidV7(partnerAlphaEd.publicKey()).toString();

        RegisterPartnerIspRequest alphaBootReq = OfflineGovernanceCli.signPartnerIspBootstrap(
                rootEdPriv,
                ISP_ALPHA_CNPJ,
                "Provedor Alpha Fibra",
                alphaCorpEd.publicKey(),
                alphaCorpX.publicKey(),
                alphaAddress,
                List.of(new PartnerBootstrapInput(
                        PARTNER_ALPHA_CPF,
                        Base64.getEncoder().encodeToString(partnerAlphaEd.publicKey()),
                        Base64.getEncoder().encodeToString(partnerAlphaX.publicKey()))),
                nowMs);
        GovernanceAck alphaAck = PrincipalGovernanceServiceGrpc.newBlockingStub(adminMtlsPool.getChannel(principalAddress))
                .registerPartnerIsp(alphaBootReq);
        assertTrue(alphaAck.getAccepted(), alphaAck::getMessage);

        betaCorpEd = CanonicalTranscripts.generateEd25519KeyPair();
        betaCorpX = CanonicalTranscripts.generateX25519KeyPair();
        partnerBetaEd = CanonicalTranscripts.generateEd25519KeyPair();
        partnerBetaX = CanonicalTranscripts.generateX25519KeyPair();
        partnerBetaUuid = CanonicalTranscripts.deriveIdentityUuidV7(partnerBetaEd.publicKey()).toString();

        RegisterPartnerIspRequest betaBootReq = OfflineGovernanceCli.signPartnerIspBootstrap(
                rootEdPriv,
                ISP_BETA_CNPJ,
                "Provedor Beta Redes",
                betaCorpEd.publicKey(),
                betaCorpX.publicKey(),
                betaAddress,
                List.of(new PartnerBootstrapInput(
                        PARTNER_BETA_CPF,
                        Base64.getEncoder().encodeToString(partnerBetaEd.publicKey()),
                        Base64.getEncoder().encodeToString(partnerBetaX.publicKey()))),
                nowMs);
        GovernanceAck betaAck = PrincipalGovernanceServiceGrpc.newBlockingStub(adminMtlsPool.getChannel(principalAddress))
                .registerPartnerIsp(betaBootReq);
        assertTrue(betaAck.getAccepted(), betaAck::getMessage);

        // Obtem VerificationCredential corporativa CNPJ do Provedor Alpha via Edge Proxy
        byte[] getCorpSig = CanonicalTranscripts.signEd25519(
                alphaCorpEd.privateKey(),
                CanonicalTranscripts.buildGetOrRenewCredentialTranscript(alphaCorpUuid, alphaCorpUuid, nowMs));
        VerificationBundle alphaCorpBundle = PrincipalGovernanceServiceGrpc.newBlockingStub(edgeToAlphaChannel)
                .getOrRenewCredential(GetCredentialRequest.newBuilder()
                        .setUserUuid(alphaCorpUuid)
                        .setDeviceId(alphaCorpUuid)
                        .setTimestampMs(nowMs)
                        .setCallerPublicKey(ByteString.copyFrom(alphaCorpEd.publicKey()))
                        .setSignature(ByteString.copyFrom(getCorpSig))
                        .build());
        alphaCorpCred = alphaCorpBundle.getCredential();
        assertEquals(DocumentType.CNPJ, alphaCorpCred.getDocType());
        assertTrue(nodeAlphaCtx.getBean(DhtStoreService.class)
                .storeHomeNodeClaim(alphaCorpBundle.getHomeNodeClaim(), alphaCorpCred, alphaCorpEd.publicKey()));
    }

    @AfterAll
    void shutdownCluster() throws Exception {
        for (ManagedChannel ch : new ManagedChannel[]{edgeToAlphaChannel, edgeToBetaChannel, edgeToPrincipalChannel}) {
            if (ch != null) {
                ch.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            }
        }
        if (adminMtlsPool != null) {
            adminMtlsPool.shutdownAll();
        }
        if (erpBridgeCtx != null) {
            erpBridgeCtx.close();
        }
        if (mockErpWebhookServer != null) {
            mockErpWebhookServer.stop(0);
        }
        if (nodeAlphaCtx != null) {
            nodeAlphaCtx.close();
        }
        if (nodeBetaCtx != null) {
            nodeBetaCtx.close();
        }
        if (principalCtx != null) {
            principalCtx.close();
        }
        if (embeddedPostgres != null) {
            embeddedPostgres.close();
        }
    }

    @Test
    @Order(1)
    void cuj1_unverifiedHouseholdMemberJoinsWithFallbackClaimAndStrictCapabilityLimits() throws Exception {
        long nowMs = System.currentTimeMillis();
        householdUnvEd = CanonicalTranscripts.generateEd25519KeyPair();
        householdUnvX = CanonicalTranscripts.generateX25519KeyPair();
        householdUnvUuid = CanonicalTranscripts.deriveIdentityUuidV7(householdUnvEd.publicKey()).toString();

        // Assina HomeNodeClaim auto-assinado (epoch = 0) apontando para o Principal Fallback Node
        HomeNodeClaim fallbackClaim = HomeNodeClaim.newBuilder()
                .setUserUuid(householdUnvUuid)
                .setHomeNodeAddress(principalAddress)
                .setRoutingEpoch(0L)
                .setIssuedAtMs(nowMs)
                .setSignedByPrincipal(false)
                .build();
        fallbackClaim = fallbackClaim.toBuilder()
                .setClaimSignature(ByteString.copyFrom(CanonicalTranscripts.signEd25519(
                        householdUnvEd.privateKey(),
                        CanonicalTranscripts.buildHomeNodeClaimTranscript(fallbackClaim))))
                .build();

        byte[] dhtSig = CanonicalTranscripts.signEd25519(
                householdUnvEd.privateKey(),
                CanonicalTranscripts.buildDhtSessionV2Transcript(
                        householdUnvUuid,
                        householdUnvUuid,
                        alphaAddress,
                        householdUnvEd.publicKey(),
                        householdUnvEd.publicKey(),
                        householdUnvX.publicKey(),
                        nowMs));

        var dhtStub = DhtClientServiceGrpc.newBlockingStub(edgeToAlphaChannel);
        assertTrue(dhtStub.registerSession(RegisterSessionRequest.newBuilder()
                .setUserUuid(householdUnvUuid)
                .setDeviceId(householdUnvUuid)
                .setNodeAddress(alphaAddress)
                .setRootEd25519Pub(ByteString.copyFrom(householdUnvEd.publicKey()))
                .setPublicKey(ByteString.copyFrom(householdUnvEd.publicKey()))
                .setX25519PublicKey(ByteString.copyFrom(householdUnvX.publicKey()))
                .setSignature(ByteString.copyFrom(dhtSig))
                .setTimestamp(nowMs)
                .setHomeNodeClaim(fallbackClaim)
                .build()).getSuccess());

        // 1. Abre stream primaria no No Alpha -> aceita; credencial TURN = 600s
        StreamObserver<ClientEnvelope> primaryStream = ClientStreamServiceGrpc.newStub(edgeToAlphaChannel)
                .connect(new StreamObserver<>() {
                    @Override public void onNext(ServerEnvelope value) {}
                    @Override public void onError(Throwable t) {}
                    @Override public void onCompleted() {}
                });
        primaryStream.onNext(buildSignedV2Envelope(
                CanonicalTranscripts.generateTemporalUuidV7().toString(),
                householdUnvUuid, householdUnvUuid, householdUnvUuid,
                MessageType.AUTH_HANDSHAKE, "", new byte[0],
                householdUnvEd, null, null, nowMs));

        byte[] turnChallenge = CanonicalTranscripts.buildTurnCredentialRequestTranscript(
                householdUnvUuid, householdUnvUuid, nowMs);
        TurnCredentialResponse turnResp = TurnCredentialServiceGrpc.newBlockingStub(edgeToAlphaChannel)
                .getTurnCredentials(TurnCredentialRequest.newBuilder()
                        .setUserUuid(householdUnvUuid)
                        .setDeviceId(householdUnvUuid)
                        .setTimestampMs(nowMs)
                        .setCallerPublicKey(ByteString.copyFrom(householdUnvEd.publicKey()))
                        .setSignature(ByteString.copyFrom(
                                CanonicalTranscripts.signEd25519(householdUnvEd.privateKey(), turnChallenge)))
                        .build());
        assertEquals(600, turnResp.getTtlSeconds(), "Conta Unverified deve receber TURN com TTL curto de 600s");

        // 2. Tentativa de 2a stream concorrente no mesmo No -> rejeitada com RESOURCE_EXHAUSTED
        CountDownLatch secondStreamErrLatch = new CountDownLatch(1);
        AtomicReference<Throwable> secondStreamErr = new AtomicReference<>();
        StreamObserver<ClientEnvelope> secondStream = ClientStreamServiceGrpc.newStub(edgeToAlphaChannel)
                .connect(new StreamObserver<>() {
                    @Override public void onNext(ServerEnvelope value) {}
                    @Override public void onError(Throwable t) {
                        secondStreamErr.set(t);
                        secondStreamErrLatch.countDown();
                    }
                    @Override public void onCompleted() {}
                });
        secondStream.onNext(buildSignedV2Envelope(
                CanonicalTranscripts.generateTemporalUuidV7().toString(),
                householdUnvUuid, householdUnvUuid, householdUnvUuid,
                MessageType.AUTH_HANDSHAKE, "", new byte[0],
                householdUnvEd, null, null, System.currentTimeMillis()));
        assertTrue(secondStreamErrLatch.await(3, TimeUnit.SECONDS));
        assertEquals(Status.Code.RESOURCE_EXHAUSTED,
                ((StatusRuntimeException) secondStreamErr.get()).getStatus().getCode());

        // 3. Tentativa de delegar Desktop em conta Unverified -> rejeitada com PERMISSION_DENIED
        RawKeyPair unvDeskEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair unvDeskX = CanonicalTranscripts.generateX25519KeyPair();
        DeviceDelegationCert unvDeskCert = signDelegation(
                householdUnvEd, unvDeskEd, unvDeskX, DeviceRole.DESKTOP, System.currentTimeMillis());

        CountDownLatch delErrLatch = new CountDownLatch(1);
        AtomicReference<Throwable> delErr = new AtomicReference<>();
        StreamObserver<ClientEnvelope> delStream = ClientStreamServiceGrpc.newStub(edgeToAlphaChannel)
                .connect(new StreamObserver<>() {
                    @Override public void onNext(ServerEnvelope value) {}
                    @Override public void onError(Throwable t) {
                        delErr.set(t);
                        delErrLatch.countDown();
                    }
                    @Override public void onCompleted() {}
                });
        delStream.onNext(buildSignedV2Envelope(
                CanonicalTranscripts.generateTemporalUuidV7().toString(),
                householdUnvUuid, unvDeskCert.getDeviceId(), householdUnvUuid,
                MessageType.AUTH_HANDSHAKE, "", new byte[0],
                unvDeskEd, unvDeskCert, null, System.currentTimeMillis()));
        assertTrue(delErrLatch.await(3, TimeUnit.SECONDS));
        assertEquals(Status.Code.PERMISSION_DENIED,
                ((StatusRuntimeException) delErr.get()).getStatus().getCode());

        primaryStream.onCompleted();
    }

    @Test
    @Order(2)
    void cuj2_collaboratorVerifiesSubscriberCarlosThroughPartnerIspEdgeProxy() {
        long nowMs = System.currentTimeMillis();
        var alphaEdgeGov = PrincipalGovernanceServiceGrpc.newBlockingStub(edgeToAlphaChannel);

        // Socio do ISP Alpha autoriza Colaboradora Ana via EdgeGovernanceProxyGrpcService -> mTLS -> Principal
        collabAnaEd = CanonicalTranscripts.generateEd25519KeyPair();
        collabAnaUuid = CanonicalTranscripts.deriveIdentityUuidV7(collabAnaEd.publicKey()).toString();
        CollaboratorAuthCert unsignedAna = CollaboratorAuthCert.newBuilder()
                .setCertId(CanonicalTranscripts.generateTemporalUuidV7().toString())
                .setIspCnpj(ISP_ALPHA_CNPJ)
                .setPartnerUuid(partnerAlphaUuid)
                .setCollaboratorUuid(collabAnaUuid)
                .setCollabCpfSha256(ByteString.copyFrom(CanonicalTranscripts.sha256Utf8(COLLAB_ANA_CPF)))
                .setCollabEd25519Pub(ByteString.copyFrom(collabAnaEd.publicKey()))
                .setIssuedAtMs(nowMs - 1_000L)
                .setExpiresAtMs(nowMs + TimeUnit.DAYS.toMillis(180))
                .build();
        byte[] partnerSig = CanonicalTranscripts.signEd25519(
                partnerAlphaEd.privateKey(),
                CanonicalTranscripts.buildCollaboratorAuthTranscript(unsignedAna));
        CollaboratorAuthCert signedAna = unsignedAna.toBuilder()
                .setPartnerSignature(ByteString.copyFrom(partnerSig))
                .build();

        collabAnaCert = alphaEdgeGov.authorizeCollaborator(AuthorizeCollaboratorRequest.newBuilder()
                .setCert(signedAna)
                .setCollaboratorCpf(COLLAB_ANA_CPF)
                .build());
        assertEquals(collabAnaUuid, collabAnaCert.getCollaboratorUuid());

        // Assinante Carlos assina ISPCHAT-VERIFY-REQ-V1 no celular e Colaboradora Ana submete SubmitVerification
        carlosOldEd = CanonicalTranscripts.generateEd25519KeyPair();
        carlosOldX = CanonicalTranscripts.generateX25519KeyPair();
        carlosOldUuid = CanonicalTranscripts.deriveIdentityUuidV7(carlosOldEd.publicKey()).toString();
        byte[] carlosCpfSha256 = CanonicalTranscripts.sha256Utf8(CARLOS_CPF);

        UserVerificationConsent unsignedConsent = UserVerificationConsent.newBuilder()
                .setUserUuid(carlosOldUuid)
                .setUserEd25519Pub(ByteString.copyFrom(carlosOldEd.publicKey()))
                .setUserX25519Pub(ByteString.copyFrom(carlosOldX.publicKey()))
                .setDocType(DocumentType.CPF)
                .setDocSha256(ByteString.copyFrom(carlosCpfSha256))
                .setIspCnpj(ISP_ALPHA_CNPJ)
                .setTimestampMs(nowMs)
                .build();
        byte[] consentSig = CanonicalTranscripts.signEd25519(
                carlosOldEd.privateKey(),
                CanonicalTranscripts.buildUserVerificationConsentTranscript(unsignedConsent));
        UserVerificationConsent consent = unsignedConsent.toBuilder()
                .setUserSignature(ByteString.copyFrom(consentSig))
                .build();

        byte[] collabAttestSig = CanonicalTranscripts.signEd25519(
                collabAnaEd.privateKey(),
                CanonicalTranscripts.buildCollaboratorUserAttestationTranscript(
                        ISP_ALPHA_CNPJ, collabAnaUuid, carlosOldUuid,
                        DocumentType.CPF, carlosCpfSha256,
                        alphaAddress, consentSig, nowMs));

        VerificationBundle submittedBundle = alphaEdgeGov.submitVerification(SubmitVerificationRequest.newBuilder()
                .setIspCnpj(ISP_ALPHA_CNPJ)
                .setCollaboratorCert(collabAnaCert)
                .setUserConsent(consent)
                .setNormalizedDocument(CARLOS_CPF)
                .setHomeNodeAddress(alphaAddress)
                .setAttestedAtMs(nowMs)
                .setCollaboratorSignature(ByteString.copyFrom(collabAttestSig))
                .build());

        assertTrue(submittedBundle.hasCredential());
        assertEquals(alphaAddress, submittedBundle.getCredential().getHomeNodeAddress());
        assertEquals(1L, submittedBundle.getCredential().getRoutingEpoch());

        // Celular de Carlos recupera credencial via GetOrRenewCredential e recebe TURN de 3600s
        byte[] getSig = CanonicalTranscripts.signEd25519(
                carlosOldEd.privateKey(),
                CanonicalTranscripts.buildGetOrRenewCredentialTranscript(carlosOldUuid, carlosOldUuid, nowMs));
        VerificationBundle fetchedBundle = alphaEdgeGov.getOrRenewCredential(GetCredentialRequest.newBuilder()
                .setUserUuid(carlosOldUuid)
                .setDeviceId(carlosOldUuid)
                .setTimestampMs(nowMs)
                .setCallerPublicKey(ByteString.copyFrom(carlosOldEd.publicKey()))
                .setSignature(ByteString.copyFrom(getSig))
                .build());
        carlosCredV1 = fetchedBundle.getCredential();
        carlosHomeClaimV1 = fetchedBundle.getHomeNodeClaim();
        assertEquals(carlosOldUuid, carlosCredV1.getUserUuid());

        byte[] turnSig = CanonicalTranscripts.signEd25519(
                carlosOldEd.privateKey(),
                CanonicalTranscripts.buildTurnCredentialRequestTranscript(carlosOldUuid, carlosOldUuid, nowMs));
        TurnCredentialResponse verifiedTurn = TurnCredentialServiceGrpc.newBlockingStub(edgeToAlphaChannel)
                .getTurnCredentials(TurnCredentialRequest.newBuilder()
                        .setUserUuid(carlosOldUuid)
                        .setDeviceId(carlosOldUuid)
                        .setTimestampMs(nowMs)
                        .setCallerPublicKey(ByteString.copyFrom(carlosOldEd.publicKey()))
                        .setSignature(ByteString.copyFrom(turnSig))
                        .setVerificationCredential(carlosCredV1)
                        .build());
        assertEquals(3600, verifiedTurn.getTtlSeconds(), "Assinante Verificado deve receber TURN de 3600s");
    }

    @Test
    @Order(3)
    void cuj3_verifiedSubscriberPairsDesktopViaQrAndSynchronizesMultiDeviceMessages() throws Exception {
        long nowMs = System.currentTimeMillis();
        carlosDesktopEd = CanonicalTranscripts.generateEd25519KeyPair();
        carlosDesktopX = CanonicalTranscripts.generateX25519KeyPair();
        carlosDesktopCert = signDelegation(carlosOldEd, carlosDesktopEd, carlosDesktopX, DeviceRole.DESKTOP, nowMs);

        // Registra Mobile Raiz e Desktop Delegado na DHT do No Alpha (replicando tambem ao peer Beta)
        registerDhtSession(edgeToAlphaChannel, alphaAddress, carlosOldUuid, carlosOldUuid,
                carlosOldEd.publicKey(), carlosOldEd, carlosOldX, null, carlosCredV1, carlosHomeClaimV1, nowMs);
        registerDhtSession(edgeToAlphaChannel, alphaAddress, carlosOldUuid, carlosDesktopCert.getDeviceId(),
                carlosOldEd.publicKey(), carlosDesktopEd, carlosDesktopX, carlosDesktopCert, carlosCredV1, carlosHomeClaimV1, nowMs);

        BlockingQueue<ServerEnvelope> carlosMobileInbox = new LinkedBlockingQueue<>();
        BlockingQueue<ServerEnvelope> carlosDesktopInbox = new LinkedBlockingQueue<>();

        StreamObserver<ClientEnvelope> carlosMobileStream = openStream(
                edgeToAlphaChannel, carlosOldUuid, carlosOldUuid, carlosOldEd, null, carlosCredV1, carlosMobileInbox);
        StreamObserver<ClientEnvelope> carlosDesktopStream = openStream(
                edgeToAlphaChannel, carlosOldUuid, carlosDesktopCert.getDeviceId(),
                carlosDesktopEd, carlosDesktopCert, carlosCredV1, carlosDesktopInbox);

        // Familiar (CUJ 1) envia mensagem E2E embrulhada para Mobile + Desktop de Carlos
        BlockingQueue<ServerEnvelope> householdInbox = new LinkedBlockingQueue<>();
        StreamObserver<ClientEnvelope> householdStream = openStream(
                edgeToAlphaChannel, householdUnvUuid, householdUnvUuid, householdUnvEd, null, null, householdInbox);

        String familyMsgId = CanonicalTranscripts.generateTemporalUuidV7().toString();
        String familyText = "Oi Carlos, a internet fibra ja foi instalada!";
        Map<String, byte[]> carlosTargets = new LinkedHashMap<>();
        carlosTargets.put(carlosOldUuid, carlosOldX.publicKey());
        carlosTargets.put(carlosDesktopCert.getDeviceId(), carlosDesktopX.publicKey());
        MultiDeviceEncryptedPayload encryptedToCarlos = CanonicalTranscripts.encryptMultiDevicePayload(
                familyMsgId, householdUnvUuid, carlosOldUuid,
                familyText.getBytes(StandardCharsets.UTF_8), carlosTargets);

        householdStream.onNext(buildSignedV2Envelope(
                familyMsgId, householdUnvUuid, householdUnvUuid, carlosOldUuid,
                MessageType.TEXT, "", encryptedToCarlos.toByteArray(),
                householdUnvEd, null, null, System.currentTimeMillis()));

        ServerEnvelope mobEnv = carlosMobileInbox.poll(3, TimeUnit.SECONDS);
        ServerEnvelope deskEnv = carlosDesktopInbox.poll(3, TimeUnit.SECONDS);
        assertNotNull(mobEnv);
        assertNotNull(deskEnv);
        assertEquals(familyText, new String(CanonicalTranscripts.decryptMultiDevicePayload(
                familyMsgId, householdUnvUuid, carlosOldUuid, carlosOldUuid,
                carlosOldX.privateKey(), MultiDeviceEncryptedPayload.parseFrom(mobEnv.getEncryptedPayload())),
                StandardCharsets.UTF_8));
        assertEquals(familyText, new String(CanonicalTranscripts.decryptMultiDevicePayload(
                familyMsgId, householdUnvUuid, carlosOldUuid, carlosDesktopCert.getDeviceId(),
                carlosDesktopX.privateKey(), MultiDeviceEncryptedPayload.parseFrom(deskEnv.getEncryptedPayload())),
                StandardCharsets.UTF_8));

        // Carlos responde pelo Celular embrulhando tambem para seu proprio Desktop (sincronia de enviados)
        String replyId = CanonicalTranscripts.generateTemporalUuidV7().toString();
        String replyText = "Sim! Ja estou usando no notebook tambem.";
        Map<String, byte[]> replyTargets = new LinkedHashMap<>();
        replyTargets.put(householdUnvUuid, householdUnvX.publicKey());
        replyTargets.put(carlosDesktopCert.getDeviceId(), carlosDesktopX.publicKey());
        MultiDeviceEncryptedPayload encryptedReply = CanonicalTranscripts.encryptMultiDevicePayload(
                replyId, carlosOldUuid, householdUnvUuid, replyText.getBytes(StandardCharsets.UTF_8), replyTargets);

        carlosMobileStream.onNext(buildSignedV2Envelope(
                replyId, carlosOldUuid, carlosOldUuid, householdUnvUuid,
                MessageType.TEXT, "", encryptedReply.toByteArray(),
                carlosOldEd, null, carlosCredV1, System.currentTimeMillis()));

        ServerEnvelope syncedAtDesktop = carlosDesktopInbox.poll(3, TimeUnit.SECONDS);
        assertNotNull(syncedAtDesktop, "Desktop irmao de Carlos deve receber copia sincronizada com receiver_uuid preenchido");
        assertEquals(householdUnvUuid, syncedAtDesktop.getReceiverUuid());
        assertEquals(replyText, new String(CanonicalTranscripts.decryptMultiDevicePayload(
                replyId, carlosOldUuid, householdUnvUuid, carlosDesktopCert.getDeviceId(),
                carlosDesktopX.privateKey(), MultiDeviceEncryptedPayload.parseFrom(syncedAtDesktop.getEncryptedPayload())),
                StandardCharsets.UTF_8));

        householdStream.onCompleted();
        carlosMobileStream.onCompleted();
        carlosDesktopStream.onCompleted();
    }

    @Test
    @Order(4)
    void cuj4_corporateCnpjDirectorySearchAndErpBridgeBillingAndSupportFlow() throws Exception {
        // 1. Carlos pesquisa o CNPJ corporativo no Diretorio Publico via proxy do No Alpha
        SearchCnpjResponse dirResp = DirectoryServiceGrpc.newBlockingStub(edgeToAlphaChannel)
                .searchVerifiedCnpj(SearchCnpjRequest.newBuilder()
                        .setQuery("Provedor Alpha")
                        .setLimit(10)
                        .build());
        assertEquals(1, dirResp.getEntriesCount());
        assertEquals(alphaCorpUuid, dirResp.getEntries(0).getCorporateUuid());
        assertEquals(ISP_ALPHA_CNPJ, dirResp.getEntries(0).getCnpj());

        // 2. Sobe webhook mock do ERP + ErpBridgeApplication pareado na conta CNPJ do Provedor Alpha
        mockErpWebhookServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockErpWebhookServer.createContext("/ixc/webhook", exchange -> {
            byte[] rawBody = exchange.getRequestBody().readAllBytes();
            String expectedHmac = WebhookDispatcher.computeSignatureHeader(
                    ERP_WEBHOOK_HMAC.getBytes(StandardCharsets.UTF_8), rawBody);
            if (!expectedHmac.equals(exchange.getRequestHeaders().getFirst("X-IspChat-Signature"))) {
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }
            erpWebhookInbox.add(objectMapper.readValue(rawBody, new TypeReference<>() {}));
            byte[] ok = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, ok.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(ok);
            }
        });
        mockErpWebhookServer.start();
        String webhookUrl = "http://127.0.0.1:" + mockErpWebhookServer.getAddress().getPort() + "/ixc/webhook";

        RawKeyPair erpEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair erpX = CanonicalTranscripts.generateX25519KeyPair();
        DeviceDelegationCert erpCert = signDelegation(
                alphaCorpEd, erpEd, erpX, DeviceRole.ERP_API, System.currentTimeMillis());

        erpBridgeCtx = new SpringApplicationBuilder(ErpBridgeApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--spring.main.web-application-type=servlet",
                        "--server.address=127.0.0.1",
                        "--server.port=0",
                        "--spring.autoconfigure.exclude="
                                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
                        "--ispchat.erp.node-address=" + alphaAddress,
                        "--ispchat.erp.ca-cert-path=../ca/ca.crt",
                        "--ispchat.root.public-key=" + Base64.getEncoder().encodeToString(rootEdPub),
                        "--ispchat.erp.api-bearer-secret=" + ERP_BEARER_SECRET,
                        "--ispchat.erp.webhook-url=" + webhookUrl,
                        "--ispchat.erp.webhook-hmac-secret=" + ERP_WEBHOOK_HMAC,
                        "--ispchat.erp.device-ed25519-private-key=" + Base64.getEncoder().encodeToString(erpEd.privateKey()),
                        "--ispchat.erp.device-x25519-private-key=" + Base64.getEncoder().encodeToString(erpX.privateKey()),
                        "--ispchat.erp.delegation-cert-base64=" + Base64.getEncoder().encodeToString(erpCert.toByteArray()),
                        "--ispchat.erp.verification-credential-base64=" + Base64.getEncoder().encodeToString(alphaCorpCred.toByteArray()));

        int erpPort = ((WebServerApplicationContext) erpBridgeCtx).getWebServer().getPort();
        erpBaseUrl = "http://127.0.0.1:" + erpPort;

        // Conecta celular de Carlos ao vivo e envia fatura via POST /api/v1/messages
        BlockingQueue<ServerEnvelope> carlosInbox = new LinkedBlockingQueue<>();
        StreamObserver<ClientEnvelope> carlosStream = openStream(
                edgeToAlphaChannel, carlosOldUuid, carlosOldUuid, carlosOldEd, null, carlosCredV1, carlosInbox);

        String invoiceText = "Provedor Alpha Fibra: Fatura R$ 109,90 disponivel.";
        HttpResponse<String> erpSendResp = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(erpBaseUrl + "/api/v1/messages"))
                        .header("Authorization", "Bearer " + ERP_BEARER_SECRET)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of(
                                "recipientUuid", carlosOldUuid,
                                "text", invoiceText,
                                "idempotencyKey", "cuj4-inv-01",
                                "erpAdapter", "IXC"))))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(202, erpSendResp.statusCode(), erpSendResp::body);

        ServerEnvelope receivedInvoice = carlosInbox.poll(3, TimeUnit.SECONDS);
        assertNotNull(receivedInvoice);
        assertEquals(alphaCorpUuid, receivedInvoice.getSenderUuid());
        assertTrue(receivedInvoice.hasSenderVerificationCredential());
        assertEquals(ISP_ALPHA_CNPJ, receivedInvoice.getSenderVerificationCredential().getPublicCnpj());
        assertEquals(invoiceText, new String(CanonicalTranscripts.decryptMultiDevicePayload(
                receivedInvoice.getMessageId(), alphaCorpUuid, carlosOldUuid, carlosOldUuid,
                carlosOldX.privateKey(), MultiDeviceEncryptedPayload.parseFrom(receivedInvoice.getEncryptedPayload())),
                StandardCharsets.UTF_8));

        // Carlos responde pedindo suporte -> Webhook recebe com senderVerified=true e esvazia fila via DELIVERY_ACK
        String ticketMsgId = CanonicalTranscripts.generateTemporalUuidV7().toString();
        String ticketText = "Preciso abrir chamado para trocar roteador Wi-Fi 6.";
        Map<String, byte[]> erpTargets = Map.of(erpCert.getDeviceId(), erpX.publicKey());
        MultiDeviceEncryptedPayload encryptedTicket = CanonicalTranscripts.encryptMultiDevicePayload(
                ticketMsgId, carlosOldUuid, alphaCorpUuid, ticketText.getBytes(StandardCharsets.UTF_8), erpTargets);

        carlosStream.onNext(buildSignedV2Envelope(
                ticketMsgId, carlosOldUuid, carlosOldUuid, alphaCorpUuid,
                MessageType.TEXT, "", encryptedTicket.toByteArray(),
                carlosOldEd, null, carlosCredV1, System.currentTimeMillis()));

        Map<String, Object> webhookTicket = erpWebhookInbox.poll(5, TimeUnit.SECONDS);
        assertNotNull(webhookTicket);
        assertEquals(ticketMsgId, webhookTicket.get("messageId"));
        assertEquals(carlosOldUuid, webhookTicket.get("senderUuid"));
        assertEquals(Boolean.TRUE, webhookTicket.get("senderVerified"));
        assertEquals(ticketText, webhookTicket.get("text"));

        OfflineMessageQueueService alphaQueue = nodeAlphaCtx.getBean(OfflineMessageQueueService.class);
        long deadline = System.currentTimeMillis() + 3_000L;
        while (!alphaQueue.peekPendingMessages(alphaCorpUuid, erpCert.getDeviceId()).isEmpty()
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
        assertTrue(alphaQueue.peekPendingMessages(alphaCorpUuid, erpCert.getDeviceId()).isEmpty());

        carlosStream.onCompleted();
    }

    @Test
    @Order(5)
    void cuj5_crossIspOfflineStoreAndForwardAndSignedSubscriberPortabilityWithRedirectTombstone() throws Exception {
        long nowMs = System.currentTimeMillis();
        Thread.sleep(100);

        // 1. Alice conecta no Provedor Beta (127.0.0.1:<betaPort>) e envia mensagem para Carlos (Home Node = ISP Alpha)
        aliceBetaEd = CanonicalTranscripts.generateEd25519KeyPair();
        aliceBetaX = CanonicalTranscripts.generateX25519KeyPair();
        aliceBetaUuid = CanonicalTranscripts.deriveIdentityUuidV7(aliceBetaEd.publicKey()).toString();
        registerDhtSession(edgeToBetaChannel, betaAddress, aliceBetaUuid, aliceBetaUuid,
                aliceBetaEd.publicKey(), aliceBetaEd, aliceBetaX, null, null, null, nowMs);

        BlockingQueue<ServerEnvelope> aliceInbox = new LinkedBlockingQueue<>();
        StreamObserver<ClientEnvelope> aliceStream = openStream(
                edgeToBetaChannel, aliceBetaUuid, aliceBetaUuid, aliceBetaEd, null, null, aliceInbox);

        String crossIspMsgId = CanonicalTranscripts.generateTemporalUuidV7().toString();
        String crossIspText = "Oi Carlos, te mandei mensagem pelo Provedor Beta enquanto voce estava offline!";
        Map<String, byte[]> carlosTargets = new LinkedHashMap<>();
        carlosTargets.put(carlosOldUuid, carlosOldX.publicKey());
        carlosTargets.put(carlosDesktopCert.getDeviceId(), carlosDesktopX.publicKey());
        MultiDeviceEncryptedPayload crossEncrypted = CanonicalTranscripts.encryptMultiDevicePayload(
                crossIspMsgId, aliceBetaUuid, carlosOldUuid, crossIspText.getBytes(StandardCharsets.UTF_8), carlosTargets);

        aliceStream.onNext(buildSignedV2Envelope(
                crossIspMsgId, aliceBetaUuid, aliceBetaUuid, carlosOldUuid,
                MessageType.TEXT, "", crossEncrypted.toByteArray(),
                aliceBetaEd, null, null, System.currentTimeMillis()));

        // Alice recebe SERVER_QUEUED_ACK vindo do encaminhamento mTLS ISP Beta -> ISP Alpha!
        ServerEnvelope queuedAck = aliceInbox.poll(3, TimeUnit.SECONDS);
        assertNotNull(queuedAck, "Alice no ISP Beta deve receber SERVER_QUEUED_ACK do Home Node ISP Alpha");
        assertEquals(MessageType.SERVER_QUEUED_ACK, queuedAck.getType());
        assertEquals(crossIspMsgId, queuedAck.getRefMessageId());

        OfflineMessageQueueService alphaQueue = nodeAlphaCtx.getBean(OfflineMessageQueueService.class);
        assertEquals(1, alphaQueue.peekPendingMessages(carlosOldUuid, carlosOldUuid).size());
        // Desktop estava offline durante: (1) fatura ERP em CUJ 4, (2) sync de enviado do chamado em CUJ 4, e (3) msg cross-ISP de Alice em CUJ 5
        assertEquals(3, alphaQueue.peekPendingMessages(carlosOldUuid, carlosDesktopCert.getDeviceId()).size());

        // Carlos reconecta no ISP Alpha, recebe a mensagem pendente e confirma com DELIVERY_ACK
        BlockingQueue<ServerEnvelope> carlosAlphaInbox = new LinkedBlockingQueue<>();
        StreamObserver<ClientEnvelope> carlosAlphaStream = openStream(
                edgeToAlphaChannel, carlosOldUuid, carlosOldUuid, carlosOldEd, null, carlosCredV1, carlosAlphaInbox);
        ServerEnvelope deliveredOffline = carlosAlphaInbox.poll(3, TimeUnit.SECONDS);
        assertNotNull(deliveredOffline);
        assertEquals(crossIspMsgId, deliveredOffline.getMessageId());

        carlosAlphaStream.onNext(buildSignedV2Envelope(
                CanonicalTranscripts.generateTemporalUuidV7().toString(),
                carlosOldUuid, carlosOldUuid, carlosOldUuid,
                MessageType.DELIVERY_ACK, crossIspMsgId, new byte[0],
                carlosOldEd, null, null, System.currentTimeMillis()));
        carlosAlphaStream.onCompleted();
        Thread.sleep(100);

        // 2. Portabilidade assinada: Socio do ISP Beta autoriza Colaborador Bruno e Carlos migra para o ISP Beta (epoch = 2)
        var betaEdgeGov = PrincipalGovernanceServiceGrpc.newBlockingStub(edgeToBetaChannel);
        collabBrunoEd = CanonicalTranscripts.generateEd25519KeyPair();
        collabBrunoUuid = CanonicalTranscripts.deriveIdentityUuidV7(collabBrunoEd.publicKey()).toString();
        CollaboratorAuthCert unsignedBruno = CollaboratorAuthCert.newBuilder()
                .setCertId(CanonicalTranscripts.generateTemporalUuidV7().toString())
                .setIspCnpj(ISP_BETA_CNPJ)
                .setPartnerUuid(partnerBetaUuid)
                .setCollaboratorUuid(collabBrunoUuid)
                .setCollabCpfSha256(ByteString.copyFrom(CanonicalTranscripts.sha256Utf8(COLLAB_BRUNO_CPF)))
                .setCollabEd25519Pub(ByteString.copyFrom(collabBrunoEd.publicKey()))
                .setIssuedAtMs(nowMs - 1_000L)
                .setExpiresAtMs(nowMs + TimeUnit.DAYS.toMillis(180))
                .build();
        CollaboratorAuthCert signedBruno = unsignedBruno.toBuilder()
                .setPartnerSignature(ByteString.copyFrom(CanonicalTranscripts.signEd25519(
                        partnerBetaEd.privateKey(),
                        CanonicalTranscripts.buildCollaboratorAuthTranscript(unsignedBruno))))
                .build();
        collabBrunoCert = betaEdgeGov.authorizeCollaborator(AuthorizeCollaboratorRequest.newBuilder()
                .setCert(signedBruno)
                .setCollaboratorCpf(COLLAB_BRUNO_CPF)
                .build());

        long portTs = System.currentTimeMillis();
        byte[] portClaimTranscript = CanonicalTranscripts.buildPortAccountTranscript(
                carlosOldUuid, betaAddress, ISP_BETA_CNPJ, 2L, portTs);
        byte[] carlosPortSig = CanonicalTranscripts.signEd25519(carlosOldEd.privateKey(), portClaimTranscript);
        byte[] brunoPortSig = CanonicalTranscripts.signEd25519(collabBrunoEd.privateKey(), portClaimTranscript);

        VerificationBundle portedBundle = betaEdgeGov.portAccount(PortAccountRequest.newBuilder()
                .setUserUuid(carlosOldUuid)
                .setTargetHomeNodeAddress(betaAddress)
                .setTargetIspCnpj(ISP_BETA_CNPJ)
                .setExpectedRoutingEpoch(2L)
                .setTimestampMs(portTs)
                .setRootSignature(ByteString.copyFrom(carlosPortSig))
                .setTargetCollaboratorCert(collabBrunoCert)
                .setTargetCollaboratorSignature(ByteString.copyFrom(brunoPortSig))
                .build());

        assertEquals(2L, portedBundle.getCredential().getRoutingEpoch());
        assertEquals(betaAddress, portedBundle.getHomeNodeClaim().getHomeNodeAddress());

        // ISP Alpha registra tombstone de redirecionamento de 30 dias apontando para o ISP Beta
        nodeAlphaCtx.getBean(DhtStoreService.class).storeRedirectTombstone(carlosOldUuid, betaAddress);
        registerDhtSession(edgeToBetaChannel, betaAddress, carlosOldUuid, carlosDesktopCert.getDeviceId(),
                carlosOldEd.publicKey(), carlosDesktopEd, carlosDesktopX,
                carlosDesktopCert, portedBundle.getCredential(), portedBundle.getHomeNodeClaim(), System.currentTimeMillis());

        // Familiar conectado no ISP Alpha envia mensagem offline para Carlos -> ISP Alpha segue o tombstone ate a fila do ISP Beta!
        BlockingQueue<ServerEnvelope> famInbox = new LinkedBlockingQueue<>();
        StreamObserver<ClientEnvelope> famStream = openStream(
                edgeToAlphaChannel, householdUnvUuid, householdUnvUuid, householdUnvEd, null, null, famInbox);

        String redirectedMsgId = CanonicalTranscripts.generateTemporalUuidV7().toString();
        MultiDeviceEncryptedPayload redirectedEnc = CanonicalTranscripts.encryptMultiDevicePayload(
                redirectedMsgId, householdUnvUuid, carlosOldUuid,
                "Mensagem apos portabilidade!".getBytes(StandardCharsets.UTF_8), carlosTargets);
        famStream.onNext(buildSignedV2Envelope(
                redirectedMsgId, householdUnvUuid, householdUnvUuid, carlosOldUuid,
                MessageType.TEXT, "", redirectedEnc.toByteArray(),
                householdUnvEd, null, null, System.currentTimeMillis()));

        ServerEnvelope redirectQueuedAck = famInbox.poll(3, TimeUnit.SECONDS);
        assertNotNull(redirectQueuedAck);
        assertEquals(MessageType.SERVER_QUEUED_ACK, redirectQueuedAck.getType());
        assertTrue(nodeBetaCtx.getBean(OfflineMessageQueueService.class).getStoredMessageCount(carlosOldUuid) >= 1,
                "Mensagem enviada ao antigo No Alpha deve ser encaminhada pelo tombstone a fila do novo Home Node Beta");

        famStream.onCompleted();
        aliceStream.onCompleted();
    }

    @Test
    @Order(6)
    void cuj6_lostDeviceImmediateCpfRebindDisconnectsOldStreamNotifiesPeersAndVerifiesEscrowLedger() throws Exception {
        // 1. Desktop antigo de Carlos esta conectado ao vivo no ISP Beta quando Carlos perde o celular
        BlockingQueue<ServerEnvelope> oldDesktopInbox = new LinkedBlockingQueue<>();
        CountDownLatch oldDesktopClosedLatch = new CountDownLatch(1);
        StreamObserver<ClientEnvelope> oldDesktopStream = ClientStreamServiceGrpc.newStub(edgeToBetaChannel)
                .connect(new StreamObserver<>() {
                    @Override
                    public void onNext(ServerEnvelope value) {
                        oldDesktopInbox.add(value);
                    }

                    @Override
                    public void onError(Throwable t) {
                        oldDesktopClosedLatch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        oldDesktopClosedLatch.countDown();
                    }
                });
        oldDesktopStream.onNext(buildSignedV2Envelope(
                CanonicalTranscripts.generateTemporalUuidV7().toString(),
                carlosOldUuid, carlosDesktopCert.getDeviceId(), carlosOldUuid,
                MessageType.AUTH_HANDSHAKE, "", new byte[0],
                carlosDesktopEd, carlosDesktopCert, carlosCredV1, System.currentTimeMillis()));

        // 2. Carlos instala o app em celular novo (carlosNewUuid) e Colaborador Bruno executa SubmitCpfRebind (0h hold)
        RawKeyPair carlosNewEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair carlosNewX = CanonicalTranscripts.generateX25519KeyPair();
        String carlosNewUuid = CanonicalTranscripts.deriveIdentityUuidV7(carlosNewEd.publicKey()).toString();
        long rebindTs = System.currentTimeMillis();
        byte[] cpfSha256 = CanonicalTranscripts.sha256Utf8(CARLOS_CPF);

        UserVerificationConsent unsignedNewConsent = UserVerificationConsent.newBuilder()
                .setUserUuid(carlosNewUuid)
                .setUserEd25519Pub(ByteString.copyFrom(carlosNewEd.publicKey()))
                .setUserX25519Pub(ByteString.copyFrom(carlosNewX.publicKey()))
                .setDocType(DocumentType.CPF)
                .setDocSha256(ByteString.copyFrom(cpfSha256))
                .setIspCnpj(ISP_BETA_CNPJ)
                .setTimestampMs(rebindTs)
                .build();
        byte[] newConsentSig = CanonicalTranscripts.signEd25519(
                carlosNewEd.privateKey(),
                CanonicalTranscripts.buildUserVerificationConsentTranscript(unsignedNewConsent));
        UserVerificationConsent newConsent = unsignedNewConsent.toBuilder()
                .setUserSignature(ByteString.copyFrom(newConsentSig))
                .build();

        byte[] rebindAttestSig = CanonicalTranscripts.signEd25519(
                collabBrunoEd.privateKey(),
                CanonicalTranscripts.buildCpfRebindTranscript(
                        ISP_BETA_CNPJ, collabBrunoUuid, carlosOldUuid, carlosNewUuid,
                        cpfSha256, betaAddress, newConsentSig, rebindTs));

        VerificationBundle reboundBundle = PrincipalGovernanceServiceGrpc.newBlockingStub(edgeToBetaChannel)
                .submitCpfRebind(SubmitCpfRebindRequest.newBuilder()
                        .setIspCnpj(ISP_BETA_CNPJ)
                        .setCollaboratorCert(collabBrunoCert)
                        .setOldUserUuid(carlosOldUuid)
                        .setNewUserConsent(newConsent)
                        .setNormalizedCpf(CARLOS_CPF)
                        .setHomeNodeAddress(betaAddress)
                        .setAttestedAtMs(rebindTs)
                        .setCollaboratorSignature(ByteString.copyFrom(rebindAttestSig))
                        .build());

        assertEquals(carlosNewUuid, reboundBundle.getCredential().getUserUuid());
        assertEquals(carlosOldUuid, reboundBundle.getReplacementNotice().getOldUserUuid());
        assertEquals(carlosNewUuid, reboundBundle.getReplacementNotice().getNewUserUuid());

        // 3. StreamRevocations empurra o evento ao RevocationSyncWorker do ISP Beta -> Desktop antigo recebe REVOCATION_NOTICE e cai!
        assertTrue(oldDesktopClosedLatch.await(5, TimeUnit.SECONDS),
                "Stream ativa do UUIDv7 antigo deve ser desconectada automaticamente via StreamRevocations");
        boolean receivedRevocationNotice = oldDesktopInbox.stream()
                .anyMatch(env -> env.getType() == MessageType.REVOCATION_NOTICE);
        assertTrue(receivedRevocationNotice, "Dispositivo antigo deve receber quadro REVOCATION_NOTICE antes do fechamento");

        // 4. Quando Alice tenta enviar mensagem para o UUIDv7 antigo de Carlos, recebe IDENTITY_REPLACED apontando para carlosNewUuid
        BlockingQueue<ServerEnvelope> aliceInbox = new LinkedBlockingQueue<>();
        StreamObserver<ClientEnvelope> aliceStream = openStream(
                edgeToBetaChannel, aliceBetaUuid, aliceBetaUuid, aliceBetaEd, null, null, aliceInbox);

        String staleMsgId = CanonicalTranscripts.generateTemporalUuidV7().toString();
        aliceStream.onNext(buildSignedV2Envelope(
                staleMsgId, aliceBetaUuid, aliceBetaUuid, carlosOldUuid,
                MessageType.TEXT, "", "tentativa-uuid-antigo".getBytes(StandardCharsets.UTF_8),
                aliceBetaEd, null, null, System.currentTimeMillis()));

        ServerEnvelope replacedNoticeEnv = aliceInbox.poll(3, TimeUnit.SECONDS);
        assertNotNull(replacedNoticeEnv, "Par deve receber aviso assinado IDENTITY_REPLACED ao tentar contatar UUIDv7 antigo");
        assertEquals(MessageType.IDENTITY_REPLACED, replacedNoticeEnv.getType());
        assertEquals(carlosNewUuid, replacedNoticeEnv.getReplacementNotice().getNewUserUuid());
        aliceStream.onCompleted();

        // 5. Verifica integridade completa do hash-chain no PostgreSQL via OfflineGovernanceCli e descriptografa o selo judicial
        AuditLedgerService ledgerService = principalCtx.getBean(AuditLedgerService.class);
        Path ledgerExportPath = coldVaultDir.resolve("cuj6-ledger.json");
        Files.writeString(ledgerExportPath, ledgerService.exportLedgerChainJson(), StandardCharsets.UTF_8);

        ByteArrayOutputStream vOut = new ByteArrayOutputStream();
        ByteArrayOutputStream vErr = new ByteArrayOutputStream();
        assertEquals(0, OfflineGovernanceCli.runCli(
                new String[]{"--verify-ledger-chain", "--ledger-json", ledgerExportPath.toString()},
                new PrintStream(vOut), new PrintStream(vErr)),
                vErr::toString);

        List<LedgerEntryRecord> allEntries = ledgerService.listAllEntriesOrdered();
        assertFalse(allEntries.isEmpty());
        LedgerEntryRecord rebindEntry = allEntries.getLast();
        assertEquals("SUBMIT_CPF_REBIND", rebindEntry.actionType());
        assertEquals(carlosNewUuid, rebindEntry.targetUuid());

        AuditAccountabilityPayload unsealed = OfflineGovernanceCli.decryptAndVerifyAuditEntry(escrowXPriv, rebindEntry);
        assertEquals(CARLOS_CPF, unsealed.userDoc());
        assertEquals(carlosOldUuid, unsealed.oldUserUuid());
        assertEquals(
                HexFormat.of().formatHex(CanonicalTranscripts.sha256Utf8(COLLAB_BRUNO_CPF)),
                unsealed.collaboratorCpf());
        assertEquals(collabBrunoUuid, unsealed.collaboratorUuid());
        assertEquals(ISP_BETA_CNPJ, unsealed.ispCnpj());

        // Confirma tambem a resolucao completa ate o CPF em claro do Colaborador Bruno no selo AUTHORIZE_COLLABORATOR
        LedgerEntryRecord brunoAuthEntry = allEntries.stream()
                .filter(e -> "AUTHORIZE_COLLABORATOR".equals(e.actionType()) && collabBrunoUuid.equals(e.targetUuid()))
                .findFirst()
                .orElseThrow();
        AuditAccountabilityPayload unsealedBruno = OfflineGovernanceCli.decryptAndVerifyAuditEntry(escrowXPriv, brunoAuthEntry);
        assertEquals(COLLAB_BRUNO_CPF, unsealedBruno.collaboratorCpf());
    }

    // =========================================================================
    // HELPERS DE INFRAESTRUTURA DE TESTE E ASSINATURA V2
    // =========================================================================

    private ConfigurableApplicationContext bootPartnerIspNode(
            int port,
            String publicAddress,
            String dhtNodeId,
            boolean redisAvailable,
            String redisHost,
            String redisPort,
            int redisDbIndex) {
        List<String> args = new ArrayList<>(List.of(
                "--grpc.server.enabled=true",
                "--grpc.server.port=" + port,
                "--ispchat.node.public-address=" + publicAddress,
                "--ispchat.principal.address=" + principalAddress,
                "--ispchat.principal.fallback-address=" + principalAddress,
                "--ispchat.dht.node-id=" + dhtNodeId,
                "--ispchat.dht.srv-bootstrap-enabled=false",
                "--ispchat.governance.revocation-sync-enabled=true",
                "--ispchat.root.public-key=" + Base64.getEncoder().encodeToString(rootEdPub),
                "--ispchat.security.dev-mode=true",
                "--ispchat.security.ca-cert-path=../ca/ca.crt",
                "--ispchat.security.node-cert-path=../ca/dev-node.crt",
                "--ispchat.security.node-key-path=../ca/dev-node.key",
                "--ispchat.turn.static-auth-secret=partner-turn-secret"));
        appendPartnerNodeExcludes(args, redisAvailable, redisHost, redisPort, redisDbIndex);
        return new SpringApplicationBuilder(NodeApplication.class)
                .web(WebApplicationType.NONE)
                .run(args.toArray(String[]::new));
    }

    private static void appendRedisArgs(
            List<String> args,
            boolean redisAvailable,
            String redisHost,
            String redisPort,
            int dbIndex) {
        if (redisAvailable) {
            args.add("--spring.data.redis.host=" + redisHost);
            args.add("--spring.data.redis.port=" + redisPort);
            args.add("--spring.data.redis.database=" + dbIndex);
        } else {
            args.add("--spring.autoconfigure.exclude="
                    + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");
        }
    }

    private static void appendPartnerNodeExcludes(
            List<String> args,
            boolean redisAvailable,
            String redisHost,
            String redisPort,
            int dbIndex) {
        String baseExcludes = "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration";
        if (redisAvailable) {
            args.add("--spring.autoconfigure.exclude=" + baseExcludes);
            args.add("--spring.data.redis.host=" + redisHost);
            args.add("--spring.data.redis.port=" + redisPort);
            args.add("--spring.data.redis.database=" + dbIndex);
        } else {
            args.add("--spring.autoconfigure.exclude=" + baseExcludes + ","
                    + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");
        }
    }

    private void registerDhtSession(
            ManagedChannel edgeChannel,
            String nodeAddr,
            String userUuid,
            String deviceId,
            byte[] rootPub,
            RawKeyPair activeEd,
            RawKeyPair activeX,
            DeviceDelegationCert delCert,
            VerificationCredential verCred,
            HomeNodeClaim homeClaim,
            long tsMs) {
        byte[] sig = CanonicalTranscripts.signEd25519(
                activeEd.privateKey(),
                CanonicalTranscripts.buildDhtSessionV2Transcript(
                        userUuid, deviceId, nodeAddr, rootPub,
                        activeEd.publicKey(), activeX.publicKey(), tsMs));
        RegisterSessionRequest.Builder req = RegisterSessionRequest.newBuilder()
                .setUserUuid(userUuid)
                .setDeviceId(deviceId)
                .setNodeAddress(nodeAddr)
                .setRootEd25519Pub(ByteString.copyFrom(rootPub))
                .setPublicKey(ByteString.copyFrom(activeEd.publicKey()))
                .setX25519PublicKey(ByteString.copyFrom(activeX.publicKey()))
                .setSignature(ByteString.copyFrom(sig))
                .setTimestamp(tsMs);
        if (delCert != null) {
            req.setDelegationCert(delCert);
        }
        if (verCred != null) {
            req.setVerificationCredential(verCred);
        }
        if (homeClaim != null) {
            req.setHomeNodeClaim(homeClaim);
        }
        assertTrue(DhtClientServiceGrpc.newBlockingStub(edgeChannel).registerSession(req.build()).getSuccess());
    }

    private StreamObserver<ClientEnvelope> openStream(
            ManagedChannel edgeChannel,
            String userUuid,
            String deviceId,
            RawKeyPair signerEd,
            DeviceDelegationCert delCert,
            VerificationCredential verCred,
            BlockingQueue<ServerEnvelope> inbox) {
        StreamObserver<ClientEnvelope> out = ClientStreamServiceGrpc.newStub(edgeChannel)
                .connect(new StreamObserver<>() {
                    @Override public void onNext(ServerEnvelope value) { inbox.add(value); }
                    @Override public void onError(Throwable t) {}
                    @Override public void onCompleted() {}
                });
        out.onNext(buildSignedV2Envelope(
                CanonicalTranscripts.generateTemporalUuidV7().toString(),
                userUuid, deviceId, userUuid, MessageType.AUTH_HANDSHAKE, "",
                new byte[0], signerEd, delCert, verCred, System.currentTimeMillis()));
        return out;
    }

    private static DeviceDelegationCert signDelegation(
            RawKeyPair rootEd,
            RawKeyPair devEd,
            RawKeyPair devX,
            DeviceRole role,
            long nowMs) {
        String userUuid = CanonicalTranscripts.deriveIdentityUuidV7(rootEd.publicKey()).toString();
        String devId = CanonicalTranscripts.deriveIdentityUuidV7(devEd.publicKey()).toString();
        DeviceDelegationCert unsigned = DeviceDelegationCert.newBuilder()
                .setCertId(CanonicalTranscripts.generateTemporalUuidV7().toString())
                .setUserUuid(userUuid)
                .setDeviceId(devId)
                .setRole(role)
                .setDeviceEd25519Pub(ByteString.copyFrom(devEd.publicKey()))
                .setDeviceX25519Pub(ByteString.copyFrom(devX.publicKey()))
                .setIssuedAtMs(nowMs - 1_000L)
                .setExpiresAtMs(nowMs + TimeUnit.DAYS.toMillis(90))
                .build();
        return unsigned.toBuilder()
                .setRootSignature(ByteString.copyFrom(CanonicalTranscripts.signEd25519(
                        rootEd.privateKey(), CanonicalTranscripts.buildDeviceDelegationTranscript(unsigned))))
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
            long tsMs) {
        byte[] sig = CanonicalTranscripts.signEd25519(
                signerEd.privateKey(),
                CanonicalTranscripts.buildMessageEnvelopeV2Transcript(
                        messageId, senderUuid, senderDeviceId, receiverUuid,
                        type.getNumber(), refMessageId, encryptedPayload, tsMs));
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
                .setTimestamp(tsMs);
        if (delCert != null) {
            builder.setSenderDelegationCert(delCert);
        }
        if (verCred != null) {
            builder.setSenderVerificationCredential(verCred);
        }
        return builder.build();
    }

    private ManagedChannel buildEdgeTlsChannel(String host, int port) throws Exception {
        return NettyChannelBuilder.forAddress(host, port)
                .sslContext(GrpcSslContexts.forClient()
                        .trustManager(resolveCert("ca/ca.crt"))
                        .protocols("TLSv1.3")
                        .build())
                .build();
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

    private static int reserveFreePort() throws Exception {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    private static void cleanSchema(DataSource ds) {
        Flyway.configure()
                .dataSource(ds)
                .cleanDisabled(false)
                .load()
                .clean();
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
