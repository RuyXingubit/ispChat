package br.dev.ispchat.node.webrtc;

import br.dev.ispchat.common.crypto.CanonicalTranscripts;
import br.dev.ispchat.common.crypto.CanonicalTranscripts.RawKeyPair;
import br.dev.ispchat.grpc.DeviceDelegationCert;
import br.dev.ispchat.grpc.DeviceRole;
import br.dev.ispchat.grpc.DocumentType;
import br.dev.ispchat.grpc.TurnCredentialRequest;
import br.dev.ispchat.grpc.TurnCredentialResponse;
import br.dev.ispchat.grpc.VerificationCredential;
import br.dev.ispchat.node.security.CryptoService;
import br.dev.ispchat.node.security.TierRateLimiterService;
import br.dev.ispchat.node.security.V2GovernanceTestFixtures;
import br.dev.ispchat.node.security.V2GovernanceTestFixtures.GovernancePki;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TurnCredentialGrpcServiceTest {

    private static final long FIXED_NOW_MS = 1762900000000L;
    private static final String SHARED_TURN_SECRET = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

    @Test
    @DisplayName("Deve emitir credenciais TURN de 600s (max 5/h) para Unverified e 3600s para Verified (Root e Desktop)")
    void shouldIssueTieredTurnCredentialsAndEnforceHourlyQuota() {
        Clock clock = Clock.fixed(Instant.ofEpochMilli(FIXED_NOW_MS), ZoneOffset.UTC);
        GovernancePki pki = GovernancePki.create(FIXED_NOW_MS);
        CryptoService crypto = new CryptoService(clock, pki.rootEd25519().publicKey(), "fallback.ispchat.dev.br:443");
        TierRateLimiterService rateLimiter = new TierRateLimiterService(crypto);
        TurnCredentialGrpcService turnSvc = new TurnCredentialGrpcService(
                crypto,
                rateLimiter,
                SHARED_TURN_SECRET,
                "",
                "node1.ispchat.dev.br:443",
                64_000);

        // 1. Usuario nao verificado: TTL = 600s, max_bps = 64000, limite = 5 emissoes/hora
        RawKeyPair unverifiedKey = CanonicalTranscripts.generateEd25519KeyPair();
        String unvUuid = CanonicalTranscripts.deriveIdentityUuidV7(unverifiedKey.publicKey()).toString();

        for (int i = 0; i < 5; i++) {
            TurnCredentialResponse resp = invokeTurnSuccess(
                    turnSvc, buildTurnRequest(unvUuid, unvUuid, unverifiedKey, null, null, FIXED_NOW_MS));
            assertEquals(600, resp.getTtlSeconds());
            assertEquals(64_000, resp.getMaxBps());
            long expectedExpiry = (FIXED_NOW_MS / 1000L) + 600;
            assertEquals(expectedExpiry + ":" + unvUuid, resp.getUsername());
            assertEquals(
                    TurnCredentialGrpcService.computeTurnRestCredential(SHARED_TURN_SECRET, resp.getUsername()),
                    resp.getCredential());
            assertTrue(resp.getUrisList().contains("turn:node1.ispchat.dev.br:3478?transport=udp"));
        }

        // 6a requisicao dentro de 1h para Unverified deve falhar com RESOURCE_EXHAUSTED
        StatusRuntimeException quotaEx = invokeTurnFailure(
                turnSvc, buildTurnRequest(unvUuid, unvUuid, unverifiedKey, null, null, FIXED_NOW_MS));
        assertEquals(Status.Code.RESOURCE_EXHAUSTED, quotaEx.getStatus().getCode());

        // 2. Usuario CPF verificado (Desktop delegado): TTL = 3600s, max_bps = 64000
        RawKeyPair verRootEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair verRootX = CanonicalTranscripts.generateX25519KeyPair();
        VerificationCredential cred = pki.issueCredential(
                verRootEd, verRootX, DocumentType.CPF, "node1.ispchat.dev.br:443", 1L, FIXED_NOW_MS);
        RawKeyPair deskEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair deskX = CanonicalTranscripts.generateX25519KeyPair();
        DeviceDelegationCert deskCert = V2GovernanceTestFixtures.signDeviceDelegation(
                verRootEd, deskEd, deskX, DeviceRole.DESKTOP, FIXED_NOW_MS);

        TurnCredentialResponse verResp = invokeTurnSuccess(
                turnSvc,
                buildTurnRequest(cred.getUserUuid(), deskCert.getDeviceId(), deskEd, deskCert, cred, FIXED_NOW_MS));
        assertEquals(3600, verResp.getTtlSeconds());
        assertEquals(64_000, verResp.getMaxBps());
        assertEquals(((FIXED_NOW_MS / 1000L) + 3600) + ":" + cred.getUserUuid(), verResp.getUsername());
    }

    @Test
    @DisplayName("Deve rejeitar requisicoes TURN com assinatura invalida, timestamp expirado ou conta revogada")
    void shouldRejectInvalidOrRevokedTurnRequests() {
        Clock clock = Clock.fixed(Instant.ofEpochMilli(FIXED_NOW_MS), ZoneOffset.UTC);
        GovernancePki pki = GovernancePki.create(FIXED_NOW_MS);
        CryptoService crypto = new CryptoService(clock, pki.rootEd25519().publicKey(), "fallback.ispchat.dev.br:443");
        TierRateLimiterService rateLimiter = new TierRateLimiterService(crypto);
        TurnCredentialGrpcService turnSvc = new TurnCredentialGrpcService(
                crypto, rateLimiter, SHARED_TURN_SECRET, "", "node1.ispchat.dev.br:443", 64_000);

        RawKeyPair userKey = CanonicalTranscripts.generateEd25519KeyPair();
        String userUuid = CanonicalTranscripts.deriveIdentityUuidV7(userKey.publicKey()).toString();

        // Timestamp fora da janela de 5 min -> UNAUTHENTICATED
        StatusRuntimeException staleEx = invokeTurnFailure(
                turnSvc, buildTurnRequest(userUuid, userUuid, userKey, null, null, FIXED_NOW_MS - 600_000L));
        assertEquals(Status.Code.UNAUTHENTICATED, staleEx.getStatus().getCode());

        // Assinatura adulterada -> UNAUTHENTICATED
        TurnCredentialRequest validReq = buildTurnRequest(userUuid, userUuid, userKey, null, null, FIXED_NOW_MS);
        byte[] badSig = validReq.getSignature().toByteArray();
        badSig[0] ^= 0x55;
        StatusRuntimeException badSigEx = invokeTurnFailure(
                turnSvc, validReq.toBuilder().setSignature(ByteString.copyFrom(badSig)).build());
        assertEquals(Status.Code.UNAUTHENTICATED, badSigEx.getStatus().getCode());

        // Conta revogada -> PERMISSION_DENIED
        rateLimiter.markAccountRevoked(userUuid, "ABUSE");
        StatusRuntimeException revokedEx = invokeTurnFailure(turnSvc, validReq);
        assertEquals(Status.Code.PERMISSION_DENIED, revokedEx.getStatus().getCode());
    }

    private static TurnCredentialRequest buildTurnRequest(
            String userUuid,
            String deviceId,
            RawKeyPair signerEd,
            DeviceDelegationCert delCert,
            VerificationCredential cred,
            long timestampMs) {
        byte[] transcript = CanonicalTranscripts.buildTurnCredentialRequestTranscript(userUuid, deviceId, timestampMs);
        byte[] sig = CanonicalTranscripts.signEd25519(signerEd.privateKey(), transcript);
        TurnCredentialRequest.Builder b = TurnCredentialRequest.newBuilder()
                .setUserUuid(userUuid)
                .setDeviceId(deviceId)
                .setTimestampMs(timestampMs)
                .setCallerPublicKey(ByteString.copyFrom(signerEd.publicKey()))
                .setSignature(ByteString.copyFrom(sig));
        if (delCert != null) {
            b.setDelegationCert(delCert);
        }
        if (cred != null) {
            b.setVerificationCredential(cred);
        }
        return b.build();
    }

    private static TurnCredentialResponse invokeTurnSuccess(
            TurnCredentialGrpcService svc, TurnCredentialRequest req) {
        AtomicReference<TurnCredentialResponse> resp = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        svc.getTurnCredentials(req, new StreamObserver<>() {
            @Override public void onNext(TurnCredentialResponse value) { resp.set(value); }
            @Override public void onError(Throwable t) { err.set(t); }
            @Override public void onCompleted() {}
        });
        assertNull(err.get(), () -> "Nao esperava erro: " + err.get());
        assertNotNull(resp.get());
        return resp.get();
    }

    private static StatusRuntimeException invokeTurnFailure(
            TurnCredentialGrpcService svc, TurnCredentialRequest req) {
        AtomicReference<Throwable> err = new AtomicReference<>();
        svc.getTurnCredentials(req, new StreamObserver<>() {
            @Override public void onNext(TurnCredentialResponse value) {}
            @Override public void onError(Throwable t) { err.set(t); }
            @Override public void onCompleted() {}
        });
        assertNotNull(err.get());
        return assertInstanceOf(StatusRuntimeException.class, err.get());
    }
}
