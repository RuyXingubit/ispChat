package br.dev.ispchat.node.dht;

import br.dev.ispchat.grpc.DeviceDelegationCert;
import br.dev.ispchat.grpc.DocumentType;
import br.dev.ispchat.grpc.HomeNodeClaim;
import br.dev.ispchat.grpc.IdentityReplacementNotice;
import br.dev.ispchat.grpc.RegisterSessionRequest;
import br.dev.ispchat.grpc.StoreRequest;
import br.dev.ispchat.grpc.UserSessionInfo;
import br.dev.ispchat.grpc.VerificationCredential;
import br.dev.ispchat.node.security.CryptoService;
import br.dev.ispchat.node.security.TierRateLimiterService;
import br.dev.ispchat.node.security.TierRateLimiterService.AccountTier;
import br.dev.ispchat.node.security.TierRateLimiterService.ValidatedSessionIdentity;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Armazenamento local da DHT Kademlia em Redis Hash (TTL 30 dias) com contingencia em memoria:
 * - ispchat:dht:sessions:<user_uuid> -> Hash(device_id -> Base64(UserSessionInfo))
 * - ispchat:dht:homenode:<user_uuid> -> Base64(HomeNodeClaim) com protecao monotona de routing_epoch
 * - ispchat:dht:redirect:<user_uuid> -> Endereco do novo Home Node em portabilidade (TTL 30 dias)
 * - ispchat:dht:replaced:<old_uuid>  -> Base64(IdentityReplacementNotice) em CPF Re-Bind
 */
@Service
public class DhtStoreService {

    private static final Logger log = LoggerFactory.getLogger(DhtStoreService.class);

    public static final String REDIS_SESSIONS_PREFIX = "ispchat:dht:sessions:";
    public static final String REDIS_HOMENODE_PREFIX = "ispchat:dht:homenode:";
    public static final String REDIS_REDIRECT_PREFIX = "ispchat:dht:redirect:";
    public static final String REDIS_REPLACED_PREFIX = "ispchat:dht:replaced:";
    public static final Duration DHT_RECORD_TTL = Duration.ofDays(30);
    public static final Duration REPLACEMENT_NOTICE_TTL = Duration.ofDays(90);

    public enum StoreStatus {
        STORED,
        INVALID_SIGNATURE_OR_TIMESTAMP,
        PERMISSION_DENIED,
        RESOURCE_EXHAUSTED
    }

    public record StoreOutcome(StoreStatus status, String message) {
        public boolean isSuccess() {
            return status == StoreStatus.STORED;
        }
    }

    private final CryptoService cryptoService;
    private final StringRedisTemplate redisTemplate;
    private final TierRateLimiterService tierRateLimiterService;

    // Armazenamento em memoria (contingencia e testes unitarios sem Redis ativo)
    private final Map<String, Map<String, UserSessionInfo>> fallbackSessions = new ConcurrentHashMap<>();
    private final Map<String, HomeNodeClaim> fallbackHomeNodes = new ConcurrentHashMap<>();
    private final Map<String, String> fallbackRedirects = new ConcurrentHashMap<>();
    private final Map<String, IdentityReplacementNotice> fallbackReplacements = new ConcurrentHashMap<>();

    public DhtStoreService(CryptoService cryptoService) {
        this(cryptoService, null, new TierRateLimiterService(cryptoService));
    }

    @Autowired
    public DhtStoreService(
            CryptoService cryptoService,
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            @Autowired(required = false) TierRateLimiterService tierRateLimiterService) {
        this.cryptoService = cryptoService;
        this.redisTemplate = redisTemplate;
        this.tierRateLimiterService = tierRateLimiterService != null
                ? tierRateLimiterService
                : new TierRateLimiterService(cryptoService);
    }

