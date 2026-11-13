package br.dev.ispchat.common.crypto;

import br.dev.ispchat.grpc.CollaboratorAuthCert;
import br.dev.ispchat.grpc.DeviceDelegationCert;
import br.dev.ispchat.grpc.DocumentType;
import br.dev.ispchat.grpc.HomeNodeClaim;
import br.dev.ispchat.grpc.IdentityReplacementNotice;
import br.dev.ispchat.grpc.MultiDeviceEncryptedPayload;
import br.dev.ispchat.grpc.PartnerSeedEntry;
import br.dev.ispchat.grpc.PrincipalSigningCert;
import br.dev.ispchat.grpc.RevocationEvent;
import br.dev.ispchat.grpc.UserVerificationConsent;
import br.dev.ispchat.grpc.VerificationCredential;
import br.dev.ispchat.grpc.WrappedContentKey;
import com.github.f4b6a3.uuid.UuidCreator;
import com.google.protobuf.ByteString;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.EdECPrivateKey;
import java.security.interfaces.XECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical length-prefixed binary transcripts, CPF/CNPJ normalizers, RFC 9562 UUIDv7 helpers,
 * and cross-language (Java 25 <-> Flutter/Dart) Ed25519 / X25519 / HKDF-SHA256 / ChaCha20-Poly1305 primitives.
 */
public final class CanonicalTranscripts {

    public static final int RAW_KEY_LENGTH = 32;
    public static final int ED25519_SIGNATURE_LENGTH = 64;
    public static final int CHACHA20_NONCE_LENGTH = 12;
    public static final int POLY1305_TAG_LENGTH = 16;
    public static final int WRAPPED_KEY_BLOB_LENGTH = CHACHA20_NONCE_LENGTH + RAW_KEY_LENGTH + POLY1305_TAG_LENGTH;

    // Canonical Domain Separation Tags (Technical Plan Section 2)
    public static final String DOMAIN_ROOT_PRIN_V1 = "ISPCHAT-ROOT-PRIN-V1";
    public static final String DOMAIN_CHAIN_ISP_V1 = "ISPCHAT-CHAIN-ISP-V1";
    public static final String DOMAIN_CHAIN_COLLAB_V1 = "ISPCHAT-CHAIN-COLLAB-V1";
    public static final String DOMAIN_COLLAB_REVOKE_V1 = "ISPCHAT-COLLAB-REVOKE-V1";
    public static final String DOMAIN_VERIFY_REQ_V1 = "ISPCHAT-VERIFY-REQ-V1";
    public static final String DOMAIN_CHAIN_USER_V1 = "ISPCHAT-CHAIN-USER-V1";
    public static final String DOMAIN_REBIND_V1 = "ISPCHAT-REBIND-V1";
    public static final String DOMAIN_CRED_V1 = "ISPCHAT-CRED-V1";
    public static final String DOMAIN_CRED_FETCH_V1 = "ISPCHAT-CRED-FETCH-V1";
    public static final String DOMAIN_DELEGATE_V1 = "ISPCHAT-DELEGATE-V1";
    public static final String DOMAIN_DEVICE_REVOKE_V1 = "ISPCHAT-DEVICE-REVOKE-V1";
    public static final String DOMAIN_PORT_V1 = "ISPCHAT-PORT-V1";
    public static final String DOMAIN_TURN_REQ_V1 = "ISPCHAT-TURN-REQ-V1";
    public static final String DOMAIN_DHT_V2 = "ISPCHAT-DHT-V2";
    public static final String DOMAIN_HOME_V1 = "ISPCHAT-HOME-V1";
    public static final String DOMAIN_REPLACE_V1 = "ISPCHAT-REPLACE-V1";
    public static final String DOMAIN_REVOKE_V1 = "ISPCHAT-REVOKE-V1";
    public static final String DOMAIN_MSG_V2 = "ISPCHAT-MSG-V2";
    public static final String DOMAIN_LEDGER_ROW_V1 = "ISPCHAT-LEDGER-ROW-V1";

    private static final String HKDF_AUDIT_INFO = "ISPCHAT-AUDIT-ESCROW-V1";
    private static final String HKDF_WRAP_PREFIX = "ISPCHAT-WRAP-V2:";

    // RFC 8410 ASN.1 SPKI (12 bytes) & PKCS#8 v0 (16 bytes) headers for Ed25519 (OID 1.3.101.112)
    private static final byte[] ED25519_SPKI_PREFIX = new byte[]{
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };
    private static final byte[] ED25519_PKCS8_PREFIX = new byte[]{
            0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x04, 0x22, 0x04, 0x20
    };

    // RFC 8410 ASN.1 SPKI (12 bytes) & PKCS#8 v0 (16 bytes) headers for X25519 (OID 1.3.101.110)
    private static final byte[] X25519_SPKI_PREFIX = new byte[]{
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x03, 0x21, 0x00
    };
    private static final byte[] X25519_PKCS8_PREFIX = new byte[]{
            0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x04, 0x22, 0x04, 0x20
    };

