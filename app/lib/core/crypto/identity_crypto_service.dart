import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:bip39/bip39.dart' as bip39;
import 'package:cryptography/cryptography.dart';
import 'package:fixnum/fixnum.dart' as $fixnum;
import 'package:app/core/config/network_bootstrap_config.dart';
import 'package:app/core/crypto/canonical_transcripts.dart';
import 'package:app/core/grpc/generated/ispchat.pb.dart';
import 'package:uuid/uuid.dart';

export 'canonical_transcripts.dart';

/// Complete local Root identity generated from or restored by a BIP39 mnemonic.
class IdentityBundle {
  final String userUuid;
  final String mnemonic;
  final SimpleKeyPair ed25519KeyPair;
  final SimpleKeyPair x25519KeyPair;
  final Uint8List ed25519PublicKeyBytes;
  final Uint8List x25519PublicKeyBytes;
  final Uint8List x25519PrivateKeyBytes;

  IdentityBundle({
    required this.userUuid,
    required this.mnemonic,
    required this.ed25519KeyPair,
    required this.x25519KeyPair,
    required this.ed25519PublicKeyBytes,
    required this.x25519PublicKeyBytes,
    required this.x25519PrivateKeyBytes,
  });
}

/// Ephemeral or persisted device-specific keypair with optional Root delegation.
class LocalDeviceIdentity {
  final String deviceId;
  final DeviceRole role;
  final SimpleKeyPair ed25519KeyPair;
  final SimpleKeyPair x25519KeyPair;
  final Uint8List ed25519PublicKeyBytes;
  final Uint8List x25519PublicKeyBytes;
  final DeviceDelegationCert? delegationCert;

  const LocalDeviceIdentity({
    required this.deviceId,
    required this.role,
    required this.ed25519KeyPair,
    required this.x25519KeyPair,
    required this.ed25519PublicKeyBytes,
    required this.x25519PublicKeyBytes,
    this.delegationCert,
  });

  LocalDeviceIdentity withDelegationCert(DeviceDelegationCert cert) {
    return LocalDeviceIdentity(
      deviceId: deviceId,
      role: role,
      ed25519KeyPair: ed25519KeyPair,
      x25519KeyPair: x25519KeyPair,
      ed25519PublicKeyBytes: ed25519PublicKeyBytes,
      x25519PublicKeyBytes: x25519PublicKeyBytes,
      delegationCert: cert,
    );
  }
}

/// Verified or unverified peer contact identity metadata.
class ContactBundle {
  final String uuid;
  final Uint8List ed25519PublicKey;
  final Uint8List x25519PublicKey;
  final String? displayName;
  final bool isVerified;
  final DocumentType? docType;
  final String? publicCnpj;
  final String? publicDisplayName;
  final String? verifyingIspCnpj;
  final String? verifyingIspName;
  final VerificationCredential? verificationCredential;
  final HomeNodeClaim? homeNodeClaim;

  const ContactBundle({
    required this.uuid,
    required this.ed25519PublicKey,
    required this.x25519PublicKey,
    this.displayName,
    this.isVerified = false,
    this.docType,
    this.publicCnpj,
    this.publicDisplayName,
    this.verifyingIspCnpj,
    this.verifyingIspName,
    this.verificationCredential,
    this.homeNodeClaim,
  });

  ContactBundle copyWith({
    String? uuid,
    Uint8List? ed25519PublicKey,
    Uint8List? x25519PublicKey,
    String? displayName,
    bool? isVerified,
    DocumentType? docType,
    String? publicCnpj,
    String? publicDisplayName,
    String? verifyingIspCnpj,
    String? verifyingIspName,
    VerificationCredential? verificationCredential,
    HomeNodeClaim? homeNodeClaim,
  }) {
    return ContactBundle(
      uuid: uuid ?? this.uuid,
      ed25519PublicKey: ed25519PublicKey ?? this.ed25519PublicKey,
      x25519PublicKey: x25519PublicKey ?? this.x25519PublicKey,
      displayName: displayName ?? this.displayName,
      isVerified: isVerified ?? this.isVerified,
      docType: docType ?? this.docType,
      publicCnpj: publicCnpj ?? this.publicCnpj,
      publicDisplayName: publicDisplayName ?? this.publicDisplayName,
      verifyingIspCnpj: verifyingIspCnpj ?? this.verifyingIspCnpj,
      verifyingIspName: verifyingIspName ?? this.verifyingIspName,
      verificationCredential:
          verificationCredential ?? this.verificationCredential,
      homeNodeClaim: homeNodeClaim ?? this.homeNodeClaim,
    );
  }
}

/// Parsed consent URI sent from a subscriber to an ISP collaborator.
class VerificationConsentUriBundle {
  final UserVerificationConsent consent;
  final String? displayName;

  const VerificationConsentUriBundle({
    required this.consent,
    this.displayName,
  });
}

/// Parsed desktop pairing request shown as a QR code or URI on desktop.
class DevicePairRequestBundle {
  final String deviceId;
  final DeviceRole role;
  final Uint8List deviceEd25519Pub;
  final Uint8List deviceX25519Pub;

  const DevicePairRequestBundle({
    required this.deviceId,
    required this.role,
    required this.deviceEd25519Pub,
    required this.deviceX25519Pub,
  });
}

/// Parsed pairing grant issued by the Root device to authorize a secondary device.
class DevicePairGrantBundle {
  final DeviceDelegationCert delegationCert;
  final Uint8List rootEd25519Pub;
  final Uint8List rootX25519Pub;
  final String? homeNodeAddress;
  final VerificationCredential? verificationCredential;

  const DevicePairGrantBundle({
    required this.delegationCert,
    required this.rootEd25519Pub,
    required this.rootX25519Pub,
    this.homeNodeAddress,
    this.verificationCredential,
  });
}

/// Cryptographic operations for ispChat identities, Chain of Trust V2
/// transcripts, multi-device E2EE, and QR / deep link protocols.
class IdentityCryptoService {
  static final Ed25519 _ed25519 = Ed25519();
  static final X25519 _x25519 = X25519();
  static final Chacha20 _chacha20 = Chacha20.poly1305Aead();
  static final Sha256 _sha256 = Sha256();
  static final Hkdf _hkdf = Hkdf(hmac: Hmac.sha256(), outputLength: 32);
  static const Uuid _uuid = Uuid();
  static final Random _secureRandom = Random.secure();

  final NetworkBootstrapConfig bootstrapConfig;

  const IdentityCryptoService({
    this.bootstrapConfig = const NetworkBootstrapConfig(),
  });

  static String generateTemporalUuidV7() => _uuid.v7();

  Future<IdentityBundle> generateNewIdentity() async {
    final mnemonic = bip39.generateMnemonic(strength: 128);
    return restoreIdentityFromMnemonic(mnemonic);
  }

