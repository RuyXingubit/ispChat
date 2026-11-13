package br.dev.ispchat.node.governance;

import br.dev.ispchat.grpc.PrincipalGovernanceServiceGrpc;
import br.dev.ispchat.grpc.RevocationEvent;
import br.dev.ispchat.grpc.StreamRevocationsRequest;
import br.dev.ispchat.node.dht.DhtStoreService;
import br.dev.ispchat.node.grpc.GrpcChannelPool;
import br.dev.ispchat.node.messaging.LocalSessionRegistry;
import br.dev.ispchat.node.security.CryptoService;
import br.dev.ispchat.node.security.TierRateLimiterService;
import io.grpc.ManagedChannel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Worker assincrono de sincronizacao do feed monotono de revogacoes (StreamRevocations)
 * a partir do servidor Principal sobre mTLS.
 *
 * Mantem o cursor last_revocation_seq no Redis (ispchat:revoked:last_seq), grava chaves locais
 * de bloqueio imediato, publica IdentityReplacementNotice em caso de CPF Re-Bind e derruba
 * instantaneamente streams revogadas no LocalSessionRegistry com REVOCATION_NOTICE.
 */
@Component
@ConditionalOnProperty(name = "ispchat.governance.revocation-sync-enabled", havingValue = "true", matchIfMissing = true)
public class RevocationSyncWorker {

    private static final Logger log = LoggerFactory.getLogger(RevocationSyncWorker.class);
    public static final String REDIS_LAST_REVOCATION_SEQ_KEY = "ispchat:revoked:last_seq";

    private final GrpcChannelPool channelPool;
    private final CryptoService cryptoService;
    private final TierRateLimiterService tierRateLimiterService;
    private final DhtStoreService dhtStoreService;
    private final LocalSessionRegistry localSessionRegistry;
    private final StringRedisTemplate redisTemplate;
    private final String principalAddress;
    private final String localPublicAddress;
    private final Counter revocationsAppliedCounter;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong memoryLastSeq = new AtomicLong(0L);
    private Thread workerThread;

    public RevocationSyncWorker(
            GrpcChannelPool channelPool,
            CryptoService cryptoService,
            TierRateLimiterService tierRateLimiterService,
            DhtStoreService dhtStoreService,
            LocalSessionRegistry localSessionRegistry) {
        this(
                channelPool,
                cryptoService,
                tierRateLimiterService,
                dhtStoreService,
                localSessionRegistry,
                null,
                new SimpleMeterRegistry(),
                "",
                "");
    }

    @Autowired
    public RevocationSyncWorker(
            GrpcChannelPool channelPool,
            CryptoService cryptoService,
            TierRateLimiterService tierRateLimiterService,
            DhtStoreService dhtStoreService,
            LocalSessionRegistry localSessionRegistry,
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            @Autowired(required = false) MeterRegistry meterRegistry,
            @Value("${ispchat.principal.address:}") String principalAddress,
            @Value("${ispchat.node.public-address:}") String localPublicAddress) {
        this.channelPool = channelPool;
        this.cryptoService = cryptoService;
        this.tierRateLimiterService = tierRateLimiterService;
        this.dhtStoreService = dhtStoreService;
        this.localSessionRegistry = localSessionRegistry;
        this.redisTemplate = redisTemplate;
        this.principalAddress = principalAddress != null ? principalAddress.trim() : "";
        this.localPublicAddress = localPublicAddress != null ? localPublicAddress.trim() : "";
        MeterRegistry registry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
        this.revocationsAppliedCounter = Counter.builder("ispchat_revocations_applied_total")
                .description("Total de eventos de revogacao aplicados neste No ISP")
                .register(registry);
    }

    @PostConstruct
    public void start() {
        if (principalAddress.isBlank() || principalAddress.equalsIgnoreCase(localPublicAddress)) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        loadCursorFromRedis();
        workerThread = Thread.ofVirtual().name("ispchat-revocation-sync").start(this::syncLoop);
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }

