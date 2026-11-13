import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:fixnum/fixnum.dart' as $fixnum;
import 'package:app/core/config/network_bootstrap_config.dart';
import 'package:app/core/crypto/identity_crypto_service.dart';
import 'package:app/core/grpc/generated/ispchat.pb.dart';
import 'package:app/core/grpc/ispchat_node_client.dart';
import 'package:app/core/security/blocklist_service.dart';
import 'package:app/core/storage/secure_local_vault.dart';

/// Decrypted WebRTC signaling frame delivered through [E2eSessionManager].
class DecryptedSignalingMessage {
  final String messageId;
  final String senderUuid;
  final String senderDeviceId;
  final MessageType type;
  final String payloadJson;
  final int timestampMs;

  const DecryptedSignalingMessage({
    required this.messageId,
    required this.senderUuid,
    required this.senderDeviceId,
    required this.type,
    required this.payloadJson,
    required this.timestampMs,
  });
}

/// Manages session authentication, V2 multi-device E2EE messaging, ACKs,
/// credential renewal, revocation handling, and contact migration.
class E2eSessionManager {
  final IdentityBundle? rootIdentity;
  final LocalDeviceIdentity deviceIdentity;
  final String activeUserUuid;
  final Uint8List activeRootEd25519Pub;
  final IspChatNodeClient nodeClient;
  final SecureLocalVault vault;
  final BlocklistService blocklist;
  final IdentityCryptoService crypto;
  final NetworkBootstrapConfig bootstrapConfig;
  final Uint8List? pinnedRootPubOverride;

  final StreamController<StoredChatMessage> _incomingMessages =
      StreamController<StoredChatMessage>.broadcast();
  final StreamController<DecryptedSignalingMessage> _incomingSignaling =
      StreamController<DecryptedSignalingMessage>.broadcast();
  final StreamController<RevocationEvent> _revocationEvents =
      StreamController<RevocationEvent>.broadcast();
  final StreamController<IdentityReplacementNotice> _replacementEvents =
      StreamController<IdentityReplacementNotice>.broadcast();
  final StreamController<String> _messageStatusUpdates =
      StreamController<String>.broadcast();

  StreamSubscription<ServerEnvelope>? _streamSub;
  VerificationCredential? _cachedCredential;
  HomeNodeClaim? _cachedHomeNodeClaim;
  bool _isRevokedLocally = false;

  E2eSessionManager({
    this.rootIdentity,
    required this.deviceIdentity,
    required this.activeUserUuid,
    required this.activeRootEd25519Pub,
    required this.nodeClient,
    required this.vault,
    required this.blocklist,
    this.crypto = const IdentityCryptoService(),
    this.bootstrapConfig = const NetworkBootstrapConfig(),
    this.pinnedRootPubOverride,
  });

  /// Convenience factory deriving a `MOBILE_ROOT` [LocalDeviceIdentity] from a
  /// root [IdentityBundle] when no separate desktop pairing record is passed.
  static Future<E2eSessionManager> createForRootIdentity({
    required IdentityBundle identity,
    required IspChatNodeClient nodeClient,
    required SecureLocalVault vault,
    required BlocklistService blocklist,
    IdentityCryptoService crypto = const IdentityCryptoService(),
    NetworkBootstrapConfig bootstrapConfig = const NetworkBootstrapConfig(),
    Uint8List? pinnedRootPubOverride,
  }) async {
    final deviceIdentity = await crypto.deriveMobileRootDeviceIdentity(identity);
    return E2eSessionManager(
      rootIdentity: identity,
      deviceIdentity: deviceIdentity,
      activeUserUuid: identity.userUuid,
      activeRootEd25519Pub: identity.ed25519PublicKeyBytes,
      nodeClient: nodeClient,
      vault: vault,
      blocklist: blocklist,
      crypto: crypto,
      bootstrapConfig: bootstrapConfig,
      pinnedRootPubOverride: pinnedRootPubOverride,
    );
  }