  Future<IdentityBundle> restoreIdentityFromMnemonic(String mnemonic) async {
    final normalized = mnemonic.trim().replaceAll(RegExp(r'\s+'), ' ');
    if (!bip39.validateMnemonic(normalized)) {
      throw const FormatException('Invalid BIP39 mnemonic.');
    }
    final seedBytes = bip39.mnemonicToSeed(normalized);
    final edSeed = (await _sha256.hash([
      ...seedBytes.sublist(0, 32),
      ...utf8.encode('ispchat-ed25519-root-v1'),
    ])).bytes;
    final xSeed = (await _sha256.hash([
      ...seedBytes.sublist(32, 64),
      ...utf8.encode('ispchat-x25519-root-v1'),
    ])).bytes;

    final edPair = await _ed25519.newKeyPairFromSeed(edSeed);
    final xPair = await _x25519.newKeyPairFromSeed(xSeed);
    final edPub = Uint8List.fromList((await edPair.extractPublicKey()).bytes);
    final xPub = Uint8List.fromList((await xPair.extractPublicKey()).bytes);
    final xPriv = Uint8List.fromList(await xPair.extractPrivateKeyBytes());
    final userUuid = await deriveIdentityUuidV7(edPub);

    return IdentityBundle(
      userUuid: userUuid,
      mnemonic: normalized,
      ed25519KeyPair: edPair,
      x25519KeyPair: xPair,
      ed25519PublicKeyBytes: edPub,
      x25519PublicKeyBytes: xPub,
      x25519PrivateKeyBytes: xPriv,
    );
  }

  /// Creates an independent device keypair, optionally immediately signed by a Root identity.
  Future<LocalDeviceIdentity> generateDeviceIdentity({
    required DeviceRole role,
    IdentityBundle? rootIdentityForDelegation,
    String? deviceIdOverride,
    int validityDays = 365,
    int? nowMs,
  }) async {
    final deviceId = CanonicalTranscripts.canonicalUuid(
      deviceIdOverride ?? generateTemporalUuidV7(),
    );
    final edSeed = _randomBytes(32);
    final xSeed = _randomBytes(32);
    final edPair = await _ed25519.newKeyPairFromSeed(edSeed);
    final xPair = await _x25519.newKeyPairFromSeed(xSeed);
    final edPub = Uint8List.fromList((await edPair.extractPublicKey()).bytes);
    final xPub = Uint8List.fromList((await xPair.extractPublicKey()).bytes);

    DeviceDelegationCert? cert;
    if (rootIdentityForDelegation != null) {
      cert = await signDeviceDelegationCert(
        rootIdentity: rootIdentityForDelegation,
        targetDeviceId: deviceId,
        role: role,
        deviceEd25519Pub: edPub,
        deviceX25519Pub: xPub,
        validityDays: validityDays,
        nowMs: nowMs,
      );
    }

    return LocalDeviceIdentity(
      deviceId: deviceId,
      role: role,
      ed25519KeyPair: edPair,
      x25519KeyPair: xPair,
      ed25519PublicKeyBytes: edPub,
      x25519PublicKeyBytes: xPub,
      delegationCert: cert,
    );
  }

  Future<LocalDeviceIdentity> restoreDeviceIdentityFromSeeds({
    required String deviceId,
    required DeviceRole role,
    required List<int> ed25519Seed,
    required List<int> x25519Seed,
    DeviceDelegationCert? delegationCert,
  }) async {
    final edPair = await _ed25519.newKeyPairFromSeed(ed25519Seed);
    final xPair = await _x25519.newKeyPairFromSeed(x25519Seed);
    return LocalDeviceIdentity(
      deviceId: CanonicalTranscripts.canonicalUuid(deviceId),
      role: role,
      ed25519KeyPair: edPair,
      x25519KeyPair: xPair,
      ed25519PublicKeyBytes: Uint8List.fromList(
        (await edPair.extractPublicKey()).bytes,
      ),
      x25519PublicKeyBytes: Uint8List.fromList(
        (await xPair.extractPublicKey()).bytes,
      ),
      delegationCert: delegationCert,
    );
  }

  /// Creates the Root device's self-signed delegation record so V2 multi-device
  /// flows handle mobile Root and paired desktop uniformly.
  Future<LocalDeviceIdentity> deriveMobileRootDeviceIdentity(
    IdentityBundle rootIdentity, {
    String? deviceIdOverride,
    int validityDays = 3650,
    int? nowMs,
  }) async {
    final deviceId = CanonicalTranscripts.canonicalUuid(
      deviceIdOverride ?? rootIdentity.userUuid,
    );
    final cert = await signDeviceDelegationCert(
      rootIdentity: rootIdentity,
      targetDeviceId: deviceId,
      role: DeviceRole.MOBILE_ROOT,
      deviceEd25519Pub: rootIdentity.ed25519PublicKeyBytes,
      deviceX25519Pub: rootIdentity.x25519PublicKeyBytes,
      validityDays: validityDays,
      nowMs: nowMs,
    );
    return LocalDeviceIdentity(
      deviceId: deviceId,
      role: DeviceRole.MOBILE_ROOT,
      ed25519KeyPair: rootIdentity.ed25519KeyPair,
      x25519KeyPair: rootIdentity.x25519KeyPair,
      ed25519PublicKeyBytes: rootIdentity.ed25519PublicKeyBytes,
      x25519PublicKeyBytes: rootIdentity.x25519PublicKeyBytes,
      delegationCert: cert,
    );
  }

  Future<String> deriveIdentityUuidV7(List<int> publicKeyBytes) {
    return CanonicalTranscripts.deriveIdentityUuidV7(publicKeyBytes);
  }

  Future<bool> verifyUuidBinding(String uuid, List<int> publicKeyBytes) {
    return CanonicalTranscripts.verifyIdentityBinding(uuid, publicKeyBytes);
  }

  Future<Uint8List> signRaw(SimpleKeyPair keyPair, List<int> messageBytes) async {
    final signature = await _ed25519.sign(messageBytes, keyPair: keyPair);
    return Uint8List.fromList(signature.bytes);
  }

  Future<bool> verifySignature({
    required List<int> publicKeyBytes,
    required List<int> messageBytes,
    required List<int> signatureBytes,
  }) async {
    if (publicKeyBytes.length != CanonicalTranscripts.rawKeyLength ||
        signatureBytes.length != CanonicalTranscripts.ed25519SignatureLength) {
      return false;
    }
    try {
      return await _ed25519.verify(
        messageBytes,
        signature: Signature(
          signatureBytes,
          publicKey: SimplePublicKey(
            publicKeyBytes,
            type: KeyPairType.ed25519,
          ),
        ),
      );
    } catch (_) {
      return false;
    }
  }

  Future<String> sha256Hex(List<int> bytes) async {
    final digest = await _sha256.hash(bytes);
    return digest.bytes
        .map((b) => (b & 0xFF).toRadixString(16).padLeft(2, '0'))
        .join();
  }

  Future<Uint8List> sha256Bytes(List<int> bytes) async {
    final digest = await _sha256.hash(bytes);
    return Uint8List.fromList(digest.bytes);
  }

  Future<Uint8List> sha256Utf8Bytes(String value) {
    return sha256Bytes(utf8.encode(value));
  }

  // ---- V1 Legacy Helpers (kept for Unverified backward compatibility) ----

  Future<Uint8List> signDhtRegisterPayload({
    required SimpleKeyPair ed25519KeyPair,
    required String userUuid,
    required String nodeAddress,
    required List<int> ed25519PublicKey,
    required List<int> x25519PublicKey,
    required int timestampMs,
  }) async {
    final payload = utf8.encode(
      'ISPCHAT-DHT-V1|$userUuid|$nodeAddress|${base64Encode(ed25519PublicKey)}|'
      '${base64Encode(x25519PublicKey)}|$timestampMs',
    );
    return signRaw(ed25519KeyPair, payload);
  }