    /**
     * Metodo legado V1 (single-device Unverified): valida ISPCHAT-DHT-V1 e armazena sessao com device_id == user_uuid.
     */
    public boolean storeVerifiedSession(
            String userUuid,
            String nodeAddress,
            byte[] ed25519PublicKey,
            byte[] x25519PublicKey,
            byte[] signature,
            long timestampMillis) {
        RegisterSessionRequest request = RegisterSessionRequest.newBuilder()
                .setUserUuid(userUuid != null ? userUuid : "")
                .setNodeAddress(nodeAddress != null ? nodeAddress : "")
                .setPublicKey(ed25519PublicKey != null ? ByteString.copyFrom(ed25519PublicKey) : ByteString.EMPTY)
                .setX25519PublicKey(x25519PublicKey != null ? ByteString.copyFrom(x25519PublicKey) : ByteString.EMPTY)
                .setSignature(signature != null ? ByteString.copyFrom(signature) : ByteString.EMPTY)
                .setTimestamp(timestampMillis)
                .build();
        return storeSessionFromClient(request).isSuccess();
    }

    public StoreOutcome storeSessionFromClient(RegisterSessionRequest request) {
        return storeSessionInternal(
                request.getUserUuid(),
                request.getDeviceId(),
                request.getNodeAddress(),
                request.getPublicKey().toByteArray(),
                request.getX25519PublicKey().toByteArray(),
                request.getRootEd25519Pub().toByteArray(),
                request.getSignature().toByteArray(),
                request.getTimestamp(),
                request.hasDelegationCert() ? request.getDelegationCert() : null,
                request.hasVerificationCredential() ? request.getVerificationCredential() : null,
                request.hasHomeNodeClaim() ? request.getHomeNodeClaim() : null);
    }

    public StoreOutcome storeSessionFromPeer(StoreRequest request) {
        return storeSessionInternal(
                request.getUserUuid(),
                request.getDeviceId(),
                request.getNodeAddress(),
                request.getPublicKey().toByteArray(),
                request.getX25519PublicKey().toByteArray(),
                request.getRootEd25519Pub().toByteArray(),
                request.getSignature().toByteArray(),
                request.getTimestamp(),
                request.hasDelegationCert() ? request.getDelegationCert() : null,
                request.hasVerificationCredential() ? request.getVerificationCredential() : null,
                request.hasHomeNodeClaim() ? request.getHomeNodeClaim() : null);
    }

    private StoreOutcome storeSessionInternal(
            String rawUserUuid,
            String rawDeviceId,
            String rawNodeAddress,
            byte[] activeDeviceEdPub,
            byte[] activeDeviceX25519Pub,
            byte[] explicitRootEdPub,
            byte[] signature,
            long timestampMillis,
            DeviceDelegationCert delegationCert,
            VerificationCredential verificationCredential,
            HomeNodeClaim homeNodeClaim) {

        if (rawUserUuid == null || rawUserUuid.isBlank() || rawNodeAddress == null || rawNodeAddress.isBlank()) {
            return new StoreOutcome(StoreStatus.INVALID_SIGNATURE_OR_TIMESTAMP, "user_uuid ou node_address ausente");
        }

        ValidatedSessionIdentity identity;
        try {
            identity = tierRateLimiterService.validateSessionCredentials(
                    rawUserUuid,
                    rawDeviceId,
                    activeDeviceEdPub,
                    delegationCert,
                    verificationCredential);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.PERMISSION_DENIED) {
                return new StoreOutcome(StoreStatus.PERMISSION_DENIED, e.getStatus().getDescription());
            }
            return new StoreOutcome(StoreStatus.INVALID_SIGNATURE_OR_TIMESTAMP, e.getStatus().getDescription());
        }

        String normalizedUuid = identity.userUuid();
        String normalizedDeviceId = identity.deviceId();
        String normalizedAddress = rawNodeAddress.trim();
        byte[] rootEdPub = (explicitRootEdPub != null && explicitRootEdPub.length == CryptoService.ED25519_PUBLIC_KEY_LENGTH)
                ? explicitRootEdPub
                : identity.rootEd25519Pub();

        boolean hasV2Metadata = (rawDeviceId != null && !rawDeviceId.isBlank())
                || (explicitRootEdPub != null && explicitRootEdPub.length > 0)
                || (delegationCert != null && !delegationCert.getCertId().isBlank())
                || (verificationCredential != null && !verificationCredential.getCredentialId().isBlank());