  Stream<StoredChatMessage> get incomingMessages => _incomingMessages.stream;
  Stream<DecryptedSignalingMessage> get incomingSignaling =>
      _incomingSignaling.stream;
  Stream<RevocationEvent> get revocationEvents => _revocationEvents.stream;
  Stream<IdentityReplacementNotice> get replacementEvents =>
      _replacementEvents.stream;
  Stream<String> get messageStatusUpdates => _messageStatusUpdates.stream;
  VerificationCredential? get currentCredential => _cachedCredential;
  HomeNodeClaim? get currentHomeNodeClaim => _cachedHomeNodeClaim;
  bool get isRevokedLocally => _isRevokedLocally;

  Uint8List? get _effectivePinnedRootPub {
    if (pinnedRootPubOverride != null) {
      return pinnedRootPubOverride;
    }
    return bootstrapConfig.decodePinnedRootPublicKeyBytes();
  }

  /// Registers session keys on the connected Home/Edge Node, auto-renews
  /// expiring Verified credentials (`<= 30d` remaining / `>= 60d` old), and
  /// opens the authenticated bidirectional message stream.
  Future<RegisterSessionResponse> initializeAndRegister({
    String? nodeAddressOverride,
  }) async {
    final nodeAddr = nodeAddressOverride ??
        nodeClient.connectedNodeAddress ??
        bootstrapConfig.principalFallbackAddress;

    _cachedCredential = await vault.loadVerificationCredential();
    _cachedHomeNodeClaim = await vault.loadHomeNodeClaim();

    await maybeRefreshCredential();

    if (_cachedHomeNodeClaim == null && rootIdentity != null) {
      final selfClaim = await crypto.signSelfHomeNodeClaim(
        rootIdentity: rootIdentity!,
        homeNodeAddress: nodeAddr,
      );
      _cachedHomeNodeClaim = selfClaim;
      await vault.saveHomeNodeClaim(selfClaim);
    }

    final regResp = await _sendRegisterSession(nodeAddr);

    await _streamSub?.cancel();
    _streamSub = nodeClient.incomingEnvelopes.listen(
      (env) {
        unawaited(handleServerEnvelope(env));
      },
    );

    final handshake = await _buildSignedClientEnvelope(
      receiverUuid: activeUserUuid,
      type: MessageType.AUTH_HANDSHAKE,
      encryptedPayload: const <int>[],
      includeCerts: true,
    );
    await nodeClient.openMessageStream(
      initialHandshake: handshake,
      onReconnected: () async {
        await _sendRegisterSession(nodeAddr);
        final reAuth = await _buildSignedClientEnvelope(
          receiverUuid: activeUserUuid,
          type: MessageType.AUTH_HANDSHAKE,
          encryptedPayload: const <int>[],
          includeCerts: true,
        );
        await nodeClient.sendEnvelope(reAuth);
      },
    );

    return regResp;
  }

  /// Automatically refreshes a stored [VerificationCredential] when it has
  /// crossed the 60-day renewal threshold (<= 30 days remaining).
  Future<VerificationCredential?> maybeRefreshCredential({int? nowMs}) async {
    final cred = _cachedCredential ?? await vault.loadVerificationCredential();
    if (cred == null) {
      return null;
    }
    final checkMs = nowMs ?? DateTime.now().millisecondsSinceEpoch;
    final remainingMs = cred.expiresAtMs.toInt() - checkMs;
    final ageMs = checkMs - cred.issuedAtMs.toInt();
    final needsRefresh =
        remainingMs <= bootstrapConfig.credentialRenewWindow.inMilliseconds ||
            ageMs >= const Duration(days: 60).inMilliseconds;
    if (!needsRefresh) {
      return cred;
    }

    try {
      final req = await crypto.buildSignedGetCredentialRequest(
        userUuid: activeUserUuid,
        deviceIdentity: deviceIdentity,
        nowMs: checkMs,
      );
      final bundle = await nodeClient.getOrRenewCredential(req);
      if (bundle.hasCredential()) {
        final rootPub = _effectivePinnedRootPub;
        final valid = rootPub == null ||
            await crypto.verifyVerificationCredential(
              bundle.credential,
              pinnedRootEd25519Pub: rootPub,
              nowMs: checkMs,
            );
        if (valid) {
          _cachedCredential = bundle.credential;
          await vault.saveVerificationCredential(bundle.credential);
          if (bundle.hasHomeNodeClaim()) {
            _cachedHomeNodeClaim = bundle.homeNodeClaim;
            await vault.saveHomeNodeClaim(bundle.homeNodeClaim);
          }
        }
      }
    } catch (_) {
      // Offline / Principal temporarily unavailable: keep cached credential if not expired.
    }
    return _cachedCredential;
  }

