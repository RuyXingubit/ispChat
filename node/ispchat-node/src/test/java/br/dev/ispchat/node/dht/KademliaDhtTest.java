package br.dev.ispchat.node.dht;

import br.dev.ispchat.common.crypto.CanonicalTranscripts;
import br.dev.ispchat.common.crypto.CanonicalTranscripts.RawKeyPair;
import br.dev.ispchat.grpc.DeviceDelegationCert;
import br.dev.ispchat.grpc.DeviceRole;
import br.dev.ispchat.grpc.DhtClientServiceGrpc;
import br.dev.ispchat.grpc.DhtPeerServiceGrpc;
import br.dev.ispchat.grpc.DocumentType;
import br.dev.ispchat.grpc.FindUserSessionsRequest;
import br.dev.ispchat.grpc.FindUserSessionsResponse;
import br.dev.ispchat.grpc.FindValueRequest;
import br.dev.ispchat.grpc.FindValueResponse;
import br.dev.ispchat.grpc.HomeNodeClaim;
import br.dev.ispchat.grpc.NodeInfo;
import br.dev.ispchat.grpc.RegisterSessionRequest;
import br.dev.ispchat.grpc.RegisterSessionResponse;
import br.dev.ispchat.grpc.UserSessionInfo;
import br.dev.ispchat.grpc.VerificationCredential;
import br.dev.ispchat.node.grpc.GrpcChannelPool;
import br.dev.ispchat.node.grpc.GrpcServerRunner;
import br.dev.ispchat.node.security.CryptoService;
import br.dev.ispchat.node.security.MtlsFederationInterceptor;
import br.dev.ispchat.node.security.TierRateLimiterService;
import br.dev.ispchat.node.security.V2GovernanceTestFixtures;
import br.dev.ispchat.node.security.V2GovernanceTestFixtures.GovernancePki;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class KademliaDhtTest {

    private static final long FIXED_NOW_MILLIS = 1762128000000L;

    @Test
    @DisplayName("Deve ordenar nos mais proximos usando a metrica de distancia XOR Kademlia")
    void shouldOrderClosestNodesByXorDistance() {
        String localId = "01950000-0000-7000-8000-000000000000";
        KademliaRoutingTable table = new KademliaRoutingTable(localId);

        String targetUuid = "01950000-0000-7000-8000-000000000010";
        String veryCloseNode = "01950000-0000-7000-8000-000000000011"; // XOR diff = 1
        String mediumNode = "01950000-0000-7000-8000-0000000000ff";    // XOR diff = 0xef
        String farNode = "ffffffff-ffff-7fff-bfff-ffffffffffff";       // XOR diff = alto

        table.registerOrUpdateNode(farNode, "node-far.isp.com");
        table.registerOrUpdateNode(veryCloseNode, "node-close.isp.com");
        table.registerOrUpdateNode(mediumNode, "node-medium.isp.com");

        List<NodeInfo> closest = table.findClosestNodes(targetUuid, 2);
        assertEquals(2, closest.size());
        assertEquals(veryCloseNode, closest.get(0).getNodeId());
        assertEquals(mediumNode, closest.get(1).getNodeId());
    }

    @Test
    @DisplayName("ISPCHAT-DHT-V2: Deve suportar multi-dispositivo verificado (Root + Desktop Delegado) e ignorar pacotes antigos")
    void shouldSupportVerifiedMultiDeviceSessionsAndIgnoreStaleUpdates() {
        Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(FIXED_NOW_MILLIS), ZoneOffset.UTC);
        GovernancePki pki = GovernancePki.create(FIXED_NOW_MILLIS);
        CryptoService cryptoService = new CryptoService(
                fixedClock, pki.rootEd25519().publicKey(), "fallback.ispchat.dev.br:443");
        TierRateLimiterService rateLimiter = new TierRateLimiterService(cryptoService);
        DhtStoreService storeService = new DhtStoreService(cryptoService, null, rateLimiter);

        RawKeyPair rootEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair rootX = CanonicalTranscripts.generateX25519KeyPair();
        VerificationCredential cred = pki.issueCredential(
                rootEd, rootX, DocumentType.CPF, "node-a.isp.com:443", 1L, FIXED_NOW_MILLIS);
        String userUuid = cred.getUserUuid();

        RawKeyPair desktopEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair desktopX = CanonicalTranscripts.generateX25519KeyPair();
        DeviceDelegationCert desktopCert = V2GovernanceTestFixtures.signDeviceDelegation(
                rootEd, desktopEd, desktopX, DeviceRole.DESKTOP, FIXED_NOW_MILLIS);

        // Aparelho 1: Celular Raiz conectado ao Provedor A
        assertTrue(storeService.storeSessionFromClient(V2GovernanceTestFixtures.buildV2RegisterRequest(
                userUuid, userUuid, "node-a.isp.com:443", rootEd.publicKey(),
                rootEd, rootX, null, cred, null, FIXED_NOW_MILLIS)).isSuccess());

        // Aparelho 2: Desktop Delegado conectado ao mesmo ou outro Provedor B
        assertTrue(storeService.storeSessionFromClient(V2GovernanceTestFixtures.buildV2RegisterRequest(
                userUuid, desktopCert.getDeviceId(), "node-b.isp.com:443", rootEd.publicKey(),
                desktopEd, desktopX, desktopCert, cred, null, FIXED_NOW_MILLIS + 1000)).isSuccess());

        // Pacote antigo reordenado para o mesmo Desktop (timestamp menor) deve ser ignorado
        assertTrue(storeService.storeSessionFromClient(V2GovernanceTestFixtures.buildV2RegisterRequest(
                userUuid, desktopCert.getDeviceId(), "stale-node.isp.com:443", rootEd.publicKey(),
                desktopEd, desktopX, desktopCert, cred, null, FIXED_NOW_MILLIS - 2000)).isSuccess());

        List<UserSessionInfo> activeSessions = storeService.getSessions(userUuid);
        assertEquals(2, activeSessions.size(), "Ambos os dispositivos (Root e Desktop delegado) devem estar ativos");
        UserSessionInfo storedDesktop = activeSessions.stream()
                .filter(s -> s.getDeviceId().equals(desktopCert.getDeviceId()))
                .findFirst()
                .orElseThrow();
        assertEquals("node-b.isp.com:443", storedDesktop.getNodeAddress());
    }

    @Test
    @DisplayName("Seguranca HomeNodeClaim: Bloqueia falsificacao de No ISP por chave Sybil e rejeita routing_epoch obsoleto")
    void shouldRejectForgedIspHomeClaimAndStaleRoutingEpoch() {
        String fallbackAddr = "fallback.ispchat.dev.br:443";
        Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(FIXED_NOW_MILLIS), ZoneOffset.UTC);
        GovernancePki pki = GovernancePki.create(FIXED_NOW_MILLIS);
        CryptoService crypto = new CryptoService(fixedClock, pki.rootEd25519().publicKey(), fallbackAddr);
        crypto.verifyPrincipalSigningCert(pki.principalCert());
        DhtStoreService store = new DhtStoreService(crypto);

        RawKeyPair userEd = CanonicalTranscripts.generateEd25519KeyPair();
        String userUuid = CanonicalTranscripts.deriveIdentityUuidV7(userEd.publicKey()).toString();

        // 1. Usuario nao verificado auto-assina claim epoch=0 apontando para o Fallback Node -> ACEITO
        HomeNodeClaim fallbackClaimUnsigned = HomeNodeClaim.newBuilder()
                .setUserUuid(userUuid)
                .setHomeNodeAddress(fallbackAddr)
                .setRoutingEpoch(0L)
                .setIssuedAtMs(FIXED_NOW_MILLIS)
                .setSignedByPrincipal(false)
                .build();
        byte[] userSigFallback = CanonicalTranscripts.signEd25519(
                userEd.privateKey(), CanonicalTranscripts.buildHomeNodeClaimTranscript(fallbackClaimUnsigned));
        HomeNodeClaim validFallbackClaim = fallbackClaimUnsigned.toBuilder()
                .setClaimSignature(ByteString.copyFrom(userSigFallback))
                .build();
        assertTrue(store.storeHomeNodeClaim(validFallbackClaim, null, userEd.publicKey()));

        // 2. Atacante Sybil tenta auto-assinar HomeNodeClaim apontando para um No de Provedor Parceiro -> REJEITADO
        HomeNodeClaim sybilIspClaimUnsigned = HomeNodeClaim.newBuilder()
                .setUserUuid(userUuid)
                .setHomeNodeAddress("node1.provedor-vitima.com.br:443")
                .setRoutingEpoch(0L)
                .setIssuedAtMs(FIXED_NOW_MILLIS)
                .setSignedByPrincipal(false)
                .build();
        byte[] sybilSig = CanonicalTranscripts.signEd25519(
                userEd.privateKey(), CanonicalTranscripts.buildHomeNodeClaimTranscript(sybilIspClaimUnsigned));
        HomeNodeClaim forgedIspClaim = sybilIspClaimUnsigned.toBuilder()
                .setClaimSignature(ByteString.copyFrom(sybilSig))
                .build();
        assertFalse(store.storeHomeNodeClaim(forgedIspClaim, null, userEd.publicKey()),
                "HomeNodeClaim para No de Provedor Parceiro exige obrigatoriamente principal_signature");

        // 3. Principal emite claim oficial epoch=2 -> ACEITO; tentativa de rollback para epoch=1 -> REJEITADO
        HomeNodeClaim epoch2 = pki.signPrincipalHomeClaim(userUuid, "node-isp-b.com.br:443", 2L, FIXED_NOW_MILLIS);
        HomeNodeClaim epoch1Stale = pki.signPrincipalHomeClaim(userUuid, "node-isp-a.com.br:443", 1L, FIXED_NOW_MILLIS);
        assertTrue(store.storeHomeNodeClaim(epoch2, null, userEd.publicKey()));
        assertFalse(store.storeHomeNodeClaim(epoch1Stale, null, userEd.publicKey()),
                "Deve rejeitar HomeNodeClaim com routing_epoch inferior ao armazenado");
        assertEquals("node-isp-b.com.br:443", store.getHomeNodeClaim(userUuid).orElseThrow().getHomeNodeAddress());
    }

    @Test
    @DisplayName("Anti-MITM ISPCHAT-DHT-V2 & DNS SRV Bootstrap: Descarta X25519 adulterada por par malicioso e descobre pares via SRV Ping")
    void shouldRejectTamperedX25519FromMaliciousPeerAndBootstrapViaDnsSrv() throws Exception {
        File caCert = resolveFile("../ca/ca.crt", "ca/ca.crt");
        File nodeCert = resolveFile("../ca/dev-node.crt", "ca/dev-node.crt");
        File nodeKey = resolveFile("../ca/dev-node.key", "ca/dev-node.key");

        Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(FIXED_NOW_MILLIS), ZoneOffset.UTC);
        GovernancePki pki = GovernancePki.create(FIXED_NOW_MILLIS);
        CryptoService crypto = new CryptoService(fixedClock, pki.rootEd25519().publicKey(), "fallback.ispchat.dev.br:443");
        MtlsFederationInterceptor mtlsInterceptor = new MtlsFederationInterceptor();

        RawKeyPair victimEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair victimX = CanonicalTranscripts.generateX25519KeyPair();
        RawKeyPair attackerMitmX = CanonicalTranscripts.generateX25519KeyPair();
        String victimUuid = CanonicalTranscripts.deriveIdentityUuidV7(victimEd.publicKey()).toString();

        // Monta registro V2 legitimo e depois adultera apenas os 32 bytes da chave X25519 (tentativa de MitM E2E)
        RegisterSessionRequest legitReq = V2GovernanceTestFixtures.buildV2RegisterRequest(
                victimUuid, victimUuid, "node-victim.isp.com:443",
                victimEd.publicKey(), victimEd, victimX, null, null, null, FIXED_NOW_MILLIS);
        UserSessionInfo poisonedSession = UserSessionInfo.newBuilder()
                .setNodeAddress(legitReq.getNodeAddress())
                .setDeviceId(victimUuid)
                .setRootEd25519Pub(legitReq.getRootEd25519Pub())
                .setDeviceEd25519Pub(legitReq.getPublicKey())
                .setEd25519PublicKey(legitReq.getPublicKey())
                .setX25519PublicKey(ByteString.copyFrom(attackerMitmX.publicKey())) // Chave X25519 trocada!
                .setTimestamp(FIXED_NOW_MILLIS)
                .setSessionSignature(legitReq.getSignature())
                .build();

        String maliciousPeerId = "01950000-0000-7000-8000-000000000bad";
        DhtPeerServiceGrpc.DhtPeerServiceImplBase maliciousPeerSvc = new DhtPeerServiceGrpc.DhtPeerServiceImplBase() {
            @Override
            public void ping(br.dev.ispchat.grpc.PingRequest request, StreamObserver<br.dev.ispchat.grpc.PingResponse> responseObserver) {
                responseObserver.onNext(br.dev.ispchat.grpc.PingResponse.newBuilder()
                        .setResponderNodeId(maliciousPeerId)
                        .build());
                responseObserver.onCompleted();
            }

            @Override
            public void findValue(FindValueRequest request, StreamObserver<FindValueResponse> responseObserver) {
                responseObserver.onNext(FindValueResponse.newBuilder()
                        .addSessions(poisonedSession)
                        .build());
                responseObserver.onCompleted();
            }
        };

        Server peerServer = NettyServerBuilder.forPort(0)
                .sslContext(GrpcServerRunner.buildServerSslContext(caCert, nodeCert, nodeKey))
                .addService(ServerInterceptors.intercept(maliciousPeerSvc, mtlsInterceptor))
                .build()
                .start();

        KademliaRoutingTable localTable = new KademliaRoutingTable("01950000-0000-7000-8000-000000000001");
        GrpcChannelPool pool = new GrpcChannelPool(true, caCert.getPath(), nodeCert.getPath(), nodeKey.getPath());
        DhtStoreService localStore = new DhtStoreService(crypto);
        DhtClientGrpcService clientSvc = new DhtClientGrpcService(localStore, localTable, pool, crypto);

        try {
            // 1. DnsSrvBootstrapResolver resolve o SRV simulado, faz Ping mTLS e popula KademliaRoutingTable
            DnsSrvBootstrapResolver srvResolver = new DnsSrvBootstrapResolver(
                    localTable,
                    pool,
                    "_ispchat._tcp.ispchat.dev.br",
                    "127.0.0.1:8443",
                    60_000L,
                    domain -> List.of(
                            "10 20 8443 127.0.0.1.", // proprio no -> ignorado
                            "10 50 " + peerServer.getPort() + " 127.0.0.1."));
            assertEquals(1, srvResolver.refreshPeersOnce());
            assertEquals(1, localTable.size());

            // 2. FindUserSessions consulta o par descoberto, detecta adulteracao da chave X25519 e descarta a sessao!
            FindUserSessionsResponse[] holder = new FindUserSessionsResponse[1];
            clientSvc.findUserSessions(
                    FindUserSessionsRequest.newBuilder().setTargetUuid(victimUuid).build(),
                    new StreamObserver<>() {
                        @Override public void onNext(FindUserSessionsResponse value) { holder[0] = value; }
                        @Override public void onError(Throwable t) { fail(t); }
                        @Override public void onCompleted() {}
                    });
            assertNotNull(holder[0]);
            assertEquals(0, holder[0].getSessionsCount(),
                    "Sessao remota com x25519_public_key nao autenticada por ISPCHAT-DHT-V2 deve ser descartada");
        } finally {
            pool.shutdownAll();
            peerServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("Deve replicar registro de sessao do No A para o No B via mTLS e permitir descoberta cruzada na DHT")
    void shouldReplicateSessionAcrossNodesViaMtlsAndResolveRemotely() throws Exception {
        File caCert = resolveFile("../ca/ca.crt", "ca/ca.crt");
        File nodeCert = resolveFile("../ca/dev-node.crt", "ca/dev-node.crt");
        File nodeKey = resolveFile("../ca/dev-node.key", "ca/dev-node.key");

        Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(FIXED_NOW_MILLIS), ZoneOffset.UTC);
        CryptoService cryptoService = new CryptoService(fixedClock);
        MtlsFederationInterceptor mtlsInterceptor = new MtlsFederationInterceptor();

        // Configura No B (Peer Kademlia remoto)
        KademliaRoutingTable tableB = new KademliaRoutingTable("01950000-0000-7000-8000-00000000000b");
        DhtStoreService storeB = new DhtStoreService(cryptoService);
        GrpcChannelPool poolB = new GrpcChannelPool(true, caCert.getPath(), nodeCert.getPath(), nodeKey.getPath());
        DhtPeerGrpcService peerServiceB = new DhtPeerGrpcService(tableB, storeB);
        DhtClientGrpcService clientServiceB = new DhtClientGrpcService(storeB, tableB, poolB, cryptoService);

        SslContext serverSslB = GrpcServerRunner.buildServerSslContext(caCert, nodeCert, nodeKey);
        Server serverB = NettyServerBuilder.forPort(0)
                .sslContext(serverSslB)
                .addService(ServerInterceptors.intercept(peerServiceB, mtlsInterceptor))
                .addService(ServerInterceptors.intercept(clientServiceB, mtlsInterceptor))
                .build()
                .start();

        // Configura No A
        KademliaRoutingTable tableA = new KademliaRoutingTable("01950000-0000-7000-8000-00000000000a");
        tableA.registerOrUpdateNode(tableB.getLocalNodeId(), "127.0.0.1:" + serverB.getPort());
        DhtStoreService storeA = new DhtStoreService(cryptoService);
        GrpcChannelPool poolA = new GrpcChannelPool(true, caCert.getPath(), nodeCert.getPath(), nodeKey.getPath());
        DhtPeerGrpcService peerServiceA = new DhtPeerGrpcService(tableA, storeA);
        DhtClientGrpcService clientServiceA = new DhtClientGrpcService(storeA, tableA, poolA, cryptoService);

        SslContext serverSslA = GrpcServerRunner.buildServerSslContext(caCert, nodeCert, nodeKey);
        Server serverA = NettyServerBuilder.forPort(0)
                .sslContext(serverSslA)
                .addService(ServerInterceptors.intercept(peerServiceA, mtlsInterceptor))
                .addService(ServerInterceptors.intercept(clientServiceA, mtlsInterceptor))
                .build()
                .start();

        SslContext edgeClientSsl = GrpcSslContexts.forClient()
                .trustManager(caCert)
                .protocols("TLSv1.3")
                .build();

        ManagedChannel appChannelToA = NettyChannelBuilder.forAddress("127.0.0.1", serverA.getPort())
                .sslContext(edgeClientSsl)
                .build();
        ManagedChannel appChannelToB = NettyChannelBuilder.forAddress("127.0.0.1", serverB.getPort())
                .sslContext(edgeClientSsl)
                .build();

        try {
            KeyPair userKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            byte[] rawPubKey = extractRaw32BytePublicKey(userKeyPair);
            String userUuid = cryptoService.deriveIdentityUuidV7(rawPubKey).toString();
            String hostedAtAddress = "127.0.0.1:" + serverA.getPort();

            byte[] canonicalBytes = cryptoService.buildCanonicalDhtPayload(userUuid, hostedAtAddress, FIXED_NOW_MILLIS);
            byte[] signature = sign(userKeyPair, canonicalBytes);

            DhtClientServiceGrpc.DhtClientServiceBlockingStub clientStubA = DhtClientServiceGrpc.newBlockingStub(appChannelToA);
            RegisterSessionResponse regResp = clientStubA.registerSession(RegisterSessionRequest.newBuilder()
                    .setUserUuid(userUuid)
                    .setNodeAddress(hostedAtAddress)
                    .setPublicKey(ByteString.copyFrom(rawPubKey))
                    .setSignature(ByteString.copyFrom(signature))
                    .setTimestamp(FIXED_NOW_MILLIS)
                    .build());

            assertTrue(regResp.getSuccess(), "Registro no No A deve ser aceito e replicado para o No B via mTLS");

            DhtClientServiceGrpc.DhtClientServiceBlockingStub clientStubB = DhtClientServiceGrpc.newBlockingStub(appChannelToB);
            FindUserSessionsResponse findResp = clientStubB.findUserSessions(FindUserSessionsRequest.newBuilder()
                    .setTargetUuid(userUuid)
                    .build());

            assertEquals(1, findResp.getNodeAddressesCount(), "No B deve ter recebido a sessao replicada via mTLS");
            assertEquals(hostedAtAddress, findResp.getNodeAddresses(0));
            assertArrayEquals(rawPubKey, findResp.getSessions(0).getEd25519PublicKey().toByteArray());
        } finally {
            appChannelToA.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            appChannelToB.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            poolA.shutdownAll();
            poolB.shutdownAll();
            serverA.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            serverB.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static byte[] extractRaw32BytePublicKey(KeyPair keyPair) {
        byte[] spkiEncoded = keyPair.getPublic().getEncoded();
        return Arrays.copyOfRange(spkiEncoded, 12, 44);
    }

    private static byte[] sign(KeyPair keyPair, byte[] message) throws Exception {
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(keyPair.getPrivate());
        sig.update(message);
        return sig.sign();
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
