import 'dart:convert';
import 'dart:typed_data';

import 'package:cryptography/cryptography.dart';
import 'package:fixnum/fixnum.dart' as $fixnum;
import 'package:app/core/grpc/generated/ispchat.pb.dart';

/// Length-prefixed binary transcript builder matching Java `CanonicalTranscripts`.
///
/// Format: `UTF8(domainTag) || [uint32_be(len) || fieldBytes]...`
class TranscriptWriter {
  final BytesBuilder _buffer = BytesBuilder(copy: false);

  TranscriptWriter(String domainTag) {
    _buffer.add(utf8.encode(domainTag));
  }

  TranscriptWriter writeUtf8(String value) {
    return writeBytes(utf8.encode(value));
  }

  TranscriptWriter writeUuid(String uuid) {
    return writeUtf8(CanonicalTranscripts.canonicalUuid(uuid));
  }

  TranscriptWriter writeBytes(List<int> bytes) {
    final header = ByteData(4)..setUint32(0, bytes.length, Endian.big);
    _buffer.add(header.buffer.asUint8List());
    if (bytes.isNotEmpty) {
      _buffer.add(bytes);
    }
    return this;
  }

  TranscriptWriter writeFixedBytes(List<int> bytes, int expectedLength) {
    if (bytes.length != expectedLength) {
      throw ArgumentError(
        'Expected $expectedLength bytes in transcript, got ${bytes.length}.',
      );
    }
    return writeBytes(bytes);
  }

  TranscriptWriter writeUint32(int value) {
    final encoded = ByteData(4)..setUint32(0, value, Endian.big);
    return writeBytes(encoded.buffer.asUint8List());
  }

  TranscriptWriter writeInt64(int value) {
    final v64 = $fixnum.Int64(value);
    return writeFixnumInt64(v64);
  }

  TranscriptWriter writeFixnumInt64($fixnum.Int64 value) {
    final encoded = ByteData(8);
    encoded.setUint32(0, (value >> 32).toInt() & 0xFFFFFFFF, Endian.big);
    encoded.setUint32(4, value.toInt() & 0xFFFFFFFF, Endian.big);
    return writeBytes(encoded.buffer.asUint8List());
  }

  Uint8List toBytes() => _buffer.toBytes();
}

/// Canonical transcript serialization, RFC 9562 UUIDv7 helpers, and Brazilian
/// CPF / Alphanumeric CNPJ (IN RFB 2229/2024) validators with exact parity to Java.
class CanonicalTranscripts {
  static const int rawKeyLength = 32;
  static const int ed25519SignatureLength = 64;
  static const int chacha20NonceLength = 12;
  static const int poly1305TagLength = 16;

  static const String domainRootPrinV1 = 'ISPCHAT-ROOT-PRIN-V1';
  static const String domainChainIspV1 = 'ISPCHAT-CHAIN-ISP-V1';
  static const String domainChainCollabV1 = 'ISPCHAT-CHAIN-COLLAB-V1';
  static const String domainCollabRevokeV1 = 'ISPCHAT-COLLAB-REVOKE-V1';
  static const String domainVerifyReqV1 = 'ISPCHAT-VERIFY-REQ-V1';
  static const String domainChainUserV1 = 'ISPCHAT-CHAIN-USER-V1';
  static const String domainRebindV1 = 'ISPCHAT-REBIND-V1';
  static const String domainCredV1 = 'ISPCHAT-CRED-V1';
  static const String domainCredFetchV1 = 'ISPCHAT-CRED-FETCH-V1';
  static const String domainDelegateV1 = 'ISPCHAT-DELEGATE-V1';
  static const String domainDeviceRevokeV1 = 'ISPCHAT-DEVICE-REVOKE-V1';
  static const String domainPortV1 = 'ISPCHAT-PORT-V1';
  static const String domainTurnReqV1 = 'ISPCHAT-TURN-REQ-V1';
  static const String domainDhtV2 = 'ISPCHAT-DHT-V2';
  static const String domainHomeV1 = 'ISPCHAT-HOME-V1';
  static const String domainReplaceV1 = 'ISPCHAT-REPLACE-V1';
  static const String domainRevokeV1 = 'ISPCHAT-REVOKE-V1';
  static const String domainMsgV2 = 'ISPCHAT-MSG-V2';