  /// Sends an encrypted multi-device E2EE text message, persisting it locally
  /// first so pending/queued/delivered states track seamlessly.
  Future<StoredChatMessage> sendTextMessage({
    required ContactBundle recipient,
    required String text,
  }) async {
    final messageId = IdentityCryptoService.generateTemporalUuidV7();
    final nowMs = DateTime.now().millisecondsSinceEpoch;

    final pending = StoredChatMessage(
      messageId: messageId,
      peerUuid: recipient.uuid,
      senderUuid: activeUserUuid,
      text: text,
      isOutgoing: true,
      timestampMs: nowMs,
      deliveryState: 'sending',
    );
    await vault.appendChatMessage(pending);

    final envelope = await _encryptAndSignMessage(
      messageId: messageId,
      recipient: recipient,
      type: MessageType.TEXT,
      plaintextBytes: utf8.encode(text),
      timestampMs: nowMs,
    );
    await nodeClient.sendEnvelope(envelope);
    return pending;
  }

  /// Encrypts and dispatches a WebRTC signaling frame (`RING`, `SDP_OFFER`,
  /// `SDP_ANSWER`, `ICE_CANDIDATE`, `CALL_DECLINE`) via `MultiDeviceEncryptedPayload`.
  Future<String> sendSignalingPayload({
    required ContactBundle recipient,
    required MessageType type,
    required Map<String, dynamic> payload,
    String refMessageId = '',
  }) async {
    final messageId = IdentityCryptoService.generateTemporalUuidV7();
    final nowMs = DateTime.now().millisecondsSinceEpoch;
    final envelope = await _encryptAndSignMessage(
      messageId: messageId,
      recipient: recipient,
      type: type,
      plaintextBytes: utf8.encode(jsonEncode(payload)),
      refMessageId: refMessageId,
      timestampMs: nowMs,
    );
    await nodeClient.sendEnvelope(envelope);
    return messageId;
  }