  Future<Uint8List> signEnvelopePayload({
    required SimpleKeyPair ed25519KeyPair,
    required String messageId,
    required String senderUuid,
    required String receiverUuid,
    required int messageType,
    required List<int> encryptedPayload,
    required int timestampMs,
  }) async {
    final digestHex = await sha256Hex(encryptedPayload);
    final payload = utf8.encode(
      'ISPCHAT-MSG-V1|$messageId|$senderUuid|$receiverUuid|'
      '$messageType|$digestHex|$timestampMs',
    );
    return signRaw(ed25519KeyPair, payload);
  }

  Future<Uint8List> encryptPayloadE2E({
    required List<int> plaintext,
    required SimpleKeyPair senderX25519KeyPair,
    required List<int> recipientX25519PublicKey,
  }) async {
    final shared = await _x25519.sharedSecretKey(
      keyPair: senderX25519KeyPair,
      remotePublicKey: SimplePublicKey(
        recipientX25519PublicKey,
        type: KeyPairType.x25519,
      ),
    );
    final aesKey = await _hkdf.deriveKey(
      secretKey: shared,
      nonce: const <int>[],
      info: utf8.encode('ISPCHAT-E2E-V1'),
    );
    final nonce = _randomBytes(CanonicalTranscripts.chacha20NonceLength);
    final box = await _chacha20.encrypt(
      plaintext,
      secretKey: aesKey,
      nonce: nonce,
    );
    return Uint8List.fromList(<int>[
      ...nonce,
      ...box.mac.bytes,
      ...box.cipherText,
    ]);
  }

  Future<Uint8List> decryptPayloadE2E({
    required List<int> encryptedBlob,
    required SimpleKeyPair recipientX25519KeyPair,
    required List<int> senderX25519PublicKey,
  }) async {
    if (encryptedBlob.length < 28) {
      throw const FormatException('Encrypted E2E blob is truncated.');
    }
    final nonce = encryptedBlob.sublist(0, 12);
    final mac = Mac(encryptedBlob.sublist(12, 28));
    final cipherText = encryptedBlob.sublist(28);
    final shared = await _x25519.sharedSecretKey(
      keyPair: recipientX25519KeyPair,
      remotePublicKey: SimplePublicKey(
        senderX25519PublicKey,
        type: KeyPairType.x25519,
      ),
    );
    final aesKey = await _hkdf.deriveKey(
      secretKey: shared,
      nonce: const <int>[],
      info: utf8.encode('ISPCHAT-E2E-V1'),
    );
    final clear = await _chacha20.decrypt(
      SecretBox(cipherText, nonce: nonce, mac: mac),
      secretKey: aesKey,
    );
    return Uint8List.fromList(clear);
  }

  // ---- V2 Multi-Device E2EE (exact Java CanonicalTranscripts parity) ----

  Future<MultiDeviceEncryptedPayload> encryptMultiDevicePayload({
    required String messageId,
    required String senderUuid,
    required String receiverUuid,
    required List<int> plaintext,
    required Map<String, List<int>> targetDeviceX25519Pubs,
  }) async {
    if (targetDeviceX25519Pubs.isEmpty) {
      throw ArgumentError('At least one target device key is required.');
    }
    final normMsgId = CanonicalTranscripts.canonicalUuid(messageId);
    final normSender = CanonicalTranscripts.canonicalUuid(senderUuid);
    final normReceiver = CanonicalTranscripts.canonicalUuid(receiverUuid);

    final contentKeyBytes = _randomBytes(CanonicalTranscripts.rawKeyLength);
    final contentNonce = _randomBytes(CanonicalTranscripts.chacha20NonceLength);
    final contentAad = utf8.encode('$normMsgId|$normSender|$normReceiver');
    final contentBox = await _chacha20.encrypt(
      plaintext,
      secretKey: SecretKey(contentKeyBytes),
      nonce: contentNonce,
      aad: contentAad,
    );

    final ephPair = await _x25519.newKeyPair();
    final ephPub = (await ephPair.extractPublicKey()).bytes;

    final payload = MultiDeviceEncryptedPayload()
      ..ephemeralX25519Pub = ephPub
      ..contentNonce = contentNonce
      ..ciphertextWithTag = <int>[
        ...contentBox.cipherText,
        ...contentBox.mac.bytes,
      ];

    for (final entry in targetDeviceX25519Pubs.entries) {
      final normDeviceId = CanonicalTranscripts.canonicalUuid(entry.key);
      final targetXPub = entry.value;
      if (targetXPub.length != CanonicalTranscripts.rawKeyLength) {
        throw ArgumentError('Target device X25519 key must be 32 bytes.');
      }
      final shared = await _x25519.sharedSecretKey(
        keyPair: ephPair,
        remotePublicKey: SimplePublicKey(targetXPub, type: KeyPairType.x25519),
      );
      final wrapKey = await _hkdf.deriveKey(
        secretKey: shared,
        nonce: <int>[...ephPub, ...targetXPub],
        info: utf8.encode('ISPCHAT-WRAP-V2:$normDeviceId'),
      );
      final wrapNonce = _randomBytes(CanonicalTranscripts.chacha20NonceLength);
      final wrapBox = await _chacha20.encrypt(
        contentKeyBytes,
        secretKey: wrapKey,
        nonce: wrapNonce,
        aad: utf8.encode('$normMsgId|$normDeviceId'),
      );
      payload.wrappedKeys.add(
        WrappedContentKey()
          ..targetDeviceId = normDeviceId
          ..wrappedKeyBlob = <int>[
            ...wrapNonce,
            ...wrapBox.cipherText,
            ...wrapBox.mac.bytes,
          ],
      );
    }

    return payload;
  }

  Future<Uint8List> decryptMultiDevicePayload({
    required String messageId,
    required String senderUuid,
    required String receiverUuid,
    required String targetDeviceId,
    required SimpleKeyPair targetDeviceX25519KeyPair,
    required MultiDeviceEncryptedPayload payload,
  }) async {
    final normMsgId = CanonicalTranscripts.canonicalUuid(messageId);
    final normSender = CanonicalTranscripts.canonicalUuid(senderUuid);
    final normReceiver = CanonicalTranscripts.canonicalUuid(receiverUuid);
    final normDeviceId = CanonicalTranscripts.canonicalUuid(targetDeviceId);

    if (payload.ephemeralX25519Pub.length != CanonicalTranscripts.rawKeyLength ||
        payload.contentNonce.length != CanonicalTranscripts.chacha20NonceLength ||
        payload.ciphertextWithTag.length < CanonicalTranscripts.poly1305TagLength) {
      throw const FormatException('Malformed MultiDeviceEncryptedPayload.');
    }

    WrappedContentKey? match;
    for (final wk in payload.wrappedKeys) {
      try {
        if (CanonicalTranscripts.canonicalUuid(wk.targetDeviceId) == normDeviceId) {
          match = wk;
          break;
        }
      } catch (_) {
        // Ignore malformed foreign device entries.
      }
    }
    if (match == null) {
      throw StateError('No wrapped key present for device $normDeviceId.');
    }
    final blob = match.wrappedKeyBlob;
    if (blob.length != 60) {
      throw FormatException('Invalid wrapped key blob length: ${blob.length}.');
    }

    final myXPub = (await targetDeviceX25519KeyPair.extractPublicKey()).bytes;
    final shared = await _x25519.sharedSecretKey(
      keyPair: targetDeviceX25519KeyPair,
      remotePublicKey: SimplePublicKey(
        payload.ephemeralX25519Pub,
        type: KeyPairType.x25519,
      ),
    );
    final wrapKey = await _hkdf.deriveKey(
      secretKey: shared,
      nonce: <int>[...payload.ephemeralX25519Pub, ...myXPub],
      info: utf8.encode('ISPCHAT-WRAP-V2:$normDeviceId'),
    );

    final wrapNonce = blob.sublist(0, 12);
    final wrapCipher = blob.sublist(12, 44);
    final wrapMac = Mac(blob.sublist(44, 60));
    final contentKeyBytes = await _chacha20.decrypt(
      SecretBox(wrapCipher, nonce: wrapNonce, mac: wrapMac),
      secretKey: wrapKey,
      aad: utf8.encode('$normMsgId|$normDeviceId'),
    );

    final ctLen = payload.ciphertextWithTag.length - 16;
    final cipherText = payload.ciphertextWithTag.sublist(0, ctLen);
    final contentMac = Mac(payload.ciphertextWithTag.sublist(ctLen));
    final clear = await _chacha20.decrypt(
      SecretBox(cipherText, nonce: payload.contentNonce, mac: contentMac),
      secretKey: SecretKey(contentKeyBytes),
      aad: utf8.encode('$normMsgId|$normSender|$normReceiver'),
    );
    return Uint8List.fromList(clear);
  }