  static final RegExp _uuidRegex = RegExp(
    r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',
  );
  static final RegExp _cnpjAlnumRegex = RegExp(r'^[0-9A-Z]{12}[0-9]{2}$');

  static String canonicalUuid(String? raw) {
    if (raw == null) {
      throw ArgumentError('UUID cannot be null');
    }
    final normalized = raw.trim().toLowerCase();
    if (!_uuidRegex.hasMatch(normalized)) {
      throw ArgumentError('Invalid UUID format: "$raw"');
    }
    return normalized;
  }

  /// Derives the deterministic RFC 9562 UUIDv7 bound to an Ed25519 public key.
  static Future<String> deriveIdentityUuidV7(List<int> publicKeyBytes) async {
    if (publicKeyBytes.length != rawKeyLength) {
      throw ArgumentError('Ed25519 public key must be 32 bytes.');
    }
    final digest = await Sha256().hash(publicKeyBytes);
    final bytes = Uint8List.fromList(digest.bytes.sublist(0, 16));
    bytes[6] = (bytes[6] & 0x0F) | 0x70;
    bytes[8] = (bytes[8] & 0x3F) | 0x80;
    return formatUuidBytes(bytes);
  }

  /// Verifies that [uuidString] matches the deterministic UUIDv7 of [publicKeyBytes].
  static Future<bool> verifyIdentityBinding(
    String? uuidString,
    List<int> publicKeyBytes,
  ) async {
    if (uuidString == null || publicKeyBytes.length != rawKeyLength) {
      return false;
    }
    try {
      final normalized = canonicalUuid(uuidString);
      final expected = await deriveIdentityUuidV7(publicKeyBytes);
      return normalized == expected;
    } catch (_) {
      return false;
    }
  }

  static String formatUuidBytes(List<int> bytes) {
    final hex = bytes
        .map((b) => (b & 0xFF).toRadixString(16).padLeft(2, '0'))
        .join();
    return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-'
        '${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}';
  }

  /// Validates and strips formatting from a Brazilian CPF (11 numeric digits).
  static String normalizeAndValidateCpf(String? rawCpf) {
    if (rawCpf == null) {
      throw ArgumentError('CPF cannot be null');
    }
    final digits = rawCpf.replaceAll(RegExp(r'[^0-9A-Za-z]'), '');
    if (digits.length != 11 || !RegExp(r'^[0-9]{11}$').hasMatch(digits)) {
      throw ArgumentError('CPF must contain exactly 11 numeric digits.');
    }
    if (digits.split('').toSet().length == 1) {
      throw ArgumentError('Invalid CPF (repeated digits).');
    }
    final d1 = _cpfCheckDigit(digits, 9, 10);
    final d2 = _cpfCheckDigit(digits, 10, 11);
    if (d1 != (digits.codeUnitAt(9) - 48) ||
        d2 != (digits.codeUnitAt(10) - 48)) {
      throw ArgumentError('CPF check digits failed Mod-11 validation.');
    }
    return digits;
  }

  /// Validates numeric or alphanumeric CNPJ per Receita Federal IN RFB 2229/2024.
  static String normalizeAndValidateCnpj(String? rawCnpj) {
    if (rawCnpj == null) {
      throw ArgumentError('CNPJ cannot be null');
    }
    final cleaned = rawCnpj
        .trim()
        .replaceAll(RegExp(r'[.\-/\s]'), '')
        .toUpperCase();
    if (!_cnpjAlnumRegex.hasMatch(cleaned)) {
      throw ArgumentError(
        'CNPJ must match 12 alphanumeric characters + 2 numeric check digits.',
      );
    }
    if (cleaned == '00000000000000') {
      throw ArgumentError('Invalid CNPJ (all zeros).');
    }
    const w1 = <int>[5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];
    const w2 = <int>[6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];
    final d1 = _cnpjCheckDigit(cleaned, w1);
    final d2 = _cnpjCheckDigit(cleaned, w2);
    if (d1 != (cleaned.codeUnitAt(12) - 48) ||
        d2 != (cleaned.codeUnitAt(13) - 48)) {
      throw ArgumentError('CNPJ check digits failed Mod-11 validation.');
    }
    return cleaned;
  }

