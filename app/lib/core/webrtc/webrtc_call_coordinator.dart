import 'dart:convert';

import '../crypto/identity_crypto_service.dart';
import '../grpc/ispchat_pb.dart';
import '../messaging/e2e_session_manager.dart';

/// Estados do ciclo de vida de uma chamada de voz/video WebRTC E2E.
enum CallLifecycleState {
  idle,
  ringingOutgoing,
  ringingIncoming,
  connecting,
  connected,
  declined,
  ended,
}

/// Snapshot imutavel do estado atual da chamada WebRTC.
class WebRtcCallSnapshot {
  final String? callId;
  final String? peerUuid;
  final CallLifecycleState state;
  final String? remoteSdpOffer;
  final String? remoteSdpAnswer;
  final List<Map<String, dynamic>> remoteIceCandidates;

  const WebRtcCallSnapshot({
    this.callId,
    this.peerUuid,
    this.state = CallLifecycleState.idle,
    this.remoteSdpOffer,
    this.remoteSdpAnswer,
    this.remoteIceCandidates = const <Map<String, dynamic>>[],
  });

  WebRtcCallSnapshot copyWith({
    String? callId,
    String? peerUuid,
    CallLifecycleState? state,
    String? remoteSdpOffer,
    String? remoteSdpAnswer,
    List<Map<String, dynamic>>? remoteIceCandidates,
  }) {
    return WebRtcCallSnapshot(
      callId: callId ?? this.callId,
      peerUuid: peerUuid ?? this.peerUuid,
      state: state ?? this.state,
      remoteSdpOffer: remoteSdpOffer ?? this.remoteSdpOffer,
      remoteSdpAnswer: remoteSdpAnswer ?? this.remoteSdpAnswer,
      remoteIceCandidates: remoteIceCandidates ?? this.remoteIceCandidates,
    );
  }
}

/// Coordenador de Sinalizacao WebRTC E2E e Modo Doomsday (IPv6 P2P Direto).
///
/// Todos os metadados de sinalizacao (RING, SDP_OFFER, SDP_ANSWER, ICE_CANDIDATE, CALL_DECLINE)
/// sao criptografados de ponta a ponta com X25519 + ChaCha20-Poly1305 e assinados com Ed25519
/// via `E2eSessionManager`, garantindo que o No do Provedor enxergue zero metadados de SDP/IP.
class WebRtcCallCoordinator {
  final E2eSessionManager sessionManager;
  WebRtcCallSnapshot _currentCall = const WebRtcCallSnapshot();

  WebRtcCallCoordinator({
    required this.sessionManager,
  });

  WebRtcCallSnapshot get currentCall => _currentCall;

  /// Inicia uma chamada enviando o pacote `RING` criptografado E2E (que fara todos os
  /// dispositivos do destinatario tocarem simultaneamente via broadcast do No).
  Future<ClientEnvelopeProto> initiateCallRing({
    required UserIdentity callerIdentity,
    required ContactBundle calleeContact,
    required String callId,
    bool videoEnabled = false,
    int? nowMillis,
  }) async {
    final String payloadJson = jsonEncode(<String, dynamic>{
      'callId': callId,
      'video': videoEnabled,
    });

    _currentCall = WebRtcCallSnapshot(
      callId: callId,
      peerUuid: calleeContact.userUuid,
      state: CallLifecycleState.ringingOutgoing,
    );

    return sessionManager.buildOutgoingEncryptedEnvelope(
      senderIdentity: callerIdentity,
      recipientContact: calleeContact,
      type: ProtoMessageType.ring,
      cleartextOrJsonSignal: payloadJson,
      nowMillis: nowMillis,
    );
  }

  /// Envia o `SDP_OFFER` WebRTC criptografado E2E para o destinatario.
  Future<ClientEnvelopeProto> createEncryptedSdpOffer({
    required UserIdentity callerIdentity,
    required ContactBundle calleeContact,
    required String callId,
    required String sdpOffer,
    int? nowMillis,
  }) async {
    final String payloadJson = jsonEncode(<String, dynamic>{
      'callId': callId,
      'type': 'offer',
      'sdp': sdpOffer,
    });

    _currentCall = _currentCall.copyWith(
      callId: callId,
      peerUuid: calleeContact.userUuid,
      state: CallLifecycleState.connecting,
    );

    return sessionManager.buildOutgoingEncryptedEnvelope(
      senderIdentity: callerIdentity,
      recipientContact: calleeContact,
      type: ProtoMessageType.sdpOffer,
      cleartextOrJsonSignal: payloadJson,
      nowMillis: nowMillis,
    );
  }

