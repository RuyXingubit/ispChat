package br.dev.ispchat.node.security;

import br.dev.ispchat.common.crypto.CanonicalTranscripts;
import br.dev.ispchat.common.crypto.CanonicalTranscripts.RawKeyPair;
import br.dev.ispchat.grpc.ClientEnvelope;
import br.dev.ispchat.grpc.DeviceDelegationCert;
import br.dev.ispchat.grpc.DeviceRole;
import br.dev.ispchat.grpc.DocumentType;
import br.dev.ispchat.grpc.HomeNodeClaim;
import br.dev.ispchat.grpc.IdentityReplacementNotice;
import br.dev.ispchat.grpc.MessageType;
import br.dev.ispchat.grpc.PrincipalSigningCert;
import br.dev.ispchat.grpc.RegisterSessionRequest;
import br.dev.ispchat.grpc.RevocationEvent;
import br.dev.ispchat.grpc.VerificationCredential;
import com.google.protobuf.ByteString;

import java.time.Duration;

/**
 * Fabrica utilitaria de certificados e envelopes criptograficos reais V2 (sem mocks)
 * para os testes unitarios e de integracao do modulo :ispchat-node.
 */
public final class V2GovernanceTestFixtures {

    public static final String SAMPLE_ISP_CNPJ = "11222333000181";
    public static final String SAMPLE_CORP_CNPJ = "12ABC34501DE35";

    private V2GovernanceTestFixtures() {
    }

