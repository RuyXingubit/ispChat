package br.dev.ispchat.node.messaging;

import br.dev.ispchat.grpc.ServerEnvelope;
import com.google.protobuf.InvalidProtocolBufferException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerenciador de Fila Offline Multi-Dispositivo (Ring-Buffer + Cursor ACKed) baseado em Lua no Redis.
 *
 * Estrutura Redis V2 (namespaces isolados de chaves V1 para rollback seguro):
 * - ispchat:offline:msgs:<user_uuid>               -> Hash(message_id -> Base64(ServerEnvelope)) (cap 1.000, TTL 7d)
 * - ispchat:offline:order:<user_uuid>              -> ZSET(seq -> message_id) para ordem FIFO deterministica
 * - ispchat:offline:seq:<user_uuid>                -> Contador monotono INCR
 * - ispchat:offline:devs:<user_uuid>               -> SET de device_id conhecidos com pendencias
 * - ispchat:offline:pending:<user_uuid>:<device_id> -> ZSET(seq -> message_id) pendente de DELIVERY_ACK
 *
 * Regras:
 * 1. Ao atingir 1.000 mensagens no Hash, o script Lua despeja (ring-buffer eviction) a mensagem mais antiga
 *    em todos os pending ZSETs para que um Desktop desligado jamais bloqueie o celular ativo.
 * 2. No AUTH_HANDSHAKE, os envelopes pendentes do device_id sao transmitidos sem exclusao destrutiva.
 * 3. Somente ao receber ClientEnvelope(DELIVERY_ACK, ref_message_id) o item sai de pending:<uuid>:<device_id>,
 *    e o payload no Hash e removido assim que zero dispositivos tiverem referencia pendente.
 */
@Service
public class OfflineMessageQueueService {

    private static final Logger log = LoggerFactory.getLogger(OfflineMessageQueueService.class);

    public static final String REDIS_MSGS_KEY_PREFIX = "ispchat:offline:msgs:";
    public static final String REDIS_ORDER_KEY_PREFIX = "ispchat:offline:order:";
    public static final String REDIS_SEQ_KEY_PREFIX = "ispchat:offline:seq:";
    public static final String REDIS_DEVS_KEY_PREFIX = "ispchat:offline:devs:";
    public static final String REDIS_PENDING_KEY_PREFIX = "ispchat:offline:pending:";

    public static final int MAX_QUEUE_SIZE_PER_USER = 1000;
    public static final Duration OFFLINE_QUEUE_TTL = Duration.ofDays(7);
    private static final long TTL_SECONDS = OFFLINE_QUEUE_TTL.toSeconds();

    /**
     * Script Lua atomico para enfileiramento Ring-Buffer:
     * KEYS[1] = msgs Hash
     * KEYS[2] = order ZSET
     * KEYS[3] = seq Counter
     * KEYS[4] = devs SET
     * KEYS[5 .. 4 + targetCount] = pending ZSETs dos dispositivos alvo
     * KEYS[5 + targetCount .. #KEYS] = pending ZSETs dos demais dispositivos conhecidos
     * ARGV[1] = messageId, ARGV[2] = base64Payload, ARGV[3] = maxSize, ARGV[4] = ttlSeconds, ARGV[5] = targetCount
     * Retorna quantidade de mensagens antigas despejadas (evicted >= 0).
     */
    private static final DefaultRedisScript<Long> ENQUEUE_RING_BUFFER_SCRIPT = new DefaultRedisScript<>(
            """
            local msgId = ARGV[1]
            local payload = ARGV[2]
            local maxSize = tonumber(ARGV[3])
            local ttl = tonumber(ARGV[4])
            local targetCount = tonumber(ARGV[5])
            local targetEnd = 4 + targetCount

            if redis.call('HEXISTS', KEYS[1], msgId) == 1 then
                local existingScore = redis.call('ZSCORE', KEYS[2], msgId)
                local scoreNum = existingScore and tonumber(existingScore) or redis.call('INCR', KEYS[3])
                for i = 5, targetEnd do
                    redis.call('ZADD', KEYS[i], 'NX', scoreNum, msgId)
                    redis.call('EXPIRE', KEYS[i], ttl)
                end
                redis.call('EXPIRE', KEYS[1], ttl)
                redis.call('EXPIRE', KEYS[2], ttl)
                redis.call('EXPIRE', KEYS[3], ttl)
                redis.call('EXPIRE', KEYS[4], ttl)
                return 0
            end

            local evicted = 0
            while redis.call('HLEN', KEYS[1]) >= maxSize do
                local oldest = redis.call('ZPOPMIN', KEYS[2], 1)
                if oldest == nil or #oldest == 0 then
                    break
                end
                local oldMsgId = oldest[1]
                redis.call('HDEL', KEYS[1], oldMsgId)
                for i = 5, #KEYS do
                    redis.call('ZREM', KEYS[i], oldMsgId)
                end
                evicted = evicted + 1
            end

            local seq = redis.call('INCR', KEYS[3])
            redis.call('HSET', KEYS[1], msgId, payload)
            redis.call('ZADD', KEYS[2], seq, msgId)
            for i = 5, targetEnd do
                redis.call('ZADD', KEYS[i], seq, msgId)
                redis.call('EXPIRE', KEYS[i], ttl)
            end
            redis.call('EXPIRE', KEYS[1], ttl)
            redis.call('EXPIRE', KEYS[2], ttl)
            redis.call('EXPIRE', KEYS[3], ttl)
            redis.call('EXPIRE', KEYS[4], ttl)
            return evicted
            """,
            Long.class
    );

