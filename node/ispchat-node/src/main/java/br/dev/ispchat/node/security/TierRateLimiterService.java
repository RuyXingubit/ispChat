package br.dev.ispchat.node.security;

import br.dev.ispchat.grpc.DeviceDelegationCert;
import br.dev.ispchat.grpc.DeviceRole;
import br.dev.ispchat.grpc.DocumentType;
import br.dev.ispchat.grpc.MessageType;
import br.dev.ispchat.grpc.VerificationCredential;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforcer Dual-Tier de capacidades criptograficas, prefixos de rede (/32 IPv4 e /64 IPv6),
 * rate limiting via scripts Lua atomicos no Redis e verificacao local de revogacoes.
 */
@Service
public class TierRateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(TierRateLimiterService.class);

    public static final String REVOKED_UUID_KEY_PREFIX = "ispchat:revoked:uuid:";
    public static final String REVOKED_DEVICE_KEY_PREFIX = "ispchat:revoked:device:";
    public static final String RL_HANDSHAKE_PREFIX = "ispchat:rl:hs:";
    public static final String RL_UNVERIFIED_REG_PREFIX = "ispchat:rl:unvreg:";
    public static final String RL_MSG_PREFIX = "ispchat:rl:msg:";
    public static final String RL_SIGNAL_PREFIX = "ispchat:rl:sig:";
    public static final String RL_PEERS_24H_PREFIX = "ispchat:rl:peers24h:";
    public static final String RL_TURN_PREFIX = "ispchat:rl:turn:";

    public static final int DEFAULT_PREFIX_HANDSHAKES_PER_MIN = 30;
    public static final int DEFAULT_PREFIX_UNVERIFIED_REG_PER_HOUR = 10;
    public static final int DEFAULT_UNVERIFIED_MSG_PER_MIN = 10;
    public static final int DEFAULT_VERIFIED_CPF_MSG_PER_MIN = 120;
    public static final int DEFAULT_VERIFIED_CNPJ_MSG_PER_MIN = 600;
    public static final int DEFAULT_UNVERIFIED_NEW_PEERS_PER_24H = 20;
    public static final int DEFAULT_UNVERIFIED_TURN_PER_HOUR = 5;
    public static final int DEFAULT_VERIFIED_TURN_PER_HOUR = 60;

    private static final long ONE_MINUTE_MS = 60_000L;
    private static final long ONE_HOUR_MS = 3_600_000L;
    private static final long TWENTY_FOUR_HOURS_MS = 86_400_000L;
    private static final Duration REVOCATION_TTL = Duration.ofDays(100);

    /**
     * Script Lua de janela deslizante em ZSET:
     * KEYS[1] = chave ZSET
     * ARGV[1] = nowMs, ARGV[2] = windowMs, ARGV[3] = limit, ARGV[4] = uniqueMember
     * Retorna 1 se permitido, 0 se excedeu limite.
     */
    private static final DefaultRedisScript<Long> SLIDING_WINDOW_SCRIPT = new DefaultRedisScript<>(
            """
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - window)
            local count = redis.call('ZCARD', KEYS[1])
            if count >= limit then
                return 0
            end
            redis.call('ZADD', KEYS[1], now, ARGV[4])
            redis.call('PEXPIRE', KEYS[1], window)
            return 1
            """,
            Long.class
    );

    /**
     * Script Lua para limite movel de 24h de novos destinatarios distintos:
     * KEYS[1] = chave ZSET (member = receiverUuid, score = lastContactMs)
     * ARGV[1] = nowMs, ARGV[2] = windowMs (86400000), ARGV[3] = limit (20), ARGV[4] = receiverUuid
     * Retorna 1 se ja existia ou se havia vaga para novo destinatario; 0 se bloqueado.
     */
    private static final DefaultRedisScript<Long> DISTINCT_PEERS_24H_SCRIPT = new DefaultRedisScript<>(
            """
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local peer = ARGV[4]
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - window)
            local existing = redis.call('ZSCORE', KEYS[1], peer)
            if existing ~= false then
                redis.call('ZADD', KEYS[1], now, peer)
                redis.call('PEXPIRE', KEYS[1], window)
                return 1
            end
            local count = redis.call('ZCARD', KEYS[1])
            if count >= limit then
                return 0
            end
            redis.call('ZADD', KEYS[1], now, peer)
            redis.call('PEXPIRE', KEYS[1], window)
            return 1
            """,
            Long.class
    );

    public enum AccountTier {
        UNVERIFIED(1),
        VERIFIED_CPF(5),
        VERIFIED_CNPJ(10);

        private final int maxActiveDevices;

        AccountTier(int maxActiveDevices) {
            this.maxActiveDevices = maxActiveDevices;
        }

        public int maxActiveDevices() {
            return maxActiveDevices;
        }

        public boolean isVerified() {
            return this != UNVERIFIED;
        }
    }

    public record ValidatedSessionIdentity(
            String userUuid,
            String deviceId,
            AccountTier tier,
            DeviceRole role,
            byte[] rootEd25519Pub,
            byte[] deviceEd25519Pub) {
    }

    private final CryptoService cryptoService;
    private final StringRedisTemplate redisTemplate;
    private final Clock clock;
    private final int unverifiedMsgPerMin;
    private final int verifiedCpfMsgPerMin;
    private final int verifiedCnpjMsgPerMin;
    private final int unverifiedNewPeersPer24h;
    private final Counter rateLimitDropsCounter;

    // Estruturas locais de contingencia para execucao isolada em testes unitarios sem Redis ativo
    private final Map<String, String> fallbackRevokedAccounts = new ConcurrentHashMap<>();
    private final Map<String, String> fallbackRevokedDevices = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> fallbackSlidingWindows = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Long>> fallbackDistinctPeers24h = new ConcurrentHashMap<>();

    public TierRateLimiterService(CryptoService cryptoService) {
        this(
                cryptoService,
                null,
                new SimpleMeterRegistry(),
                DEFAULT_UNVERIFIED_MSG_PER_MIN,
                DEFAULT_VERIFIED_CPF_MSG_PER_MIN,
                DEFAULT_VERIFIED_CNPJ_MSG_PER_MIN,
                DEFAULT_UNVERIFIED_NEW_PEERS_PER_24H);
    }

    @Autowired
    public TierRateLimiterService(
            CryptoService cryptoService,
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            @Autowired(required = false) MeterRegistry meterRegistry,
            @Value("${ispchat.ratelimit.unverified-msg-per-min:10}") int unverifiedMsgPerMin,
            @Value("${ispchat.ratelimit.verified-cpf-msg-per-min:120}") int verifiedCpfMsgPerMin,
            @Value("${ispchat.ratelimit.verified-cnpj-msg-per-min:600}") int verifiedCnpjMsgPerMin,
            @Value("${ispchat.ratelimit.unverified-new-peers-24h:20}") int unverifiedNewPeersPer24h) {
        this.cryptoService = Objects.requireNonNull(cryptoService, "cryptoService nao pode ser nulo");
        this.redisTemplate = redisTemplate;
        this.clock = cryptoService.getClock();
        this.unverifiedMsgPerMin = unverifiedMsgPerMin;
        this.verifiedCpfMsgPerMin = verifiedCpfMsgPerMin;
        this.verifiedCnpjMsgPerMin = verifiedCnpjMsgPerMin;
        this.unverifiedNewPeersPer24h = unverifiedNewPeersPer24h;
        MeterRegistry registry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
        this.rateLimitDropsCounter = Counter.builder("ispchat_rate_limit_drops_total")
                .description("Total de requisicoes ou mensagens rejeitadas pelos limites Dual-Tier")
                .register(registry);
    }

    /**
     * Valida capacidades criptograficas Dual-Tier, revogacoes e certificados de delegacao.
     * Lanca StatusRuntimeException (PERMISSION_DENIED ou UNAUTHENTICATED) em caso de violacao.
     */
    public ValidatedSessionIdentity validateSessionCredentials(
            String rawUserUuid,
            String rawDeviceId,
            byte[] callerEd25519Pub,
            DeviceDelegationCert delegationCert,
            VerificationCredential verificationCredential) {

        if (rawUserUuid == null || rawUserUuid.isBlank()) {
            throw Status.UNAUTHENTICATED.withDescription("user_uuid ausente").asRuntimeException();
        }
        String userUuid = rawUserUuid.trim().toLowerCase(Locale.ROOT);
        String deviceId = (rawDeviceId == null || rawDeviceId.isBlank())
                ? userUuid
                : rawDeviceId.trim().toLowerCase(Locale.ROOT);

        boolean hasCred = verificationCredential != null && !verificationCredential.getCredentialId().isBlank();
        boolean hasDelCert = delegationCert != null && !delegationCert.getCertId().isBlank();
        boolean isSecondaryDevice = !userUuid.equals(deviceId);

        if (isAccountRevoked(userUuid)) {
            throw Status.PERMISSION_DENIED.withDescription("Conta revogada na governanca ISP Chat").asRuntimeException();
        }
        if (isDeviceRevoked(deviceId)) {
            throw Status.PERMISSION_DENIED.withDescription("Dispositivo delegado revogado pelo titular").asRuntimeException();
        }

        if (!hasCred) {
            if (hasDelCert || isSecondaryDevice) {
                throw Status.PERMISSION_DENIED
                        .withDescription("Contas nao verificadas nao possuem permissao para delegar dispositivos secundarios")
                        .asRuntimeException();
            }
            if (!cryptoService.verifyIdentityBinding(userUuid, callerEd25519Pub)) {
                throw Status.UNAUTHENTICATED
                        .withDescription("Chave publica Ed25519 incompativel com o UUIDv7 informado")
                        .asRuntimeException();
            }
            return new ValidatedSessionIdentity(
                    userUuid,
                    userUuid,
                    AccountTier.UNVERIFIED,
                    DeviceRole.MOBILE_ROOT,
                    callerEd25519Pub.clone(),
                    callerEd25519Pub.clone());
        }

        // Fluxo Verificado (CPF ou CNPJ)
        if (!cryptoService.verifyVerificationCredential(verificationCredential)
                || !verificationCredential.getUserUuid().trim().equalsIgnoreCase(userUuid)) {
            throw Status.PERMISSION_DENIED
                    .withDescription("VerificationCredential invalida, expirada ou pertencente a outro UUIDv7")
                    .asRuntimeException();
        }

        AccountTier tier = verificationCredential.getDocType() == DocumentType.CNPJ
                ? AccountTier.VERIFIED_CNPJ
                : AccountTier.VERIFIED_CPF;
        byte[] rootEdPub = verificationCredential.getRootEd25519Pub().toByteArray();

        if (isSecondaryDevice || hasDelCert) {
            if (!hasDelCert) {
                throw Status.PERMISSION_DENIED
                        .withDescription("Dispositivos secundarios exigem DeviceDelegationCert assinado pela raiz")
                        .asRuntimeException();
            }
            if (!cryptoService.verifyDeviceDelegationCert(delegationCert, rootEdPub)
                    || !delegationCert.getUserUuid().trim().equalsIgnoreCase(userUuid)
                    || !delegationCert.getDeviceId().trim().equalsIgnoreCase(deviceId)
                    || !Arrays.equals(delegationCert.getDeviceEd25519Pub().toByteArray(), callerEd25519Pub)) {
                throw Status.PERMISSION_DENIED
                        .withDescription("DeviceDelegationCert invalido, expirado ou incompativel com a chave do dispositivo")
                        .asRuntimeException();
            }
            if (tier == AccountTier.VERIFIED_CPF && delegationCert.getRole() == DeviceRole.ERP_API) {
                throw Status.PERMISSION_DENIED
                        .withDescription("Sub-chaves ERP_API sao exclusivas para contas corporativas CNPJ verificadas")
                        .asRuntimeException();
            }
            return new ValidatedSessionIdentity(
                    userUuid,
                    deviceId,
                    tier,
                    delegationCert.getRole(),
                    rootEdPub,
                    callerEd25519Pub.clone());
        }

        if (!Arrays.equals(rootEdPub, callerEd25519Pub)
                || !cryptoService.verifyIdentityBinding(userUuid, callerEd25519Pub)) {
            throw Status.UNAUTHENTICATED
                    .withDescription("Chave publica do dispositivo raiz nao corresponde a VerificationCredential")
                    .asRuntimeException();
        }

        return new ValidatedSessionIdentity(
                userUuid,
                userUuid,
                tier,
                DeviceRole.MOBILE_ROOT,
                rootEdPub,
                callerEd25519Pub.clone());
    }

    /**
     * Normaliza um SocketAddress remoto em prefixo /32 (IPv4) ou /64 (IPv6).
     */
    public static String normalizeNetworkPrefix(SocketAddress remoteAddress) {
        if (remoteAddress instanceof InetSocketAddress inetSock && inetSock.getAddress() != null) {
            return normalizeInetAddressPrefix(inetSock.getAddress());
        }
        return null;
    }

    /**
     * Normaliza uma string literal de IP (IPv4 ou IPv6) em prefixo /32 ou /64.
     */
    public static String normalizeIpPrefix(String ipLiteral) {
        if (ipLiteral == null || ipLiteral.isBlank()) {
            throw new IllegalArgumentException("IP nao pode ser vazio");
        }
        try {
            InetAddress addr = InetAddress.getByName(ipLiteral.trim());
            return normalizeInetAddressPrefix(addr);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Literal IP invalido: " + ipLiteral, e);
        }
    }

    private static String normalizeInetAddressPrefix(InetAddress addr) {
        byte[] raw = addr.getAddress();
        if (raw.length == 4) {
            return "v4:" + (raw[0] & 0xFF) + "." + (raw[1] & 0xFF) + "." + (raw[2] & 0xFF) + "." + (raw[3] & 0xFF) + "/32";
        }
        if (raw.length == 16) {
            // Trata IPv4-mapped IPv6 (::ffff:a.b.c.d) como IPv4 /32
            boolean v4Mapped = true;
            for (int i = 0; i < 10; i++) {
                if (raw[i] != 0) {
                    v4Mapped = false;
                    break;
                }
            }
            if (v4Mapped && raw[10] == (byte) 0xFF && raw[11] == (byte) 0xFF) {
                return "v4:" + (raw[12] & 0xFF) + "." + (raw[13] & 0xFF) + "." + (raw[14] & 0xFF) + "." + (raw[15] & 0xFF) + "/32";
            }
            return "v6:" + HexFormat.of().formatHex(raw, 0, 8) + "/64";
        }
        throw new IllegalArgumentException("Tamanho de endereco IP inesperado: " + raw.length);
    }

    public boolean checkHandshakePrefixAllowed(SocketAddress remoteAddress) {
        String prefix = normalizeNetworkPrefix(remoteAddress);
        if (prefix == null) {
            return true;
        }
        return checkHandshakePrefixAllowed(prefix);
    }

    public boolean checkHandshakePrefixAllowed(String normalizedPrefix) {
        if (normalizedPrefix == null || normalizedPrefix.isBlank()) {
            return true;
        }
        boolean allowed = consumeSlidingWindow(
                RL_HANDSHAKE_PREFIX + normalizedPrefix,
                ONE_MINUTE_MS,
                DEFAULT_PREFIX_HANDSHAKES_PER_MIN);
        if (!allowed) {
            rateLimitDropsCounter.increment();
        }
        return allowed;
    }

    public boolean checkUnverifiedRegistrationPrefixAllowed(SocketAddress remoteAddress) {
        String prefix = normalizeNetworkPrefix(remoteAddress);
        if (prefix == null) {
            return true;
        }
        return checkUnverifiedRegistrationPrefixAllowed(prefix);
    }

    public boolean checkUnverifiedRegistrationPrefixAllowed(String normalizedPrefix) {
        if (normalizedPrefix == null || normalizedPrefix.isBlank()) {
            return true;
        }
        boolean allowed = consumeSlidingWindow(
                RL_UNVERIFIED_REG_PREFIX + normalizedPrefix,
                ONE_HOUR_MS,
                DEFAULT_PREFIX_UNVERIFIED_REG_PER_HOUR);
        if (!allowed) {
            rateLimitDropsCounter.increment();
        }
        return allowed;
    }

    /**
     * Verifica limites de envio de mensagens e de novos destinatarios em 24h por conta.
     */
    public boolean checkOutgoingEnvelopeAllowed(
            String senderUuid,
            AccountTier tier,
            MessageType type,
            String receiverUuid) {
        if (senderUuid == null || senderUuid.isBlank() || tier == null || type == null) {
            return false;
        }
        if (type == MessageType.AUTH_HANDSHAKE || type == MessageType.DELIVERY_ACK) {
            return true;
        }

        String normalizedSender = senderUuid.trim().toLowerCase(Locale.ROOT);
        String normalizedReceiver = receiverUuid == null ? "" : receiverUuid.trim().toLowerCase(Locale.ROOT);

        // Para contas nao verificadas, aplica teto movel de 20 destinatarios distintos em 24h antes de consumir cota
        if (tier == AccountTier.UNVERIFIED
                && !normalizedReceiver.isBlank()
                && !normalizedReceiver.equals(normalizedSender)) {
            boolean peerAllowed = checkAndRecordDistinctRecipient24h(normalizedSender, normalizedReceiver);
            if (!peerAllowed) {
                rateLimitDropsCounter.increment();
                return false;
            }
        }

        if (type == MessageType.ICE_CANDIDATE
                || type == MessageType.SDP_ANSWER
                || type == MessageType.CALL_DECLINE) {
            int signalLimit = tier == AccountTier.UNVERIFIED ? 120 : 1200;
            boolean sigAllowed = consumeSlidingWindow(RL_SIGNAL_PREFIX + normalizedSender, ONE_MINUTE_MS, signalLimit);
            if (!sigAllowed) {
                rateLimitDropsCounter.increment();
            }
            return sigAllowed;
        }

        int msgLimit = switch (tier) {
            case UNVERIFIED -> unverifiedMsgPerMin;
            case VERIFIED_CPF -> verifiedCpfMsgPerMin;
            case VERIFIED_CNPJ -> verifiedCnpjMsgPerMin;
        };

        boolean msgAllowed = consumeSlidingWindow(RL_MSG_PREFIX + normalizedSender, ONE_MINUTE_MS, msgLimit);
        if (!msgAllowed) {
            rateLimitDropsCounter.increment();
        }
        return msgAllowed;
    }

    public boolean checkTurnIssuanceAllowed(String userUuid, AccountTier tier) {
        if (userUuid == null || userUuid.isBlank() || tier == null) {
            return false;
        }
        int limit = tier == AccountTier.UNVERIFIED
                ? DEFAULT_UNVERIFIED_TURN_PER_HOUR
                : DEFAULT_VERIFIED_TURN_PER_HOUR;
        boolean allowed = consumeSlidingWindow(
                RL_TURN_PREFIX + userUuid.trim().toLowerCase(Locale.ROOT),
                ONE_HOUR_MS,
                limit);
        if (!allowed) {
            rateLimitDropsCounter.increment();
        }
        return allowed;
    }

    public void markAccountRevoked(String userUuid, String reason) {
        if (userUuid == null || userUuid.isBlank()) {
            return;
        }
        String normalized = userUuid.trim().toLowerCase(Locale.ROOT);
        String safeReason = (reason == null || reason.isBlank()) ? "REVOKED" : reason.trim();
        fallbackRevokedAccounts.put(normalized, safeReason);
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(REVOKED_UUID_KEY_PREFIX + normalized, safeReason, REVOCATION_TTL);
            } catch (Exception e) {
                log.debug("Redis indisponivel ao gravar revogacao de conta {}, mantida em memoria: {}", normalized, e.getMessage());
            }
        }
    }

    public void markDeviceRevoked(String userUuid, String deviceId, String reason) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }
        String normalizedDev = deviceId.trim().toLowerCase(Locale.ROOT);
        String safeReason = (reason == null || reason.isBlank()) ? "DEVICE_REVOKED" : reason.trim();
        fallbackRevokedDevices.put(normalizedDev, safeReason);
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(REVOKED_DEVICE_KEY_PREFIX + normalizedDev, safeReason, REVOCATION_TTL);
            } catch (Exception e) {
                log.debug("Redis indisponivel ao gravar revogacao de dispositivo {}, mantida em memoria: {}", normalizedDev, e.getMessage());
            }
        }
    }

    public boolean isAccountRevoked(String userUuid) {
        if (userUuid == null || userUuid.isBlank()) {
            return false;
        }
        String normalized = userUuid.trim().toLowerCase(Locale.ROOT);
        if (fallbackRevokedAccounts.containsKey(normalized)) {
            return true;
        }
        if (redisTemplate != null) {
            try {
                Boolean exists = redisTemplate.hasKey(REVOKED_UUID_KEY_PREFIX + normalized);
                return Boolean.TRUE.equals(exists);
            } catch (Exception e) {
                log.debug("Redis indisponivel ao consultar revogacao de conta {}: {}", normalized, e.getMessage());
            }
        }
        return false;
    }

    public boolean isDeviceRevoked(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return false;
        }
        String normalized = deviceId.trim().toLowerCase(Locale.ROOT);
        if (fallbackRevokedDevices.containsKey(normalized)) {
            return true;
        }
        if (redisTemplate != null) {
            try {
                Boolean exists = redisTemplate.hasKey(REVOKED_DEVICE_KEY_PREFIX + normalized);
                return Boolean.TRUE.equals(exists);
            } catch (Exception e) {
                log.debug("Redis indisponivel ao consultar revogacao de dispositivo {}: {}", normalized, e.getMessage());
            }
        }
        return false;
    }

    public boolean isRevoked(String userUuid, String deviceId) {
        return isAccountRevoked(userUuid) || isDeviceRevoked(deviceId);
    }

    private boolean consumeSlidingWindow(String redisKey, long windowMs, int limit) {
        long now = clock.millis();
        if (redisTemplate != null) {
            try {
                String member = now + ":" + UUID.randomUUID();
                Long res = redisTemplate.execute(
                        SLIDING_WINDOW_SCRIPT,
                        List.of(redisKey),
                        String.valueOf(now),
                        String.valueOf(windowMs),
                        String.valueOf(limit),
                        member);
                if (res != null) {
                    return res == 1L;
                }
            } catch (Exception e) {
                log.debug("Fallback em memoria para rate limit ({}): {}", redisKey, e.getMessage());
            }
        }

        Deque<Long> timestamps = fallbackSlidingWindows.computeIfAbsent(redisKey, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            long cutoff = now - windowMs;
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= limit) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    private boolean checkAndRecordDistinctRecipient24h(String normalizedSender, String normalizedReceiver) {
        long now = clock.millis();
        String redisKey = RL_PEERS_24H_PREFIX + normalizedSender;
        if (redisTemplate != null) {
            try {
                Long res = redisTemplate.execute(
                        DISTINCT_PEERS_24H_SCRIPT,
                        List.of(redisKey),
                        String.valueOf(now),
                        String.valueOf(TWENTY_FOUR_HOURS_MS),
                        String.valueOf(unverifiedNewPeersPer24h),
                        normalizedReceiver);
                if (res != null) {
                    return res == 1L;
                }
            } catch (Exception e) {
                log.debug("Fallback em memoria para limite 24h de destinatarios ({}): {}", redisKey, e.getMessage());
            }
        }

        Map<String, Long> peers = fallbackDistinctPeers24h.computeIfAbsent(normalizedSender, k -> new ConcurrentHashMap<>());
        synchronized (peers) {
            long cutoff = now - TWENTY_FOUR_HOURS_MS;
            Iterator<Map.Entry<String, Long>> it = peers.entrySet().iterator();
            while (it.hasNext()) {
                if (it.next().getValue() <= cutoff) {
                    it.remove();
                }
            }
            if (peers.containsKey(normalizedReceiver)) {
                peers.put(normalizedReceiver, now);
                return true;
            }
            if (peers.size() >= unverifiedNewPeersPer24h) {
                return false;
            }
            peers.put(normalizedReceiver, now);
            return true;
        }
    }
}