    public record GovernancePki(
            RawKeyPair rootEd25519,
            RawKeyPair principalEd25519,
            RawKeyPair escrowX25519,
            PrincipalSigningCert principalCert) {

        public static GovernancePki create(long nowMs) {
            RawKeyPair root = CanonicalTranscripts.generateEd25519KeyPair();
            RawKeyPair principal = CanonicalTranscripts.generateEd25519KeyPair();
            RawKeyPair escrow = CanonicalTranscripts.generateX25519KeyPair();

            PrincipalSigningCert unsignedCert = PrincipalSigningCert.newBuilder()
                    .setCertId(CanonicalTranscripts.generateTemporalUuidV7().toString())
                    .setPrincipalEd25519Pub(ByteString.copyFrom(principal.publicKey()))
                    .setEscrowX25519Pub(ByteString.copyFrom(escrow.publicKey()))
                    .setIssuedAtMs(nowMs - 1_000L)
                    .setExpiresAtMs(nowMs + Duration.ofDays(365).toMillis())
                    .build();

            byte[] rootSig = CanonicalTranscripts.signEd25519(
                    root.privateKey(),
                    CanonicalTranscripts.buildPrincipalSigningCertTranscript(unsignedCert));

            PrincipalSigningCert signedCert = unsignedCert.toBuilder()
                    .setRootSignature(ByteString.copyFrom(rootSig))
                    .build();

            return new GovernancePki(root, principal, escrow, signedCert);
        }

        public VerificationCredential issueCredential(
                RawKeyPair accountRootEd25519,
                RawKeyPair accountRootX25519,
                DocumentType docType,
                String homeNodeAddress,
                long routingEpoch,
                long nowMs) {
            String userUuid = CanonicalTranscripts.deriveIdentityUuidV7(accountRootEd25519.publicKey()).toString();
            VerificationCredential.Builder builder = VerificationCredential.newBuilder()
                    .setCredentialId(CanonicalTranscripts.generateTemporalUuidV7().toString())
                    .setUserUuid(userUuid)
                    .setRootEd25519Pub(ByteString.copyFrom(accountRootEd25519.publicKey()))
                    .setRootX25519Pub(ByteString.copyFrom(accountRootX25519.publicKey()))
                    .setDocType(docType)
                    .setPublicCnpj(docType == DocumentType.CNPJ ? SAMPLE_CORP_CNPJ : "")
                    .setPublicDisplayName(docType == DocumentType.CNPJ ? "Provedor Fibra S.A." : "")
                    .setVerifyingIspCnpj(SAMPLE_ISP_CNPJ)
                    .setVerifyingIspName("Provedor Parceiro ISP")
                    .setHomeNodeAddress(homeNodeAddress)
                    .setRoutingEpoch(routingEpoch)
                    .setIssuedAtMs(nowMs - 1_000L)
                    .setExpiresAtMs(nowMs + Duration.ofDays(90).toMillis())
                    .setPrincipalCert(principalCert);

            VerificationCredential unsigned = builder.build();
            byte[] sig = CanonicalTranscripts.signEd25519(
                    principalEd25519.privateKey(),
                    CanonicalTranscripts.buildVerificationCredentialTranscript(unsigned));
            return unsigned.toBuilder()
                    .setPrincipalSignature(ByteString.copyFrom(sig))
                    .build();
        }

        public HomeNodeClaim signPrincipalHomeClaim(
                String userUuid,
                String homeNodeAddress,
                long routingEpoch,
                long nowMs) {
            HomeNodeClaim unsigned = HomeNodeClaim.newBuilder()
                    .setUserUuid(userUuid)
                    .setHomeNodeAddress(homeNodeAddress)
                    .setRoutingEpoch(routingEpoch)
                    .setIssuedAtMs(nowMs)
                    .setSignedByPrincipal(true)
                    .build();
            byte[] sig = CanonicalTranscripts.signEd25519(
                    principalEd25519.privateKey(),
                    CanonicalTranscripts.buildHomeNodeClaimTranscript(unsigned));
            return unsigned.toBuilder()
                    .setClaimSignature(ByteString.copyFrom(sig))
                    .build();
        }

        public RevocationEvent signRevocationEvent(
                long seq,
                String targetUuid,
                String revokedDeviceId,
                String reason,
                IdentityReplacementNotice replacementNotice,
                long nowMs) {
            String revId = replacementNotice != null
                    ? replacementNotice.getRevocationId()
                    : CanonicalTranscripts.generateTemporalUuidV7().toString();
            RevocationEvent.Builder builder = RevocationEvent.newBuilder()
                    .setRevocationId(revId)
                    .setRevocationSeq(seq)
                    .setTargetUuid(targetUuid)
                    .setRevokedDeviceId(revokedDeviceId != null ? revokedDeviceId : "")
                    .setReason(reason)
                    .setCreatedAtMs(nowMs);
            if (replacementNotice != null) {
                builder.setReplacementNotice(replacementNotice);
            }
            RevocationEvent unsigned = builder.build();
            byte[] sig = CanonicalTranscripts.signEd25519(
                    principalEd25519.privateKey(),
                    CanonicalTranscripts.buildRevocationEventTranscript(unsigned));
            return unsigned.toBuilder()
                    .setPrincipalSignature(ByteString.copyFrom(sig))
                    .build();
        }

        public IdentityReplacementNotice signReplacementNotice(
                String oldUserUuid,
                String newUserUuid,
                long nowMs) {
            IdentityReplacementNotice unsigned = IdentityReplacementNotice.newBuilder()
                    .setRevocationId(CanonicalTranscripts.generateTemporalUuidV7().toString())
                    .setOldUserUuid(oldUserUuid)
                    .setNewUserUuid(newUserUuid)
                    .setVerifyingIspCnpj(SAMPLE_ISP_CNPJ)
                    .setReplacedAtMs(nowMs)
                    .build();
            byte[] sig = CanonicalTranscripts.signEd25519(
                    principalEd25519.privateKey(),
                    CanonicalTranscripts.buildIdentityReplacementTranscript(unsigned));
            return unsigned.toBuilder()
                    .setPrincipalSignature(ByteString.copyFrom(sig))
                    .build();
        }
    }