  static String normalizeAndValidateDocument(
    DocumentType docType,
    String? rawDocument,
  ) {
    switch (docType) {
      case DocumentType.CPF:
        return normalizeAndValidateCpf(rawDocument);
      case DocumentType.CNPJ:
        return normalizeAndValidateCnpj(rawDocument);
      default:
        throw ArgumentError('Unsupported DocumentType: $docType');
    }
  }

  static bool isValidCpf(String? rawCpf) {
    try {
      normalizeAndValidateCpf(rawCpf);
      return true;
    } catch (_) {
      return false;
    }
  }

  static bool isValidCnpj(String? rawCnpj) {
    try {
      normalizeAndValidateCnpj(rawCnpj);
      return true;
    } catch (_) {
      return false;
    }
  }

  static int _cpfCheckDigit(String digits, int len, int startWeight) {
    var sum = 0;
    for (var i = 0; i < len; i++) {
      sum += (digits.codeUnitAt(i) - 48) * (startWeight - i);
    }
    final rem = (sum * 10) % 11;
    return rem == 10 ? 0 : rem;
  }

  static int _cnpjCheckDigit(String cleaned, List<int> weights) {
    var sum = 0;
    for (var i = 0; i < weights.length; i++) {
      sum += (cleaned.codeUnitAt(i) - 48) * weights[i];
    }
    final rem = sum % 11;
    return rem < 2 ? 0 : 11 - rem;
  }

  static Uint8List buildPrincipalSigningCertTranscript(PrincipalSigningCert cert) {
    return TranscriptWriter(domainRootPrinV1)
        .writeUuid(cert.certId)
        .writeFixedBytes(cert.principalEd25519Pub, rawKeyLength)
        .writeFixedBytes(cert.escrowX25519Pub, rawKeyLength)
        .writeFixnumInt64(cert.issuedAtMs)
        .writeFixnumInt64(cert.expiresAtMs)
        .toBytes();
  }

  static Uint8List buildCollaboratorAuthTranscript(CollaboratorAuthCert cert) {
    return TranscriptWriter(domainChainCollabV1)
        .writeUuid(cert.certId)
        .writeUtf8(normalizeAndValidateCnpj(cert.ispCnpj))
        .writeUuid(cert.partnerUuid)
        .writeUuid(cert.collaboratorUuid)
        .writeFixedBytes(cert.collabCpfSha256, rawKeyLength)
        .writeFixedBytes(cert.collabEd25519Pub, rawKeyLength)
        .writeFixnumInt64(cert.issuedAtMs)
        .writeFixnumInt64(cert.expiresAtMs)
        .toBytes();
  }

  static Uint8List buildRevokeCollaboratorTranscript({
    required String ispCnpj,
    required String partnerUuid,
    required String collaboratorUuid,
    required String reason,
    required int timestampMs,
  }) {
    return TranscriptWriter(domainCollabRevokeV1)
        .writeUtf8(normalizeAndValidateCnpj(ispCnpj))
        .writeUuid(partnerUuid)
        .writeUuid(collaboratorUuid)
        .writeUtf8(reason)
        .writeInt64(timestampMs)
        .toBytes();
  }

  static Uint8List buildUserVerificationConsentTranscript(
    UserVerificationConsent consent,
  ) {
    return TranscriptWriter(domainVerifyReqV1)
        .writeUuid(consent.userUuid)
        .writeFixedBytes(consent.userEd25519Pub, rawKeyLength)
        .writeFixedBytes(consent.userX25519Pub, rawKeyLength)
        .writeUint32(consent.docType.value)
        .writeFixedBytes(consent.docSha256, rawKeyLength)
        .writeUtf8(normalizeAndValidateCnpj(consent.ispCnpj))
        .writeFixnumInt64(consent.timestampMs)
        .toBytes();
  }