  /// Processes an incoming [ServerEnvelope], prioritizing unencrypted control
  /// frames (`SERVER_QUEUED_ACK`, `DELIVERY_ACK`, `DELIVERY_ERROR`,
  /// `REVOCATION_NOTICE`, `IDENTITY_REPLACED`) prior to AEAD decryption.
  Future<void> handleServerEnvelope(ServerEnvelope env) async {
    if (env.senderUuid.isNotEmpty && blocklist.isBlocked(env.senderUuid)) {
      return;
    }

    switch (env.type) {
      case MessageType.SERVER_QUEUED_ACK:
        await _updateStatusForRef(env.senderUuid, env.refMessageId, 'queued');
        return;
      case MessageType.DELIVERY_ACK:
        if (env.signature.isEmpty || await crypto.verifyServerEnvelopeV2(env)) {
          await _updateStatusForRef(
            env.senderUuid,
            env.refMessageId,
            'delivered',
          );
        }
        return;
      case MessageType.DELIVERY_ERROR:
        await _updateStatusForRef(env.senderUuid, env.refMessageId, 'failed');
        return;
      case MessageType.REVOCATION_NOTICE:
        await _handleRevocationNotice(env);
        return;
      case MessageType.IDENTITY_REPLACED:
        await _handleIdentityReplaced(env);
        return;
      default:
        break;
    }

    // Encrypted data/signaling frames: verify V2 signature before decrypting.
    if (!await crypto.verifyServerEnvelopeV2(env)) {
      return;
    }

    final contacts = await vault.loadContacts();
    ContactBundle? senderContact = _findContact(contacts, env.senderUuid);

    if (!await _verifyAndPersistAuthenticatedSender(env, senderContact)) {
      return;
    }

    // Reload contact if first-contact persistence happened.
    senderContact ??= _findContact(await vault.loadContacts(), env.senderUuid);

    Uint8List plaintext;
    try {
      final multiPayload = MultiDeviceEncryptedPayload.fromBuffer(
        env.encryptedPayload,
      );
      plaintext = await crypto.decryptMultiDevicePayload(
        messageId: env.messageId,
        senderUuid: env.senderUuid,
        receiverUuid: env.receiverUuid,
        targetDeviceId: deviceIdentity.deviceId,
        targetDeviceX25519KeyPair: deviceIdentity.x25519KeyPair,
        payload: multiPayload,
      );
    } catch (_) {
      if (senderContact != null && env.senderDeviceId.isEmpty) {
        plaintext = await crypto.decryptPayloadE2E(
          encryptedBlob: env.encryptedPayload,
          recipientX25519KeyPair: deviceIdentity.x25519KeyPair,
          senderX25519PublicKey: senderContact.x25519PublicKey,
        );
      } else {
        return;
      }
    }

    final text = utf8.decode(plaintext);
    if (env.type == MessageType.TEXT) {
      final stored = StoredChatMessage(
        messageId: env.messageId,
        peerUuid: env.senderUuid,
        senderUuid: env.senderUuid,
        text: text,
        isOutgoing: false,
        timestampMs: env.timestamp.toInt(),
        deliveryState: 'received',
      );
      await vault.appendChatMessage(stored);
      if (!_incomingMessages.isClosed) {
        _incomingMessages.add(stored);
      }
      await _emitCertTrimmedDeliveryAck(
        recipientUuid: env.senderUuid,
        refMessageId: env.messageId,
      );
    } else if (env.type == MessageType.RING ||
        env.type == MessageType.SDP_OFFER ||
        env.type == MessageType.SDP_ANSWER ||
        env.type == MessageType.ICE_CANDIDATE ||
        env.type == MessageType.CALL_DECLINE) {
      if (!_incomingSignaling.isClosed) {
        _incomingSignaling.add(
          DecryptedSignalingMessage(
            messageId: env.messageId,
            senderUuid: env.senderUuid,
            senderDeviceId: env.senderDeviceId,
            type: env.type,
            payloadJson: text,
            timestampMs: env.timestamp.toInt(),
          ),
        );
      }
    }
  }

  Future<RegisterSessionResponse> _sendRegisterSession(String nodeAddr) async {
    final nowMs = DateTime.now().millisecondsSinceEpoch;
    final sig = await crypto.signDhtSessionV2(
      deviceEd25519KeyPair: deviceIdentity.ed25519KeyPair,
      userUuid: activeUserUuid,
      deviceId: deviceIdentity.deviceId,
      nodeAddress: nodeAddr,
      deviceEd25519Pub: deviceIdentity.ed25519PublicKeyBytes,
      deviceX25519Pub: deviceIdentity.x25519PublicKeyBytes,
      rootEd25519Pub: activeRootEd25519Pub,
      timestampMs: nowMs,
    );

    final req = RegisterSessionRequest()
      ..userUuid = CanonicalTranscripts.canonicalUuid(activeUserUuid)
      ..deviceId = CanonicalTranscripts.canonicalUuid(deviceIdentity.deviceId)
      ..nodeAddress = nodeAddr
      ..publicKey = deviceIdentity.ed25519PublicKeyBytes
      ..x25519PublicKey = deviceIdentity.x25519PublicKeyBytes
      ..rootEd25519Pub = activeRootEd25519Pub
      ..timestamp = $fixnum.Int64(nowMs)
      ..signature = sig;

    if (deviceIdentity.delegationCert != null) {
      req.delegationCert = deviceIdentity.delegationCert!;
    }
    if (_cachedCredential != null) {
      req.verificationCredential = _cachedCredential!;
    }
    if (_cachedHomeNodeClaim != null) {
      req.homeNodeClaim = _cachedHomeNodeClaim!;
    }
    return nodeClient.registerSession(req);
  }