    private static final int[] CNPJ_WEIGHTS_DV1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] CNPJ_WEIGHTS_DV2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CanonicalTranscripts() {
    }

    /**
     * Raw 32-byte public/private keypair representation.
     */
    public record RawKeyPair(byte[] publicKey, byte[] privateKey) {
        public RawKeyPair {
            Objects.requireNonNull(publicKey, "publicKey cannot be null");
            Objects.requireNonNull(privateKey, "privateKey cannot be null");
            if (publicKey.length != RAW_KEY_LENGTH || privateKey.length != RAW_KEY_LENGTH) {
                throw new IllegalArgumentException("Raw keys must be 32 bytes");
            }
            publicKey = publicKey.clone();
            privateKey = privateKey.clone();
        }

        @Override
        public byte[] publicKey() {
            return publicKey.clone();
        }

        @Override
        public byte[] privateKey() {
            return privateKey.clone();
        }
    }

    /**
     * Sealed asymmetric X25519 + ChaCha20-Poly1305 audit escrow output.
     */
    public record SealedEscrowRecord(byte[] ephemeralX25519Pub, byte[] sealedAuditBlob) {
        public SealedEscrowRecord {
            Objects.requireNonNull(ephemeralX25519Pub, "ephemeralX25519Pub cannot be null");
            Objects.requireNonNull(sealedAuditBlob, "sealedAuditBlob cannot be null");
            ephemeralX25519Pub = ephemeralX25519Pub.clone();
            sealedAuditBlob = sealedAuditBlob.clone();
        }

        @Override
        public byte[] ephemeralX25519Pub() {
            return ephemeralX25519Pub.clone();
        }

        @Override
        public byte[] sealedAuditBlob() {
            return sealedAuditBlob.clone();
        }
    }

    /**
     * Generates a time-ordered RFC 9562 UUIDv7 using uuid-creator.
     */
    public static UUID generateTemporalUuidV7() {
        return UuidCreator.getTimeOrderedEpoch();
    }

    /**
     * Deterministically derives an identity UUIDv7 from a 32-byte raw Ed25519 public key.
     */
    public static UUID deriveIdentityUuidV7(byte[] ed25519PublicKey32Bytes) {
        requireLength(ed25519PublicKey32Bytes, RAW_KEY_LENGTH, "ed25519PublicKey");
        byte[] hash = sha256(ed25519PublicKey32Bytes);
        byte[] uuidBytes = Arrays.copyOf(hash, 16);
        uuidBytes[6] = (byte) ((uuidBytes[6] & 0x0F) | 0x70);
        uuidBytes[8] = (byte) ((uuidBytes[8] & 0x3F) | 0x80);
        ByteBuffer buffer = ByteBuffer.wrap(uuidBytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    /**
     * Verifies that uuidString is a valid RFC 9562 UUIDv7 matching deriveIdentityUuidV7(ed25519PublicKey).
     */
    public static boolean verifyIdentityBinding(String uuidString, byte[] ed25519PublicKey32Bytes) {
        if (uuidString == null || uuidString.isBlank()
                || ed25519PublicKey32Bytes == null || ed25519PublicKey32Bytes.length != RAW_KEY_LENGTH) {
            return false;
        }
        try {
            UUID parsed = UUID.fromString(uuidString.trim());
            if (parsed.version() != 7 || parsed.variant() != 2) {
                return false;
            }
            return deriveIdentityUuidV7(ed25519PublicKey32Bytes).equals(parsed);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Normalizes and validates a Brazilian CPF (11 ASCII digits + Modulo-11 check digits).
     */
    public static String normalizeAndValidateCpf(String rawCpf) {
        if (rawCpf == null) {
            throw new IllegalArgumentException("CPF cannot be null");
        }
        String cleaned = stripDocumentPunctuation(rawCpf);
        if (cleaned.length() != 11) {
            throw new IllegalArgumentException("CPF must contain 11 digits");
        }
        boolean allSame = true;
        int firstChar = cleaned.charAt(0);
        for (int i = 0; i < 11; i++) {
            char c = cleaned.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("CPF must contain only ASCII digits");
            }
            if (c != firstChar) {
                allSame = false;
            }
        }
        if (allSame) {
            throw new IllegalArgumentException("CPF cannot consist of 11 identical digits");
        }

        int sum1 = 0;
        for (int i = 0; i < 9; i++) {
            sum1 += (cleaned.charAt(i) - '0') * (10 - i);
        }
        int dv1 = 11 - (sum1 % 11);
        if (dv1 >= 10) {
            dv1 = 0;
        }
        if ((cleaned.charAt(9) - '0') != dv1) {
            throw new IllegalArgumentException("Invalid CPF check digits");
        }

        int sum2 = 0;
        for (int i = 0; i < 10; i++) {
            sum2 += (cleaned.charAt(i) - '0') * (11 - i);
        }
        int dv2 = 11 - (sum2 % 11);
        if (dv2 >= 10) {
            dv2 = 0;
        }
        if ((cleaned.charAt(10) - '0') != dv2) {
            throw new IllegalArgumentException("Invalid CPF check digits");
        }
        return cleaned;
    }

    /**
     * Normalizes and validates a Brazilian CNPJ (14 uppercase ASCII alphanumeric chars [0-9A-Z]{12}[0-9]{2}
     * per Receita Federal IN RFB 2229/2024 + Modulo-11 check digits).
     */
    public static String normalizeAndValidateCnpj(String rawCnpj) {
        if (rawCnpj == null) {
            throw new IllegalArgumentException("CNPJ cannot be null");
        }
        String cleaned = stripDocumentPunctuation(rawCnpj).toUpperCase(Locale.ROOT);
        if (cleaned.length() != 14) {
            throw new IllegalArgumentException("CNPJ must contain 14 characters");
        }

        boolean allSame = true;
        char first = cleaned.charAt(0);
        for (int i = 0; i < 12; i++) {
            char c = cleaned.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z'))) {
                throw new IllegalArgumentException("CNPJ base must contain only [0-9A-Z]");
            }
            if (c != first) {
                allSame = false;
            }
        }
        for (int i = 12; i < 14; i++) {
            char c = cleaned.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("CNPJ check digits must be numeric [0-9]");
            }
            if (c != first) {
                allSame = false;
            }
        }
        if (allSame) {
            throw new IllegalArgumentException("CNPJ cannot consist of 14 identical characters");
        }

        int sum1 = 0;
        for (int i = 0; i < 12; i++) {
            sum1 += (cleaned.charAt(i) - '0') * CNPJ_WEIGHTS_DV1[i];
        }
        int rem1 = sum1 % 11;
        int dv1 = (rem1 < 2) ? 0 : (11 - rem1);
        if ((cleaned.charAt(12) - '0') != dv1) {
            throw new IllegalArgumentException("Invalid CNPJ check digits");
        }

        int sum2 = 0;
        for (int i = 0; i < 13; i++) {
            sum2 += (cleaned.charAt(i) - '0') * CNPJ_WEIGHTS_DV2[i];
        }
        int rem2 = sum2 % 11;
        int dv2 = (rem2 < 2) ? 0 : (11 - rem2);
        if ((cleaned.charAt(13) - '0') != dv2) {
            throw new IllegalArgumentException("Invalid CNPJ check digits");
        }
        return cleaned;
    }

    /**
     * Normalizes and validates a document according to DocumentType (CPF or CNPJ).
     */
    public static String normalizeAndValidateDocument(DocumentType docType, String rawDocument) {
        Objects.requireNonNull(docType, "docType cannot be null");
        return switch (docType) {
            case CPF -> normalizeAndValidateCpf(rawDocument);
            case CNPJ -> normalizeAndValidateCnpj(rawDocument);
            default -> throw new IllegalArgumentException("Unsupported DocumentType: " + docType);
        };
    }

    private static String stripDocumentPunctuation(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '.' && c != '-' && c != '/' && !Character.isWhitespace(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // =========================================================================
    // CANONICAL LENGTH-PREFIXED TRANSCRIPT BUILDERS
    // =========================================================================

    public static byte[] buildPrincipalSigningCertTranscript(PrincipalSigningCert cert) {
        Objects.requireNonNull(cert, "cert cannot be null");
        return new TranscriptWriter(DOMAIN_ROOT_PRIN_V1)
                .writeUuid(cert.getCertId())
                .writeFixedBytes(cert.getPrincipalEd25519Pub().toByteArray(), RAW_KEY_LENGTH, "principal_ed25519_pub")
                .writeFixedBytes(cert.getEscrowX25519Pub().toByteArray(), RAW_KEY_LENGTH, "escrow_x25519_pub")
                .writeInt64Be(cert.getIssuedAtMs())
                .writeInt64Be(cert.getExpiresAtMs())
                .toByteArray();
    }

    public static byte[] computePartnersDigestSha256(List<PartnerSeedEntry> partners) {
        Objects.requireNonNull(partners, "partners cannot be null");
        List<PartnerSeedEntry> sorted = partners.stream()
                .sorted(Comparator.comparing(p -> canonicalUuid(p.getPartnerUuid())))
                .toList();
        TranscriptWriter writer = new TranscriptWriter("ISPCHAT-PARTNERS-DIGEST-V1");
        writer.writeUint32Be(sorted.size());
        for (PartnerSeedEntry p : sorted) {
            String normalizedCpf = normalizeAndValidateCpf(p.getPartnerCpf());
            writer.writeUuid(p.getPartnerUuid())
                    .writeFixedBytes(sha256Utf8(normalizedCpf), RAW_KEY_LENGTH, "partner_cpf_sha256")
                    .writeFixedBytes(p.getPartnerEd25519Pub().toByteArray(), RAW_KEY_LENGTH, "partner_ed25519_pub")
                    .writeFixedBytes(p.getPartnerX25519Pub().toByteArray(), RAW_KEY_LENGTH, "partner_x25519_pub");
        }
        return sha256(writer.toByteArray());
    }

    public static byte[] buildPartnerIspBootstrapTranscript(
            String ispCnpj,
            String tradeName,
            String corporateUuid,
            byte[] corpRootEd25519Pub,
            byte[] corpRootX25519Pub,
            String nodeAddress,
            byte[] partnersDigestSha256,
            long issuedAtMs) {
        String normalizedCnpj = normalizeAndValidateCnpj(ispCnpj);
        return new TranscriptWriter(DOMAIN_CHAIN_ISP_V1)
                .writeUtf8(normalizedCnpj)
                .writeUtf8(Objects.requireNonNull(tradeName, "tradeName").trim())
                .writeUuid(corporateUuid)
                .writeFixedBytes(corpRootEd25519Pub, RAW_KEY_LENGTH, "corp_root_ed25519_pub")
                .writeFixedBytes(corpRootX25519Pub, RAW_KEY_LENGTH, "corp_root_x25519_pub")
                .writeUtf8(Objects.requireNonNull(nodeAddress, "nodeAddress").trim().toLowerCase(Locale.ROOT))
                .writeFixedBytes(partnersDigestSha256, RAW_KEY_LENGTH, "partners_digest_sha256")
                .writeInt64Be(issuedAtMs)
                .toByteArray();
    }

    public static byte[] buildCollaboratorAuthTranscript(CollaboratorAuthCert cert) {
        Objects.requireNonNull(cert, "cert cannot be null");
        String normalizedCnpj = normalizeAndValidateCnpj(cert.getIspCnpj());
        return new TranscriptWriter(DOMAIN_CHAIN_COLLAB_V1)
                .writeUuid(cert.getCertId())
                .writeUtf8(normalizedCnpj)
                .writeUuid(cert.getPartnerUuid())
                .writeUuid(cert.getCollaboratorUuid())
                .writeFixedBytes(cert.getCollabCpfSha256().toByteArray(), RAW_KEY_LENGTH, "collab_cpf_sha256")
                .writeFixedBytes(cert.getCollabEd25519Pub().toByteArray(), RAW_KEY_LENGTH, "collab_ed25519_pub")
                .writeInt64Be(cert.getIssuedAtMs())
                .writeInt64Be(cert.getExpiresAtMs())
                .toByteArray();
    }

    public static byte[] buildRevokeCollaboratorTranscript(
            String ispCnpj,
            String partnerUuid,
            String collaboratorUuid,
            String reason,
            long timestampMs) {
        String normalizedCnpj = normalizeAndValidateCnpj(ispCnpj);
        return new TranscriptWriter(DOMAIN_COLLAB_REVOKE_V1)
                .writeUtf8(normalizedCnpj)
                .writeUuid(partnerUuid)
                .writeUuid(collaboratorUuid)
                .writeUtf8(Objects.requireNonNull(reason, "reason").trim())
                .writeInt64Be(timestampMs)
                .toByteArray();
    }

    public static byte[] buildUserVerificationConsentTranscript(UserVerificationConsent consent) {
        Objects.requireNonNull(consent, "consent cannot be null");
        String normalizedIspCnpj = normalizeAndValidateCnpj(consent.getIspCnpj());
        return new TranscriptWriter(DOMAIN_VERIFY_REQ_V1)
                .writeUuid(consent.getUserUuid())
                .writeFixedBytes(consent.getUserEd25519Pub().toByteArray(), RAW_KEY_LENGTH, "user_ed25519_pub")
                .writeFixedBytes(consent.getUserX25519Pub().toByteArray(), RAW_KEY_LENGTH, "user_x25519_pub")
                .writeUint32Be(consent.getDocTypeValue())
                .writeFixedBytes(consent.getDocSha256().toByteArray(), RAW_KEY_LENGTH, "doc_sha256")
                .writeUtf8(normalizedIspCnpj)
                .writeInt64Be(consent.getTimestampMs())
                .toByteArray();
    }

    public static byte[] buildCollaboratorUserAttestationTranscript(
            String ispCnpj,
            String collaboratorUuid,
            String userUuid,
            DocumentType docType,
            byte[] docSha256,
            String homeNodeAddress,
            byte[] consentSignature,
            long attestedAtMs) {
        String normalizedIspCnpj = normalizeAndValidateCnpj(ispCnpj);
        return new TranscriptWriter(DOMAIN_CHAIN_USER_V1)
                .writeUtf8(normalizedIspCnpj)
                .writeUuid(collaboratorUuid)
                .writeUuid(userUuid)
                .writeUint32Be(Objects.requireNonNull(docType, "docType").getNumber())
                .writeFixedBytes(docSha256, RAW_KEY_LENGTH, "doc_sha256")
                .writeUtf8(Objects.requireNonNull(homeNodeAddress, "homeNodeAddress").trim().toLowerCase(Locale.ROOT))
                .writeFixedBytes(consentSignature, ED25519_SIGNATURE_LENGTH, "consent_signature")
                .writeInt64Be(attestedAtMs)
                .toByteArray();
    }

    public static byte[] buildCpfRebindTranscript(
            String ispCnpj,
            String collaboratorUuid,
            String oldUserUuid,
            String newUserUuid,
            byte[] docSha256,
            String homeNodeAddress,
            byte[] newConsentSignature,
            long attestedAtMs) {
        String normalizedIspCnpj = normalizeAndValidateCnpj(ispCnpj);
        return new TranscriptWriter(DOMAIN_REBIND_V1)
                .writeUtf8(normalizedIspCnpj)
                .writeUuid(collaboratorUuid)
                .writeUuid(oldUserUuid)
                .writeUuid(newUserUuid)
                .writeFixedBytes(docSha256, RAW_KEY_LENGTH, "doc_sha256")
                .writeUtf8(Objects.requireNonNull(homeNodeAddress, "homeNodeAddress").trim().toLowerCase(Locale.ROOT))
                .writeFixedBytes(newConsentSignature, ED25519_SIGNATURE_LENGTH, "new_consent_signature")
                .writeInt64Be(attestedAtMs)
                .toByteArray();
    }

    public static byte[] buildVerificationCredentialTranscript(VerificationCredential cred) {
        Objects.requireNonNull(cred, "cred cannot be null");
        String normalizedVerifyingIspCnpj = normalizeAndValidateCnpj(cred.getVerifyingIspCnpj());
        String publicCnpj = cred.getDocType() == DocumentType.CNPJ
                ? normalizeAndValidateCnpj(cred.getPublicCnpj())
                : "";
        String publicDisplayName = cred.getDocType() == DocumentType.CNPJ
                ? cred.getPublicDisplayName().trim()
                : "";
        return new TranscriptWriter(DOMAIN_CRED_V1)
                .writeUuid(cred.getCredentialId())
                .writeUuid(cred.getUserUuid())
                .writeFixedBytes(cred.getRootEd25519Pub().toByteArray(), RAW_KEY_LENGTH, "root_ed25519_pub")
                .writeFixedBytes(cred.getRootX25519Pub().toByteArray(), RAW_KEY_LENGTH, "root_x25519_pub")
                .writeUint32Be(cred.getDocTypeValue())
                .writeUtf8(publicCnpj)
                .writeUtf8(publicDisplayName)
                .writeUtf8(normalizedVerifyingIspCnpj)
                .writeUtf8(cred.getVerifyingIspName().trim())
                .writeUtf8(cred.getHomeNodeAddress().trim().toLowerCase(Locale.ROOT))
                .writeInt64Be(cred.getRoutingEpoch())
                .writeInt64Be(cred.getIssuedAtMs())
                .writeInt64Be(cred.getExpiresAtMs())
                .toByteArray();
    }

    public static byte[] buildGetOrRenewCredentialTranscript(String userUuid, String deviceId, long timestampMs) {
        return new TranscriptWriter(DOMAIN_CRED_FETCH_V1)
                .writeUuid(userUuid)
                .writeUuid(deviceId)
                .writeInt64Be(timestampMs)
                .toByteArray();
    }

    public static byte[] buildDeviceDelegationTranscript(DeviceDelegationCert cert) {
        Objects.requireNonNull(cert, "cert cannot be null");
        return new TranscriptWriter(DOMAIN_DELEGATE_V1)
                .writeUuid(cert.getCertId())
                .writeUuid(cert.getUserUuid())
                .writeUuid(cert.getDeviceId())
                .writeUint32Be(cert.getRoleValue())
                .writeFixedBytes(cert.getDeviceEd25519Pub().toByteArray(), RAW_KEY_LENGTH, "device_ed25519_pub")
                .writeFixedBytes(cert.getDeviceX25519Pub().toByteArray(), RAW_KEY_LENGTH, "device_x25519_pub")
                .writeInt64Be(cert.getIssuedAtMs())
                .writeInt64Be(cert.getExpiresAtMs())
                .toByteArray();
    }

    public static byte[] buildRevokeDeviceTranscript(
            String userUuid,
            String revokedDeviceId,
            String reason,
            long timestampMs) {
        return new TranscriptWriter(DOMAIN_DEVICE_REVOKE_V1)
                .writeUuid(userUuid)
                .writeUuid(revokedDeviceId)
                .writeUtf8(Objects.requireNonNull(reason, "reason").trim())
                .writeInt64Be(timestampMs)
                .toByteArray();
    }

    public static byte[] buildPortAccountTranscript(
            String userUuid,
            String targetHomeNodeAddress,
            String targetIspCnpj,
            long expectedRoutingEpoch,
            long timestampMs) {
        String normalizedTargetCnpj = (targetIspCnpj == null || targetIspCnpj.isBlank())
                ? ""
                : normalizeAndValidateCnpj(targetIspCnpj);
        return new TranscriptWriter(DOMAIN_PORT_V1)
                .writeUuid(userUuid)
                .writeUtf8(Objects.requireNonNull(targetHomeNodeAddress, "targetHomeNodeAddress").trim().toLowerCase(Locale.ROOT))
                .writeUtf8(normalizedTargetCnpj)
                .writeInt64Be(expectedRoutingEpoch)
                .writeInt64Be(timestampMs)
                .toByteArray();
    }

    public static byte[] buildTurnCredentialRequestTranscript(String userUuid, String deviceId, long timestampMs) {
        return new TranscriptWriter(DOMAIN_TURN_REQ_V1)
                .writeUuid(userUuid)
                .writeUuid(deviceId)
                .writeInt64Be(timestampMs)
                .toByteArray();
    }

    public static byte[] buildDhtSessionV2Transcript(
            String userUuid,
            String deviceId,
            String nodeAddress,
            byte[] rootEd25519Pub,
            byte[] deviceEd25519Pub,
            byte[] deviceX25519Pub,
            long timestampMs) {
        return new TranscriptWriter(DOMAIN_DHT_V2)
                .writeUuid(userUuid)
                .writeUuid(deviceId)
                .writeUtf8(Objects.requireNonNull(nodeAddress, "nodeAddress").trim().toLowerCase(Locale.ROOT))
                .writeFixedBytes(rootEd25519Pub, RAW_KEY_LENGTH, "root_ed25519_pub")
                .writeFixedBytes(deviceEd25519Pub, RAW_KEY_LENGTH, "device_ed25519_pub")
                .writeFixedBytes(deviceX25519Pub, RAW_KEY_LENGTH, "device_x25519_pub")
                .writeInt64Be(timestampMs)
                .toByteArray();
    }

    public static byte[] buildHomeNodeClaimTranscript(HomeNodeClaim claim) {
        Objects.requireNonNull(claim, "claim cannot be null");
        return new TranscriptWriter(DOMAIN_HOME_V1)
                .writeUuid(claim.getUserUuid())
                .writeUtf8(claim.getHomeNodeAddress().trim().toLowerCase(Locale.ROOT))
                .writeInt64Be(claim.getRoutingEpoch())
                .writeInt64Be(claim.getIssuedAtMs())
                .toByteArray();
    }

    public static byte[] buildIdentityReplacementTranscript(IdentityReplacementNotice notice) {
        Objects.requireNonNull(notice, "notice cannot be null");
        String normalizedIspCnpj = normalizeAndValidateCnpj(notice.getVerifyingIspCnpj());
        return new TranscriptWriter(DOMAIN_REPLACE_V1)
                .writeUuid(notice.getRevocationId())
                .writeUuid(notice.getOldUserUuid())
                .writeUuid(notice.getNewUserUuid())
                .writeUtf8(normalizedIspCnpj)
                .writeInt64Be(notice.getReplacedAtMs())
                .toByteArray();
    }

    public static byte[] buildRevocationEventTranscript(RevocationEvent event) {
        Objects.requireNonNull(event, "event cannot be null");
        String optionalRevokedDevice = event.getRevokedDeviceId().isBlank()
                ? ""
                : canonicalUuid(event.getRevokedDeviceId());
        return new TranscriptWriter(DOMAIN_REVOKE_V1)
                .writeUuid(event.getRevocationId())
                .writeInt64Be(event.getRevocationSeq())
                .writeUuid(event.getTargetUuid())
                .writeUtf8(optionalRevokedDevice)
                .writeUtf8(event.getReason().trim())
                .writeInt64Be(event.getCreatedAtMs())
                .toByteArray();
    }

    public static byte[] buildMessageEnvelopeV2Transcript(
            String messageId,
            String senderUuid,
            String senderDeviceId,
            String receiverUuid,
            int messageTypeNumber,
            String refMessageId,
            byte[] encryptedPayloadBytes,
            long timestampMs) {
        String normalizedRef = (refMessageId == null || refMessageId.isBlank())
                ? ""
                : canonicalUuid(refMessageId);
        byte[] payload = encryptedPayloadBytes != null ? encryptedPayloadBytes : new byte[0];
        return new TranscriptWriter(DOMAIN_MSG_V2)
                .writeUuid(messageId)
                .writeUuid(senderUuid)
                .writeUuid(senderDeviceId)
                .writeUuid(receiverUuid)
                .writeUint32Be(messageTypeNumber)
                .writeUtf8(normalizedRef)
                .writeBytes(payload)
                .writeInt64Be(timestampMs)
                .toByteArray();
    }

    public static byte[] buildAuditLedgerRowTranscript(
            long seq,
            String entryId,
            String actionType,
            String targetUuid,
            byte[] docBlindIndex,
            String ispCnpj,
            String collaboratorUuidOrEmpty,
            byte[] ephemeralX25519Pub,
            byte[] sealedAuditBlob,
            long createdAtEpochMs) {
        String normalizedCnpj = normalizeAndValidateCnpj(ispCnpj);
        String collab = (collaboratorUuidOrEmpty == null || collaboratorUuidOrEmpty.isBlank())
                ? ""
                : canonicalUuid(collaboratorUuidOrEmpty);
        return new TranscriptWriter(DOMAIN_LEDGER_ROW_V1)
                .writeInt64Be(seq)
                .writeUuid(entryId)
                .writeUtf8(Objects.requireNonNull(actionType, "actionType").trim())
                .writeUuid(targetUuid)
                .writeFixedBytes(docBlindIndex, RAW_KEY_LENGTH, "doc_blind_index")
                .writeUtf8(normalizedCnpj)
                .writeUtf8(collab)
                .writeFixedBytes(ephemeralX25519Pub, RAW_KEY_LENGTH, "ephemeral_x25519_pub")
                .writeBytes(Objects.requireNonNull(sealedAuditBlob, "sealedAuditBlob"))
                .writeInt64Be(createdAtEpochMs)
                .toByteArray();
    }

    public static byte[] computeLedgerEntryHash(byte[] prevEntryHash32Bytes, byte[] canonicalRowBytes) {
        requireLength(prevEntryHash32Bytes, RAW_KEY_LENGTH, "prevEntryHash");
        Objects.requireNonNull(canonicalRowBytes, "canonicalRowBytes cannot be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(prevEntryHash32Bytes);
            digest.update(canonicalRowBytes);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    // =========================================================================
    // ED25519 & X25519 RAW KEY / AEAD CRYPTOGRAPHY
    // =========================================================================

    public static RawKeyPair generateEd25519KeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            KeyPair kp = kpg.generateKeyPair();
            byte[] pub = extractRawPublicKey(kp.getPublic(), ED25519_SPKI_PREFIX);
            byte[] priv = ((EdECPrivateKey) kp.getPrivate()).getBytes()
                    .orElseThrow(() -> new IllegalStateException("Ed25519 raw seed unavailable"));
            return new RawKeyPair(pub, priv);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to generate Ed25519 keypair", e);
        }
    }

    public static RawKeyPair generateX25519KeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
            KeyPair kp = kpg.generateKeyPair();
            byte[] pub = extractRawPublicKey(kp.getPublic(), X25519_SPKI_PREFIX);
            byte[] priv = ((XECPrivateKey) kp.getPrivate()).getScalar()
                    .orElseThrow(() -> new IllegalStateException("X25519 raw scalar unavailable"));
            return new RawKeyPair(pub, priv);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to generate X25519 keypair", e);
        }
    }

    public static PublicKey toJavaEd25519PublicKey(byte[] rawPublicKey32Bytes) throws GeneralSecurityException {
        requireLength(rawPublicKey32Bytes, RAW_KEY_LENGTH, "rawEd25519PublicKey");
        byte[] spki = concat(ED25519_SPKI_PREFIX, rawPublicKey32Bytes);
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(spki));
    }

    public static PrivateKey toJavaEd25519PrivateKey(byte[] rawPrivateSeed32Bytes) throws GeneralSecurityException {
        requireLength(rawPrivateSeed32Bytes, RAW_KEY_LENGTH, "rawEd25519PrivateSeed");
        byte[] pkcs8 = concat(ED25519_PKCS8_PREFIX, rawPrivateSeed32Bytes);
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    }

    public static PublicKey toJavaX25519PublicKey(byte[] rawPublicKey32Bytes) throws GeneralSecurityException {
        requireLength(rawPublicKey32Bytes, RAW_KEY_LENGTH, "rawX25519PublicKey");
        byte[] spki = concat(X25519_SPKI_PREFIX, rawPublicKey32Bytes);
        return KeyFactory.getInstance("X25519").generatePublic(new X509EncodedKeySpec(spki));
    }

    public static PrivateKey toJavaX25519PrivateKey(byte[] rawPrivateScalar32Bytes) throws GeneralSecurityException {
        requireLength(rawPrivateScalar32Bytes, RAW_KEY_LENGTH, "rawX25519PrivateScalar");
        byte[] pkcs8 = concat(X25519_PKCS8_PREFIX, rawPrivateScalar32Bytes);
        return KeyFactory.getInstance("X25519").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    }

    public static byte[] signEd25519(byte[] rawPrivateSeed32Bytes, byte[] messageBytes) {
        Objects.requireNonNull(messageBytes, "messageBytes cannot be null");
        try {
            PrivateKey privateKey = toJavaEd25519PrivateKey(rawPrivateSeed32Bytes);
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(privateKey);
            signer.update(messageBytes);
            return signer.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ed25519 signing failed", e);
        }
    }

    public static boolean verifyEd25519(byte[] rawPublicKey32Bytes, byte[] messageBytes, byte[] signature64Bytes) {
        if (rawPublicKey32Bytes == null || rawPublicKey32Bytes.length != RAW_KEY_LENGTH
                || messageBytes == null
                || signature64Bytes == null || signature64Bytes.length != ED25519_SIGNATURE_LENGTH) {
            return false;
        }
        try {
            PublicKey publicKey = toJavaEd25519PublicKey(rawPublicKey32Bytes);
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(messageBytes);
            return verifier.verify(signature64Bytes);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    public static byte[] x25519SharedSecret(byte[] rawPrivateScalar32Bytes, byte[] rawPeerPublicKey32Bytes)
            throws GeneralSecurityException {
        PrivateKey privateKey = toJavaX25519PrivateKey(rawPrivateScalar32Bytes);
        PublicKey peerPublicKey = toJavaX25519PublicKey(rawPeerPublicKey32Bytes);
        KeyAgreement ka = KeyAgreement.getInstance("X25519");
        ka.init(privateKey);
        ka.doPhase(peerPublicKey, true);
        return ka.generateSecret();
    }

    /**
     * RFC 5869 HKDF-SHA256 key derivation (Extract + Expand).
     */
    public static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int outputLength) {
        Objects.requireNonNull(ikm, "ikm cannot be null");
        if (outputLength <= 0 || outputLength > 255 * 32) {
            throw new IllegalArgumentException("Invalid HKDF outputLength: " + outputLength);
        }
        byte[] actualSalt = (salt == null || salt.length == 0) ? new byte[32] : salt;
        byte[] actualInfo = (info == null) ? new byte[0] : info;
        byte[] prk = hmacSha256(actualSalt, ikm);

        int blocks = (outputLength + 31) / 32;
        byte[] okm = new byte[outputLength];
        byte[] prevBlock = new byte[0];
        int offset = 0;

        for (int i = 1; i <= blocks; i++) {
            ByteBuffer blockInput = ByteBuffer.allocate(prevBlock.length + actualInfo.length + 1);
            blockInput.put(prevBlock);
            blockInput.put(actualInfo);
            blockInput.put((byte) i);
            prevBlock = hmacSha256(prk, blockInput.array());
            int toCopy = Math.min(32, outputLength - offset);
            System.arraycopy(prevBlock, 0, okm, offset, toCopy);
            offset += toCopy;
        }
        return okm;
    }

    public static byte[] hmacSha256(byte[] key, byte[] data) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(data, "data cannot be null");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 failure", e);
        }
    }

    /**
     * Encrypts plaintext once under a random 32-byte content_key and wraps content_key for each target device_id.
     */
    public static MultiDeviceEncryptedPayload encryptMultiDevicePayload(
            String messageId,
            String senderUuid,
            String receiverUuid,
            byte[] plaintextBytes,
            Map<String, byte[]> targetDeviceX25519PublicKeys) throws GeneralSecurityException {
        Objects.requireNonNull(plaintextBytes, "plaintextBytes cannot be null");
        Objects.requireNonNull(targetDeviceX25519PublicKeys, "targetDeviceX25519PublicKeys cannot be null");
        if (targetDeviceX25519PublicKeys.isEmpty()) {
            throw new IllegalArgumentException("At least one target device X25519 public key is required");
        }

        String normMsgId = canonicalUuid(messageId);
        String normSender = canonicalUuid(senderUuid);
        String normReceiver = canonicalUuid(receiverUuid);

        byte[] contentKey = randomBytes(RAW_KEY_LENGTH);
        byte[] contentNonce = randomBytes(CHACHA20_NONCE_LENGTH);
        byte[] contentAad = (normMsgId + "|" + normSender + "|" + normReceiver).getBytes(StandardCharsets.UTF_8);
        byte[] ciphertextWithTag = chacha20Poly1305Encrypt(contentKey, contentNonce, contentAad, plaintextBytes);

        RawKeyPair ephemeral = generateX25519KeyPair();
        MultiDeviceEncryptedPayload.Builder builder = MultiDeviceEncryptedPayload.newBuilder()
                .setEphemeralX25519Pub(ByteString.copyFrom(ephemeral.publicKey()))
                .setContentNonce(ByteString.copyFrom(contentNonce))
                .setCiphertextWithTag(ByteString.copyFrom(ciphertextWithTag));

        for (Map.Entry<String, byte[]> entry : targetDeviceX25519PublicKeys.entrySet()) {
            String targetDeviceId = canonicalUuid(entry.getKey());
            byte[] targetPub = entry.getValue();
            requireLength(targetPub, RAW_KEY_LENGTH, "targetDeviceX25519PublicKey");

            byte[] sharedSecret = x25519SharedSecret(ephemeral.privateKey(), targetPub);
            byte[] salt = concat(ephemeral.publicKey(), targetPub);
            byte[] info = (HKDF_WRAP_PREFIX + targetDeviceId).getBytes(StandardCharsets.UTF_8);
            byte[] wrapKey = hkdfSha256(sharedSecret, salt, info, RAW_KEY_LENGTH);

            byte[] wrapNonce = randomBytes(CHACHA20_NONCE_LENGTH);
            byte[] wrapAad = (normMsgId + "|" + targetDeviceId).getBytes(StandardCharsets.UTF_8);
            byte[] encryptedKeyWithTag = chacha20Poly1305Encrypt(wrapKey, wrapNonce, wrapAad, contentKey);
            byte[] wrappedBlob = concat(wrapNonce, encryptedKeyWithTag);

            builder.addWrappedKeys(WrappedContentKey.newBuilder()
                    .setTargetDeviceId(targetDeviceId)
                    .setWrappedKeyBlob(ByteString.copyFrom(wrappedBlob))
                    .build());
        }

        Arrays.fill(contentKey, (byte) 0);
        return builder.build();
    }

    /**
     * Unwraps content_key for myDeviceId and decrypts MultiDeviceEncryptedPayload.
     */
    public static byte[] decryptMultiDevicePayload(
            String messageId,
            String senderUuid,
            String receiverUuid,
            String myDeviceId,
            byte[] myDeviceX25519PrivateKey32Bytes,
            MultiDeviceEncryptedPayload payload) throws GeneralSecurityException {
        Objects.requireNonNull(payload, "payload cannot be null");
        String normMsgId = canonicalUuid(messageId);
        String normSender = canonicalUuid(senderUuid);
        String normReceiver = canonicalUuid(receiverUuid);
        String normDeviceId = canonicalUuid(myDeviceId);

        byte[] ephemeralPub = payload.getEphemeralX25519Pub().toByteArray();
        byte[] contentNonce = payload.getContentNonce().toByteArray();
        byte[] ciphertextWithTag = payload.getCiphertextWithTag().toByteArray();
        requireLength(ephemeralPub, RAW_KEY_LENGTH, "ephemeral_x25519_pub");
        requireLength(contentNonce, CHACHA20_NONCE_LENGTH, "content_nonce");

        WrappedContentKey matchingWrap = payload.getWrappedKeysList().stream()
                .filter(w -> normDeviceId.equalsIgnoreCase(w.getTargetDeviceId().trim()))
                .findFirst()
                .orElseThrow(() -> new GeneralSecurityException("No WrappedContentKey found for device " + normDeviceId));

        byte[] wrappedBlob = matchingWrap.getWrappedKeyBlob().toByteArray();
        if (wrappedBlob.length != WRAPPED_KEY_BLOB_LENGTH) {
            throw new GeneralSecurityException("Invalid WrappedContentKey blob length: " + wrappedBlob.length);
        }

        byte[] myDevicePub = deriveX25519PublicKeyFromPrivate(myDeviceX25519PrivateKey32Bytes);
        byte[] sharedSecret = x25519SharedSecret(myDeviceX25519PrivateKey32Bytes, ephemeralPub);
        byte[] salt = concat(ephemeralPub, myDevicePub);
        byte[] info = (HKDF_WRAP_PREFIX + normDeviceId).getBytes(StandardCharsets.UTF_8);
        byte[] wrapKey = hkdfSha256(sharedSecret, salt, info, RAW_KEY_LENGTH);

        byte[] wrapNonce = Arrays.copyOfRange(wrappedBlob, 0, CHACHA20_NONCE_LENGTH);
        byte[] encryptedKeyWithTag = Arrays.copyOfRange(wrappedBlob, CHACHA20_NONCE_LENGTH, wrappedBlob.length);
        byte[] wrapAad = (normMsgId + "|" + normDeviceId).getBytes(StandardCharsets.UTF_8);
        byte[] contentKey = chacha20Poly1305Decrypt(wrapKey, wrapNonce, wrapAad, encryptedKeyWithTag);

        try {
            byte[] contentAad = (normMsgId + "|" + normSender + "|" + normReceiver).getBytes(StandardCharsets.UTF_8);
            return chacha20Poly1305Decrypt(contentKey, contentNonce, contentAad, ciphertextWithTag);
        } finally {
            Arrays.fill(contentKey, (byte) 0);
        }
    }

    /**
     * Seals plaintext audit JSON to the offline Root Escrow X25519 public key as [nonce(12) || ciphertext || tag(16)].
     */
    public static SealedEscrowRecord sealAuditEscrowBlob(
            byte[] escrowX25519Pub32Bytes,
            byte[] prevEntryHash32Bytes,
            byte[] plaintextAuditJsonUtf8) throws GeneralSecurityException {
        requireLength(escrowX25519Pub32Bytes, RAW_KEY_LENGTH, "escrowX25519Pub");
        requireLength(prevEntryHash32Bytes, RAW_KEY_LENGTH, "prevEntryHash");
        Objects.requireNonNull(plaintextAuditJsonUtf8, "plaintextAuditJsonUtf8 cannot be null");

        RawKeyPair ephemeral = generateX25519KeyPair();
        byte[] sharedSecret = x25519SharedSecret(ephemeral.privateKey(), escrowX25519Pub32Bytes);
        byte[] salt = concat(ephemeral.publicKey(), escrowX25519Pub32Bytes);
        byte[] sealKey = hkdfSha256(sharedSecret, salt, HKDF_AUDIT_INFO.getBytes(StandardCharsets.UTF_8), RAW_KEY_LENGTH);

        byte[] nonce = randomBytes(CHACHA20_NONCE_LENGTH);
        byte[] ciphertextWithTag = chacha20Poly1305Encrypt(sealKey, nonce, prevEntryHash32Bytes, plaintextAuditJsonUtf8);
        Arrays.fill(sealKey, (byte) 0);
        return new SealedEscrowRecord(ephemeral.publicKey(), concat(nonce, ciphertextWithTag));
    }

    /**
     * Decrypts a sealed_audit_blob offline using the air-gapped Root Escrow X25519 private key.
     */
    public static byte[] openAuditEscrowBlob(
            byte[] escrowX25519Priv32Bytes,
            byte[] ephemeralX25519Pub32Bytes,
            byte[] prevEntryHash32Bytes,
            byte[] sealedAuditBlob) throws GeneralSecurityException {
        requireLength(escrowX25519Priv32Bytes, RAW_KEY_LENGTH, "escrowX25519Priv");
        requireLength(ephemeralX25519Pub32Bytes, RAW_KEY_LENGTH, "ephemeralX25519Pub");
        requireLength(prevEntryHash32Bytes, RAW_KEY_LENGTH, "prevEntryHash");
        Objects.requireNonNull(sealedAuditBlob, "sealedAuditBlob cannot be null");
        if (sealedAuditBlob.length < CHACHA20_NONCE_LENGTH + POLY1305_TAG_LENGTH) {
            throw new GeneralSecurityException("sealedAuditBlob too short");
        }

        byte[] escrowPub = deriveX25519PublicKeyFromPrivate(escrowX25519Priv32Bytes);
        byte[] sharedSecret = x25519SharedSecret(escrowX25519Priv32Bytes, ephemeralX25519Pub32Bytes);
        byte[] salt = concat(ephemeralX25519Pub32Bytes, escrowPub);
        byte[] sealKey = hkdfSha256(sharedSecret, salt, HKDF_AUDIT_INFO.getBytes(StandardCharsets.UTF_8), RAW_KEY_LENGTH);

        byte[] nonce = Arrays.copyOfRange(sealedAuditBlob, 0, CHACHA20_NONCE_LENGTH);
        byte[] ciphertextWithTag = Arrays.copyOfRange(sealedAuditBlob, CHACHA20_NONCE_LENGTH, sealedAuditBlob.length);
        try {
            return chacha20Poly1305Decrypt(sealKey, nonce, prevEntryHash32Bytes, ciphertextWithTag);
        } finally {
            Arrays.fill(sealKey, (byte) 0);
        }
    }

    /**
     * Derives the 32-byte X25519 u-coordinate public key from a 32-byte raw scalar using RFC 7748 basepoint=9.
     */
    public static byte[] deriveX25519PublicKeyFromPrivate(byte[] rawPrivateScalar32Bytes)
            throws GeneralSecurityException {
        byte[] basePoint = new byte[RAW_KEY_LENGTH];
        basePoint[0] = 9;
        return x25519SharedSecret(rawPrivateScalar32Bytes, basePoint);
    }

    public static byte[] sha256(byte[] input) {
        Objects.requireNonNull(input, "input cannot be null");
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static byte[] sha256Utf8(String input) {
        Objects.requireNonNull(input, "input cannot be null");
        return sha256(input.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] chacha20Poly1305Encrypt(byte[] key32, byte[] nonce12, byte[] aad, byte[] plaintext)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key32, "ChaCha20"), new IvParameterSpec(nonce12));
        if (aad != null && aad.length > 0) {
            cipher.updateAAD(aad);
        }
        return cipher.doFinal(plaintext);
    }

    private static byte[] chacha20Poly1305Decrypt(byte[] key32, byte[] nonce12, byte[] aad, byte[] ciphertextWithTag)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key32, "ChaCha20"), new IvParameterSpec(nonce12));
        if (aad != null && aad.length > 0) {
            cipher.updateAAD(aad);
        }
        return cipher.doFinal(ciphertextWithTag);
    }

    private static byte[] extractRawPublicKey(PublicKey publicKey, byte[] expectedSpkiPrefix) {
        byte[] encoded = publicKey.getEncoded();
        if (encoded.length != expectedSpkiPrefix.length + RAW_KEY_LENGTH) {
            throw new IllegalStateException("Unexpected SPKI length: " + encoded.length);
        }
        for (int i = 0; i < expectedSpkiPrefix.length; i++) {
            if (encoded[i] != expectedSpkiPrefix[i]) {
                throw new IllegalStateException("Unexpected SPKI OID prefix");
            }
        }
        return Arrays.copyOfRange(encoded, expectedSpkiPrefix.length, encoded.length);
    }

    public static String canonicalUuid(String rawUuid) {
        if (rawUuid == null || rawUuid.isBlank()) {
            throw new IllegalArgumentException("UUID cannot be null or blank");
        }
        return UUID.fromString(rawUuid.trim()).toString().toLowerCase(Locale.ROOT);
    }

    private static void requireLength(byte[] bytes, int expectedLength, String fieldName) {
        if (bytes == null || bytes.length != expectedLength) {
            throw new IllegalArgumentException(fieldName + " must be exactly " + expectedLength + " bytes");
        }
    }

    private static byte[] randomBytes(int length) {
        byte[] out = new byte[length];
        SECURE_RANDOM.nextBytes(out);
        return out;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] combined = new byte[first.length + second.length];
        System.arraycopy(first, 0, combined, 0, first.length);
        System.arraycopy(second, 0, combined, first.length, second.length);
        return combined;
    }

    /**
     * Length-prefixed transcript writer: UTF8(DOMAIN_TAG) || [uint32_be(len) || field_bytes]...
     */
    public static final class TranscriptWriter {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream(256);

        public TranscriptWriter(String domainTag) {
            Objects.requireNonNull(domainTag, "domainTag cannot be null");
            if (domainTag.isBlank()) {
                throw new IllegalArgumentException("domainTag cannot be blank");
            }
            byte[] tagBytes = domainTag.getBytes(StandardCharsets.UTF_8);
            out.writeBytes(tagBytes);
        }

        public TranscriptWriter writeBytes(byte[] fieldBytes) {
            Objects.requireNonNull(fieldBytes, "fieldBytes cannot be null");
            writeRawUint32Be(fieldBytes.length);
            out.writeBytes(fieldBytes);
            return this;
        }

        public TranscriptWriter writeFixedBytes(byte[] fieldBytes, int expectedLen, String fieldName) {
            requireLength(fieldBytes, expectedLen, fieldName);
            return writeBytes(fieldBytes);
        }

        public TranscriptWriter writeUtf8(String value) {
            Objects.requireNonNull(value, "value cannot be null");
            return writeBytes(value.getBytes(StandardCharsets.UTF_8));
        }

        public TranscriptWriter writeUuid(String uuidString) {
            return writeUtf8(canonicalUuid(uuidString));
        }

        public TranscriptWriter writeInt64Be(long value) {
            byte[] buf = ByteBuffer.allocate(8).putLong(value).array();
            return writeBytes(buf);
        }

        public TranscriptWriter writeUint32Be(int value) {
            byte[] buf = ByteBuffer.allocate(4).putInt(value).array();
            return writeBytes(buf);
        }

        private void writeRawUint32Be(int len) {
            out.write((len >>> 24) & 0xFF);
            out.write((len >>> 16) & 0xFF);
            out.write((len >>> 8) & 0xFF);
            out.write(len & 0xFF);
        }

        public byte[] toByteArray() {
            return out.toByteArray();
        }
    }
}
