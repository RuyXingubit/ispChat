import 'package:app/core/crypto/identity_crypto_service.dart';
import 'package:app/core/discovery/dns_srv_resolver.dart';
import 'package:app/core/discovery/node_discovery_service.dart';
import 'package:app/core/grpc/generated/ispchat.pbgrpc.dart';
import 'package:app/core/grpc/ispchat_node_client.dart';
import 'package:app/core/storage/secure_local_vault.dart';
import 'package:app/main.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

class _InProcessUiNodeClient extends IspChatNodeClient {
  bool _connected = false;
  String? _address;
  final List<ClientEnvelope> sentEnvelopes = <ClientEnvelope>[];

  @override
  bool get isConnected => _connected;

  @override
  String? get connectedNodeAddress => _address;

  @override
  Future<void> connectToAddress(
    String nodeAddress, {
    bool useTls = true,
    dynamic tlsCredentials,
    String? principalAddress,
    bool routeGovernanceThroughEdgeChannel = false,
  }) async {
    _connected = true;
    _address = nodeAddress.trim();
  }

  @override
  Future<RegisterSessionResponse> registerSession(
    RegisterSessionRequest request,
  ) async {
    return RegisterSessionResponse()..success = true;
  }

  @override
  Future<FindUserSessionsResponse> findUserSessions(String targetUuid) async {
    return FindUserSessionsResponse();
  }

  @override
  Future<void> openMessageStream({
    ClientEnvelope? initialHandshake,
    Future<void> Function()? onReconnected,
  }) async {}

  @override
  Future<void> sendEnvelope(ClientEnvelope envelope) async {
    sentEnvelopes.add(envelope);
  }

  @override
  Future<void> disconnect() async {
    _connected = false;
    _address = null;
  }
}

class _AlwaysFastProbe implements LatencyProbe {
  @override
  Future<int?> measureLatencyMs(
    String nodeAddress, {
    Duration timeout = const Duration(seconds: 2),
  }) async {
    return 7;
  }
}

void main() {
  test('SecureLocalVault persists V2 credentials, devices, and contacts',
      () async {
    const crypto = IdentityCryptoService();
    final alice = await crypto.generateNewIdentity();
    final bob = await crypto.generateNewIdentity();
    final bobNew = await crypto.generateNewIdentity();
    final store = InMemorySecureKeyValueStore();
    final vault = SecureLocalVault(store: store);

    await vault.saveMnemonic(alice.mnemonic);
    expect((await vault.restoreSavedIdentity(crypto))?.userUuid, alice.userUuid);

    await vault.saveContact(
      ContactBundle(
        uuid: bob.userUuid,
        ed25519PublicKey: bob.ed25519PublicKeyBytes,
        x25519PublicKey: bob.x25519PublicKeyBytes,
        displayName: 'Bob',
        isVerified: true,
        publicCnpj: '12ABC34501DE35',
      ),
    );
    await vault.appendChatMessage(
      StoredChatMessage(
        messageId: IdentityCryptoService.generateTemporalUuidV7(),
        peerUuid: bob.userUuid,
        senderUuid: bob.userUuid,
        text: 'Antes do rebind',
        isOutgoing: false,
        timestampMs: 1700000000000,
      ),
    );

    await vault.migrateReplacedContact(
      oldUserUuid: bob.userUuid,
      updatedContact: ContactBundle(
        uuid: bobNew.userUuid,
        ed25519PublicKey: bobNew.ed25519PublicKeyBytes,
        x25519PublicKey: bobNew.x25519PublicKeyBytes,
        displayName: 'Bob',
        isVerified: true,
      ),
    );
    expect(await vault.loadChatMessages(bob.userUuid), isEmpty);
    expect((await vault.loadChatMessages(bobNew.userUuid)).first.text, 'Antes do rebind');

    await vault.saveBlockedUuids([bob.userUuid]);
    expect(await vault.loadBlockedUuids(), [bob.userUuid]);
    await vault.saveBootstrapSeeds(['127.0.0.1:8443']);
    expect(await vault.loadBootstrapSeeds(), ['127.0.0.1:8443']);
  });

  testWidgets(
      'Main UI generates identity, adds contact, connects to local node, and opens chat',
      (tester) async {
    const crypto = IdentityCryptoService();
    final peer = await crypto.generateNewIdentity();
    final peerUri = await crypto.buildSignedContactUri(
      identity: peer,
      displayName: 'Peer Contato',
    );

    final fakeClient = _InProcessUiNodeClient();
    final store = InMemorySecureKeyValueStore();
    tester.view.physicalSize = const Size(1024, 768);
    tester.view.devicePixelRatio = 1.0;

    try {
      await tester.pumpWidget(
        IspChatApp(
          customSecureStore: store,
          customNodeClient: fakeClient,
          customLatencyProbe: _AlwaysFastProbe(),
          customDnsSrvResolver: DnsSrvResolver(
            endpoints: const [],
          ),
          testNodePortOverride: 8443,
          useInsecureLocalTestTransport: true,
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('btn_generate_identity')));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('txt_user_uuid')), findsOneWidget);

      await tester.tap(find.byKey(const Key('btn_elect_and_connect_node')));
      await tester.pumpAndSettle();
      expect(
        tester.widget<Text>(find.byKey(const Key('txt_connected_node_host'))).data,
        '127.0.0.1:8443',
      );

      await tester.enterText(
        find.byKey(const Key('input_contact_uri')),
        peerUri,
      );
      await tester.tap(find.byKey(const Key('btn_add_contact')));
      await tester.pumpAndSettle();

      final tileFinder = find.byKey(Key('contact_tile_${peer.userUuid}'));
      expect(tileFinder, findsOneWidget);
      await tester.tap(tileFinder);
      await tester.pumpAndSettle();

      await tester.enterText(
        find.byKey(const Key('input_chat_message')),
        'Mensagem na conversa',
      );
      await tester.tap(find.byKey(const Key('btn_send_message')));
      await tester.pumpAndSettle();

      expect(find.text('Mensagem na conversa'), findsOneWidget);
      expect(fakeClient.sentEnvelopes.length, 1);
      expect(tester.takeException(), isNull);
    } finally {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
      await fakeClient.dispose();
    }
  });
}