  // ---- V2 Signing & Verification ----

  Future<DeviceDelegationCert> signDeviceDelegationCert({
    required IdentityBundle rootIdentity,
    required String targetDeviceId,
    required DeviceRole role,
    required List<int> deviceEd25519Pub,
    required List<int> deviceX25519Pub,
    int validityDays = 365,
    int? nowMs,
  }) async {
    final issuedAt = nowMs ?? DateTime.now().millisecondsSinceEpoch;
    final expiresAt = issuedAt + validityDays * 86400000;
    final cert = DeviceDelegationCert()
      ..certId = generateTemporalUuidV7()
      ..userUuid = CanonicalTranscripts.canonicalUuid(rootIdentity.userUuid)
      ..deviceId = CanonicalTranscripts.canonicalUuid(targetDeviceId)
      ..role = role
      ..deviceEd25519Pub = deviceEd25519Pub
      ..deviceX25519Pub = deviceX25519Pub
      ..issuedAtMs = $fixnum.Int64(issuedAt)
      ..expiresAtMs = $fixnum.Int64(expiresAt);
    final transcript = CanonicalTranscripts.buildDeviceDelegationTranscript(cert);
    cert.rootSignature = await signRaw(rootIdentity.ed25519KeyPair, transcript);
    return cert;
  }

  Future<bool> verifyPrincipalSigningCert(
    PrincipalSigningCert cert, {
    List<int>? pinnedRootEd25519Pub,
    int? nowMs,
  }) async {
    final rootPub =
        pinnedRootEd25519Pub ?? bootstrapConfig.decodePinnedRootPublicKeyBytes();
    if (rootPub == null) {
      return false;
    }
    final checkTime = nowMs ?? DateTime.now().millisecondsSinceEpoch;
    if (cert.expiresAtMs.toInt() < checkTime) {
      return false;
    }
    try {
      final transcript = CanonicalTranscripts.buildPrincipalSigningCertTranscript(cert);
      return await verifySignature(
        publicKeyBytes: rootPub,
        messageBytes: transcript,
        signatureBytes: cert.rootSignature,
      );
    } catch (_) {
      return false;
    }
  }

  Future<bool> verifyVerificationCredential(
    VerificationCredential cred, {
    List<int>? pinnedRootEd25519Pub,
    int? nowMs,
  }) async {
    final checkTime = nowMs ?? DateTime.now().millisecondsSinceEpoch;
    if (cred.expiresAtMs.toInt() < checkTime || !cred.hasPrincipalCert()) {
      return false;
    }
    if (!await verifyPrincipalSigningCert(
      cred.principalCert,
      pinnedRootEd25519Pub: pinnedRootEd25519Pub,
      nowMs: checkTime,
    )) {
      return false;
    }
    if (!await verifyUuidBinding(cred.userUuid, cred.rootEd25519Pub)) {
      return false;
    }
    if (cred.docType == DocumentType.CNPJ &&
        cred.publicCnpj.trim().isNotEmpty &&
        !CanonicalTranscripts.isValidCnpj(cred.publicCnpj)) {
      return false;
    }
    try {
      final transcript = CanonicalTranscripts.buildVerificationCredentialTranscript(cred);
      return await verifySignature(
        publicKeyBytes: cred.principalCert.principalEd25519Pub,
        messageBytes: transcript,
        signatureBytes: cred.principalSignature,
      );
    } catch (_) {
      return false;
    }
  }

  Future<bool> verifyDeviceDelegationCert(
    DeviceDelegationCert cert, {
    required List<int> rootEd25519Pub,
    int? nowMs,
  }) async {
    final checkTime = nowMs ?? DateTime.now().millisecondsSinceEpoch;
    if (cert.expiresAtMs.toInt() < checkTime) {
      return false;
    }
    if (!await verifyUuidBinding(cert.userUuid, rootEd25519Pub)) {
      return false;
    }
    try {
      final transcript = CanonicalTranscripts.buildDeviceDelegationTranscript(cert);
      return await verifySignature(
        publicKeyBytes: rootEd25519Pub,
        messageBytes: transcript,
        signatureBytes: cert.rootSignature,
      );
    } catch (_) {
      return false;
    }
  }

  Future<HomeNodeClaim> signSelfHomeNodeClaim({
    required IdentityBundle rootIdentity,
    required String homeNodeAddress,
    int routingEpoch = 0,
    int? nowMs,
  }) async {
    final issuedAt = nowMs ?? DateTime.now().millisecondsSinceEpoch;
    final claim = HomeNodeClaim()
      ..userUuid = CanonicalTranscripts.canonicalUuid(rootIdentity.userUuid)
      ..homeNodeAddress = homeNodeAddress
      ..routingEpoch = $fixnum.Int64(routingEpoch)
      ..issuedAtMs = $fixnum.Int64(issuedAt)
      ..signedByPrincipal = false;
    final transcript = CanonicalTranscripts.buildHomeNodeClaimTranscript(claim);
    claim.claimSignature = await signRaw(rootIdentity.ed25519KeyPair, transcript);
    return claim;
  }

  Future<bool> verifyHomeNodeClaim(
    HomeNodeClaim claim, {
    List<int>? userRootEd25519Pub,
    PrincipalSigningCert? principalCert,
    List<int>? pinnedRootEd25519Pub,
    int? nowMs,
  }) async {
    try {
      final transcript = CanonicalTranscripts.buildHomeNodeClaimTranscript(claim);
      if (claim.signedByPrincipal) {
        if (principalCert == null ||
            !await verifyPrincipalSigningCert(
              principalCert,
              pinnedRootEd25519Pub: pinnedRootEd25519Pub,
              nowMs: nowMs,
            )) {
          return false;
        }
        return await verifySignature(
          publicKeyBytes: principalCert.principalEd25519Pub,
          messageBytes: transcript,
          signatureBytes: claim.claimSignature,
        );
      }
      if (userRootEd25519Pub == null ||
          !await verifyUuidBinding(claim.userUuid, userRootEd25519Pub)) {
        return false;
      }
      return await verifySignature(
        publicKeyBytes: userRootEd25519Pub,
        messageBytes: transcript,
        signatureBytes: claim.claimSignature,
      );
    } catch (_) {
      return false;
    }
  }