    /**
     * Script Lua atomico para DELIVERY_ACK e garbage collection:
     * KEYS[1] = msgs Hash
     * KEYS[2] = order ZSET
     * KEYS[3] = pending ZSET do dispositivo que enviou o DELIVERY_ACK
     * KEYS[4 .. #KEYS] = pending ZSETs dos demais dispositivos irmaos
     * ARGV[1] = messageId
     * Retorna:
     *   2 = removido do dispositivo E coletado do Hash (zero dispositivos restantes)
     *   1 = removido deste dispositivo, mas ainda pendente para outro dispositivo irmao
     *   0 = messageId ja nao estava pendente para este dispositivo
     */
    private static final DefaultRedisScript<Long> ACK_DELIVERY_SCRIPT = new DefaultRedisScript<>(
            """
            local msgId = ARGV[1]
            local removed = redis.call('ZREM', KEYS[3], msgId)
            local stillPending = 0
            for i = 4, #KEYS do
                if redis.call('ZSCORE', KEYS[i], msgId) ~= false then
                    stillPending = 1
                    break
                end
            end
            if stillPending == 0 then
                local deletedHash = redis.call('HDEL', KEYS[1], msgId)
                redis.call('ZREM', KEYS[2], msgId)
                if removed > 0 or deletedHash > 0 then
                    return 2
                end
                return 0
            end
            return removed
            """,
            Long.class
    );

    private static final class InMemoryUserQueue {
        final LinkedHashMap<String, byte[]> messagesById = new LinkedHashMap<>();
        final Map<String, LinkedHashSet<String>> pendingByDevice = new LinkedHashMap<>();
    }

    private final StringRedisTemplate redisTemplate;
    private final Counter enqueuedCounter;
    private final Counter evictedCounter;
    private final Map<String, InMemoryUserQueue> fallbackStore = new ConcurrentHashMap<>();

    public OfflineMessageQueueService() {
        this(null, new SimpleMeterRegistry());
    }

    public OfflineMessageQueueService(StringRedisTemplate redisTemplate) {
        this(redisTemplate, new SimpleMeterRegistry());
    }

    @Autowired
    public OfflineMessageQueueService(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            @Autowired(required = false) MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        MeterRegistry registry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
        this.enqueuedCounter = Counter.builder("ispchat_offline_enqueued_total")
                .description("Total de envelopes armazenados na fila offline do Home Node")
                .register(registry);
        this.evictedCounter = Counter.builder("ispchat_offline_evicted_total")
                .description("Total de envelopes antigos despejados pelo limite ring-buffer de 1.000 mensagens")
                .register(registry);
    }