    public static DeviceDelegationCert signDeviceDelegation(
            RawKeyPair accountRootEd25519,
            RawKeyPair deviceEd25519,
            RawKeyPair deviceX25519,
            DeviceRole role,
            long nowMs) {
        String userUuid = CanonicalTranscripts.deriveIdentityUuidV7(accountRootEd25519.publicKey()).toString();
        String deviceId = CanonicalTranscripts.deriveIdentityUuidV7(deviceEd25519.publicKey()).toString();
        DeviceDelegationCert unsigned = DeviceDelegationCert.newBuilder()
                .setCertId(CanonicalTranscripts.generateTemporalUuidV7().toString())
                .setUserUuid(userUuid)
                .setDeviceId(deviceId)
                .setRole(role)
                .setDeviceEd25519Pub(ByteString.copyFrom(deviceEd25519.publicKey()))
                .setDeviceX25519Pub(ByteString.copyFrom(deviceX25519.publicKey()))
                .setIssuedAtMs(nowMs - 1_000L)
                .setExpiresAtMs(nowMs + Duration.ofDays(60).toMillis())
                .build();
        byte[] sig = CanonicalTranscripts.signEd25519(
                accountRootEd25519.privateKey(),
                CanonicalTranscripts.buildDeviceDelegationTranscript(unsigned));
        return unsigned.toBuilder()
                .setRootSignature(ByteString.copyFrom(sig))
                .build();
    }

    public static RegisterSessionRequest buildV2RegisterRequest(
            String userUuid,
            String deviceId,
            String nodeAddress,
            byte[] rootEdPub,
            RawKeyPair activeDeviceEd,
            RawKeyPair activeDeviceX25519,
            DeviceDelegationCert delegationCert,
            VerificationCredential verificationCredential,
            HomeNodeClaim homeNodeClaim,
            long timestampMs) {
        byte[] transcript = CanonicalTranscripts.buildDhtSessionV2Transcript(
                userUuid,
                deviceId,
                nodeAddress,
                rootEdPub,
                activeDeviceEd.publicKey(),
                activeDeviceX25519.publicKey(),
                timestampMs);
        byte[] sig = CanonicalTranscripts.signEd25519(activeDeviceEd.privateKey(), transcript);

        RegisterSessionRequest.Builder builder = RegisterSessionRequest.newBuilder()
                .setUserUuid(userUuid)
                .setDeviceId(deviceId)
                .setNodeAddress(nodeAddress)
                .setRootEd25519Pub(ByteString.copyFrom(rootEdPub))
                .setPublicKey(ByteString.copyFrom(activeDeviceEd.publicKey()))
                .setX25519PublicKey(ByteString.copyFrom(activeDeviceX25519.publicKey()))
                .setSignature(ByteString.copyFrom(sig))
                .setTimestamp(timestampMs);
        if (delegationCert != null) {
            builder.setDelegationCert(delegationCert);
        }
        if (verificationCredential != null) {
            builder.setVerificationCredential(verificationCredential);
        }
        if (homeNodeClaim != null) {
            builder.setHomeNodeClaim(homeNodeClaim);
        }
        return builder.build();
    }

    public static ClientEnvelope buildSignedV2Envelope(
            String messageId,
            String senderUuid,
            String senderDeviceId,
            String receiverUuid,
            MessageType type,
            String refMessageId,
            byte[] payload,
            RawKeyPair activeDeviceEd,
            DeviceDelegationCert delegationCert,
            VerificationCredential verificationCredential,
            long timestampMs,
            boolean includeCertsOnWire) {
        byte[] safePayload = payload != null ? payload : new byte[0];
        byte[] transcript = CanonicalTranscripts.buildMessageEnvelopeV2Transcript(
                messageId,
                senderUuid,
                senderDeviceId,
                receiverUuid,
                type.getNumber(),
                refMessageId,
                safePayload,
                timestampMs);
        byte[] sig = CanonicalTranscripts.signEd25519(activeDeviceEd.privateKey(), transcript);

        ClientEnvelope.Builder builder = ClientEnvelope.newBuilder()
                .setMessageId(messageId)
                .setSenderUuid(senderUuid)
                .setSenderDeviceId(senderDeviceId)
                .setReceiverUuid(receiverUuid)
                .setType(type)
                .setRefMessageId(refMessageId != null ? refMessageId : "")
                .setEncryptedPayload(ByteString.copyFrom(safePayload))
                .setTimestamp(timestampMs)
                .setSignature(ByteString.copyFrom(sig));

        if (includeCertsOnWire) {
            builder.setSenderPublicKey(ByteString.copyFrom(activeDeviceEd.publicKey()));
            if (delegationCert != null) {
                builder.setSenderDelegationCert(delegationCert);
            }
            if (verificationCredential != null) {
                builder.setSenderVerificationCredential(verificationCredential);
            }
        }
        return builder.build();
    }
}
