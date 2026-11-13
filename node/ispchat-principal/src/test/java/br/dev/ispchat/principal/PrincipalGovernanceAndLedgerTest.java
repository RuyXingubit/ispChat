package br.dev.ispchat.principal;

import br.dev.ispchat.common.crypto.CanonicalTranscripts;
import br.dev.ispchat.common.crypto.CanonicalTranscripts.RawKeyPair;
import br.dev.ispchat.grpc.AuthorizeCollaboratorRequest;
import br.dev.ispchat.grpc.CollaboratorAuthCert;
import br.dev.ispchat.grpc.DeviceDelegationCert;
import br.dev.ispchat.grpc.DeviceRole;
import br.dev.ispchat.grpc.DirectoryServiceGrpc;
import br.dev.ispchat.grpc.DocumentType;
import br.dev.ispchat.grpc.GetCredentialRequest;
import br.dev.ispchat.grpc.GovernanceAck;
import br.dev.ispchat.grpc.PortAccountRequest;
import br.dev.ispchat.grpc.PrincipalGovernanceServiceGrpc;
import br.dev.ispchat.grpc.PrincipalSigningCert;
import br.dev.ispchat.grpc.RegisterPartnerIspRequest;
import br.dev.ispchat.grpc.RevocationEvent;
import br.dev.ispchat.grpc.RevokeCollaboratorRequest;
import br.dev.ispchat.grpc.RevokeDeviceRequest;
import br.dev.ispchat.grpc.SearchCnpjRequest;
import br.dev.ispchat.grpc.SearchCnpjResponse;
import br.dev.ispchat.grpc.StreamRevocationsRequest;
import br.dev.ispchat.grpc.SubmitCpfRebindRequest;
import br.dev.ispchat.grpc.SubmitVerificationRequest;
import br.dev.ispchat.grpc.UserVerificationConsent;
import br.dev.ispchat.grpc.VerificationBundle;
import br.dev.ispchat.node.dht.DhtStoreService;
import br.dev.ispchat.node.grpc.GrpcChannelPool;
import br.dev.ispchat.node.grpc.GrpcServerRunner;
import br.dev.ispchat.principal.cli.OfflineGovernanceCli;
import br.dev.ispchat.principal.cli.OfflineGovernanceCli.PartnerBootstrapInput;
import br.dev.ispchat.principal.ledger.AuditLedgerService;
import br.dev.ispchat.principal.ledger.AuditLedgerService.AuditAccountabilityPayload;
import br.dev.ispchat.principal.ledger.AuditLedgerService.LedgerEntryRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
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
import org.springframework.context.ConfigurableApplicationContext;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PrincipalGovernanceAndLedgerTest {

    private static final String ISP_ALPHA_CNPJ = "11222333000181";
    private static final String ISP_BETA_CNPJ = "12ABC34501DE35";
    private static final String PARTNER_ALPHA_CPF = "52998224725";
    private static final String COLLAB_ALPHA_CPF = "11144477735";
    private static final String SUBSCRIBER_CPF = "93541134780";
    private static final String CLIENT_CORP_CNPJ = "06990590000123";

    private EmbeddedPostgres embeddedPostgres;
    private String jdbcUrl;
    private String dbUser;
    private String dbPassword;

    private Path coldVaultDir;
    private byte[] rootEdPub;
    private byte[] rootEdPriv;
    private byte[] escrowXPub;
    private byte[] escrowXPriv;
    private RawKeyPair principalOnlineEd;
    private PrincipalSigningCert principalCert;

    private ConfigurableApplicationContext springContext;
    private GrpcChannelPool mtlsChannelPool;
    private ManagedChannel mtlsChannel;
    private ManagedChannel plainEdgeTlsChannel;
    private PrincipalGovernanceServiceGrpc.PrincipalGovernanceServiceBlockingStub mtlsGovStub;
    private PrincipalGovernanceServiceGrpc.PrincipalGovernanceServiceStub mtlsGovAsyncStub;
    private PrincipalGovernanceServiceGrpc.PrincipalGovernanceServiceBlockingStub edgeGovStub;
    private DirectoryServiceGrpc.DirectoryServiceBlockingStub edgeDirStub;

    private RawKeyPair ispAlphaCorpEd;
    private RawKeyPair ispAlphaCorpX;
    private RawKeyPair partnerAlphaEd;
    private RawKeyPair partnerAlphaX;
    private String partnerAlphaUuid;

    private RawKeyPair collabAlphaEd;
    private String collabAlphaUuid;
    private CollaboratorAuthCert collabAlphaCert;

    private RawKeyPair subscriberOldEd;
    private RawKeyPair subscriberOldX;
    private String subscriberOldUuid;

    private RawKeyPair subscriberNewEd;
    private RawKeyPair subscriberNewX;
    private String subscriberNewUuid;

    private File resolveCert(String relativePath) {
        for (String prefix : new String[]{"", "../", "../../"}) {
            File f = new File(prefix + relativePath);
            if (f.exists()) {
                return f;
            }
        }
        throw new IllegalStateException("Certificado de teste nao encontrado: " + relativePath);
    }

    @BeforeAll
    void startPostgresAndPrincipalContext(@TempDir Path tempDir) throws Exception {
        this.coldVaultDir = tempDir.resolve("airgapped-root");

        // 1. Executa OfflineGovernanceCli --init-root e --sign-principal-cert
        ByteArrayOutputStream cliOut = new ByteArrayOutputStream();
        ByteArrayOutputStream cliErr = new ByteArrayOutputStream();
        int initExit = OfflineGovernanceCli.runCli(
                new String[]{"--init-root", "--out-dir", coldVaultDir.toString()},
                new PrintStream(cliOut),
                new PrintStream(cliErr));
        assertEquals(0, initExit, () -> "Falha em --init-root: " + cliErr);

        rootEdPub = Base64.getDecoder().decode(Files.readString(coldVaultDir.resolve("root_ed25519.pub")).trim());
        rootEdPriv = Base64.getDecoder().decode(Files.readString(coldVaultDir.resolve("root_ed25519.key")).trim());
        escrowXPub = Base64.getDecoder().decode(Files.readString(coldVaultDir.resolve("escrow_x25519.pub")).trim());
        escrowXPriv = Base64.getDecoder().decode(Files.readString(coldVaultDir.resolve("escrow_x25519.key")).trim());

        principalOnlineEd = CanonicalTranscripts.generateEd25519KeyPair();
        Path certOutPath = tempDir.resolve("principal-signing-cert.b64");
        int signCertExit = OfflineGovernanceCli.runCli(
                new String[]{
                        "--sign-principal-cert",
                        "--root-key", coldVaultDir.resolve("root_ed25519.key").toString(),
                        "--principal-pub", Base64.getEncoder().encodeToString(principalOnlineEd.publicKey()),
                        "--escrow-pub", coldVaultDir.resolve("escrow_x25519.pub").toString(),
                        "--ttl-days", "365",
                        "--out", certOutPath.toString()
                },
                new PrintStream(cliOut),
                new PrintStream(cliErr));
        assertEquals(0, signCertExit, () -> "Falha em --sign-principal-cert: " + cliErr);
        String certBase64 = Files.readString(certOutPath).trim();
        principalCert = PrincipalSigningCert.parseFrom(Base64.getDecoder().decode(certBase64));

        // 2. Inicia PostgreSQL 17 (reaproveita ISPCHAT_TEST_JDBC_URL no CI ou EmbeddedPostgres local)
        String envJdbcUrl = System.getenv("ISPCHAT_TEST_JDBC_URL");
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

        byte[] pepper = new byte[32];
        new java.security.SecureRandom().nextBytes(pepper);

        // 3. Sobe aplicacao completa ispchat-principal com Flyway + GrpcServerRunner TLS 1.3
        springContext = new SpringApplicationBuilder(PrincipalApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--grpc.server.enabled=true",
                        "--grpc.server.port=0",
                        "--spring.datasource.url=" + jdbcUrl,
                        "--spring.datasource.username=" + dbUser,
                        "--spring.datasource.password=" + dbPassword,
                        "--spring.flyway.enabled=true",
                        "--ispchat.dht.srv-bootstrap-enabled=false",
                        "--ispchat.governance.revocation-sync-enabled=false",
                        "--ispchat.security.dev-mode=true",
                        "--ispchat.security.ca-cert-path=../ca/ca.crt",
                        "--ispchat.security.node-cert-path=../ca/dev-node.crt",
                        "--ispchat.security.node-key-path=../ca/dev-node.key",
                        "--ispchat.node.public-address=principal.ispchat.dev.br:443",
                        "--ispchat.principal.address=principal.ispchat.dev.br:443",
                        "--ispchat.principal.fallback-address=principal.ispchat.dev.br:443",
                        "--ispchat.root.public-key=" + Base64.getEncoder().encodeToString(rootEdPub),
                        "--ispchat.principal.signing-private-key=" + Base64.getEncoder().encodeToString(principalOnlineEd.privateKey()),
                        "--ispchat.principal.signing-cert-base64=" + certBase64,
                        "--ispchat.principal.blind-index-pepper=" + Base64.getEncoder().encodeToString(pepper),
                        "--ispchat.principal.argon2-memory-kib=65536",
                        "--ispchat.principal.argon2-iterations=3",
                        "--ispchat.principal.argon2-concurrency=4",
                        "--ispchat.turn.static-auth-secret=principal-test-turn-secret");

        // Garante que os proxies @ConditionalOnMissingBean de borda foram substituidos pelos beans primarios
        assertFalse(springContext.containsBean("edgeGovernanceProxyGrpcService"));
        assertFalse(springContext.containsBean("edgeDirectoryProxyGrpcService"));
        assertTrue(springContext.containsBean("principalGovernanceGrpcService"));
        assertTrue(springContext.containsBean("directoryGrpcService"));

        int grpcPort = springContext.getBean(GrpcServerRunner.class).getPort();
        mtlsChannelPool = new GrpcChannelPool(true, "../ca/ca.crt", "../ca/dev-node.crt", "../ca/dev-node.key");
        mtlsChannel = mtlsChannelPool.getChannel("localhost:" + grpcPort);
        mtlsGovStub = PrincipalGovernanceServiceGrpc.newBlockingStub(mtlsChannel);
        mtlsGovAsyncStub = PrincipalGovernanceServiceGrpc.newStub(mtlsChannel);

        // Canal TLS 1.3 de borda SEM certificado X.509 de cliente (simula App Mobile direto)
        plainEdgeTlsChannel = NettyChannelBuilder.forAddress("localhost", grpcPort)
                .sslContext(GrpcSslContexts.forClient()
                        .trustManager(resolveCert("ca/ca.crt"))
                        .protocols("TLSv1.3")
                        .build())
                .build();
        edgeGovStub = PrincipalGovernanceServiceGrpc.newBlockingStub(plainEdgeTlsChannel);
        edgeDirStub = DirectoryServiceGrpc.newBlockingStub(plainEdgeTlsChannel);
    }

    private static void cleanSchema(DataSource ds) {
        Flyway.configure()
                .dataSource(ds)
                .cleanDisabled(false)
                .load()
                .clean();
    }

    @AfterAll
    void stopAll() throws Exception {
        if (plainEdgeTlsChannel != null) {
            plainEdgeTlsChannel.shutdownNow();
        }
        if (mtlsChannelPool != null) {
            mtlsChannelPool.shutdownAll();
        }
        if (springContext != null) {
            springContext.close();
        }
        if (embeddedPostgres != null) {
            embeddedPostgres.close();
        }
    }

    @Test
    @Order(1)
    void fourLevelChainOfTrustAndCorporateDirectoryPrivacy() throws Exception {
        long nowMs = System.currentTimeMillis();

        // Nivel 1: CLI offline assina pacote de bootstrap para ISP Alpha (localhost:18443 -> bate com SAN localhost)
        ispAlphaCorpEd = CanonicalTranscripts.generateEd25519KeyPair();
        ispAlphaCorpX = CanonicalTranscripts.generateX25519KeyPair();
        partnerAlphaEd = CanonicalTranscripts.generateEd25519KeyPair();
        partnerAlphaX = CanonicalTranscripts.generateX25519KeyPair();
        partnerAlphaUuid = CanonicalTranscripts.deriveIdentityUuidV7(partnerAlphaEd.publicKey()).toString();

        Path partnersJson = coldVaultDir.resolve("isp-alpha-partners.json");
        Files.writeString(partnersJson, new ObjectMapper().writeValueAsString(List.of(
                new PartnerBootstrapInput(
                        PARTNER_ALPHA_CPF,
                        Base64.getEncoder().encodeToString(partnerAlphaEd.publicKey()),
                        Base64.getEncoder().encodeToString(partnerAlphaX.publicKey())))),
                StandardCharsets.UTF_8);

        Path bootstrapOut = coldVaultDir.resolve("isp-alpha-bootstrap.b64");
        int bootExit = OfflineGovernanceCli.runCli(
                new String[]{
                        "--sign-isp-bootstrap",
                        "--root-key", coldVaultDir.resolve("root_ed25519.key").toString(),
                        "--isp-cnpj", ISP_ALPHA_CNPJ,
                        "--trade-name", "ISP Alpha Fibra",
                        "--corp-ed25519-pub", Base64.getEncoder().encodeToString(ispAlphaCorpEd.publicKey()),
                        "--corp-x25519-pub", Base64.getEncoder().encodeToString(ispAlphaCorpX.publicKey()),
                        "--node-address", "localhost:18443",
                        "--partners-json", partnersJson.toString(),
                        "--out", bootstrapOut.toString()
                },
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream()));
        assertEquals(0, bootExit);

        RegisterPartnerIspRequest ispAlphaReq = RegisterPartnerIspRequest.parseFrom(
                Base64.getDecoder().decode(Files.readString(bootstrapOut).trim()));

        // Chamada sem mTLS X.509 deve falhar com UNAUTHENTICATED
        StatusRuntimeException unauthErr = assertThrows(StatusRuntimeException.class,
                () -> edgeGovStub.registerPartnerIsp(ispAlphaReq));
        assertEquals(Status.Code.UNAUTHENTICATED, unauthErr.getStatus().getCode());

        GovernanceAck ack = mtlsGovStub.registerPartnerIsp(ispAlphaReq);
        assertTrue(ack.getAccepted());
        assertEquals(DocumentType.CNPJ, ack.getCorporateBundle().getCredential().getDocType());

        // Registra tambem ISP Beta com node_address "isp-beta.net.br:443" para teste de anti-spoofing mTLS SAN
        RawKeyPair betaCorpEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair betaCorpX = CanonicalTranscripts.generateX25519KeyPair();
        RawKeyPair betaPartnerEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair betaPartnerX = CanonicalTranscripts.generateX25519KeyPair();
        RegisterPartnerIspRequest ispBetaReq = OfflineGovernanceCli.signPartnerIspBootstrap(
                rootEdPriv,
                ISP_BETA_CNPJ,
                "ISP Beta Telecom",
                betaCorpEd.publicKey(),
                betaCorpX.publicKey(),
                "isp-beta.net.br:443",
                List.of(new PartnerBootstrapInput(
                        "39053344705",
                        Base64.getEncoder().encodeToString(betaPartnerEd.publicKey()),
                        Base64.getEncoder().encodeToString(betaPartnerX.publicKey()))),
                nowMs);
        assertTrue(mtlsGovStub.registerPartnerIsp(ispBetaReq).getAccepted());

        // Nivel 2: Socio Administrador do ISP Alpha autoriza Colaborador
        collabAlphaEd = CanonicalTranscripts.generateEd25519KeyPair();
        collabAlphaUuid = CanonicalTranscripts.deriveIdentityUuidV7(collabAlphaEd.publicKey()).toString();
        CollaboratorAuthCert unsignedCollab = CollaboratorAuthCert.newBuilder()
                .setCertId(CanonicalTranscripts.generateTemporalUuidV7().toString())
                .setIspCnpj(ISP_ALPHA_CNPJ)
                .setPartnerUuid(partnerAlphaUuid)
                .setCollaboratorUuid(collabAlphaUuid)
                .setCollabCpfSha256(ByteString.copyFrom(CanonicalTranscripts.sha256Utf8(COLLAB_ALPHA_CPF)))
                .setCollabEd25519Pub(ByteString.copyFrom(collabAlphaEd.publicKey()))
                .setIssuedAtMs(nowMs - 1_000L)
                .setExpiresAtMs(nowMs + Duration.ofDays(180).toMillis())
                .build();
        byte[] partnerSig = CanonicalTranscripts.signEd25519(
                partnerAlphaEd.privateKey(),
                CanonicalTranscripts.buildCollaboratorAuthTranscript(unsignedCollab));
        collabAlphaCert = unsignedCollab.toBuilder()
                .setPartnerSignature(ByteString.copyFrom(partnerSig))
                .build();

        CollaboratorAuthCert storedCollab = mtlsGovStub.authorizeCollaborator(
                AuthorizeCollaboratorRequest.newBuilder()
                        .setCert(collabAlphaCert)
                        .setCollaboratorCpf(COLLAB_ALPHA_CPF)
                        .build());
        assertEquals(collabAlphaUuid, storedCollab.getCollaboratorUuid());

        // Nivel 3 & 4: Assinante CPF assina consentimento no proprio celular + Colaborador atesta
        subscriberOldEd = CanonicalTranscripts.generateEd25519KeyPair();
        subscriberOldX = CanonicalTranscripts.generateX25519KeyPair();
        subscriberOldUuid = CanonicalTranscripts.deriveIdentityUuidV7(subscriberOldEd.publicKey()).toString();

        SubmitVerificationRequest subVerifyReq = buildVerificationSubmission(
                ISP_ALPHA_CNPJ,
                collabAlphaCert,
                collabAlphaEd,
                subscriberOldEd,
                subscriberOldX,
                DocumentType.CPF,
                SUBSCRIBER_CPF,
                "",
                "localhost:18443",
                System.currentTimeMillis());

        VerificationBundle subBundle = mtlsGovStub.submitVerification(subVerifyReq);
        assertEquals(subscriberOldUuid, subBundle.getCredential().getUserUuid());
        assertEquals(DocumentType.CPF, subBundle.getCredential().getDocType());
        assertEquals("", subBundle.getCredential().getPublicCnpj());

        // Unicidade 1 CPF = 1 UUIDv7 ativo: tentativa de registrar segundo UUIDv7 para o mesmo CPF falha com ALREADY_EXISTS
        RawKeyPair duplicateCpfEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair duplicateCpfX = CanonicalTranscripts.generateX25519KeyPair();
        SubmitVerificationRequest dupReq = buildVerificationSubmission(
                ISP_ALPHA_CNPJ,
                collabAlphaCert,
                collabAlphaEd,
                duplicateCpfEd,
                duplicateCpfX,
                DocumentType.CPF,
                SUBSCRIBER_CPF,
                "",
                "localhost:18443",
                System.currentTimeMillis());
        StatusRuntimeException dupErr = assertThrows(StatusRuntimeException.class,
                () -> mtlsGovStub.submitVerification(dupReq));
        assertEquals(Status.Code.ALREADY_EXISTS, dupErr.getStatus().getCode());

        // Replay do mesmo UserVerificationConsent tambem deve falhar com PERMISSION_DENIED
        StatusRuntimeException replayErr = assertThrows(StatusRuntimeException.class,
                () -> mtlsGovStub.submitVerification(subVerifyReq));
        assertEquals(Status.Code.PERMISSION_DENIED, replayErr.getStatus().getCode());

        // Verifica tambem uma empresa cliente CNPJ e valida privacidade estrita de SearchVerifiedCnpj
        RawKeyPair clientCorpEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair clientCorpX = CanonicalTranscripts.generateX25519KeyPair();
        SubmitVerificationRequest corpVerifyReq = buildVerificationSubmission(
                ISP_ALPHA_CNPJ,
                collabAlphaCert,
                collabAlphaEd,
                clientCorpEd,
                clientCorpX,
                DocumentType.CNPJ,
                CLIENT_CORP_CNPJ,
                "Padaria Central Ltda",
                "localhost:18443",
                System.currentTimeMillis());
        mtlsGovStub.submitVerification(corpVerifyReq);

        SearchCnpjResponse corpSearch = edgeDirStub.searchVerifiedCnpj(
                SearchCnpjRequest.newBuilder().setQuery("Padaria").setLimit(10).build());
        assertEquals(1, corpSearch.getEntriesCount());
        assertEquals(CLIENT_CORP_CNPJ, corpSearch.getEntries(0).getCnpj());
        assertEquals(32, corpSearch.getEntries(0).getRootX25519Pub().size());

        // Buscas pelo CPF do assinante ou do socio retornam ZERO entradas (privacidade CPF estrita)
        SearchCnpjResponse cpfSearch = edgeDirStub.searchVerifiedCnpj(
                SearchCnpjRequest.newBuilder().setQuery(SUBSCRIBER_CPF).setLimit(10).build());
        assertEquals(0, cpfSearch.getEntriesCount());
    }

    @Test
    @Order(2)
    void concurrentParallelSubmitVerificationProducesZeroHashChainForksAndDecryptsOffline() throws Exception {
        int parallelWorkers = 8;
        List<String> validCpfs = generateDistinctValidCpfs(200_000_000, parallelWorkers);
        ExecutorService pool = Executors.newFixedThreadPool(parallelWorkers);
        List<Callable<VerificationBundle>> tasks = new ArrayList<>();

        for (int i = 0; i < parallelWorkers; i++) {
            final String cpf = validCpfs.get(i);
            tasks.add(() -> {
                RawKeyPair uEd = CanonicalTranscripts.generateEd25519KeyPair();
                RawKeyPair uX = CanonicalTranscripts.generateX25519KeyPair();
                SubmitVerificationRequest req = buildVerificationSubmission(
                        ISP_ALPHA_CNPJ,
                        collabAlphaCert,
                        collabAlphaEd,
                        uEd,
                        uX,
                        DocumentType.CPF,
                        cpf,
                        "",
                        "localhost:18443",
                        System.currentTimeMillis());
                return mtlsGovStub.submitVerification(req);
            });
        }

        List<Future<VerificationBundle>> futures = pool.invokeAll(tasks);
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        for (Future<VerificationBundle> f : futures) {
            assertNotNull(f.get().getCredential().getCredentialId());
        }

        // Exporta o ledger inteiro e valida via OfflineGovernanceCli --verify-ledger-chain e --decrypt-audit-entry
        AuditLedgerService ledgerService = springContext.getBean(AuditLedgerService.class);
        List<LedgerEntryRecord> allRows = ledgerService.listAllEntriesOrdered();
        assertTrue(allRows.size() >= 13);

        Path ledgerExportFile = coldVaultDir.resolve("exported-ledger.json");
        Files.writeString(ledgerExportFile, ledgerService.exportLedgerChainJson(), StandardCharsets.UTF_8);

        ByteArrayOutputStream verifyOut = new ByteArrayOutputStream();
        ByteArrayOutputStream verifyErr = new ByteArrayOutputStream();
        int verifyExit = OfflineGovernanceCli.runCli(
                new String[]{"--verify-ledger-chain", "--ledger-json", ledgerExportFile.toString()},
                new PrintStream(verifyOut),
                new PrintStream(verifyErr));
        assertEquals(0, verifyExit, () -> "Falha na cadeia de hashes: " + verifyErr);

        // Descriptografa judicialmente offline uma linha do ledger usando a chave privada air-gapped escrow_x25519.key
        LedgerEntryRecord lastRow = allRows.getLast();
        Path singleEntryFile = coldVaultDir.resolve("single-entry.json");
        Files.writeString(
                singleEntryFile,
                new ObjectMapper().writeValueAsString(lastRow.toExportedJson()),
                StandardCharsets.UTF_8);

        ByteArrayOutputStream decOut = new ByteArrayOutputStream();
        int decExit = OfflineGovernanceCli.runCli(
                new String[]{
                        "--decrypt-audit-entry",
                        "--escrow-key", coldVaultDir.resolve("escrow_x25519.key").toString(),
                        "--entry-json", singleEntryFile.toString()
                },
                new PrintStream(decOut),
                new PrintStream(verifyErr));
        assertEquals(0, decExit);
        AuditAccountabilityPayload opened = new ObjectMapper().readValue(
                decOut.toByteArray(), AuditAccountabilityPayload.class);
        assertEquals("SUBMIT_VERIFICATION", opened.actionType());
        assertTrue(validCpfs.contains(opened.userDoc()));

        // Adulteracao de 1 bit em sealed_audit_blob deve ser detectada imediatamente
        byte[] tamperedBlob = lastRow.sealedAuditBlob().clone();
        tamperedBlob[0] ^= 0x01;
        LedgerEntryRecord tamperedRecord = new LedgerEntryRecord(
                lastRow.seq(),
                lastRow.entryId(),
                lastRow.actionType(),
                lastRow.targetUuid(),
                lastRow.docBlindIndex(),
                lastRow.ispCnpj(),
                lastRow.collaboratorUuid(),
                lastRow.ephemeralX25519Pub(),
                tamperedBlob,
                lastRow.prevEntryHash(),
                lastRow.entryHash(),
                lastRow.createdAtEpochMs());
        assertThrows(SecurityException.class,
                () -> OfflineGovernanceCli.decryptAndVerifyAuditEntry(escrowXPriv, tamperedRecord));
    }

    @Test
    @Order(3)
    void zeroHourCpfRebindDeviceRevocationPortabilityAndNegativeSecurityGuards() throws Exception {
        // Abre StreamRevocations em tempo real para capturar notificacoes push pos-commit
        List<RevocationEvent> liveRevocations = new CopyOnWriteArrayList<>();
        mtlsGovAsyncStub.streamRevocations(
                StreamRevocationsRequest.newBuilder().setSinceRevocationSeq(0L).build(),
                new StreamObserver<>() {
                    @Override
                    public void onNext(RevocationEvent value) {
                        liveRevocations.add(value);
                    }

                    @Override
                    public void onError(Throwable t) {
                    }

                    @Override
                    public void onCompleted() {
                    }
                });

        // 1. SubmitCpfRebind imediato (0h hold): rotaciona SUBSCRIBER_CPF de subscriberOldUuid -> subscriberNewUuid
        long nowMs = System.currentTimeMillis();
        subscriberNewEd = CanonicalTranscripts.generateEd25519KeyPair();
        subscriberNewX = CanonicalTranscripts.generateX25519KeyPair();
        subscriberNewUuid = CanonicalTranscripts.deriveIdentityUuidV7(subscriberNewEd.publicKey()).toString();

        byte[] cpfSha = CanonicalTranscripts.sha256Utf8(SUBSCRIBER_CPF);
        UserVerificationConsent unsignedNewConsent = UserVerificationConsent.newBuilder()
                .setUserUuid(subscriberNewUuid)
                .setUserEd25519Pub(ByteString.copyFrom(subscriberNewEd.publicKey()))
                .setUserX25519Pub(ByteString.copyFrom(subscriberNewX.publicKey()))
                .setDocType(DocumentType.CPF)
                .setDocSha256(ByteString.copyFrom(cpfSha))
                .setIspCnpj(ISP_ALPHA_CNPJ)
                .setTimestampMs(nowMs)
                .build();
        byte[] newUserSig = CanonicalTranscripts.signEd25519(
                subscriberNewEd.privateKey(),
                CanonicalTranscripts.buildUserVerificationConsentTranscript(unsignedNewConsent));
        UserVerificationConsent signedNewConsent = unsignedNewConsent.toBuilder()
                .setUserSignature(ByteString.copyFrom(newUserSig))
                .build();

        byte[] rebindSig = CanonicalTranscripts.signEd25519(
                collabAlphaEd.privateKey(),
                CanonicalTranscripts.buildCpfRebindTranscript(
                        ISP_ALPHA_CNPJ,
                        collabAlphaUuid,
                        subscriberOldUuid,
                        subscriberNewUuid,
                        cpfSha,
                        "localhost:18443",
                        newUserSig,
                        nowMs));

        VerificationBundle reboundBundle = mtlsGovStub.submitCpfRebind(
                SubmitCpfRebindRequest.newBuilder()
                        .setIspCnpj(ISP_ALPHA_CNPJ)
                        .setCollaboratorCert(collabAlphaCert)
                        .setOldUserUuid(subscriberOldUuid)
                        .setNewUserConsent(signedNewConsent)
                        .setNormalizedCpf(SUBSCRIBER_CPF)
                        .setHomeNodeAddress("localhost:18443")
                        .setAttestedAtMs(nowMs)
                        .setCollaboratorSignature(ByteString.copyFrom(rebindSig))
                        .build());

        assertEquals(subscriberNewUuid, reboundBundle.getCredential().getUserUuid());
        assertEquals(subscriberOldUuid, reboundBundle.getReplacementNotice().getOldUserUuid());
        assertEquals(subscriberNewUuid, reboundBundle.getReplacementNotice().getNewUserUuid());

        // Aguarda recebimento push em StreamRevocations
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (liveRevocations.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertFalse(liveRevocations.isEmpty());
        assertEquals(subscriberOldUuid, liveRevocations.getFirst().getTargetUuid());
        assertEquals(subscriberNewUuid, liveRevocations.getFirst().getReplacementNotice().getNewUserUuid());

        // UUIDv7_old nao consegue mais obter/renovar credencial (PERMISSION_DENIED)
        byte[] oldFetchSig = CanonicalTranscripts.signEd25519(
                subscriberOldEd.privateKey(),
                CanonicalTranscripts.buildGetOrRenewCredentialTranscript(subscriberOldUuid, subscriberOldUuid, nowMs));
        StatusRuntimeException oldCredDenied = assertThrows(StatusRuntimeException.class,
                () -> edgeGovStub.getOrRenewCredential(GetCredentialRequest.newBuilder()
                        .setUserUuid(subscriberOldUuid)
                        .setDeviceId(subscriberOldUuid)
                        .setTimestampMs(nowMs)
                        .setCallerPublicKey(ByteString.copyFrom(subscriberOldEd.publicKey()))
                        .setSignature(ByteString.copyFrom(oldFetchSig))
                        .build()));
        assertEquals(Status.Code.PERMISSION_DENIED, oldCredDenied.getStatus().getCode());

        // 2. GetOrRenewCredential via subchave DESKTOP delegada + RevokeDevice
        RawKeyPair desktopEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair desktopX = CanonicalTranscripts.generateX25519KeyPair();
        String desktopDevId = CanonicalTranscripts.deriveIdentityUuidV7(desktopEd.publicKey()).toString();
        DeviceDelegationCert unsignedDel = DeviceDelegationCert.newBuilder()
                .setCertId(CanonicalTranscripts.generateTemporalUuidV7().toString())
                .setUserUuid(subscriberNewUuid)
                .setDeviceId(desktopDevId)
                .setRole(DeviceRole.DESKTOP)
                .setDeviceEd25519Pub(ByteString.copyFrom(desktopEd.publicKey()))
                .setDeviceX25519Pub(ByteString.copyFrom(desktopX.publicKey()))
                .setIssuedAtMs(nowMs - 1_000L)
                .setExpiresAtMs(nowMs + Duration.ofDays(30).toMillis())
                .build();
        DeviceDelegationCert signedDel = unsignedDel.toBuilder()
                .setRootSignature(ByteString.copyFrom(CanonicalTranscripts.signEd25519(
                        subscriberNewEd.privateKey(),
                        CanonicalTranscripts.buildDeviceDelegationTranscript(unsignedDel))))
                .build();

        byte[] deskFetchSig = CanonicalTranscripts.signEd25519(
                desktopEd.privateKey(),
                CanonicalTranscripts.buildGetOrRenewCredentialTranscript(subscriberNewUuid, desktopDevId, nowMs));
        VerificationBundle fetchedByDesktop = edgeGovStub.getOrRenewCredential(GetCredentialRequest.newBuilder()
                .setUserUuid(subscriberNewUuid)
                .setDeviceId(desktopDevId)
                .setTimestampMs(nowMs)
                .setCallerPublicKey(ByteString.copyFrom(desktopEd.publicKey()))
                .setSignature(ByteString.copyFrom(deskFetchSig))
                .setDelegationCert(signedDel)
                .build());
        assertEquals(subscriberNewUuid, fetchedByDesktop.getCredential().getUserUuid());

        // Revoga subchave DESKTOP e confirma bloqueio imediato
        byte[] devRevSig = CanonicalTranscripts.signEd25519(
                subscriberNewEd.privateKey(),
                CanonicalTranscripts.buildRevokeDeviceTranscript(subscriberNewUuid, desktopDevId, "LOGOUT_PC", nowMs));
        RevocationEvent devRevEvent = edgeGovStub.revokeDevice(RevokeDeviceRequest.newBuilder()
                .setUserUuid(subscriberNewUuid)
                .setRevokedDeviceId(desktopDevId)
                .setReason("LOGOUT_PC")
                .setTimestampMs(nowMs)
                .setRootSignature(ByteString.copyFrom(devRevSig))
                .build());
        assertEquals(desktopDevId, devRevEvent.getRevokedDeviceId());

        StatusRuntimeException revokedDeskDenied = assertThrows(StatusRuntimeException.class,
                () -> edgeGovStub.getOrRenewCredential(GetCredentialRequest.newBuilder()
                        .setUserUuid(subscriberNewUuid)
                        .setDeviceId(desktopDevId)
                        .setTimestampMs(nowMs)
                        .setCallerPublicKey(ByteString.copyFrom(desktopEd.publicKey()))
                        .setSignature(ByteString.copyFrom(deskFetchSig))
                        .setDelegationCert(signedDel)
                        .build()));
        assertEquals(Status.Code.PERMISSION_DENIED, revokedDeskDenied.getStatus().getCode());

        // 3. Portabilidade: migra para Fallback Home Node (epoch 2, apenas assinatura raiz) e retorna ao ISP Alpha (epoch 3)
        byte[] portToFallbackSig = CanonicalTranscripts.signEd25519(
                subscriberNewEd.privateKey(),
                CanonicalTranscripts.buildPortAccountTranscript(
                        subscriberNewUuid, "principal.ispchat.dev.br:443", "", 2L, nowMs));
        VerificationBundle fallbackPorted = edgeGovStub.portAccount(PortAccountRequest.newBuilder()
                .setUserUuid(subscriberNewUuid)
                .setTargetHomeNodeAddress("principal.ispchat.dev.br:443")
                .setExpectedRoutingEpoch(2L)
                .setTimestampMs(nowMs)
                .setRootSignature(ByteString.copyFrom(portToFallbackSig))
                .build());
        assertEquals(2L, fallbackPorted.getHomeNodeClaim().getRoutingEpoch());
        assertEquals("principal.ispchat.dev.br:443", fallbackPorted.getHomeNodeClaim().getHomeNodeAddress());

        byte[] portToIspTranscript = CanonicalTranscripts.buildPortAccountTranscript(
                subscriberNewUuid, "localhost:18443", ISP_ALPHA_CNPJ, 3L, nowMs);
        VerificationBundle ispPorted = mtlsGovStub.portAccount(PortAccountRequest.newBuilder()
                .setUserUuid(subscriberNewUuid)
                .setTargetHomeNodeAddress("localhost:18443")
                .setTargetIspCnpj(ISP_ALPHA_CNPJ)
                .setExpectedRoutingEpoch(3L)
                .setTimestampMs(nowMs)
                .setRootSignature(ByteString.copyFrom(
                        CanonicalTranscripts.signEd25519(subscriberNewEd.privateKey(), portToIspTranscript)))
                .setTargetCollaboratorCert(collabAlphaCert)
                .setTargetCollaboratorSignature(ByteString.copyFrom(
                        CanonicalTranscripts.signEd25519(collabAlphaEd.privateKey(), portToIspTranscript)))
                .build());
        assertEquals(3L, ispPorted.getHomeNodeClaim().getRoutingEpoch());
        assertEquals("localhost:18443",
                springContext.getBean(DhtStoreService.class).getRedirectTombstone(subscriberNewUuid).orElseThrow());

        // 4. Seguranca Negativa: Cross-ISP mTLS SAN Spoofing (certificado localhost tentando operar como ISP Beta)
        RawKeyPair spoofCollabEd = CanonicalTranscripts.generateEd25519KeyPair();
        CollaboratorAuthCert spoofCert = CollaboratorAuthCert.newBuilder()
                .setCertId(CanonicalTranscripts.generateTemporalUuidV7().toString())
                .setIspCnpj(ISP_BETA_CNPJ)
                .setPartnerUuid(partnerAlphaUuid)
                .setCollaboratorUuid(CanonicalTranscripts.deriveIdentityUuidV7(spoofCollabEd.publicKey()).toString())
                .setCollabCpfSha256(ByteString.copyFrom(CanonicalTranscripts.sha256Utf8(COLLAB_ALPHA_CPF)))
                .setCollabEd25519Pub(ByteString.copyFrom(spoofCollabEd.publicKey()))
                .setIssuedAtMs(nowMs - 1_000L)
                .setExpiresAtMs(nowMs + Duration.ofDays(30).toMillis())
                .setPartnerSignature(ByteString.copyFrom(new byte[64]))
                .build();
        StatusRuntimeException sanSpoofErr = assertThrows(StatusRuntimeException.class,
                () -> mtlsGovStub.authorizeCollaborator(AuthorizeCollaboratorRequest.newBuilder()
                        .setCert(spoofCert)
                        .setCollaboratorCpf(COLLAB_ALPHA_CPF)
                        .build()));
        assertEquals(Status.Code.PERMISSION_DENIED, sanSpoofErr.getStatus().getCode());

        // 5. Seguranca Negativa: Consentimento expirado/scraping (> 5 min atras) -> PERMISSION_DENIED
        RawKeyPair staleUserEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair staleUserX = CanonicalTranscripts.generateX25519KeyPair();
        String unusedCpf = generateDistinctValidCpfs(300_000_000, 1).getFirst();
        SubmitVerificationRequest staleConsentReq = buildVerificationSubmission(
                ISP_ALPHA_CNPJ,
                collabAlphaCert,
                collabAlphaEd,
                staleUserEd,
                staleUserX,
                DocumentType.CPF,
                unusedCpf,
                "",
                "localhost:18443",
                nowMs - Duration.ofMinutes(10).toMillis());
        StatusRuntimeException staleErr = assertThrows(StatusRuntimeException.class,
                () -> mtlsGovStub.submitVerification(staleConsentReq));
        assertEquals(Status.Code.PERMISSION_DENIED, staleErr.getStatus().getCode());

        // 6. RevokeCollaborator bloqueia imediatamente novas atestacoes desse colaborador
        byte[] revCollabSig = CanonicalTranscripts.signEd25519(
                partnerAlphaEd.privateKey(),
                CanonicalTranscripts.buildRevokeCollaboratorTranscript(
                        ISP_ALPHA_CNPJ, partnerAlphaUuid, collabAlphaUuid, "OFFBOARDING", nowMs));
        RevocationEvent collabRev = mtlsGovStub.revokeCollaborator(RevokeCollaboratorRequest.newBuilder()
                .setIspCnpj(ISP_ALPHA_CNPJ)
                .setPartnerUuid(partnerAlphaUuid)
                .setCollaboratorUuid(collabAlphaUuid)
                .setReason("OFFBOARDING")
                .setTimestampMs(nowMs)
                .setPartnerSignature(ByteString.copyFrom(revCollabSig))
                .build());
        assertNotEquals(0L, collabRev.getRevocationSeq());

        SubmitVerificationRequest postRevokeReq = buildVerificationSubmission(
                ISP_ALPHA_CNPJ,
                collabAlphaCert,
                collabAlphaEd,
                staleUserEd,
                staleUserX,
                DocumentType.CPF,
                unusedCpf,
                "",
                "localhost:18443",
                System.currentTimeMillis());
        StatusRuntimeException revokedCollabErr = assertThrows(StatusRuntimeException.class,
                () -> mtlsGovStub.submitVerification(postRevokeReq));
        assertEquals(Status.Code.PERMISSION_DENIED, revokedCollabErr.getStatus().getCode());
    }

    private static SubmitVerificationRequest buildVerificationSubmission(
            String ispCnpj,
            CollaboratorAuthCert collabCert,
            RawKeyPair collabEd,
            RawKeyPair userEd,
            RawKeyPair userX,
            DocumentType docType,
            String normalizedDoc,
            String publicDisplayName,
            String homeNodeAddr,
            long consentTimestampMs) {
        String userUuid = CanonicalTranscripts.deriveIdentityUuidV7(userEd.publicKey()).toString();
        byte[] docSha256 = CanonicalTranscripts.sha256Utf8(normalizedDoc);

        UserVerificationConsent unsignedConsent = UserVerificationConsent.newBuilder()
                .setUserUuid(userUuid)
                .setUserEd25519Pub(ByteString.copyFrom(userEd.publicKey()))
                .setUserX25519Pub(ByteString.copyFrom(userX.publicKey()))
                .setDocType(docType)
                .setDocSha256(ByteString.copyFrom(docSha256))
                .setIspCnpj(ispCnpj)
                .setTimestampMs(consentTimestampMs)
                .build();
        byte[] userSig = CanonicalTranscripts.signEd25519(
                userEd.privateKey(),
                CanonicalTranscripts.buildUserVerificationConsentTranscript(unsignedConsent));
        UserVerificationConsent signedConsent = unsignedConsent.toBuilder()
                .setUserSignature(ByteString.copyFrom(userSig))
                .build();

        long attestedAtMs = System.currentTimeMillis();
        byte[] collabSig = CanonicalTranscripts.signEd25519(
                collabEd.privateKey(),
                CanonicalTranscripts.buildCollaboratorUserAttestationTranscript(
                        ispCnpj,
                        collabCert.getCollaboratorUuid(),
                        userUuid,
                        docType,
                        docSha256,
                        homeNodeAddr,
                        userSig,
                        attestedAtMs));

        return SubmitVerificationRequest.newBuilder()
                .setIspCnpj(ispCnpj)
                .setCollaboratorCert(collabCert)
                .setUserConsent(signedConsent)
                .setNormalizedDocument(normalizedDoc)
                .setPublicDisplayName(publicDisplayName != null ? publicDisplayName : "")
                .setHomeNodeAddress(homeNodeAddr)
                .setAttestedAtMs(attestedAtMs)
                .setCollaboratorSignature(ByteString.copyFrom(collabSig))
                .build();
    }

    private static List<String> generateDistinctValidCpfs(int startBase9Digits, int count) {
        List<String> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String base9 = String.format("%09d", startBase9Digits + i);
            int sum1 = 0;
            for (int j = 0; j < 9; j++) {
                sum1 += (base9.charAt(j) - '0') * (10 - j);
            }
            int dv1 = 11 - (sum1 % 11);
            if (dv1 >= 10) {
                dv1 = 0;
            }
            String base10 = base9 + dv1;
            int sum2 = 0;
            for (int j = 0; j < 10; j++) {
                sum2 += (base10.charAt(j) - '0') * (11 - j);
            }
            int dv2 = 11 - (sum2 % 11);
            if (dv2 >= 10) {
                dv2 = 0;
            }
            list.add(base10 + dv2);
        }
        return list;
    }
}
