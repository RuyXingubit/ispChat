import 'dart:async';

import 'package:app/core/crypto/identity_crypto_service.dart';
import 'package:app/core/grpc/generated/ispchat.pbgrpc.dart';
import 'package:app/core/grpc/ispchat_node_client.dart';
import 'package:app/core/messaging/e2e_session_manager.dart';
import 'package:app/core/security/blocklist_service.dart';
import 'package:app/core/storage/secure_local_vault.dart';
import 'package:fixnum/fixnum.dart' as $fixnum;
import 'package:flutter_test/flutter_test.dart';
import 'package:grpc/grpc.dart' as grpc;

class _MultiDeviceNodeRouter {
  // key: "userUuid|deviceId"
  final Map<String, StreamController<ServerEnvelope>> activeStreams = {};
  final Map<String, List<UserSessionInfo>> sessionsByUser = {};

  void register(RegisterSessionRequest req) {
    final list = sessionsByUser.putIfAbsent(req.userUuid, () => []);
    list.removeWhere((s) => s.deviceId == req.deviceId);
    final info = UserSessionInfo()
      ..nodeAddress = req.nodeAddress
      ..ed25519PublicKey = req.publicKey
      ..x25519PublicKey = req.x25519PublicKey
      ..timestamp = req.timestamp
      ..deviceId = req.deviceId
      ..rootEd25519Pub = req.rootEd25519Pub
      ..deviceEd25519Pub = req.publicKey
      ..sessionSignature = req.signature;
    if (req.hasDelegationCert()) {
      info.delegationCert = req.delegationCert;
    }
    if (req.hasVerificationCredential()) {
      info.verificationCredential = req.verificationCredential;
    }
    list.add(info);
  }

  void bindStream(
    String userUuid,
    String deviceId,
    StreamController<ServerEnvelope> ctrl,
  ) {
    activeStreams['$userUuid|$deviceId'] = ctrl;
  }

  void unbindStream(String userUuid, String deviceId) {
    activeStreams.remove('$userUuid|$deviceId');
  }

  void routeEnvelope(ClientEnvelope env) {
    final delivered = ServerEnvelope()
      ..messageId = env.messageId
      ..senderUuid = env.senderUuid
      ..senderDeviceId = env.senderDeviceId
      ..receiverUuid = env.receiverUuid
      ..type = env.type
      ..encryptedPayload = env.encryptedPayload
      ..senderPublicKey = env.senderPublicKey
      ..signature = env.signature
      ..timestamp = env.timestamp
      ..refMessageId = env.refMessageId;
    if (env.hasSenderDelegationCert()) {
      delivered.senderDelegationCert = env.senderDelegationCert;
    }
    if (env.hasSenderVerificationCredential()) {
      delivered.senderVerificationCredential = env.senderVerificationCredential;
    }

    final targetSessions = sessionsByUser[env.receiverUuid] ?? const [];
    for (final s in targetSessions) {
      final ctrl = activeStreams['${env.receiverUuid}|${s.deviceId}'];
      if (ctrl != null && !ctrl.isClosed) {
        ctrl.add(delivered);
      }
    }

    // Also deliver sibling copy for TEXT to sender's other devices.
    if (env.type == MessageType.TEXT) {
      final ownSessions = sessionsByUser[env.senderUuid] ?? const [];
      for (final s in ownSessions) {
        if (s.deviceId == env.senderDeviceId) continue;
        final ctrl = activeStreams['${env.senderUuid}|${s.deviceId}'];
        if (ctrl != null && !ctrl.isClosed) {
          ctrl.add(delivered);
        }
      }
      final senderCtrl = activeStreams['${env.senderUuid}|${env.senderDeviceId}'];
      if (senderCtrl != null && !senderCtrl.isClosed) {
        senderCtrl.add(
          ServerEnvelope()
            ..messageId = IdentityCryptoService.generateTemporalUuidV7()
            ..senderUuid = env.receiverUuid
            ..receiverUuid = env.senderUuid
            ..type = MessageType.SERVER_QUEUED_ACK
            ..refMessageId = env.messageId
            ..timestamp = $fixnum.Int64(DateTime.now().millisecondsSinceEpoch),
        );
      }
    }
  }
}

class _LiveDhtService extends DhtClientServiceBase {
  final _MultiDeviceNodeRouter router;
  _LiveDhtService(this.router);

  @override
  Future<RegisterSessionResponse> registerSession(
    grpc.ServiceCall call,
    RegisterSessionRequest request,
  ) async {
    router.register(request);
    return RegisterSessionResponse()..success = true;
  }

  @override
  Future<FindUserSessionsResponse> findUserSessions(
    grpc.ServiceCall call,
    FindUserSessionsRequest request,
  ) async {
    return FindUserSessionsResponse()
      ..sessions.addAll(router.sessionsByUser[request.targetUuid] ?? const []);
  }
}

class _LiveStreamService extends ClientStreamServiceBase {
  final _MultiDeviceNodeRouter router;
  _LiveStreamService(this.router);

  @override
  Stream<ServerEnvelope> connect(
    grpc.ServiceCall call,
    Stream<ClientEnvelope> request,
  ) {
    final ctrl = StreamController<ServerEnvelope>();
    String? boundUser;
    String? boundDevice;
    request.listen(
      (env) {
        if (env.type == MessageType.AUTH_HANDSHAKE) {
          boundUser = env.senderUuid;
          boundDevice = env.senderDeviceId;
          router.bindStream(boundUser!, boundDevice!, ctrl);
        } else {
          router.routeEnvelope(env);
        }
      },
      onError: (_) {
        if (boundUser != null && boundDevice != null) {
          router.unbindStream(boundUser!, boundDevice!);
        }
        if (!ctrl.isClosed) ctrl.close();
      },
      onDone: () {
        if (boundUser != null && boundDevice != null) {
          router.unbindStream(boundUser!, boundDevice!);
        }
        if (!ctrl.isClosed) ctrl.close();
      },
      cancelOnError: true,
    );
    return ctrl.stream;
  }
}

