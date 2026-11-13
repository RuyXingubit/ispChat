package br.dev.ispchat.node.messaging;

import br.dev.ispchat.common.crypto.CanonicalTranscripts;
import br.dev.ispchat.common.crypto.CanonicalTranscripts.RawKeyPair;
import br.dev.ispchat.grpc.ClientEnvelope;
import br.dev.ispchat.grpc.ClientStreamServiceGrpc;
import br.dev.ispchat.grpc.DeviceDelegationCert;
import br.dev.ispchat.grpc.DeviceRole;
import br.dev.ispchat.grpc.DocumentType;
import br.dev.ispchat.grpc.HomeNodeClaim;
import br.dev.ispchat.grpc.MessageType;
import br.dev.ispchat.grpc.RoutePayloadRequest;
import br.dev.ispchat.grpc.RoutePayloadResponse;
import br.dev.ispchat.grpc.ServerEnvelope;
import br.dev.ispchat.grpc.VerificationCredential;
import br.dev.ispchat.node.dht.DhtClientGrpcService;
import br.dev.ispchat.node.dht.DhtPeerGrpcService;
import br.dev.ispchat.node.dht.DhtStoreService;
import br.dev.ispchat.node.dht.KademliaRoutingTable;
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
    @DisplayName("Store-and-Forward + DELIVERY_ACK: Deve manter mensagem na fila ate o cliente confirmar com DELIVERY_ACK")
    void shouldQueueMessageWhenOfflineAndGarbageCollectOnlyUponDeliveryAck() throws Exception {
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

        RawKeyPair aliceKeys = CanonicalTranscripts.generateEd25519KeyPair();
        String aliceUuid = cryptoService.deriveIdentityUuidV7(aliceKeys.publicKey()).toString();

        RawKeyPair bobKeys = CanonicalTranscripts.generateEd25519KeyPair();
        String bobUuid = cryptoService.deriveIdentityUuidV7(bobKeys.publicKey()).toString();

        // 1. Alice envia mensagem para Bob enquanto Bob esta OFFLINE
        String msgId = cryptoService.generateTemporalUuidV7().toString();
        byte[] ciphertext = "ola-bob-offline-e2e".getBytes(StandardCharsets.UTF_8);
        ClientEnvelope aliceMsg = V2GovernanceTestFixtures.buildSignedV2Envelope(
                msgId, aliceUuid, aliceUuid, bobUuid, MessageType.TEXT, "",
                ciphertext, aliceKeys, null, null, FIXED_NOW_MILLIS, true);

        assertTrue(routerService.routeClientEnvelope(aliceMsg));

        // 2. Bob conecta e recebe a mensagem pendente no AUTH_HANDSHAKE (sem LPOP destrutivo)
        BlockingQueue<ServerEnvelope> bobInbox = new LinkedBlockingQueue<>();
        StreamObserver<ClientEnvelope> bobOutStream = streamService.connect(new StreamObserver<>() {
            @Override public void onNext(ServerEnvelope value) { bobInbox.add(value); }
            @Override public void onError(Throwable t) {}
            @Override public void onCompleted() {}
        });

        bobOutStream.onNext(V2GovernanceTestFixtures.buildSignedV2Envelope(
                cryptoService.generateTemporalUuidV7().toString(),
                bobUuid, bobUuid, bobUuid, MessageType.AUTH_HANDSHAKE, "",
                new byte[0], bobKeys, null, null, FIXED_NOW_MILLIS, true));

        ServerEnvelope deliveredMsg = bobInbox.poll(2, TimeUnit.SECONDS);
        assertNotNull(deliveredMsg);
        assertEquals(msgId, deliveredMsg.getMessageId());
        assertEquals(bobUuid, deliveredMsg.getReceiverUuid());
        assertArrayEquals(ciphertext, deliveredMsg.getEncryptedPayload().toByteArray());

        // Antes do DELIVERY_ACK, a mensagem permanece protegida na fila caso o app feche abruptamente
        assertEquals(1, offlineQueue.peekPendingMessages(bobUuid, bobUuid).size());

        // 3. Bob grava no cofre local e envia DELIVERY_ACK enxuto (sem certificados redundantes na wire)
        ClientEnvelope ackFrame = V2GovernanceTestFixtures.buildSignedV2Envelope(
                cryptoService.generateTemporalUuidV7().toString(),
                bobUuid, bobUuid, bobUuid, MessageType.DELIVERY_ACK, msgId,
                new byte[0], bobKeys, null, null, FIXED_NOW_MILLIS, false);
        bobOutStream.onNext(ackFrame);

        assertTrue(offlineQueue.peekPendingMessages(bobUuid, bobUuid).isEmpty(),
                "Fila pendente deve ser esvaziada somente apos DELIVERY_ACK");
        assertEquals(0, offlineQueue.getStoredMessageCount(bobUuid));

        bobOutStream.onCompleted();
        channelPool.shutdownAll();
    }

    @Test
    @DisplayName("Broadcast Multi-Dispositivo Verificado: Deve disparar RING simultaneamente no Celular Raiz e no Desktop Delegado")
    void shouldBroadcastRingToMultipleVerifiedDevicesSimultaneously() throws Exception {
        Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(FIXED_NOW_MILLIS), ZoneOffset.UTC);
        GovernancePki pki = GovernancePki.create(FIXED_NOW_MILLIS);
        CryptoService cryptoService = new CryptoService(
                fixedClock, pki.rootEd25519().publicKey(), "fallback.ispchat.dev.br:443");
        TierRateLimiterService rateLimiter = new TierRateLimiterService(cryptoService);
        LocalSessionRegistry sessionRegistry = new LocalSessionRegistry();
        OfflineMessageQueueService offlineQueue = new OfflineMessageQueueService();
        KademliaRoutingTable routingTable = new KademliaRoutingTable("01950000-0000-7000-8000-000000000001");
        DhtStoreService dhtStore = new DhtStoreService(cryptoService, null, rateLimiter);
        GrpcChannelPool channelPool = new GrpcChannelPool(true, "../ca/ca.crt", "../ca/dev-node.crt", "../ca/dev-node.key");

        MessageRouterService routerService = new MessageRouterService(
                sessionRegistry, offlineQueue, dhtStore, routingTable, channelPool, cryptoService);
        ClientStreamGrpcService streamService = new ClientStreamGrpcService(
                cryptoService, sessionRegistry, offlineQueue, routerService, rateLimiter);

        RawKeyPair bobRootEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair bobRootX = CanonicalTranscripts.generateX25519KeyPair();
        VerificationCredential bobCred = pki.issueCredential(
                bobRootEd, bobRootX, DocumentType.CPF, "127.0.0.1:8443", 1L, FIXED_NOW_MILLIS);
        String bobUuid = bobCred.getUserUuid();

        RawKeyPair bobDesktopEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair bobDesktopX = CanonicalTranscripts.generateX25519KeyPair();
        DeviceDelegationCert bobDesktopCert = V2GovernanceTestFixtures.signDeviceDelegation(
                bobRootEd, bobDesktopEd, bobDesktopX, DeviceRole.DESKTOP, FIXED_NOW_MILLIS);

        BlockingQueue<ServerEnvelope> mobileInbox = new LinkedBlockingQueue<>();
        BlockingQueue<ServerEnvelope> desktopInbox = new LinkedBlockingQueue<>();

        // Conecta Celular Raiz de Bob (MOBILE_ROOT)
        StreamObserver<ClientEnvelope> mobileStream = streamService.connect(new StreamObserver<>() {
            @Override public void onNext(ServerEnvelope value) { mobileInbox.add(value); }
            @Override public void onError(Throwable t) { fail(t); }
            @Override public void onCompleted() {}
        });
        mobileStream.onNext(V2GovernanceTestFixtures.buildSignedV2Envelope(
                cryptoService.generateTemporalUuidV7().toString(),
                bobUuid, bobUuid, bobUuid, MessageType.AUTH_HANDSHAKE, "",
                new byte[0], bobRootEd, null, bobCred, FIXED_NOW_MILLIS, true));

        // Conecta Desktop pareado via QR Code (DeviceDelegationCert)
        StreamObserver<ClientEnvelope> desktopStream = streamService.connect(new StreamObserver<>() {
            @Override public void onNext(ServerEnvelope value) { desktopInbox.add(value); }
            @Override public void onError(Throwable t) { fail(t); }
            @Override public void onCompleted() {}
        });
        desktopStream.onNext(V2GovernanceTestFixtures.buildSignedV2Envelope(
                cryptoService.generateTemporalUuidV7().toString(),
                bobUuid, bobDesktopCert.getDeviceId(), bobUuid, MessageType.AUTH_HANDSHAKE, "",
                new byte[0], bobDesktopEd, bobDesktopCert, bobCred, FIXED_NOW_MILLIS, true));

        assertEquals(2, sessionRegistry.getActiveDeviceCount(bobUuid));

        // Alice liga para Bob (MessageType.RING)
        KeyPair aliceKeys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] alicePub = extractRaw32BytePublicKey(aliceKeys);
        String aliceUuid = cryptoService.deriveIdentityUuidV7(alicePub).toString();
        String ringId = cryptoService.generateTemporalUuidV7().toString();

        ClientEnvelope ringEnvelope = buildSignedV1Envelope(
                cryptoService, aliceKeys, alicePub, aliceUuid, bobUuid, ringId,
                MessageType.RING, "webrtc-ring-metadata".getBytes(StandardCharsets.UTF_8));

        assertTrue(routerService.routeClientEnvelope(ringEnvelope));

        ServerEnvelope mobileRing = mobileInbox.poll(2, TimeUnit.SECONDS);
        ServerEnvelope desktopRing = desktopInbox.poll(2, TimeUnit.SECONDS);

        assertNotNull(mobileRing, "Celular de Bob deve tocar");
        assertNotNull(desktopRing, "Desktop delegado de Bob deve tocar simultaneamente");
        assertEquals(MessageType.RING, mobileRing.getType());
        assertEquals(MessageType.RING, desktopRing.getType());
        assertEquals(ringId, mobileRing.getMessageId());
        assertEquals(ringId, desktopRing.getMessageId());

        mobileStream.onCompleted();
        desktopStream.onCompleted();
        channelPool.shutdownAll();
    }

    @Test
    @DisplayName("Fila Ring-Buffer Multi-Dispositivo: Evita bloqueio por Desktop desligado (cap 1.000) e coleta apos todos os ACKs")
    void shouldEvictOldestBeyond1000AndGarbageCollectAcrossSiblingDevices() {
        OfflineMessageQueueService queue = new OfflineMessageQueueService();
        String userUuid = CanonicalTranscripts.generateTemporalUuidV7().toString();
        String mobileDev = userUuid;
        String desktopDev = CanonicalTranscripts.generateTemporalUuidV7().toString();

        // 1. Enfileira 1.005 mensagens para Mobile + Desktop -> as 5 mais antigas devem sofrer ring-buffer eviction
        String sixthMessageId = null;
        String lastMessageId = null;
        for (int i = 1; i <= 1005; i++) {
            String msgId = String.format("01950000-0000-7000-8000-%012d", i);
            if (i == 6) {
                sixthMessageId = msgId;
            }
            lastMessageId = msgId;
            ServerEnvelope env = ServerEnvelope.newBuilder()
                    .setMessageId(msgId)
                    .setSenderUuid(userUuid)
                    .setReceiverUuid(userUuid)
                    .setType(MessageType.TEXT)
                    .setTimestamp(FIXED_NOW_MILLIS + i)
                    .build();
            assertTrue(queue.enqueueForDevices(userUuid, env, List.of(mobileDev, desktopDev)));
        }

        assertEquals(1000, queue.getStoredMessageCount(userUuid));
        List<ServerEnvelope> mobilePending = queue.peekPendingMessages(userUuid, mobileDev);
        assertEquals(1000, mobilePending.size());
        assertEquals(sixthMessageId, mobilePending.getFirst().getMessageId(),
                "Mensagens 1..5 devem ter sido despejadas pelo ring-buffer em ordem FIFO");
        assertEquals(lastMessageId, mobilePending.getLast().getMessageId());

        // 2. Mobile confirma a ultima mensagem -> continua armazenada ate o Desktop tambem confirmar
        assertTrue(queue.acknowledgeDelivery(userUuid, mobileDev, lastMessageId));
        assertEquals(999, queue.peekPendingMessages(userUuid, mobileDev).size());
        assertEquals(1000, queue.peekPendingMessages(userUuid, desktopDev).size());
        assertEquals(1000, queue.getStoredMessageCount(userUuid));

        // 3. Desktop confirma a mesma mensagem -> coletada definitivamente do Hash
        assertTrue(queue.acknowledgeDelivery(userUuid, desktopDev, lastMessageId));
        assertEquals(999, queue.getStoredMessageCount(userUuid));
    }

    @Test
    @DisplayName("CUJ 5 & CUJ 4: Roteamento Cross-ISP para HomeNodeClaim remoto (SERVER_QUEUED_ACK), ERP_API sem fila irma e aborto em hop_count > 2")
    void shouldRouteOfflineTextToRemoteHomeNodeSkipErpSiblingQueueAndAbortHopLoops() throws Exception {
        File caCert = resolveFile("../ca/ca.crt", "ca/ca.crt");
        File nodeCert = resolveFile("../ca/dev-node.crt", "ca/dev-node.crt");
        File nodeKey = resolveFile("../ca/dev-node.key", "ca/dev-node.key");

        Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(FIXED_NOW_MILLIS), ZoneOffset.UTC);
        GovernancePki pki = GovernancePki.create(FIXED_NOW_MILLIS);
        CryptoService crypto = new CryptoService(fixedClock, pki.rootEd25519().publicKey(), "fallback.ispchat.dev.br:443");
        crypto.verifyPrincipalSigningCert(pki.principalCert());
        MtlsFederationInterceptor mtlsInterceptor = new MtlsFederationInterceptor();

        // Levanta No A (Home Node oficial do Assinante Carlos)
        TierRateLimiterService limiterA = new TierRateLimiterService(crypto);
        KademliaRoutingTable tableA = new KademliaRoutingTable("01950000-0000-7000-8000-00000000000a");
        DhtStoreService dhtStoreA = new DhtStoreService(crypto, null, limiterA);
        GrpcChannelPool poolA = new GrpcChannelPool(true, caCert.getPath(), nodeCert.getPath(), nodeKey.getPath());
        LocalSessionRegistry registryA = new LocalSessionRegistry();
        OfflineMessageQueueService queueA = new OfflineMessageQueueService();

        NodeRoutingGrpcService routingSvcA = new NodeRoutingGrpcService(
                crypto, registryA, queueA, dhtStoreA, limiterA, "");
        Server serverA = NettyServerBuilder.forPort(0)
                .sslContext(GrpcServerRunner.buildServerSslContext(caCert, nodeCert, nodeKey))
                .addService(ServerInterceptors.intercept(new DhtPeerGrpcService(tableA, dhtStoreA), mtlsInterceptor))
                .addService(ServerInterceptors.intercept(routingSvcA, mtlsInterceptor))
                .build()
                .start();
        String addressA = "127.0.0.1:" + serverA.getPort();

        // Levanta No B (No do Provedor Corporativo CNPJ onde o ERP Bridge envia a fatura enquanto Carlos esta offline)
        TierRateLimiterService limiterB = new TierRateLimiterService(crypto);
        KademliaRoutingTable tableB = new KademliaRoutingTable("01950000-0000-7000-8000-00000000000b");
        tableB.registerOrUpdateNode(tableA.getLocalNodeId(), addressA);
        DhtStoreService dhtStoreB = new DhtStoreService(crypto, null, limiterB);
        GrpcChannelPool poolB = new GrpcChannelPool(true, caCert.getPath(), nodeCert.getPath(), nodeKey.getPath());
        LocalSessionRegistry registryB = new LocalSessionRegistry();
        OfflineMessageQueueService queueB = new OfflineMessageQueueService();
        MessageRouterService routerB = new MessageRouterService(
                registryB, queueB, dhtStoreB, tableB, poolB, crypto, "127.0.0.1:9999");
        ClientStreamGrpcService streamSvcB = new ClientStreamGrpcService(
                crypto, registryB, queueB, routerB, limiterB);

        try {
            // Registra HomeNodeClaim oficial de Carlos no No A (Carlos esta OFFLINE)
            RawKeyPair carlosEd = CanonicalTranscripts.generateEd25519KeyPair();
            RawKeyPair carlosX = CanonicalTranscripts.generateX25519KeyPair();
            VerificationCredential carlosCred = pki.issueCredential(
                    carlosEd, carlosX, DocumentType.CPF, addressA, 1L, FIXED_NOW_MILLIS);
            String carlosUuid = carlosCred.getUserUuid();
            HomeNodeClaim carlosHomeClaim = pki.signPrincipalHomeClaim(carlosUuid, addressA, 1L, FIXED_NOW_MILLIS);
            assertTrue(dhtStoreA.storeHomeNodeClaim(carlosHomeClaim, carlosCred, carlosEd.publicKey()));

            // Conta Corporativa CNPJ com Desktop offline + ERP_API online no No B
            RawKeyPair corpRootEd = CanonicalTranscripts.generateEd25519KeyPair();
            RawKeyPair corpRootX = CanonicalTranscripts.generateX25519KeyPair();
            VerificationCredential corpCred = pki.issueCredential(
                    corpRootEd, corpRootX, DocumentType.CNPJ, "127.0.0.1:9999", 1L, FIXED_NOW_MILLIS);
            String corpUuid = corpCred.getUserUuid();

            RawKeyPair corpDeskEd = CanonicalTranscripts.generateEd25519KeyPair();
            RawKeyPair corpDeskX = CanonicalTranscripts.generateX25519KeyPair();
            DeviceDelegationCert corpDeskCert = V2GovernanceTestFixtures.signDeviceDelegation(
                    corpRootEd, corpDeskEd, corpDeskX, DeviceRole.DESKTOP, FIXED_NOW_MILLIS);
            assertTrue(dhtStoreB.storeSessionFromClient(V2GovernanceTestFixtures.buildV2RegisterRequest(
                    corpUuid, corpDeskCert.getDeviceId(), "127.0.0.1:9999", corpRootEd.publicKey(),
                    corpDeskEd, corpDeskX, corpDeskCert, corpCred, null, FIXED_NOW_MILLIS)).isSuccess());

            RawKeyPair erpEd = CanonicalTranscripts.generateEd25519KeyPair();
            RawKeyPair erpX = CanonicalTranscripts.generateX25519KeyPair();
            DeviceDelegationCert erpCert = V2GovernanceTestFixtures.signDeviceDelegation(
                    corpRootEd, erpEd, erpX, DeviceRole.ERP_API, FIXED_NOW_MILLIS);

            BlockingQueue<ServerEnvelope> erpFeedback = new LinkedBlockingQueue<>();
            StreamObserver<ClientEnvelope> erpStream = streamSvcB.connect(new StreamObserver<>() {
                @Override public void onNext(ServerEnvelope value) { erpFeedback.add(value); }
                @Override public void onError(Throwable t) { fail(t); }
                @Override public void onCompleted() {}
            });
            erpStream.onNext(V2GovernanceTestFixtures.buildSignedV2Envelope(
                    CanonicalTranscripts.generateTemporalUuidV7().toString(),
                    corpUuid, erpCert.getDeviceId(), corpUuid, MessageType.AUTH_HANDSHAKE, "",
                    new byte[0], erpEd, erpCert, corpCred, FIXED_NOW_MILLIS, true));

            // ERP_API envia boleto E2E para Carlos (offline no No A)
            String invoiceMsgId = CanonicalTranscripts.generateTemporalUuidV7().toString();
            erpStream.onNext(V2GovernanceTestFixtures.buildSignedV2Envelope(
                    invoiceMsgId, corpUuid, erpCert.getDeviceId(), carlosUuid, MessageType.TEXT, "",
                    "boleto-criptografado".getBytes(StandardCharsets.UTF_8),
                    erpEd, erpCert, corpCred, FIXED_NOW_MILLIS, false));

            // 1. No B encaminha via mTLS ao Home Node A e devolve SERVER_QUEUED_ACK ao ERP Bridge
            ServerEnvelope queuedAck = erpFeedback.poll(3, TimeUnit.SECONDS);
            assertNotNull(queuedAck, "ERP Bridge deve receber SERVER_QUEUED_ACK do No Casa remoto");
            assertEquals(MessageType.SERVER_QUEUED_ACK, queuedAck.getType());
            assertEquals(invoiceMsgId, queuedAck.getRefMessageId());

            // 2. Fila offline do No A (e NAO a do No B) possui o boleto de Carlos
            assertEquals(1, queueA.getStoredMessageCount(carlosUuid));
            assertEquals(0, queueB.getStoredMessageCount(carlosUuid));

            // 3. O Desktop corporativo offline NAO deve ter recebido copia na fila offline (sender role == ERP_API)
            assertEquals(0, queueB.getStoredMessageCount(corpUuid));

            // 4. Protecao anti-loop: RoutePayload com hop_count > 2 deve ser abortado com Status.ABORTED
            AtomicReference<Throwable> loopErr = new AtomicReference<>();
            routingSvcA.routePayload(
                    RoutePayloadRequest.newBuilder()
                            .setMessageId(CanonicalTranscripts.generateTemporalUuidV7().toString())
                            .setSenderUuid(corpUuid)
                            .setReceiverUuid(carlosUuid)
                            .setType(MessageType.TEXT)
                            .setHopCount(3)
                            .build(),
                    new StreamObserver<>() {
                        @Override public void onNext(RoutePayloadResponse value) {}
                        @Override public void onError(Throwable t) { loopErr.set(t); }
                        @Override public void onCompleted() {}
                    });
            assertNotNull(loopErr.get());
            assertEquals(Status.Code.ABORTED, ((StatusRuntimeException) loopErr.get()).getStatus().getCode());

            erpStream.onCompleted();
        } finally {
            poolA.shutdownAll();
            poolB.shutdownAll();
            serverA.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("Roteamento E2E Inter-Provedores via mTLS: Alice no No A envia mensagem ao vivo para Bob no No B")
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
                @Override public void onNext(ServerEnvelope value) { bobReceivedQueue.add(value); }
                @Override public void onError(Throwable t) {}
                @Override public void onCompleted() {}
            });

            bobStream.onNext(buildSignedV1Envelope(cryptoService, bobKeys, bobPub, bobUuid, bobUuid,
                    cryptoService.generateTemporalUuidV7().toString(), MessageType.AUTH_HANDSHAKE, new byte[0]));

            long deadline = System.currentTimeMillis() + 2000;
            while (registryB.getActiveDeviceCount(bobUuid) == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertEquals(1, registryB.getActiveDeviceCount(bobUuid));

            // Alice abre stream no No A, autentica com AUTH_HANDSHAKE e envia mensagem E2E para Bob
            ClientStreamServiceGrpc.ClientStreamServiceStub aliceAsyncStub = ClientStreamServiceGrpc.newStub(aliceChannelToA);
            StreamObserver<ClientEnvelope> aliceStream = aliceAsyncStub.connect(new StreamObserver<>() {
                @Override public void onNext(ServerEnvelope value) {}
                @Override public void onError(Throwable t) {}
                @Override public void onCompleted() {}
            });

            aliceStream.onNext(buildSignedV1Envelope(cryptoService, aliceKeys, alicePub, aliceUuid, aliceUuid,
                    cryptoService.generateTemporalUuidV7().toString(), MessageType.AUTH_HANDSHAKE, new byte[0]));

            String msgId = cryptoService.generateTemporalUuidV7().toString();
            byte[] encryptedPayload = "mensagem-secreta-inter-provedores".getBytes(StandardCharsets.UTF_8);
            ClientEnvelope aliceToBob = buildSignedV1Envelope(
                    cryptoService, aliceKeys, alicePub, aliceUuid, bobUuid, msgId, MessageType.TEXT, encryptedPayload);

            aliceStream.onNext(aliceToBob);

            ServerEnvelope receivedByBob = bobReceivedQueue.poll(5, TimeUnit.SECONDS);
            assertNotNull(receivedByBob, "Bob no No B deve receber a mensagem enviada por Alice no No A via mTLS");
            assertEquals(msgId, receivedByBob.getMessageId());
            assertEquals(aliceUuid, receivedByBob.getSenderUuid());
            assertEquals(bobUuid, receivedByBob.getReceiverUuid());
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
            @Override public void onNext(ServerEnvelope value) {}
            @Override public void onError(Throwable t) {
                capturedError.set(t);
                errorLatch.countDown();
            }
            @Override public void onCompleted() {}
        });

        KeyPair attackerKeys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] attackerPub = extractRaw32BytePublicKey(attackerKeys);
        String fakeVictimUuid = "01950000-0000-7000-8000-999999999999";

        ClientEnvelope forgedHandshake = buildSignedV1Envelope(
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
            @Override public void onNext(ServerEnvelope value) {}
            @Override public void onError(Throwable t) {
                capturedError.set(t);
                errorLatch.countDown();
            }
            @Override public void onCompleted() {}
        });

        KeyPair aliceKeys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] alicePub = extractRaw32BytePublicKey(aliceKeys);
        String aliceUuid = cryptoService.deriveIdentityUuidV7(alicePub).toString();
        String bobUuid = "01950000-0000-7000-8000-000000000099";

        ClientEnvelope prematureText = buildSignedV1Envelope(
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

    private static ClientEnvelope buildSignedV1Envelope(
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
        for (String candidate : new String[]{primary, "../" + primary, fallback, "../../" + fallback}) {
            File f = new File(candidate);
            if (f.exists()) {
                return f;
            }
        }
        throw new IllegalStateException("Certificado de teste nao encontrado: " + primary);
    }
}