  Future<Uint8List> signDhtSessionV2({
    required SimpleKeyPair deviceEd25519KeyPair,
    required String userUuid,
    required String deviceId,
    required String nodeAddress,
    required List<int> deviceEd25519Pub,
    required List<int> deviceX25519Pub,
    required List<int> rootEd25519Pub,
    required int timestampMs,
  }) {
    final transcript = CanonicalTranscripts.buildDhtSessionV2Transcript(
      userUuid: userUuid,
      deviceId: deviceId,
      nodeAddress: nodeAddress,
      deviceEd25519Pub: deviceEd25519Pub,
      deviceX25519Pub: deviceX25519Pub,
      rootEd25519Pub: rootEd25519Pub,
      timestampMs: timestampMs,
    );
    return signRaw(deviceEd25519KeyPair, transcript);
  }

  Future<bool> verifyUserSessionInfo(
    UserSessionInfo info, {
    required String expectedUserUuid,
    List<int>? pinnedRootEd25519Pub,
    int? nowMs,
  }) async {
    if (info.deviceId.trim().isEmpty || info.delegationCert.certId.isEmpty) {
      return false;
    }
    try {
      if (CanonicalTranscripts.canonicalUuid(info.delegationCert.userUuid) !=
              CanonicalTranscripts.canonicalUuid(expectedUserUuid) ||
          CanonicalTranscripts.canonicalUuid(info.delegationCert.deviceId) !=
              CanonicalTranscripts.canonicalUuid(info.deviceId)) {
        return false;
      }
      final rootPub = info.rootEd25519Pub;
      if (!await verifyDeviceDelegationCert(
        info.delegationCert,
        rootEd25519Pub: rootPub,
        nowMs: nowMs,
      )) {
        return false;
      }
      if (!_bytesEqual(info.delegationCert.deviceEd25519Pub, info.deviceEd25519Pub) ||
          !_bytesEqual(info.delegationCert.deviceX25519Pub, info.x25519PublicKey)) {
        return false;
      }
      if (info.hasVerificationCredential() &&
          !await verifyVerificationCredential(
            info.verificationCredential,
            pinnedRootEd25519Pub: pinnedRootEd25519Pub,
            nowMs: nowMs,
          )) {
        return false;
      }
      final transcript = CanonicalTranscripts.buildDhtSessionV2Transcript(
        userUuid: expectedUserUuid,
        deviceId: info.deviceId,
        nodeAddress: info.nodeAddress,
        deviceEd25519Pub: info.deviceEd25519Pub,
        deviceX25519Pub: info.x25519PublicKey,
        rootEd25519Pub: rootPub,
        timestampMs: info.timestamp.toInt(),
      );
      final sigToVerify =
          info.sessionSignature.isNotEmpty ? info.sessionSignature : const <int>[];
      return await verifySignature(
        publicKeyBytes: info.deviceEd25519Pub,
        messageBytes: transcript,
        signatureBytes: sigToVerify,
      );
    } catch (_) {
      return false;
    }
  }

  Future<Uint8List> signMessageEnvelopeV2({
    required SimpleKeyPair deviceEd25519KeyPair,
    required String messageId,
    required String senderUuid,
    required String senderDeviceId,
    required String receiverUuid,
    required MessageType type,
    required String refMessageId,
    required List<int> encryptedPayload,
    required int timestampMs,
  }) async {
    final digest = await sha256Bytes(encryptedPayload);
    final transcript = CanonicalTranscripts.buildMessageEnvelopeV2Transcript(
      messageId: messageId,
      senderUuid: senderUuid,
      senderDeviceId: senderDeviceId,
      receiverUuid: receiverUuid,
      typeValue: type.value,
      refMessageId: refMessageId,
      encryptedPayloadSha256: digest,
      timestampMs: timestampMs,
    );
    return signRaw(deviceEd25519KeyPair, transcript);
  }

  Future<bool> verifyServerEnvelopeV2(ServerEnvelope env) async {
    if (env.senderDeviceId.trim().isEmpty) {
      return false;
    }
    try {
      final digest = await sha256Bytes(env.encryptedPayload);
      final transcript = CanonicalTranscripts.buildMessageEnvelopeV2Transcript(
        messageId: env.messageId,
        senderUuid: env.senderUuid,
        senderDeviceId: env.senderDeviceId,
        receiverUuid: env.receiverUuid,
        typeValue: env.type.value,
        refMessageId: env.refMessageId,
        encryptedPayloadSha256: digest,
        timestampMs: env.timestamp.toInt(),
      );
      return await verifySignature(
        publicKeyBytes: env.senderPublicKey,
        messageBytes: transcript,
        signatureBytes: env.signature,
      );
    } catch (_) {
      return false;
    }
  }

  Future<bool> verifyIdentityReplacementNotice(
    IdentityReplacementNotice notice, {
    required PrincipalSigningCert principalCert,
    List<int>? pinnedRootEd25519Pub,
    int? nowMs,
  }) async {
    if (!await verifyPrincipalSigningCert(
      principalCert,
      pinnedRootEd25519Pub: pinnedRootEd25519Pub,
      nowMs: nowMs,
    )) {
      return false;
    }
    try {
      final transcript = CanonicalTranscripts.buildIdentityReplacementTranscript(
        notice,
      );
      return await verifySignature(
        publicKeyBytes: principalCert.principalEd25519Pub,
        messageBytes: transcript,
        signatureBytes: notice.principalSignature,
      );
    } catch (_) {
      return false;
    }
  }

  Future<bool> verifyRevocationEvent(
    RevocationEvent event, {
    required PrincipalSigningCert principalCert,
    List<int>? pinnedRootEd25519Pub,
    int? nowMs,
  }) async {
    if (!await verifyPrincipalSigningCert(
      principalCert,
      pinnedRootEd25519Pub: pinnedRootEd25519Pub,
      nowMs: nowMs,
    )) {
      return false;
    }
    try {
      final transcript = CanonicalTranscripts.buildRevocationEventTranscript(event);
      return await verifySignature(
        publicKeyBytes: principalCert.principalEd25519Pub,
        messageBytes: transcript,
        signatureBytes: event.principalSignature,
      );
    } catch (_) {
      return false;
    }
  }

  Future<TurnCredentialRequest> buildSignedTurnCredentialRequest({
    required String userUuid,
    required LocalDeviceIdentity deviceIdentity,
    VerificationCredential? verificationCredential,
    int? nowMs,
  }) async {
    final ts = nowMs ?? DateTime.now().millisecondsSinceEpoch;
    final transcript = CanonicalTranscripts.buildTurnCredentialRequestTranscript(
      userUuid: userUuid,
      deviceId: deviceIdentity.deviceId,
      timestampMs: ts,
    );
    final req = TurnCredentialRequest()
      ..userUuid = CanonicalTranscripts.canonicalUuid(userUuid)
      ..deviceId = CanonicalTranscripts.canonicalUuid(deviceIdentity.deviceId)
      ..timestampMs = $fixnum.Int64(ts)
      ..callerPublicKey = deviceIdentity.ed25519PublicKeyBytes
      ..signature = await signRaw(deviceIdentity.ed25519KeyPair, transcript);
    if (deviceIdentity.delegationCert != null) {
      req.delegationCert = deviceIdentity.delegationCert!;
    }
    if (verificationCredential != null) {
      req.verificationCredential = verificationCredential;
    }
    return req;
  }

