package br.dev.ispchat.node.security;

import br.dev.ispchat.common.crypto.CanonicalTranscripts;
import br.dev.ispchat.grpc.DeviceDelegationCert;
import br.dev.ispchat.grpc.DeviceRole;
import br.dev.ispchat.grpc.DocumentType;
import br.dev.ispchat.grpc.HomeNodeClaim;
import br.dev.ispchat.grpc.IdentityReplacementNotice;
import br.dev.ispchat.grpc.PrincipalSigningCert;
import br.dev.ispchat.grpc.RevocationEvent;
import br.dev.ispchat.grpc.UserSessionInfo;
import br.dev.ispchat.grpc.VerificationCredential;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servico central de criptografia e identidade deterministica do ISP Chat.
 *
 * Responsabilidades:
 * 1. Derivar deterministicamente o UUIDv7 de identidade a partir da chave publica Ed25519 (32 bytes).
 * 2. Gerar UUIDv7 temporais (RFC 9562) para mensagens e eventos via uuid-creator.
 * 3. Converter chaves publicas Ed25519 de 32 bytes raw (vindas do Flutter) para o formato X.509 SPKI nativo do Java 25.
 * 4. Validar matematicamente o vinculo (PublicKey -> UUIDv7), a janela anti-replay e a assinatura digital Ed25519.
 * 5. Validar localmente em memoria os certificados V2 da Cadeia de Confianca (PrincipalSigningCert,
 *    VerificationCredential, DeviceDelegationCert, ISPCHAT-DHT-V2, ISPCHAT-HOME-V1, ISPCHAT-MSG-V2).
 */
@Service
public class CryptoService {

    public static final int ED25519_PUBLIC_KEY_LENGTH = 32;
    public static final int ED25519_SIGNATURE_LENGTH = 64;
    public static final long MAX_TIMESTAMP_DRIFT_MILLIS = 5 * 60 * 1000L; // 5 minutos
    public static final long MAX_DELEGATION_TTL_MILLIS = Duration.ofDays(91).toMillis();
    public static final long MAX_DHT_RECORD_AGE_MILLIS = Duration.ofDays(31).toMillis();

    public static final String DHT_DOMAIN_PREFIX = "ISPCHAT-DHT-V1|";
    public static final String MSG_DOMAIN_PREFIX = "ISPCHAT-MSG-V1|";

    /**
     * Prefixo ASN.1 SubjectPublicKeyInfo (RFC 8410) de 12 bytes para chaves publicas Ed25519 de 32 bytes.
     * Sequencia: 30 2a 30 05 06 03 2b 65 70 03 21 00
     */
    private static final byte[] ED25519_SPKI_PREFIX = new byte[]{
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };

    private final Clock clock;
    private final byte[] pinnedRootPublicKey;
    private final String principalFallbackAddress;
    private final Map<ByteBuffer, Long> trustedPrincipalKeysWithExpiry = new ConcurrentHashMap<>();

    public CryptoService() {
        this(Clock.systemUTC(), null, "");
    }

    public CryptoService(Clock clock) {
        this(clock, null, "");
    }

    @Autowired
    public CryptoService(
            @Value("${ispchat.root.public-key:}") String rootPublicKeyConfig,
            @Value("${ispchat.principal.fallback-address:${ispchat.principal.address:}}") String principalFallbackAddress,
            @Value("${ispchat.principal.public-key:}") String configuredPrincipalPublicKey) {
        this(Clock.systemUTC(), parseConfiguredPublicKey(rootPublicKeyConfig), principalFallbackAddress);
        byte[] principalPub = parseConfiguredPublicKey(configuredPrincipalPublicKey);
        if (principalPub != null) {
            trustedPrincipalKeysWithExpiry.put(ByteBuffer.wrap(principalPub), Long.MAX_VALUE);
        }
    }