  Future<ClientEnvelope> _encryptAndSignMessage({
    required String messageId,
    required ContactBundle recipient,
    required MessageType type,
    required List<int> plaintextBytes,
    String refMessageId = '',
    required int timestampMs,
  }) async {
    final targetKeys = await _resolveTargetDeviceX25519Keys(recipient);
    final payload = await crypto.encryptMultiDevicePayload(
      messageId: messageId,
      senderUuid: activeUserUuid,
      receiverUuid: recipient.uuid,
      plaintext: plaintextBytes,
      targetDeviceX25519Pubs: targetKeys,
    );
    return _buildSignedClientEnvelope(
      messageIdOverride: messageId,
      receiverUuid: recipient.uuid,
      type: type,
      encryptedPayload: payload.writeToBuffer(),
      refMessageId: refMessageId,
      timestampMsOverride: timestampMs,
      includeCerts: true,
    );
  }

  Future<Map<String, List<int>>> _resolveTargetDeviceX25519Keys(
    ContactBundle recipient,
  ) async {
    final targets = <String, List<int>>{};
    final rootPub = _effectivePinnedRootPub;
    var activePeerUuid = recipient.uuid;

    if (nodeClient.isConnected) {
      try {
        final peerResp = await nodeClient.findUserSessions(recipient.uuid);
        if (peerResp.hasReplacementNotice() &&
            _cachedCredential?.hasPrincipalCert() == true &&
            rootPub != null &&
            await crypto.verifyIdentityReplacementNotice(
              peerResp.replacementNotice,
              principalCert: _cachedCredential!.principalCert,
              pinnedRootEd25519Pub: rootPub,
            )) {
          activePeerUuid = peerResp.replacementNotice.newUserUuid;
        }
        for (final s in peerResp.sessions) {
          final ok = await crypto.verifyUserSessionInfo(
            s,
            expectedUserUuid: activePeerUuid,
            pinnedRootEd25519Pub: rootPub,
          );
          if (ok) {
            targets[CanonicalTranscripts.canonicalUuid(s.deviceId)] =
                s.x25519PublicKey;
          }
        }
      } catch (_) {
        // Fallback to cached peer X25519 key if DHT lookup temporarily fails.
      }

      // Include own sibling active devices so messages stay synced across desktop/mobile.
      try {
        final ownResp = await nodeClient.findUserSessions(activeUserUuid);
        for (final s in ownResp.sessions) {
          if (CanonicalTranscripts.canonicalUuid(s.deviceId) ==
              CanonicalTranscripts.canonicalUuid(deviceIdentity.deviceId)) {
            continue;
          }
          final ok = await crypto.verifyUserSessionInfo(
            s,
            expectedUserUuid: activeUserUuid,
            pinnedRootEd25519Pub: rootPub,
          );
          if (ok) {
            targets[CanonicalTranscripts.canonicalUuid(s.deviceId)] =
                s.x25519PublicKey;
          }
        }
      } catch (_) {}
    }

    if (targets.isEmpty) {
      // Cold-start / unit test fallback: wrap directly for recipient root deviceId == uuid.
      targets[CanonicalTranscripts.canonicalUuid(activePeerUuid)] =
          recipient.x25519PublicKey;
    }
    return targets;
  }

