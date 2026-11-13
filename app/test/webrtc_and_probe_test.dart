import 'dart:async';

import 'package:app/core/crypto/identity_crypto_service.dart';
import 'package:app/core/discovery/tcp_latency_probe.dart';
import 'package:app/core/grpc/generated/ispchat.pbgrpc.dart';
import 'package:app/core/grpc/ispchat_node_client.dart';
import 'package:app/core/messaging/e2e_session_manager.dart';
import 'package:app/core/security/blocklist_service.dart';
import 'package:app/core/storage/secure_local_vault.dart';
import 'package:app/core/webrtc/webrtc_call_coordinator.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart' hide MessageType;
import 'package:grpc/grpc.dart' as grpc;

class _FakeWebRtcEngine implements WebRtcMediaEngine {
  bool started = false;
  bool muted = false;
  bool videoDisabled = false;
  bool closed = false;
  RTCSessionDescription? lastRemote;
  RTCSessionDescription? lastLocal;
  final List<RTCIceCandidate> addedIce = <RTCIceCandidate>[];
  void Function(RTCIceCandidate)? _onIce;
  void Function()? _onTrack;

  @override
  RTCVideoRenderer? get localVideoRenderer => null;

  @override
  RTCVideoRenderer? get remoteVideoRenderer => null;

  @override
  Future<void> initializeRenderers() async {}

  @override
  Future<void> startSession({
    required Map<String, dynamic> rtcConfiguration,
    required bool videoEnabled,
    required void Function(RTCIceCandidate candidate) onIceCandidate,
    required void Function() onRemoteTrackReady,
  }) async {
    started = true;
    _onIce = onIceCandidate;
    _onTrack = onRemoteTrackReady;
  }

  @override
  Future<RTCSessionDescription> createOffer() async {
    return RTCSessionDescription('v=0\r\no=- 1 1 IN IP4 127.0.0.1\r\n', 'offer');
  }

  @override
  Future<RTCSessionDescription> createAnswer() async {
    return RTCSessionDescription('v=0\r\no=- 2 1 IN IP4 127.0.0.1\r\n', 'answer');
  }

  @override
  Future<void> setLocalDescription(RTCSessionDescription description) async {
    lastLocal = description;
  }

  @override
  Future<void> setRemoteDescription(RTCSessionDescription description) async {
    lastRemote = description;
  }

  @override
  Future<void> addIceCandidate(RTCIceCandidate candidate) async {
    addedIce.add(candidate);
  }

  @override
  void setMicrophoneMuted(bool m) => muted = m;

  @override
  void setVideoDisabled(bool d) => videoDisabled = d;

  @override
  Future<void> stopAndClose() async {
    closed = true;
  }

  void simulateIceAndTrack() {
    _onIce?.call(RTCIceCandidate('candidate:1 1 udp 1 127.0.0.1 9999 typ host', '0', 0));
    _onTrack?.call();
  }
}

class _FakeRelayServices {
  final Map<String, StreamController<ServerEnvelope>> streams = {};
  final List<UserSessionInfo> registered = [];

  void dispatchClientEnvelope(ClientEnvelope env) {
    final target = streams[env.receiverUuid];
    if (target == null || target.isClosed) return;
    target.add(
      ServerEnvelope()
        ..messageId = env.messageId
        ..senderUuid = env.senderUuid
        ..senderDeviceId = env.senderDeviceId
        ..receiverUuid = env.receiverUuid
        ..type = env.type
        ..encryptedPayload = env.encryptedPayload
        ..senderPublicKey = env.senderPublicKey
        ..signature = env.signature
        ..timestamp = env.timestamp
        ..refMessageId = env.refMessageId
        ..senderDelegationCert = env.senderDelegationCert,
    );
  }
}

class _TestDht extends DhtClientServiceBase {
  final _FakeRelayServices relay;
  _TestDht(this.relay);

  @override
  Future<RegisterSessionResponse> registerSession(
    grpc.ServiceCall call,
    RegisterSessionRequest request,
  ) async {
    relay.registered.removeWhere(
      (s) => s.deviceId == request.deviceId,
    );
    final session = UserSessionInfo()
      ..nodeAddress = request.nodeAddress
      ..ed25519PublicKey = request.publicKey
      ..x25519PublicKey = request.x25519PublicKey
      ..timestamp = request.timestamp
      ..deviceId = request.deviceId
      ..rootEd25519Pub = request.rootEd25519Pub
      ..deviceEd25519Pub = request.publicKey
      ..sessionSignature = request.signature;
    if (request.hasDelegationCert()) {
      session.delegationCert = request.delegationCert;
    }
    if (request.hasVerificationCredential()) {
      session.verificationCredential = request.verificationCredential;
    }
    relay.registered.add(session);
    return RegisterSessionResponse()..success = true;
  }

  @override
  Future<FindUserSessionsResponse> findUserSessions(
    grpc.ServiceCall call,
    FindUserSessionsRequest request,
  ) async {
    return FindUserSessionsResponse()
      ..sessions.addAll(
        relay.registered.where(
          (s) => s.delegationCert.userUuid == request.targetUuid,
        ),
      );
  }
}

class _TestStream extends ClientStreamServiceBase {
  final _FakeRelayServices relay;
  _TestStream(this.relay);

