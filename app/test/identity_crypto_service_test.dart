import 'dart:convert';
import 'dart:typed_data';

import 'package:app/core/config/network_bootstrap_config.dart';
import 'package:app/core/crypto/identity_crypto_service.dart';
import 'package:app/core/grpc/generated/ispchat.pb.dart';
import 'package:cryptography/cryptography.dart';
import 'package:fixnum/fixnum.dart' as $fixnum;
import 'package:flutter_test/flutter_test.dart';

String _bytesToHex(List<int> bytes) {
  return bytes.map((b) => (b & 0xFF).toRadixString(16).padLeft(2, '0')).join();
}

List<int> _hexToBytes(String hex) {
  final out = <int>[];
  for (var i = 0; i < hex.length; i += 2) {
    out.add(int.parse(hex.substring(i, i + 2), radix: 16));
  }
  return out;
}

Future<VerificationCredential> _issueTestCredential(
  IdentityCryptoService crypto, {
  required SimpleKeyPair govRootEd,
  required IdentityBundle subject,
  required DocumentType docType,
  String publicCnpj = '',
  String publicDisplayName = '',
  int? expiresAtMs,
}) async {
  final prinEd = await Ed25519().newKeyPair();
  final escrowX = await X25519().newKeyPair();
  final now = DateTime.now().millisecondsSinceEpoch;

  final pCert = PrincipalSigningCert()
    ..certId = IdentityCryptoService.generateTemporalUuidV7()
    ..principalEd25519Pub = (await prinEd.extractPublicKey()).bytes
    ..escrowX25519Pub = (await escrowX.extractPublicKey()).bytes
    ..issuedAtMs = $fixnum.Int64(now)
    ..expiresAtMs = $fixnum.Int64(now + 365 * 86400000);
  pCert.rootSignature = await crypto.signRaw(
    govRootEd,
    CanonicalTranscripts.buildPrincipalSigningCertTranscript(pCert),
  );

  final vc = VerificationCredential()
    ..credentialId = IdentityCryptoService.generateTemporalUuidV7()
    ..userUuid = subject.userUuid
    ..rootEd25519Pub = subject.ed25519PublicKeyBytes
    ..rootX25519Pub = subject.x25519PublicKeyBytes
    ..docType = docType
    ..publicCnpj = publicCnpj
    ..publicDisplayName = publicDisplayName
    ..verifyingIspCnpj = '11222333000181'
    ..verifyingIspName = 'Fibra Sul Telecom'
    ..homeNodeAddress = 'node1.isp.br:8443'
    ..routingEpoch = $fixnum.Int64(1)
    ..issuedAtMs = $fixnum.Int64(now)
    ..expiresAtMs = $fixnum.Int64(expiresAtMs ?? (now + 90 * 86400000))
    ..principalCert = pCert;
  vc.principalSignature = await crypto.signRaw(
    prinEd,
    CanonicalTranscripts.buildVerificationCredentialTranscript(vc),
  );
  return vc;
}