  Future<ClientEnvelope> _buildSignedClientEnvelope({
    String? messageIdOverride,
    required String receiverUuid,
    required MessageType type,
    required List<int> encryptedPayload,
    String refMessageId = '',
    int? timestampMsOverride,
    required bool includeCerts,
  }) async {
    final msgId = messageIdOverride ?? IdentityCryptoService.generateTemporalUuidV7();
    final ts = timestampMsOverride ?? DateTime.now().millisecondsSinceEpoch;
    final sig = await crypto.signMessageEnvelopeV2(
      deviceEd25519KeyPair: deviceIdentity.ed25519KeyPair,
      messageId: msgId,
      senderUuid: activeUserUuid,
      senderDeviceId: deviceIdentity.deviceId,
      receiverUuid: receiverUuid,
      type: type,
      refMessageId: refMessageId,
      encryptedPayload: encryptedPayload,
      timestampMs: ts,
    );

    final env = ClientEnvelope()
      ..messageId = CanonicalTranscripts.canonicalUuid(msgId)
      ..senderUuid = CanonicalTranscripts.canonicalUuid(activeUserUuid)
      ..senderDeviceId = CanonicalTranscripts.canonicalUuid(deviceIdentity.deviceId)
      ..receiverUuid = CanonicalTranscripts.canonicalUuid(receiverUuid)
      ..type = type
      ..encryptedPayload = encryptedPayload
      ..senderPublicKey = deviceIdentity.ed25519PublicKeyBytes
      ..signature = sig
      ..timestamp = $fixnum.Int64(ts)
      ..refMessageId = refMessageId;

    if (includeCerts && deviceIdentity.delegationCert != null) {
      env.senderDelegationCert = deviceIdentity.delegationCert!;
    }
    if (includeCerts && _cachedCredential != null) {
      env.senderVerificationCredential = _cachedCredential!;
    }
    return env;
  }

  Future<void> _emitCertTrimmedDeliveryAck({
    required String recipientUuid,
    required String refMessageId,
  }) async {
    if (!nodeClient.isConnected) return;
    try {
      final ack = await _buildSignedClientEnvelope(
        receiverUuid: recipientUuid,
        type: MessageType.DELIVERY_ACK,
        encryptedPayload: const <int>[],
        refMessageId: refMessageId,
        includeCerts: false,
      );
      await nodeClient.sendEnvelope(ack);
    } catch (_) {}
  }

