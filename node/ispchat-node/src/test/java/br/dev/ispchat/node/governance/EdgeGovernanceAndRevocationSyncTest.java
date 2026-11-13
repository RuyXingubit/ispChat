package br.dev.ispchat.node.governance;

import br.dev.ispchat.common.crypto.CanonicalTranscripts;
import br.dev.ispchat.common.crypto.CanonicalTranscripts.RawKeyPair;
import br.dev.ispchat.grpc.AuthorizeCollaboratorRequest;
import br.dev.ispchat.grpc.CollaboratorAuthCert;
import br.dev.ispchat.grpc.DeviceRole;
import br.dev.ispchat.grpc.DirectoryServiceGrpc;
import br.dev.ispchat.grpc.DocumentType;
import br.dev.ispchat.grpc.GetCredentialRequest;
import br.dev.ispchat.grpc.HomeNodeClaim;
import br.dev.ispchat.grpc.IdentityReplacementNotice;
import br.dev.ispchat.grpc.MessageType;
import br.dev.ispchat.grpc.PortAccountRequest;
import br.dev.ispchat.grpc.PrincipalGovernanceServiceGrpc;
import br.dev.ispchat.grpc.RegisterPartnerIspRequest;
import br.dev.ispchat.grpc.RevocationEvent;
import br.dev.ispchat.grpc.RevokeCollaboratorRequest;
import br.dev.ispchat.grpc.RevokeDeviceRequest;
import br.dev.ispchat.grpc.SearchCnpjRequest;
import br.dev.ispchat.grpc.SearchCnpjResponse;
import br.dev.ispchat.grpc.ServerEnvelope;
import br.dev.ispchat.grpc.StreamRevocationsRequest;
import br.dev.ispchat.grpc.SubmitCpfRebindRequest;
import br.dev.ispchat.grpc.SubmitVerificationRequest;
import br.dev.ispchat.grpc.VerificationBundle;
import br.dev.ispchat.grpc.VerificationCredential;
import br.dev.ispchat.grpc.VerifiedCorporateEntry;
import br.dev.ispchat.node.dht.DhtStoreService;
import br.dev.ispchat.node.grpc.GrpcChannelPool;
import br.dev.ispchat.node.grpc.GrpcServerRunner;
import br.dev.ispchat.node.messaging.LocalSessionRegistry;
import br.dev.ispchat.node.security.CryptoService;
import br.dev.ispchat.node.security.MtlsFederationInterceptor;
import br.dev.ispchat.node.security.TierRateLimiterService;
import br.dev.ispchat.node.security.TierRateLimiterService.AccountTier;
import br.dev.ispchat.node.security.V2GovernanceTestFixtures;
import br.dev.ispchat.node.security.V2GovernanceTestFixtures.GovernancePki;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EdgeGovernanceAndRevocationSyncTest {

    private static final Instant T0 = Instant.parse("2026-11-12T12:00:00Z");

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;

        MutableClock(Instant initial) {
            this.current = new AtomicReference<>(initial);
        }

        void advance(Duration delta) {
            current.updateAndGet(now -> now.plus(delta));
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }

    private MutableClock clock;
    private GovernancePki pki;
    private CryptoService cryptoService;
    private DhtStoreService edgeDhtStore;
    private TierRateLimiterService edgeRateLimiter;
    private LocalSessionRegistry edgeSessionRegistry;
    private GrpcChannelPool channelPool;
    private Server upstreamPrincipalServer;
    private GrpcServerRunner edgeServerRunner;
    private RevocationSyncWorker syncWorker;

    private final AtomicInteger directoryUpstreamHits = new AtomicInteger(0);
    private final List<RevocationEvent> queuedRevocationStream = new CopyOnWriteArrayList<>();

    private File resolveCert(String relativePath) {
        for (String prefix : new String[]{"", "../", "../../"}) {
            File f = new File(prefix + relativePath);
            if (f.exists()) {
                return f;
            }
        }
        throw new IllegalStateException("Certificado de teste nao encontrado: " + relativePath);
    }

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        pki = GovernancePki.create(T0.toEpochMilli());
        cryptoService = new CryptoService(clock, pki.rootEd25519().publicKey(), "");
        cryptoService.registerTrustedPrincipalPublicKey(pki.principalEd25519().publicKey(), Long.MAX_VALUE);

        edgeRateLimiter = new TierRateLimiterService(cryptoService);
        edgeDhtStore = new DhtStoreService(cryptoService, null, edgeRateLimiter);
        edgeSessionRegistry = new LocalSessionRegistry();
        channelPool = new GrpcChannelPool(true, "../ca/ca.crt", "../ca/dev-node.crt", "../ca/dev-node.key");
    }

    @AfterEach
    void tearDown() {
        if (syncWorker != null) {
            syncWorker.stop();
        }
        if (edgeServerRunner != null) {
            edgeServerRunner.stop();
        }
        if (upstreamPrincipalServer != null) {
            upstreamPrincipalServer.shutdownNow();
        }
        if (channelPool != null) {
            channelPool.shutdownAll();
        }
    }

    @Test
    void edgeProxyAutoConfigurationRespectsConditionalOnMissingBean() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(EdgeProxyAutoConfiguration.class))
                .withBean(GrpcChannelPool.class, () -> channelPool)
                .withBean(DhtStoreService.class, () -> edgeDhtStore)
                .withBean(CryptoService.class, () -> cryptoService)
                .withPropertyValues(
                        "ispchat.principal.address=localhost:18443",
                        "ispchat.node.public-address=localhost:8443");

        // Cenario 1 (:ispchat-node): registra ambos os proxies de borda
        runner.run(ctx -> {
            assertTrue(ctx.containsBean("edgeGovernanceProxyGrpcService"));
            assertTrue(ctx.containsBean("edgeDirectoryProxyGrpcService"));
        });

        // Cenario 2 (:ispchat-principal): omite os proxies quando os servicos primarios ja existem
        runner.withBean(
                        "primaryGovernance",
                        PrincipalGovernanceServiceGrpc.PrincipalGovernanceServiceImplBase.class,
                        () -> new PrincipalGovernanceServiceGrpc.PrincipalGovernanceServiceImplBase() {
                        })
                .withBean(
                        "primaryDirectory",
                        DirectoryServiceGrpc.DirectoryServiceImplBase.class,
                        () -> new DirectoryServiceGrpc.DirectoryServiceImplBase() {
                        })
                .run(ctx -> {
                    assertFalse(ctx.containsBean("edgeGovernanceProxyGrpcService"));
                    assertFalse(ctx.containsBean("edgeDirectoryProxyGrpcService"));
                });
    }

    @Test
    void edgeGovernanceAndDirectoryProxiesForwardOverMtlsAndCacheLocally() throws Exception {
        long nowMs = clock.millis();
        RawKeyPair userEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair userX = CanonicalTranscripts.generateX25519KeyPair();
        String userUuid = CanonicalTranscripts.deriveIdentityUuidV7(userEd.publicKey()).toString();

        RawKeyPair reboundEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair reboundX = CanonicalTranscripts.generateX25519KeyPair();
        String reboundUuid = CanonicalTranscripts.deriveIdentityUuidV7(reboundEd.publicKey()).toString();

        VerificationCredential verifiedCred = pki.issueCredential(
                userEd, userX, DocumentType.CPF, "localhost:8443", 1L, nowMs);
        HomeNodeClaim initialClaim = pki.signPrincipalHomeClaim(userUuid, "localhost:8443", 1L, nowMs);

        VerificationCredential reboundCred = pki.issueCredential(
                reboundEd, reboundX, DocumentType.CPF, "localhost:8443", 1L, nowMs);
        HomeNodeClaim reboundClaim = pki.signPrincipalHomeClaim(reboundUuid, "localhost:8443", 1L, nowMs);
        IdentityReplacementNotice replacementNotice = pki.signReplacementNotice(userUuid, reboundUuid, nowMs);

        VerificationCredential portedCred = pki.issueCredential(
                userEd, userX, DocumentType.CPF, "isp-destino.net.br:443", 2L, nowMs);
        HomeNodeClaim portedClaim = pki.signPrincipalHomeClaim(userUuid, "isp-destino.net.br:443", 2L, nowMs);

        MtlsFederationInterceptor interceptor = new MtlsFederationInterceptor();
        upstreamPrincipalServer = NettyServerBuilder.forPort(0)
                .sslContext(GrpcServerRunner.buildServerSslContext(
                        resolveCert("ca/ca.crt"),
                        resolveCert("ca/dev-node.crt"),
                        resolveCert("ca/dev-node.key")))
                .addService(ServerInterceptors.intercept(new PrincipalGovernanceServiceGrpc.PrincipalGovernanceServiceImplBase() {
                    @Override
                    public void authorizeCollaborator(
                            AuthorizeCollaboratorRequest request,
                            StreamObserver<CollaboratorAuthCert> responseObserver) {
                        responseObserver.onNext(CollaboratorAuthCert.newBuilder()
                                .setCertId("collab-cert-1")
                                .setIspCnpj(request.getCert().getIspCnpj())
                                .build());
                        responseObserver.onCompleted();
                    }

                    @Override
                    public void revokeCollaborator(
                            RevokeCollaboratorRequest request,
                            StreamObserver<RevocationEvent> responseObserver) {
                        responseObserver.onNext(pki.signRevocationEvent(
                                1L, request.getCollaboratorUuid(), "", request.getReason(), null, nowMs));
                        responseObserver.onCompleted();
                    }

                    @Override
                    public void submitVerification(
                            SubmitVerificationRequest request,
                            StreamObserver<VerificationBundle> responseObserver) {
                        responseObserver.onNext(VerificationBundle.newBuilder()
                                .setCredential(verifiedCred)
                                .setHomeNodeClaim(initialClaim)
                                .build());
                        responseObserver.onCompleted();
                    }

                    @Override
                    public void submitCpfRebind(
                            SubmitCpfRebindRequest request,
                            StreamObserver<VerificationBundle> responseObserver) {
                        responseObserver.onNext(VerificationBundle.newBuilder()
                                .setCredential(reboundCred)
                                .setHomeNodeClaim(reboundClaim)
                                .setReplacementNotice(replacementNotice)
                                .build());
                        responseObserver.onCompleted();
                    }

                    @Override
                    public void getOrRenewCredential(
                            GetCredentialRequest request,
                            StreamObserver<VerificationBundle> responseObserver) {
                        responseObserver.onNext(VerificationBundle.newBuilder()
                                .setCredential(verifiedCred)
                                .setHomeNodeClaim(initialClaim)
                                .build());
                        responseObserver.onCompleted();
                    }

                    @Override
                    public void revokeDevice(
                            RevokeDeviceRequest request,
                            StreamObserver<RevocationEvent> responseObserver) {
                        responseObserver.onNext(pki.signRevocationEvent(
                                2L, request.getUserUuid(), request.getRevokedDeviceId(), request.getReason(), null, nowMs));
                        responseObserver.onCompleted();
                    }

                    @Override
                    public void portAccount(
                            PortAccountRequest request,
                            StreamObserver<VerificationBundle> responseObserver) {
                        responseObserver.onNext(VerificationBundle.newBuilder()
                                .setCredential(portedCred)
                                .setHomeNodeClaim(portedClaim)
                                .build());
                        responseObserver.onCompleted();
                    }
                }, interceptor))
                .addService(ServerInterceptors.intercept(new DirectoryServiceGrpc.DirectoryServiceImplBase() {
                    @Override
                    public void searchVerifiedCnpj(
                            SearchCnpjRequest request,
                            StreamObserver<SearchCnpjResponse> responseObserver) {
                        directoryUpstreamHits.incrementAndGet();
                        responseObserver.onNext(SearchCnpjResponse.newBuilder()
                                .addEntries(VerifiedCorporateEntry.newBuilder()
                                        .setCorporateUuid(userUuid)
                                        .setCnpj(V2GovernanceTestFixtures.SAMPLE_CORP_CNPJ)
                                        .setDisplayName("Provedor Fibra S.A.")
                                        .setHomeNodeAddress("localhost:8443")
                                        .build())
                                .build());
                        responseObserver.onCompleted();
                    }
                }, interceptor))
                .build()
                .start();

        String upstreamAddr = "localhost:" + upstreamPrincipalServer.getPort();
        EdgeGovernanceProxyGrpcService govProxy = new EdgeGovernanceProxyGrpcService(
                channelPool, edgeDhtStore, upstreamAddr, "localhost:8443");
        EdgeDirectoryProxyGrpcService dirProxy = new EdgeDirectoryProxyGrpcService(
                channelPool, null, upstreamAddr, clock);

        // Inicia o GrpcServerRunner real do No ISP na porta 0 (efemera)
        edgeServerRunner = new GrpcServerRunner(List.of(govProxy, dirProxy), interceptor);
        ReflectionTestUtils.setField(edgeServerRunner, "port", 0);
        ReflectionTestUtils.setField(edgeServerRunner, "caCertPath", "../ca/ca.crt");
        ReflectionTestUtils.setField(edgeServerRunner, "nodeCertPath", "../ca/dev-node.crt");
        ReflectionTestUtils.setField(edgeServerRunner, "nodeKeyPath", "../ca/dev-node.key");
        edgeServerRunner.start();

        ManagedChannel edgeChannel = channelPool.getChannel("localhost:" + edgeServerRunner.getPort());
        PrincipalGovernanceServiceGrpc.PrincipalGovernanceServiceBlockingStub govStub =
                PrincipalGovernanceServiceGrpc.newBlockingStub(edgeChannel);
        DirectoryServiceGrpc.DirectoryServiceBlockingStub dirStub =
                DirectoryServiceGrpc.newBlockingStub(edgeChannel);

        // 1. RegisterPartnerIsp e StreamRevocations devem ser barrados no proxy de borda
        StatusRuntimeException regDenied = assertThrows(StatusRuntimeException.class,
                () -> govStub.registerPartnerIsp(RegisterPartnerIspRequest.getDefaultInstance()));
        assertEquals(Status.Code.PERMISSION_DENIED, regDenied.getStatus().getCode());

        StatusRuntimeException streamDenied = assertThrows(StatusRuntimeException.class, () -> {
            var it = govStub.streamRevocations(StreamRevocationsRequest.getDefaultInstance());
            it.hasNext();
        });
        assertEquals(Status.Code.PERMISSION_DENIED, streamDenied.getStatus().getCode());

        // 2. Repasse de AuthorizeCollaborator, RevokeCollaborator, SubmitVerification, GetOrRenewCredential, RevokeDevice
        CollaboratorAuthCert issuedCollab = govStub.authorizeCollaborator(
                AuthorizeCollaboratorRequest.newBuilder()
                        .setCert(CollaboratorAuthCert.newBuilder().setIspCnpj("11222333000181").build())
                        .build());
        assertEquals("collab-cert-1", issuedCollab.getCertId());
        assertEquals("11222333000181", issuedCollab.getIspCnpj());

        assertEquals(1L, govStub.revokeCollaborator(
                RevokeCollaboratorRequest.newBuilder().setCollaboratorUuid(userUuid).setReason("DESLIGAMENTO").build())
                .getRevocationSeq());

        VerificationBundle verifiedBundle = govStub.submitVerification(SubmitVerificationRequest.getDefaultInstance());
        assertEquals(userUuid, verifiedBundle.getCredential().getUserUuid());
        assertTrue(edgeDhtStore.getHomeNodeClaim(userUuid).isPresent());

        assertEquals(userUuid, govStub.getOrRenewCredential(GetCredentialRequest.getDefaultInstance())
                .getCredential().getUserUuid());

        String revokedDeviceUuid = CanonicalTranscripts.generateTemporalUuidV7().toString();
        assertEquals(2L, govStub.revokeDevice(RevokeDeviceRequest.newBuilder()
                .setUserUuid(userUuid).setRevokedDeviceId(revokedDeviceUuid).setReason("LOST").build()).getRevocationSeq());

        // 3. SubmitCpfRebind grava aviso de substituicao de identidade localmente
        VerificationBundle rebindBundle = govStub.submitCpfRebind(SubmitCpfRebindRequest.getDefaultInstance());
        assertEquals(reboundUuid, rebindBundle.getReplacementNotice().getNewUserUuid());
        assertTrue(edgeDhtStore.getReplacementNotice(userUuid).isPresent());

        // 4. PortAccount grava redirect tombstone de 30 dias quando migrado para outro ISP
        govStub.portAccount(PortAccountRequest.newBuilder().setUserUuid(userUuid).build());
        assertEquals("isp-destino.net.br:443", edgeDhtStore.getRedirectTombstone(userUuid).orElseThrow());

        // 5. DirectoryService faz cache local de 5 minutos e renova apos expiracao
        SearchCnpjResponse r1 = dirStub.searchVerifiedCnpj(
                SearchCnpjRequest.newBuilder().setQuery("Provedor").setLimit(10).build());
        SearchCnpjResponse r2 = dirStub.searchVerifiedCnpj(
                SearchCnpjRequest.newBuilder().setQuery("  provedor ").setLimit(10).build());
        assertEquals(1, r1.getEntriesCount());
        assertEquals(1, r2.getEntriesCount());
        assertEquals(1, directoryUpstreamHits.get(), "Segunda consulta dentro de 5 min deve vir do cache local");

        clock.advance(Duration.ofMinutes(6));
        dirStub.searchVerifiedCnpj(SearchCnpjRequest.newBuilder().setQuery("Provedor").setLimit(10).build());
        assertEquals(2, directoryUpstreamHits.get(), "Apos expirar TTL de 5 min deve consultar Principal novamente");

        // 6. Proxy sem endereco Principal configurado retorna UNAVAILABLE
        EdgeGovernanceProxyGrpcService unconfiguredProxy = new EdgeGovernanceProxyGrpcService(
                channelPool, edgeDhtStore, "", "localhost:8443");
        AtomicReference<Throwable> unconfiguredErr = new AtomicReference<>();
        unconfiguredProxy.authorizeCollaborator(AuthorizeCollaboratorRequest.getDefaultInstance(),
                new StreamObserver<>() {
                    @Override
                    public void onNext(CollaboratorAuthCert value) {
                    }

                    @Override
                    public void onError(Throwable t) {
                        unconfiguredErr.set(t);
                    }

                    @Override
                    public void onCompleted() {
                    }
                });
        assertNotNull(unconfiguredErr.get());
        assertEquals(Status.Code.UNAVAILABLE, Status.fromThrowable(unconfiguredErr.get()).getCode());
    }

    @Test
    void revocationSyncWorkerStreamsEventsRejectsForgeriesAndDisconnectsLocalSessions() throws Exception {
        long nowMs = clock.millis();
        RawKeyPair aliceEd = CanonicalTranscripts.generateEd25519KeyPair();
        String aliceUuid = CanonicalTranscripts.deriveIdentityUuidV7(aliceEd.publicKey()).toString();
        String desktopDeviceId = CanonicalTranscripts.generateTemporalUuidV7().toString();
        String mobileDeviceId = aliceUuid;

        RawKeyPair aliceNewEd = CanonicalTranscripts.generateEd25519KeyPair();
        String aliceNewUuid = CanonicalTranscripts.deriveIdentityUuidV7(aliceNewEd.publicKey()).toString();

        // Registra duas sessoes locais ativas para Alice (MOBILE_ROOT e DESKTOP)
        List<ServerEnvelope> desktopFrames = new CopyOnWriteArrayList<>();
        List<ServerEnvelope> mobileFrames = new CopyOnWriteArrayList<>();
        assertTrue(edgeSessionRegistry.tryRegisterDeviceStream(
                aliceUuid,
                desktopDeviceId,
                DeviceRole.DESKTOP,
                AccountTier.VERIFIED_CPF,
                new StreamObserver<>() {
                    @Override
                    public void onNext(ServerEnvelope value) {
                        desktopFrames.add(value);
                    }

                    @Override
                    public void onError(Throwable t) {
                    }

                    @Override
                    public void onCompleted() {
                    }
                }));
        assertTrue(edgeSessionRegistry.tryRegisterDeviceStream(
                aliceUuid,
                mobileDeviceId,
                DeviceRole.MOBILE_ROOT,
                AccountTier.VERIFIED_CPF,
                new StreamObserver<>() {
                    @Override
                    public void onNext(ServerEnvelope value) {
                        mobileFrames.add(value);
                    }

                    @Override
                    public void onError(Throwable t) {
                    }

                    @Override
                    public void onCompleted() {
                    }
                }));

        // Evento 1: revogacao apenas do DESKTOP
        RevocationEvent deviceRev = pki.signRevocationEvent(
                10L, aliceUuid, desktopDeviceId, "DESKTOP_COMPROMISED", null, nowMs);

        // Evento forjado (assinado por chave invasora) -> DEVE SER DESCARTADO
        RawKeyPair attackerKey = CanonicalTranscripts.generateEd25519KeyPair();
        RevocationEvent unsignedForgery = RevocationEvent.newBuilder()
                .setRevocationId(CanonicalTranscripts.generateTemporalUuidV7().toString())
                .setRevocationSeq(11L)
                .setTargetUuid(aliceUuid)
                .setReason("FORGED_ATTACK")
                .setCreatedAtMs(nowMs)
                .build();
        RevocationEvent forgedRev = unsignedForgery.toBuilder()
                .setPrincipalSignature(ByteString.copyFrom(
                        CanonicalTranscripts.signEd25519(
                                attackerKey.privateKey(),
                                CanonicalTranscripts.buildRevocationEventTranscript(unsignedForgery))))
                .build();

        // Evento 2: CPF Re-Bind (0h hold) revogando toda a conta antiga com IdentityReplacementNotice
        IdentityReplacementNotice notice = pki.signReplacementNotice(aliceUuid, aliceNewUuid, nowMs);
        RevocationEvent accountRebindRev = pki.signRevocationEvent(
                12L, aliceUuid, "", "CPF_REBIND", notice, nowMs);

        queuedRevocationStream.addAll(List.of(deviceRev, forgedRev, accountRebindRev));

        MtlsFederationInterceptor interceptor = new MtlsFederationInterceptor();
        upstreamPrincipalServer = NettyServerBuilder.forPort(0)
                .sslContext(GrpcServerRunner.buildServerSslContext(
                        resolveCert("ca/ca.crt"),
                        resolveCert("ca/dev-node.crt"),
                        resolveCert("ca/dev-node.key")))
                .addService(ServerInterceptors.intercept(new PrincipalGovernanceServiceGrpc.PrincipalGovernanceServiceImplBase() {
                    @Override
                    public void streamRevocations(
                            StreamRevocationsRequest request,
                            StreamObserver<RevocationEvent> responseObserver) {
                        for (RevocationEvent ev : queuedRevocationStream) {
                            if (ev.getRevocationSeq() > request.getSinceRevocationSeq()) {
                                responseObserver.onNext(ev);
                            }
                        }
                        responseObserver.onCompleted();
                    }
                }, interceptor))
                .build()
                .start();

        syncWorker = new RevocationSyncWorker(
                channelPool,
                cryptoService,
                edgeRateLimiter,
                edgeDhtStore,
                edgeSessionRegistry,
                null,
                null,
                "localhost:" + upstreamPrincipalServer.getPort(),
                "localhost:8443");

        // Rejeicao unitaria explicita da falsificacao
        assertFalse(syncWorker.applyRevocationEvent(forgedRev));
        assertFalse(edgeRateLimiter.isAccountRevoked(aliceUuid));

        // Inicia sincronizacao real via virtual thread sobre mTLS
        syncWorker.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (syncWorker.getLastAppliedSeq() < 12L && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }

        assertEquals(12L, syncWorker.getLastAppliedSeq());
        assertTrue(edgeRateLimiter.isDeviceRevoked(desktopDeviceId));
        assertTrue(edgeRateLimiter.isAccountRevoked(aliceUuid));
        assertEquals(0, edgeSessionRegistry.getActiveDeviceCount(aliceUuid));
        assertEquals(1, desktopFrames.size());
        assertEquals(MessageType.REVOCATION_NOTICE, desktopFrames.getFirst().getType());
        assertEquals(1, mobileFrames.size());
        assertEquals(MessageType.REVOCATION_NOTICE, mobileFrames.getFirst().getType());
        assertEquals(aliceNewUuid, edgeDhtStore.getReplacementNotice(aliceUuid).orElseThrow().getNewUserUuid());
    }
}
