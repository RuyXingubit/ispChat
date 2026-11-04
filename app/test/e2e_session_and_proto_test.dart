import 'package:app/core/crypto/identity_crypto_service.dart';
import 'package:app/core/grpc/ispchat_pb.dart';
import 'package:app/core/messaging/e2e_session_manager.dart';
import 'package:app/core/security/blocklist_service.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  late IdentityCryptoService cryptoService;
  late BlocklistService bobBlocklist;
  late E2eSessionManager aliceSession;
  late E2eSessionManager bobSession;

  setUp(() {
    cryptoService = IdentityCryptoService();
    bobBlocklist = BlocklistService();
    aliceSession = E2eSessionManager(
      cryptoService: cryptoService,
      blocklistService: BlocklistService(),
    );
    bobSession = E2eSessionManager(
      cryptoService: cryptoService,
      blocklistService: bobBlocklist,
    );
  });

  test('Deve serializar e desserializar ClientEnvelopeProto e ServerEnvelopeProto em formato wire Protobuf', () async {
    final UserIdentity alice = await cryptoService.generateNewIdentity();
    final ClientEnvelopeProto handshake = await aliceSession.createSignedAuthHandshake(
      alice,
      nowMillis: 1762128000000,
    );

    final List<int> wireBytes = handshake.writeToBuffer();
    final ClientEnvelopeProto decoded = ClientEnvelopeProto.fromBuffer(wireBytes);

    expect(decoded.messageId, equals(handshake.messageId));
    expect(decoded.senderUuid, equals(alice.userUuid));
    expect(decoded.receiverUuid, equals(alice.userUuid));
    expect(decoded.type, equals(ProtoMessageType.authHandshake));
    expect(decoded.senderPublicKey, equals(alice.ed25519PublicKeyBytes));
    expect(decoded.signature, equals(handshake.signature));
    expect(decoded.timestamp, equals(1762128000000));

    // Valida serializacao wire de FindUserSessionsRequestProto e FindUserSessionsResponseProto
    final FindUserSessionsRequestProto findReq =
        FindUserSessionsRequestProto(targetUuid: alice.userUuid);
    final FindUserSessionsRequestProto decodedFindReq =
        FindUserSessionsRequestProto.fromBuffer(findReq.writeToBuffer());
    expect(decodedFindReq.targetUuid, equals(alice.userUuid));

    final FindUserSessionsResponseProto findRes = FindUserSessionsResponseProto(
      nodeAddresses: const <String>['node1.isp.dev.br:443'],
      sessions: <UserSessionInfoProto>[
        UserSessionInfoProto(
          nodeAddress: 'node1.isp.dev.br:443',
          ed25519PublicKey: alice.ed25519PublicKeyBytes,
          x25519PublicKey: alice.x25519PublicKeyBytes,
          timestamp: 1762128000000,
        ),
      ],
    );
    final FindUserSessionsResponseProto decodedFindRes =
        FindUserSessionsResponseProto.fromBuffer(findRes.writeToBuffer());
    expect(decodedFindRes.found, isTrue);
    expect(decodedFindRes.nodeAddresses, equals(<String>['node1.isp.dev.br:443']));
    expect(decodedFindRes.sessions.single.ed25519PublicKey, equals(alice.ed25519PublicKeyBytes));
    expect(decodedFindRes.sessions.single.x25519PublicKey, equals(alice.x25519PublicKeyBytes));
  });

  test('Deve completar fluxo E2E de texto e sinalizacao WebRTC entre Alice e Bob apos troca de Link Magico', () async {
    final UserIdentity alice = await cryptoService.generateNewIdentity();
    final UserIdentity bob = await cryptoService.generateNewIdentity();

    // Troca de identidade via Link Magico / QR Code
    final ContactBundle aliceBundle = await cryptoService.parseAndVerifyContactUri(
      cryptoService.buildShareableContactUri(alice),
    );
    final ContactBundle bobBundle = await cryptoService.parseAndVerifyContactUri(
      cryptoService.buildShareableContactUri(bob),
    );

    aliceSession.registerVerifiedContact(bobBundle);
    bobSession.registerVerifiedContact(aliceBundle);

    // 1. Alice envia mensagem de texto E2E para Bob
    final ClientEnvelopeProto clientEnv = await aliceSession.buildOutgoingEncryptedEnvelope(
      senderIdentity: alice,
      recipientContact: bobBundle,
      type: ProtoMessageType.text,
      cleartextOrJsonSignal: 'Mensagem ultra secreta via malha P2P ISP Chat!',
      nowMillis: 1762128005000,
    );

    // Simula o No repassando para Bob como ServerEnvelopeProto (serializado em wire Protobuf)
    final ServerEnvelopeProto serverWire = ServerEnvelopeProto(
      messageId: clientEnv.messageId,
      senderUuid: clientEnv.senderUuid,
      type: clientEnv.type,
      encryptedPayload: clientEnv.encryptedPayload,
      senderPublicKey: clientEnv.senderPublicKey,
      signature: clientEnv.signature,
      timestamp: clientEnv.timestamp,
    );
    final ServerEnvelopeProto receivedFromWire = ServerEnvelopeProto.fromBuffer(serverWire.writeToBuffer());

    final DecryptedIncomingEvent? event = await bobSession.processIncomingServerEnvelope(
      localIdentity: bob,
      serverEnvelope: receivedFromWire,
    );

    expect(event, isNotNull);
    expect(event!.senderUuid, equals(alice.userUuid));
    expect(event.type, equals(ProtoMessageType.text));
    expect(event.cleartextOrJsonSignal, equals('Mensagem ultra secreta via malha P2P ISP Chat!'));

    // 2. Alice inicia chamada WebRTC enviando SDP_OFFER criptografado E2E
    final ClientEnvelopeProto sdpOfferEnv = await aliceSession.buildOutgoingEncryptedEnvelope(
      senderIdentity: alice,
      recipientContact: bobBundle,
      type: ProtoMessageType.sdpOffer,
      cleartextOrJsonSignal: '{"type":"offer","sdp":"v=0\\r\\no=- 12345 2 IN IP6 2804:14d::1"}',
      nowMillis: 1762128010000,
    );

    final DecryptedIncomingEvent? sdpEvent = await bobSession.processIncomingServerEnvelope(
      localIdentity: bob,
      serverEnvelope: ServerEnvelopeProto(
        messageId: sdpOfferEnv.messageId,
        senderUuid: sdpOfferEnv.senderUuid,
        type: sdpOfferEnv.type,
        encryptedPayload: sdpOfferEnv.encryptedPayload,
        senderPublicKey: sdpOfferEnv.senderPublicKey,
        signature: sdpOfferEnv.signature,
        timestamp: sdpOfferEnv.timestamp,
      ),
    );

    expect(sdpEvent, isNotNull);
    expect(sdpEvent!.type, equals(ProtoMessageType.sdpOffer));
    expect(sdpEvent.cleartextOrJsonSignal, contains('"type":"offer"'));
  });

  test('Deve derrubar silenciosamente chamada RING de remetente bloqueado na Blocklist Local', () async {
    final UserIdentity spammer = await cryptoService.generateNewIdentity();
    final UserIdentity bob = await cryptoService.generateNewIdentity();

    final ContactBundle spammerBundle = await cryptoService.parseAndVerifyContactUri(
      cryptoService.buildShareableContactUri(spammer),
    );
    final ContactBundle bobBundle = await cryptoService.parseAndVerifyContactUri(
      cryptoService.buildShareableContactUri(bob),
    );

    aliceSession.registerVerifiedContact(bobBundle);
    bobSession.registerVerifiedContact(spammerBundle);

    // Bob bloqueia o UUIDv7 do spammer localmente no aparelho
    bobBlocklist.blockUuid(spammer.userUuid);

    final ClientEnvelopeProto ringEnv = await aliceSession.buildOutgoingEncryptedEnvelope(
      senderIdentity: spammer,
      recipientContact: bobBundle,
      type: ProtoMessageType.ring,
      cleartextOrJsonSignal: '{"callId":"call-999"}',
      nowMillis: 1762128020000,
    );

    final DecryptedIncomingEvent? result = await bobSession.processIncomingServerEnvelope(
      localIdentity: bob,
      serverEnvelope: ServerEnvelopeProto(
        messageId: ringEnv.messageId,
        senderUuid: ringEnv.senderUuid,
        type: ringEnv.type,
        encryptedPayload: ringEnv.encryptedPayload,
        senderPublicKey: ringEnv.senderPublicKey,
        signature: ringEnv.signature,
        timestamp: ringEnv.timestamp,
      ),
    );

    expect(result, isNull, reason: 'Chamada de UUID bloqueado deve ser descartada antes de tocar');
  });
}
