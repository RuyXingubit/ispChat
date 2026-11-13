import 'dart:async';
import 'dart:convert';

import 'package:flutter_webrtc/flutter_webrtc.dart' hide MessageType;
import 'package:app/core/crypto/identity_crypto_service.dart';
import 'package:app/core/grpc/generated/ispchat.pb.dart';
import 'package:app/core/messaging/e2e_session_manager.dart';

/// Lifecycle states of a WebRTC call session.
enum CallStatus {
  idle,
  ringingOutgoing,
  ringingIncoming,
  connecting,
  inCall,
  ended,
}

/// Immutable UI snapshot for the active WebRTC call.
class WebRtcCallSnapshot {
  final CallStatus status;
  final String? peerUuid;
  final String? callId;
  final bool videoEnabled;
  final bool audioMuted;
  final bool cameraDisabled;
  final String? endReason;
  final int iceCandidateCount;
  final bool turnAuthenticated;

  const WebRtcCallSnapshot({
    this.status = CallStatus.idle,
    this.peerUuid,
    this.callId,
    this.videoEnabled = false,
    this.audioMuted = false,
    this.cameraDisabled = false,
    this.endReason,
    this.iceCandidateCount = 0,
    this.turnAuthenticated = false,
  });

  WebRtcCallSnapshot copyWith({
    CallStatus? status,
    String? peerUuid,
    String? callId,
    bool? videoEnabled,
    bool? audioMuted,
    bool? cameraDisabled,
    String? endReason,
    int? iceCandidateCount,
    bool? turnAuthenticated,
  }) {
    return WebRtcCallSnapshot(
      status: status ?? this.status,
      peerUuid: peerUuid ?? this.peerUuid,
      callId: callId ?? this.callId,
      videoEnabled: videoEnabled ?? this.videoEnabled,
      audioMuted: audioMuted ?? this.audioMuted,
      cameraDisabled: cameraDisabled ?? this.cameraDisabled,
      endReason: endReason ?? this.endReason,
      iceCandidateCount: iceCandidateCount ?? this.iceCandidateCount,
      turnAuthenticated: turnAuthenticated ?? this.turnAuthenticated,
    );
  }
}

/// Abstraction over a native WebRTC peer connection & media streams so unit
/// tests can exercise call negotiation deterministically without camera hardware.
abstract class WebRtcMediaEngine {
  Future<void> initializeRenderers();
  Future<void> startSession({
    required Map<String, dynamic> rtcConfiguration,
    required bool videoEnabled,
    required void Function(RTCIceCandidate candidate) onIceCandidate,
    required void Function() onRemoteTrackReady,
  });
  Future<RTCSessionDescription> createOffer();
  Future<RTCSessionDescription> createAnswer();
  Future<void> setLocalDescription(RTCSessionDescription description);
  Future<void> setRemoteDescription(RTCSessionDescription description);
  Future<void> addIceCandidate(RTCIceCandidate candidate);
  void setMicrophoneMuted(bool muted);
  void setVideoDisabled(bool disabled);
  RTCVideoRenderer? get localVideoRenderer;
  RTCVideoRenderer? get remoteVideoRenderer;
  Future<void> stopAndClose();
}

/// Production `flutter_webrtc` engine backed by native `RTCPeerConnection` and
/// `navigator.mediaDevices.getUserMedia`.
class FlutterWebRtcMediaEngine implements WebRtcMediaEngine {
  RTCPeerConnection? _peerConnection;
  MediaStream? _localStream;
  RTCVideoRenderer? _localRenderer;
  RTCVideoRenderer? _remoteRenderer;

  @override
  RTCVideoRenderer? get localVideoRenderer => _localRenderer;

  @override
  RTCVideoRenderer? get remoteVideoRenderer => _remoteRenderer;

