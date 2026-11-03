package br.dev.ispchat.node.messaging;

import br.dev.ispchat.grpc.ServerEnvelope;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro de streams gRPC ativas conectadas localmente neste No.
 * Permite broadcast simultaneo (fan-out) para multiplos dispositivos do mesmo UUIDv7
 * (ex: Celular e PC tocando juntos ao receber um RING ou recebendo mensagens em tempo real).
 */
@Component
public class LocalSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(LocalSessionRegistry.class);

    private final Map<String, Set<StreamObserver<ServerEnvelope>>> activeStreamsByUuid = new ConcurrentHashMap<>();

    /**
     * Registra uma stream autenticada para um UUIDv7.
     */
    public void registerStream(String userUuid, StreamObserver<ServerEnvelope> streamObserver) {
        if (userUuid == null || userUuid.isBlank() || streamObserver == null) {
            return;
        }
        String normalizedUuid = userUuid.trim().toLowerCase();
        activeStreamsByUuid
                .computeIfAbsent(normalizedUuid, k -> ConcurrentHashMap.newKeySet())
                .add(streamObserver);
    }

    /**
     * Remove uma stream encerrada ou com erro de rede.
     */
    public void unregisterStream(String userUuid, StreamObserver<ServerEnvelope> streamObserver) {
        if (userUuid == null || userUuid.isBlank() || streamObserver == null) {
            return;
        }
        String normalizedUuid = userUuid.trim().toLowerCase();
        activeStreamsByUuid.computeIfPresent(normalizedUuid, (uuid, streams) -> {
            streams.remove(streamObserver);
            return streams.isEmpty() ? null : streams;
        });
    }

    /**
     * Envia um ServerEnvelope simultaneamente para todos os aparelhos conectados localmente daquele UUIDv7.
     *
     * @return numero de dispositivos locais que receberam o pacote com sucesso.
     */
    public int deliverToLocalStreams(String receiverUuid, ServerEnvelope envelope) {
        if (receiverUuid == null || receiverUuid.isBlank() || envelope == null) {
            return 0;
        }
        String normalizedUuid = receiverUuid.trim().toLowerCase();
        Set<StreamObserver<ServerEnvelope>> streams = activeStreamsByUuid.get(normalizedUuid);
        if (streams == null || streams.isEmpty()) {
            return 0;
        }

        int deliveredCount = 0;
        for (StreamObserver<ServerEnvelope> observer : streams) {
            try {
                // StreamObserver do gRPC nao e thread-safe para escritas concorrentes
                synchronized (observer) {
                    observer.onNext(envelope);
                }
                deliveredCount++;
            } catch (Exception e) {
                log.debug("Removendo stream inativa para UUID {}: {}", normalizedUuid, e.getMessage());
                unregisterStream(normalizedUuid, observer);
            }
        }
        return deliveredCount;
    }

    public int getActiveDeviceCount(String userUuid) {
        if (userUuid == null || userUuid.isBlank()) {
            return 0;
        }
        Set<StreamObserver<ServerEnvelope>> streams = activeStreamsByUuid.get(userUuid.trim().toLowerCase());
        return streams != null ? streams.size() : 0;
    }
}
