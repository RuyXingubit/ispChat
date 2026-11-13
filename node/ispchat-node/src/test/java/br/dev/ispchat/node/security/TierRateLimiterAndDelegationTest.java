package br.dev.ispchat.node.security;

import br.dev.ispchat.common.crypto.CanonicalTranscripts;
import br.dev.ispchat.common.crypto.CanonicalTranscripts.RawKeyPair;
import br.dev.ispchat.grpc.ClientEnvelope;
import br.dev.ispchat.grpc.DeviceDelegationCert;
import br.dev.ispchat.grpc.DeviceRole;
import br.dev.ispchat.grpc.DirectoryServiceGrpc;
import br.dev.ispchat.grpc.DocumentType;
import br.dev.ispchat.grpc.IdentityReplacementNotice;
import br.dev.ispchat.grpc.MessageType;
import br.dev.ispchat.grpc.RevocationEvent;
import br.dev.ispchat.grpc.SearchCnpjRequest;
import br.dev.ispchat.grpc.SearchCnpjResponse;
import br.dev.ispchat.grpc.ServerEnvelope;
import br.dev.ispchat.grpc.StreamRevocationsRequest;
import br.dev.ispchat.grpc.VerificationCredential;
import br.dev.ispchat.grpc.VerifiedCorporateEntry;
import br.dev.ispchat.node.dht.DhtClientGrpcService;
import br.dev.ispchat.node.dht.DhtStoreService;
import br.dev.ispchat.node.dht.KademliaRoutingTable;
import br.dev.ispchat.node.governance.EdgeDirectoryProxyGrpcService;
import br.dev.ispchat.node.governance.EdgeGovernanceProxyGrpcService;
import br.dev.ispchat.node.governance.RevocationSyncWorker;
import br.dev.ispchat.node.grpc.GrpcChannelPool;
import br.dev.ispchat.node.grpc.GrpcServerRunner;
import br.dev.ispchat.node.messaging.ClientStreamGrpcService;
import br.dev.ispchat.node.messaging.LocalSessionRegistry;
import br.dev.ispchat.node.messaging.MessageRouterService;
import br.dev.ispchat.node.messaging.OfflineMessageQueueService;
import br.dev.ispchat.node.security.V2GovernanceTestFixtures.GovernancePki;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TierRateLimiterAndDelegationTest {

    private static final long FIXED_NOW_MS = 1762900000000L;

    @Test
    @DisplayName("CUJ 1: Conta nao verificada enviando DeviceDelegationCert deve ser barrada com PERMISSION_DENIED")
    void unverifiedAccountSubmittingDelegationCertShouldFailWithPermissionDenied() throws Exception {
        Clock clock = Clock.fixed(Instant.ofEpochMilli(FIXED_NOW_MS), ZoneOffset.UTC);
        GovernancePki pki = GovernancePki.create(FIXED_NOW_MS);
        CryptoService crypto = new CryptoService(clock, pki.rootEd25519().publicKey(), "fallback.ispchat.dev.br:443");
        TierRateLimiterService rateLimiter = new TierRateLimiterService(crypto);
        LocalSessionRegistry registry = new LocalSessionRegistry();
        OfflineMessageQueueService queue = new OfflineMessageQueueService();
        KademliaRoutingTable table = new KademliaRoutingTable("01950000-0000-7000-8000-000000000001");
        DhtStoreService dhtStore = new DhtStoreService(crypto, null, rateLimiter);
        GrpcChannelPool pool = new GrpcChannelPool(true, "../ca/ca.crt", "../ca/dev-node.crt", "../ca/dev-node.key");
        MessageRouterService router = new MessageRouterService(registry, queue, dhtStore, table, pool, crypto);
        ClientStreamGrpcService streamSvc = new ClientStreamGrpcService(crypto, registry, queue, router, rateLimiter);
        DhtClientGrpcService dhtClientSvc = new DhtClientGrpcService(dhtStore, table, pool, crypto, rateLimiter);

        RawKeyPair unverifiedRoot = CanonicalTranscripts.generateEd25519KeyPair();
        String userUuid = CanonicalTranscripts.deriveIdentityUuidV7(unverifiedRoot.publicKey()).toString();
        RawKeyPair desktopEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair desktopX = CanonicalTranscripts.generateX25519KeyPair();
        DeviceDelegationCert illegalDelegation = V2GovernanceTestFixtures.signDeviceDelegation(
                unverifiedRoot, desktopEd, desktopX, DeviceRole.DESKTOP, FIXED_NOW_MS);

        // 1. Rejeicao PERMISSION_DENIED no DhtClientService.RegisterSession
        AtomicReference<Throwable> dhtErr = new AtomicReference<>();
        dhtClientSvc.registerSession(
                V2GovernanceTestFixtures.buildV2RegisterRequest(
                        userUuid,
                        illegalDelegation.getDeviceId(),
                        "node1.isp.com:443",
                        unverifiedRoot.publicKey(),
                        desktopEd,
                        desktopX,
                        illegalDelegation,
                        null,
                        null,
                        FIXED_NOW_MS),
                new StreamObserver<>() {
                    @Override public void onNext(br.dev.ispchat.grpc.RegisterSessionResponse value) {}
                    @Override public void onError(Throwable t) { dhtErr.set(t); }
                    @Override public void onCompleted() {}
                });
        assertNotNull(dhtErr.get());
        assertEquals(Status.Code.PERMISSION_DENIED, ((StatusRuntimeException) dhtErr.get()).getStatus().getCode());

        // 2. Rejeicao PERMISSION_DENIED no ClientStreamService.Connect
        AtomicReference<Throwable> streamErr = new AtomicReference<>();
        StreamObserver<ClientEnvelope> in = streamSvc.connect(new StreamObserver<>() {
            @Override public void onNext(ServerEnvelope value) {}
            @Override public void onError(Throwable t) { streamErr.set(t); }
            @Override public void onCompleted() {}
        });
        in.onNext(V2GovernanceTestFixtures.buildSignedV2Envelope(
                CanonicalTranscripts.generateTemporalUuidV7().toString(),
                userUuid,
                illegalDelegation.getDeviceId(),
                userUuid,
                MessageType.AUTH_HANDSHAKE,
                "",
                new byte[0],
                desktopEd,
                illegalDelegation,
                null,
                FIXED_NOW_MS,
                true));

        assertNotNull(streamErr.get());
        assertEquals(Status.Code.PERMISSION_DENIED, ((StatusRuntimeException) streamErr.get()).getStatus().getCode());
        pool.shutdownAll();
    }

    @Test
    @DisplayName("CUJ 1: Segunda stream concorrente de conta nao verificada deve falhar com RESOURCE_EXHAUSTED mantendo a primeira")
    void unverifiedAccountSecondConcurrentStreamShouldFailWithResourceExhausted() {
        Clock clock = Clock.fixed(Instant.ofEpochMilli(FIXED_NOW_MS), ZoneOffset.UTC);
        CryptoService crypto = new CryptoService(clock);
        TierRateLimiterService rateLimiter = new TierRateLimiterService(crypto);
        LocalSessionRegistry registry = new LocalSessionRegistry();
        OfflineMessageQueueService queue = new OfflineMessageQueueService();
        KademliaRoutingTable table = new KademliaRoutingTable("01950000-0000-7000-8000-000000000001");
        DhtStoreService dhtStore = new DhtStoreService(crypto, null, rateLimiter);
        GrpcChannelPool pool = new GrpcChannelPool(true, "../ca/ca.crt", "../ca/dev-node.crt", "../ca/dev-node.key");
        MessageRouterService router = new MessageRouterService(registry, queue, dhtStore, table, pool, crypto);
        ClientStreamGrpcService streamSvc = new ClientStreamGrpcService(crypto, registry, queue, router, rateLimiter);

        RawKeyPair unverifiedKey = CanonicalTranscripts.generateEd25519KeyPair();
        String userUuid = CanonicalTranscripts.deriveIdentityUuidV7(unverifiedKey.publicKey()).toString();

        AtomicReference<Throwable> firstErr = new AtomicReference<>();
        StreamObserver<ClientEnvelope> firstStream = streamSvc.connect(new StreamObserver<>() {
            @Override public void onNext(ServerEnvelope value) {}
            @Override public void onError(Throwable t) { firstErr.set(t); }
            @Override public void onCompleted() {}
        });
        firstStream.onNext(V2GovernanceTestFixtures.buildSignedV2Envelope(
                CanonicalTranscripts.generateTemporalUuidV7().toString(),
                userUuid, userUuid, userUuid, MessageType.AUTH_HANDSHAKE, "",
                new byte[0], unverifiedKey, null, null, FIXED_NOW_MS, true));
        assertNull(firstErr.get());
        assertEquals(1, registry.getActiveDeviceCount(userUuid));

        // Tentativa de abrir segunda stream simultanea (ex: clonar as 12 palavras em Desktop)
        AtomicReference<Throwable> secondErr = new AtomicReference<>();
        StreamObserver<ClientEnvelope> secondStream = streamSvc.connect(new StreamObserver<>() {
            @Override public void onNext(ServerEnvelope value) {}
            @Override public void onError(Throwable t) { secondErr.set(t); }
            @Override public void onCompleted() {}
        });
        secondStream.onNext(V2GovernanceTestFixtures.buildSignedV2Envelope(
                CanonicalTranscripts.generateTemporalUuidV7().toString(),
                userUuid, userUuid, userUuid, MessageType.AUTH_HANDSHAKE, "",
                new byte[0], unverifiedKey, null, null, FIXED_NOW_MS, true));

        assertNotNull(secondErr.get());
        assertEquals(Status.Code.RESOURCE_EXHAUSTED, ((StatusRuntimeException) secondErr.get()).getStatus().getCode());
        assertEquals(1, registry.getActiveDeviceCount(userUuid), "Primeira stream legitima deve permanecer conectada");

        firstStream.onCompleted();
        assertEquals(0, registry.getActiveDeviceCount(userUuid));
        pool.shutdownAll();
    }

    @Test
    @DisplayName("Deve normalizar prefixos /32 IPv4 e /64 IPv6 e aplicar limites de taxa e teto de 20 novos contatos em 24h")
    void shouldEnforcePrefixBucketsMessageRateAnd24hDistinctPeers() {
        Clock clock = Clock.fixed(Instant.ofEpochMilli(FIXED_NOW_MS), ZoneOffset.UTC);
        GovernancePki pki = GovernancePki.create(FIXED_NOW_MS);
        CryptoService crypto = new CryptoService(clock, pki.rootEd25519().publicKey(), "fallback.ispchat.dev.br:443");
        TierRateLimiterService rateLimiter = new TierRateLimiterService(crypto);

        // 1. Dois IPs distintos dentro do mesmo /64 IPv6 devem compartilhar o mesmo balde anti-Sybil
        String ipv6HostA = TierRateLimiterService.normalizeIpPrefix("2001:db8:abcd:1234:1111:2222:3333:4444");
        String ipv6HostB = TierRateLimiterService.normalizeIpPrefix("2001:db8:abcd:1234:ffff:eeee:dddd:cccc");
        assertEquals(ipv6HostA, ipv6HostB);
        assertEquals("v6:20010db8abcd1234/64", ipv6HostA);
        assertEquals("v4:203.0.113.9/32", TierRateLimiterService.normalizeIpPrefix("::ffff:203.0.113.9"));

        for (int i = 0; i < 10; i++) {
            assertTrue(rateLimiter.checkUnverifiedRegistrationPrefixAllowed(ipv6HostA));
        }
        assertFalse(rateLimiter.checkUnverifiedRegistrationPrefixAllowed(ipv6HostB),
                "11o registro Unverified no mesmo /64 IPv6 em 1h deve ser bloqueado");

        for (int i = 0; i < 30; i++) {
            assertTrue(rateLimiter.checkHandshakePrefixAllowed("v4:198.51.100.42/32"));
        }
        assertFalse(rateLimiter.checkHandshakePrefixAllowed("v4:198.51.100.42/32"));

        // 2. Limite de 10 mensagens/min para Unverified vs 600/min para CNPJ Verificado
        String unvSender = CanonicalTranscripts.generateTemporalUuidV7().toString();
        String fixedPeer = CanonicalTranscripts.generateTemporalUuidV7().toString();
        for (int i = 0; i < 10; i++) {
            assertTrue(rateLimiter.checkOutgoingEnvelopeAllowed(
                    unvSender, TierRateLimiterService.AccountTier.UNVERIFIED, MessageType.TEXT, fixedPeer));
        }
        assertFalse(rateLimiter.checkOutgoingEnvelopeAllowed(
                unvSender, TierRateLimiterService.AccountTier.UNVERIFIED, MessageType.TEXT, fixedPeer),
                "11a mensagem no mesmo minuto de conta nao verificada deve ser bloqueada");

        String corpSender = CanonicalTranscripts.generateTemporalUuidV7().toString();
        for (int i = 0; i < 50; i++) {
            assertTrue(rateLimiter.checkOutgoingEnvelopeAllowed(
                    corpSender, TierRateLimiterService.AccountTier.VERIFIED_CNPJ, MessageType.TEXT, fixedPeer));
        }

        // 3. Limite movel de 20 novos destinatarios distintos em 24h para Unverified
        TierRateLimiterService highMsgQuotaLimiter = new TierRateLimiterService(
                crypto, null, null, 500, 500, 600, 20);
        String socialUnvSender = CanonicalTranscripts.generateTemporalUuidV7().toString();
        List<String> twentyPeers = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String peer = CanonicalTranscripts.generateTemporalUuidV7().toString();
            twentyPeers.add(peer);
            assertTrue(highMsgQuotaLimiter.checkOutgoingEnvelopeAllowed(
                    socialUnvSender, TierRateLimiterService.AccountTier.UNVERIFIED, MessageType.TEXT, peer));
        }
        // Conversar novamente com um dos 20 contatos ja estabelecidos continua permitido
        assertTrue(highMsgQuotaLimiter.checkOutgoingEnvelopeAllowed(
                socialUnvSender, TierRateLimiterService.AccountTier.UNVERIFIED, MessageType.TEXT, twentyPeers.get(0)));

        // Tentar iniciar conversa com um 21o destinatario distinto dentro de 24h e bloqueado
        String twentyFirstPeer = CanonicalTranscripts.generateTemporalUuidV7().toString();
        assertFalse(highMsgQuotaLimiter.checkOutgoingEnvelopeAllowed(
                socialUnvSender, TierRateLimiterService.AccountTier.UNVERIFIED, MessageType.TEXT, twentyFirstPeer));
    }

    @Test
    @DisplayName("CUJ 3 & CUJ 4: Conta CPF verificada aceita ate 5 dispositivos (bloqueia ERP_API) e CNPJ aceita ERP_API + Desktops ate 10")
    void shouldEnforceVerifiedDeviceCapsAndRoleRestrictions() {
        Clock clock = Clock.fixed(Instant.ofEpochMilli(FIXED_NOW_MS), ZoneOffset.UTC);
        GovernancePki pki = GovernancePki.create(FIXED_NOW_MS);
        CryptoService crypto = new CryptoService(clock, pki.rootEd25519().publicKey(), "fallback.ispchat.dev.br:443");
        TierRateLimiterService rateLimiter = new TierRateLimiterService(crypto);
        LocalSessionRegistry registry = new LocalSessionRegistry();

        // Conta CPF verificada tentando usar ERP_API -> PERMISSION_DENIED
        RawKeyPair cpfRootEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair cpfRootX = CanonicalTranscripts.generateX25519KeyPair();
        VerificationCredential cpfCred = pki.issueCredential(
                cpfRootEd, cpfRootX, DocumentType.CPF, "node1.isp.com:443", 1L, FIXED_NOW_MS);

        RawKeyPair illegalErpEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair illegalErpX = CanonicalTranscripts.generateX25519KeyPair();
        DeviceDelegationCert illegalErpCert = V2GovernanceTestFixtures.signDeviceDelegation(
                cpfRootEd, illegalErpEd, illegalErpX, DeviceRole.ERP_API, FIXED_NOW_MS);

        StatusRuntimeException erpOnCpfEx = assertThrows(StatusRuntimeException.class, () ->
                rateLimiter.validateSessionCredentials(
                        cpfCred.getUserUuid(),
                        illegalErpCert.getDeviceId(),
                        illegalErpEd.publicKey(),
                        illegalErpCert,
                        cpfCred));
        assertEquals(Status.Code.PERMISSION_DENIED, erpOnCpfEx.getStatus().getCode());

        // CPF Verificado permite ate 5 dispositivos simultaneos e rejeita o 6o
        for (int i = 0; i < 5; i++) {
            String devId = (i == 0)
                    ? cpfCred.getUserUuid()
                    : CanonicalTranscripts.generateTemporalUuidV7().toString();
            assertTrue(registry.tryRegisterDeviceStream(
                    cpfCred.getUserUuid(),
                    devId,
                    i == 0 ? DeviceRole.MOBILE_ROOT : DeviceRole.DESKTOP,
                    TierRateLimiterService.AccountTier.VERIFIED_CPF,
                    new NoopObserver()));
        }
        assertFalse(registry.tryRegisterDeviceStream(
                cpfCred.getUserUuid(),
                CanonicalTranscripts.generateTemporalUuidV7().toString(),
                DeviceRole.DESKTOP,
                TierRateLimiterService.AccountTier.VERIFIED_CPF,
                new NoopObserver()), "6o dispositivo em conta CPF deve retornar false (RESOURCE_EXHAUSTED)");

        // CNPJ Verificado aceita ERP_API e ate 10 dispositivos
        RawKeyPair cnpjRootEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair cnpjRootX = CanonicalTranscripts.generateX25519KeyPair();
        VerificationCredential cnpjCred = pki.issueCredential(
                cnpjRootEd, cnpjRootX, DocumentType.CNPJ, "node1.isp.com:443", 1L, FIXED_NOW_MS);
        RawKeyPair validErpEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair validErpX = CanonicalTranscripts.generateX25519KeyPair();
        DeviceDelegationCert validErpCert = V2GovernanceTestFixtures.signDeviceDelegation(
                cnpjRootEd, validErpEd, validErpX, DeviceRole.ERP_API, FIXED_NOW_MS);

        TierRateLimiterService.ValidatedSessionIdentity validatedErp = rateLimiter.validateSessionCredentials(
                cnpjCred.getUserUuid(),
                validErpCert.getDeviceId(),
                validErpEd.publicKey(),
                validErpCert,
                cnpjCred);
        assertEquals(TierRateLimiterService.AccountTier.VERIFIED_CNPJ, validatedErp.tier());
        assertEquals(DeviceRole.ERP_API, validatedErp.role());
    }

    @Test
    @DisplayName("CUJ 6: RevocationSyncWorker desconecta imediatamente dispositivo/conta com REVOCATION_NOTICE e emite IDENTITY_REPLACED")
    void revocationSyncShouldDisconnectTargetAndEmitIdentityReplacedOnCpfRebind() throws Exception {
        Clock clock = Clock.fixed(Instant.ofEpochMilli(FIXED_NOW_MS), ZoneOffset.UTC);
        GovernancePki pki = GovernancePki.create(FIXED_NOW_MS);
        CryptoService crypto = new CryptoService(clock, pki.rootEd25519().publicKey(), "fallback.ispchat.dev.br:443");
        crypto.verifyPrincipalSigningCert(pki.principalCert());

        TierRateLimiterService rateLimiter = new TierRateLimiterService(crypto);
        LocalSessionRegistry registry = new LocalSessionRegistry();
        OfflineMessageQueueService queue = new OfflineMessageQueueService();
        KademliaRoutingTable table = new KademliaRoutingTable("01950000-0000-7000-8000-000000000001");
        DhtStoreService dhtStore = new DhtStoreService(crypto, null, rateLimiter);
        GrpcChannelPool pool = new GrpcChannelPool(true, "../ca/ca.crt", "../ca/dev-node.crt", "../ca/dev-node.key");
        MessageRouterService router = new MessageRouterService(registry, queue, dhtStore, table, pool, crypto);
        ClientStreamGrpcService streamSvc = new ClientStreamGrpcService(crypto, registry, queue, router, rateLimiter);
        RevocationSyncWorker revocationWorker = new RevocationSyncWorker(pool, crypto, rateLimiter, dhtStore, registry);

        RawKeyPair carlosOldRoot = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair carlosOldX = CanonicalTranscripts.generateX25519KeyPair();
        VerificationCredential carlosCred = pki.issueCredential(
                carlosOldRoot, carlosOldX, DocumentType.CPF, "node1.isp.com:443", 1L, FIXED_NOW_MS);
        String oldUuid = carlosCred.getUserUuid();

        RawKeyPair desktopEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair desktopX = CanonicalTranscripts.generateX25519KeyPair();
        DeviceDelegationCert desktopCert = V2GovernanceTestFixtures.signDeviceDelegation(
                carlosOldRoot, desktopEd, desktopX, DeviceRole.DESKTOP, FIXED_NOW_MS);

        BlockingQueue<ServerEnvelope> mobileFrames = new LinkedBlockingQueue<>();
        BlockingQueue<ServerEnvelope> desktopFrames = new LinkedBlockingQueue<>();
        CountDownLatch desktopClosed = new CountDownLatch(1);
        CountDownLatch mobileClosed = new CountDownLatch(1);

        StreamObserver<ClientEnvelope> mobileIn = streamSvc.connect(new StreamObserver<>() {
            @Override public void onNext(ServerEnvelope value) { mobileFrames.add(value); }
            @Override public void onError(Throwable t) {}
            @Override public void onCompleted() { mobileClosed.countDown(); }
        });
        mobileIn.onNext(V2GovernanceTestFixtures.buildSignedV2Envelope(
                CanonicalTranscripts.generateTemporalUuidV7().toString(),
                oldUuid, oldUuid, oldUuid, MessageType.AUTH_HANDSHAKE, "",
                new byte[0], carlosOldRoot, null, carlosCred, FIXED_NOW_MS, true));

        StreamObserver<ClientEnvelope> desktopIn = streamSvc.connect(new StreamObserver<>() {
            @Override public void onNext(ServerEnvelope value) { desktopFrames.add(value); }
            @Override public void onError(Throwable t) {}
            @Override public void onCompleted() { desktopClosed.countDown(); }
        });
        desktopIn.onNext(V2GovernanceTestFixtures.buildSignedV2Envelope(
                CanonicalTranscripts.generateTemporalUuidV7().toString(),
                oldUuid, desktopCert.getDeviceId(), oldUuid, MessageType.AUTH_HANDSHAKE, "",
                new byte[0], desktopEd, desktopCert, carlosCred, FIXED_NOW_MS, true));

        assertEquals(2, registry.getActiveDeviceCount(oldUuid));

        // 1. Revoga apenas o Desktop secundario -> Mobile permanece conectado
        RevocationEvent devRevoke = pki.signRevocationEvent(
                1L, oldUuid, desktopCert.getDeviceId(), "WORKSTATION_RETIRED", null, FIXED_NOW_MS);
        assertTrue(revocationWorker.applyRevocationEvent(devRevoke));
        assertTrue(desktopClosed.await(2, TimeUnit.SECONDS));
        ServerEnvelope deskNotice = desktopFrames.poll(1, TimeUnit.SECONDS);
        assertNotNull(deskNotice);
        assertEquals(MessageType.REVOCATION_NOTICE, deskNotice.getType());
        assertEquals(1, registry.getActiveDeviceCount(oldUuid));

        // 2. CPF Re-Bind (perda de celular): revoga oldUuid inteiro e publica IdentityReplacementNotice -> newUuid
        String newUuid = CanonicalTranscripts.generateTemporalUuidV7().toString();
        IdentityReplacementNotice replacement = pki.signReplacementNotice(oldUuid, newUuid, FIXED_NOW_MS);
        RevocationEvent cpfRebindEvent = pki.signRevocationEvent(
                2L, oldUuid, "", "CPF_REBIND", replacement, FIXED_NOW_MS);
        assertTrue(revocationWorker.applyRevocationEvent(cpfRebindEvent));
        assertTrue(mobileClosed.await(2, TimeUnit.SECONDS));
        ServerEnvelope mobNotice = mobileFrames.poll(1, TimeUnit.SECONDS);
        assertNotNull(mobNotice);
        assertEquals(MessageType.REVOCATION_NOTICE, mobNotice.getType());
        assertEquals(0, registry.getActiveDeviceCount(oldUuid));
        assertEquals(2L, revocationWorker.getLastAppliedSeq());

        // 3. Quando Alice envia mensagem para oldUuid, recebe imediatamente IDENTITY_REPLACED apontando para newUuid
        RawKeyPair aliceKey = CanonicalTranscripts.generateEd25519KeyPair();
        String aliceUuid = CanonicalTranscripts.deriveIdentityUuidV7(aliceKey.publicKey()).toString();
        BlockingQueue<ServerEnvelope> aliceInbox = new LinkedBlockingQueue<>();
        StreamObserver<ClientEnvelope> aliceIn = streamSvc.connect(new StreamObserver<>() {
            @Override public void onNext(ServerEnvelope value) { aliceInbox.add(value); }
            @Override public void onError(Throwable t) {}
            @Override public void onCompleted() {}
        });
        aliceIn.onNext(V2GovernanceTestFixtures.buildSignedV2Envelope(
                CanonicalTranscripts.generateTemporalUuidV7().toString(),
                aliceUuid, aliceUuid, aliceUuid, MessageType.AUTH_HANDSHAKE, "",
                new byte[0], aliceKey, null, null, FIXED_NOW_MS, true));

        String msgToOld = CanonicalTranscripts.generateTemporalUuidV7().toString();
        aliceIn.onNext(V2GovernanceTestFixtures.buildSignedV2Envelope(
                msgToOld, aliceUuid, aliceUuid, oldUuid, MessageType.TEXT, "",
                "oi-carlos".getBytes(StandardCharsets.UTF_8), aliceKey, null, null, FIXED_NOW_MS, false));

        ServerEnvelope aliceFeedback = aliceInbox.poll(2, TimeUnit.SECONDS);
        assertNotNull(aliceFeedback);
        assertEquals(MessageType.IDENTITY_REPLACED, aliceFeedback.getType());
        assertEquals(newUuid, aliceFeedback.getReplacementNotice().getNewUserUuid());
        assertEquals(msgToOld, aliceFeedback.getRefMessageId());

        aliceIn.onCompleted();
        pool.shutdownAll();
    }

    @Test
    @DisplayName("Deve extrair SANs do certificado X.509 e cachear SearchVerifiedCnpj por 5 minutos no EdgeDirectoryProxy")
    void shouldExtractX509SansAndCacheDirectoryLookupsForFiveMinutes() throws Exception {
        File caCert = resolveFile("../ca/ca.crt", "ca/ca.crt");
        File nodeCert = resolveFile("../ca/dev-node.crt", "ca/dev-node.crt");
        File nodeKey = resolveFile("../ca/dev-node.key", "ca/dev-node.key");

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (FileInputStream fis = new FileInputStream(nodeCert)) {
            X509Certificate cert = (X509Certificate) cf.generateCertificate(fis);
            Set<String> sans = MtlsFederationInterceptor.extractPeerSans(cert);
            assertFalse(sans.isEmpty(), "Certificado de no deve conter SANs DNS/IP");
            assertTrue(sans.contains("127.0.0.1") || sans.contains("localhost"));
        }

        AtomicInteger upstreamCalls = new AtomicInteger(0);
        DirectoryServiceGrpc.DirectoryServiceImplBase fakePrincipalDir =
                new DirectoryServiceGrpc.DirectoryServiceImplBase() {
                    @Override
                    public void searchVerifiedCnpj(
                            SearchCnpjRequest request,
                            StreamObserver<SearchCnpjResponse> responseObserver) {
                        upstreamCalls.incrementAndGet();
                        responseObserver.onNext(SearchCnpjResponse.newBuilder()
                                .addEntries(VerifiedCorporateEntry.newBuilder()
                                        .setCorporateUuid("01950000-0000-7000-8000-000000000777")
                                        .setCnpj(V2GovernanceTestFixtures.SAMPLE_CORP_CNPJ)
                                        .setDisplayName("Provedor Fibra S.A.")
                                        .build())
                                .build());
                        responseObserver.onCompleted();
                    }
                };

        MtlsFederationInterceptor interceptor = new MtlsFederationInterceptor();
        Server upstreamPrincipal = NettyServerBuilder.forPort(0)
                .sslContext(GrpcServerRunner.buildServerSslContext(caCert, nodeCert, nodeKey))
                .addService(ServerInterceptors.intercept(fakePrincipalDir, interceptor))
                .build()
                .start();

        GrpcChannelPool pool = new GrpcChannelPool(true, caCert.getPath(), nodeCert.getPath(), nodeKey.getPath());
        try {
            String principalAddr = "127.0.0.1:" + upstreamPrincipal.getPort();
            Clock clock = Clock.fixed(Instant.ofEpochMilli(FIXED_NOW_MS), ZoneOffset.UTC);
            EdgeDirectoryProxyGrpcService dirProxy = new EdgeDirectoryProxyGrpcService(pool, null, principalAddr, clock);
            EdgeGovernanceProxyGrpcService govProxy = new EdgeGovernanceProxyGrpcService(pool, null, principalAddr, "127.0.0.1:8443");

            // StreamRevocations via proxy de borda deve ser bloqueado com PERMISSION_DENIED
            AtomicReference<Throwable> streamRevErr = new AtomicReference<>();
            govProxy.streamRevocations(StreamRevocationsRequest.getDefaultInstance(), new StreamObserver<>() {
                @Override public void onNext(RevocationEvent value) {}
                @Override public void onError(Throwable t) { streamRevErr.set(t); }
                @Override public void onCompleted() {}
            });
            assertNotNull(streamRevErr.get());
            assertEquals(Status.Code.PERMISSION_DENIED, ((StatusRuntimeException) streamRevErr.get()).getStatus().getCode());

            // Primeira busca vai ao Principal; segunda busca identica e servida do cache de 5 min
            for (int i = 0; i < 2; i++) {
                AtomicReference<SearchCnpjResponse> respRef = new AtomicReference<>();
                dirProxy.searchVerifiedCnpj(
                        SearchCnpjRequest.newBuilder().setQuery("Fibra").setLimit(10).build(),
                        new StreamObserver<>() {
                            @Override public void onNext(SearchCnpjResponse value) { respRef.set(value); }
                            @Override public void onError(Throwable t) { fail(t); }
                            @Override public void onCompleted() {}
                        });
                assertNotNull(respRef.get());
                assertEquals(1, respRef.get().getEntriesCount());
            }
            assertEquals(1, upstreamCalls.get(), "Segunda consulta dentro de 5 minutos deve vir do cache local");
        } finally {
            pool.shutdownAll();
            upstreamPrincipal.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static final class NoopObserver implements StreamObserver<ServerEnvelope> {
        @Override public void onNext(ServerEnvelope value) {}
        @Override public void onError(Throwable t) {}
        @Override public void onCompleted() {}
    }

    private static File resolveFile(String primary, String fallback) {
        for (String candidate : new String[]{primary, "../" + primary, fallback, "../../" + fallback}) {
            File f = new File(candidate);
            if (f.exists()) {
                return f;
            }
        }
        throw new IllegalStateException("Certificado de teste nao encontrado: " + primary);
    }
}