  Future<bool> _verifyAndPersistAuthenticatedSender(
    ServerEnvelope env,
    ContactBundle? existingContact,
  ) async {
    if (!env.hasSenderDelegationCert()) {
      return existingContact != null;
    }
    final cert = env.senderDelegationCert;
    if (!_bytesEqual(cert.deviceEd25519Pub, env.senderPublicKey) ||
        CanonicalTranscripts.canonicalUuid(cert.userUuid) !=
            CanonicalTranscripts.canonicalUuid(env.senderUuid) ||
        CanonicalTranscripts.canonicalUuid(cert.deviceId) !=
            CanonicalTranscripts.canonicalUuid(env.senderDeviceId)) {
      return false;
    }

    VerificationCredential? verifiedCred;
    if (env.hasSenderVerificationCredential()) {
      final rootPub = _effectivePinnedRootPub;
      if (rootPub != null &&
          await crypto.verifyVerificationCredential(
            env.senderVerificationCredential,
            pinnedRootEd25519Pub: rootPub,
          ) &&
          CanonicalTranscripts.canonicalUuid(
                env.senderVerificationCredential.userUuid,
              ) ==
              CanonicalTranscripts.canonicalUuid(env.senderUuid)) {
        verifiedCred = env.senderVerificationCredential;
      } else if (rootPub != null) {
        return false;
      }
    }

    if (existingContact != null) {
      return crypto.verifyDeviceDelegationCert(
        cert,
        rootEd25519Pub: existingContact.ed25519PublicKey,
      );
    }

    // Authenticated first-contact persistence.
    if (verifiedCred != null) {
      final verified = await crypto.verifyDeviceDelegationCert(
        cert,
        rootEd25519Pub: verifiedCred.rootEd25519Pub,
      );
      if (!verified) return false;
      await vault.saveContact(
        ContactBundle(
          uuid: CanonicalTranscripts.canonicalUuid(env.senderUuid),
          ed25519PublicKey: Uint8List.fromList(verifiedCred.rootEd25519Pub),
          x25519PublicKey: Uint8List.fromList(verifiedCred.rootX25519Pub),
          displayName: verifiedCred.publicDisplayName.isNotEmpty
              ? verifiedCred.publicDisplayName
              : null,
          isVerified: true,
          docType: verifiedCred.docType,
          publicCnpj: verifiedCred.publicCnpj.isNotEmpty
              ? verifiedCred.publicCnpj
              : null,
          publicDisplayName: verifiedCred.publicDisplayName.isNotEmpty
              ? verifiedCred.publicDisplayName
              : null,
          verifyingIspCnpj: verifiedCred.verifyingIspCnpj.isNotEmpty
              ? verifiedCred.verifyingIspCnpj
              : null,
          verifyingIspName: verifiedCred.verifyingIspName.isNotEmpty
              ? verifiedCred.verifyingIspName
              : null,
          verificationCredential: verifiedCred,
        ),
      );
      return true;
    }

    // Unverified first-contact: verify DelegationCert against DHT rootEd25519Pub.
    if (nodeClient.isConnected) {
      try {
        final resp = await nodeClient.findUserSessions(env.senderUuid);
        for (final s in resp.sessions) {
          if (await crypto.verifyDeviceDelegationCert(
            cert,
            rootEd25519Pub: s.rootEd25519Pub,
          )) {
            await vault.saveContact(
              ContactBundle(
                uuid: CanonicalTranscripts.canonicalUuid(env.senderUuid),
                ed25519PublicKey: Uint8List.fromList(s.rootEd25519Pub),
                x25519PublicKey: Uint8List.fromList(s.x25519PublicKey),
                isVerified: false,
              ),
            );
            return true;
          }
        }
      } catch (_) {}
    }

    return false;
  }

  Future<void> _handleRevocationNotice(ServerEnvelope env) async {
    if (!env.hasRevocationEvent()) return;
    final ev = env.revocationEvent;
    final pCert = _cachedCredential?.hasPrincipalCert() == true
        ? _cachedCredential!.principalCert
        : (env.hasSenderVerificationCredential() &&
                env.senderVerificationCredential.hasPrincipalCert()
            ? env.senderVerificationCredential.principalCert
            : null);
    final rootPub = _effectivePinnedRootPub;
    if (pCert != null && rootPub != null) {
      final ok = await crypto.verifyRevocationEvent(
        ev,
        principalCert: pCert,
        pinnedRootEd25519Pub: rootPub,
      );
      if (!ok) return;
    }

    if (CanonicalTranscripts.canonicalUuid(ev.targetUuid) ==
        CanonicalTranscripts.canonicalUuid(activeUserUuid)) {
      if (ev.revokedDeviceId.isEmpty ||
          CanonicalTranscripts.canonicalUuid(ev.revokedDeviceId) ==
              CanonicalTranscripts.canonicalUuid(deviceIdentity.deviceId)) {
        _isRevokedLocally = true;
      }
      if (ev.revokedDeviceId.isNotEmpty) {
        await vault.markPairedDeviceRevoked(ev.revokedDeviceId);
      }
    }
    if (ev.revocationSeq > 0) {
      await vault.saveLastRevocationSeq(ev.revocationSeq.toInt());
    }
    if (!_revocationEvents.isClosed) {
      _revocationEvents.add(ev);
    }
  }

