import 'dart:io';

import 'package:app/core/crypto/identity_crypto_service.dart';
import 'package:app/core/discovery/tcp_latency_probe.dart';
import 'package:app/core/grpc/ispchat_pb.dart';
import 'package:app/core/messaging/e2e_session_manager.dart';
import 'package:app/core/security/blocklist_service.dart';
import 'package:app/core/webrtc/webrtc_call_coordinator.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('WebRtcCallCoordinator (Ciclo Completo de Sinalizacao WebRTC E2E)', () {
    test('Deve negociar RING, SDP_OFFER, SDP_ANSWER, ICE_CANDIDATE e CALL_DECLINE criptografados E2E', () async {
      final IdentityCryptoService crypto = IdentityCryptoService();
      final E2eSessionManager aliceManager = E2eSessionManager(
        cryptoService: crypto,
        blocklistService: BlocklistService(),
      );
      final E2eSessionManager bobManager = E2eSessionManager(
        cryptoService: crypto,
        blocklistService: BlocklistService(),
      );

      final WebRtcCallCoordinator aliceCalls = WebRtcCallCoordinator(sessionManager: aliceManager);
      final WebRtcCallCoordinator bobCalls = WebRtcCallCoordinator(sessionManager: bobManager);

      final UserIdentity alice = await crypto.generateNewIdentity();
      final UserIdentity bob = await crypto.generateNewIdentity();

      final ContactBundle aliceContact = await crypto.parseAndVerifyContactUri(
        crypto.buildShareableContactUri(alice),
      );
      final ContactBundle bobContact = await crypto.parseAndVerifyContactUri(
        crypto.buildShareableContactUri(bob),
      );

      aliceManager.registerVerifiedContact(bobContact);
      bobManager.registerVerifiedContact(aliceContact);

      const String callId = '01950000-0000-7000-8000-00000000ca11';

      // 1. Alice inicia o toque (RING)
      final ClientEnvelopeProto ringClient = await aliceCalls.initiateCallRing(
        callerIdentity: alice,
        calleeContact: bobContact,
        callId: callId,
        videoEnabled: true,
      );
      expect(aliceCalls.currentCall.state, equals(CallLifecycleState.ringingOutgoing));

      final DecryptedIncomingEvent? bobRingEvent = await bobManager.processIncomingServerEnvelope(
        localIdentity: bob,
        serverEnvelope: _toServerEnvelope(ringClient),
      );
      expect(bobRingEvent, isNotNull);
      final WebRtcCallSnapshot bobAfterRing = bobCalls.handleIncomingSignalingEvent(bobRingEvent!);
      expect(bobAfterRing.state, equals(CallLifecycleState.ringingIncoming));
      expect(bobAfterRing.callId, equals(callId));

      // 2. Alice envia SDP_OFFER criptografado E2E
      const String sampleOfferSdp = 'v=0\r\no=- 111 2 IN IP6 2804:14d::1\r\ns=-\r\n';
      final ClientEnvelopeProto offerClient = await aliceCalls.createEncryptedSdpOffer(
        callerIdentity: alice,
        calleeContact: bobContact,
        callId: callId,
        sdpOffer: sampleOfferSdp,
      );
      final DecryptedIncomingEvent? bobOfferEvent = await bobManager.processIncomingServerEnvelope(
        localIdentity: bob,
        serverEnvelope: _toServerEnvelope(offerClient),
      );
      final WebRtcCallSnapshot bobAfterOffer = bobCalls.handleIncomingSignalingEvent(bobOfferEvent!);
      expect(bobAfterOffer.state, equals(CallLifecycleState.connecting));
      expect(bobAfterOffer.remoteSdpOffer, equals(sampleOfferSdp));

      // 3. Bob atende enviando SDP_ANSWER criptografado E2E
      const String sampleAnswerSdp = 'v=0\r\no=- 222 2 IN IP6 2804:14d::2\r\ns=-\r\n';
      final ClientEnvelopeProto answerClient = await bobCalls.createEncryptedSdpAnswer(
        calleeIdentity: bob,
        callerContact: aliceContact,
        callId: callId,
        sdpAnswer: sampleAnswerSdp,
      );
      expect(bobCalls.currentCall.state, equals(CallLifecycleState.connected));

      final DecryptedIncomingEvent? aliceAnswerEvent = await aliceManager.processIncomingServerEnvelope(
        localIdentity: alice,
        serverEnvelope: _toServerEnvelope(answerClient),
      );
      final WebRtcCallSnapshot aliceAfterAnswer = aliceCalls.handleIncomingSignalingEvent(aliceAnswerEvent!);
      expect(aliceAfterAnswer.state, equals(CallLifecycleState.connected));
      expect(aliceAfterAnswer.remoteSdpAnswer, equals(sampleAnswerSdp));

      // 4. Troca de ICE_CANDIDATE criptografado E2E
      final ClientEnvelopeProto iceClient = await aliceCalls.createEncryptedIceCandidate(
        localIdentity: alice,
        peerContact: bobContact,
        callId: callId,
        candidate: 'candidate:1 1 UDP 2122252543 2804:14d::1 54321 typ host',
        sdpMid: '0',
        sdpMLineIndex: 0,
      );
      final DecryptedIncomingEvent? bobIceEvent = await bobManager.processIncomingServerEnvelope(
        localIdentity: bob,
        serverEnvelope: _toServerEnvelope(iceClient),
      );
      final WebRtcCallSnapshot bobAfterIce = bobCalls.handleIncomingSignalingEvent(bobIceEvent!);
      expect(bobAfterIce.remoteIceCandidates.length, equals(1));

      // 5. Encerramento da chamada (CALL_DECLINE)
      final ClientEnvelopeProto declineClient = await bobCalls.declineOrHangupCall(
        localIdentity: bob,
        peerContact: aliceContact,
        callId: callId,
      );
      final DecryptedIncomingEvent? aliceDeclineEvent = await aliceManager.processIncomingServerEnvelope(
        localIdentity: alice,
        serverEnvelope: _toServerEnvelope(declineClient),
      );
      final WebRtcCallSnapshot aliceAfterDecline = aliceCalls.handleIncomingSignalingEvent(aliceDeclineEvent!);
      expect(aliceAfterDecline.state, equals(CallLifecycleState.declined));
    });
  });

  group('TcpLatencyProbe (Medicao Real de Ping TCP e Parser IPv4/IPv6)', () {
    test('Deve fazer parse de enderecos IPv4, hostname e IPv6 com colchetes', () {
      final ({String host, int port}) ipv4Default = TcpLatencyProbe.parseHostAndPort('203.0.113.10');
      expect(ipv4Default.host, equals('203.0.113.10'));
      expect(ipv4Default.port, equals(443));

      final ({String host, int port}) ipv4Custom = TcpLatencyProbe.parseHostAndPort('127.0.0.1:8443');
      expect(ipv4Custom.host, equals('127.0.0.1'));
      expect(ipv4Custom.port, equals(8443));

      final ({String host, int port}) ipv6Bracket = TcpLatencyProbe.parseHostAndPort('[2804:14d:1::99]:443');
      expect(ipv6Bracket.host, equals('2804:14d:1::99'));
      expect(ipv6Bracket.port, equals(443));
    });

    test('Deve medir latencia TCP real contra um socket local aberto', () async {
      final ServerSocket server = await ServerSocket.bind(InternetAddress.loopbackIPv4, 0);
      addTearDown(() async => server.close());

      const TcpLatencyProbe probe = TcpLatencyProbe(timeout: Duration(seconds: 1));
      final int? latencyMs = await probe.measureLatencyMs('127.0.0.1:${server.port}');
      expect(latencyMs, isNotNull);
      expect(latencyMs!, greaterThanOrEqualTo(0));
    });
  });
}

ServerEnvelopeProto _toServerEnvelope(ClientEnvelopeProto client) {
  return ServerEnvelopeProto(
    messageId: client.messageId,
    senderUuid: client.senderUuid,
    type: client.type,
    encryptedPayload: client.encryptedPayload,
    senderPublicKey: client.senderPublicKey,
    signature: client.signature,
    timestamp: client.timestamp,
  );
}
