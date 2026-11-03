package br.dev.ispchat.node.messaging;

import br.dev.ispchat.grpc.ClientEnvelope;
import br.dev.ispchat.grpc.ClientStreamServiceGrpc;
import br.dev.ispchat.grpc.MessageType;
import br.dev.ispchat.grpc.ServerEnvelope;
import br.dev.ispchat.node.security.CryptoService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Servico gRPC de Stream Bidirecional (App Flutter <-> No Local).
 *
 * Regras de Seguranca:
 * 1. Todo pacote recebido (inclusive o primeiro AUTH_HANDSHAKE) deve conter uma assinatura
 *    Ed25519 valida onde deriveIdentityUuidV7(sender_public_key) == sender_uuid.
 * 2. O primeiro pacote enviado pelo App registra a identidade da stream no LocalSessionRegistry
 *    e descarrega imediatamente a fila offline (Redis List Store-and-Forward).
 * 3. Pacotes subsequentes na mesma stream nao podem trocar o sender_uuid autenticado.
 */
@Service
public class ClientStreamGrpcService extends ClientStreamServiceGrpc.ClientStreamServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(ClientStreamGrpcService.class);

    private final CryptoService cryptoService;
    private final LocalSessionRegistry localSessionRegistry;
    private final OfflineMessageQueueService offlineQueueService;
    private final MessageRouterService messageRouterService;

    public ClientStreamGrpcService(
            CryptoService cryptoService,
            LocalSessionRegistry localSessionRegistry,
            OfflineMessageQueueService offlineQueueService,
            MessageRouterService messageRouterService) {
        this.cryptoService = cryptoService;
        this.localSessionRegistry = localSessionRegistry;
        this.offlineQueueService = offlineQueueService;
        this.messageRouterService = messageRouterService;
    }

    @Override
    public StreamObserver<ClientEnvelope> connect(StreamObserver<ServerEnvelope> responseObserver) {
        AtomicReference<String> authenticatedUserUuid = new AtomicReference<>(null);

        return new StreamObserver<>() {
            @Override
            public void onNext(ClientEnvelope envelope) {
                boolean valid = cryptoService.verifyMessageEnvelope(
                        envelope.getMessageId(),
                        envelope.getSenderUuid(),
                        envelope.getReceiverUuid(),
                        envelope.getType().getNumber(),
                        envelope.getTimestamp(),
                        envelope.getEncryptedPayload().toByteArray(),
                        envelope.getSenderPublicKey().toByteArray(),
                        envelope.getSignature().toByteArray()
                );

                if (!valid) {
                    log.warn("Pacote ClientEnvelope rejeitado por assinatura Ed25519 invalida (sender={})",
                            envelope.getSenderUuid());
                    responseObserver.onError(Status.UNAUTHENTICATED
                            .withDescription("Assinatura Ed25519 invalida, timestamp expirado ou UUIDv7 incompativel com a PublicKey")
                            .asRuntimeException());
                    cleanupStream();
                    return;
                }

                String normalizedSender = envelope.getSenderUuid().trim().toLowerCase();
                String currentBoundUuid = authenticatedUserUuid.get();

                if (currentBoundUuid == null) {
                    if (authenticatedUserUuid.compareAndSet(null, normalizedSender)) {
                        localSessionRegistry.registerStream(normalizedSender, responseObserver);
                        drainOfflineMessagesToStream(normalizedSender, responseObserver);
                    }
                } else if (!currentBoundUuid.equals(normalizedSender)) {
                    log.warn("Tentativa de troca de sender_uuid na mesma stream ({} -> {}) bloqueada",
                            currentBoundUuid, normalizedSender);
                    responseObserver.onError(Status.PERMISSION_DENIED
                            .withDescription("Nao e permitido alterar o sender_uuid de uma stream ja autenticada")
                            .asRuntimeException());
                    cleanupStream();
                    return;
                }

                if (envelope.getType() == MessageType.AUTH_HANDSHAKE) {
                    // Handshake concluido e fila offline ja drenada
                    return;
                }

                messageRouterService.routeClientEnvelope(envelope);
            }

            @Override
            public void onError(Throwable t) {
                cleanupStream();
            }

            @Override
            public void onCompleted() {
                cleanupStream();
                responseObserver.onCompleted();
            }

            private void cleanupStream() {
                String boundUuid = authenticatedUserUuid.getAndSet(null);
                if (boundUuid != null) {
                    localSessionRegistry.unregisterStream(boundUuid, responseObserver);
                }
            }
        };
    }

    private void drainOfflineMessagesToStream(String userUuid, StreamObserver<ServerEnvelope> responseObserver) {
        List<ServerEnvelope> offlineMessages = offlineQueueService.drainQueue(userUuid);
        for (ServerEnvelope msg : offlineMessages) {
            synchronized (responseObserver) {
                responseObserver.onNext(msg);
            }
        }
    }
}