  @override
  Future<void> initializeRenderers() async {
    if (_localRenderer == null) {
      _localRenderer = RTCVideoRenderer();
      await _localRenderer!.initialize();
    }
    if (_remoteRenderer == null) {
      _remoteRenderer = RTCVideoRenderer();
      await _remoteRenderer!.initialize();
    }
  }

  @override
  Future<void> startSession({
    required Map<String, dynamic> rtcConfiguration,
    required bool videoEnabled,
    required void Function(RTCIceCandidate candidate) onIceCandidate,
    required void Function() onRemoteTrackReady,
  }) async {
    await initializeRenderers();
    _localStream = await navigator.mediaDevices.getUserMedia(<String, dynamic>{
      'audio': true,
      'video': videoEnabled
          ? <String, dynamic>{'facingMode': 'user'}
          : false,
    });
    if (_localRenderer != null) {
      _localRenderer!.srcObject = _localStream;
    }

    final pc = await createPeerConnection(rtcConfiguration);
    _peerConnection = pc;

    for (final track in _localStream!.getTracks()) {
      await pc.addTrack(track, _localStream!);
    }

    pc.onIceCandidate = (candidate) {
      if (candidate.candidate != null && candidate.candidate!.isNotEmpty) {
        onIceCandidate(candidate);
      }
    };

    pc.onTrack = (event) {
      if (event.streams.isNotEmpty && _remoteRenderer != null) {
        _remoteRenderer!.srcObject = event.streams.first;
      }
      onRemoteTrackReady();
    };
  }

  @override
  Future<RTCSessionDescription> createOffer() async {
    return _peerConnection!.createOffer();
  }

  @override
  Future<RTCSessionDescription> createAnswer() async {
    return _peerConnection!.createAnswer();
  }

  @override
  Future<void> setLocalDescription(RTCSessionDescription description) {
    return _peerConnection!.setLocalDescription(description);
  }

  @override
  Future<void> setRemoteDescription(RTCSessionDescription description) {
    return _peerConnection!.setRemoteDescription(description);
  }

  @override
  Future<void> addIceCandidate(RTCIceCandidate candidate) {
    return _peerConnection!.addCandidate(candidate);
  }

  @override
  void setMicrophoneMuted(bool muted) {
    final stream = _localStream;
    if (stream == null) return;
    for (final track in stream.getAudioTracks()) {
      track.enabled = !muted;
    }
  }

  @override
  void setVideoDisabled(bool disabled) {
    final stream = _localStream;
    if (stream == null) return;
    for (final track in stream.getVideoTracks()) {
      track.enabled = !disabled;
    }
  }

  @override
  Future<void> stopAndClose() async {
    _localRenderer?.srcObject = null;
    _remoteRenderer?.srcObject = null;
    final stream = _localStream;
    _localStream = null;
    if (stream != null) {
      for (final track in stream.getTracks()) {
        await track.stop();
      }
      await stream.dispose();
    }
    final pc = _peerConnection;
    _peerConnection = null;
    if (pc != null) {
      await pc.close();
    }
  }
}

/// Coordinates end-to-end encrypted WebRTC signaling (`RING`, `SDP_OFFER`,
/// `SDP_ANSWER`, `ICE_CANDIDATE`, `CALL_DECLINE`), signed TURN credential
/// provisioning, and real `flutter_webrtc` peer media lifecycle.
class WebRtcCallCoordinator {
  final E2eSessionManager sessionManager;
  final WebRtcMediaEngine _engine;

  final StreamController<WebRtcCallSnapshot> _stateController =
      StreamController<WebRtcCallSnapshot>.broadcast();
  WebRtcCallSnapshot _snapshot = const WebRtcCallSnapshot();
  StreamSubscription<DecryptedSignalingMessage>? _signalingSub;

  ContactBundle? _activePeer;
  Map<String, dynamic>? _pendingOffer;
  final List<RTCIceCandidate> _pendingRemoteIce = <RTCIceCandidate>[];
  bool _remoteDescriptionSet = false;