  @override
  Stream<ServerEnvelope> connect(
    grpc.ServiceCall call,
    Stream<ClientEnvelope> request,
  ) {
    final controller = StreamController<ServerEnvelope>();
    request.listen(
      (env) {
        if (env.type == MessageType.AUTH_HANDSHAKE) {
          relay.streams[env.senderUuid] = controller;
        } else {
          relay.dispatchClientEnvelope(env);
        }
      },
      onError: (_) {
        if (!controller.isClosed) controller.close();
      },
      onDone: () {
        if (!controller.isClosed) controller.close();
      },
      cancelOnError: true,
    );
    return controller.stream;
  }
}

class _TestTurn extends TurnCredentialServiceBase {
  @override
  Future<TurnCredentialResponse> getTurnCredentials(
    grpc.ServiceCall call,
    TurnCredentialRequest request,
  ) async {
    return TurnCredentialResponse()
      ..uris.addAll(const ['turn:turn.isp.br:3478?transport=udp'])
      ..username = 'ephemeral-user'
      ..credential = 'hmac-secret'
      ..ttlSeconds = 300
      ..maxBps = 2000000;
  }
}

void main() {
  test('TcpLatencyProbe parses IPv4, hostnames, and bracketed IPv6', () {
    final v4 = TcpLatencyProbe.parseHostAndPort('192.0.2.10:8443');
    expect(v4.host, '192.0.2.10');
    expect(v4.port, 8443);

    final v6 = TcpLatencyProbe.parseHostAndPort('[2001:db8::1]:8443');
    expect(v6.host, '2001:db8::1');
    expect(v6.port, 8443);

    expect(
      () => TcpLatencyProbe.parseHostAndPort('[2001:db8::1'),
      throwsFormatException,
    );
  });

  test('WebRtcCallCoordinator full offer/TURN/answer/ICE/mute/hangup lifecycle',
      () async {
    const crypto = IdentityCryptoService();
    final relay = _FakeRelayServices();
    final server = grpc.Server.create(
      services: [_TestDht(relay), _TestStream(relay), _TestTurn()],
    );
    await server.serve(address: '127.0.0.1', port: 0);

    final aliceId = await crypto.generateNewIdentity();
    final bobId = await crypto.generateNewIdentity();

    final aliceVault = SecureLocalVault(store: InMemorySecureKeyValueStore());
    final bobVault = SecureLocalVault(store: InMemorySecureKeyValueStore());
    await aliceVault.saveContact(
      ContactBundle(
        uuid: bobId.userUuid,
        ed25519PublicKey: bobId.ed25519PublicKeyBytes,
        x25519PublicKey: bobId.x25519PublicKeyBytes,
      ),
    );
    await bobVault.saveContact(
      ContactBundle(
        uuid: aliceId.userUuid,
        ed25519PublicKey: aliceId.ed25519PublicKeyBytes,
        x25519PublicKey: aliceId.x25519PublicKeyBytes,
      ),
    );

    final aliceClient = IspChatNodeClient();
    final bobClient = IspChatNodeClient();
    await aliceClient.connect(
      host: '127.0.0.1',
      port: server.port!,
      useTls: false,
      routeGovernanceThroughEdgeChannel: true,
    );
    await bobClient.connect(
      host: '127.0.0.1',
      port: server.port!,
      useTls: false,
      routeGovernanceThroughEdgeChannel: true,
    );

    final aliceSession = await E2eSessionManager.createForRootIdentity(
      identity: aliceId,
      nodeClient: aliceClient,
      vault: aliceVault,
      blocklist: BlocklistService(),
    );
    final bobSession = await E2eSessionManager.createForRootIdentity(
      identity: bobId,
      nodeClient: bobClient,
      vault: bobVault,
      blocklist: BlocklistService(),
    );

    await aliceSession.initializeAndRegister();
    await bobSession.initializeAndRegister();

    final aliceEngine = _FakeWebRtcEngine();
    final bobEngine = _FakeWebRtcEngine();
    final aliceCall = WebRtcCallCoordinator(
      sessionManager: aliceSession,
      mediaEngine: aliceEngine,
    );
    final bobCall = WebRtcCallCoordinator(
      sessionManager: bobSession,
      mediaEngine: bobEngine,
    );

    try {
      final bobRinging = bobCall.callStateStream.firstWhere(
        (s) => s.status == CallStatus.ringingIncoming,
      );
      await aliceCall.startCall(
        peer: (await aliceVault.loadContacts()).first,
        videoEnabled: true,
      );
      final incomingSnap = await bobRinging.timeout(const Duration(seconds: 5));
      expect(incomingSnap.videoEnabled, isTrue);

      await bobCall.acceptIncomingCall(videoEnabled: true);
      await Future<void>.delayed(const Duration(milliseconds: 200));

      aliceEngine.simulateIceAndTrack();
      await Future<void>.delayed(const Duration(milliseconds: 200));

      expect(aliceCall.currentSnapshot.status, CallStatus.inCall);
      expect(aliceCall.currentSnapshot.turnAuthenticated, isTrue);
      expect(bobEngine.addedIce.length, 1);

      aliceCall.toggleMute();
      aliceCall.toggleCamera();
      expect(aliceEngine.muted, isTrue);
      expect(aliceEngine.videoDisabled, isTrue);

      await aliceCall.hangup();
      await Future<void>.delayed(const Duration(milliseconds: 150));
      expect(aliceEngine.closed, isTrue);
      expect(bobCall.currentSnapshot.status, CallStatus.ended);
    } finally {
      await aliceCall.dispose();
      await bobCall.dispose();
      await aliceSession.dispose();
      await bobSession.dispose();
      await aliceClient.dispose();
      await bobClient.dispose();
      await server.shutdown();
    }
  });
}