  /// Aceita uma chamada enviando o `SDP_ANSWER` WebRTC criptografado E2E.
  Future<ClientEnvelopeProto> createEncryptedSdpAnswer({
    required UserIdentity calleeIdentity,
    required ContactBundle callerContact,
    required String callId,
    required String sdpAnswer,
    int? nowMillis,
  }) async {
    final String payloadJson = jsonEncode(<String, dynamic>{
      'callId': callId,
      'type': 'answer',
      'sdp': sdpAnswer,
    });

    _currentCall = _currentCall.copyWith(
      callId: callId,
      peerUuid: callerContact.userUuid,
      state: CallLifecycleState.connected,
    );

    return sessionManager.buildOutgoingEncryptedEnvelope(
      senderIdentity: calleeIdentity,
      recipientContact: callerContact,
      type: ProtoMessageType.sdpAnswer,
      cleartextOrJsonSignal: payloadJson,
      nowMillis: nowMillis,
    );
  }

  /// Envia um `ICE_CANDIDATE` WebRTC criptografado E2E.
  Future<ClientEnvelopeProto> createEncryptedIceCandidate({
    required UserIdentity localIdentity,
    required ContactBundle peerContact,
    required String callId,
    required String candidate,
    required String sdpMid,
    required int sdpMLineIndex,
    int? nowMillis,
  }) async {
    final String payloadJson = jsonEncode(<String, dynamic>{
      'callId': callId,
      'candidate': candidate,
      'sdpMid': sdpMid,
      'sdpMLineIndex': sdpMLineIndex,
    });

    return sessionManager.buildOutgoingEncryptedEnvelope(
      senderIdentity: localIdentity,
      recipientContact: peerContact,
      type: ProtoMessageType.iceCandidate,
      cleartextOrJsonSignal: payloadJson,
      nowMillis: nowMillis,
    );
  }

  /// Recusa ou encerra uma chamada enviando `CALL_DECLINE` criptografado E2E.
  Future<ClientEnvelopeProto> declineOrHangupCall({
    required UserIdentity localIdentity,
    required ContactBundle peerContact,
    required String callId,
    int? nowMillis,
  }) async {
    final String payloadJson = jsonEncode(<String, dynamic>{
      'callId': callId,
      'reason': 'declined_or_hangup',
    });

    _currentCall = _currentCall.copyWith(
      callId: callId,
      peerUuid: peerContact.userUuid,
      state: CallLifecycleState.declined,
    );

    return sessionManager.buildOutgoingEncryptedEnvelope(
      senderIdentity: localIdentity,
      recipientContact: peerContact,
      type: ProtoMessageType.callDecline,
      cleartextOrJsonSignal: payloadJson,
      nowMillis: nowMillis,
    );
  }

  /// Atualiza a maquina de estados da chamada a partir de um evento E2E ja descriptografado.
  WebRtcCallSnapshot handleIncomingSignalingEvent(DecryptedIncomingEvent event) {
    final Map<String, dynamic> decoded =
        jsonDecode(event.cleartextOrJsonSignal) as Map<String, dynamic>;
    final String? callId = decoded['callId'] as String?;

    switch (event.type) {
      case ProtoMessageType.ring:
        _currentCall = WebRtcCallSnapshot(
          callId: callId,
          peerUuid: event.senderUuid,
          state: CallLifecycleState.ringingIncoming,
        );
      case ProtoMessageType.sdpOffer:
        _currentCall = _currentCall.copyWith(
          callId: callId,
          peerUuid: event.senderUuid,
          state: CallLifecycleState.connecting,
          remoteSdpOffer: decoded['sdp'] as String?,
        );
      case ProtoMessageType.sdpAnswer:
        _currentCall = _currentCall.copyWith(
          callId: callId,
          peerUuid: event.senderUuid,
          state: CallLifecycleState.connected,
          remoteSdpAnswer: decoded['sdp'] as String?,
        );
      case ProtoMessageType.iceCandidate:
        final List<Map<String, dynamic>> updatedCandidates =
            List<Map<String, dynamic>>.from(_currentCall.remoteIceCandidates)..add(decoded);
        _currentCall = _currentCall.copyWith(
          callId: callId,
          peerUuid: event.senderUuid,
          remoteIceCandidates: updatedCandidates,
        );
      case ProtoMessageType.callDecline:
        _currentCall = _currentCall.copyWith(
          callId: callId,
          peerUuid: event.senderUuid,
          state: CallLifecycleState.declined,
        );
      case ProtoMessageType.unknown:
      case ProtoMessageType.text:
      case ProtoMessageType.authHandshake:
        break;
    }

    return _currentCall;
  }

  void resetCallState() {
    _currentCall = const WebRtcCallSnapshot();
  }
}
