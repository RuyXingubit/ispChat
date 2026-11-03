package br.dev.ispchat.node.messaging;

import br.dev.ispchat.grpc.MessageType;
import br.dev.ispchat.grpc.NodeRoutingServiceGrpc;
import br.dev.ispchat.grpc.RoutePayloadRequest;
import br.dev.ispchat.grpc.RoutePayloadResponse;
import br.dev.ispchat.grpc.ServerEnvelope;
import br.dev.ispchat.node.security.CryptoService;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Servico gRPC de Federacao (No <-> No via mTLS obrigatorio) para recepcao de payloads roteados.
 *
 * Mesmo trafegando sobre mTLS entre provedores, aplica validacao Zero-Trust na assinatura Ed25519
 * do remetente original antes de entregar ao destinatario ou persistir na fila offline do Redis.
 */
@Service
public class NodeRoutingGrpcService extends NodeRoutingServiceGrpc.NodeRoutingServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(NodeRoutingGrpcService.class);

    private final CryptoService cryptoService;
    private final LocalSessionRegistry localSessionRegistry;
    private final OfflineMessageQueueService offlineQueueService;

    public NodeRoutingGrpcService(
            CryptoService cryptoService,
            LocalSessionRegistry localSessionRegistry,
            OfflineMessageQueueService offlineQueueService) {
        this.cryptoService = cryptoService;
        this.localSessionRegistry = localSessionRegistry;
        this.offlineQueueService = offlineQueueService;
    }

    @Override
    public void routePayload(RoutePayloadRequest request, StreamObserver<RoutePayloadResponse> responseObserver) {
        boolean signatureValid = cryptoService.verifyMessageEnvelope(
                request.getMessageId(),
                request.getSenderUuid(),
                request.getReceiverUuid(),
                request.getType().getNumber(),
                request.getTimestamp(),
                request.getEncryptedPayload().toByteArray(),
                request.getSenderPublicKey().toByteArray(),
                request.getSignature().toByteArray()
        );

        if (!signatureValid) {
            log.warn("Rejeitado RoutePayload de {} para {}: assinatura Ed25519 ou vinculo UUIDv7 invalido",
                    request.getSenderUuid(), request.getReceiverUuid());
            responseObserver.onNext(RoutePayloadResponse.newBuilder().setDelivered(false).build());
            responseObserver.onCompleted();
            return;
        }

        ServerEnvelope serverEnvelope = ServerEnvelope.newBuilder()
                .setMessageId(request.getMessageId())
                .setSenderUuid(request.getSenderUuid())
                .setType(request.getType())
                .setEncryptedPayload(request.getEncryptedPayload())
                .setSenderPublicKey(request.getSenderPublicKey())
                .setSignature(request.getSignature())
                .setTimestamp(request.getTimestamp())
                .build();

        int localDelivered = localSessionRegistry.deliverToLocalStreams(request.getReceiverUuid(), serverEnvelope);
        boolean deliveredOrQueued = localDelivered > 0;

        if (!deliveredOrQueued && request.getType() == MessageType.TEXT) {
            deliveredOrQueued = offlineQueueService.enqueue(request.getReceiverUuid(), serverEnvelope);
        }

        responseObserver.onNext(RoutePayloadResponse.newBuilder()
                .setDelivered(deliveredOrQueued)
                .build());
        responseObserver.onCompleted();
    }
}
