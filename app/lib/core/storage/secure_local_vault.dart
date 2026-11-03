import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../crypto/identity_crypto_service.dart';

/// Mensagem armazenada no historico local do dispositivo (ja decifrada para exibicao local).
class StoredChatMessage {
  final String messageId;
  final String senderUuid;
  final String receiverUuid;
  final String text;
  final int timestampMillis;
  final bool isOutgoing;

  const StoredChatMessage({
    required this.messageId,
    required this.senderUuid,
    required this.receiverUuid,
    required this.text,
    required this.timestampMillis,
    required this.isOutgoing,
  });

  Map<String, dynamic> toJson() => <String, dynamic>{
        'messageId': messageId,
        'senderUuid': senderUuid,
        'receiverUuid': receiverUuid,
        'text': text,
        'timestampMillis': timestampMillis,
        'isOutgoing': isOutgoing,
      };

  factory StoredChatMessage.fromJson(Map<String, dynamic> json) {
    return StoredChatMessage(
      messageId: json['messageId'] as String,
      senderUuid: json['senderUuid'] as String,
      receiverUuid: json['receiverUuid'] as String,
      text: json['text'] as String,
      timestampMillis: json['timestampMillis'] as int,
      isOutgoing: json['isOutgoing'] as bool,
    );
  }
}

/// Interface de armazenamento chave-valor seguro (Keychain / Keystore ou In-Memory para testes).
abstract class KeyValueSecureStore {
  Future<void> write(String key, String value);
  Future<String?> read(String key);
  Future<void> delete(String key);
}

/// Implementacao real usando `FlutterSecureStorage` (Keychain no iOS/macOS, Keystore no Android)
/// com fallback automatico em memoria caso o ambiente de execucao nao possua Keychain ativo.
class FlutterSecureKeyValueStore implements KeyValueSecureStore {
  final FlutterSecureStorage _storage = const FlutterSecureStorage();
  final Map<String, String> _fallbackMemory = <String, String>{};

  @override
  Future<void> write(String key, String value) async {
    _fallbackMemory[key] = value;
    try {
      await _storage.write(key: key, value: value);
    } catch (_) {
      // Em ambientes de teste sem Keychain nativo, mantem no fallback em memoria
    }
  }

  @override
  Future<String?> read(String key) async {
    try {
      final String? val = await _storage.read(key: key);
      if (val != null) {
        _fallbackMemory[key] = val;
        return val;
      }
    } catch (_) {}
    return _fallbackMemory[key];
  }

  @override
  Future<void> delete(String key) async {
    _fallbackMemory.remove(key);
    try {
      await _storage.delete(key: key);
    } catch (_) {}
  }
}

/// Implementacao deterministica em memoria para testes unitarios e de widget.
class InMemorySecureKeyValueStore implements KeyValueSecureStore {
  final Map<String, String> _data = <String, String>{};

  @override
  Future<void> write(String key, String value) async {
    _data[key] = value;
  }

  @override
  Future<String?> read(String key) async {
    return _data[key];
  }

  @override
  Future<void> delete(String key) async {
    _data.remove(key);
  }
}

/// Cofre Seguro Local do App Flutter.
///
/// Persiste de forma segura:
/// 1. Frase semente de 12 palavras (BIP39) para restauracao automatica da identidade no boot.
/// 2. Lista de contatos verificados (UUIDv7 + chaves publicas Ed25519 e X25519).
/// 3. Blocklist local (UUIDv7 bloqueados silenciosamente).
/// 4. Nos sementes injetados (Bootstrap Social).
/// 5. Historico local de mensagens E2E por contato.
class SecureLocalVault {
  static const String _keyMnemonic = 'ispchat.vault.mnemonic';
  static const String _keyContacts = 'ispchat.vault.contacts_v1';
  static const String _keyBlocklist = 'ispchat.vault.blocklist_v1';
  static const String _keySeeds = 'ispchat.vault.seeds_v1';
  static const String _keyMessagesPrefix = 'ispchat.vault.messages_v1.';

  final KeyValueSecureStore store;
  final IdentityCryptoService cryptoService;

  SecureLocalVault({
    required this.store,
    required this.cryptoService,
  });

  /// Salva a frase semente de 12 palavras no cofre seguro.
  Future<void> saveIdentityMnemonic(String mnemonic) async {
    await store.write(_keyMnemonic, mnemonic.trim().toLowerCase());
  }

  /// Carrega e restaura a identidade do usuario salva no cofre seguro (ou `null` se ainda nao criada).
  Future<UserIdentity?> loadSavedIdentity() async {
    final String? savedMnemonic = await store.read(_keyMnemonic);
    if (savedMnemonic == null || savedMnemonic.trim().isEmpty) {
      return null;
    }
    try {
      return await cryptoService.restoreIdentityFromMnemonic(savedMnemonic);
    } catch (_) {
      return null;
    }
  }