  Future<GetCredentialRequest> buildSignedGetCredentialRequest({
    required String userUuid,
    required LocalDeviceIdentity deviceIdentity,
    int? nowMs,
  }) async {
    final ts = nowMs ?? DateTime.now().millisecondsSinceEpoch;
    final transcript = CanonicalTranscripts.buildGetOrRenewCredentialTranscript(
      userUuid: userUuid,
      deviceId: deviceIdentity.deviceId,
      timestampMs: ts,
    );
    final req = GetCredentialRequest()
      ..userUuid = CanonicalTranscripts.canonicalUuid(userUuid)
      ..deviceId = CanonicalTranscripts.canonicalUuid(deviceIdentity.deviceId)
      ..timestampMs = $fixnum.Int64(ts)
      ..callerPublicKey = deviceIdentity.ed25519PublicKeyBytes
      ..signature = await signRaw(deviceIdentity.ed25519KeyPair, transcript);
    if (deviceIdentity.delegationCert != null) {
      req.delegationCert = deviceIdentity.delegationCert!;
    }
    return req;
  }

  Future<RevokeDeviceRequest> buildSignedRevokeDeviceRequest({
    required IdentityBundle rootIdentity,
    required String revokedDeviceId,
    required String reason,
    int? nowMs,
  }) async {
    final ts = nowMs ?? DateTime.now().millisecondsSinceEpoch;
    final transcript = CanonicalTranscripts.buildRevokeDeviceTranscript(
      userUuid: rootIdentity.userUuid,
      revokedDeviceId: revokedDeviceId,
      reason: reason,
      timestampMs: ts,
    );
    return RevokeDeviceRequest()
      ..userUuid = CanonicalTranscripts.canonicalUuid(rootIdentity.userUuid)
      ..revokedDeviceId = CanonicalTranscripts.canonicalUuid(revokedDeviceId)
      ..reason = reason
      ..timestampMs = $fixnum.Int64(ts)
      ..rootSignature = await signRaw(rootIdentity.ed25519KeyPair, transcript);
  }

  Future<PortAccountRequest> buildSignedPortAccountRequest({
    required IdentityBundle rootIdentity,
    required String targetHomeNodeAddress,
    required String targetIspCnpj,
    required int expectedRoutingEpoch,
    CollaboratorAuthCert? targetCollaboratorCert,
    List<int>? targetCollaboratorSignature,
    int? nowMs,
  }) async {
    final ts = nowMs ?? DateTime.now().millisecondsSinceEpoch;
    final cleanCnpj = CanonicalTranscripts.normalizeAndValidateCnpj(targetIspCnpj);
    final transcript = CanonicalTranscripts.buildPortAccountTranscript(
      userUuid: rootIdentity.userUuid,
      targetHomeNodeAddress: targetHomeNodeAddress,
      targetIspCnpj: cleanCnpj,
      expectedRoutingEpoch: expectedRoutingEpoch,
      timestampMs: ts,
    );
    final req = PortAccountRequest()
      ..userUuid = CanonicalTranscripts.canonicalUuid(rootIdentity.userUuid)
      ..targetHomeNodeAddress = targetHomeNodeAddress
      ..targetIspCnpj = cleanCnpj
      ..expectedRoutingEpoch = $fixnum.Int64(expectedRoutingEpoch)
      ..timestampMs = $fixnum.Int64(ts)
      ..rootSignature = await signRaw(rootIdentity.ed25519KeyPair, transcript);
    if (targetCollaboratorCert != null) {
      req.targetCollaboratorCert = targetCollaboratorCert;
    }
    if (targetCollaboratorSignature != null &&
        targetCollaboratorSignature.isNotEmpty) {
      req.targetCollaboratorSignature = targetCollaboratorSignature;
    }
    return req;
  }

  Future<UserVerificationConsent> buildSignedUserVerificationConsent({
    required IdentityBundle userIdentity,
    required DocumentType docType,
    required String rawDocument,
    required String ispCnpj,
    int? nowMs,
  }) async {
    final normalizedDoc = CanonicalTranscripts.normalizeAndValidateDocument(
      docType,
      rawDocument,
    );
    final normalizedIsp = CanonicalTranscripts.normalizeAndValidateCnpj(ispCnpj);
    final docSha = await sha256Utf8Bytes(normalizedDoc);
    final ts = nowMs ?? DateTime.now().millisecondsSinceEpoch;
    final consent = UserVerificationConsent()
      ..userUuid = CanonicalTranscripts.canonicalUuid(userIdentity.userUuid)
      ..userEd25519Pub = userIdentity.ed25519PublicKeyBytes
      ..userX25519Pub = userIdentity.x25519PublicKeyBytes
      ..docType = docType
      ..docSha256 = docSha
      ..ispCnpj = normalizedIsp
      ..timestampMs = $fixnum.Int64(ts);
    final transcript = CanonicalTranscripts.buildUserVerificationConsentTranscript(
      consent,
    );
    consent.userSignature = await signRaw(userIdentity.ed25519KeyPair, transcript);
    return consent;
  }

  Future<CollaboratorAuthCert> buildSignedCollaboratorAuthCert({
    required IdentityBundle partnerIdentity,
    required String ispCnpj,
    required String collaboratorUuid,
    required String collaboratorCpf,
    required List<int> collaboratorEd25519Pub,
    int validityDays = 180,
    int? nowMs,
  }) async {
    final issuedAt = nowMs ?? DateTime.now().millisecondsSinceEpoch;
    final cleanCnpj = CanonicalTranscripts.normalizeAndValidateCnpj(ispCnpj);
    final cleanCpf = CanonicalTranscripts.normalizeAndValidateCpf(collaboratorCpf);
    final cert = CollaboratorAuthCert()
      ..certId = generateTemporalUuidV7()
      ..ispCnpj = cleanCnpj
      ..partnerUuid = CanonicalTranscripts.canonicalUuid(partnerIdentity.userUuid)
      ..collaboratorUuid = CanonicalTranscripts.canonicalUuid(collaboratorUuid)
      ..collabCpfSha256 = await sha256Utf8Bytes(cleanCpf)
      ..collabEd25519Pub = collaboratorEd25519Pub
      ..issuedAtMs = $fixnum.Int64(issuedAt)
      ..expiresAtMs = $fixnum.Int64(issuedAt + validityDays * 86400000);
    final transcript = CanonicalTranscripts.buildCollaboratorAuthTranscript(cert);
    cert.partnerSignature = await signRaw(
      partnerIdentity.ed25519KeyPair,
      transcript,
    );
    return cert;
  }

