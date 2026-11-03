package br.dev.ispchat.node.security;

import br.dev.ispchat.grpc.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CryptoServiceTest {

    private static final long FIXED_NOW_MILLIS = 1762128000000L;
    private CryptoService cryptoService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(FIXED_NOW_MILLIS), ZoneOffset.UTC);
        cryptoService = new CryptoService(fixedClock);
    }

    @Test
    @DisplayName("Deve gerar UUIDv7 temporal valido via uuid-creator para mensagens e eventos")
    void shouldGenerateValidTemporalUuidV7() {
        UUID id1 = cryptoService.generateTemporalUuidV7();
        UUID id2 = cryptoService.generateTemporalUuidV7();

        assertNotNull(id1);
        assertNotNull(id2);
        assertEquals(7, id1.version());
        assertEquals(2, id1.variant());
        assertNotEquals(id1, id2);
    }

    @Test
    @DisplayName("Deve derivar UUIDv7 de identidade deterministicamente a partir da chave publica Ed25519 de 32 bytes")
    void shouldDeriveDeterministicIdentityUuidV7FromPublicKey() {
        byte[] samplePubKey = new byte[32];
        for (int i = 0; i < 32; i++) {
            samplePubKey[i] = (byte) (i + 1);
        }

        UUID uuidFirstRun = cryptoService.deriveIdentityUuidV7(samplePubKey);
        UUID uuidSecondRun = cryptoService.deriveIdentityUuidV7(samplePubKey);

        assertEquals(uuidFirstRun, uuidSecondRun, "A mesma chave publica deve sempre gerar o exato mesmo UUIDv7");
        assertEquals(7, uuidFirstRun.version(), "Os bits de versao devem respeitar RFC 9562 (v7)");
        assertEquals(2, uuidFirstRun.variant(), "Os bits de variante devem respeitar RFC 9562 (IETF variant 2)");
        assertTrue(cryptoService.verifyIdentityBinding(uuidFirstRun.toString(), samplePubKey));
    }

    @Test
    @DisplayName("Deve validar registro legitimo na DHT e rejeitar tentativa de Spoofing (Account Takeover)")
    void shouldVerifyValidDhtRegistrationAndBlockSpoofing() throws Exception {
        KeyPair victimKeyPair = generateEd25519KeyPair();
        byte[] victimRawPubKey = extractRaw32BytePublicKey(victimKeyPair);
        String victimUuid = cryptoService.deriveIdentityUuidV7(victimRawPubKey).toString();
        String nodeAddress = "node1.ispchat.dev.br";

        byte[] canonicalPayload = cryptoService.buildCanonicalDhtPayload(victimUuid, nodeAddress, FIXED_NOW_MILLIS);
        byte[] validSignature = signEd25519(victimKeyPair.getPrivate(), canonicalPayload);

        // 1. Registro legitimo deve ser aceito
        assertTrue(cryptoService.verifyDhtSessionRegistration(
                victimUuid, nodeAddress, FIXED_NOW_MILLIS, victimRawPubKey, validSignature));

        // 2. Tentativa de Spoofing: Atacante gera seu proprio par de chaves e tenta registrar o UUID da vitima
        KeyPair attackerKeyPair = generateEd25519KeyPair();
        byte[] attackerRawPubKey = extractRaw32BytePublicKey(attackerKeyPair);
        byte[] attackerSignature = signEd25519(attackerKeyPair.getPrivate(), canonicalPayload);

        assertFalse(cryptoService.verifyDhtSessionRegistration(
                victimUuid, nodeAddress, FIXED_NOW_MILLIS, attackerRawPubKey, attackerSignature),
                "Deve bloquear imediatamente tentativa de sequestrar o UUID de outro usuario com chave publica diferente");

        // 3. Tentativa de adulterar o IP/endereco do no mantendo a assinatura da vitima
        assertFalse(cryptoService.verifyDhtSessionRegistration(
                victimUuid, "evil-node.hacker.com", FIXED_NOW_MILLIS, victimRawPubKey, validSignature),
                "Deve rejeitar se o node_address tiver sido alterado em transito");

        // 4. Tentativa de Replay Attack (timestamp expirado ha 10 minutos)
        long expiredTimestamp = FIXED_NOW_MILLIS - (10 * 60 * 1000L);
        byte[] expiredCanonical = cryptoService.buildCanonicalDhtPayload(victimUuid, nodeAddress, expiredTimestamp);
        byte[] expiredSignature = signEd25519(victimKeyPair.getPrivate(), expiredCanonical);

        assertFalse(cryptoService.verifyDhtSessionRegistration(
                victimUuid, nodeAddress, expiredTimestamp, victimRawPubKey, expiredSignature),
                "Deve rejeitar pacotes fora da janela anti-replay de 5 minutos");
    }

    @Test
    @DisplayName("Deve validar envelope de mensagem E2E assinado e bloquear adulteracao de payload")
    void shouldVerifyMessageEnvelopeAndDetectTampering() throws Exception {
        KeyPair senderKeyPair = generateEd25519KeyPair();
        byte[] senderRawPubKey = extractRaw32BytePublicKey(senderKeyPair);
        String senderUuid = cryptoService.deriveIdentityUuidV7(senderRawPubKey).toString();
        String receiverUuid = cryptoService.generateTemporalUuidV7().toString();
        String messageId = cryptoService.generateTemporalUuidV7().toString();
        byte[] encryptedPayload = "ciphertext-e2e-x25519-blob".getBytes(StandardCharsets.UTF_8);

        byte[] canonicalBytes = cryptoService.buildCanonicalMessagePayload(
                messageId,
                senderUuid,
                receiverUuid,
                MessageType.TEXT.getNumber(),
                FIXED_NOW_MILLIS,
                encryptedPayload
        );
        byte[] signature = signEd25519(senderKeyPair.getPrivate(), canonicalBytes);

        // Envelope integro e autentico
        assertTrue(cryptoService.verifyMessageEnvelope(
                messageId,
                senderUuid,
                receiverUuid,
                MessageType.TEXT.getNumber(),
                FIXED_NOW_MILLIS,
                encryptedPayload,
                senderRawPubKey,
                signature
        ));

        // Adulteracao de 1 byte no payload criptografado
        byte[] tamperedPayload = Arrays.copyOf(encryptedPayload, encryptedPayload.length);
        tamperedPayload[0] ^= 0x01;

        assertFalse(cryptoService.verifyMessageEnvelope(
                messageId,
                senderUuid,
                receiverUuid,
                MessageType.TEXT.getNumber(),
                FIXED_NOW_MILLIS,
                tamperedPayload,
                senderRawPubKey,
                signature
        ), "Qualquer modificacao no ciphertext deve invalidar a assinatura Ed25519");
    }

    private static KeyPair generateEd25519KeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        return kpg.generateKeyPair();
    }

    private static byte[] extractRaw32BytePublicKey(KeyPair keyPair) {
        byte[] spkiEncoded = keyPair.getPublic().getEncoded();
        assertEquals(44, spkiEncoded.length, "Chave Ed25519 X.509 SPKI no Java possui 44 bytes (12 header + 32 raw)");
        return Arrays.copyOfRange(spkiEncoded, 12, 44);
    }

    private static byte[] signEd25519(PrivateKey privateKey, byte[] message) throws Exception {
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(privateKey);
        sig.update(message);
        return sig.sign();
    }
}
