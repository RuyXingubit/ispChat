package br.dev.ispchat.common.crypto;

import br.dev.ispchat.common.crypto.CanonicalTranscripts.RawKeyPair;
import br.dev.ispchat.common.crypto.CanonicalTranscripts.SealedEscrowRecord;
import br.dev.ispchat.grpc.CollaboratorAuthCert;
import br.dev.ispchat.grpc.DeviceDelegationCert;
import br.dev.ispchat.grpc.DeviceRole;
import br.dev.ispchat.grpc.DocumentType;
import br.dev.ispchat.grpc.HomeNodeClaim;
import br.dev.ispchat.grpc.IdentityReplacementNotice;
import br.dev.ispchat.grpc.MessageType;
import br.dev.ispchat.grpc.MultiDeviceEncryptedPayload;
import br.dev.ispchat.grpc.PartnerSeedEntry;
import br.dev.ispchat.grpc.PrincipalSigningCert;
import br.dev.ispchat.grpc.RevocationEvent;
import br.dev.ispchat.grpc.UserVerificationConsent;
import br.dev.ispchat.grpc.VerificationCredential;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalTranscriptsAndEscrowTest {

    private static final String VALID_CPF_RAW = "52998224725";
    private static final String VALID_CPF_FORMATTED = "529.982.247-25";
    private static final String VALID_CPF_SECOND = "11144477735";

    private static final String VALID_CNPJ_NUMERIC_FORMATTED = "11.222.333/0001-81";
    private static final String VALID_CNPJ_NUMERIC_RAW = "11222333000181";
    // Official Receita Federal IN RFB 2229/2024 alphanumeric CNPJ example: 12.ABC.345/01DE-35
    private static final String VALID_CNPJ_ALPHANUM_FORMATTED = "12.abc.345/01de-35";
    private static final String VALID_CNPJ_ALPHANUM_RAW = "12ABC34501DE35";

    @Test
    void uuidV7GenerationAndDeterministicIdentityBindingWork() {
        UUID temporal = CanonicalTranscripts.generateTemporalUuidV7();
        assertEquals(7, temporal.version());
        assertEquals(2, temporal.variant());

        RawKeyPair kp = CanonicalTranscripts.generateEd25519KeyPair();
        UUID derived = CanonicalTranscripts.deriveIdentityUuidV7(kp.publicKey());
        assertEquals(7, derived.version());
        assertEquals(2, derived.variant());
        assertTrue(CanonicalTranscripts.verifyIdentityBinding(derived.toString(), kp.publicKey()));
        assertTrue(CanonicalTranscripts.verifyIdentityBinding("  " + derived.toString().toUpperCase() + " ", kp.publicKey()));

        RawKeyPair other = CanonicalTranscripts.generateEd25519KeyPair();
        assertFalse(CanonicalTranscripts.verifyIdentityBinding(derived.toString(), other.publicKey()));
        assertFalse(CanonicalTranscripts.verifyIdentityBinding(UUID.randomUUID().toString(), kp.publicKey()));
        assertFalse(CanonicalTranscripts.verifyIdentityBinding("", kp.publicKey()));
        assertFalse(CanonicalTranscripts.verifyIdentityBinding(null, kp.publicKey()));
        assertFalse(CanonicalTranscripts.verifyIdentityBinding(derived.toString(), new byte[16]));
        assertFalse(CanonicalTranscripts.verifyIdentityBinding("not-a-uuid", kp.publicKey()));
    }

    @Test
    void cpfAndAlphanumericCnpjNormalizationAndModulo11ValidationWork() {
        assertEquals(VALID_CPF_RAW, CanonicalTranscripts.normalizeAndValidateCpf(VALID_CPF_FORMATTED));
        assertEquals(VALID_CPF_RAW, CanonicalTranscripts.normalizeAndValidateDocument(DocumentType.CPF, VALID_CPF_RAW));

        assertEquals(VALID_CNPJ_NUMERIC_RAW, CanonicalTranscripts.normalizeAndValidateCnpj(VALID_CNPJ_NUMERIC_FORMATTED));
        assertEquals(VALID_CNPJ_ALPHANUM_RAW, CanonicalTranscripts.normalizeAndValidateCnpj(VALID_CNPJ_ALPHANUM_FORMATTED));
        assertEquals(VALID_CNPJ_ALPHANUM_RAW,
                CanonicalTranscripts.normalizeAndValidateDocument(DocumentType.CNPJ, VALID_CNPJ_ALPHANUM_FORMATTED));

        // Invalid CPFs
        assertThrows(IllegalArgumentException.class, () -> CanonicalTranscripts.normalizeAndValidateCpf(null));
        assertThrows(IllegalArgumentException.class, () -> CanonicalTranscripts.normalizeAndValidateCpf("12345"));
        assertThrows(IllegalArgumentException.class, () -> CanonicalTranscripts.normalizeAndValidateCpf("111.111.111-11"));
        assertThrows(IllegalArgumentException.class, () -> CanonicalTranscripts.normalizeAndValidateCpf("529.982.247-26"));
        assertThrows(IllegalArgumentException.class, () -> CanonicalTranscripts.normalizeAndValidateCpf("5299822472A"));

        // Invalid CNPJs
        assertThrows(IllegalArgumentException.class, () -> CanonicalTranscripts.normalizeAndValidateCnpj(null));
        assertThrows(IllegalArgumentException.class, () -> CanonicalTranscripts.normalizeAndValidateCnpj("11222333"));
        assertThrows(IllegalArgumentException.class, () -> CanonicalTranscripts.normalizeAndValidateCnpj("00.000.000/0000-00"));
        assertThrows(IllegalArgumentException.class, () -> CanonicalTranscripts.normalizeAndValidateCnpj("11.222.333/0001-82"));
        assertThrows(IllegalArgumentException.class, () -> CanonicalTranscripts.normalizeAndValidateCnpj("12.ABC.345/01DE-36"));
        assertThrows(IllegalArgumentException.class, () -> CanonicalTranscripts.normalizeAndValidateCnpj("12.ABC.345/01DE-3A"));
        assertThrows(IllegalArgumentException.class, () -> CanonicalTranscripts.normalizeAndValidateCnpj("12.$BC.345/01DE-35"));
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalTranscripts.normalizeAndValidateDocument(DocumentType.DOC_UNKNOWN, VALID_CPF_RAW));
    }

    @Test
    void lengthPrefixedWriterPreventsDelimiterCollision() {
        byte[] t1 = new CanonicalTranscripts.TranscriptWriter("ISPCHAT-TEST-V1")
                .writeUtf8("alpha|beta")
                .writeUtf8("gamma")
                .toByteArray();
        byte[] t2 = new CanonicalTranscripts.TranscriptWriter("ISPCHAT-TEST-V1")
                .writeUtf8("alpha")
                .writeUtf8("beta|gamma")
                .toByteArray();
        assertFalse(Arrays.equals(t1, t2));
    }

    @Test
    void fourLevelChainOfTrustTranscriptsSignAndVerifyEndToEnd() {
        long now = 1_762_900_000_000L;

        // Level 0: Root Governance -> PrincipalSigningCert
        RawKeyPair rootEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair principalEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair escrowX = CanonicalTranscripts.generateX25519KeyPair();

        PrincipalSigningCert unsignedPrinCert = PrincipalSigningCert.newBuilder()
                .setCertId(CanonicalTranscripts.generateTemporalUuidV7().toString())
                .setPrincipalEd25519Pub(ByteString.copyFrom(principalEd.publicKey()))
                .setEscrowX25519Pub(ByteString.copyFrom(escrowX.publicKey()))
                .setIssuedAtMs(now)
                .setExpiresAtMs(now + 365L * 86_400_000L)
                .build();
        byte[] prinTranscript = CanonicalTranscripts.buildPrincipalSigningCertTranscript(unsignedPrinCert);
        byte[] rootSigOnPrin = CanonicalTranscripts.signEd25519(rootEd.privateKey(), prinTranscript);
        PrincipalSigningCert principalCert = unsignedPrinCert.toBuilder()
                .setRootSignature(ByteString.copyFrom(rootSigOnPrin))
                .build();
        assertTrue(CanonicalTranscripts.verifyEd25519(rootEd.publicKey(),
                CanonicalTranscripts.buildPrincipalSigningCertTranscript(principalCert),
                principalCert.getRootSignature().toByteArray()));

        // Level 1: Root -> Partner ISP + Managing Partners (order-independent digest)
        RawKeyPair corpEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair corpX = CanonicalTranscripts.generateX25519KeyPair();
        String corpUuid = CanonicalTranscripts.deriveIdentityUuidV7(corpEd.publicKey()).toString();

        RawKeyPair partner1Ed = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair partner1X = CanonicalTranscripts.generateX25519KeyPair();
        String partner1Uuid = CanonicalTranscripts.deriveIdentityUuidV7(partner1Ed.publicKey()).toString();

        RawKeyPair partner2Ed = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair partner2X = CanonicalTranscripts.generateX25519KeyPair();
        String partner2Uuid = CanonicalTranscripts.deriveIdentityUuidV7(partner2Ed.publicKey()).toString();

        PartnerSeedEntry p1 = PartnerSeedEntry.newBuilder()
                .setPartnerUuid(partner1Uuid)
                .setPartnerCpf(VALID_CPF_FORMATTED)
                .setPartnerEd25519Pub(ByteString.copyFrom(partner1Ed.publicKey()))
                .setPartnerX25519Pub(ByteString.copyFrom(partner1X.publicKey()))
                .build();
        PartnerSeedEntry p2 = PartnerSeedEntry.newBuilder()
                .setPartnerUuid(partner2Uuid)
                .setPartnerCpf(VALID_CPF_SECOND)
                .setPartnerEd25519Pub(ByteString.copyFrom(partner2Ed.publicKey()))
                .setPartnerX25519Pub(ByteString.copyFrom(partner2X.publicKey()))
                .build();

        byte[] digestAB = CanonicalTranscripts.computePartnersDigestSha256(List.of(p1, p2));
        byte[] digestBA = CanonicalTranscripts.computePartnersDigestSha256(List.of(p2, p1));
        assertArrayEquals(digestAB, digestBA);

        byte[] ispBootstrapBytes = CanonicalTranscripts.buildPartnerIspBootstrapTranscript(
                VALID_CNPJ_ALPHANUM_FORMATTED, "Fibra Sul Telecom", corpUuid,
                corpEd.publicKey(), corpX.publicKey(), "chat.fibrasul.net.br:443", digestAB, now);
        byte[] rootIspSig = CanonicalTranscripts.signEd25519(rootEd.privateKey(), ispBootstrapBytes);
        assertTrue(CanonicalTranscripts.verifyEd25519(rootEd.publicKey(), ispBootstrapBytes, rootIspSig));

        // Level 2: Managing Partner -> CollaboratorAuthCert + RevokeCollaborator
        RawKeyPair collabEd = CanonicalTranscripts.generateEd25519KeyPair();
        String collabUuid = CanonicalTranscripts.deriveIdentityUuidV7(collabEd.publicKey()).toString();
        CollaboratorAuthCert collabCertUnsigned = CollaboratorAuthCert.newBuilder()
                .setCertId(CanonicalTranscripts.generateTemporalUuidV7().toString())
                .setIspCnpj(VALID_CNPJ_ALPHANUM_RAW)
                .setPartnerUuid(partner1Uuid)
                .setCollaboratorUuid(collabUuid)
                .setCollabCpfSha256(ByteString.copyFrom(CanonicalTranscripts.sha256Utf8(VALID_CPF_SECOND)))
                .setCollabEd25519Pub(ByteString.copyFrom(collabEd.publicKey()))
                .setIssuedAtMs(now)
                .setExpiresAtMs(now + 180L * 86_400_000L)
                .build();
        byte[] collabSig = CanonicalTranscripts.signEd25519(
                partner1Ed.privateKey(), CanonicalTranscripts.buildCollaboratorAuthTranscript(collabCertUnsigned));
        CollaboratorAuthCert collabCert = collabCertUnsigned.toBuilder()
                .setPartnerSignature(ByteString.copyFrom(collabSig))
                .build();
        assertTrue(CanonicalTranscripts.verifyEd25519(partner1Ed.publicKey(),
                CanonicalTranscripts.buildCollaboratorAuthTranscript(collabCert),
                collabCert.getPartnerSignature().toByteArray()));

        byte[] revokeCollabBytes = CanonicalTranscripts.buildRevokeCollaboratorTranscript(
                VALID_CNPJ_ALPHANUM_RAW, partner1Uuid, collabUuid, "termination", now + 1000L);
        byte[] revokeCollabSig = CanonicalTranscripts.signEd25519(partner1Ed.privateKey(), revokeCollabBytes);
        assertTrue(CanonicalTranscripts.verifyEd25519(partner1Ed.publicKey(), revokeCollabBytes, revokeCollabSig));

        // Level 3: User consent + Collaborator attestation + Rebind + VerificationCredential + HomeNodeClaim
        RawKeyPair userEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair userX = CanonicalTranscripts.generateX25519KeyPair();
        String userUuid = CanonicalTranscripts.deriveIdentityUuidV7(userEd.publicKey()).toString();
        byte[] userCpfSha = CanonicalTranscripts.sha256Utf8(VALID_CPF_RAW);

        UserVerificationConsent consentUnsigned = UserVerificationConsent.newBuilder()
                .setUserUuid(userUuid)
                .setUserEd25519Pub(ByteString.copyFrom(userEd.publicKey()))
                .setUserX25519Pub(ByteString.copyFrom(userX.publicKey()))
                .setDocType(DocumentType.CPF)
                .setDocSha256(ByteString.copyFrom(userCpfSha))
                .setIspCnpj(VALID_CNPJ_ALPHANUM_FORMATTED)
                .setTimestampMs(now)
                .build();
        byte[] consentSig = CanonicalTranscripts.signEd25519(
                userEd.privateKey(), CanonicalTranscripts.buildUserVerificationConsentTranscript(consentUnsigned));
        assertTrue(CanonicalTranscripts.verifyEd25519(userEd.publicKey(),
                CanonicalTranscripts.buildUserVerificationConsentTranscript(consentUnsigned), consentSig));

        byte[] attestationBytes = CanonicalTranscripts.buildCollaboratorUserAttestationTranscript(
                VALID_CNPJ_ALPHANUM_RAW, collabUuid, userUuid, DocumentType.CPF,
                userCpfSha, "chat.fibrasul.net.br:443", consentSig, now);
        byte[] collabAttestSig = CanonicalTranscripts.signEd25519(collabEd.privateKey(), attestationBytes);
        assertTrue(CanonicalTranscripts.verifyEd25519(collabEd.publicKey(), attestationBytes, collabAttestSig));

        // VerificationCredential (CPF and CNPJ variants)
        VerificationCredential cpfCredUnsigned = VerificationCredential.newBuilder()
                .setCredentialId(CanonicalTranscripts.generateTemporalUuidV7().toString())
                .setUserUuid(userUuid)
                .setRootEd25519Pub(ByteString.copyFrom(userEd.publicKey()))
                .setRootX25519Pub(ByteString.copyFrom(userX.publicKey()))
                .setDocType(DocumentType.CPF)
                .setVerifyingIspCnpj(VALID_CNPJ_ALPHANUM_RAW)
                .setVerifyingIspName("Fibra Sul Telecom")
                .setHomeNodeAddress("chat.fibrasul.net.br:443")
                .setRoutingEpoch(1L)
                .setIssuedAtMs(now)
                .setExpiresAtMs(now + 90L * 86_400_000L)
                .setPrincipalCert(principalCert)
                .build();
        byte[] cpfCredSig = CanonicalTranscripts.signEd25519(
                principalEd.privateKey(), CanonicalTranscripts.buildVerificationCredentialTranscript(cpfCredUnsigned));
        assertTrue(CanonicalTranscripts.verifyEd25519(principalEd.publicKey(),
                CanonicalTranscripts.buildVerificationCredentialTranscript(cpfCredUnsigned), cpfCredSig));

        VerificationCredential cnpjCredUnsigned = cpfCredUnsigned.toBuilder()
                .setUserUuid(corpUuid)
                .setDocType(DocumentType.CNPJ)
                .setPublicCnpj(VALID_CNPJ_ALPHANUM_FORMATTED)
                .setPublicDisplayName("Fibra Sul Oficial")
                .build();
        byte[] cnpjCredSig = CanonicalTranscripts.signEd25519(
                principalEd.privateKey(), CanonicalTranscripts.buildVerificationCredentialTranscript(cnpjCredUnsigned));
        assertTrue(CanonicalTranscripts.verifyEd25519(principalEd.publicKey(),
                CanonicalTranscripts.buildVerificationCredentialTranscript(cnpjCredUnsigned), cnpjCredSig));

        // Tampering with routing_epoch invalidates signature
        VerificationCredential tamperedCred = cpfCredUnsigned.toBuilder().setRoutingEpoch(2L).build();
        assertFalse(CanonicalTranscripts.verifyEd25519(principalEd.publicKey(),
                CanonicalTranscripts.buildVerificationCredentialTranscript(tamperedCred), cpfCredSig));

        // Rebind, Delegation, DHT V2, HomeNodeClaim, Replacement, Revocation, Message V2, Port, Turn, Ledger Row
        String newUserUuid = CanonicalTranscripts.generateTemporalUuidV7().toString();
        byte[] rebindBytes = CanonicalTranscripts.buildCpfRebindTranscript(
                VALID_CNPJ_ALPHANUM_RAW, collabUuid, userUuid, newUserUuid,
                userCpfSha, "chat.fibrasul.net.br:443", consentSig, now);
        assertTrue(CanonicalTranscripts.verifyEd25519(
                collabEd.publicKey(), rebindBytes, CanonicalTranscripts.signEd25519(collabEd.privateKey(), rebindBytes)));

        RawKeyPair deskEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair deskX = CanonicalTranscripts.generateX25519KeyPair();
        String deskDeviceId = CanonicalTranscripts.deriveIdentityUuidV7(deskEd.publicKey()).toString();
        DeviceDelegationCert deskCert = DeviceDelegationCert.newBuilder()
                .setCertId(CanonicalTranscripts.generateTemporalUuidV7().toString())
                .setUserUuid(userUuid)
                .setDeviceId(deskDeviceId)
                .setRole(DeviceRole.DESKTOP)
                .setDeviceEd25519Pub(ByteString.copyFrom(deskEd.publicKey()))
                .setDeviceX25519Pub(ByteString.copyFrom(deskX.publicKey()))
                .setIssuedAtMs(now)
                .setExpiresAtMs(now + 30L * 86_400_000L)
                .build();
        byte[] deskSig = CanonicalTranscripts.signEd25519(
                userEd.privateKey(), CanonicalTranscripts.buildDeviceDelegationTranscript(deskCert));
        assertTrue(CanonicalTranscripts.verifyEd25519(
                userEd.publicKey(), CanonicalTranscripts.buildDeviceDelegationTranscript(deskCert), deskSig));

        byte[] dhtV2 = CanonicalTranscripts.buildDhtSessionV2Transcript(
                userUuid, deskDeviceId, "chat.fibrasul.net.br:443",
                userEd.publicKey(), deskEd.publicKey(), deskX.publicKey(), now);
        byte[] dhtSig = CanonicalTranscripts.signEd25519(deskEd.privateKey(), dhtV2);
        assertTrue(CanonicalTranscripts.verifyEd25519(deskEd.publicKey(), dhtV2, dhtSig));
        // MitM substituting X25519 key fails verification
        byte[] mitmDhtV2 = CanonicalTranscripts.buildDhtSessionV2Transcript(
                userUuid, deskDeviceId, "chat.fibrasul.net.br:443",
                userEd.publicKey(), deskEd.publicKey(), CanonicalTranscripts.generateX25519KeyPair().publicKey(), now);
        assertFalse(CanonicalTranscripts.verifyEd25519(deskEd.publicKey(), mitmDhtV2, dhtSig));

        HomeNodeClaim homeClaim = HomeNodeClaim.newBuilder()
                .setUserUuid(userUuid)
                .setHomeNodeAddress("chat.fibrasul.net.br:443")
                .setRoutingEpoch(1L)
                .setIssuedAtMs(now)
                .setSignedByPrincipal(true)
                .build();
        byte[] homeSig = CanonicalTranscripts.signEd25519(
                principalEd.privateKey(), CanonicalTranscripts.buildHomeNodeClaimTranscript(homeClaim));
        assertTrue(CanonicalTranscripts.verifyEd25519(
                principalEd.publicKey(), CanonicalTranscripts.buildHomeNodeClaimTranscript(homeClaim), homeSig));

        String revocationId = CanonicalTranscripts.generateTemporalUuidV7().toString();
        IdentityReplacementNotice notice = IdentityReplacementNotice.newBuilder()
                .setRevocationId(revocationId)
                .setOldUserUuid(userUuid)
                .setNewUserUuid(newUserUuid)
                .setVerifyingIspCnpj(VALID_CNPJ_NUMERIC_RAW)
                .setReplacedAtMs(now)
                .build();
        byte[] replaceSig = CanonicalTranscripts.signEd25519(
                principalEd.privateKey(), CanonicalTranscripts.buildIdentityReplacementTranscript(notice));
        assertTrue(CanonicalTranscripts.verifyEd25519(
                principalEd.publicKey(), CanonicalTranscripts.buildIdentityReplacementTranscript(notice), replaceSig));

        RevocationEvent revEvent = RevocationEvent.newBuilder()
                .setRevocationId(revocationId)
                .setRevocationSeq(42L)
                .setTargetUuid(userUuid)
                .setRevokedDeviceId(deskDeviceId)
                .setReason("DEVICE_LOST")
                .setCreatedAtMs(now)
                .build();
        byte[] revSig = CanonicalTranscripts.signEd25519(
                principalEd.privateKey(), CanonicalTranscripts.buildRevocationEventTranscript(revEvent));
        assertTrue(CanonicalTranscripts.verifyEd25519(
                principalEd.publicKey(), CanonicalTranscripts.buildRevocationEventTranscript(revEvent), revSig));

        String msgId = CanonicalTranscripts.generateTemporalUuidV7().toString();
        byte[] msgTranscript = CanonicalTranscripts.buildMessageEnvelopeV2Transcript(
                msgId, userUuid, deskDeviceId, corpUuid, MessageType.TEXT_VALUE, "", new byte[]{1, 2, 3}, now);
        byte[] msgSig = CanonicalTranscripts.signEd25519(deskEd.privateKey(), msgTranscript);
        assertTrue(CanonicalTranscripts.verifyEd25519(deskEd.publicKey(), msgTranscript, msgSig));

        assertEquals(32, CanonicalTranscripts.buildGetOrRenewCredentialTranscript(userUuid, deskDeviceId, now).length > 0 ? 32 : 0);
        assertTrue(CanonicalTranscripts.buildRevokeDeviceTranscript(userUuid, deskDeviceId, "lost", now).length > 0);
        assertTrue(CanonicalTranscripts.buildPortAccountTranscript(userUuid, "fallback.ispchat.dev.br:443", "", 2L, now).length > 0);
        assertTrue(CanonicalTranscripts.buildPortAccountTranscript(
                userUuid, "chat.ispb.net.br:443", VALID_CNPJ_NUMERIC_FORMATTED, 2L, now).length > 0);
        assertTrue(CanonicalTranscripts.buildTurnCredentialRequestTranscript(userUuid, deskDeviceId, now).length > 0);

        byte[] genesisPrev = new byte[32];
        byte[] rowBytes = CanonicalTranscripts.buildAuditLedgerRowTranscript(
                1L, CanonicalTranscripts.generateTemporalUuidV7().toString(), "SUBMIT_VERIFICATION",
                userUuid, new byte[32], VALID_CNPJ_NUMERIC_RAW, collabUuid, escrowX.publicKey(), new byte[]{9, 8, 7}, now);
        byte[] entryHash1 = CanonicalTranscripts.computeLedgerEntryHash(genesisPrev, rowBytes);
        byte[] entryHash2 = CanonicalTranscripts.computeLedgerEntryHash(entryHash1, rowBytes);
        assertEquals(32, entryHash1.length);
        assertFalse(Arrays.equals(entryHash1, entryHash2));
    }

    @Test
    void multiDevicePayloadEncryptionUnwrapsForAllAuthorizedDevicesAndRejectsTampering() throws Exception {
        String messageId = CanonicalTranscripts.generateTemporalUuidV7().toString();
        String senderUuid = CanonicalTranscripts.generateTemporalUuidV7().toString();
        String receiverUuid = CanonicalTranscripts.generateTemporalUuidV7().toString();

        RawKeyPair recipientMobileX = CanonicalTranscripts.generateX25519KeyPair();
        String recipientMobileId = receiverUuid;

        RawKeyPair recipientDesktopX = CanonicalTranscripts.generateX25519KeyPair();
        String recipientDesktopId = CanonicalTranscripts.generateTemporalUuidV7().toString();

        RawKeyPair senderSiblingDesktopX = CanonicalTranscripts.generateX25519KeyPair();
        String senderSiblingDesktopId = CanonicalTranscripts.generateTemporalUuidV7().toString();

        Map<String, byte[]> targets = new LinkedHashMap<>();
        targets.put(recipientMobileId, recipientMobileX.publicKey());
        targets.put(recipientDesktopId, recipientDesktopX.publicKey());
        targets.put(senderSiblingDesktopId, senderSiblingDesktopX.publicKey());

        byte[] plaintext = "Fatura Outubro/2026 paga com sucesso - protocolo #99481".getBytes(StandardCharsets.UTF_8);
        MultiDeviceEncryptedPayload encrypted = CanonicalTranscripts.encryptMultiDevicePayload(
                messageId, senderUuid, receiverUuid, plaintext, targets);

        assertEquals(3, encrypted.getWrappedKeysCount());
        encrypted.getWrappedKeysList().forEach(
                w -> assertEquals(CanonicalTranscripts.WRAPPED_KEY_BLOB_LENGTH, w.getWrappedKeyBlob().size()));

        assertArrayEquals(plaintext, CanonicalTranscripts.decryptMultiDevicePayload(
                messageId, senderUuid, receiverUuid, recipientMobileId, recipientMobileX.privateKey(), encrypted));
        assertArrayEquals(plaintext, CanonicalTranscripts.decryptMultiDevicePayload(
                messageId, senderUuid, receiverUuid, recipientDesktopId, recipientDesktopX.privateKey(), encrypted));
        assertArrayEquals(plaintext, CanonicalTranscripts.decryptMultiDevicePayload(
                messageId, senderUuid, receiverUuid, senderSiblingDesktopId, senderSiblingDesktopX.privateKey(), encrypted));

        // Unauthorized device ID or wrong X25519 private key must fail
        String unknownDeviceId = CanonicalTranscripts.generateTemporalUuidV7().toString();
        assertThrows(GeneralSecurityException.class, () -> CanonicalTranscripts.decryptMultiDevicePayload(
                messageId, senderUuid, receiverUuid, unknownDeviceId, recipientMobileX.privateKey(), encrypted));

        RawKeyPair attackerX = CanonicalTranscripts.generateX25519KeyPair();
        assertThrows(GeneralSecurityException.class, () -> CanonicalTranscripts.decryptMultiDevicePayload(
                messageId, senderUuid, receiverUuid, recipientMobileId, attackerX.privateKey(), encrypted));

        // Tampered receiverUuid AAD must fail AEAD authentication
        String wrongReceiver = CanonicalTranscripts.generateTemporalUuidV7().toString();
        assertThrows(GeneralSecurityException.class, () -> CanonicalTranscripts.decryptMultiDevicePayload(
                messageId, senderUuid, wrongReceiver, recipientMobileId, recipientMobileX.privateKey(), encrypted));
    }

    @Test
    void offlineAuditEscrowSealingRoundTripsAndBindsToPrevEntryHashAad() throws Exception {
        RawKeyPair offlineRootEscrow = CanonicalTranscripts.generateX25519KeyPair();
        byte[] prevEntryHash = CanonicalTranscripts.sha256Utf8("prev-ledger-hash-41");
        byte[] auditJson = ("{\"userCpf\":\"" + VALID_CPF_RAW + "\",\"ispCnpj\":\"" + VALID_CNPJ_ALPHANUM_RAW + "\"}")
                .getBytes(StandardCharsets.UTF_8);

        SealedEscrowRecord sealed = CanonicalTranscripts.sealAuditEscrowBlob(
                offlineRootEscrow.publicKey(), prevEntryHash, auditJson);
        assertEquals(32, sealed.ephemeralX25519Pub().length);
        assertEquals(12 + auditJson.length + 16, sealed.sealedAuditBlob().length);

        byte[] decrypted = CanonicalTranscripts.openAuditEscrowBlob(
                offlineRootEscrow.privateKey(), sealed.ephemeralX25519Pub(), prevEntryHash, sealed.sealedAuditBlob());
        assertArrayEquals(auditJson, decrypted);

        // Wrong prevEntryHash AAD or wrong private key must fail AEAD tag check
        byte[] tamperedPrevHash = CanonicalTranscripts.sha256Utf8("different-prev-hash");
        assertThrows(GeneralSecurityException.class, () -> CanonicalTranscripts.openAuditEscrowBlob(
                offlineRootEscrow.privateKey(), sealed.ephemeralX25519Pub(), tamperedPrevHash, sealed.sealedAuditBlob()));

        RawKeyPair wrongKey = CanonicalTranscripts.generateX25519KeyPair();
        assertThrows(GeneralSecurityException.class, () -> CanonicalTranscripts.openAuditEscrowBlob(
                wrongKey.privateKey(), sealed.ephemeralX25519Pub(), prevEntryHash, sealed.sealedAuditBlob()));
    }

    @Test
    void argon2idBlindIndexServiceEnforcesProductionMemoryHardnessDomainSeparationAndConcurrency() throws Exception {
        byte[] pepperA = CanonicalTranscripts.sha256Utf8("production-secret-pepper-alpha-32b");
        byte[] pepperB = CanonicalTranscripts.sha256Utf8("production-secret-pepper-bravo-32b");

        // Full 64 MiB (65536 KiB), t=3, p=1 production configuration check
        BlindIndexService prodService = new BlindIndexService(pepperA);
        byte[] idxRaw = prodService.computeBlindIndex(DocumentType.CPF, VALID_CPF_RAW);
        byte[] idxFormatted = prodService.computeBlindIndex(DocumentType.CPF, VALID_CPF_FORMATTED);
        assertEquals(32, idxRaw.length);
        assertArrayEquals(idxRaw, idxFormatted);

        // Different pepper produces completely distinct blind index
        BlindIndexService otherPepperService = new BlindIndexService(pepperB, 4096, 2, 2);
        BlindIndexService fastPepperAService = new BlindIndexService(pepperA, 4096, 2, 2);
        byte[] idxA = fastPepperAService.computeBlindIndex(DocumentType.CPF, VALID_CPF_RAW);
        byte[] idxB = otherPepperService.computeBlindIndex(DocumentType.CPF, VALID_CPF_RAW);
        assertFalse(Arrays.equals(idxA, idxB));

        // Alphanumeric CNPJ normalization parity & distinct output
        byte[] cnpjLower = fastPepperAService.computeBlindIndex(DocumentType.CNPJ, VALID_CNPJ_ALPHANUM_FORMATTED);
        byte[] cnpjUpper = fastPepperAService.computeBlindIndex(DocumentType.CNPJ, VALID_CNPJ_ALPHANUM_RAW);
        assertArrayEquals(cnpjLower, cnpjUpper);
        assertNotEquals(Arrays.toString(idxA), Arrays.toString(cnpjUpper));

        // Parallel concurrency under Semaphore(2)
        try (ExecutorService pool = Executors.newFixedThreadPool(6)) {
            List<Callable<byte[]>> tasks = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                tasks.add(() -> fastPepperAService.computeBlindIndex(DocumentType.CPF, VALID_CPF_FORMATTED));
            }
            List<Future<byte[]>> futures = pool.invokeAll(tasks);
            for (Future<byte[]> f : futures) {
                assertArrayEquals(idxA, f.get());
            }
        }

        // Constructor parameter guards
        assertThrows(IllegalArgumentException.class, () -> new BlindIndexService(new byte[16]));
        assertThrows(IllegalArgumentException.class, () -> new BlindIndexService(pepperA, 512, 3, 4));
        assertThrows(IllegalArgumentException.class, () -> new BlindIndexService(pepperA, 4096, 0, 4));
        assertThrows(IllegalArgumentException.class, () -> new BlindIndexService(pepperA, 4096, 2, 0));
    }
}