  WebRtcCallCoordinator({
    required this.sessionManager,
    WebRtcMediaEngine? mediaEngine,
  }) : _engine = mediaEngine ?? FlutterWebRtcMediaEngine() {
    _signalingSub = sessionManager.incomingSignaling.listen(
      (msg) {
        unawaited(handleIncomingSignaling(msg));
      },
    );
  }

  Stream<WebRtcCallSnapshot> get callStateStream => _stateController.stream;
  WebRtcCallSnapshot get currentSnapshot => _snapshot;
  WebRtcMediaEngine get mediaEngine => _engine;

  /// Converts a [TurnCredentialResponse] into an `RTCPeerConnection` ICE config.
  static Map<String, dynamic> buildRtcConfiguration(
    TurnCredentialResponse? turnResponse,
  ) {
    final iceServers = <Map<String, dynamic>>[];
    if (turnResponse != null && turnResponse.uris.isNotEmpty) {
      iceServers.add(<String, dynamic>{
        'urls': turnResponse.uris.toList(growable: false),
        if (turnResponse.username.isNotEmpty)
          'username': turnResponse.username,
        if (turnResponse.credential.isNotEmpty)
          'credential': turnResponse.credential,
      });
    } else {
      iceServers.add(const <String, dynamic>{
        'urls': <String>['stun:stun.l.google.com:19302'],
      });
    }
    return <String, dynamic>{
      'iceServers': iceServers,
      'sdpSemantics': 'unified-plan',
    };
  }

  /// Initiates an audio or video call to [peer].
  Future<void> startCall({
    required ContactBundle peer,
    bool videoEnabled = false,
  }) async {
    if (_snapshot.status != CallStatus.idle &&
        _snapshot.status != CallStatus.ended) {
      throw StateError('A call is already active.');
    }

    final callId = IdentityCryptoService.generateTemporalUuidV7();
    _activePeer = peer;
    _remoteDescriptionSet = false;
    _pendingRemoteIce.clear();

    _emit(
      WebRtcCallSnapshot(
        status: CallStatus.ringingOutgoing,
        peerUuid: peer.uuid,
        callId: callId,
        videoEnabled: videoEnabled,
      ),
    );

    await sessionManager.sendSignalingPayload(
      recipient: peer,
      type: MessageType.RING,
      payload: <String, dynamic>{
        'callId': callId,
        'video': videoEnabled,
      },
    );

    final turnResp = await _fetchTurnCredentials();
    await _engine.startSession(
      rtcConfiguration: buildRtcConfiguration(turnResp),
      videoEnabled: videoEnabled,
      onIceCandidate: (candidate) {
        unawaited(_sendIceCandidate(peer, callId, candidate));
      },
      onRemoteTrackReady: () {
        _emit(_snapshot.copyWith(status: CallStatus.inCall));
      },
    );

    final offer = await _engine.createOffer();
    await _engine.setLocalDescription(offer);

    _emit(
      _snapshot.copyWith(
        status: CallStatus.connecting,
        turnAuthenticated: turnResp != null && turnResp.uris.isNotEmpty,
      ),
    );

    await sessionManager.sendSignalingPayload(
      recipient: peer,
      type: MessageType.SDP_OFFER,
      payload: <String, dynamic>{
        'callId': callId,
        'sdp': offer.sdp,
        'type': offer.type,
        'video': videoEnabled,
      },
    );
  }

