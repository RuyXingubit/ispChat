package br.dev.ispchat.node.dht;

import br.dev.ispchat.grpc.DhtClientServiceGrpc;
import br.dev.ispchat.grpc.FindUserSessionsRequest;
import br.dev.ispchat.grpc.FindUserSessionsResponse;
import br.dev.ispchat.grpc.NodeInfo;
import br.dev.ispchat.grpc.RegisterSessionRequest;
import br.dev.ispchat.grpc.RegisterSessionResponse;
import br.dev.ispchat.grpc.UserSessionInfo;
import br.dev.ispchat.node.grpc.GrpcChannelPool;
import br.dev.ispchat.node.grpc.GrpcServerRunner;
import br.dev.ispchat.node.security.CryptoService;
import br.dev.ispchat.node.security.MtlsFederationInterceptor;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
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
    @DisplayName("Deve suportar multi-dispositivo (array de sessoes para o mesmo UUIDv7) e ignorar pacotes antigos")
    void shouldSupportMultiDeviceSessionsAndIgnoreStaleUpdates() throws Exception {
        Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(FIXED_NOW_MILLIS), ZoneOffset.UTC);
        CryptoService cryptoService = new CryptoService(fixedClock);
        DhtStoreService storeService = new DhtStoreService(cryptoService);

        KeyPair userKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] rawPubKey = extractRaw32BytePublicKey(userKeyPair);
        String userUuid = cryptoService.deriveIdentityUuidV7(rawPubKey).toString();

        // Aparelho 1 (Celular conectado ao Provedor A)
        String nodeA = "node-a.isp.com";
        byte[] sigA = sign(userKeyPair, cryptoService.buildCanonicalDhtPayload(userUuid, nodeA, FIXED_NOW_MILLIS));
        assertTrue(storeService.storeVerifiedSession(userUuid, nodeA, rawPubKey, new byte[32], sigA, FIXED_NOW_MILLIS));

        // Aparelho 2 (Desktop conectado ao Provedor B com a mesma frase semente de 12 palavras)
        String nodeB = "node-b.isp.com";
        byte[] sigB = sign(userKeyPair, cryptoService.buildCanonicalDhtPayload(userUuid, nodeB, FIXED_NOW_MILLIS + 1000));
        assertTrue(storeService.storeVerifiedSession(userUuid, nodeB, rawPubKey, new byte[32], sigB, FIXED_NOW_MILLIS + 1000));

        List<UserSessionInfo> activeSessions = storeService.getSessions(userUuid);
        assertEquals(2, activeSessions.size(), "Ambos os dispositivos (nos A e B) devem estar ativos para o mesmo UUIDv7");
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

        // Configura No A (No onde o App Flutter do usuario se conecta e que conhece o No B)
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
            // Usuario cria identidade deterministica e registra sessao no No A
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

            // Outro cliente conectado ao No B busca pelo userUuid e o encontra (replicado via mTLS na DHT!)
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
        File f1 = new File(primary);
        if (f1.exists()) {
            return f1;
        }
        File f2 = new File(fallback);
        if (f2.exists()) {
            return f2;
        }
        throw new IllegalStateException("Certificado de teste nao encontrado: " + primary);
    }
}
