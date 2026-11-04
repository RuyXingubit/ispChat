import 'dart:async';

import 'package:app/core/crypto/identity_crypto_service.dart';
import 'package:app/core/grpc/ispchat_node_client.dart';
import 'package:app/core/grpc/ispchat_pb.dart';
import 'package:app/core/messaging/e2e_session_manager.dart';
import 'package:app/core/security/blocklist_service.dart';
import 'package:app/core/storage/secure_local_vault.dart';
import 'package:app/main.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:grpc/grpc.dart' as grpc;

/// Implementacao fake de borda do No ISP (`DhtClientService`) para testes integrados gRPC reais.
class _FakeDhtClientService extends grpc.Service {
  @override
  String get $name => 'br.dev.ispchat.grpc.DhtClientService';

  final Map<String, RegisterSessionRequestProto> registeredUsers =
      <String, RegisterSessionRequestProto>{};

  _FakeDhtClientService() {
    $addMethod(grpc.ServiceMethod<RegisterSessionRequestProto, RegisterSessionResponseProto>(
      'RegisterSession',
      registerSession,
      false,
      false,
      (List<int> value) => RegisterSessionRequestProto.fromBuffer(value),
      (RegisterSessionResponseProto value) => value.writeToBuffer(),
    ));
    $addMethod(grpc.ServiceMethod<FindUserSessionsRequestProto, FindUserSessionsResponseProto>(
      'FindUserSessions',
      findUserSessions,
      false,
      false,
      (List<int> value) => FindUserSessionsRequestProto.fromBuffer(value),
      (FindUserSessionsResponseProto value) => value.writeToBuffer(),
    ));
  }

  Future<RegisterSessionResponseProto> registerSession(
    grpc.ServiceCall call,
    Future<RegisterSessionRequestProto> requestFuture,
  ) async {
    final RegisterSessionRequestProto request = await requestFuture;
    registeredUsers[request.userUuid] = request;
    return const RegisterSessionResponseProto(
      success: true,
      errorMessage: '',
    );
  }

  Future<FindUserSessionsResponseProto> findUserSessions(
    grpc.ServiceCall call,
    Future<FindUserSessionsRequestProto> requestFuture,
  ) async {
    final FindUserSessionsRequestProto request = await requestFuture;
    final RegisterSessionRequestProto? found = registeredUsers[request.targetUuid];
    if (found == null) {
      return const FindUserSessionsResponseProto();
    }
    return FindUserSessionsResponseProto(
      nodeAddresses: <String>[found.nodeAddress],
      sessions: <UserSessionInfoProto>[
        UserSessionInfoProto(
          nodeAddress: found.nodeAddress,
          ed25519PublicKey: found.publicKey,
          x25519PublicKey: found.x25519PublicKey,
          timestamp: found.timestamp,
        ),
      ],
    );
  }
}

/// Implementacao fake da stream bidirecional (`ClientStreamService`) para testes integrados gRPC reais.
class _FakeClientStreamService extends grpc.Service {
  @override
  String get $name => 'br.dev.ispchat.grpc.ClientStreamService';

  final List<ClientEnvelopeProto> receivedEnvelopes = <ClientEnvelopeProto>[];
  final Completer<ClientEnvelopeProto> firstHandshakeCompleter =
      Completer<ClientEnvelopeProto>();
  final Completer<ClientEnvelopeProto> firstChatMessageCompleter =
      Completer<ClientEnvelopeProto>();
  StreamController<ServerEnvelopeProto>? activeServerOut;

  _FakeClientStreamService() {
    $addMethod(grpc.ServiceMethod<ClientEnvelopeProto, ServerEnvelopeProto>(
      'Connect',
      connect,
      true,
      true,
      (List<int> value) => ClientEnvelopeProto.fromBuffer(value),
      (ServerEnvelopeProto value) => value.writeToBuffer(),
    ));
  }

  Stream<ServerEnvelopeProto> connect(
    grpc.ServiceCall call,
    Stream<ClientEnvelopeProto> requestStream,
  ) {
    final StreamController<ServerEnvelopeProto> serverOut =
        StreamController<ServerEnvelopeProto>();
    activeServerOut = serverOut;

    requestStream.listen(
      (ClientEnvelopeProto env) {
        receivedEnvelopes.add(env);
        if (env.type == ProtoMessageType.authHandshake &&
            !firstHandshakeCompleter.isCompleted) {
          firstHandshakeCompleter.complete(env);
        } else if (env.type == ProtoMessageType.text &&
            !firstChatMessageCompleter.isCompleted) {
          firstChatMessageCompleter.complete(env);
        }
      },
      onError: (_) {
        if (!serverOut.isClosed) {
          serverOut.close();
        }
      },
      onDone: () {
        if (!serverOut.isClosed) {
          serverOut.close();
        }
      },
    );

    return serverOut.stream;
  }
}

