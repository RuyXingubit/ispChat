package br.dev.ispchat.node.messaging;

import br.dev.ispchat.grpc.ServerEnvelope;
import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Gerenciador de Fila Offline (Store-and-Forward) baseado em Redis List.
 *
 * Regras de seguranca e resiliencia:
 * - Limite maximo de mensagens por UUIDv7 (MAX_QUEUE_SIZE_PER_USER) contra ataques de exaustao de RAM (DoS).
 * - TTL automatico de 7 dias na chave Redis.
 * - Suporte a fallback em memoria quando o Redis nao estiver disponivel em testes locais isolados.
 */
@Service
public class OfflineMessageQueueService {

    private static final Logger log = LoggerFactory.getLogger(OfflineMessageQueueService.class);

    public static final String REDIS_QUEUE_KEY_PREFIX = "ispchat:offline:queue:";
    public static final int MAX_QUEUE_SIZE_PER_USER = 1000;
    public static final Duration OFFLINE_QUEUE_TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final Map<String, Deque<byte[]>> fallbackMemoryStore = new ConcurrentHashMap<>();

    @Autowired
    public OfflineMessageQueueService(@Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public OfflineMessageQueueService() {
        this(null);
    }

    /**
     * Enfileira um ServerEnvelope criptografado para entrega futura assim que o destinatario ficar online.
     */
    public boolean enqueue(String receiverUuid, ServerEnvelope envelope) {
        if (receiverUuid == null || receiverUuid.isBlank() || envelope == null) {
            return false;
        }
        String normalizedUuid = receiverUuid.trim().toLowerCase();
        byte[] serialized = envelope.toByteArray();

        if (redisTemplate != null) {
            try {
                String redisKey = REDIS_QUEUE_KEY_PREFIX + normalizedUuid;
                Long currentSize = redisTemplate.opsForList().size(redisKey);
                if (currentSize != null && currentSize >= MAX_QUEUE_SIZE_PER_USER) {
                    log.warn("Fila offline do Redis atingiu o limite anti-DoS ({}) para UUID {}", MAX_QUEUE_SIZE_PER_USER, normalizedUuid);
                    return false;
                }
                String base64Payload = Base64.getEncoder().encodeToString(serialized);
                redisTemplate.opsForList().rightPush(redisKey, base64Payload);
                redisTemplate.expire(redisKey, OFFLINE_QUEUE_TTL);
                return true;
            } catch (Exception e) {
                log.debug("Redis indisponivel ao enfileirar mensagem para {}, usando armazenamento local de contingencia: {}",
                        normalizedUuid, e.getMessage());
            }
        }

        Deque<byte[]> queue = fallbackMemoryStore.computeIfAbsent(normalizedUuid, k -> new ConcurrentLinkedDeque<>());
        if (queue.size() >= MAX_QUEUE_SIZE_PER_USER) {
            return false;
        }
        queue.addLast(serialized);
        return true;
    }

    /**
     * Descarrega e apaga atomicamente todas as mensagens pendentes da fila offline de um UUIDv7.
     */
    public List<ServerEnvelope> drainQueue(String receiverUuid) {
        if (receiverUuid == null || receiverUuid.isBlank()) {
            return List.of();
        }
        String normalizedUuid = receiverUuid.trim().toLowerCase();
        List<ServerEnvelope> drained = new ArrayList<>();

        if (redisTemplate != null) {
            try {
                String redisKey = REDIS_QUEUE_KEY_PREFIX + normalizedUuid;
                while (true) {
                    String base64Item = redisTemplate.opsForList().leftPop(redisKey);
                    if (base64Item == null) {
                        break;
                    }
                    byte[] rawBytes = Base64.getDecoder().decode(base64Item);
                    drained.add(ServerEnvelope.parseFrom(rawBytes));
                }
            } catch (Exception e) {
                log.debug("Redis indisponivel ao drenar fila para {}, verificando fila em memoria: {}",
                        normalizedUuid, e.getMessage());
            }
        }

        Deque<byte[]> memoryQueue = fallbackMemoryStore.remove(normalizedUuid);
        if (memoryQueue != null) {
            byte[] item;
            while ((item = memoryQueue.pollFirst()) != null) {
                try {
                    drained.add(ServerEnvelope.parseFrom(item));
                } catch (InvalidProtocolBufferException e) {
                    log.warn("Mensagem corrompida descartada ao drenar fila offline para {}", normalizedUuid);
                }
            }
        }

        return drained;
    }
}
