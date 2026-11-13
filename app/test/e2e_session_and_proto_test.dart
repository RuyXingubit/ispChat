import 'dart:typed_data';

import 'package:app/core/crypto/identity_crypto_service.dart';
import 'package:app/core/grpc/generated/ispchat.pbgrpc.dart';
import 'package:app/core/grpc/ispchat_node_client.dart';
import 'package:app/core/messaging/e2e_session_manager.dart';
import 'package:app/core/security/blocklist_service.dart';
import 'package:app/core/storage/secure_local_vault.dart';
import 'package:cryptography/cryptography.dart';
import 'package:fixnum/fixnum.dart' as $fixnum;
import 'package:flutter_test/flutter_test.dart';
import 'package:grpc/grpc.dart' as grpc;

class _FakeGovernanceBackend extends PrincipalGovernanceServiceBase {
  final SimpleKeyPair prinEd;
  final PrincipalSigningCert pCert;
  VerificationCredential? renewedCredential;

  _FakeGovernanceBackend({required this.prinEd, required this.pCert});

  @override
  Future<VerificationBundle> getOrRenewCredential(
    grpc.ServiceCall call,
    GetCredentialRequest request,
  ) async {
    return VerificationBundle()..credential = renewedCredential!;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      Future<Object?>.error(grpc.GrpcError.unimplemented());
}

void main() {
  test(
      'E2eSessionManager processes control frames, blocklist, revocations, '
      'first-contact verification, IDENTITY_REPLACED, and 60d auto-renew',
      () async {
    const crypto = IdentityCryptoService();
    final govRoot = await Ed25519().newKeyPair();
    final govRootPub =
        Uint8List.fromList((await govRoot.extractPublicKey()).bytes);
    final prinEd = await Ed25519().newKeyPair();
    final now = DateTime.now().millisecondsSinceEpoch;
    final pCert = PrincipalSigningCert()
      ..certId = IdentityCryptoService.generateTemporalUuidV7()
      ..principalEd25519Pub = (await prinEd.extractPublicKey()).bytes
      ..escrowX25519Pub = List<int>.filled(32, 9)
      ..issuedAtMs = $fixnum.Int64(now)
      ..expiresAtMs = $fixnum.Int64(now + 365 * 86400000);
    pCert.rootSignature = await crypto.signRaw(
      govRoot,
      CanonicalTranscripts.buildPrincipalSigningCertTranscript(pCert),
    );

    Future<VerificationCredential> signVc(
      IdentityBundle id, {
      int issuedAtOffsetMs = 0,
      int expiresInDays = 90,
      String cnpj = '',
      String display = '',
    }) async {
      final issued = now + issuedAtOffsetMs;
      final vc = VerificationCredential()
        ..credentialId = IdentityCryptoService.generateTemporalUuidV7()
        ..userUuid = id.userUuid
        ..rootEd25519Pub = id.ed25519PublicKeyBytes
        ..rootX25519Pub = id.x25519PublicKeyBytes
        ..docType = cnpj.isNotEmpty ? DocumentType.CNPJ : DocumentType.CPF
        ..publicCnpj = cnpj
        ..publicDisplayName = display
        ..verifyingIspCnpj = '11222333000181'
        ..verifyingIspName = 'Fibra Sul'
        ..homeNodeAddress = '127.0.0.1:8443'
        ..routingEpoch = $fixnum.Int64(1)
        ..issuedAtMs = $fixnum.Int64(issued)
        ..expiresAtMs = $fixnum.Int64(issued + expiresInDays * 86400000)
        ..principalCert = pCert;
      vc.principalSignature = await crypto.signRaw(
        prinEd,
        CanonicalTranscripts.buildVerificationCredentialTranscript(vc),
      );
      return vc;
    }

    final alice = await crypto.generateNewIdentity();
    final bob = await crypto.generateNewIdentity();
    final bobNew = await crypto.generateNewIdentity();
    final blockedId = await crypto.generateNewIdentity();

    // 1. Seed stale 65-day-old Alice credential to verify auto-renewal.
    final staleVc = await signVc(
      alice,
      issuedAtOffsetMs: -const Duration(days: 65).inMilliseconds,
    );
    final freshVc = await signVc(alice);

    final govSvc = _FakeGovernanceBackend(prinEd: prinEd, pCert: pCert)
      ..renewedCredential = freshVc;
    final server = grpc.Server.create(services: [govSvc]);
    await server.serve(address: '127.0.0.1', port: 0);

    final aliceVault = SecureLocalVault(store: InMemorySecureKeyValueStore());
    await aliceVault.saveVerificationCredential(staleVc);

    final blocklist = BlocklistService()..blockUuid(blockedId.userUuid);
    final client = IspChatNodeClient();
    await client.connect(
      host: '127.0.0.1',
      port: server.port!,
      useTls: false,
      routeGovernanceThroughEdgeChannel: true,
    );

    final aliceSession = await E2eSessionManager.createForRootIdentity(
      identity: alice,
      nodeClient: client,
      vault: aliceVault,
      blocklist: blocklist,
      pinnedRootPubOverride: govRootPub,
    );

    try {
      final renewed = await aliceSession.maybeRefreshCredential();
      expect(renewed?.credentialId, freshVc.credentialId);
      expect(
        (await aliceVault.loadVerificationCredential())?.credentialId,
        freshVc.credentialId,
      );

      // 2. First-contact verified incoming message (without Bob previously in contacts).
      final bobVc = await signVc(
        bob,
        cnpj: '12ABC34501DE35',
        display: 'Provedor Bob',
      );
      final bobDevice = await crypto.deriveMobileRootDeviceIdentity(bob);
      final msgId = IdentityCryptoService.generateTemporalUuidV7();
      final payload = await crypto.encryptMultiDevicePayload(
        messageId: msgId,
        senderUuid: bob.userUuid,
        receiverUuid: alice.userUuid,
        plaintext: 'ola verificado'.codeUnits,
        targetDeviceX25519Pubs: {
          aliceSession.deviceIdentity.deviceId:
              aliceSession.deviceIdentity.x25519PublicKeyBytes,
        },
      );
      final payloadBytes = payload.writeToBuffer();
      final ts = DateTime.now().millisecondsSinceEpoch;
      final sig = await crypto.signMessageEnvelopeV2(
        deviceEd25519KeyPair: bobDevice.ed25519KeyPair,
        messageId: msgId,
        senderUuid: bob.userUuid,
        senderDeviceId: bobDevice.deviceId,
        receiverUuid: alice.userUuid,
        type: MessageType.TEXT,
        refMessageId: '',
        encryptedPayload: payloadBytes,
        timestampMs: ts,
      );

      final incomingEnv = ServerEnvelope()
        ..messageId = msgId
        ..senderUuid = bob.userUuid
        ..senderDeviceId = bobDevice.deviceId
        ..receiverUuid = alice.userUuid
        ..type = MessageType.TEXT
        ..encryptedPayload = payloadBytes
        ..senderPublicKey = bobDevice.ed25519PublicKeyBytes
        ..signature = sig
        ..timestamp = $fixnum.Int64(ts)
        ..senderDelegationCert = bobDevice.delegationCert!
        ..senderVerificationCredential = bobVc;

      await aliceSession.handleServerEnvelope(incomingEnv);
      final msgs = await aliceVault.loadChatMessages(bob.userUuid);
      expect(msgs.length, 1);
      expect(msgs.first.text, 'ola verificado');
      final contactsAfterFirst = await aliceVault.loadContacts();
      expect(contactsAfterFirst.length, 1);
      expect(contactsAfterFirst.first.isVerified, isTrue);
      expect(contactsAfterFirst.first.publicCnpj, '12ABC34501DE35');

      // 3. Blocked sender is silently dropped before persisting or decrypting.
      final blockedDevice =
          await crypto.deriveMobileRootDeviceIdentity(blockedId);
      final blockedMsgId = IdentityCryptoService.generateTemporalUuidV7();
      await aliceSession.handleServerEnvelope(
        ServerEnvelope()
          ..messageId = blockedMsgId
          ..senderUuid = blockedId.userUuid
          ..senderDeviceId = blockedDevice.deviceId
          ..receiverUuid = alice.userUuid
          ..type = MessageType.TEXT
          ..encryptedPayload = const [1, 2, 3],
      );
      expect(await aliceVault.loadChatMessages(blockedId.userUuid), isEmpty);

      // 4. Unencrypted ACK control frames processed prior to AEAD decryption.
      final outMsgId = IdentityCryptoService.generateTemporalUuidV7();
      await aliceVault.appendChatMessage(
        StoredChatMessage(
          messageId: outMsgId,
          peerUuid: bob.userUuid,
          senderUuid: alice.userUuid,
          text: 'outbound',
          isOutgoing: true,
          timestampMs: ts,
          deliveryState: 'sending',
        ),
      );
      await aliceSession.handleServerEnvelope(
        ServerEnvelope()
          ..messageId = IdentityCryptoService.generateTemporalUuidV7()
          ..senderUuid = bob.userUuid
          ..receiverUuid = alice.userUuid
          ..type = MessageType.SERVER_QUEUED_ACK
          ..refMessageId = outMsgId,
      );
      expect(
        (await aliceVault.loadChatMessages(bob.userUuid))
            .last
            .deliveryState,
        'queued',
      );
      await aliceSession.handleServerEnvelope(
        ServerEnvelope()
          ..messageId = IdentityCryptoService.generateTemporalUuidV7()
          ..senderUuid = bob.userUuid
          ..receiverUuid = alice.userUuid
          ..type = MessageType.DELIVERY_ACK
          ..refMessageId = outMsgId,
      );
      expect(
        (await aliceVault.loadChatMessages(bob.userUuid))
            .last
            .deliveryState,
        'delivered',
      );

      // 5. IDENTITY_REPLACED migrates Bob -> bobNew and chat history.
      final bobNewVc = await signVc(bobNew);
      final notice = IdentityReplacementNotice()
        ..revocationId = IdentityCryptoService.generateTemporalUuidV7()
        ..oldUserUuid = bob.userUuid
        ..newUserUuid = bobNew.userUuid
        ..verifyingIspCnpj = '11222333000181'
        ..replacedAtMs = $fixnum.Int64(ts);
      notice.principalSignature = await crypto.signRaw(
        prinEd,
        CanonicalTranscripts.buildIdentityReplacementTranscript(notice),
      );
      await aliceSession.handleServerEnvelope(
        ServerEnvelope()
          ..messageId = IdentityCryptoService.generateTemporalUuidV7()
          ..type = MessageType.IDENTITY_REPLACED
          ..replacementNotice = notice
          ..senderVerificationCredential = bobNewVc,
      );
      final migratedMsgs = await aliceVault.loadChatMessages(bobNew.userUuid);
      expect(migratedMsgs.length, 2);
      expect(await aliceVault.loadChatMessages(bob.userUuid), isEmpty);

      // 6. REVOCATION_NOTICE marks local revocation if targeting active device.
      final rev = RevocationEvent()
        ..revocationId = IdentityCryptoService.generateTemporalUuidV7()
        ..revocationSeq = $fixnum.Int64(4)
        ..targetUuid = alice.userUuid
        ..revokedDeviceId = aliceSession.deviceIdentity.deviceId
        ..reason = 'lost_device'
        ..createdAtMs = $fixnum.Int64(ts);
      rev.principalSignature = await crypto.signRaw(
        prinEd,
        CanonicalTranscripts.buildRevocationEventTranscript(rev),
      );
      await aliceSession.handleServerEnvelope(
        ServerEnvelope()
          ..messageId = IdentityCryptoService.generateTemporalUuidV7()
          ..type = MessageType.REVOCATION_NOTICE
          ..revocationEvent = rev,
      );
      expect(aliceSession.isRevokedLocally, isTrue);
      expect(await aliceVault.loadLastRevocationSeq(), 4);
    } finally {
      await aliceSession.dispose();
      await client.dispose();
      await server.shutdown();
    }
  });
}