  Future<SubmitVerificationRequest> buildSignedSubmitVerificationRequest({
    required IdentityBundle collaboratorIdentity,
    required CollaboratorAuthCert collaboratorCert,
    required UserVerificationConsent userConsent,
    required String rawDocument,
    required String publicDisplayName,
    required String homeNodeAddress,
    int? nowMs,
  }) async {
    final normalizedDoc = CanonicalTranscripts.normalizeAndValidateDocument(
      userConsent.docType,
      rawDocument,
    );
    final cleanIsp = CanonicalTranscripts.normalizeAndValidateCnpj(
      collaboratorCert.ispCnpj,
    );
    final ts = nowMs ?? DateTime.now().millisecondsSinceEpoch;
    final docSha = await sha256Utf8Bytes(normalizedDoc);
    final transcript =
        CanonicalTranscripts.buildCollaboratorUserAttestationTranscript(
      ispCnpj: cleanIsp,
      collaboratorUuid: collaboratorIdentity.userUuid,
      userUuid: userConsent.userUuid,
      docType: userConsent.docType,
      docSha256: docSha,
      homeNodeAddress: homeNodeAddress,
      userConsentSignature: userConsent.userSignature,
      attestedAtMs: ts,
    );
    return SubmitVerificationRequest()
      ..ispCnpj = cleanIsp
      ..collaboratorCert = collaboratorCert
      ..userConsent = userConsent
      ..normalizedDocument = normalizedDoc
      ..publicDisplayName = publicDisplayName.trim()
      ..homeNodeAddress = homeNodeAddress.trim()
      ..attestedAtMs = $fixnum.Int64(ts)
      ..collaboratorSignature = await signRaw(
        collaboratorIdentity.ed25519KeyPair,
        transcript,
      );
  }

  Future<SubmitCpfRebindRequest> buildSignedSubmitCpfRebindRequest({
    required IdentityBundle collaboratorIdentity,
    required CollaboratorAuthCert collaboratorCert,
    required String oldUserUuid,
    required UserVerificationConsent newUserConsent,
    required String rawCpf,
    required String homeNodeAddress,
    int? nowMs,
  }) async {
    final normalizedCpf = CanonicalTranscripts.normalizeAndValidateCpf(rawCpf);
    final cleanIsp = CanonicalTranscripts.normalizeAndValidateCnpj(
      collaboratorCert.ispCnpj,
    );
    final ts = nowMs ?? DateTime.now().millisecondsSinceEpoch;
    final cpfSha = await sha256Utf8Bytes(normalizedCpf);
    final transcript = CanonicalTranscripts.buildCpfRebindTranscript(
      ispCnpj: cleanIsp,
      collaboratorUuid: collaboratorIdentity.userUuid,
      oldUserUuid: oldUserUuid,
      newUserUuid: newUserConsent.userUuid,
      cpfSha256: cpfSha,
      homeNodeAddress: homeNodeAddress,
      newConsentSignature: newUserConsent.userSignature,
      attestedAtMs: ts,
    );
    return SubmitCpfRebindRequest()
      ..ispCnpj = cleanIsp
      ..collaboratorCert = collaboratorCert
      ..oldUserUuid = CanonicalTranscripts.canonicalUuid(oldUserUuid)
      ..newUserConsent = newUserConsent
      ..normalizedCpf = normalizedCpf
      ..homeNodeAddress = homeNodeAddress.trim()
      ..attestedAtMs = $fixnum.Int64(ts)
      ..collaboratorSignature = await signRaw(
        collaboratorIdentity.ed25519KeyPair,
        transcript,
      );
  }

  // ---- QR & Deep Link URI Protocols ----

  Future<String> buildSignedContactUri({
    required IdentityBundle identity,
    String? displayName,
    VerificationCredential? verificationCredential,
  }) async {
    final kB64 = base64UrlEncode(identity.ed25519PublicKeyBytes);
    final xB64 = base64UrlEncode(identity.x25519PublicKeyBytes);
    final message = utf8.encode(
      'ISPCHAT-CONTACT-V1|${identity.userUuid}|$kB64|$xB64',
    );
    final sig = await signRaw(identity.ed25519KeyPair, message);
    final params = <String, String>{
      'u': identity.userUuid,
      'k': kB64,
      'x': xB64,
      'sig': base64UrlEncode(sig),
    };
    if (displayName != null && displayName.trim().isNotEmpty) {
      params['n'] = displayName.trim();
    }
    if (verificationCredential != null &&
        verificationCredential.credentialId.isNotEmpty) {
      params['vc'] = base64UrlEncode(verificationCredential.writeToBuffer());
    }
    return Uri(
      scheme: 'ispchat',
      host: 'contact',
      queryParameters: params,
    ).toString();
  }

  Future<ContactBundle> parseAndVerifyContactUri(
    String uriString, {
    List<int>? pinnedRootEd25519Pub,
    int? nowMs,
  }) async {
    final uri = Uri.tryParse(uriString.trim());
    if (uri == null || uri.scheme != 'ispchat' || uri.host != 'contact') {
      throw const FormatException('Invalid ispchat://contact URI.');
    }
    final u = uri.queryParameters['u'];
    final k = uri.queryParameters['k'];
    final x = uri.queryParameters['x'];
    final sig = uri.queryParameters['sig'];
    if (u == null || k == null || x == null || sig == null) {
      throw const FormatException('Missing required fields in contact URI.');
    }
    final kBytes = _decodeBase64Url(k);
    final xBytes = _decodeBase64Url(x);
    final sigBytes = _decodeBase64Url(sig);

    if (!await verifyUuidBinding(u, kBytes)) {
      throw const FormatException('UUID is not cryptographically bound to key.');
    }
    final message = utf8.encode('ISPCHAT-CONTACT-V1|$u|$k|$x');
    final valid = await verifySignature(
      publicKeyBytes: kBytes,
      messageBytes: message,
      signatureBytes: sigBytes,
    );
    if (!valid) {
      throw const FormatException('Invalid contact URI Ed25519 signature.');
    }

    final vcB64 = uri.queryParameters['vc'];
    VerificationCredential? vc;
    var verified = false;
    if (vcB64 != null && vcB64.isNotEmpty) {
      vc = VerificationCredential.fromBuffer(_decodeBase64Url(vcB64));
      final vcValid = await verifyVerificationCredential(
        vc,
        pinnedRootEd25519Pub: pinnedRootEd25519Pub,
        nowMs: nowMs,
      );
      final keysMatch = CanonicalTranscripts.canonicalUuid(vc.userUuid) ==
              CanonicalTranscripts.canonicalUuid(u) &&
          _bytesEqual(vc.rootEd25519Pub, kBytes) &&
          _bytesEqual(vc.rootX25519Pub, xBytes);
      if (!vcValid || !keysMatch) {
        throw const FormatException(
          'Contact VerificationCredential failed Root Chain of Trust verification.',
        );
      }
      verified = true;
    }

    return ContactBundle(
      uuid: CanonicalTranscripts.canonicalUuid(u),
      ed25519PublicKey: kBytes,
      x25519PublicKey: xBytes,
      displayName: uri.queryParameters['n'],
      isVerified: verified,
      docType: vc?.docType,
      publicCnpj: vc != null && vc.publicCnpj.isNotEmpty ? vc.publicCnpj : null,
      publicDisplayName: vc != null && vc.publicDisplayName.isNotEmpty
          ? vc.publicDisplayName
          : null,
      verifyingIspCnpj:
          vc != null && vc.verifyingIspCnpj.isNotEmpty ? vc.verifyingIspCnpj : null,
      verifyingIspName:
          vc != null && vc.verifyingIspName.isNotEmpty ? vc.verifyingIspName : null,
      verificationCredential: vc,
    );
  }