/// Subclasse deterministica de `IspChatNodeClient` para testes de Widget (`FakeAsync`).
class _LoopbackWidgetNodeClient extends IspChatNodeClient {
  bool _connected = false;
  String? _host;
  final List<ClientEnvelopeProto> sentEnvelopes = <ClientEnvelopeProto>[];
  final StreamController<DecryptedIncomingEvent> _testIncoming =
      StreamController<DecryptedIncomingEvent>.broadcast();

  _LoopbackWidgetNodeClient({required super.sessionManager});

  @override
  bool get isConnected => _connected;

  @override
  String? get connectedHost => _host;

  @override
  Stream<DecryptedIncomingEvent> get incomingEvents => _testIncoming.stream;

  @override
  Future<RegisterSessionResponseProto> connectAndAuthenticate({
    required String nodeHost,
    required UserIdentity identity,
    int port = 443,
    List<int>? trustedCaCertificateBytes,
    String? authorityOverride,
    bool useInsecureLocalTestTransport = false,
  }) async {
    _connected = true;
    _host = nodeHost;
    return const RegisterSessionResponseProto(success: true, errorMessage: '');
  }

  @override
  bool sendSignedEnvelope(ClientEnvelopeProto envelope) {
    if (!_connected) return false;
    sentEnvelopes.add(envelope);
    return true;
  }

  void pushIncomingEvent(DecryptedIncomingEvent event) {
    if (!_testIncoming.isClosed) {
      _testIncoming.add(event);
    }
  }

  @override
  Future<void> dispose() async {
    _connected = false;
    await _testIncoming.close();
    await super.dispose();
  }
}

