package br.dev.ispchat.node.dht;

import br.dev.ispchat.grpc.DhtClientServiceGrpc;
import br.dev.ispchat.grpc.DhtPeerServiceGrpc;
import br.dev.ispchat.grpc.FindUserSessionsRequest;
import br.dev.ispchat.grpc.FindUserSessionsResponse;
import br.dev.ispchat.grpc.FindValueRequest;
import br.dev.ispchat.grpc.FindValueResponse;
import br.dev.ispchat.grpc.HomeNodeClaim;
import br.dev.ispchat.grpc.IdentityReplacementNotice;
import br.dev.ispchat.grpc.NodeInfo;
import br.dev.ispchat.grpc.RegisterSessionRequest;
import br.dev.ispchat.grpc.RegisterSessionResponse;
import br.dev.ispchat.grpc.StoreRequest;
import br.dev.ispchat.grpc.UserSessionInfo;
import br.dev.ispchat.node.dht.DhtStoreService.StoreOutcome;
import br.dev.ispchat.node.grpc.GrpcChannelPool;
import br.dev.ispchat.node.security.CryptoService;
import br.dev.ispchat.node.security.MtlsFederationInterceptor;
import br.dev.ispchat.node.security.TierRateLimiterService;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Servico gRPC de Borda (App Flutter <-> No Local) para registro e descoberta de sessoes V1/V2 na DHT.
 */
