package br.dev.ispchat.node.dht;

import br.dev.ispchat.grpc.DhtPeerServiceGrpc;
import br.dev.ispchat.grpc.FindNodeRequest;
import br.dev.ispchat.grpc.FindNodeResponse;
import br.dev.ispchat.grpc.FindValueRequest;
import br.dev.ispchat.grpc.FindValueResponse;
import br.dev.ispchat.grpc.NodeInfo;
import br.dev.ispchat.grpc.PingRequest;
import br.dev.ispchat.grpc.PingResponse;
import br.dev.ispchat.grpc.StoreRequest;
import br.dev.ispchat.grpc.StoreResponse;
import br.dev.ispchat.grpc.UserSessionInfo;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Servico gRPC Kademlia de Federacao (No <-> No via mTLS obrigatorio).
 */
@Service
public class DhtPeerGrpcService extends DhtPeerServiceGrpc.DhtPeerServiceImplBase {

    private final KademliaRoutingTable routingTable;
    private final DhtStoreService dhtStoreService;

    public DhtPeerGrpcService(
            KademliaRoutingTable routingTable,
            DhtStoreService dhtStoreService) {
        this.routingTable = routingTable;
        this.dhtStoreService = dhtStoreService;
    }

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        if (!request.getSenderNodeId().isBlank() && !request.getSenderAddress().isBlank()) {
            routingTable.registerOrUpdateNode(request.getSenderNodeId(), request.getSenderAddress());
        }
        PingResponse response = PingResponse.newBuilder()
                .setResponderNodeId(routingTable.getLocalNodeId())
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void store(StoreRequest request, StreamObserver<StoreResponse> responseObserver) {
        boolean stored = dhtStoreService.storeVerifiedSession(
                request.getUserUuid(),
                request.getNodeAddress(),
                request.getPublicKey().toByteArray(),
                request.getX25519PublicKey().toByteArray(),
                request.getSignature().toByteArray(),
                request.getTimestamp()
        );

        StoreResponse response = StoreResponse.newBuilder()
                .setSuccess(stored)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void findNode(FindNodeRequest request, StreamObserver<FindNodeResponse> responseObserver) {
        List<NodeInfo> closestNodes = routingTable.findClosestNodes(request.getTargetId());
        FindNodeResponse response = FindNodeResponse.newBuilder()
                .addAllClosestNodes(closestNodes)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void findValue(FindValueRequest request, StreamObserver<FindValueResponse> responseObserver) {
        String targetUuid = request.getTargetUuid();
        List<UserSessionInfo> localSessions = dhtStoreService.getSessions(targetUuid);
        List<NodeInfo> closestNodes = routingTable.findClosestNodes(targetUuid);

        FindValueResponse.Builder builder = FindValueResponse.newBuilder()
                .addAllSessions(localSessions)
                .addAllClosestNodes(closestNodes);

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }
}
