import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:app/core/crypto/identity_crypto_service.dart';
import 'package:app/core/storage/secure_local_vault.dart';
import 'package:app/main.dart';

void main() {
  group('SecureLocalVault Unit Tests', () {
    late IdentityCryptoService crypto;
    late InMemorySecureKeyValueStore store;
    late SecureLocalVault vault;

    setUp(() {
      crypto = IdentityCryptoService();
      store = InMemorySecureKeyValueStore();
      vault = SecureLocalVault(store: store, cryptoService: crypto);
    });

    test('Persiste e restaura identidade, contatos, blocklist, seeds e chat E2E', () async {
      final UserIdentity alice = await crypto.generateNewIdentity();
      final UserIdentity bob = await crypto.generateNewIdentity();

      // 1. Identidade
      await vault.saveIdentityMnemonic(alice.mnemonic);
      final UserIdentity? loadedAlice = await vault.loadSavedIdentity();
      expect(loadedAlice, isNotNull);
      expect(loadedAlice!.userUuid, equals(alice.userUuid));
      expect(loadedAlice.ed25519PublicKeyBytes, equals(alice.ed25519PublicKeyBytes));

      // 2. Contatos (incluindo rejeicao de contato com chave adulterada)
      final ContactBundle bobBundle = ContactBundle(
        userUuid: bob.userUuid,
        ed25519PublicKeyBytes: bob.ed25519PublicKeyBytes,
        x25519PublicKeyBytes: bob.x25519PublicKeyBytes,
        directIpv6Address: '2804:14d:1:2::99',
      );
      final ContactBundle spoofedBundle = ContactBundle(
        userUuid: bob.userUuid,
        ed25519PublicKeyBytes: Uint8List(32), // Chave invalida para o UUID de Bob
        x25519PublicKeyBytes: bob.x25519PublicKeyBytes,
      );

      await vault.saveContacts(<ContactBundle>[bobBundle, spoofedBundle]);
      final List<ContactBundle> loadedContacts = await vault.loadContacts();
      expect(loadedContacts.length, equals(1));
      expect(loadedContacts.first.userUuid, equals(bob.userUuid));
      expect(loadedContacts.first.directIpv6Address, equals('2804:14d:1:2::99'));

      // 3. Blocklist e Seeds
      await vault.saveBlockedUuids(<String>{'0195f3a0-0000-7000-8000-000000000099'});
      expect(await vault.loadBlockedUuids(), contains('0195f3a0-0000-7000-8000-000000000099'));

      await vault.saveSeedNodes(<String>['200.160.2.3', '2804:14d:1::10']);
      expect(await vault.loadSeedNodes(), equals(<String>['200.160.2.3', '2804:14d:1::10']));

      // 4. Historico de Mensagens E2E
      final StoredChatMessage msg = StoredChatMessage(
        messageId: crypto.generateTemporalUuidV7(),
        senderUuid: alice.userUuid,
        receiverUuid: bob.userUuid,
        text: 'Mensagem persistida no cofre local',
        timestampMillis: 1743465600000,
        isOutgoing: true,
      );
      await vault.appendChatMessage(bob.userUuid, msg);

      final List<StoredChatMessage> chatHistory = await vault.loadChatMessages(bob.userUuid);
      expect(chatHistory.length, equals(1));
      expect(chatHistory.first.text, equals('Mensagem persistida no cofre local'));
      expect(chatHistory.first.messageId, equals(msg.messageId));
    });
  });

  group('QR Dialog, Vault Hydration & ContactChatScreen Widget Tests', () {
    testWidgets(
      'Hidrata estado do cofre, exibe QR Code Doomsday e conversa/liga na ContactChatScreen sem overflow',
      (WidgetTester tester) async {
        final IdentityCryptoService crypto = IdentityCryptoService();
        final UserIdentity alice = await crypto.generateNewIdentity();
        final UserIdentity bob = await crypto.generateNewIdentity();

        final InMemorySecureKeyValueStore memoryStore = InMemorySecureKeyValueStore();
        final SecureLocalVault seedVault = SecureLocalVault(
          store: memoryStore,
          cryptoService: crypto,
        );

        // Pre-popula o cofre com a identidade da Alice e o contato do Bob (com IPv6 Doomsday)
        await seedVault.saveIdentityMnemonic(alice.mnemonic);
        await seedVault.saveContacts(<ContactBundle>[
          ContactBundle(
            userUuid: bob.userUuid,
            ed25519PublicKeyBytes: bob.ed25519PublicKeyBytes,
            x25519PublicKeyBytes: bob.x25519PublicKeyBytes,
            directIpv6Address: '2804:14d:1:2::77',
          ),
        ]);

        await tester.pumpWidget(IspChatApp(customSecureStore: memoryStore));
        await tester.pumpAndSettle();

        // Verifica que a identidade da Alice e o contato do Bob foram hidratados automaticamente
        expect(find.byKey(const Key('txt_user_uuid')), findsOneWidget);
        expect(find.text(alice.userUuid), findsOneWidget);
        expect(find.byKey(Key('contact_tile_${bob.userUuid}')), findsOneWidget);

        // Abre o Dialogo de QR Code e ativa o Modo Doomsday IPv6
        final Finder qrBtn = find.byKey(const Key('btn_show_qr_code'));
        await tester.ensureVisible(qrBtn);
        await tester.tap(qrBtn);
        await tester.pumpAndSettle();

        expect(find.text('QR Code de Identidade & Modo Doomsday'), findsOneWidget);
        await tester.tap(find.byKey(const Key('switch_doomsday_ipv6')));
        await tester.pumpAndSettle();

        await tester.enterText(
          find.byKey(const Key('input_doomsday_ipv6')),
          '2804:14d:99::1',
        );
        await tester.pumpAndSettle();
        expect(find.textContaining('2804%3A14d%3A99%3A%3A1'), findsOneWidget);

        // Fecha o dialogo
        await tester.tap(find.byIcon(Icons.close));
        await tester.pumpAndSettle();

        // Toca no contato do Bob para abrir a ContactChatScreen
        final Finder bobTile = find.byKey(Key('contact_tile_${bob.userUuid}'));
        await tester.ensureVisible(bobTile);
        await tester.tap(bobTile);
        await tester.pumpAndSettle();

        expect(find.text('Chat E2E Verificado'), findsOneWidget);
        expect(
          find.textContaining('Rota Doomsday IPv6 Direta disponivel: 2804:14d:1:2::77'),
          findsOneWidget,
        );

        // Envia uma mensagem E2E
        await tester.enterText(
          find.byKey(const Key('input_chat_message')),
          'Oi Bob, testando chat E2E!',
        );
        await tester.tap(find.byKey(const Key('btn_send_chat_message')));
        await tester.pumpAndSettle();

        expect(find.text('Oi Bob, testando chat E2E!'), findsOneWidget);
        expect(find.textContaining('Pacote E2E assinado'), findsOneWidget);

        // Inicia uma chamada de voz WebRTC E2E e depois encerra
        await tester.tap(find.byKey(const Key('btn_start_audio_call')));
        await tester.pumpAndSettle();

        expect(find.byKey(const Key('txt_active_call_status')), findsOneWidget);
        expect(find.textContaining('ringingOutgoing'), findsOneWidget);

        await tester.tap(find.byKey(const Key('btn_hangup_call')));
        await tester.pumpAndSettle();

        expect(find.textContaining('Chamada encerrada'), findsOneWidget);
      },
    );
  });
}