  /// Persiste a lista de contatos verificados.
  Future<void> saveContacts(List<ContactBundle> contacts) async {
    final List<Map<String, dynamic>> serialized = contacts
        .map((ContactBundle c) => <String, dynamic>{
              'userUuid': c.userUuid,
              'ed25519': base64UrlEncode(c.ed25519PublicKeyBytes),
              'x25519': base64UrlEncode(c.x25519PublicKeyBytes),
              if (c.directIpv6Address != null) 'ipv6': c.directIpv6Address,
            })
        .toList();
    await store.write(_keyContacts, jsonEncode(serialized));
  }

  /// Carrega todos os contatos verificados do cofre local.
  Future<List<ContactBundle>> loadContacts() async {
    final String? rawJson = await store.read(_keyContacts);
    if (rawJson == null || rawJson.isEmpty) {
      return <ContactBundle>[];
    }
    try {
      final List<dynamic> list = jsonDecode(rawJson) as List<dynamic>;
      final List<ContactBundle> loaded = <ContactBundle>[];
      for (final dynamic item in list) {
        final Map<String, dynamic> map = item as Map<String, dynamic>;
        final String uuid = map['userUuid'] as String;
        final Uint8List ed25519 = Uint8List.fromList(base64Url.decode(map['ed25519'] as String));
        final Uint8List x25519 = Uint8List.fromList(base64Url.decode(map['x25519'] as String));
        final String? ipv6 = map['ipv6'] as String?;

        final bool bound = await cryptoService.verifyIdentityBinding(uuid, ed25519);
        if (bound) {
          loaded.add(ContactBundle(
            userUuid: uuid,
            ed25519PublicKeyBytes: ed25519,
            x25519PublicKeyBytes: x25519,
            directIpv6Address: ipv6,
          ));
        }
      }
      return loaded;
    } catch (_) {
      return <ContactBundle>[];
    }
  }

  /// Persiste o conjunto de UUIDs bloqueados na Blocklist Local.
  Future<void> saveBlockedUuids(Set<String> blockedUuids) async {
    await store.write(_keyBlocklist, jsonEncode(blockedUuids.toList()));
  }

  /// Carrega os UUIDs bloqueados da Blocklist Local.
  Future<List<String>> loadBlockedUuids() async {
    final String? rawJson = await store.read(_keyBlocklist);
    if (rawJson == null || rawJson.isEmpty) {
      return <String>[];
    }
    try {
      final List<dynamic> list = jsonDecode(rawJson) as List<dynamic>;
      return list.map((dynamic e) => e.toString()).toList();
    } catch (_) {
      return <String>[];
    }
  }

  /// Persiste a lista de nos sementes (Bootstrap Social).
  Future<void> saveSeedNodes(List<String> seeds) async {
    await store.write(_keySeeds, jsonEncode(seeds));
  }

  /// Carrega a lista de nos sementes.
  Future<List<String>> loadSeedNodes() async {
    final String? rawJson = await store.read(_keySeeds);
    if (rawJson == null || rawJson.isEmpty) {
      return <String>[];
    }
    try {
      final List<dynamic> list = jsonDecode(rawJson) as List<dynamic>;
      return list.map((dynamic e) => e.toString()).toList();
    } catch (_) {
      return <String>[];
    }
  }

  /// Adiciona uma mensagem ao historico local de conversa com um contato especifico.
  Future<List<StoredChatMessage>> appendChatMessage(
    String peerUuid,
    StoredChatMessage message,
  ) async {
    final List<StoredChatMessage> current = await loadChatMessages(peerUuid);
    current.add(message);
    final String key = '$_keyMessagesPrefix${peerUuid.trim().toLowerCase()}';
    await store.write(
      key,
      jsonEncode(current.map((StoredChatMessage m) => m.toJson()).toList()),
    );
    return current;
  }

  /// Carrega o historico local de mensagens trocadas com um contato.
  Future<List<StoredChatMessage>> loadChatMessages(String peerUuid) async {
    final String key = '$_keyMessagesPrefix${peerUuid.trim().toLowerCase()}';
    final String? rawJson = await store.read(key);
    if (rawJson == null || rawJson.isEmpty) {
      return <StoredChatMessage>[];
    }
    try {
      final List<dynamic> list = jsonDecode(rawJson) as List<dynamic>;
      return list
          .map((dynamic item) => StoredChatMessage.fromJson(item as Map<String, dynamic>))
          .toList();
    } catch (_) {
      return <StoredChatMessage>[];
    }
  }
}