  static Uint8List buildCollaboratorUserAttestationTranscript({
    required String ispCnpj,
    required String collaboratorUuid,
    required String userUuid,
    required DocumentType docType,
    required List<int> docSha256,
    required String homeNodeAddress,
    required List<int> userConsentSignature,
    required int attestedAtMs,
  }) {
    return TranscriptWriter(domainChainUserV1)
        .writeUtf8(normalizeAndValidateCnpj(ispCnpj))
        .writeUuid(collaboratorUuid)
        .writeUuid(userUuid)
        .writeUint32(docType.value)
        .writeFixedBytes(docSha256, rawKeyLength)
        .writeUtf8(homeNodeAddress)
        .writeFixedBytes(userConsentSignature, ed25519SignatureLength)
        .writeInt64(attestedAtMs)
        .toBytes();
  }

  static Uint8List buildCpfRebindTranscript({
    required String ispCnpj,
    required String collaboratorUuid,
    required String oldUserUuid,
    required String newUserUuid,
    required List<int> cpfSha256,
    required String homeNodeAddress,
    required List<int> newConsentSignature,
    required int attestedAtMs,
  }) {
    return TranscriptWriter(domainRebindV1)
        .writeUtf8(normalizeAndValidateCnpj(ispCnpj))
        .writeUuid(collaboratorUuid)
        .writeUuid(oldUserUuid)
        .writeUuid(newUserUuid)
        .writeFixedBytes(cpfSha256, rawKeyLength)
        .writeUtf8(homeNodeAddress)
        .writeFixedBytes(newConsentSignature, ed25519SignatureLength)
        .writeInt64(attestedAtMs)
        .toBytes();
  }

  static Uint8List buildVerificationCredentialTranscript(
    VerificationCredential cred,
  ) {
    return TranscriptWriter(domainCredV1)
        .writeUuid(cred.credentialId)
        .writeUuid(cred.userUuid)
        .writeFixedBytes(cred.rootEd25519Pub, rawKeyLength)
        .writeFixedBytes(cred.rootX25519Pub, rawKeyLength)
        .writeUint32(cred.docType.value)
        .writeUtf8(cred.publicCnpj)
        .writeUtf8(cred.publicDisplayName)
        .writeUtf8(cred.verifyingIspCnpj)
        .writeUtf8(cred.verifyingIspName)
        .writeUtf8(cred.homeNodeAddress)
        .writeFixnumInt64(cred.routingEpoch)
        .writeFixnumInt64(cred.issuedAtMs)
        .writeFixnumInt64(cred.expiresAtMs)
        .toBytes();
  }

  static Uint8List buildGetOrRenewCredentialTranscript({
    required String userUuid,
    required String deviceId,
    required int timestampMs,
  }) {
    return TranscriptWriter(domainCredFetchV1)
        .writeUuid(userUuid)
        .writeUuid(deviceId)
        .writeInt64(timestampMs)
        .toBytes();
  }

  static Uint8List buildDeviceDelegationTranscript(DeviceDelegationCert cert) {
    return TranscriptWriter(domainDelegateV1)
        .writeUuid(cert.certId)
        .writeUuid(cert.userUuid)
        .writeUuid(cert.deviceId)
        .writeUint32(cert.role.value)
        .writeFixedBytes(cert.deviceEd25519Pub, rawKeyLength)
        .writeFixedBytes(cert.deviceX25519Pub, rawKeyLength)
        .writeFixnumInt64(cert.issuedAtMs)
        .writeFixnumInt64(cert.expiresAtMs)
        .toBytes();
  }

  static Uint8List buildRevokeDeviceTranscript({
    required String userUuid,
    required String revokedDeviceId,
    required String reason,
    required int timestampMs,
  }) {
    return TranscriptWriter(domainDeviceRevokeV1)
        .writeUuid(userUuid)
        .writeUuid(revokedDeviceId)
        .writeUtf8(reason)
        .writeInt64(timestampMs)
        .toBytes();
  }