    /**
     * Enfileira um ServerEnvelope para o dispositivo padrao (deviceId == receiverUuid).
     */
    public boolean enqueue(String receiverUuid, ServerEnvelope envelope) {
        if (receiverUuid == null || receiverUuid.isBlank()) {
            return false;
        }
        return enqueueForDevices(receiverUuid, envelope, List.of(receiverUuid));
    }

    /**
     * Enfileira um ServerEnvelope para o conjunto de device_id offline informados (ring-buffer de 1.000).
     */
    public boolean enqueueForDevices(
            String receiverUuid,
            ServerEnvelope envelope,
            Collection<String> targetDeviceIds) {
        if (receiverUuid == null || receiverUuid.isBlank() || envelope == null || envelope.getMessageId().isBlank()) {
            return false;
        }
        String normalizedUuid = receiverUuid.trim().toLowerCase(Locale.ROOT);
        Set<String> normalizedTargets = new LinkedHashSet<>();
        if (targetDeviceIds != null) {
            for (String dev : targetDeviceIds) {
                if (dev != null && !dev.isBlank()) {
                    normalizedTargets.add(dev.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        if (normalizedTargets.isEmpty()) {
            normalizedTargets.add(normalizedUuid);
        }

        String msgId = envelope.getMessageId().trim().toLowerCase(Locale.ROOT);
        byte[] serialized = envelope.toByteArray();

        if (redisTemplate != null) {
            try {
                String devsKey = REDIS_DEVS_KEY_PREFIX + normalizedUuid;
                redisTemplate.opsForSet().add(devsKey, normalizedTargets.toArray(String[]::new));
                Set<String> allKnownDevices = redisTemplate.opsForSet().members(devsKey);

                List<String> keys = new ArrayList<>();
                keys.add(REDIS_MSGS_KEY_PREFIX + normalizedUuid);
                keys.add(REDIS_ORDER_KEY_PREFIX + normalizedUuid);
                keys.add(REDIS_SEQ_KEY_PREFIX + normalizedUuid);
                keys.add(devsKey);
                for (String targetDev : normalizedTargets) {
                    keys.add(REDIS_PENDING_KEY_PREFIX + normalizedUuid + ":" + targetDev);
                }
                if (allKnownDevices != null) {
                    for (String otherDev : allKnownDevices) {
                        String normOther = otherDev.trim().toLowerCase(Locale.ROOT);
                        if (!normalizedTargets.contains(normOther)) {
                            keys.add(REDIS_PENDING_KEY_PREFIX + normalizedUuid + ":" + normOther);
                        }
                    }
                }

                String base64Payload = Base64.getEncoder().encodeToString(serialized);
                Long evicted = redisTemplate.execute(
                        ENQUEUE_RING_BUFFER_SCRIPT,
                        keys,
                        msgId,
                        base64Payload,
                        String.valueOf(MAX_QUEUE_SIZE_PER_USER),
                        String.valueOf(TTL_SECONDS),
                        String.valueOf(normalizedTargets.size()));

                if (evicted != null) {
                    enqueuedCounter.increment();
                    if (evicted > 0) {
                        evictedCounter.increment(evicted);
                    }
                    return true;
                }
            } catch (Exception e) {
                log.debug("Redis indisponivel ao enfileirar ring-buffer para {}, usando memoria local: {}",
                        normalizedUuid, e.getMessage());
            }
        }

        InMemoryUserQueue userQueue = fallbackStore.computeIfAbsent(normalizedUuid, k -> new InMemoryUserQueue());
        int evictedCount = 0;
        synchronized (userQueue) {
            if (!userQueue.messagesById.containsKey(msgId)) {
                while (userQueue.messagesById.size() >= MAX_QUEUE_SIZE_PER_USER) {
                    Iterator<String> it = userQueue.messagesById.keySet().iterator();
                    if (!it.hasNext()) {
                        break;
                    }
                    String oldestId = it.next();
                    it.remove();
                    for (LinkedHashSet<String> devSet : userQueue.pendingByDevice.values()) {
                        devSet.remove(oldestId);
                    }
                    evictedCount++;
                }
                userQueue.messagesById.put(msgId, serialized);
            }
            for (String targetDev : normalizedTargets) {
                userQueue.pendingByDevice
                        .computeIfAbsent(targetDev, k -> new LinkedHashSet<>())
                        .add(msgId);
            }
        }
        enqueuedCounter.increment();
        if (evictedCount > 0) {
            evictedCounter.increment(evictedCount);
        }
        return true;
    }

    /**
     * Retorna em ordem FIFO as mensagens pendentes de DELIVERY_ACK para o deviceId sem apaga-las.
     */
    public List<ServerEnvelope> peekPendingMessages(String receiverUuid, String deviceId) {
        if (receiverUuid == null || receiverUuid.isBlank()) {
            return List.of();
        }
        String normalizedUuid = receiverUuid.trim().toLowerCase(Locale.ROOT);
        String normalizedDeviceId = (deviceId == null || deviceId.isBlank())
                ? normalizedUuid
                : deviceId.trim().toLowerCase(Locale.ROOT);

        if (redisTemplate != null) {
            try {
                String pendingKey = REDIS_PENDING_KEY_PREFIX + normalizedUuid + ":" + normalizedDeviceId;
                Set<String> pendingIds = redisTemplate.opsForZSet().range(pendingKey, 0, -1);
                if (pendingIds != null && !pendingIds.isEmpty()) {
                    List<Object> hashKeys = new ArrayList<>(pendingIds);
                    List<Object> rawValues = redisTemplate.opsForHash()
                            .multiGet(REDIS_MSGS_KEY_PREFIX + normalizedUuid, hashKeys);
                    List<ServerEnvelope> result = new ArrayList<>(pendingIds.size());
                    if (rawValues != null) {
                        int idx = 0;
                        for (String pendingId : pendingIds) {
                            Object val = idx < rawValues.size() ? rawValues.get(idx) : null;
                            idx++;
                            if (val instanceof String b64 && !b64.isBlank()) {
                                result.add(ServerEnvelope.parseFrom(Base64.getDecoder().decode(b64)));
                            } else {
                                redisTemplate.opsForZSet().remove(pendingKey, pendingId);
                            }
                        }
                    }
                    return result;
                }
                // Verifica se havia apenas em memoria local (ex: Redis offline no teste)
                if (!fallbackStore.containsKey(normalizedUuid)) {
                    return List.of();
                }
            } catch (Exception e) {
                log.debug("Redis indisponivel ao consultar fila pendente de {} ({}), usando memoria local: {}",
                        normalizedUuid, normalizedDeviceId, e.getMessage());
            }
        }

        InMemoryUserQueue userQueue = fallbackStore.get(normalizedUuid);
        if (userQueue == null) {
            return List.of();
        }
        List<byte[]> rawPayloads = new ArrayList<>();
        synchronized (userQueue) {
            LinkedHashSet<String> pendingIds = userQueue.pendingByDevice.get(normalizedDeviceId);
            if (pendingIds == null || pendingIds.isEmpty()) {
                return List.of();
            }
            Iterator<String> it = pendingIds.iterator();
            while (it.hasNext()) {
                String id = it.next();
                byte[] raw = userQueue.messagesById.get(id);
                if (raw != null) {
                    rawPayloads.add(raw);
                } else {
                    it.remove();
                }
            }
        }

        List<ServerEnvelope> envelopes = new ArrayList<>(rawPayloads.size());
        for (byte[] raw : rawPayloads) {
            try {
                envelopes.add(ServerEnvelope.parseFrom(raw));
            } catch (InvalidProtocolBufferException e) {
                log.warn("Mensagem corrompida ignorada na fila offline de {}", normalizedUuid);
            }
        }
        return envelopes;
    }

    /**
     * Confirma o recebimento (DELIVERY_ACK) de messageId por um deviceId especifico,
     * removendo o payload do Hash assim que todos os dispositivos alvo tiverem confirmado.
     */
    public boolean acknowledgeDelivery(String receiverUuid, String deviceId, String messageId) {
        if (receiverUuid == null || receiverUuid.isBlank() || messageId == null || messageId.isBlank()) {
            return false;
        }
        String normalizedUuid = receiverUuid.trim().toLowerCase(Locale.ROOT);
        String normalizedDeviceId = (deviceId == null || deviceId.isBlank())
                ? normalizedUuid
                : deviceId.trim().toLowerCase(Locale.ROOT);
        String normalizedMsgId = messageId.trim().toLowerCase(Locale.ROOT);

        boolean redisHandled = false;
        boolean ackedInRedis = false;
        if (redisTemplate != null) {
            try {
                String devsKey = REDIS_DEVS_KEY_PREFIX + normalizedUuid;
                Set<String> allKnownDevices = redisTemplate.opsForSet().members(devsKey);
                List<String> keys = new ArrayList<>();
                keys.add(REDIS_MSGS_KEY_PREFIX + normalizedUuid);
                keys.add(REDIS_ORDER_KEY_PREFIX + normalizedUuid);
                keys.add(REDIS_PENDING_KEY_PREFIX + normalizedUuid + ":" + normalizedDeviceId);
                if (allKnownDevices != null) {
                    for (String dev : allKnownDevices) {
                        String normOther = dev.trim().toLowerCase(Locale.ROOT);
                        if (!normOther.equals(normalizedDeviceId)) {
                            keys.add(REDIS_PENDING_KEY_PREFIX + normalizedUuid + ":" + normOther);
                        }
                    }
                }
                Long status = redisTemplate.execute(ACK_DELIVERY_SCRIPT, keys, normalizedMsgId);
                if (status != null) {
                    redisHandled = true;
                    ackedInRedis = status > 0L;
                }
            } catch (Exception e) {
                log.debug("Redis indisponivel ao processar DELIVERY_ACK ({}, {}), aplicando em memoria: {}",
                        normalizedUuid, normalizedMsgId, e.getMessage());
            }
        }

        InMemoryUserQueue userQueue = fallbackStore.get(normalizedUuid);
        boolean ackedInMemory = false;
        if (userQueue != null) {
            synchronized (userQueue) {
                LinkedHashSet<String> targetSet = userQueue.pendingByDevice.get(normalizedDeviceId);
                if (targetSet != null && targetSet.remove(normalizedMsgId)) {
                    ackedInMemory = true;
                }
                boolean stillReferenced = false;
                for (Map.Entry<String, LinkedHashSet<String>> entry : userQueue.pendingByDevice.entrySet()) {
                    if (!entry.getKey().equals(normalizedDeviceId) && entry.getValue().contains(normalizedMsgId)) {
                        stillReferenced = true;
                        break;
                    }
                }
                if (!stillReferenced) {
                    if (userQueue.messagesById.remove(normalizedMsgId) != null) {
                        ackedInMemory = true;
                    }
                }
                userQueue.pendingByDevice.entrySet().removeIf(e -> e.getValue().isEmpty());
                if (userQueue.messagesById.isEmpty() && userQueue.pendingByDevice.isEmpty()) {
                    fallbackStore.remove(normalizedUuid, userQueue);
                }
            }
        }

        return redisHandled ? (ackedInRedis || ackedInMemory) : ackedInMemory;
    }

    /**
     * Retorna a quantidade total de mensagens unicas atualmente armazenadas no Hash de um UUIDv7.
     */
    public int getStoredMessageCount(String receiverUuid) {
        if (receiverUuid == null || receiverUuid.isBlank()) {
            return 0;
        }
        String normalizedUuid = receiverUuid.trim().toLowerCase(Locale.ROOT);
        if (redisTemplate != null) {
            try {
                Long size = redisTemplate.opsForHash().size(REDIS_MSGS_KEY_PREFIX + normalizedUuid);
                if (size != null && (size > 0 || !fallbackStore.containsKey(normalizedUuid))) {
                    return size.intValue();
                }
            } catch (Exception ignored) {
            }
        }
        InMemoryUserQueue userQueue = fallbackStore.get(normalizedUuid);
        if (userQueue == null) {
            return 0;
        }
        synchronized (userQueue) {
            return userQueue.messagesById.size();
        }
    }

    /**
     * Metodo legado V1 mantido para compatibilidade: le e confirma todas as mensagens do dispositivo raiz.
     */
    public List<ServerEnvelope> drainQueue(String receiverUuid) {
        List<ServerEnvelope> pending = peekPendingMessages(receiverUuid, receiverUuid);
        for (ServerEnvelope env : pending) {
            acknowledgeDelivery(receiverUuid, receiverUuid, env.getMessageId());
        }
        return pending;
    }
}