  /// Accepts an incoming ringing call, fetches TURN credentials, sets the
  /// remote offer, and transmits an encrypted `SDP_ANSWER`.
  Future<void> acceptIncomingCall({bool? videoEnabled}) async {
    if (_snapshot.status != CallStatus.ringingIncoming || _activePeer == null) {
      throw StateError('No incoming ringing call to accept.');
    }
    final peer = _activePeer!;
    final callId = _snapshot.callId!;
    final effectiveVideo = videoEnabled ?? _snapshot.videoEnabled;

    _emit(
      _snapshot.copyWith(
        status: CallStatus.connecting,
        videoEnabled: effectiveVideo,
      ),
    );

    final turnResp = await _fetchTurnCredentials();
    await _engine.startSession(
      rtcConfiguration: buildRtcConfiguration(turnResp),
      videoEnabled: effectiveVideo,
      onIceCandidate: (candidate) {
        unawaited(_sendIceCandidate(peer, callId, candidate));
      },
      onRemoteTrackReady: () {
        _emit(_snapshot.copyWith(status: CallStatus.inCall));
      },
    );

    _emit(
      _snapshot.copyWith(
        turnAuthenticated: turnResp != null && turnResp.uris.isNotEmpty,
      ),
    );

    final offer = _pendingOffer;
    if (offer != null) {
      await _applyRemoteOfferAndSendAnswer(peer, callId, offer);
    }
  }

  Future<void> declineCall({String reason = 'declined'}) async {
    final peer = _activePeer;
    final callId = _snapshot.callId;
    if (peer != null && callId != null) {
      await _trySendDecline(peer, callId, reason);
    }
    await _resetSession(reason);
  }

  Future<void> hangup() async {
    await declineCall(reason: 'hangup');
  }

  void toggleMute() {
    final next = !_snapshot.audioMuted;
    _engine.setMicrophoneMuted(next);
    _emit(_snapshot.copyWith(audioMuted: next));
  }

  void toggleCamera() {
    final next = !_snapshot.cameraDisabled;
    _engine.setVideoDisabled(next);
    _emit(_snapshot.copyWith(cameraDisabled: next));
  }

  /// Processes decrypted signaling frames delivered from [E2eSessionManager].
  Future<void> handleIncomingSignaling(DecryptedSignalingMessage msg) async {
    Map<String, dynamic> data;
    try {
      data = jsonDecode(msg.payloadJson) as Map<String, dynamic>;
    } catch (_) {
      return;
    }

    final contacts = await sessionManager.vault.loadContacts();
    ContactBundle? peer;
    for (final c in contacts) {
      if (CanonicalTranscripts.canonicalUuid(c.uuid) ==
          CanonicalTranscripts.canonicalUuid(msg.senderUuid)) {
        peer = c;
        break;
      }
    }
    if (peer == null) return;

    switch (msg.type) {
      case MessageType.RING:
        if (_snapshot.status != CallStatus.idle &&
            _snapshot.status != CallStatus.ended) {
          await _trySendDecline(
            peer,
            (data['callId'] as String?) ?? msg.messageId,
            'busy',
          );
          return;
        }
        _activePeer = peer;
        _pendingOffer = null;
        _pendingRemoteIce.clear();
        _remoteDescriptionSet = false;
        _emit(
          WebRtcCallSnapshot(
            status: CallStatus.ringingIncoming,
            peerUuid: peer.uuid,
            callId: (data['callId'] as String?) ?? msg.messageId,
            videoEnabled: (data['video'] as bool?) ?? false,
          ),
        );
        return;

      case MessageType.SDP_OFFER:
        if (_activePeer?.uuid != peer.uuid) return;
        if (_snapshot.status == CallStatus.ringingIncoming) {
          _pendingOffer = data;
          return;
        }
        await _applyRemoteOfferAndSendAnswer(
          peer,
          _snapshot.callId ?? (data['callId'] as String?) ?? msg.messageId,
          data,
        );
        return;

      case MessageType.SDP_ANSWER:
        if (_activePeer?.uuid != peer.uuid) return;
        final sdp = data['sdp'] as String?;
        final type = (data['type'] as String?) ?? 'answer';
        if (sdp == null) return;
        await _engine.setRemoteDescription(RTCSessionDescription(sdp, type));
        _remoteDescriptionSet = true;
        await _flushPendingIce();
        _emit(_snapshot.copyWith(status: CallStatus.inCall));
        return;

      case MessageType.ICE_CANDIDATE:
        if (_activePeer?.uuid != peer.uuid) return;
        final candidateStr = data['candidate'] as String?;
        if (candidateStr == null) return;
        final ice = RTCIceCandidate(
          candidateStr,
          data['sdpMid'] as String?,
          data['sdpMLineIndex'] as int?,
        );
        if (_remoteDescriptionSet) {
          await _engine.addIceCandidate(ice);
        } else {
          _pendingRemoteIce.add(ice);
        }
        _emit(
          _snapshot.copyWith(iceCandidateCount: _snapshot.iceCandidateCount + 1),
        );
        return;

      case MessageType.CALL_DECLINE:
        if (_activePeer?.uuid == peer.uuid) {
          await _resetSession((data['reason'] as String?) ?? 'remote_ended');
        }
        return;

      default:
        return;
    }
  }