  static Uint8List buildPortAccountTranscript({
    required String userUuid,
    required String targetHomeNodeAddress,
    required String targetIspCnpj,
    required int expectedRoutingEpoch,
    required int timestampMs,
  }) {
    return TranscriptWriter(domainPortV1)
        .writeUuid(userUuid)
        .writeUtf8(targetHomeNodeAddress)
        .writeUtf8(normalizeAndValidateCnpj(targetIspCnpj))
        .writeInt64(expectedRoutingEpoch)
        .writeInt64(timestampMs)
        .toBytes();
  }

  static Uint8List buildTurnCredentialRequestTranscript({
    required String userUuid,
    required String deviceId,
    required int timestampMs,
  }) {
    return TranscriptWriter(domainTurnReqV1)
        .writeUuid(userUuid)
        .writeUuid(deviceId)
        .writeInt64(timestampMs)
        .toBytes();
  }

  static Uint8List buildDhtSessionV2Transcript({
    required String userUuid,
    required String deviceId,
    required String nodeAddress,
    required List<int> deviceEd25519Pub,
    required List<int> deviceX25519Pub,
    required List<int> rootEd25519Pub,
    required int timestampMs,
  }) {
    return TranscriptWriter(domainDhtV2)
        .writeUuid(userUuid)
        .writeUuid(deviceId)
        .writeUtf8(nodeAddress)
        .writeFixedBytes(deviceEd25519Pub, rawKeyLength)
        .writeFixedBytes(deviceX25519Pub, rawKeyLength)
        .writeFixedBytes(rootEd25519Pub, rawKeyLength)
        .writeInt64(timestampMs)
        .toBytes();
  }

  static Uint8List buildHomeNodeClaimTranscript(HomeNodeClaim claim) {
    return TranscriptWriter(domainHomeV1)
        .writeUuid(claim.userUuid)
        .writeUtf8(claim.homeNodeAddress)
        .writeFixnumInt64(claim.routingEpoch)
        .writeFixnumInt64(claim.issuedAtMs)
        .toBytes();
  }

  static Uint8List buildIdentityReplacementTranscript(
    IdentityReplacementNotice notice,
  ) {
    return TranscriptWriter(domainReplaceV1)
        .writeUuid(notice.revocationId)
        .writeUuid(notice.oldUserUuid)
        .writeUuid(notice.newUserUuid)
        .writeUtf8(notice.verifyingIspCnpj)
        .writeFixnumInt64(notice.replacedAtMs)
        .toBytes();
  }

  static Uint8List buildRevocationEventTranscript(RevocationEvent event) {
    final trimmedDeviceId = event.revokedDeviceId.trim();
    return TranscriptWriter(domainRevokeV1)
        .writeUuid(event.revocationId)
        .writeFixnumInt64(event.revocationSeq)
        .writeUuid(event.targetUuid)
        .writeUtf8(trimmedDeviceId.isEmpty ? '' : canonicalUuid(trimmedDeviceId))
        .writeUtf8(event.reason)
        .writeFixnumInt64(event.createdAtMs)
        .toBytes();
  }

  static Uint8List buildMessageEnvelopeV2Transcript({
    required String messageId,
    required String senderUuid,
    required String senderDeviceId,
    required String receiverUuid,
    required int typeValue,
    required String refMessageId,
    required List<int> encryptedPayloadSha256,
    required int timestampMs,
  }) {
    return TranscriptWriter(domainMsgV2)
        .writeUuid(messageId)
        .writeUuid(senderUuid)
        .writeUuid(senderDeviceId)
        .writeUuid(receiverUuid)
        .writeUint32(typeValue)
        .writeUtf8(refMessageId)
        .writeFixedBytes(encryptedPayloadSha256, rawKeyLength)
        .writeInt64(timestampMs)
        .toBytes();
  }
}
