package br.dev.ispchat.principal.config;

import br.dev.ispchat.common.crypto.CanonicalTranscripts;
import br.dev.ispchat.grpc.PrincipalSigningCert;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Material criptografico imutavel em memoria do servidor Principal:
 * - Chave publica Raiz Nivel 0 (offline) pinada;
 * - Chave privada online Ed25519 do Principal;
 * - PrincipalSigningCert assinado pela Raiz Nivel 0 (contendo tambem escrow_x25519_pub).
 */
public final class PrincipalKeyMaterial {

    private final byte[] rootEd25519Pub;
    private final byte[] principalSigningPriv;
    private final PrincipalSigningCert principalCert;

    public PrincipalKeyMaterial(
            byte[] rootEd25519Pub,
            byte[] principalSigningPriv,
            PrincipalSigningCert principalCert) {
        Objects.requireNonNull(rootEd25519Pub, "rootEd25519Pub nao pode ser nulo");
        Objects.requireNonNull(principalSigningPriv, "principalSigningPriv nao pode ser nulo");
        Objects.requireNonNull(principalCert, "principalCert nao pode ser nulo");
        if (rootEd25519Pub.length != CanonicalTranscripts.RAW_KEY_LENGTH) {
            throw new IllegalArgumentException("rootEd25519Pub deve conter 32 bytes");
        }
        if (principalSigningPriv.length != CanonicalTranscripts.RAW_KEY_LENGTH) {
            throw new IllegalArgumentException("principalSigningPriv deve conter 32 bytes");
        }

        byte[] certTranscript = CanonicalTranscripts.buildPrincipalSigningCertTranscript(principalCert);
        if (!CanonicalTranscripts.verifyEd25519(
                rootEd25519Pub,
                certTranscript,
                principalCert.getRootSignature().toByteArray())) {
            throw new IllegalArgumentException(
                    "PrincipalSigningCert invalido: assinatura Ed25519 nao confere com a chave Raiz Nivel 0");
        }

        byte[] challenge = "ISPCHAT-PRIN-KEYPAIR-SELFTEST-V1".getBytes(StandardCharsets.UTF_8);
        byte[] testSig = CanonicalTranscripts.signEd25519(principalSigningPriv, challenge);
        if (!CanonicalTranscripts.verifyEd25519(
                principalCert.getPrincipalEd25519Pub().toByteArray(),
                challenge,
                testSig)) {
            throw new IllegalArgumentException(
                    "principalSigningPriv nao corresponde a principal_ed25519_pub do PrincipalSigningCert");
        }

        if (principalCert.getEscrowX25519Pub().size() != CanonicalTranscripts.RAW_KEY_LENGTH) {
            throw new IllegalArgumentException("escrow_x25519_pub do PrincipalSigningCert deve conter 32 bytes");
        }

        this.rootEd25519Pub = rootEd25519Pub.clone();
        this.principalSigningPriv = principalSigningPriv.clone();
        this.principalCert = principalCert;
    }

    public byte[] rootEd25519Pub() {
        return rootEd25519Pub.clone();
    }

    public byte[] principalEd25519Pub() {
        return principalCert.getPrincipalEd25519Pub().toByteArray();
    }

    public byte[] escrowX25519Pub() {
        return principalCert.getEscrowX25519Pub().toByteArray();
    }

    public PrincipalSigningCert principalCert() {
        return principalCert;
    }

    public byte[] signWithPrincipal(byte[] transcript) {
        return CanonicalTranscripts.signEd25519(principalSigningPriv, transcript);
    }

    public void zeroize() {
        Arrays.fill(principalSigningPriv, (byte) 0);
    }

    public static byte[] decodeFlexibleBytes(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Configuracao obrigatoria ausente: " + fieldName);
        }
        String trimmed = raw.trim();
        if (trimmed.length() % 2 == 0 && trimmed.matches("^[0-9a-fA-F]+$")) {
            return HexFormat.of().parseHex(trimmed);
        }
        try {
            return Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException ignored) {
            return Base64.getUrlDecoder().decode(trimmed);
        }
    }
}
