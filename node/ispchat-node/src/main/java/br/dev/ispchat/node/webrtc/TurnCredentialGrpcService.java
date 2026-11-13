package br.dev.ispchat.node.webrtc;

import br.dev.ispchat.common.crypto.CanonicalTranscripts;
import br.dev.ispchat.grpc.DeviceDelegationCert;
import br.dev.ispchat.grpc.TurnCredentialRequest;
import br.dev.ispchat.grpc.TurnCredentialResponse;
import br.dev.ispchat.grpc.TurnCredentialServiceGrpc;
import br.dev.ispchat.grpc.VerificationCredential;
import br.dev.ispchat.node.security.CryptoService;
import br.dev.ispchat.node.security.TierRateLimiterService;
import br.dev.ispchat.node.security.TierRateLimiterService.AccountTier;
import br.dev.ispchat.node.security.TierRateLimiterService.ValidatedSessionIdentity;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Emissor de credenciais efemeras Coturn (draft-uberti-behave-turn-rest / use-auth-secret).
 *
 * Politica Dual-Tier:
 * - Contas nao verificadas (UNVERIFIED): TTL = 600s (10 min), limite = 5 emissoes/hora.
 * - Contas verificadas (CPF / CNPJ): TTL = 3600s (1 hora), limite = 60 emissoes/hora.
 * - Teto de banda por alocacao imposto pelo Coturn: max_bps = 64000 (512 kbps).
 */
@Service
public class TurnCredentialGrpcService extends TurnCredentialServiceGrpc.TurnCredentialServiceImplBase {

    public static final int UNVERIFIED_TTL_SECONDS = 600;
    public static final int VERIFIED_TTL_SECONDS = 3600;
    public static final int DEFAULT_MAX_BPS = 64_000;

    private final CryptoService cryptoService;
    private final TierRateLimiterService tierRateLimiterService;
    private final String staticAuthSecret;
    private final List<String> turnUris;
    private final int maxBps;

    @Autowired
    public TurnCredentialGrpcService(
            CryptoService cryptoService,
            TierRateLimiterService tierRateLimiterService,
            @Value("${ispchat.turn.static-auth-secret:}") String staticAuthSecret,
            @Value("${ispchat.turn.uris:}") String configuredTurnUris,
            @Value("${ispchat.node.public-address:127.0.0.1}") String localPublicAddress,
            @Value("${ispchat.turn.max-bps:64000}") int maxBps) {
        this.cryptoService = cryptoService;
        this.tierRateLimiterService = tierRateLimiterService;
        this.staticAuthSecret = staticAuthSecret != null ? staticAuthSecret.trim() : "";
        this.turnUris = resolveTurnUris(configuredTurnUris, localPublicAddress);
        this.maxBps = maxBps > 0 ? maxBps : DEFAULT_MAX_BPS;
    }

    @Override
    public void getTurnCredentials(
            TurnCredentialRequest request,
            StreamObserver<TurnCredentialResponse> responseObserver) {

        if (staticAuthSecret.isBlank()) {
            responseObserver.onError(Status.FAILED_PRECONDITION
                    .withDescription("Segredo estatico do servidor Coturn nao configurado neste No ISP")
                    .asRuntimeException());
            return;
        }

        if (!cryptoService.isTimestampFresh(request.getTimestampMs())) {
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription("Timestamp expirado ou fora da janela anti-replay de 5 minutos")
                    .asRuntimeException());
            return;
        }

        DeviceDelegationCert delCert = request.hasDelegationCert() ? request.getDelegationCert() : null;
        VerificationCredential verCred = request.hasVerificationCredential() ? request.getVerificationCredential() : null;

        ValidatedSessionIdentity identity;
        try {
            identity = tierRateLimiterService.validateSessionCredentials(
                    request.getUserUuid(),
                    request.getDeviceId(),
                    request.getCallerPublicKey().toByteArray(),
                    delCert,
                    verCred);
        } catch (StatusRuntimeException e) {
            responseObserver.onError(e);
            return;
        }

        byte[] transcript;
        try {
            transcript = CanonicalTranscripts.buildTurnCredentialRequestTranscript(
                    identity.userUuid(),
                    identity.deviceId(),
                    request.getTimestampMs());
        } catch (RuntimeException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Parametros invalidos na requisicao TURN: " + e.getMessage())
                    .asRuntimeException());
            return;
        }

        if (!cryptoService.verifyEd25519Signature(
                identity.deviceEd25519Pub(),
                transcript,
                request.getSignature().toByteArray())) {
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription("Assinatura Ed25519 invalida sobre ISPCHAT-TURN-REQ-V1")
                    .asRuntimeException());
            return;
        }

        if (!tierRateLimiterService.checkTurnIssuanceAllowed(identity.userUuid(), identity.tier())) {
            responseObserver.onError(Status.RESOURCE_EXHAUSTED
                    .withDescription("Limite por hora de emissao de credenciais TURN excedido para este nivel de conta")
                    .asRuntimeException());
            return;
        }

        int ttlSeconds = identity.tier() == AccountTier.UNVERIFIED
                ? UNVERIFIED_TTL_SECONDS
                : VERIFIED_TTL_SECONDS;
        long unixExpirySeconds = (cryptoService.getClock().millis() / 1000L) + ttlSeconds;
        String username = unixExpirySeconds + ":" + identity.userUuid();
        String credential = computeTurnRestCredential(staticAuthSecret, username);

        TurnCredentialResponse response = TurnCredentialResponse.newBuilder()
                .addAllUris(turnUris)
                .setUsername(username)
                .setCredential(credential)
                .setTtlSeconds(ttlSeconds)
                .setMaxBps(maxBps)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public static String computeTurnRestCredential(String secret, String username) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] rawHmac = mac.doFinal(username.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 indisponivel no runtime Java", e);
        }
    }

    private static List<String> resolveTurnUris(String configuredTurnUris, String localPublicAddress) {
        if (configuredTurnUris != null && !configuredTurnUris.isBlank()) {
            List<String> list = new ArrayList<>();
            for (String item : configuredTurnUris.split(",")) {
                if (!item.isBlank()) {
                    list.add(item.trim());
                }
            }
            if (!list.isEmpty()) {
                return List.copyOf(list);
            }
        }
        String host = extractHostOnly(localPublicAddress);
        return List.of(
                "stun:" + host + ":3478",
                "turn:" + host + ":3478?transport=udp",
                "turn:" + host + ":3478?transport=tcp",
                "turns:" + host + ":5349?transport=tcp");
    }

    private static String extractHostOnly(String address) {
        if (address == null || address.isBlank()) {
            return "127.0.0.1";
        }
        String trimmed = address.trim();
        if (trimmed.startsWith("[")) {
            int end = trimmed.indexOf(']');
            return end > 0 ? trimmed.substring(0, end + 1) : trimmed;
        }
        int firstColon = trimmed.indexOf(':');
        int lastColon = trimmed.lastIndexOf(':');
        if (firstColon > 0 && firstColon == lastColon) {
            return trimmed.substring(0, firstColon);
        }
        return trimmed;
    }
}