void main() {
  test(
      'Live HTTP/2 gRPC loopback delivers multi-device V2 E2EE message + '
      'DELIVERY_ACK across Alice Root, Alice Desktop, and Bob', () async {
    const crypto = IdentityCryptoService();
    final router = _MultiDeviceNodeRouter();
    final server = grpc.Server.create(
      services: [_LiveDhtService(router), _LiveStreamService(router)],
    );
    await server.serve(address: '127.0.0.1', port: 0);
    final addr = '127.0.0.1:${server.port!}';

    final alice = await crypto.generateNewIdentity();
    final aliceDesktopDevice = await crypto.generateDeviceIdentity(
      role: DeviceRole.DESKTOP,
      rootIdentityForDelegation: alice,
    );
    final bob = await crypto.generateNewIdentity();

    final bobContactForAlice = ContactBundle(
      uuid: bob.userUuid,
      ed25519PublicKey: bob.ed25519PublicKeyBytes,
      x25519PublicKey: bob.x25519PublicKeyBytes,
    );
    final aliceContactForBob = ContactBundle(
      uuid: alice.userUuid,
      ed25519PublicKey: alice.ed25519PublicKeyBytes,
      x25519PublicKey: alice.x25519PublicKeyBytes,
    );

    final aliceVault = SecureLocalVault(store: InMemorySecureKeyValueStore());
    await aliceVault.saveContact(bobContactForAlice);
    final aliceDesktopVault = SecureLocalVault(
      store: InMemorySecureKeyValueStore(),
    );
    await aliceDesktopVault.saveContact(bobContactForAlice);
    final bobVault = SecureLocalVault(store: InMemorySecureKeyValueStore());
    await bobVault.saveContact(aliceContactForBob);

    final aliceClient = IspChatNodeClient();
    final aliceDesktopClient = IspChatNodeClient();
    final bobClient = IspChatNodeClient();

    await aliceClient.connectToAddress(
      addr,
      useTls: false,
      routeGovernanceThroughEdgeChannel: true,
    );
    await aliceDesktopClient.connectToAddress(
      addr,
      useTls: false,
      routeGovernanceThroughEdgeChannel: true,
    );
    await bobClient.connectToAddress(
      addr,
      useTls: false,
      routeGovernanceThroughEdgeChannel: true,
    );

    final aliceSession = await E2eSessionManager.createForRootIdentity(
      identity: alice,
      nodeClient: aliceClient,
      vault: aliceVault,
      blocklist: BlocklistService(),
    );
    final aliceDesktopSession = E2eSessionManager(
      deviceIdentity: aliceDesktopDevice,
      activeUserUuid: alice.userUuid,
      activeRootEd25519Pub: alice.ed25519PublicKeyBytes,
      nodeClient: aliceDesktopClient,
      vault: aliceDesktopVault,
      blocklist: BlocklistService(),
    );
    final bobSession = await E2eSessionManager.createForRootIdentity(
      identity: bob,
      nodeClient: bobClient,
      vault: bobVault,
      blocklist: BlocklistService(),
    );

    try {
      expect((await aliceSession.initializeAndRegister()).success, isTrue);
      expect((await aliceDesktopSession.initializeAndRegister()).success, isTrue);
      expect((await bobSession.initializeAndRegister()).success, isTrue);

      for (var i = 0; i < 40 && router.activeStreams.length < 3; i++) {
        await Future<void>.delayed(const Duration(milliseconds: 25));
      }
      expect(router.activeStreams.length, 3);

      final aliceDeliveredAck = aliceSession.messageStatusUpdates.firstWhere(
        (u) => u.endsWith(':delivered'),
      );
      final bobReceived = bobSession.incomingMessages.first;

      // Bob sends multi-device encrypted message targeting BOTH Alice's mobile
      // and Alice's delegated desktop!
      final aliceRootReceived = aliceSession.incomingMessages.first;
      final aliceDesktopReceived = aliceDesktopSession.incomingMessages.first;

      await bobSession.sendTextMessage(
        recipient: aliceContactForBob,
        text: 'Sincronizado nos 2 dispositivos',
      );
      expect(
        (await aliceRootReceived.timeout(const Duration(seconds: 5))).text,
        'Sincronizado nos 2 dispositivos',
      );
      expect(
        (await aliceDesktopReceived.timeout(const Duration(seconds: 5))).text,
        'Sincronizado nos 2 dispositivos',
      );

      // Alice sends text back from mobile; Bob receives & emits DELIVERY_ACK.
      await aliceSession.sendTextMessage(
        recipient: bobContactForAlice,
        text: 'Confirmado via HTTP/2!',
      );
      expect(
        (await bobReceived.timeout(const Duration(seconds: 5))).text,
        'Confirmado via HTTP/2!',
      );
      await aliceDeliveredAck.timeout(const Duration(seconds: 5));
    } finally {
      await aliceSession.dispose();
      await aliceDesktopSession.dispose();
      await bobSession.dispose();
      await aliceClient.dispose();
      await aliceDesktopClient.dispose();
      await bobClient.dispose();
      await server.shutdown();
    }
  });
}