  Future<void> _handleIdentityReplaced(ServerEnvelope env) async {
    if (!env.hasReplacementNotice()) return;
    final notice = env.replacementNotice;
    final pCert = _cachedCredential?.hasPrincipalCert() == true
        ? _cachedCredential!.principalCert
        : (env.hasSenderVerificationCredential() &&
                env.senderVerificationCredential.hasPrincipalCert()
            ? env.senderVerificationCredential.principalCert
            : null);
    final rootPub = _effectivePinnedRootPub;
    if (pCert != null && rootPub != null) {
      final ok = await crypto.verifyIdentityReplacementNotice(
        notice,
        principalCert: pCert,
        pinnedRootEd25519Pub: rootPub,
      );
      if (!ok) return;
    }

    VerificationCredential? newVc;
    if (env.hasSenderVerificationCredential() &&
        CanonicalTranscripts.canonicalUuid(
              env.senderVerificationCredential.userUuid,
            ) ==
            CanonicalTranscripts.canonicalUuid(notice.newUserUuid)) {
      newVc = env.senderVerificationCredential;
    }

    final contacts = await vault.loadContacts();
    final oldContact = _findContact(contacts, notice.oldUserUuid);
    if (oldContact != null) {
      final migrated = ContactBundle(
        uuid: CanonicalTranscripts.canonicalUuid(notice.newUserUuid),
        ed25519PublicKey: newVc != null
            ? Uint8List.fromList(newVc.rootEd25519Pub)
            : oldContact.ed25519PublicKey,
        x25519PublicKey: newVc != null
            ? Uint8List.fromList(newVc.rootX25519Pub)
            : oldContact.x25519PublicKey,
        displayName: oldContact.displayName,
        isVerified: newVc != null || oldContact.isVerified,
        docType: newVc?.docType ?? oldContact.docType,
        publicCnpj: newVc?.publicCnpj ?? oldContact.publicCnpj,
        publicDisplayName:
            newVc?.publicDisplayName ?? oldContact.publicDisplayName,
        verifyingIspCnpj:
            newVc?.verifyingIspCnpj ?? notice.verifyingIspCnpj,
        verifyingIspName:
            newVc?.verifyingIspName ?? oldContact.verifyingIspName,
        verificationCredential: newVc,
      );
      await vault.migrateReplacedContact(
        oldUserUuid: notice.oldUserUuid,
        updatedContact: migrated,
      );
    }

    if (!_replacementEvents.isClosed) {
      _replacementEvents.add(notice);
    }
  }

  Future<void> _updateStatusForRef(
    String senderUuid,
    String refMessageId,
    String deliveryState,
  ) async {
    if (refMessageId.isEmpty) return;
    final contacts = await vault.loadContacts();
    for (final c in contacts) {
      await vault.updateChatMessageStatus(
        peerUuid: c.uuid,
        messageId: refMessageId,
        deliveryState: deliveryState,
      );
    }
    if (!_messageStatusUpdates.isClosed) {
      _messageStatusUpdates.add('$refMessageId:$deliveryState');
    }
  }

  static ContactBundle? _findContact(List<ContactBundle> contacts, String uuid) {
    try {
      final norm = CanonicalTranscripts.canonicalUuid(uuid);
      for (final c in contacts) {
        if (CanonicalTranscripts.canonicalUuid(c.uuid) == norm) {
          return c;
        }
      }
    } catch (_) {}
    return null;
  }

  static bool _bytesEqual(List<int> a, List<int> b) {
    if (a.length != b.length) return false;
    var diff = 0;
    for (var i = 0; i < a.length; i++) {
      diff |= a[i] ^ b[i];
    }
    return diff == 0;
  }

  Future<void> dispose() async {
    await _streamSub?.cancel();
    await _incomingMessages.close();
    await _incomingSignaling.close();
    await _revocationEvents.close();
    await _replacementEvents.close();
    await _messageStatusUpdates.close();
  }
}