        boolean sigValid = false;
        boolean usedV2Transcript = false;

        if (activeDeviceX25519Pub != null && activeDeviceX25519Pub.length == CryptoService.ED25519_PUBLIC_KEY_LENGTH) {
            sigValid = cryptoService.verifyDhtSessionV2(
                    normalizedUuid,
                    normalizedDeviceId,
                    normalizedAddress,
                    rootEdPub,
                    activeDeviceEdPub,
                    activeDeviceX25519Pub,
                    timestampMillis,
                    signature,
                    delegationCert,
                    verificationCredential,
                    true);
            if (sigValid) {
                usedV2Transcript = true;
            }
        }

        if (!sigValid && !hasV2Metadata && identity.tier() == AccountTier.UNVERIFIED) {
            sigValid = cryptoService.verifyDhtSessionRegistration(
                    normalizedUuid,
                    normalizedAddress,
                    timestampMillis,
                    activeDeviceEdPub,
                    signature);
        }

        if (!sigValid) {
            log.warn("Pacote DHT Store/Register rejeitado por falha na verificacao criptografica (UUID={}, device={})",
                    normalizedUuid, normalizedDeviceId);
            return new StoreOutcome(
                    StoreStatus.INVALID_SIGNATURE_OR_TIMESTAMP,
                    "Assinatura digital Ed25519 invalida, timestamp expirado ou UUIDv7 incompativel com a PublicKey");
        }

        if (homeNodeClaim != null && !homeNodeClaim.getUserUuid().isBlank()) {
            if (!homeNodeClaim.getUserUuid().trim().equalsIgnoreCase(normalizedUuid)
                    || !storeHomeNodeClaim(homeNodeClaim, verificationCredential, rootEdPub)) {
                return new StoreOutcome(
                        StoreStatus.PERMISSION_DENIED,
                        "HomeNodeClaim invalido, nao assinado pelo Principal ou com routing_epoch obsoleto");
            }
        }

        UserSessionInfo.Builder infoBuilder = UserSessionInfo.newBuilder()
                .setNodeAddress(normalizedAddress)
                .setEd25519PublicKey(ByteString.copyFrom(activeDeviceEdPub))
                .setX25519PublicKey(activeDeviceX25519Pub != null ? ByteString.copyFrom(activeDeviceX25519Pub) : ByteString.EMPTY)
                .setTimestamp(timestampMillis)
                .setSessionSignature(ByteString.copyFrom(signature));

        if (usedV2Transcript) {
            infoBuilder.setDeviceId(normalizedDeviceId)
                    .setRootEd25519Pub(ByteString.copyFrom(rootEdPub))
                    .setDeviceEd25519Pub(ByteString.copyFrom(activeDeviceEdPub));
            if (delegationCert != null && !delegationCert.getCertId().isBlank()) {
                infoBuilder.setDelegationCert(delegationCert);
            }
            if (verificationCredential != null && !verificationCredential.getCredentialId().isBlank()) {
                infoBuilder.setVerificationCredential(verificationCredential);
            }
        }

        UserSessionInfo candidate = infoBuilder.build();
        int maxDevices = identity.tier().maxActiveDevices();