    /**
     * Aplica deterministicamente um RevocationEvent recebido do feed do Principal.
     */
    public boolean applyRevocationEvent(RevocationEvent event) {
        if (event == null || event.getRevocationId().isBlank() || event.getTargetUuid().isBlank()) {
            return false;
        }
        if (cryptoService.hasTrustedPrincipalKeys() && !cryptoService.verifyRevocationEvent(event)) {
            log.warn("Descartado RevocationEvent seq={} por assinatura Ed25519 invalida do Principal",
                    event.getRevocationSeq());
            return false;
        }

        String targetUuid = event.getTargetUuid();
        String revokedDeviceId = event.getRevokedDeviceId();

        if (!revokedDeviceId.isBlank()) {
            tierRateLimiterService.markDeviceRevoked(targetUuid, revokedDeviceId, event.getReason());
            dhtStoreService.evictDeviceSession(targetUuid, revokedDeviceId);
            localSessionRegistry.revokeAndDisconnect(targetUuid, revokedDeviceId, event);
        } else {
            tierRateLimiterService.markAccountRevoked(targetUuid, event.getReason());
            dhtStoreService.evictAllSessions(targetUuid);
            if (event.hasReplacementNotice() && !event.getReplacementNotice().getRevocationId().isBlank()) {
                dhtStoreService.storeReplacementNotice(event.getReplacementNotice());
            }
            localSessionRegistry.revokeAndDisconnect(targetUuid, "", event);
        }

        advanceCursor(event.getRevocationSeq());
        revocationsAppliedCounter.increment();
        return true;
    }

    public long getLastAppliedSeq() {
        loadCursorFromRedis();
        return memoryLastSeq.get();
    }

    private void syncLoop() {
        long backoffMillis = 1_000L;
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                long sinceSeq = getLastAppliedSeq();
                ManagedChannel channel = channelPool.getChannel(principalAddress);
                PrincipalGovernanceServiceGrpc.PrincipalGovernanceServiceBlockingStub stub =
                        PrincipalGovernanceServiceGrpc.newBlockingStub(channel);

                Iterator<RevocationEvent> stream = stub.streamRevocations(
                        StreamRevocationsRequest.newBuilder()
                                .setSinceRevocationSeq(sinceSeq)
                                .build());

                backoffMillis = 1_000L;
                while (running.get() && stream.hasNext()) {
                    RevocationEvent event = stream.next();
                    applyRevocationEvent(event);
                }
            } catch (Exception e) {
                if (!running.get()) {
                    break;
                }
                log.debug("StreamRevocations desconectado de {} (retentando em {} ms): {}",
                        principalAddress, backoffMillis, e.getMessage());
                try {
                    Thread.sleep(backoffMillis);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                backoffMillis = Math.min(30_000L, backoffMillis * 2L);
            }
        }
    }

    private void loadCursorFromRedis() {
        if (redisTemplate == null) {
            return;
        }
        try {
            String raw = redisTemplate.opsForValue().get(REDIS_LAST_REVOCATION_SEQ_KEY);
            if (raw != null && !raw.isBlank()) {
                long parsed = Long.parseLong(raw.trim());
                memoryLastSeq.updateAndGet(cur -> Math.max(cur, parsed));
            }
        } catch (Exception e) {
            log.debug("Redis indisponivel ao ler cursor de revogacoes: {}", e.getMessage());
        }
    }

    private void advanceCursor(long newSeq) {
        if (newSeq <= 0) {
            return;
        }
        long updated = memoryLastSeq.updateAndGet(cur -> Math.max(cur, newSeq));
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(REDIS_LAST_REVOCATION_SEQ_KEY, String.valueOf(updated));
            } catch (Exception e) {
                log.debug("Redis indisponivel ao persistir cursor de revogacoes ({}): {}", updated, e.getMessage());
            }
        }
    }
}
