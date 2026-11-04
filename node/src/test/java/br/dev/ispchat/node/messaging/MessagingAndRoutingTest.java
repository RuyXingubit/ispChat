package br.dev.ispchat.node.messaging;

import br.dev.ispchat.grpc.ClientEnvelope;
import br.dev.ispchat.grpc.ClientStreamServiceGrpc;
import br.dev.ispchat.grpc.DhtPeerServiceGrpc;
import br.dev.ispchat.grpc.MessageType;
import br.dev.ispchat.grpc.RegisterSessionRequest;
import br.dev.ispchat.grpc.ServerEnvelope;
import br.dev.ispchat.node.dht.DhtClientGrpcService;
import br.dev.ispchat.node.dht.DhtPeerGrpcService;
import br.dev.ispchat.node.dht.DhtStoreService;
import br.dev.ispchat.node.dht.KademliaRoutingTable;
import br.dev.ispchat.node.grpc.GrpcChannelPool;
import br.dev.ispchat.node.grpc.GrpcServerRunner;
import br.dev.ispchat.node.security.CryptoService;
import br.dev.ispchat.node.security.MtlsFederationInterceptor;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MessagingAndRoutingTest {

    private static final long FIXED_NOW_MILLIS = 1762128000000L;

    @Test
    @DisplayName("Store-and-Forward: Deve armazenar mensagem enquanto destinatario esta offline e descarregar imediatamente no AUTH_HANDSHAKE")
    void shouldQueueMessageWhenOfflineAndDrainUponHandshake() throws Exception {
        Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(FIXED_NOW_MILLIS), ZoneOffset.UTC);
        CryptoService cryptoService = new CryptoService(fixedClock);
        LocalSessionRegistry sessionRegistry = new LocalSessionRegistry();
        OfflineMessageQueueService offlineQueue = new OfflineMessageQueueService();
        KademliaRoutingTable routingTable = new KademliaRoutingTable("01950000-0000-7000-8000-000000000001");
        DhtStoreService dhtStore = new DhtStoreService(cryptoService);
        GrpcChannelPool channelPool = new GrpcChannelPool(true, "../ca/ca.crt", "../ca/dev-node.crt", "../ca/dev-node.key");

        MessageRouterService routerService = new MessageRouterService(
                sessionRegistry, offlineQueue, dhtStore, routingTable, channelPool, cryptoService);
        ClientStreamGrpcService streamService = new ClientStreamGrpcService(
                cryptoService, sessionRegistry, offlineQueue, routerService);

        KeyPair aliceKeys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] alicePub = extractRaw32BytePublicKey(aliceKeys);
        String aliceUuid = cryptoService.deriveIdentityUuidV7(alicePub).toString();

        KeyPair bobKeys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] bobPub = extractRaw32BytePublicKey(bobKeys);
        String bobUuid = cryptoService.deriveIdentityUuidV7(bobPub).toString();

        // 1. Alice envia mensagem para Bob enquanto Bob esta OFFLINE
        String msgId = cryptoService.generateTemporalUuidV7().toString();
        byte[] ciphertext = "ola-bob-offline-e2e".getBytes(StandardCharsets.UTF_8);
        ClientEnvelope aliceMsg = buildSignedEnvelope(
                cryptoService, aliceKeys, alicePub, aliceUuid, bobUuid, msgId, MessageType.TEXT, ciphertext);

        boolean queued = routerService.routeClientEnvelope(aliceMsg);
        assertTrue(queued, "Mensagem para destinatario offline deve ser enfileirada com sucesso");

        // 2. Bob abre o aplicativo e envia o pacote AUTH_HANDSHAKE assinado
        BlockingQueue<ServerEnvelope> bobInbox = new LinkedBlockingQueue<>();
        StreamObserver<ClientEnvelope> bobOutStream = streamService.connect(new StreamObserver<>() {
            @Override
            public void onNext(ServerEnvelope value) {
                bobInbox.add(value);
            }

            @Override
            public void onError(Throwable t) {}

            @Override
            public void onCompleted() {}
        });

        ClientEnvelope bobHandshake = buildSignedEnvelope(
                cryptoService, bobKeys, bobPub, bobUuid, bobUuid,
                cryptoService.generateTemporalUuidV7().toString(),
                MessageType.AUTH_HANDSHAKE, new byte[0]);
        bobOutStream.onNext(bobHandshake);

        // 3. Verifica que a mensagem guardada na fila offline foi entregue instantaneamente a Bob
        ServerEnvelope deliveredMsg = bobInbox.poll(2, TimeUnit.SECONDS);
        assertNotNull(deliveredMsg, "Bob deve receber a mensagem pendente assim que autenticar a stream");
        assertEquals(msgId, deliveredMsg.getMessageId());
        assertEquals(aliceUuid, deliveredMsg.getSenderUuid());
        assertArrayEquals(ciphertext, deliveredMsg.getEncryptedPayload().toByteArray());

        // 4. Verifica que a fila offline foi limpa apos a entrega
        List<ServerEnvelope> remaining = offlineQueue.drainQueue(bobUuid);
        assertTrue(remaining.isEmpty(), "A fila offline deve ser esvaziada apos a entrega");

        bobOutStream.onCompleted();
        channelPool.shutdownAll();
    }

    @Test
    @DisplayName("Broadcast Multi-Dispositivo: Deve disparar chamada RING simultaneamente no Celular e no Desktop do mesmo UUIDv7")
    void shouldBroadcastRingToMultipleConnectedDevicesSimultaneously() throws Exception {
        Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(FIXED_NOW_MILLIS), ZoneOffset.UTC);
        CryptoService cryptoService = new CryptoService(fixedClock);
        LocalSessionRegistry sessionRegistry = new LocalSessionRegistry();
        OfflineMessageQueueService offlineQueue = new OfflineMessageQueueService();
        KademliaRoutingTable routingTable = new KademliaRoutingTable("01950000-0000-7000-8000-000000000001");
        DhtStoreService dhtStore = new DhtStoreService(cryptoService);
        GrpcChannelPool channelPool = new GrpcChannelPool(true, "../ca/ca.crt", "../ca/dev-node.crt", "../ca/dev-node.key");

        MessageRouterService routerService = new MessageRouterService(
                sessionRegistry, offlineQueue, dhtStore, routingTable, channelPool, cryptoService);
        ClientStreamGrpcService streamService = new ClientStreamGrpcService(
                cryptoService, sessionRegistry, offlineQueue, routerService);

        KeyPair bobKeys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] bobPub = extractRaw32BytePublicKey(bobKeys);
        String bobUuid = cryptoService.deriveIdentityUuidV7(bobPub).toString();

        BlockingQueue<ServerEnvelope> mobileInbox = new LinkedBlockingQueue<>();
        BlockingQueue<ServerEnvelope> desktopInbox = new LinkedBlockingQueue<>();

        // Conecta Celular de Bob
        StreamObserver<ClientEnvelope> mobileStream = streamService.connect(new StreamObserver<>() {
            @Override
            public void onNext(ServerEnvelope value) { mobileInbox.add(value); }
            @Override
            public void onError(Throwable t) {}
            @Override
            public void onCompleted() {}
        });
        mobileStream.onNext(buildSignedEnvelope(cryptoService, bobKeys, bobPub, bobUuid, bobUuid,
                cryptoService.generateTemporalUuidV7().toString(), MessageType.AUTH_HANDSHAKE, new byte[0]));

        // Conecta Desktop de Bob (mesmas 12 palavras -> mesmo UUIDv7)
        StreamObserver<ClientEnvelope> desktopStream = streamService.connect(new StreamObserver<>() {
            @Override
            public void onNext(ServerEnvelope value) { desktopInbox.add(value); }
            @Override
            public void onError(Throwable t) {}
            @Override
            public void onCompleted() {}
        });
        desktopStream.onNext(buildSignedEnvelope(cryptoService, bobKeys, bobPub, bobUuid, bobUuid,
                cryptoService.generateTemporalUuidV7().toString(), MessageType.AUTH_HANDSHAKE, new byte[0]));

        assertEquals(2, sessionRegistry.getActiveDeviceCount(bobUuid));

        // Alice liga para Bob (MessageType.RING)
        KeyPair aliceKeys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] alicePub = extractRaw32BytePublicKey(aliceKeys);
        String aliceUuid = cryptoService.deriveIdentityUuidV7(alicePub).toString();
        String ringId = cryptoService.generateTemporalUuidV7().toString();

        ClientEnvelope ringEnvelope = buildSignedEnvelope(
                cryptoService, aliceKeys, alicePub, aliceUuid, bobUuid, ringId,
                MessageType.RING, "webrtc-ring-metadata".getBytes(StandardCharsets.UTF_8));

        assertTrue(routerService.routeClientEnvelope(ringEnvelope));

        ServerEnvelope mobileRing = mobileInbox.poll(2, TimeUnit.SECONDS);
        ServerEnvelope desktopRing = desktopInbox.poll(2, TimeUnit.SECONDS);

        assertNotNull(mobileRing, "Celular de Bob deve tocar");
        assertNotNull(desktopRing, "Desktop de Bob deve tocar simultaneamente");
        assertEquals(MessageType.RING, mobileRing.getType());
        assertEquals(MessageType.RING, desktopRing.getType());
        assertEquals(ringId, mobileRing.getMessageId());
        assertEquals(ringId, desktopRing.getMessageId());

        mobileStream.onCompleted();
        desktopStream.onCompleted();
        channelPool.shutdownAll();
    }

    @Test
    @DisplayName("Roteamento E2E Inter-Provedores via mTLS: Alice no No A envia mensagem para Bob no No B")
    void shouldRouteEncryptedMessageAcrossTwoIspNodesViaMtls() throws Exception {
        File caCert = resolveFile("../ca/ca.crt", "ca/ca.crt");
        File nodeCert = resolveFile("../ca/dev-node.crt", "ca/dev-node.crt");
        File nodeKey = resolveFile("../ca/dev-node.key", "ca/dev-node.key");

        Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(FIXED_NOW_MILLIS), ZoneOffset.UTC);
        CryptoService cryptoService = new CryptoService(fixedClock);
        MtlsFederationInterceptor mtlsInterceptor = new MtlsFederationInterceptor();

        // 1. Levanta No B (Provedor onde Bob esta conectado)
        KademliaRoutingTable tableB = new KademliaRoutingTable("01950000-0000-7000-8000-00000000000b");
        DhtStoreService dhtStoreB = new DhtStoreService(cryptoService);
        GrpcChannelPool poolB = new GrpcChannelPool(true, caCert.getPath(), nodeCert.getPath(), nodeKey.getPath());
        LocalSessionRegistry registryB = new LocalSessionRegistry();
        OfflineMessageQueueService queueB = new OfflineMessageQueueService();
        MessageRouterService routerB = new MessageRouterService(registryB, queueB, dhtStoreB, tableB, poolB, cryptoService);

        Server serverB = NettyServerBuilder.forPort(0)
                .sslContext(GrpcServerRunner.buildServerSslContext(caCert, nodeCert, nodeKey))
                .addService(ServerInterceptors.intercept(new DhtPeerGrpcService(tableB, dhtStoreB), mtlsInterceptor))
                .addService(ServerInterceptors.intercept(new DhtClientGrpcService(dhtStoreB, tableB, poolB, cryptoService), mtlsInterceptor))
                .addService(ServerInterceptors.intercept(new NodeRoutingGrpcService(cryptoService, registryB, queueB), mtlsInterceptor))
                .addService(ServerInterceptors.intercept(new ClientStreamGrpcService(cryptoService, registryB, queueB, routerB), mtlsInterceptor))
                .build()
                .start();

        // 2. Levanta No A (Provedor onde Alice esta conectada)
        KademliaRoutingTable tableA = new KademliaRoutingTable("01950000-0000-7000-8000-00000000000a");
        String addressB = "127.0.0.1:" + serverB.getPort();
        tableA.registerOrUpdateNode(tableB.getLocalNodeId(), addressB);

        DhtStoreService dhtStoreA = new DhtStoreService(cryptoService);
        GrpcChannelPool poolA = new GrpcChannelPool(true, caCert.getPath(), nodeCert.getPath(), nodeKey.getPath());
        LocalSessionRegistry registryA = new LocalSessionRegistry();
        OfflineMessageQueueService queueA = new OfflineMessageQueueService();
        MessageRouterService routerA = new MessageRouterService(registryA, queueA, dhtStoreA, tableA, poolA, cryptoService);

        Server serverA = NettyServerBuilder.forPort(0)
                .sslContext(GrpcServerRunner.buildServerSslContext(caCert, nodeCert, nodeKey))
                .addService(ServerInterceptors.intercept(new DhtPeerGrpcService(tableA, dhtStoreA), mtlsInterceptor))
                .addService(ServerInterceptors.intercept(new DhtClientGrpcService(dhtStoreA, tableA, poolA, cryptoService), mtlsInterceptor))
                .addService(ServerInterceptors.intercept(new NodeRoutingGrpcService(cryptoService, registryA, queueA), mtlsInterceptor))
                .addService(ServerInterceptors.intercept(new ClientStreamGrpcService(cryptoService, registryA, queueA, routerA), mtlsInterceptor))
                .build()
                .start();

        SslContext appTlsContext = GrpcSslContexts.forClient()
                .trustManager(caCert)
                .protocols("TLSv1.3")
                .build();

        ManagedChannel aliceChannelToA = NettyChannelBuilder.forAddress("127.0.0.1", serverA.getPort())
                .sslContext(appTlsContext)
                .build();
        ManagedChannel bobChannelToB = NettyChannelBuilder.forAddress("127.0.0.1", serverB.getPort())
                .sslContext(appTlsContext)
                .build();

        try {
            KeyPair aliceKeys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            byte[] alicePub = extractRaw32BytePublicKey(aliceKeys);
            String aliceUuid = cryptoService.deriveIdentityUuidV7(alicePub).toString();

            KeyPair bobKeys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            byte[] bobPub = extractRaw32BytePublicKey(bobKeys);
            String bobUuid = cryptoService.deriveIdentityUuidV7(bobPub).toString();

            // Bob registra presenca na DHT do No B
            byte[] bobDhtCanonical = cryptoService.buildCanonicalDhtPayload(bobUuid, addressB, FIXED_NOW_MILLIS);
            byte[] bobDhtSig = sign(bobKeys, bobDhtCanonical);
            assertTrue(dhtStoreB.storeVerifiedSession(bobUuid, addressB, bobPub, new byte[32], bobDhtSig, FIXED_NOW_MILLIS));

            // Bob abre a stream gRPC no No B
            BlockingQueue<ServerEnvelope> bobReceivedQueue = new LinkedBlockingQueue<>();
            ClientStreamServiceGrpc.ClientStreamServiceStub bobAsyncStub = ClientStreamServiceGrpc.newStub(bobChannelToB);
            StreamObserver<ClientEnvelope> bobStream = bobAsyncStub.connect(new StreamObserver<>() {
                @Override
                public void onNext(ServerEnvelope value) { bobReceivedQueue.add(value); }
                @Override
                public void onError(Throwable t) {}
                @Override
                public void onCompleted() {}
            });

            bobStream.onNext(buildSignedEnvelope(cryptoService, bobKeys, bobPub, bobUuid, bobUuid,
                    cryptoService.generateTemporalUuidV7().toString(), MessageType.AUTH_HANDSHAKE, new byte[0]));

            // Aguarda registro da stream de Bob no No B
            long deadline = System.currentTimeMillis() + 2000;
            while (registryB.getActiveDeviceCount(bobUuid) == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertEquals(1, registryB.getActiveDeviceCount(bobUuid));

            // Alice abre stream no No A, autentica com AUTH_HANDSHAKE e envia mensagem E2E para Bob
            ClientStreamServiceGrpc.ClientStreamServiceStub aliceAsyncStub = ClientStreamServiceGrpc.newStub(aliceChannelToA);
            StreamObserver<ClientEnvelope> aliceStream = aliceAsyncStub.connect(new StreamObserver<>() {
                @Override
                public void onNext(ServerEnvelope value) {}
                @Override
                public void onError(Throwable t) {}
                @Override
                public void onCompleted() {}
            });

            aliceStream.onNext(buildSignedEnvelope(cryptoService, aliceKeys, alicePub, aliceUuid, aliceUuid,
                    cryptoService.generateTemporalUuidV7().toString(), MessageType.AUTH_HANDSHAKE, new byte[0]));

            String msgId = cryptoService.generateTemporalUuidV7().toString();
            byte[] encryptedPayload = "mensagem-secreta-inter-provedores".getBytes(StandardCharsets.UTF_8);
            ClientEnvelope aliceToBob = buildSignedEnvelope(
                    cryptoService, aliceKeys, alicePub, aliceUuid, bobUuid, msgId, MessageType.TEXT, encryptedPayload);

            aliceStream.onNext(aliceToBob);

            // Verifica que o pacote viajou do App Alice -> No A -> (mTLS DHT + NodeRouting) -> No B -> App Bob
            ServerEnvelope receivedByBob = bobReceivedQueue.poll(5, TimeUnit.SECONDS);
            assertNotNull(receivedByBob, "Bob no No B deve receber a mensagem enviada por Alice no No A via mTLS");
            assertEquals(msgId, receivedByBob.getMessageId());
            assertEquals(aliceUuid, receivedByBob.getSenderUuid());
            assertArrayEquals(encryptedPayload, receivedByBob.getEncryptedPayload().toByteArray());
            assertArrayEquals(alicePub, receivedByBob.getSenderPublicKey().toByteArray());

            aliceStream.onCompleted();
            bobStream.onCompleted();
        } finally {
            aliceChannelToA.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            bobChannelToB.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            poolA.shutdownAll();
            poolB.shutdownAll();
            serverA.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            serverB.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("Seguranca da Stream: Deve derrubar stream com UNAUTHENTICATED se assinatura Ed25519 for invalida")
    void shouldCloseStreamWithUnauthenticatedOnInvalidSignature() throws Exception {
        Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(FIXED_NOW_MILLIS), ZoneOffset.UTC);
        CryptoService cryptoService = new CryptoService(fixedClock);
        LocalSessionRegistry sessionRegistry = new LocalSessionRegistry();
        OfflineMessageQueueService offlineQueue = new OfflineMessageQueueService();
        KademliaRoutingTable routingTable = new KademliaRoutingTable("01950000-0000-7000-8000-000000000001");
        DhtStoreService dhtStore = new DhtStoreService(cryptoService);
        GrpcChannelPool channelPool = new GrpcChannelPool(true, "../ca/ca.crt", "../ca/dev-node.crt", "../ca/dev-node.key");

        MessageRouterService routerService = new MessageRouterService(
                sessionRegistry, offlineQueue, dhtStore, routingTable, channelPool, cryptoService);
        ClientStreamGrpcService streamService = new ClientStreamGrpcService(
                cryptoService, sessionRegistry, offlineQueue, routerService);

        AtomicReference<Throwable> capturedError = new AtomicReference<>();
        CountDownLatch errorLatch = new CountDownLatch(1);

        StreamObserver<ClientEnvelope> clientStream = streamService.connect(new StreamObserver<>() {
            @Override
            public void onNext(ServerEnvelope value) {}
            @Override
            public void onError(Throwable t) {
                capturedError.set(t);
                errorLatch.countDown();
            }
            @Override
            public void onCompleted() {}
        });

        KeyPair attackerKeys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] attackerPub = extractRaw32BytePublicKey(attackerKeys);
        String fakeVictimUuid = "01950000-0000-7000-8000-999999999999";

        // Atacante tenta autenticar a stream reivindicando um UUIDv7 que nao pertence a sua chave publica
        ClientEnvelope forgedHandshake = buildSignedEnvelope(
                cryptoService, attackerKeys, attackerPub, fakeVictimUuid, fakeVictimUuid,
                cryptoService.generateTemporalUuidV7().toString(), MessageType.AUTH_HANDSHAKE, new byte[0]);

        clientStream.onNext(forgedHandshake);

        assertTrue(errorLatch.await(2, TimeUnit.SECONDS));
        assertInstanceOf(StatusRuntimeException.class, capturedError.get());
        assertEquals(Status.Code.UNAUTHENTICATED, ((StatusRuntimeException) capturedError.get()).getStatus().getCode());
        assertEquals(0, sessionRegistry.getActiveDeviceCount(fakeVictimUuid));

        channelPool.shutdownAll();
    }

    @Test
    @DisplayName("Seguranca da Stream: Deve rejeitar com UNAUTHENTICATED se o primeiro pacote nao for AUTH_HANDSHAKE")
    void shouldRejectInitialStreamPacketWhenNotAuthHandshake() throws Exception {
        Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(FIXED_NOW_MILLIS), ZoneOffset.UTC);
        CryptoService cryptoService = new CryptoService(fixedClock);
        LocalSessionRegistry sessionRegistry = new LocalSessionRegistry();
        OfflineMessageQueueService offlineQueue = new OfflineMessageQueueService();
        KademliaRoutingTable routingTable = new KademliaRoutingTable("01950000-0000-7000-8000-000000000001");
        DhtStoreService dhtStore = new DhtStoreService(cryptoService);
        GrpcChannelPool channelPool = new GrpcChannelPool(true, "../ca/ca.crt", "../ca/dev-node.crt", "../ca/dev-node.key");

        MessageRouterService routerService = new MessageRouterService(
                sessionRegistry, offlineQueue, dhtStore, routingTable, channelPool, cryptoService);
        ClientStreamGrpcService streamService = new ClientStreamGrpcService(
                cryptoService, sessionRegistry, offlineQueue, routerService);

        AtomicReference<Throwable> capturedError = new AtomicReference<>();
        CountDownLatch errorLatch = new CountDownLatch(1);

        StreamObserver<ClientEnvelope> clientStream = streamService.connect(new StreamObserver<>() {
            @Override
            public void onNext(ServerEnvelope value) {}
            @Override
            public void onError(Throwable t) {
                capturedError.set(t);
                errorLatch.countDown();
            }
            @Override
            public void onCompleted() {}
        });

        KeyPair aliceKeys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] alicePub = extractRaw32BytePublicKey(aliceKeys);
        String aliceUuid = cryptoService.deriveIdentityUuidV7(alicePub).toString();
        String bobUuid = "01950000-0000-7000-8000-000000000099";

        ClientEnvelope prematureText = buildSignedEnvelope(
                cryptoService, aliceKeys, alicePub, aliceUuid, bobUuid,
                cryptoService.generateTemporalUuidV7().toString(),
                MessageType.TEXT, "sem-handshake".getBytes(StandardCharsets.UTF_8));

        clientStream.onNext(prematureText);

        assertTrue(errorLatch.await(2, TimeUnit.SECONDS));
        assertInstanceOf(StatusRuntimeException.class, capturedError.get());
        assertEquals(Status.Code.UNAUTHENTICATED, ((StatusRuntimeException) capturedError.get()).getStatus().getCode());
        assertEquals(0, sessionRegistry.getActiveDeviceCount(aliceUuid));

        channelPool.shutdownAll();
    }

    private static ClientEnvelope buildSignedEnvelope(
            CryptoService cryptoService,
            KeyPair senderKeys,
            byte[] senderRawPub,
            String senderUuid,
            String receiverUuid,
            String messageId,
            MessageType type,
            byte[] encryptedPayload) throws Exception {
        byte[] canonical = cryptoService.buildCanonicalMessagePayload(
                messageId, senderUuid, receiverUuid, type.getNumber(), FIXED_NOW_MILLIS, encryptedPayload);
        byte[] signature = sign(senderKeys, canonical);

        return ClientEnvelope.newBuilder()
                .setMessageId(messageId)
                .setSenderUuid(senderUuid)
                .setReceiverUuid(receiverUuid)
                .setType(type)
                .setEncryptedPayload(ByteString.copyFrom(encryptedPayload))
                .setSenderPublicKey(ByteString.copyFrom(senderRawPub))
                .setSignature(ByteString.copyFrom(signature))
                .setTimestamp(FIXED_NOW_MILLIS)
                .build();
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