        StoreStatus persistenceStatus = persistSessionRecord(normalizedUuid, normalizedDeviceId, candidate, maxDevices);
        if (persistenceStatus == StoreStatus.RESOURCE_EXHAUSTED) {
            return new StoreOutcome(
                    StoreStatus.RESOURCE_EXHAUSTED,
                    "Limite maximo de dispositivos ativos atingido para o nivel da conta (" + maxDevices + ")");
        }
        return new StoreOutcome(StoreStatus.STORED, "");
    }

    private StoreStatus persistSessionRecord(
            String normalizedUuid,
            String normalizedDeviceId,
            UserSessionInfo candidate,
            int maxDevices) {

        Map<String, UserSessionInfo> currentSessions = loadSessionMap(normalizedUuid);
        long now = cryptoService.getClock().millis();

        // Remove certificados de delegacao ja expirados antes de validar o limite de dispositivos
        List<String> expiredKeys = new ArrayList<>();
        for (Map.Entry<String, UserSessionInfo> entry : currentSessions.entrySet()) {
            UserSessionInfo existing = entry.getValue();
            if (existing.hasDelegationCert()
                    && !existing.getDelegationCert().getCertId().isBlank()
                    && existing.getDelegationCert().getExpiresAtMs() <= now) {
                expiredKeys.add(entry.getKey());
            }
        }
        for (String expiredDevId : expiredKeys) {
            currentSessions.remove(expiredDevId);
            deleteDeviceFromRedis(normalizedUuid, expiredDevId);
        }

        UserSessionInfo existingForDevice = currentSessions.get(normalizedDeviceId);
        if (existingForDevice != null) {
            if (existingForDevice.getTimestamp() > candidate.getTimestamp()) {
                return StoreStatus.STORED; // Ignora pacote antigo reordenado na rede
            }
        } else if (currentSessions.size() >= maxDevices) {
            return StoreStatus.RESOURCE_EXHAUSTED;
        }

        fallbackSessions.computeIfAbsent(normalizedUuid, k -> new ConcurrentHashMap<>())
                .put(normalizedDeviceId, candidate);

        if (redisTemplate != null) {
            try {
                String redisKey = REDIS_SESSIONS_PREFIX + normalizedUuid;
                String encoded = Base64.getEncoder().encodeToString(candidate.toByteArray());
                redisTemplate.opsForHash().put(redisKey, normalizedDeviceId, encoded);
                redisTemplate.expire(redisKey, DHT_RECORD_TTL);
            } catch (Exception e) {
                log.debug("Redis indisponivel ao persistir sessao DHT ({}), mantida em memoria: {}",
                        normalizedUuid, e.getMessage());
            }
        }
        return StoreStatus.STORED;
    }

    /**
     * Valida e armazena um HomeNodeClaim com protecao monotona estrita de routing_epoch.
     */
    public boolean storeHomeNodeClaim(
            HomeNodeClaim claim,
            VerificationCredential optionalCredential,
            byte[] optionalUserRootEd25519Pub) {
        if (claim == null || claim.getUserUuid().isBlank()) {
            return false;
        }
        if (!cryptoService.verifyHomeNodeClaim(claim, optionalCredential, optionalUserRootEd25519Pub)) {
            return false;
        }
        String normalizedUuid = claim.getUserUuid().trim().toLowerCase(Locale.ROOT);
        Optional<HomeNodeClaim> existingOpt = getHomeNodeClaim(normalizedUuid);
        if (existingOpt.isPresent()) {
            HomeNodeClaim existing = existingOpt.get();
            if (claim.getRoutingEpoch() < existing.getRoutingEpoch()) {
                return false; // Rejeita routing_epoch obsoleto (anti-rollback)
            }
            if (existing.getSignedByPrincipal() && !claim.getSignedByPrincipal()) {
                return false; // Impede downgrade de claim oficial para auto-assinado epoch 0
            }
            if (claim.getRoutingEpoch() == existing.getRoutingEpoch()
                    && claim.getIssuedAtMs() < existing.getIssuedAtMs()) {
                return true;
            }
        }

        fallbackHomeNodes.put(normalizedUuid, claim);
        if (redisTemplate != null) {
            try {
                String redisKey = REDIS_HOMENODE_PREFIX + normalizedUuid;
                String encoded = Base64.getEncoder().encodeToString(claim.toByteArray());
                redisTemplate.opsForValue().set(redisKey, encoded, DHT_RECORD_TTL);
            } catch (Exception e) {
                log.debug("Redis indisponivel ao salvar HomeNodeClaim ({}), mantido em memoria: {}",
                        normalizedUuid, e.getMessage());
            }
        }
        return true;
    }

    public Optional<HomeNodeClaim> getHomeNodeClaim(String userUuid) {
        if (userUuid == null || userUuid.isBlank()) {
            return Optional.empty();
        }
        String normalizedUuid = userUuid.trim().toLowerCase(Locale.ROOT);
        if (redisTemplate != null) {
            try {
                String encoded = redisTemplate.opsForValue().get(REDIS_HOMENODE_PREFIX + normalizedUuid);
                if (encoded != null && !encoded.isBlank()) {
                    HomeNodeClaim parsed = HomeNodeClaim.parseFrom(Base64.getDecoder().decode(encoded));
                    fallbackHomeNodes.put(normalizedUuid, parsed);
                    return Optional.of(parsed);
                }
            } catch (Exception e) {
                log.debug("Falha ao ler HomeNodeClaim no Redis ({}): {}", normalizedUuid, e.getMessage());
            }
        }
        return Optional.ofNullable(fallbackHomeNodes.get(normalizedUuid));
    }

    public void storeRedirectTombstone(String userUuid, String newHomeNodeAddress) {
        if (userUuid == null || userUuid.isBlank() || newHomeNodeAddress == null || newHomeNodeAddress.isBlank()) {
            return;
        }
        String normalizedUuid = userUuid.trim().toLowerCase(Locale.ROOT);
        String trimmedTarget = newHomeNodeAddress.trim();
        fallbackRedirects.put(normalizedUuid, trimmedTarget);
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(REDIS_REDIRECT_PREFIX + normalizedUuid, trimmedTarget, DHT_RECORD_TTL);
            } catch (Exception e) {
                log.debug("Redis indisponivel ao salvar redirect tombstone ({}): {}", normalizedUuid, e.getMessage());
            }
        }
    }

    public Optional<String> getRedirectTombstone(String userUuid) {
        if (userUuid == null || userUuid.isBlank()) {
            return Optional.empty();
        }
        String normalizedUuid = userUuid.trim().toLowerCase(Locale.ROOT);
        if (redisTemplate != null) {
            try {
                String redirect = redisTemplate.opsForValue().get(REDIS_REDIRECT_PREFIX + normalizedUuid);
                if (redirect != null && !redirect.isBlank()) {
                    fallbackRedirects.put(normalizedUuid, redirect);
                    return Optional.of(redirect);
                }
            } catch (Exception e) {
                log.debug("Falha ao ler redirect tombstone no Redis ({}): {}", normalizedUuid, e.getMessage());
            }
        }
        return Optional.ofNullable(fallbackRedirects.get(normalizedUuid));
    }

    public boolean storeReplacementNotice(IdentityReplacementNotice notice) {
        if (notice == null || notice.getOldUserUuid().isBlank() || notice.getNewUserUuid().isBlank()) {
            return false;
        }
        if (cryptoService.hasTrustedPrincipalKeys() && !cryptoService.verifyIdentityReplacementNotice(notice)) {
            return false;
        }
        String normalizedOld = notice.getOldUserUuid().trim().toLowerCase(Locale.ROOT);
        fallbackReplacements.put(normalizedOld, notice);
        evictAllSessions(normalizedOld);
        if (redisTemplate != null) {
            try {
                String encoded = Base64.getEncoder().encodeToString(notice.toByteArray());
                redisTemplate.opsForValue().set(REDIS_REPLACED_PREFIX + normalizedOld, encoded, REPLACEMENT_NOTICE_TTL);
            } catch (Exception e) {
                log.debug("Redis indisponivel ao salvar IdentityReplacementNotice ({}): {}", normalizedOld, e.getMessage());
            }
        }
        return true;
    }

    public Optional<IdentityReplacementNotice> getReplacementNotice(String userUuid) {
        if (userUuid == null || userUuid.isBlank()) {
            return Optional.empty();
        }
        String normalizedUuid = userUuid.trim().toLowerCase(Locale.ROOT);
        if (redisTemplate != null) {
            try {
                String encoded = redisTemplate.opsForValue().get(REDIS_REPLACED_PREFIX + normalizedUuid);
                if (encoded != null && !encoded.isBlank()) {
                    IdentityReplacementNotice parsed = IdentityReplacementNotice.parseFrom(Base64.getDecoder().decode(encoded));
                    fallbackReplacements.put(normalizedUuid, parsed);
                    return Optional.of(parsed);
                }
            } catch (Exception e) {
                log.debug("Falha ao ler IdentityReplacementNotice no Redis ({}): {}", normalizedUuid, e.getMessage());
            }
        }
        return Optional.ofNullable(fallbackReplacements.get(normalizedUuid));
    }

    public void evictDeviceSession(String userUuid, String deviceId) {
        if (userUuid == null || userUuid.isBlank() || deviceId == null || deviceId.isBlank()) {
            return;
        }
        String normalizedUuid = userUuid.trim().toLowerCase(Locale.ROOT);
        String normalizedDev = deviceId.trim().toLowerCase(Locale.ROOT);
        Map<String, UserSessionInfo> mem = fallbackSessions.get(normalizedUuid);
        if (mem != null) {
            mem.remove(normalizedDev);
        }
        deleteDeviceFromRedis(normalizedUuid, normalizedDev);
    }

    public void evictAllSessions(String userUuid) {
        if (userUuid == null || userUuid.isBlank()) {
            return;
        }
        String normalizedUuid = userUuid.trim().toLowerCase(Locale.ROOT);
        fallbackSessions.remove(normalizedUuid);
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(REDIS_SESSIONS_PREFIX + normalizedUuid);
            } catch (Exception e) {
                log.debug("Falha ao remover sessoes no Redis para {}: {}", normalizedUuid, e.getMessage());
            }
        }
    }

    /**
     * Retorna todas as sessoes ativas conhecidas localmente para um UUIDv7.
     */
    public List<UserSessionInfo> getSessions(String targetUuid) {
        if (targetUuid == null || targetUuid.isBlank()) {
            return List.of();
        }
        String normalizedUuid = targetUuid.trim().toLowerCase(Locale.ROOT);
        if (tierRateLimiterService.isAccountRevoked(normalizedUuid)) {
            return List.of();
        }
        Map<String, UserSessionInfo> sessionsByDevice = loadSessionMap(normalizedUuid);
        if (sessionsByDevice.isEmpty()) {
            return List.of();
        }
        return sessionsByDevice.values().stream()
                .filter(s -> {
                    String devId = s.getDeviceId().isBlank() ? normalizedUuid : s.getDeviceId();
                    return !tierRateLimiterService.isDeviceRevoked(devId);
                })
                .sorted(Comparator.comparing(s -> s.getDeviceId().isBlank() ? normalizedUuid : s.getDeviceId()))
                .toList();
    }

    private Map<String, UserSessionInfo> loadSessionMap(String normalizedUuid) {
        Map<String, UserSessionInfo> merged = new LinkedHashMap<>();
        Map<String, UserSessionInfo> localMem = fallbackSessions.get(normalizedUuid);
        if (localMem != null) {
            merged.putAll(localMem);
        }
        if (redisTemplate != null) {
            try {
                Map<Object, Object> rawHash = redisTemplate.opsForHash().entries(REDIS_SESSIONS_PREFIX + normalizedUuid);
                if (rawHash != null && !rawHash.isEmpty()) {
                    for (Map.Entry<Object, Object> entry : rawHash.entrySet()) {
                        String devId = String.valueOf(entry.getKey());
                        String base64 = String.valueOf(entry.getValue());
                        UserSessionInfo parsed = UserSessionInfo.parseFrom(Base64.getDecoder().decode(base64));
                        merged.merge(devId, parsed, (a, b) -> b.getTimestamp() >= a.getTimestamp() ? b : a);
                    }
                }
            } catch (Exception e) {
                log.debug("Redis indisponivel ao ler sessoes DHT ({}): {}", normalizedUuid, e.getMessage());
            }
        }
        return merged;
    }

    private void deleteDeviceFromRedis(String normalizedUuid, String normalizedDevId) {
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForHash().delete(REDIS_SESSIONS_PREFIX + normalizedUuid, normalizedDevId);
            } catch (Exception e) {
                log.debug("Falha ao remover device {} de {}: {}", normalizedDevId, normalizedUuid, e.getMessage());
            }
        }
    }
}