void main() {
  group('Live gRPC Node Client & UI End-to-End Integration Tests', () {
    test(
      'IspChatNodeClient registra na DHT, envia AUTH_HANDSHAKE, consulta FindUser e troca envelopes E2E via gRPC real',
      () async {
        final _FakeDhtClientService dhtService = _FakeDhtClientService();
        final _FakeClientStreamService streamService = _FakeClientStreamService();
        final grpc.Server server = grpc.Server.create(services: <grpc.Service>[
          dhtService,
          streamService,
        ]);
        await server.serve(address: '127.0.0.1', port: 0);
        final int serverPort = server.port!;

        try {
          final IdentityCryptoService crypto = IdentityCryptoService();
          final UserIdentity alice = await crypto.generateNewIdentity();
          final UserIdentity bob = await crypto.generateNewIdentity();

          final E2eSessionManager aliceSession = E2eSessionManager(
            cryptoService: crypto,
            blocklistService: BlocklistService(),
          );
          final E2eSessionManager bobSession = E2eSessionManager(
            cryptoService: crypto,
            blocklistService: BlocklistService(),
          );

          final ContactBundle bobContact = ContactBundle(
            userUuid: bob.userUuid,
            ed25519PublicKeyBytes: bob.ed25519PublicKeyBytes,
            x25519PublicKeyBytes: bob.x25519PublicKeyBytes,
          );
          final ContactBundle aliceContact = ContactBundle(
            userUuid: alice.userUuid,
            ed25519PublicKeyBytes: alice.ed25519PublicKeyBytes,
            x25519PublicKeyBytes: alice.x25519PublicKeyBytes,
          );

          aliceSession.registerVerifiedContact(bobContact);
          bobSession.registerVerifiedContact(aliceContact);

          final IspChatNodeClient aliceClient = IspChatNodeClient(
            sessionManager: aliceSession,
          );

          // 1. Alice conecta ao servidor gRPC real e envia AUTH_HANDSHAKE automaticamente
          final RegisterSessionResponseProto regResp =
              await aliceClient.connectAndAuthenticate(
            nodeHost: '127.0.0.1',
            identity: alice,
            port: serverPort,
            useInsecureLocalTestTransport: true,
          );
          expect(regResp.success, isTrue);
          expect(aliceClient.isConnected, isTrue);

          final ClientEnvelopeProto handshake = await streamService
              .firstHandshakeCompleter.future
              .timeout(const Duration(seconds: 3));
          expect(handshake.type, equals(ProtoMessageType.authHandshake));
          expect(handshake.senderUuid, equals(alice.userUuid));
          expect(handshake.receiverUuid, equals(alice.userUuid));

          // 2. Consulta FindUserSessions na DHT para a sessao recem-registrada da Alice
          final FindUserSessionsResponseProto foundAlice =
              await aliceClient.findUserSessionsInDht(alice.userUuid);
          expect(foundAlice.found, isTrue);
          expect(
            foundAlice.sessions.single.ed25519PublicKey,
            equals(alice.ed25519PublicKeyBytes),
          );

          // 3. Alice envia mensagem E2E para Bob pela stream gRPC ativa
          await aliceClient.sendEncryptedEvent(
            senderIdentity: alice,
            recipientContact: bobContact,
            type: ProtoMessageType.text,
            cleartextOrJsonSignal: 'Mensagem ao vivo no canal gRPC!',
          );

          final ClientEnvelopeProto sentChat = await streamService
              .firstChatMessageCompleter.future
              .timeout(const Duration(seconds: 3));
          expect(sentChat.senderUuid, equals(alice.userUuid));
          expect(sentChat.receiverUuid, equals(bob.userUuid));

          // 4. Servidor gRPC injeta resposta E2E assinada por Bob de volta para a stream da Alice
          final Future<DecryptedIncomingEvent> incomingFuture =
              aliceClient.incomingEvents.first;
          final ClientEnvelopeProto bobOutgoing =
              await bobSession.buildOutgoingEncryptedEnvelope(
            senderIdentity: bob,
            recipientContact: aliceContact,
            type: ProtoMessageType.text,
            cleartextOrJsonSignal: 'Resposta do Bob via stream gRPC!',
          );

          streamService.activeServerOut!.add(ServerEnvelopeProto(
            messageId: bobOutgoing.messageId,
            senderUuid: bobOutgoing.senderUuid,
            type: bobOutgoing.type,
            encryptedPayload: bobOutgoing.encryptedPayload,
            senderPublicKey: bob.ed25519PublicKeyBytes,
            signature: bobOutgoing.signature,
            timestamp: bobOutgoing.timestamp,
          ));

          final DecryptedIncomingEvent decrypted =
              await incomingFuture.timeout(const Duration(seconds: 3));
          expect(decrypted.senderUuid, equals(bob.userUuid));
          expect(
            decrypted.cleartextOrJsonSignal,
            equals('Resposta do Bob via stream gRPC!'),
          );

          await streamService.activeServerOut?.close();
          await aliceClient.dispose();
        } finally {
          await server.shutdown();
        }
      },
    );

    testWidgets(
      'Fluxo completo na UI: elege e conecta ao No gRPC, envia mensagem E2E e recebe mensagem/sinal em tempo real',
      (WidgetTester tester) async {
        final IdentityCryptoService crypto = IdentityCryptoService();
        final UserIdentity alice = await crypto.generateNewIdentity();
        final UserIdentity bob = await crypto.generateNewIdentity();

        final InMemorySecureKeyValueStore memoryStore = InMemorySecureKeyValueStore();
        final SecureLocalVault seedVault = SecureLocalVault(
          store: memoryStore,
          cryptoService: crypto,
        );

        await seedVault.saveIdentityMnemonic(alice.mnemonic);
        await seedVault.saveContacts(<ContactBundle>[
          ContactBundle(
            userUuid: bob.userUuid,
            ed25519PublicKeyBytes: bob.ed25519PublicKeyBytes,
            x25519PublicKeyBytes: bob.x25519PublicKeyBytes,
          ),
        ]);
        await seedVault.saveSeedNodes(<String>['200.160.2.3']);

        final _LoopbackWidgetNodeClient loopbackNodeClient = _LoopbackWidgetNodeClient(
          sessionManager: E2eSessionManager(
            cryptoService: crypto,
            blocklistService: BlocklistService(),
          ),
        );

        await tester.pumpWidget(
          IspChatApp(
            customSecureStore: memoryStore,
            customLatencyProbe: (String host) async => 3,
            customNodeClient: loopbackNodeClient,
          ),
        );
        await tester.pumpAndSettle();

        // Clica no botao de eleger e conectar ao melhor No
        final Finder connectBtn = find.byKey(const Key('btn_elect_and_connect_node'));
        await tester.ensureVisible(connectBtn);
        await tester.tap(connectBtn);
        await tester.pumpAndSettle();

        expect(find.byKey(const Key('txt_connected_node_host')), findsOneWidget);
        expect(find.textContaining('Conectado e autenticado no No 200.160.2.3'), findsOneWidget);

        // Abre a ContactChatScreen do Bob
        final Finder bobTile = find.byKey(Key('contact_tile_${bob.userUuid}'));
        await tester.ensureVisible(bobTile);
        await tester.tap(bobTile);
        await tester.pumpAndSettle();

        // Envia mensagem pela UI conectada ao gRPC
        await tester.enterText(
          find.byKey(const Key('input_chat_message')),
          'Oi Bob via gRPC ao vivo!',
        );
        await tester.tap(find.byKey(const Key('btn_send_chat_message')));
        await tester.pumpAndSettle();

        expect(
          find.textContaining('Pacote E2E assinado e transmitido via gRPC'),
          findsOneWidget,
        );
        expect(loopbackNodeClient.sentEnvelopes.length, equals(1));

        // Simula chegada de mensagem E2E do Bob em tempo real na stream
        loopbackNodeClient.pushIncomingEvent(DecryptedIncomingEvent(
          messageId: crypto.generateTemporalUuidV7(),
          senderUuid: bob.userUuid,
          type: ProtoMessageType.text,
          cleartextOrJsonSignal: 'Mensagem do Bob recebida ao vivo!',
          timestampMillis: DateTime.now().millisecondsSinceEpoch,
        ));
        await tester.pumpAndSettle();

        expect(find.text('Mensagem do Bob recebida ao vivo!'), findsOneWidget);
        expect(
          find.textContaining('Mensagem E2E recebida em tempo real via stream gRPC'),
          findsOneWidget,
        );
      },
    );
  });
}