  Future<void> _applyRemoteOfferAndSendAnswer(
    ContactBundle peer,
    String callId,
    Map<String, dynamic> offerData,
  ) async {
    final sdp = offerData['sdp'] as String?;
    final type = (offerData['type'] as String?) ?? 'offer';
    if (sdp == null) return;
    await _engine.setRemoteDescription(RTCSessionDescription(sdp, type));
    _remoteDescriptionSet = true;
    await _flushPendingIce();

    final answer = await _engine.createAnswer();
    await _engine.setLocalDescription(answer);
    await sessionManager.sendSignalingPayload(
      recipient: peer,
      type: MessageType.SDP_ANSWER,
      payload: <String, dynamic>{
        'callId': callId,
        'sdp': answer.sdp,
        'type': answer.type,
      },
    );
    _emit(_snapshot.copyWith(status: CallStatus.inCall));
  }

  Future<void> _flushPendingIce() async {
    for (final ice in List<RTCIceCandidate>.from(_pendingRemoteIce)) {
      await _engine.addIceCandidate(ice);
    }
    _pendingRemoteIce.clear();
  }

  Future<void> _sendIceCandidate(
    ContactBundle peer,
    String callId,
    RTCIceCandidate candidate,
  ) async {
    try {
      await sessionManager.sendSignalingPayload(
        recipient: peer,
        type: MessageType.ICE_CANDIDATE,
        payload: <String, dynamic>{
          'callId': callId,
          'candidate': candidate.candidate,
          'sdpMid': candidate.sdpMid,
          'sdpMLineIndex': candidate.sdpMLineIndex,
        },
      );
    } catch (_) {}
  }

  Future<void> _trySendDecline(
    ContactBundle peer,
    String callId,
    String reason,
  ) async {
    try {
      await sessionManager.sendSignalingPayload(
        recipient: peer,
        type: MessageType.CALL_DECLINE,
        payload: <String, dynamic>{
          'callId': callId,
          'reason': reason,
        },
      );
    } catch (_) {}
  }

  Future<TurnCredentialResponse?> _fetchTurnCredentials() async {
    if (!sessionManager.nodeClient.isConnected) {
      return null;
    }
    try {
      final req = await sessionManager.crypto.buildSignedTurnCredentialRequest(
        userUuid: sessionManager.activeUserUuid,
        deviceIdentity: sessionManager.deviceIdentity,
        verificationCredential: sessionManager.currentCredential,
      );
      return await sessionManager.nodeClient.getTurnCredentials(req);
    } catch (_) {
      return null;
    }
  }

  Future<void> _resetSession(String reason) async {
    _activePeer = null;
    _pendingOffer = null;
    _pendingRemoteIce.clear();
    _remoteDescriptionSet = false;
    await _engine.stopAndClose();
    _emit(
      WebRtcCallSnapshot(
        status: CallStatus.ended,
        endReason: reason,
      ),
    );
  }

  void _emit(WebRtcCallSnapshot next) {
    _snapshot = next;
    if (!_stateController.isClosed) {
      _stateController.add(next);
    }
  }

  Future<void> dispose() async {
    await _signalingSub?.cancel();
    await _engine.stopAndClose();
    await _stateController.close();
  }
}