    public CryptoService(Clock clock, byte[] pinnedRootPublicKey, String principalFallbackAddress) {
        this.clock = Objects.requireNonNull(clock, "clock nao pode ser nulo");
        if (pinnedRootPublicKey != null && pinnedRootPublicKey.length > 0) {
            validateRawPublicKeyLength(pinnedRootPublicKey);
            this.pinnedRootPublicKey = pinnedRootPublicKey.clone();
        } else {
            this.pinnedRootPublicKey = null;
        }
        this.principalFallbackAddress = principalFallbackAddress != null ? principalFallbackAddress.trim() : "";
    }

    public Clock getClock() {
        return clock;
    }

    public String getPrincipalFallbackAddress() {
        return principalFallbackAddress;
    }

    /**
     * Registra uma chave publica online do Principal ja certificada (util para bootstrap local e testes).
     */
    public void registerTrustedPrincipalPublicKey(byte[] principalEd25519Pub, long expiresAtMs) {
        validateRawPublicKeyLength(principalEd25519Pub);
        trustedPrincipalKeysWithExpiry.put(ByteBuffer.wrap(principalEd25519Pub.clone()), expiresAtMs);
    }

    public boolean hasTrustedPrincipalKeys() {
        return pinnedRootPublicKey != null || !trustedPrincipalKeysWithExpiry.isEmpty();
    }

