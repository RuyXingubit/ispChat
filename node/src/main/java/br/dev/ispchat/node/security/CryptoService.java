package br.dev.ispchat.node.security;

import com.github.f4b6a3.uuid.UuidCreator;
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
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Servico central de criptografia e identidade deterministica do ISP Chat.
 *
 * Responsabilidades:
 * 1. Derivar deterministicamente o UUIDv7 de identidade a partir da chave publica Ed25519 (32 bytes).
 * 2. Gerar UUIDv7 temporais (RFC 9562) para mensagens e eventos via uuid-creator.
 * 3. Converter chaves publicas Ed25519 de 32 bytes raw (vindas do Flutter) para o formato X.509 SPKI nativo do Java 25.
 * 4. Validar matematicamente o vinculo (PublicKey -> UUIDv7), a janela anti-replay e a assinatura digital Ed25519.
 */
@Service
public class CryptoService {

    public static final int ED25519_PUBLIC_KEY_LENGTH = 32;
    public static final int ED25519_SIGNATURE_LENGTH = 64;
    public static final long MAX_TIMESTAMP_DRIFT_MILLIS = 5 * 60 * 1000L; // 5 minutos

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

    public CryptoService() {
        this(Clock.systemUTC());
    }

    public CryptoService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock nao pode ser nulo");
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
