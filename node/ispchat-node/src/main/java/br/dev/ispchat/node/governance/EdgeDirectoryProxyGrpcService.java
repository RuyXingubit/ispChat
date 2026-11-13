package br.dev.ispchat.node.governance;

import br.dev.ispchat.common.crypto.CanonicalTranscripts;
import br.dev.ispchat.grpc.DirectoryServiceGrpc;
import br.dev.ispchat.grpc.SearchCnpjRequest;
import br.dev.ispchat.grpc.SearchCnpjResponse;
import br.dev.ispchat.node.grpc.GrpcChannelPool;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Proxy gRPC de borda do No ISP Parceiro para buscas publicas de contas CNPJ verificadas
 * (DirectoryService.SearchVerifiedCnpj), com cache local de 5 minutos no Redis.
 */
public class EdgeDirectoryProxyGrpcService extends DirectoryServiceGrpc.DirectoryServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(EdgeDirectoryProxyGrpcService.class);

    public static final String REDIS_DIR_CACHE_PREFIX = "ispchat:dir:cache:";
    public static final Duration DIRECTORY_CACHE_TTL = Duration.ofMinutes(5);
    private static final int MAX_MEMORY_CACHE_ENTRIES = 256;

    private record CachedSearchEntry(SearchCnpjResponse response, long expiresAtMs) {
    }

    private final GrpcChannelPool channelPool;
    private final StringRedisTemplate redisTemplate;
    private final String principalAddress;
    private final Clock clock;
    private final Map<String, CachedSearchEntry> memoryCache = new ConcurrentHashMap<>();

    public EdgeDirectoryProxyGrpcService(
            GrpcChannelPool channelPool,
            StringRedisTemplate redisTemplate,
            String principalAddress,
            Clock clock) {
        this.channelPool = channelPool;
        this.redisTemplate = redisTemplate;
        this.principalAddress = principalAddress != null ? principalAddress.trim() : "";
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Override
    public void searchVerifiedCnpj(
            SearchCnpjRequest request,
            StreamObserver<SearchCnpjResponse> responseObserver) {

        int normalizedLimit = request.getLimit() <= 0 ? 20 : Math.min(50, request.getLimit());
        String normalizedQuery = request.getQuery().trim().toLowerCase(Locale.ROOT);
        String cacheKeySuffix = HexFormat.of().formatHex(
                CanonicalTranscripts.sha256Utf8(normalizedQuery + ":" + normalizedLimit));

        SearchCnpjResponse cached = readCachedResponse(cacheKeySuffix);
        if (cached != null) {
            responseObserver.onNext(cached);
            responseObserver.onCompleted();
            return;
        }

        if (principalAddress.isBlank()) {
            responseObserver.onError(Status.UNAVAILABLE
                    .withDescription("Servidor Principal nao configurado neste No ISP (ispchat.principal.address)")
                    .asRuntimeException());
            return;
        }

        try {
            ManagedChannel channel = channelPool.getChannel(principalAddress);
            DirectoryServiceGrpc.DirectoryServiceBlockingStub stub =
                    DirectoryServiceGrpc.newBlockingStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS);
            SearchCnpjResponse response = stub.searchVerifiedCnpj(SearchCnpjRequest.newBuilder()
                    .setQuery(request.getQuery().trim())
                    .setLimit(normalizedLimit)
                    .build());
            storeCachedResponse(cacheKeySuffix, response);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (StatusRuntimeException e) {
            responseObserver.onError(e.getStatus().asRuntimeException());
        } catch (Exception e) {
            log.warn("Falha ao consultar diretorio CNPJ no servidor Principal ({}): {}",
                    principalAddress, e.getMessage());
            responseObserver.onError(Status.UNAVAILABLE
                    .withDescription("Diretorio corporativo temporariamente indisponivel")
                    .asRuntimeException());
        }
    }

    private SearchCnpjResponse readCachedResponse(String cacheKeySuffix) {
        if (redisTemplate != null) {
            try {
                String encoded = redisTemplate.opsForValue().get(REDIS_DIR_CACHE_PREFIX + cacheKeySuffix);
                if (encoded != null && !encoded.isBlank()) {
                    return SearchCnpjResponse.parseFrom(Base64.getDecoder().decode(encoded));
                }
            } catch (Exception e) {
                log.debug("Redis indisponivel ao ler cache de diretorio CNPJ: {}", e.getMessage());
            }
        }
        CachedSearchEntry memEntry = memoryCache.get(cacheKeySuffix);
        if (memEntry != null) {
            if (memEntry.expiresAtMs() > clock.millis()) {
                return memEntry.response();
            }
            memoryCache.remove(cacheKeySuffix, memEntry);
        }
        return null;
    }

    private void storeCachedResponse(String cacheKeySuffix, SearchCnpjResponse response) {
        if (redisTemplate != null) {
            try {
                String encoded = Base64.getEncoder().encodeToString(response.toByteArray());
                redisTemplate.opsForValue().set(
                        REDIS_DIR_CACHE_PREFIX + cacheKeySuffix,
                        encoded,
                        DIRECTORY_CACHE_TTL);
            } catch (Exception e) {
                log.debug("Redis indisponivel ao gravar cache de diretorio CNPJ: {}", e.getMessage());
            }
        }
        if (memoryCache.size() >= MAX_MEMORY_CACHE_ENTRIES) {
            memoryCache.clear();
        }
        memoryCache.put(
                cacheKeySuffix,
                new CachedSearchEntry(response, clock.millis() + DIRECTORY_CACHE_TTL.toMillis()));
    }
}