    /**
     * Converte string hexadecimal (64 chars) ou Base64/Base64URL em chave publica Ed25519 de 32 bytes.
     */
    public static byte[] parseConfiguredPublicKey(String rawConfig) {
        if (rawConfig == null || rawConfig.isBlank()) {
            return null;
        }
        String trimmed = rawConfig.trim();
        if (trimmed.length() == 64 && trimmed.matches("^[0-9a-fA-F]{64}$")) {
            return HexFormat.of().parseHex(trimmed);
        }
        byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(trimmed);
        } catch (IllegalArgumentException e) {
            decoded = Base64.getDecoder().decode(trimmed);
        }
        if (decoded.length != ED25519_PUBLIC_KEY_LENGTH) {
            throw new IllegalArgumentException("Chave publica configurada deve decodificar para exatamente 32 bytes");
        }
        return decoded;
    }

    /**
     * Gera um UUIDv7 temporal padrao (RFC 9562) usando uuid-creator para IDs de mensagens e eventos.
     */
    public UUID generateTemporalUuidV7() {
        return UuidCreator.getTimeOrderedEpoch();
    }

    /**
     * Deriva deterministicamente o UUIDv7 de identidade a partir dos 32 bytes da chave publica Ed25519.
     *
     * Algoritmo:
     * 1. hash = SHA-256(ed25519PublicKey32Bytes)
     * 2. bytes = hash[0..15]
     * 3. bytes[6] = (bytes[6] & 0x0F) | 0x70 (RFC 9562 Version 7)
     * 4. bytes[8] = (bytes[8] & 0x3F) | 0x80 (RFC 9562 Variant 2 IETF)
     */
    public UUID deriveIdentityUuidV7(byte[] ed25519PublicKey) {
        validateRawPublicKeyLength(ed25519PublicKey);
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(ed25519PublicKey);
            byte[] uuidBytes = Arrays.copyOf(hash, 16);

            // Forca versao 7 (0111xxxx)
            uuidBytes[6] = (byte) ((uuidBytes[6] & 0x0F) | 0x70);
            // Forca variante IETF RFC 9562 (10xxxxxx)
            uuidBytes[8] = (byte) ((uuidBytes[8] & 0x3F) | 0x80);

            ByteBuffer buffer = ByteBuffer.wrap(uuidBytes);
            long mostSigBits = buffer.getLong();
            long leastSigBits = buffer.getLong();
            return new UUID(mostSigBits, leastSigBits);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel no runtime Java", e);
        }
    }

    /**
     * Verifica se o userUuid informado corresponde matematicamente a chave publica Ed25519 apresentada.
     */
    public boolean verifyIdentityBinding(String userUuid, byte[] ed25519PublicKey) {
        if (userUuid == null || userUuid.isBlank() || ed25519PublicKey == null
                || ed25519PublicKey.length != ED25519_PUBLIC_KEY_LENGTH) {
            return false;
        }
        try {
            UUID parsedUuid = UUID.fromString(userUuid.trim());
            if (parsedUuid.version() != 7 || parsedUuid.variant() != 2) {
                return false;
            }
            UUID derivedUuid = deriveIdentityUuidV7(ed25519PublicKey);
            return derivedUuid.equals(parsedUuid);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Verifica se o timestamp esta dentro da janela de tolerancia (+/- 5 minutos) contra replay attacks.
     */
    public boolean isTimestampFresh(long timestampMillis) {
        long now = clock.millis();
        return Math.abs(now - timestampMillis) <= MAX_TIMESTAMP_DRIFT_MILLIS;
    }

    // =========================================================================
    // VALIDACAO CRIPTOGRAFICA V2 (CHAIN OF TRUST & MULTI-DISPOSITIVO)
    // =========================================================================

    /**
     * Valida um PrincipalSigningCert contra a chave publica Raiz Nivel 0 fixada no no.
     */
    public boolean verifyPrincipalSigningCert(PrincipalSigningCert cert) {
        if (cert == null || pinnedRootPublicKey == null) {
            return false;
        }
        long now = clock.millis();
        if (cert.getIssuedAtMs() > now + MAX_TIMESTAMP_DRIFT_MILLIS || cert.getExpiresAtMs() <= now) {
            return false;
        }
        try {
            byte[] transcript = CanonicalTranscripts.buildPrincipalSigningCertTranscript(cert);
            boolean valid = CanonicalTranscripts.verifyEd25519(
                    pinnedRootPublicKey,
                    transcript,
                    cert.getRootSignature().toByteArray());
            if (valid) {
                byte[] principalPub = cert.getPrincipalEd25519Pub().toByteArray();
                trustedPrincipalKeysWithExpiry.put(ByteBuffer.wrap(principalPub), cert.getExpiresAtMs());
            }
            return valid;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Valida em memoria uma VerificationCredential auto-contida (CPF ou CNPJ) ancorada no PrincipalSigningCert.
     */
    public boolean verifyVerificationCredential(VerificationCredential cred) {
        if (cred == null || cred.getCredentialId().isBlank() || !cred.hasPrincipalCert()) {
            return false;
        }
        if (cred.getDocType() != DocumentType.CPF && cred.getDocType() != DocumentType.CNPJ) {
            return false;
        }
        if (!verifyPrincipalSigningCert(cred.getPrincipalCert())) {
            return false;
        }
        long now = clock.millis();
        if (cred.getIssuedAtMs() > now + MAX_TIMESTAMP_DRIFT_MILLIS || cred.getExpiresAtMs() <= now) {
            return false;
        }
        byte[] rootEdPub = cred.getRootEd25519Pub().toByteArray();
        if (!verifyIdentityBinding(cred.getUserUuid(), rootEdPub)) {
            return false;
        }
        if (cred.getRootX25519Pub().size() != ED25519_PUBLIC_KEY_LENGTH) {
            return false;
        }
        try {
            byte[] transcript = CanonicalTranscripts.buildVerificationCredentialTranscript(cred);
            return CanonicalTranscripts.verifyEd25519(
                    cred.getPrincipalCert().getPrincipalEd25519Pub().toByteArray(),
                    transcript,
                    cred.getPrincipalSignature().toByteArray());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Valida um DeviceDelegationCert (DESKTOP ou ERP_API) assinado pela chave Ed25519 raiz da conta.
     */
    public boolean verifyDeviceDelegationCert(DeviceDelegationCert cert, byte[] expectedAccountRootEd25519Pub) {
        if (cert == null || cert.getCertId().isBlank() || expectedAccountRootEd25519Pub == null
                || expectedAccountRootEd25519Pub.length != ED25519_PUBLIC_KEY_LENGTH) {
            return false;
        }
        if (cert.getRole() != DeviceRole.DESKTOP && cert.getRole() != DeviceRole.ERP_API) {
            return false;
        }
        if (!verifyIdentityBinding(cert.getUserUuid(), expectedAccountRootEd25519Pub)) {
            return false;
        }
        if (!verifyIdentityBinding(cert.getDeviceId(), cert.getDeviceEd25519Pub().toByteArray())) {
            return false;
        }
        if (cert.getDeviceX25519Pub().size() != ED25519_PUBLIC_KEY_LENGTH) {
            return false;
        }
        long now = clock.millis();
        if (cert.getIssuedAtMs() > now + MAX_TIMESTAMP_DRIFT_MILLIS || cert.getExpiresAtMs() <= now) {
            return false;
        }
        if (cert.getExpiresAtMs() - cert.getIssuedAtMs() > MAX_DELEGATION_TTL_MILLIS) {
            return false;
        }
        try {
            byte[] transcript = CanonicalTranscripts.buildDeviceDelegationTranscript(cert);
            return CanonicalTranscripts.verifyEd25519(
                    expectedAccountRootEd25519Pub,
                    transcript,
                    cert.getRootSignature().toByteArray());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Valida um registro de sessao DHT V2 (ISPCHAT-DHT-V2), garantindo autenticidade de device_x25519_pub.
     */
    public boolean verifyDhtSessionV2(
            String userUuid,
            String deviceId,
            String nodeAddress,
            byte[] rootEd25519Pub,
            byte[] deviceEd25519Pub,
            byte[] deviceX25519Pub,
            long timestampMillis,
            byte[] signature,
            DeviceDelegationCert delegationCert,
            VerificationCredential verificationCredential,
            boolean requireFreshTimestamp) {
        if (userUuid == null || userUuid.isBlank() || nodeAddress == null || nodeAddress.isBlank()) {
            return false;
        }
        String effectiveDeviceId = (deviceId == null || deviceId.isBlank()) ? userUuid.trim() : deviceId.trim();
        long now = clock.millis();
        if (requireFreshTimestamp) {
            if (!isTimestampFresh(timestampMillis)) {
                return false;
            }
        } else {
            if (timestampMillis > now + MAX_TIMESTAMP_DRIFT_MILLIS
                    || (now - timestampMillis) > MAX_DHT_RECORD_AGE_MILLIS) {
                return false;
            }
        }

        if (rootEd25519Pub == null || rootEd25519Pub.length != ED25519_PUBLIC_KEY_LENGTH
                || deviceEd25519Pub == null || deviceEd25519Pub.length != ED25519_PUBLIC_KEY_LENGTH
                || deviceX25519Pub == null || deviceX25519Pub.length != ED25519_PUBLIC_KEY_LENGTH) {
            return false;
        }
        if (!verifyIdentityBinding(userUuid, rootEd25519Pub)
                || !verifyIdentityBinding(effectiveDeviceId, deviceEd25519Pub)) {
            return false;
        }

        boolean hasCred = verificationCredential != null && !verificationCredential.getCredentialId().isBlank();
        boolean hasDelCert = delegationCert != null && !delegationCert.getCertId().isBlank();
        boolean isSecondaryDevice = !userUuid.trim().equalsIgnoreCase(effectiveDeviceId);

        if (hasCred) {
            if (!verifyVerificationCredential(verificationCredential)
                    || !verificationCredential.getUserUuid().equalsIgnoreCase(userUuid.trim())
                    || !Arrays.equals(verificationCredential.getRootEd25519Pub().toByteArray(), rootEd25519Pub)) {
                return false;
            }
        }

        if (isSecondaryDevice || hasDelCert) {
            if (!hasCred || !hasDelCert) {
                return false; // Contas nao verificadas jamais podem registrar dispositivos delegados
            }
            if (!verifyDeviceDelegationCert(delegationCert, rootEd25519Pub)
                    || !delegationCert.getUserUuid().equalsIgnoreCase(userUuid.trim())
                    || !delegationCert.getDeviceId().equalsIgnoreCase(effectiveDeviceId)
                    || !Arrays.equals(delegationCert.getDeviceEd25519Pub().toByteArray(), deviceEd25519Pub)
                    || !Arrays.equals(delegationCert.getDeviceX25519Pub().toByteArray(), deviceX25519Pub)) {
                return false;
            }
            if (verificationCredential.getDocType() == DocumentType.CPF
                    && delegationCert.getRole() == DeviceRole.ERP_API) {
                return false;
            }
        } else if (!Arrays.equals(rootEd25519Pub, deviceEd25519Pub)) {
            return false;
        }

        try {
            byte[] transcript = CanonicalTranscripts.buildDhtSessionV2Transcript(
                    userUuid,
                    effectiveDeviceId,
                    nodeAddress,
                    rootEd25519Pub,
                    deviceEd25519Pub,
                    deviceX25519Pub,
                    timestampMillis);
            return CanonicalTranscripts.verifyEd25519(deviceEd25519Pub, transcript, signature);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Valida anti-MITM uma sessao retornada por pares remotos da DHT (FindValue / FindUserSessions).
     * Exige assinatura valida (ISPCHAT-DHT-V2 vinculando x25519_public_key, ou ISPCHAT-DHT-V1 apenas em legado V1 sem campos V2).
     */
    public boolean verifyRemoteSessionInfo(String targetUuid, UserSessionInfo session) {
        if (targetUuid == null || targetUuid.isBlank() || session == null
                || session.getNodeAddress().isBlank() || session.getSessionSignature().isEmpty()) {
            return false;
        }
        String effectiveDeviceId = session.getDeviceId().isBlank() ? targetUuid.trim() : session.getDeviceId().trim();
        byte[] rootPub = session.getRootEd25519Pub().isEmpty()
                ? session.getEd25519PublicKey().toByteArray()
                : session.getRootEd25519Pub().toByteArray();
        byte[] devicePub = session.getDeviceEd25519Pub().isEmpty()
                ? session.getEd25519PublicKey().toByteArray()
                : session.getDeviceEd25519Pub().toByteArray();

        boolean hasV2Metadata = !session.getDeviceId().isBlank()
                || !session.getRootEd25519Pub().isEmpty()
                || !session.getDeviceEd25519Pub().isEmpty()
                || (session.hasDelegationCert() && !session.getDelegationCert().getCertId().isBlank())
                || (session.hasVerificationCredential() && !session.getVerificationCredential().getCredentialId().isBlank());

        if (session.getX25519PublicKey().size() == ED25519_PUBLIC_KEY_LENGTH) {
            boolean v2Valid = verifyDhtSessionV2(
                    targetUuid,
                    effectiveDeviceId,
                    session.getNodeAddress(),
                    rootPub,
                    devicePub,
                    session.getX25519PublicKey().toByteArray(),
                    session.getTimestamp(),
                    session.getSessionSignature().toByteArray(),
                    session.hasDelegationCert() ? session.getDelegationCert() : null,
                    session.hasVerificationCredential() ? session.getVerificationCredential() : null,
                    false);
            if (v2Valid) {
                return true;
            }
        }

        if (hasV2Metadata) {
            return false; // Impede downgrade de V2 para V1 quando x25519_public_key foi adulterada
        }

        long now = clock.millis();
        if (session.getTimestamp() > now + MAX_TIMESTAMP_DRIFT_MILLIS
                || (now - session.getTimestamp()) > MAX_DHT_RECORD_AGE_MILLIS) {
            return false;
        }
        if (!verifyIdentityBinding(targetUuid, session.getEd25519PublicKey().toByteArray())) {
            return false;
        }
        byte[] v1Payload = buildCanonicalDhtPayload(targetUuid, session.getNodeAddress(), session.getTimestamp());
        return verifyEd25519Signature(
                session.getEd25519PublicKey().toByteArray(),
                v1Payload,
                session.getSessionSignature().toByteArray());
    }

    /**
     * Valida HomeNodeClaim (ISPCHAT-HOME-V1).
     * Regras:
     * - signed_by_principal == true: exige assinatura valida de uma chave online certificada do Principal.
     * - signed_by_principal == false: permitido EXCLUSIVAMENTE em routing_epoch == 0 apontando para o
     *   Principal Fallback Node configurado, assinado pela chave raiz do proprio usuario.
     */
    public boolean verifyHomeNodeClaim(
            HomeNodeClaim claim,
            VerificationCredential optionalCredential,
            byte[] optionalUserRootEd25519Pub) {
        if (claim == null || claim.getUserUuid().isBlank() || claim.getHomeNodeAddress().isBlank()
                || claim.getRoutingEpoch() < 0) {
            return false;
        }
        long now = clock.millis();
        if (claim.getIssuedAtMs() > now + MAX_TIMESTAMP_DRIFT_MILLIS) {
            return false;
        }
        if (optionalCredential != null && !optionalCredential.getCredentialId().isBlank()) {
            verifyVerificationCredential(optionalCredential);
        }

        byte[] transcript;
        try {
            transcript = CanonicalTranscripts.buildHomeNodeClaimTranscript(claim);
        } catch (RuntimeException e) {
            return false;
        }

        byte[] sig = claim.getClaimSignature().toByteArray();
        if (claim.getSignedByPrincipal()) {
            return verifyAgainstTrustedPrincipalKeys(transcript, sig);
        }

        // Auto-assinatura pelo usuario permitida somente em routing_epoch == 0 para o Fallback Node oficial
        if (claim.getRoutingEpoch() != 0 || principalFallbackAddress.isBlank()) {
            return false;
        }
        if (!claim.getHomeNodeAddress().trim().equalsIgnoreCase(principalFallbackAddress)) {
            return false;
        }
        if (optionalUserRootEd25519Pub == null || !verifyIdentityBinding(claim.getUserUuid(), optionalUserRootEd25519Pub)) {
            return false;
        }
        return CanonicalTranscripts.verifyEd25519(optionalUserRootEd25519Pub, transcript, sig);
    }

    /**
     * Valida um envelope V2 (ISPCHAT-MSG-V2) assinado pela chave do dispositivo ativo.
     */
    public boolean verifyMessageEnvelopeV2(
            String messageId,
            String senderUuid,
            String senderDeviceId,
            String receiverUuid,
            int messageTypeNumber,
            String refMessageId,
            byte[] encryptedPayload,
            long timestampMillis,
            byte[] activeDeviceEd25519Pub,
            byte[] signature) {
        if (messageId == null || messageId.isBlank()
                || senderUuid == null || senderUuid.isBlank()
                || receiverUuid == null || receiverUuid.isBlank()) {
            return false;
        }
        if (!isTimestampFresh(timestampMillis)) {
            return false;
        }
        String effectiveDeviceId = (senderDeviceId == null || senderDeviceId.isBlank())
                ? senderUuid.trim()
                : senderDeviceId.trim();
        if (!verifyIdentityBinding(effectiveDeviceId, activeDeviceEd25519Pub)) {
            return false;
        }
        try {
            byte[] transcript = CanonicalTranscripts.buildMessageEnvelopeV2Transcript(
                    messageId,
                    senderUuid,
                    effectiveDeviceId,
                    receiverUuid,
                    messageTypeNumber,
                    refMessageId,
                    encryptedPayload,
                    timestampMillis);
            return CanonicalTranscripts.verifyEd25519(activeDeviceEd25519Pub, transcript, signature);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public boolean verifyIdentityReplacementNotice(IdentityReplacementNotice notice) {
        if (notice == null || notice.getRevocationId().isBlank()
                || notice.getOldUserUuid().isBlank() || notice.getNewUserUuid().isBlank()) {
            return false;
        }
        try {
            byte[] transcript = CanonicalTranscripts.buildIdentityReplacementTranscript(notice);
            return verifyAgainstTrustedPrincipalKeys(transcript, notice.getPrincipalSignature().toByteArray());
        } catch (RuntimeException e) {
            return false;
        }
    }

    public boolean verifyRevocationEvent(RevocationEvent event) {
        if (event == null || event.getRevocationId().isBlank() || event.getTargetUuid().isBlank()) {
            return false;
        }
        try {
            byte[] transcript = CanonicalTranscripts.buildRevocationEventTranscript(event);
            if (!verifyAgainstTrustedPrincipalKeys(transcript, event.getPrincipalSignature().toByteArray())) {
                return false;
            }
            if (event.hasReplacementNotice() && !event.getReplacementNotice().getRevocationId().isBlank()) {
                return verifyIdentityReplacementNotice(event.getReplacementNotice());
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean verifyAgainstTrustedPrincipalKeys(byte[] transcript, byte[] signature) {
        long now = clock.millis();
        for (Map.Entry<ByteBuffer, Long> entry : trustedPrincipalKeysWithExpiry.entrySet()) {
            if (entry.getValue() > now) {
                byte[] pubKey = entry.getKey().array();
                if (CanonicalTranscripts.verifyEd25519(pubKey, transcript, signature)) {
                    return true;
                }
            }
        }
        return false;
    }

    // =========================================================================
    // VALIDACAO CRIPTOGRAFICA V1 (COMPATIBILIDADE SINGLE-DEVICE UNVERIFIED)
    // =========================================================================

    /**
     * Constroi os bytes canonicos para assinatura de registro de sessao / DHT Store:
     * "ISPCHAT-DHT-V1|" + userUuid + "|" + nodeAddress + "|" + timestampMillis
     */
    public byte[] buildCanonicalDhtPayload(String userUuid, String nodeAddress, long timestampMillis) {
        Objects.requireNonNull(userUuid, "userUuid nao pode ser nulo");
        Objects.requireNonNull(nodeAddress, "nodeAddress nao pode ser nulo");
        String canonical = DHT_DOMAIN_PREFIX + userUuid.trim().toLowerCase() + "|" + nodeAddress.trim() + "|" + timestampMillis;
        return canonical.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Constroi os bytes canonicos para assinatura de envelope de mensagem / sinalizacao:
     * "ISPCHAT-MSG-V1|" + messageId + "|" + senderUuid + "|" + receiverUuid + "|" + messageTypeNumber + "|" + timestampMillis + "|" + encryptedPayload
     */
    public byte[] buildCanonicalMessagePayload(
            String messageId,
            String senderUuid,
            String receiverUuid,
            int messageTypeNumber,
            long timestampMillis,
            byte[] encryptedPayload) {
        Objects.requireNonNull(messageId, "messageId nao pode ser nulo");
        Objects.requireNonNull(senderUuid, "senderUuid nao pode ser nulo");
        Objects.requireNonNull(receiverUuid, "receiverUuid nao pode ser nulo");
        byte[] payloadBytes = encryptedPayload != null ? encryptedPayload : new byte[0];

        String header = MSG_DOMAIN_PREFIX
                + messageId.trim().toLowerCase() + "|"
                + senderUuid.trim().toLowerCase() + "|"
                + receiverUuid.trim().toLowerCase() + "|"
                + messageTypeNumber + "|"
                + timestampMillis + "|";
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(headerBytes.length + payloadBytes.length);
        buffer.put(headerBytes);
        buffer.put(payloadBytes);
        return buffer.array();
    }

    /**
     * Valida de ponta a ponta um registro de sessao na DHT:
     * 1. Janela de frescor do timestamp (+/- 5 min)
     * 2. Vinculo matematico deriveIdentityUuidV7(publicKey) == userUuid (Anti-Spoofing)
     * 3. Assinatura digital Ed25519 sobre o payload canonico ISPCHAT-DHT-V1
     */
    public boolean verifyDhtSessionRegistration(
            String userUuid,
            String nodeAddress,
            long timestampMillis,
            byte[] rawEd25519PublicKey,
            byte[] signature) {
        if (nodeAddress == null || nodeAddress.isBlank()) {
            return false;
        }
        if (!isTimestampFresh(timestampMillis)) {
            return false;
        }
        if (!verifyIdentityBinding(userUuid, rawEd25519PublicKey)) {
            return false;
        }
        byte[] canonicalBytes = buildCanonicalDhtPayload(userUuid, nodeAddress, timestampMillis);
        return verifyEd25519Signature(rawEd25519PublicKey, canonicalBytes, signature);
    }

    /**
     * Valida de ponta a ponta um envelope de mensagem/sinalizacao enviado pelo cliente ou roteado entre nos:
     * 1. Janela de frescor do timestamp (+/- 5 min)
     * 2. Vinculo matematico deriveIdentityUuidV7(senderPublicKey) == senderUuid (Anti-Spoofing)
     * 3. Assinatura digital Ed25519 sobre o payload canonico ISPCHAT-MSG-V1
     */
    public boolean verifyMessageEnvelope(
            String messageId,
            String senderUuid,
            String receiverUuid,
            int messageTypeNumber,
            long timestampMillis,
            byte[] encryptedPayload,
            byte[] rawSenderEd25519PublicKey,
            byte[] signature) {
        if (messageId == null || messageId.isBlank() || receiverUuid == null || receiverUuid.isBlank()) {
            return false;
        }
        if (!isTimestampFresh(timestampMillis)) {
            return false;
        }
        if (!verifyIdentityBinding(senderUuid, rawSenderEd25519PublicKey)) {
            return false;
        }
        byte[] canonicalBytes = buildCanonicalMessagePayload(
                messageId, senderUuid, receiverUuid, messageTypeNumber, timestampMillis, encryptedPayload);
        return verifyEd25519Signature(rawSenderEd25519PublicKey, canonicalBytes, signature);
    }

    /**
     * Verifica uma assinatura digital Ed25519 usando o provedor nativo do Java 25.
     */
    public boolean verifyEd25519Signature(byte[] rawPublicKey32Bytes, byte[] messageBytes, byte[] signatureBytes) {
        if (rawPublicKey32Bytes == null || rawPublicKey32Bytes.length != ED25519_PUBLIC_KEY_LENGTH) {
            return false;
        }
        if (signatureBytes == null || signatureBytes.length != ED25519_SIGNATURE_LENGTH || messageBytes == null) {
            return false;
        }
        try {
            PublicKey publicKey = toJavaEd25519PublicKey(rawPublicKey32Bytes);
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(messageBytes);
            return verifier.verify(signatureBytes);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    /**
     * Converte os 32 bytes puros da chave publica Ed25519 para um PublicKey JCA nativo do Java 25
     * prefixando os 12 bytes do cabecalho ASN.1 SPKI (RFC 8410).
     */
    public PublicKey toJavaEd25519PublicKey(byte[] rawPublicKey32Bytes) throws GeneralSecurityException {
        validateRawPublicKeyLength(rawPublicKey32Bytes);
        byte[] spkiEncoded = new byte[ED25519_SPKI_PREFIX.length + ED25519_PUBLIC_KEY_LENGTH];
        System.arraycopy(ED25519_SPKI_PREFIX, 0, spkiEncoded, 0, ED25519_SPKI_PREFIX.length);
        System.arraycopy(rawPublicKey32Bytes, 0, spkiEncoded, ED25519_SPKI_PREFIX.length, ED25519_PUBLIC_KEY_LENGTH);

        KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");
        return keyFactory.generatePublic(new X509EncodedKeySpec(spkiEncoded));
    }

    private void validateRawPublicKeyLength(byte[] ed25519PublicKey) {
        if (ed25519PublicKey == null || ed25519PublicKey.length != ED25519_PUBLIC_KEY_LENGTH) {
            throw new IllegalArgumentException("Chave publica Ed25519 deve conter exatamente 32 bytes");
        }
    }
}