  String buildVerificationConsentUri({
    required UserVerificationConsent consent,
    String? displayName,
  }) {
    final params = <String, String>{
      'p': base64UrlEncode(consent.writeToBuffer()),
    };
    if (displayName != null && displayName.trim().isNotEmpty) {
      params['n'] = displayName.trim();
    }
    return Uri(
      scheme: 'ispchat',
      host: 'verify-req',
      queryParameters: params,
    ).toString();
  }

  Future<VerificationConsentUriBundle> parseAndVerifyVerificationConsentUri(
    String uriString,
  ) async {
    final uri = Uri.tryParse(uriString.trim());
    if (uri == null || uri.scheme != 'ispchat' || uri.host != 'verify-req') {
      throw const FormatException('Invalid ispchat://verify-req URI.');
    }
    final payload = uri.queryParameters['p'];
    if (payload == null || payload.isEmpty) {
      throw const FormatException('Missing verification consent payload.');
    }
    final consent = UserVerificationConsent.fromBuffer(_decodeBase64Url(payload));
    if (!await verifyUuidBinding(consent.userUuid, consent.userEd25519Pub)) {
      throw const FormatException('Verification consent UUID binding mismatch.');
    }
    final transcript = CanonicalTranscripts.buildUserVerificationConsentTranscript(
      consent,
    );
    final ok = await verifySignature(
      publicKeyBytes: consent.userEd25519Pub,
      messageBytes: transcript,
      signatureBytes: consent.userSignature,
    );
    if (!ok) {
      throw const FormatException('Invalid verification consent signature.');
    }
    return VerificationConsentUriBundle(
      consent: consent,
      displayName: uri.queryParameters['n'],
    );
  }

  String buildDevicePairRequestUri({
    required LocalDeviceIdentity desktopIdentity,
  }) {
    return Uri(
      scheme: 'ispchat',
      host: 'pair-req',
      queryParameters: <String, String>{
        'd': CanonicalTranscripts.canonicalUuid(desktopIdentity.deviceId),
        'r': desktopIdentity.role.value.toString(),
        'ek': base64UrlEncode(desktopIdentity.ed25519PublicKeyBytes),
        'xk': base64UrlEncode(desktopIdentity.x25519PublicKeyBytes),
      },
    ).toString();
  }

  DevicePairRequestBundle parseDevicePairRequestUri(String uriString) {
    final uri = Uri.tryParse(uriString.trim());
    if (uri == null || uri.scheme != 'ispchat' || uri.host != 'pair-req') {
      throw const FormatException('Invalid ispchat://pair-req URI.');
    }
    final d = uri.queryParameters['d'];
    final rRaw = uri.queryParameters['r'];
    final ek = uri.queryParameters['ek'];
    final xk = uri.queryParameters['xk'];
    if (d == null || rRaw == null || ek == null || xk == null) {
      throw const FormatException('Missing fields in pair-req URI.');
    }
    final roleInt = int.tryParse(rRaw);
    final role = roleInt != null ? DeviceRole.valueOf(roleInt) : null;
    if (role == null) {
      throw const FormatException('Invalid device role in pair-req URI.');
    }
    final edBytes = _decodeBase64Url(ek);
    final xBytes = _decodeBase64Url(xk);
    if (edBytes.length != 32 || xBytes.length != 32) {
      throw const FormatException('Device public keys must be 32 bytes.');
    }
    return DevicePairRequestBundle(
      deviceId: CanonicalTranscripts.canonicalUuid(d),
      role: role,
      deviceEd25519Pub: edBytes,
      deviceX25519Pub: xBytes,
    );
  }

  String buildDevicePairGrantUri({
    required DeviceDelegationCert delegationCert,
    required Uint8List rootEd25519Pub,
    required Uint8List rootX25519Pub,
    String? homeNodeAddress,
    VerificationCredential? verificationCredential,
  }) {
    final params = <String, String>{
      'cert': base64UrlEncode(delegationCert.writeToBuffer()),
      'rk': base64UrlEncode(rootEd25519Pub),
      'rx': base64UrlEncode(rootX25519Pub),
    };
    if (homeNodeAddress != null && homeNodeAddress.trim().isNotEmpty) {
      params['hn'] = homeNodeAddress.trim();
    }
    if (verificationCredential != null &&
        verificationCredential.credentialId.isNotEmpty) {
      params['vc'] = base64UrlEncode(verificationCredential.writeToBuffer());
    }
    return Uri(
      scheme: 'ispchat',
      host: 'pair-grant',
      queryParameters: params,
    ).toString();
  }

  Future<DevicePairGrantBundle> parseAndVerifyDevicePairGrantUri(
    String uriString, {
    List<int>? pinnedRootEd25519Pub,
    int? nowMs,
  }) async {
    final uri = Uri.tryParse(uriString.trim());
    if (uri == null || uri.scheme != 'ispchat' || uri.host != 'pair-grant') {
      throw const FormatException('Invalid ispchat://pair-grant URI.');
    }
    final certB64 = uri.queryParameters['cert'];
    final rkB64 = uri.queryParameters['rk'];
    final rxB64 = uri.queryParameters['rx'];
    if (certB64 == null || rkB64 == null || rxB64 == null) {
      throw const FormatException('Missing fields in pair-grant URI.');
    }
    final cert = DeviceDelegationCert.fromBuffer(_decodeBase64Url(certB64));
    final rk = _decodeBase64Url(rkB64);
    final rx = _decodeBase64Url(rxB64);
    if (!await verifyDeviceDelegationCert(
      cert,
      rootEd25519Pub: rk,
      nowMs: nowMs,
    )) {
      throw const FormatException('Invalid or expired DeviceDelegationCert.');
    }

    VerificationCredential? vc;
    final vcB64 = uri.queryParameters['vc'];
    if (vcB64 != null && vcB64.isNotEmpty) {
      vc = VerificationCredential.fromBuffer(_decodeBase64Url(vcB64));
      final vcValid = await verifyVerificationCredential(
        vc,
        pinnedRootEd25519Pub: pinnedRootEd25519Pub,
        nowMs: nowMs,
      );
      if (!vcValid ||
          CanonicalTranscripts.canonicalUuid(vc.userUuid) !=
              CanonicalTranscripts.canonicalUuid(cert.userUuid)) {
        throw const FormatException('Invalid VerificationCredential in pair-grant.');
      }
    }

    return DevicePairGrantBundle(
      delegationCert: cert,
      rootEd25519Pub: rk,
      rootX25519Pub: rx,
      homeNodeAddress: uri.queryParameters['hn'],
      verificationCredential: vc,
    );
  }

  static Uint8List _decodeBase64Url(String raw) {
    final normalized = base64.normalize(raw.replaceAll('-', '+').replaceAll('_', '/'));
    return Uint8List.fromList(base64Decode(normalized));
  }

  static Uint8List _randomBytes(int length) {
    final bytes = Uint8List(length);
    for (var i = 0; i < length; i++) {
      bytes[i] = _secureRandom.nextInt(256);
    }
    return bytes;
  }

  static bool _bytesEqual(List<int> a, List<int> b) {
    if (a.length != b.length) return false;
    var diff = 0;
    for (var i = 0; i < a.length; i++) {
      diff |= a[i] ^ b[i];
    }
    return diff == 0;
  }
}
