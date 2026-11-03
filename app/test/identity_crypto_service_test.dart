import 'dart:convert';
import 'dart:typed_data';

import 'package:app/core/crypto/identity_crypto_service.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  late IdentityCryptoService cryptoService;

  setUp(() {
    cryptoService = IdentityCryptoService();
  });

  test('Deve gerar 12 palavras BIP39 e restaurar deterministicamente o exato mesmo UUIDv7 e chaves', () async {
    final UserIdentity created = await cryptoService.generateNewIdentity();
    expect(created.mnemonic.split(' ').length, equals(12));
    expect(created.ed25519PublicKeyBytes.length, equals(32));
    expect(created.x25519PublicKeyBytes.length, equals(32));

    // Restaura em um "segundo dispositivo" usando apenas as 12 palavras
    final UserIdentity restored = await cryptoService.restoreIdentityFromMnemonic(created.mnemonic);
    expect(restored.userUuid, equals(created.userUuid));
    expect(restored.ed25519PublicKeyBytes, equals(created.ed25519PublicKeyBytes));
    expect(restored.x25519PublicKeyBytes, equals(created.x25519PublicKeyBytes));

    // Verifica que o UUIDv7 respeita a RFC 9562 (versao 7 e variante 8/9/a/b)
    final RegExp uuidV7Regex = RegExp(
      r'^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$',
    );
    expect(uuidV7Regex.hasMatch(created.userUuid), isTrue);
  });

  test('Deve criptografar e descriptografar mensagens E2E (X25519 + ChaCha20-Poly1305) entre Alice e Bob', () async {
    final UserIdentity alice = await cryptoService.generateNewIdentity();
    final UserIdentity bob = await cryptoService.generateNewIdentity();

    final Uint8List secretMessage = Uint8List.fromList(
      utf8.encode('Comunicacao P2P criptografada ponta a ponta sem servidor central!'),
    );

    final Uint8List encryptedBlob = await cryptoService.encryptPayloadE2E(
      senderX25519KeyPair: alice.x25519KeyPair,
      recipientX25519PublicKeyBytes: bob.x25519PublicKeyBytes,
      plaintext: secretMessage,
    );

    // Bob descriptografa usando sua chave privada X25519 e a chave publica X25519 de Alice
    final Uint8List decryptedBytes = await cryptoService.decryptPayloadE2E(
      recipientX25519KeyPair: bob.x25519KeyPair,
      senderX25519PublicKeyBytes: alice.x25519PublicKeyBytes,
      encryptedBlob: encryptedBlob,
    );

    expect(utf8.decode(decryptedBytes), equals('Comunicacao P2P criptografada ponta a ponta sem servidor central!'));

    // Se um atacante adulterar 1 byte do ciphertext, a autenticacao Poly1305 deve falhar
    final Uint8List tamperedBlob = Uint8List.fromList(encryptedBlob);
    tamperedBlob[tamperedBlob.length - 1] ^= 0x01;

    expect(
      () => cryptoService.decryptPayloadE2E(
        recipientX25519KeyPair: bob.x25519KeyPair,
        senderX25519PublicKeyBytes: alice.x25519PublicKeyBytes,
        encryptedBlob: tamperedBlob,
      ),
      throwsA(anything),
    );
  });

  test('Deve assinar envelope canonico Ed25519 e bloquear tentativa de Spoofing', () async {
    final UserIdentity alice = await cryptoService.generateNewIdentity();
    final UserIdentity attacker = await cryptoService.generateNewIdentity();

    final String messageId = cryptoService.generateTemporalUuidV7();
    final String receiverUuid = cryptoService.generateTemporalUuidV7();
    final int timestamp = 1762128000000;
    final Uint8List payload = Uint8List.fromList(utf8.encode('ciphertext'));

    final Uint8List signature = await cryptoService.signMessageEnvelope(
      identity: alice,
      messageId: messageId,
      receiverUuid: receiverUuid,
      messageTypeNumber: 1,
      timestampMillis: timestamp,
      encryptedPayload: payload,
    );

    final bool valid = await cryptoService.verifyReceivedEnvelope(
      messageId: messageId,
      senderUuid: alice.userUuid,
      receiverUuid: receiverUuid,
      messageTypeNumber: 1,
      timestampMillis: timestamp,
      encryptedPayload: payload,
      senderEd25519PublicKey: alice.ed25519PublicKeyBytes,
      signatureBytes: signature,
    );
    expect(valid, isTrue);

    // Tentativa de Spoofing: Atacante assina com a propria chave tentando usar o UUID de Alice
    final bool spoofed = await cryptoService.verifyReceivedEnvelope(
      messageId: messageId,
      senderUuid: alice.userUuid,
      receiverUuid: receiverUuid,
      messageTypeNumber: 1,
      timestampMillis: timestamp,
      encryptedPayload: payload,
      senderEd25519PublicKey: attacker.ed25519PublicKeyBytes,
      signatureBytes: signature,
    );
    expect(spoofed, isFalse);
  });

  test('Deve gerar e validar Link Magico de Contato (incluindo IPv6 do Modo Doomsday) e rejeitar link forjado', () async {
    final UserIdentity alice = await cryptoService.generateNewIdentity();
    final UserIdentity attacker = await cryptoService.generateNewIdentity();

    final String uri = cryptoService.buildShareableContactUri(
      alice,
      directIpv6Address: '2804:14d:1:2::99',
    );

    final ContactBundle bundle = await cryptoService.parseAndVerifyContactUri(uri);
    expect(bundle.userUuid, equals(alice.userUuid));
    expect(bundle.ed25519PublicKeyBytes, equals(alice.ed25519PublicKeyBytes));
    expect(bundle.x25519PublicKeyBytes, equals(alice.x25519PublicKeyBytes));
    expect(bundle.directIpv6Address, equals('2804:14d:1:2::99'));

    // Link forjado onde o atacante coloca o UUID de Alice mas a chave publica do atacante
    final String forgedUri = uri.replaceFirst(alice.userUuid, attacker.userUuid);
    expect(
      () => cryptoService.parseAndVerifyContactUri(forgedUri),
      throwsFormatException,
    );
  });
}