@Service
public class DhtClientGrpcService extends DhtClientServiceGrpc.DhtClientServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(DhtClientGrpcService.class);
    public static final int REPLICATION_FACTOR_ALPHA = 3;

    private final DhtStoreService dhtStoreService;
    private final KademliaRoutingTable routingTable;
    private final GrpcChannelPool channelPool;
    private final CryptoService cryptoService;
    private final TierRateLimiterService tierRateLimiterService;

    public DhtClientGrpcService(
            DhtStoreService dhtStoreService,
            KademliaRoutingTable routingTable,
            GrpcChannelPool channelPool,
            CryptoService cryptoService) {
        this(dhtStoreService, routingTable, channelPool, cryptoService, new TierRateLimiterService(cryptoService));
    }

    @Autowired
    public DhtClientGrpcService(
            DhtStoreService dhtStoreService,
            KademliaRoutingTable routingTable,
            GrpcChannelPool channelPool,
            CryptoService cryptoService,
            @Autowired(required = false) TierRateLimiterService tierRateLimiterService) {
        this.dhtStoreService = dhtStoreService;
        this.routingTable = routingTable;
        this.channelPool = channelPool;
        this.cryptoService = cryptoService;
        this.tierRateLimiterService = tierRateLimiterService != null
                ? tierRateLimiterService
                : new TierRateLimiterService(cryptoService);
    }

    @Override
    public void registerSession(RegisterSessionRequest request, StreamObserver<RegisterSessionResponse> responseObserver) {
        boolean hasCred = request.hasVerificationCredential()
                && !request.getVerificationCredential().getCredentialId().isBlank();
        boolean hasDelCert = request.hasDelegationCert()
                && !request.getDelegationCert().getCertId().isBlank();
        boolean isSecondaryDevice = !request.getDeviceId().isBlank()
                && !request.getDeviceId().trim().equalsIgnoreCase(request.getUserUuid().trim());

        if (!hasCred && (hasDelCert || isSecondaryDevice)) {
            responseObserver.onError(Status.PERMISSION_DENIED
                    .withDescription("Contas nao verificadas nao possuem permissao para delegar dispositivos secundarios")
                    .asRuntimeException());
            return;
        }

        if (!hasCred && !tierRateLimiterService.checkUnverifiedRegistrationPrefixAllowed(
                MtlsFederationInterceptor.REMOTE_ADDR_CONTEXT_KEY.get())) {
            responseObserver.onError(Status.RESOURCE_EXHAUSTED
                    .withDescription("Limite por hora de registros nao verificados para este prefixo de rede excedido")
                    .asRuntimeException());
            return;
        }

        StoreOutcome outcome = dhtStoreService.storeSessionFromClient(request);
        switch (outcome.status()) {
            case PERMISSION_DENIED -> {
                responseObserver.onError(Status.PERMISSION_DENIED
                        .withDescription(outcome.message())
                        .asRuntimeException());
                return;
            }
            case RESOURCE_EXHAUSTED -> {
                responseObserver.onError(Status.RESOURCE_EXHAUSTED
                        .withDescription(outcome.message())
                        .asRuntimeException());
                return;
            }
            case INVALID_SIGNATURE_OR_TIMESTAMP -> {
                responseObserver.onNext(RegisterSessionResponse.newBuilder()
                        .setSuccess(false)
                        .setErrorMessage(outcome.message())
                        .build());
                responseObserver.onCompleted();
                return;
            }
            case STORED -> {
                // Propaga o registro autenticado via mTLS para os K nos mais proximos na DHT Kademlia
                replicateToClosestPeers(request);
                responseObserver.onNext(RegisterSessionResponse.newBuilder()
                        .setSuccess(true)
                        .build());
                responseObserver.onCompleted();
            }
        }
    }

    @Override
    public void findUserSessions(FindUserSessionsRequest request, StreamObserver<FindUserSessionsResponse> responseObserver) {
        String targetUuid = request.getTargetUuid();
        Map<String, UserSessionInfo> consolidatedSessions = new LinkedHashMap<>();

        // 1. Consulta armazenamento local
        for (UserSessionInfo session : dhtStoreService.getSessions(targetUuid)) {
            mergeSession(consolidatedSessions, session);
        }

        HomeNodeClaim bestHomeClaim = dhtStoreService.getHomeNodeClaim(targetUuid).orElse(null);
        IdentityReplacementNotice replacementNotice = dhtStoreService.getReplacementNotice(targetUuid).orElse(null);

        // 2. Consulta os nos mais proximos na malha Kademlia via mTLS
        List<NodeInfo> closestPeers = routingTable.findClosestNodes(targetUuid, REPLICATION_FACTOR_ALPHA);
        for (NodeInfo peer : closestPeers) {
            try {
                ManagedChannel channel = channelPool.getChannel(peer.getNodeAddress());
                DhtPeerServiceGrpc.DhtPeerServiceBlockingStub stub = DhtPeerServiceGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(3, TimeUnit.SECONDS);

                FindValueResponse peerResponse = stub.findValue(
                        FindValueRequest.newBuilder().setTargetUuid(targetUuid).build()
                );
                for (UserSessionInfo remoteSession : peerResponse.getSessionsList()) {
                    // Protecao Anti-MITM V1/V2: exige assinatura valida (incluindo x25519_public_key em ISPCHAT-DHT-V2)
                    if (cryptoService.verifyRemoteSessionInfo(targetUuid, remoteSession)) {
                        mergeSession(consolidatedSessions, remoteSession);
                    } else {
                        log.warn("Descartada sessao remota suspeita do par {} para UUID {}: falha na verificacao criptografica DHT",
                                peer.getNodeId(), targetUuid);
                    }
                }
                if (peerResponse.hasHomeNodeClaim() && !peerResponse.getHomeNodeClaim().getUserUuid().isBlank()) {
                    HomeNodeClaim candidateClaim = peerResponse.getHomeNodeClaim();
                    byte[] rootPubHint = extractRootPubFromSessions(consolidatedSessions);
                    if (candidateClaim.getUserUuid().trim().equalsIgnoreCase(targetUuid.trim())
                            && cryptoService.verifyHomeNodeClaim(candidateClaim, null, rootPubHint)) {
                        if (bestHomeClaim == null || candidateClaim.getRoutingEpoch() > bestHomeClaim.getRoutingEpoch()) {
                            bestHomeClaim = candidateClaim;
                            dhtStoreService.storeHomeNodeClaim(candidateClaim, null, rootPubHint);
                        }
                    }
                }
                if (replacementNotice == null
                        && peerResponse.hasReplacementNotice()
                        && !peerResponse.getReplacementNotice().getRevocationId().isBlank()) {
                    IdentityReplacementNotice candidateNotice = peerResponse.getReplacementNotice();
                    if (!cryptoService.hasTrustedPrincipalKeys()
                            || cryptoService.verifyIdentityReplacementNotice(candidateNotice)) {
                        replacementNotice = candidateNotice;
                    }
                }
            } catch (Exception e) {
                log.debug("Falha ao consultar par DHT {} ({}): {}", peer.getNodeId(), peer.getNodeAddress(), e.getMessage());
            }
        }

        Set<String> uniqueNodeAddresses = new LinkedHashSet<>();
        FindUserSessionsResponse.Builder responseBuilder = FindUserSessionsResponse.newBuilder();
        for (UserSessionInfo session : consolidatedSessions.values()) {
            if (!session.getNodeAddress().isBlank()) {
                uniqueNodeAddresses.add(session.getNodeAddress());
            }
            responseBuilder.addSessions(session);
        }
        responseBuilder.addAllNodeAddresses(uniqueNodeAddresses);

        if (bestHomeClaim != null) {
            responseBuilder.setHomeNodeClaim(bestHomeClaim);
        }
        if (replacementNotice != null) {
            responseBuilder.setReplacementNotice(replacementNotice);
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    private static void mergeSession(Map<String, UserSessionInfo> consolidated, UserSessionInfo candidate) {
        String key = candidate.getDeviceId().isBlank()
                ? "v1@" + candidate.getNodeAddress().trim().toLowerCase(Locale.ROOT)
                : "v2@" + candidate.getDeviceId().trim().toLowerCase(Locale.ROOT);
        consolidated.merge(key, candidate, (existing, incoming) ->
                incoming.getTimestamp() >= existing.getTimestamp() ? incoming : existing);
    }

    private static byte[] extractRootPubFromSessions(Map<String, UserSessionInfo> sessions) {
        for (UserSessionInfo s : sessions.values()) {
            if (!s.getRootEd25519Pub().isEmpty()) {
                return s.getRootEd25519Pub().toByteArray();
            }
            if (s.getDeviceId().isBlank() && !s.getEd25519PublicKey().isEmpty()) {
                return s.getEd25519PublicKey().toByteArray();
            }
        }
        return null;
    }

    private void replicateToClosestPeers(RegisterSessionRequest request) {
        List<NodeInfo> closestPeers = routingTable.findClosestNodes(request.getUserUuid(), REPLICATION_FACTOR_ALPHA);
        if (closestPeers.isEmpty()) {
            return;
        }

        StoreRequest.Builder storeBuilder = StoreRequest.newBuilder()
                .setUserUuid(request.getUserUuid())
                .setNodeAddress(request.getNodeAddress())
                .setPublicKey(request.getPublicKey())
                .setX25519PublicKey(request.getX25519PublicKey())
                .setSignature(request.getSignature())
                .setTimestamp(request.getTimestamp())
                .setDeviceId(request.getDeviceId())
                .setRootEd25519Pub(request.getRootEd25519Pub());

        if (request.hasDelegationCert()) {
            storeBuilder.setDelegationCert(request.getDelegationCert());
        }
        if (request.hasVerificationCredential()) {
            storeBuilder.setVerificationCredential(request.getVerificationCredential());
        }
        if (request.hasHomeNodeClaim()) {
            storeBuilder.setHomeNodeClaim(request.getHomeNodeClaim());
        }
        StoreRequest storeRequest = storeBuilder.build();

        for (NodeInfo peer : closestPeers) {
            try {
                ManagedChannel channel = channelPool.getChannel(peer.getNodeAddress());
                DhtPeerServiceGrpc.DhtPeerServiceBlockingStub stub = DhtPeerServiceGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(3, TimeUnit.SECONDS);
                stub.store(storeRequest);
            } catch (Exception e) {
                log.warn("Falha ao replicar Store DHT para no par {} ({}): {}",
                        peer.getNodeId(), peer.getNodeAddress(), e.getMessage());
            }
        }
    }
}