void main() {
  const crypto = IdentityCryptoService();

  test('BIP39 generation, UUIDv7 binding, and mnemonic restore match', () async {
    final generated = await crypto.generateNewIdentity();
    expect(generated.mnemonic.split(' ').length, 12);
    expect(await crypto.verifyUuidBinding(
      generated.userUuid,
      generated.ed25519PublicKeyBytes,
    ), isTrue);

    final restored = await crypto.restoreIdentityFromMnemonic(generated.mnemonic);
    expect(restored.userUuid, generated.userUuid);
    expect(restored.ed25519PublicKeyBytes, generated.ed25519PublicKeyBytes);
    expect(restored.x25519PublicKeyBytes, generated.x25519PublicKeyBytes);
  });

  test('V1 ChaCha20-Poly1305 AEAD and contact URI sign/verify', () async {
    final alice = await crypto.generateNewIdentity();
    final bob = await crypto.generateNewIdentity();

    final encrypted = await crypto.encryptPayloadE2E(
      plaintext: utf8.encode('hello-v1'),
      senderX25519KeyPair: alice.x25519KeyPair,
      recipientX25519PublicKey: bob.x25519PublicKeyBytes,
    );
    final decrypted = await crypto.decryptPayloadE2E(
      encryptedBlob: encrypted,
      recipientX25519KeyPair: bob.x25519KeyPair,
      senderX25519PublicKey: alice.x25519PublicKeyBytes,
    );
    expect(utf8.decode(decrypted), 'hello-v1');

    final uri = await crypto.buildSignedContactUri(
      identity: alice,
      displayName: 'Alice',
    );
    final parsed = await crypto.parseAndVerifyContactUri(uri);
    expect(parsed.uuid, alice.userUuid);
    expect(parsed.displayName, 'Alice');
    expect(parsed.isVerified, isFalse);

    final tampered = uri.replaceFirst('u=${alice.userUuid}', 'u=${bob.userUuid}');
    expect(
      () => crypto.parseAndVerifyContactUri(tampered),
      throwsFormatException,
    );
  });

  test('CPF and Alphanumeric CNPJ (IN RFB 2229/2024) Mod-11 parity', () {
    expect(
      CanonicalTranscripts.normalizeAndValidateCpf('529.982.247-25'),
      '52998224725',
    );
    expect(CanonicalTranscripts.isValidCpf('111.111.111-11'), isFalse);
    expect(CanonicalTranscripts.isValidCpf('529.982.247-26'), isFalse);

    expect(
      CanonicalTranscripts.normalizeAndValidateCnpj('11.222.333/0001-81'),
      '11222333000181',
    );
    expect(
      CanonicalTranscripts.normalizeAndValidateCnpj('12.abc.345/01de-35'),
      '12ABC34501DE35',
    );
    expect(CanonicalTranscripts.isValidCnpj('12.ABC.345/01DE-36'), isFalse);
    expect(CanonicalTranscripts.isValidCnpj('00.000.000/0000-00'), isFalse);
  });

  test('Canonical transcripts match Java golden binary vectors byte-for-byte', () {
    final k32 = List<int>.generate(32, (i) => i + 1);
    final h32 = List<int>.generate(32, (i) => 0xA0 + i);
    final s64 = List<int>.generate(64, (i) => 0x50 + i);

    final pCert = PrincipalSigningCert()
      ..certId = '018f0000-0000-7000-8000-000000000001'
      ..principalEd25519Pub = k32
      ..escrowX25519Pub = h32
      ..issuedAtMs = $fixnum.Int64(1700000000000)
      ..expiresAtMs = $fixnum.Int64(1709999999999);
    expect(
      _bytesToHex(CanonicalTranscripts.buildPrincipalSigningCertTranscript(pCert)),
      '495350434841542d524f4f542d5052494e2d56310000002430313866303030302d303030302d373030302d383030302d303030303030303030303031000000200102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f2000000020a0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebf000000080000018bcfe56800000000080000018e23f14bff',
    );

    final hClaim = HomeNodeClaim()
      ..userUuid = '018f0000-0000-7000-8000-000000000003'
      ..homeNodeAddress = 'node1.isp.br:8443'
      ..routingEpoch = $fixnum.Int64(5)
      ..issuedAtMs = $fixnum.Int64(1700000003000)
      ..signedByPrincipal = true;
    expect(
      _bytesToHex(CanonicalTranscripts.buildHomeNodeClaimTranscript(hClaim)),
      '495350434841542d484f4d452d56310000002430313866303030302d303030302d373030302d383030302d303030303030303030303033000000116e6f6465312e6973702e62723a38343433000000080000000000000005000000080000018bcfe573b8',
    );

    expect(
      _bytesToHex(
        CanonicalTranscripts.buildDhtSessionV2Transcript(
          userUuid: '018f0000-0000-7000-8000-000000000003',
          deviceId: '018f0000-0000-7000-8000-000000000005',
          nodeAddress: 'node1.isp.br:8443',
          deviceEd25519Pub: k32,
          deviceX25519Pub: h32,
          rootEd25519Pub: k32,
          timestampMs: 1700000004000,
        ),
      ),
      '495350434841542d4448542d56320000002430313866303030302d303030302d373030302d383030302d3030303030303030303030330000002430313866303030302d303030302d373030302d383030302d303030303030303030303035000000116e6f6465312e6973702e62723a38343433000000200102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f2000000020a0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebf000000200102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20000000080000018bcfe577a0',
    );

    expect(
      _bytesToHex(
        CanonicalTranscripts.buildMessageEnvelopeV2Transcript(
          messageId: '018f0000-0000-7000-8000-000000000006',
          senderUuid: '018f0000-0000-7000-8000-000000000003',
          senderDeviceId: '018f0000-0000-7000-8000-000000000005',
          receiverUuid: '018f0000-0000-7000-8000-000000000007',
          typeValue: 1,
          refMessageId: '018f0000-0000-7000-8000-000000000008',
          encryptedPayloadSha256: h32,
          timestampMs: 1700000005000,
        ),
      ),
      '495350434841542d4d53472d56320000002430313866303030302d303030302d373030302d383030302d3030303030303030303030360000002430313866303030302d303030302d373030302d383030302d3030303030303030303030330000002430313866303030302d303030302d373030302d383030302d3030303030303030303030350000002430313866303030302d303030302d373030302d383030302d30303030303030303030303700000004000000010000002430313866303030302d303030302d373030302d383030302d30303030303030303030303800000020a0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebf000000080000018bcfe57b88',
    );

    expect(
      _bytesToHex(
        CanonicalTranscripts.buildCollaboratorUserAttestationTranscript(
          ispCnpj: '12ABC34501DE35',
          collaboratorUuid: '018f0000-0000-7000-8000-000000000012',
          userUuid: '018f0000-0000-7000-8000-000000000003',
          docType: DocumentType.CPF,
          docSha256: h32,
          homeNodeAddress: 'node1.isp.br:8443',
          userConsentSignature: s64,
          attestedAtMs: 1700000014000,
        ),
      ),
      '495350434841542d434841494e2d555345522d56310000000e31324142433334353031444533350000002430313866303030302d303030302d373030302d383030302d3030303030303030303031320000002430313866303030302d303030302d373030302d383030302d303030303030303030303033000000040000000100000020a0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebf000000116e6f6465312e6973702e62723a3834343300000040505152535455565758595a5b5c5d5e5f606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f808182838485868788898a8b8c8d8e8f000000080000018bcfe59eb0',
    );

    final emptyDevRev = RevocationEvent()
      ..revocationId = '018f0000-0000-7000-8000-000000000030'
      ..revocationSeq = $fixnum.Int64(1)
      ..targetUuid = '018f0000-0000-7000-8000-000000000003'
      ..revokedDeviceId = ''
      ..reason = 'rebind'
      ..createdAtMs = $fixnum.Int64(1700000009000);
    expect(
      _bytesToHex(CanonicalTranscripts.buildRevocationEventTranscript(emptyDevRev)),
      '495350434841542d5245564f4b452d56310000002430313866303030302d303030302d373030302d383030302d3030303030303030303033300000000800000000000000010000002430313866303030302d303030302d373030302d383030302d3030303030303030303030330000000000000006726562696e64000000080000018bcfe58b28',
    );
  });

  test('Dart decrypts real Java MultiDeviceEncryptedPayload vector', () async {
    final xPriv = _hexToBytes(
      '1c9b1bd3ff68545b191f2129ac4640b944ea117d70288b9e556c98902fe79d69',
    );
    final xPub = _hexToBytes(
      '460df15f4acb161a4120a9b4fb5a4e42d2787922f2c0439b82a65851c7f8d976',
    );
    const encB64 =
        'CiCubXCWGW6a5c9gMxKUpbUOfrdTIWzY5P1JVO0+9KY5MhIMEhZQK27qZISnmTWqGh/1waqMx4LehXm1+uWsvItY5+rQWPgay1JY7NPIQvEuImQKJDAxOGYwMDAwLTAwMDAtNzAwMC04MDAwLTAwMDAwMDAwMDAwNRI8Rkem35nWRe55pcB1vvOIbRLkWUKzxtkFZw5b/U0KAZovhVqSLbTMnD5itUYsj3SxVbEmxtqHmhJyDDY3';
    final payload = MultiDeviceEncryptedPayload.fromBuffer(base64Decode(encB64));
    final keyPair = SimpleKeyPairData(
      xPriv,
      publicKey: SimplePublicKey(xPub, type: KeyPairType.x25519),
      type: KeyPairType.x25519,
    );

    final clear = await crypto.decryptMultiDevicePayload(
      messageId: '018f0000-0000-7000-8000-000000000006',
      senderUuid: '018f0000-0000-7000-8000-000000000003',
      receiverUuid: '018f0000-0000-7000-8000-000000000007',
      targetDeviceId: '018f0000-0000-7000-8000-000000000005',
      targetDeviceX25519KeyPair: keyPair,
      payload: payload,
    );
    expect(utf8.decode(clear), 'hello java-dart');
  });

  test('Chain of Trust verification and verified URIs enforce pinned Root key',
      () async {
    final govRoot = await Ed25519().newKeyPair();
    final govRootPub =
        Uint8List.fromList((await govRoot.extractPublicKey()).bytes);
    final corporate = await crypto.generateNewIdentity();
    final vc = await _issueTestCredential(
      crypto,
      govRootEd: govRoot,
      subject: corporate,
      docType: DocumentType.CNPJ,
      publicCnpj: '12ABC34501DE35',
      publicDisplayName: 'Fibra Sul Telecom',
    );

    expect(
      await crypto.verifyVerificationCredential(
        vc,
        pinnedRootEd25519Pub: govRootPub,
      ),
      isTrue,
    );
    // Unconfigured / empty pinned Root key must fail closed!
    expect(await crypto.verifyVerificationCredential(vc), isFalse);

    final signedContactUri = await crypto.buildSignedContactUri(
      identity: corporate,
      displayName: 'Fibra Sul Telecom',
      verificationCredential: vc,
    );
    final parsedVerified = await crypto.parseAndVerifyContactUri(
      signedContactUri,
      pinnedRootEd25519Pub: govRootPub,
    );
    expect(parsedVerified.isVerified, isTrue);
    expect(parsedVerified.publicCnpj, '12ABC34501DE35');

    // Without pinned Root key, verified VC contact URI must fail closed.
    expect(
      () => crypto.parseAndVerifyContactUri(signedContactUri),
      throwsFormatException,
    );

    // Pair-req / pair-grant round-trip + VerificationConsent URI round-trip.
    final desktop = await crypto.generateDeviceIdentity(role: DeviceRole.DESKTOP);
    final reqUri = crypto.buildDevicePairRequestUri(desktopIdentity: desktop);
    final reqParsed = crypto.parseDevicePairRequestUri(reqUri);
    expect(reqParsed.deviceId, desktop.deviceId);
    final grantCert = await crypto.signDeviceDelegationCert(
      rootIdentity: corporate,
      targetDeviceId: reqParsed.deviceId,
      role: reqParsed.role,
      deviceEd25519Pub: reqParsed.deviceEd25519Pub,
      deviceX25519Pub: reqParsed.deviceX25519Pub,
    );
    final grantUri = crypto.buildDevicePairGrantUri(
      delegationCert: grantCert,
      rootEd25519Pub: corporate.ed25519PublicKeyBytes,
      rootX25519Pub: corporate.x25519PublicKeyBytes,
      homeNodeAddress: 'node1.isp.br:8443',
      verificationCredential: vc,
    );
    final parsedGrant = await crypto.parseAndVerifyDevicePairGrantUri(
      grantUri,
      pinnedRootEd25519Pub: govRootPub,
    );
    expect(parsedGrant.delegationCert.deviceId, desktop.deviceId);
    expect(parsedGrant.verificationCredential?.publicCnpj, '12ABC34501DE35');

    final consent = await crypto.buildSignedUserVerificationConsent(
      userIdentity: corporate,
      docType: DocumentType.CNPJ,
      rawDocument: '12.abc.345/01de-35',
      ispCnpj: '11.222.333/0001-81',
    );
    final consentUri = crypto.buildVerificationConsentUri(consent: consent);
    final parsedConsent =
        await crypto.parseAndVerifyVerificationConsentUri(consentUri);
    expect(parsedConsent.consent.userUuid, corporate.userUuid);

    final config = NetworkBootstrapConfig(
      pinnedRootEd25519PublicKeyBase64: base64UrlEncode(govRootPub),
    );
    expect(config.decodePinnedRootPublicKeyBytes(), govRootPub);
  });
}
