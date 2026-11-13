import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:app/core/crypto/identity_crypto_service.dart';
import 'package:app/core/grpc/generated/ispchat.pb.dart';

/// Abstraction over platform-secure key-value storage (Keychain / Keystore).
abstract class KeyValueSecureStore {
  Future<void> write({required String key, required String value});
  Future<String?> read({required String key});
  Future<void> delete({required String key});
}

/// Production [KeyValueSecureStore] backed by `flutter_secure_storage`.
class FlutterSecureKeyValueStore implements KeyValueSecureStore {
  final FlutterSecureStorage _storage;

  const FlutterSecureKeyValueStore({FlutterSecureStorage? storage})
      : _storage = storage ?? const FlutterSecureStorage();

  @override
  Future<void> write({required String key, required String value}) {
    return _storage.write(key: key, value: value);
  }

  @override
  Future<String?> read({required String key}) {
    return _storage.read(key: key);
  }

  @override
  Future<void> delete({required String key}) {
    return _storage.delete(key: key);
  }
}

/// Deterministic in-memory [KeyValueSecureStore] for unit and widget tests.
class InMemorySecureKeyValueStore implements KeyValueSecureStore {
  final Map<String, String> _data = <String, String>{};

  @override
  Future<void> write({required String key, required String value}) async {
    _data[key] = value;
  }

  @override
  Future<String?> read({required String key}) async {
    return _data[key];
  }

  @override
  Future<void> delete({required String key}) async {
    _data.remove(key);
  }
}

/// Locally persisted message inside a peer conversation thread.
class StoredChatMessage {
  final String messageId;
  final String peerUuid;
  final String senderUuid;
  final String text;
  final bool isOutgoing;
  final int timestampMs;
  final String deliveryState;

  const StoredChatMessage({
    required this.messageId,
    required this.peerUuid,
    required this.senderUuid,
    required this.text,
    required this.isOutgoing,
    required this.timestampMs,
    this.deliveryState = 'sent',
  });

  StoredChatMessage copyWith({
    String? peerUuid,
    String? deliveryState,
  }) {
    return StoredChatMessage(
      messageId: messageId,
      peerUuid: peerUuid ?? this.peerUuid,
      senderUuid: senderUuid,
      text: text,
      isOutgoing: isOutgoing,
      timestampMs: timestampMs,
      deliveryState: deliveryState ?? this.deliveryState,
    );
  }

  Map<String, dynamic> toJson() {
    return <String, dynamic>{
      'messageId': messageId,
      'peerUuid': peerUuid,
      'senderUuid': senderUuid,
      'text': text,
      'isOutgoing': isOutgoing,
      'timestampMs': timestampMs,
      'deliveryState': deliveryState,
    };
  }

  factory StoredChatMessage.fromJson(Map<String, dynamic> json) {
    return StoredChatMessage(
      messageId: json['messageId'] as String,
      peerUuid: json['peerUuid'] as String,
      senderUuid: json['senderUuid'] as String,
      text: json['text'] as String,
      isOutgoing: json['isOutgoing'] as bool,
      timestampMs: json['timestampMs'] as int,
      deliveryState: (json['deliveryState'] as String?) ?? 'sent',
    );
  }
}

/// Persisted cryptographic material for the current device (mobile or desktop).
class StoredLocalDeviceRecord {
  final String deviceId;
  final DeviceRole role;
  final Uint8List ed25519Seed;
  final Uint8List x25519Seed;
  final DeviceDelegationCert? delegationCert;
  final String? pairedUserUuid;
  final Uint8List? rootEd25519Pub;
  final Uint8List? rootX25519Pub;

  const StoredLocalDeviceRecord({
    required this.deviceId,
    required this.role,
    required this.ed25519Seed,
    required this.x25519Seed,
    this.delegationCert,
    this.pairedUserUuid,
    this.rootEd25519Pub,
    this.rootX25519Pub,
  });

  Map<String, dynamic> toJson() {
    return <String, dynamic>{
      'deviceId': deviceId,
      'role': role.value,
      'edSeed': base64Encode(ed25519Seed),
      'xSeed': base64Encode(x25519Seed),
      if (delegationCert != null)
        'cert': base64Encode(delegationCert!.writeToBuffer()),
      if (pairedUserUuid != null) 'pairedUserUuid': pairedUserUuid,
      if (rootEd25519Pub != null) 'rootEd': base64Encode(rootEd25519Pub!),
      if (rootX25519Pub != null) 'rootX': base64Encode(rootX25519Pub!),
    };
  }

  factory StoredLocalDeviceRecord.fromJson(Map<String, dynamic> json) {
    final certB64 = json['cert'] as String?;
    final rootEdB64 = json['rootEd'] as String?;
    final rootXB64 = json['rootX'] as String?;
    return StoredLocalDeviceRecord(
      deviceId: json['deviceId'] as String,
      role: DeviceRole.valueOf(json['role'] as int) ?? DeviceRole.DESKTOP,
      ed25519Seed: Uint8List.fromList(base64Decode(json['edSeed'] as String)),
      x25519Seed: Uint8List.fromList(base64Decode(json['xSeed'] as String)),
      delegationCert: certB64 != null
          ? DeviceDelegationCert.fromBuffer(base64Decode(certB64))
          : null,
      pairedUserUuid: json['pairedUserUuid'] as String?,
      rootEd25519Pub: rootEdB64 != null ? base64Decode(rootEdB64) : null,
      rootX25519Pub: rootXB64 != null ? base64Decode(rootXB64) : null,
    );
  }
}

/// Registry entry on the Root device for a paired secondary device.
class PairedDeviceRecord {
  final String deviceId;
  final String label;
  final DeviceRole role;
  final Uint8List deviceEd25519Pub;
  final Uint8List deviceX25519Pub;
  final int issuedAtMs;
  final int expiresAtMs;
  final bool revoked;

  const PairedDeviceRecord({
    required this.deviceId,
    required this.label,
    required this.role,
    required this.deviceEd25519Pub,
    required this.deviceX25519Pub,
    required this.issuedAtMs,
    required this.expiresAtMs,
    this.revoked = false,
  });

  PairedDeviceRecord copyWith({bool? revoked, String? label}) {
    return PairedDeviceRecord(
      deviceId: deviceId,
      label: label ?? this.label,
      role: role,
      deviceEd25519Pub: deviceEd25519Pub,
      deviceX25519Pub: deviceX25519Pub,
      issuedAtMs: issuedAtMs,
      expiresAtMs: expiresAtMs,
      revoked: revoked ?? this.revoked,
    );
  }

  Map<String, dynamic> toJson() {
    return <String, dynamic>{
      'deviceId': deviceId,
      'label': label,
      'role': role.value,
      'ed': base64Encode(deviceEd25519Pub),
      'x': base64Encode(deviceX25519Pub),
      'issuedAtMs': issuedAtMs,
      'expiresAtMs': expiresAtMs,
      'revoked': revoked,
    };
  }

  factory PairedDeviceRecord.fromJson(Map<String, dynamic> json) {
    return PairedDeviceRecord(
      deviceId: json['deviceId'] as String,
      label: json['label'] as String,
      role: DeviceRole.valueOf(json['role'] as int) ?? DeviceRole.DESKTOP,
      deviceEd25519Pub: Uint8List.fromList(base64Decode(json['ed'] as String)),
      deviceX25519Pub: Uint8List.fromList(base64Decode(json['x'] as String)),
      issuedAtMs: json['issuedAtMs'] as int,
      expiresAtMs: json['expiresAtMs'] as int,
      revoked: (json['revoked'] as bool?) ?? false,
    );
  }
}

/// Encrypted local storage facade for mnemonics, V2 credentials, devices,
/// verified contacts, seeds, and chat history.
class SecureLocalVault {
  static const String _keyMnemonic = 'ispchat_mnemonic_v1';
  static const String _keyContacts = 'ispchat_contacts_v1';
  static const String _keyBlocklist = 'ispchat_blocklist_v1';
  static const String _keySeeds = 'ispchat_seeds_v1';
  static const String _keyMessagesPrefix = 'ispchat_msgs_v1_';
  static const String _keyVerificationCred = 'ispchat_verification_cred_v2';
  static const String _keyHomeNodeClaim = 'ispchat_home_node_claim_v2';
  static const String _keyLocalDevice = 'ispchat_local_device_v2';
  static const String _keyPairedDevices = 'ispchat_paired_devices_v2';
  static const String _keyLastRevocationSeq = 'ispchat_last_revocation_seq_v2';

  static final Random _secureRandom = Random.secure();

  final KeyValueSecureStore _store;
  final IdentityCryptoService _crypto;

  SecureLocalVault({
    KeyValueSecureStore? store,
    IdentityCryptoService? cryptoService,
  })  : _store = store ?? const FlutterSecureKeyValueStore(),
        _crypto = cryptoService ?? const IdentityCryptoService();

  Future<void> saveMnemonic(String mnemonic) {
    return _store.write(key: _keyMnemonic, value: mnemonic.trim());
  }

  Future<String?> loadMnemonic() {
    return _store.read(key: _keyMnemonic);
  }

  Future<void> clearMnemonic() {
    return _store.delete(key: _keyMnemonic);
  }

  Future<IdentityBundle?> restoreSavedIdentity([
    IdentityCryptoService? cryptoOverride,
  ]) async {
    final saved = await loadMnemonic();
    if (saved == null || saved.trim().isEmpty) {
      return null;
    }
    final crypto = cryptoOverride ?? _crypto;
    return crypto.restoreIdentityFromMnemonic(saved);
  }

  Future<void> saveVerificationCredential(VerificationCredential credential) {
    return _store.write(
      key: _keyVerificationCred,
      value: base64Encode(credential.writeToBuffer()),
    );
  }

  Future<VerificationCredential?> loadVerificationCredential() async {
    final raw = await _store.read(key: _keyVerificationCred);
    if (raw == null || raw.isEmpty) {
      return null;
    }
    return VerificationCredential.fromBuffer(base64Decode(raw));
  }

  Future<void> clearVerificationCredential() {
    return _store.delete(key: _keyVerificationCred);
  }

  Future<void> saveHomeNodeClaim(HomeNodeClaim claim) {
    return _store.write(
      key: _keyHomeNodeClaim,
      value: base64Encode(claim.writeToBuffer()),
    );
  }

  Future<HomeNodeClaim?> loadHomeNodeClaim() async {
    final raw = await _store.read(key: _keyHomeNodeClaim);
    if (raw == null || raw.isEmpty) {
      return null;
    }
    return HomeNodeClaim.fromBuffer(base64Decode(raw));
  }

  Future<void> saveLocalDeviceRecord(StoredLocalDeviceRecord record) {
    return _store.write(
      key: _keyLocalDevice,
      value: jsonEncode(record.toJson()),
    );
  }

  Future<StoredLocalDeviceRecord?> loadLocalDeviceRecord() async {
    final raw = await _store.read(key: _keyLocalDevice);
    if (raw == null || raw.isEmpty) {
      return null;
    }
    return StoredLocalDeviceRecord.fromJson(
      jsonDecode(raw) as Map<String, dynamic>,
    );
  }

  Future<void> clearLocalDeviceRecord() {
    return _store.delete(key: _keyLocalDevice);
  }

  /// Loads persisted secondary device credentials, or generates & stores a
  /// fresh pair if none exists yet.
  Future<LocalDeviceIdentity> loadOrCreateLocalDeviceIdentity({
    required DeviceRole role,
    IdentityCryptoService? cryptoOverride,
  }) async {
    final crypto = cryptoOverride ?? _crypto;
    final existing = await loadLocalDeviceRecord();
    if (existing != null) {
      return crypto.restoreDeviceIdentityFromSeeds(
        deviceId: existing.deviceId,
        role: existing.role,
        ed25519Seed: existing.ed25519Seed,
        x25519Seed: existing.x25519Seed,
        delegationCert: existing.delegationCert,
      );
    }
    final edSeed = _randomSeed(32);
    final xSeed = _randomSeed(32);
    final deviceId = IdentityCryptoService.generateTemporalUuidV7();
    final record = StoredLocalDeviceRecord(
      deviceId: deviceId,
      role: role,
      ed25519Seed: edSeed,
      x25519Seed: xSeed,
    );
    await saveLocalDeviceRecord(record);
    return crypto.restoreDeviceIdentityFromSeeds(
      deviceId: deviceId,
      role: role,
      ed25519Seed: edSeed,
      x25519Seed: xSeed,
    );
  }

  Future<List<PairedDeviceRecord>> loadPairedDevices() async {
    final raw = await _store.read(key: _keyPairedDevices);
    if (raw == null || raw.isEmpty) {
      return const <PairedDeviceRecord>[];
    }
    final decoded = jsonDecode(raw) as List<dynamic>;
    return decoded
        .map((e) => PairedDeviceRecord.fromJson(e as Map<String, dynamic>))
        .toList(growable: false);
  }

  Future<void> savePairedDevice(PairedDeviceRecord device) async {
    final list = List<PairedDeviceRecord>.from(await loadPairedDevices());
    final normId = CanonicalTranscripts.canonicalUuid(device.deviceId);
    final idx = list.indexWhere(
      (d) => CanonicalTranscripts.canonicalUuid(d.deviceId) == normId,
    );
    if (idx >= 0) {
      list[idx] = device;
    } else {
      list.add(device);
    }
    await _store.write(
      key: _keyPairedDevices,
      value: jsonEncode(list.map((d) => d.toJson()).toList(growable: false)),
    );
  }

  Future<void> markPairedDeviceRevoked(String deviceId) async {
    final normId = CanonicalTranscripts.canonicalUuid(deviceId);
    final list = await loadPairedDevices();
    final updated = list
        .map(
          (d) => CanonicalTranscripts.canonicalUuid(d.deviceId) == normId
              ? d.copyWith(revoked: true)
              : d,
        )
        .toList(growable: false);
    await _store.write(
      key: _keyPairedDevices,
      value: jsonEncode(updated.map((d) => d.toJson()).toList(growable: false)),
    );
  }

  Future<int> loadLastRevocationSeq() async {
    final raw = await _store.read(key: _keyLastRevocationSeq);
    return int.tryParse(raw ?? '') ?? 0;
  }

  Future<void> saveLastRevocationSeq(int seq) {
    return _store.write(key: _keyLastRevocationSeq, value: seq.toString());
  }

  Future<List<ContactBundle>> loadContacts() async {
    final raw = await _store.read(key: _keyContacts);
    if (raw == null || raw.isEmpty) {
      return const <ContactBundle>[];
    }
    final list = jsonDecode(raw) as List<dynamic>;
    return list.map((item) {
      final map = item as Map<String, dynamic>;
      final vcB64 = map['vc'] as String?;
      final vc = vcB64 != null && vcB64.isNotEmpty
          ? VerificationCredential.fromBuffer(base64Decode(vcB64))
          : null;
      final hcB64 = map['hc'] as String?;
      final hc = hcB64 != null && hcB64.isNotEmpty
          ? HomeNodeClaim.fromBuffer(base64Decode(hcB64))
          : null;
      return ContactBundle(
        uuid: map['u'] as String,
        ed25519PublicKey: Uint8List.fromList(base64Decode(map['ed'] as String)),
        x25519PublicKey: Uint8List.fromList(base64Decode(map['x'] as String)),
        displayName: map['n'] as String?,
        isVerified: (map['verified'] as bool?) ?? (vc != null),
        docType: vc?.docType,
        publicCnpj: vc != null && vc.publicCnpj.isNotEmpty ? vc.publicCnpj : null,
        publicDisplayName: vc != null && vc.publicDisplayName.isNotEmpty
            ? vc.publicDisplayName
            : null,
        verifyingIspCnpj: vc != null && vc.verifyingIspCnpj.isNotEmpty
            ? vc.verifyingIspCnpj
            : null,
        verifyingIspName: vc != null && vc.verifyingIspName.isNotEmpty
            ? vc.verifyingIspName
            : null,
        verificationCredential: vc,
        homeNodeClaim: hc,
      );
    }).toList(growable: false);
  }

  Future<void> saveContact(ContactBundle contact) async {
    final contacts = List<ContactBundle>.from(await loadContacts());
    final normUuid = CanonicalTranscripts.canonicalUuid(contact.uuid);
    final idx = contacts.indexWhere(
      (c) => CanonicalTranscripts.canonicalUuid(c.uuid) == normUuid,
    );
    if (idx >= 0) {
      contacts[idx] = contact;
    } else {
      contacts.add(contact);
    }
    await _writeContacts(contacts);
  }

  Future<void> removeContact(String uuid) async {
    final normUuid = CanonicalTranscripts.canonicalUuid(uuid);
    final contacts = (await loadContacts())
        .where((c) => CanonicalTranscripts.canonicalUuid(c.uuid) != normUuid)
        .toList(growable: false);
    await _writeContacts(contacts);
  }

  /// Replaces an existing contact UUID when a CPF rebind `IdentityReplacementNotice`
  /// is verified, migrating chat history seamlessly under the new UUID.
  Future<void> migrateReplacedContact({
    required String oldUserUuid,
    required ContactBundle updatedContact,
  }) async {
    final normOld = CanonicalTranscripts.canonicalUuid(oldUserUuid);
    final normNew = CanonicalTranscripts.canonicalUuid(updatedContact.uuid);
    final contacts = List<ContactBundle>.from(await loadContacts())
      ..removeWhere(
        (c) =>
            CanonicalTranscripts.canonicalUuid(c.uuid) == normOld ||
            CanonicalTranscripts.canonicalUuid(c.uuid) == normNew,
      )
      ..add(updatedContact);
    await _writeContacts(contacts);

    if (normOld != normNew) {
      final oldMessages = await loadChatMessages(normOld);
      if (oldMessages.isNotEmpty) {
        final newMessages = await loadChatMessages(normNew);
        final merged = <String, StoredChatMessage>{
          for (final m in oldMessages) m.messageId: m.copyWith(peerUuid: normNew),
          for (final m in newMessages) m.messageId: m,
        };
        final sorted = merged.values.toList()
          ..sort((a, b) => a.timestampMs.compareTo(b.timestampMs));
        await _writeMessagesForPeer(normNew, sorted);
        await _store.delete(key: '$_keyMessagesPrefix$normOld');
      }
    }
  }

  Future<List<String>> loadBlockedUuids() async {
    final raw = await _store.read(key: _keyBlocklist);
    if (raw == null || raw.isEmpty) {
      return const <String>[];
    }
    return (jsonDecode(raw) as List<dynamic>).cast<String>();
  }

  Future<void> saveBlockedUuids(Iterable<String> uuids) {
    final unique = uuids.map((u) => u.trim()).where((u) => u.isNotEmpty).toSet();
    return _store.write(
      key: _keyBlocklist,
      value: jsonEncode(unique.toList(growable: false)),
    );
  }

  Future<List<String>> loadBootstrapSeeds() async {
    final raw = await _store.read(key: _keySeeds);
    if (raw == null || raw.isEmpty) {
      return const <String>[];
    }
    return (jsonDecode(raw) as List<dynamic>).cast<String>();
  }

  Future<void> saveBootstrapSeeds(Iterable<String> seeds) {
    final unique = seeds.map((s) => s.trim()).where((s) => s.isNotEmpty).toSet();
    return _store.write(
      key: _keySeeds,
      value: jsonEncode(unique.toList(growable: false)),
    );
  }

  Future<List<StoredChatMessage>> loadChatMessages(String peerUuid) async {
    final raw = await _store.read(key: '$_keyMessagesPrefix$peerUuid');
    if (raw == null || raw.isEmpty) {
      return const <StoredChatMessage>[];
    }
    final decoded = jsonDecode(raw) as List<dynamic>;
    return decoded
        .map((e) => StoredChatMessage.fromJson(e as Map<String, dynamic>))
        .toList(growable: false);
  }

  Future<void> appendChatMessage(StoredChatMessage message) async {
    final existing = List<StoredChatMessage>.from(
      await loadChatMessages(message.peerUuid),
    );
    final idx = existing.indexWhere((m) => m.messageId == message.messageId);
    if (idx >= 0) {
      existing[idx] = message;
    } else {
      existing.add(message);
    }
    await _writeMessagesForPeer(message.peerUuid, existing);
  }

  Future<void> updateChatMessageStatus({
    required String peerUuid,
    required String messageId,
    required String deliveryState,
  }) async {
    final existing = await loadChatMessages(peerUuid);
    var changed = false;
    final updated = existing.map((m) {
      if (m.messageId == messageId) {
        changed = true;
        return m.copyWith(deliveryState: deliveryState);
      }
      return m;
    }).toList(growable: false);
    if (changed) {
      await _writeMessagesForPeer(peerUuid, updated);
    }
  }

  Future<void> _writeContacts(List<ContactBundle> contacts) {
    final serialized = contacts
        .map((c) => <String, dynamic>{
              'u': c.uuid,
              'ed': base64Encode(c.ed25519PublicKey),
              'x': base64Encode(c.x25519PublicKey),
              if (c.displayName != null) 'n': c.displayName,
              'verified': c.isVerified,
              if (c.verificationCredential != null)
                'vc': base64Encode(c.verificationCredential!.writeToBuffer()),
              if (c.homeNodeClaim != null)
                'hc': base64Encode(c.homeNodeClaim!.writeToBuffer()),
            })
        .toList(growable: false);
    return _store.write(key: _keyContacts, value: jsonEncode(serialized));
  }

  Future<void> _writeMessagesForPeer(
    String peerUuid,
    List<StoredChatMessage> messages,
  ) {
    return _store.write(
      key: '$_keyMessagesPrefix$peerUuid',
      value: jsonEncode(
        messages.map((m) => m.toJson()).toList(growable: false),
      ),
    );
  }

  static Uint8List _randomSeed(int len) {
    final out = Uint8List(len);
    for (var i = 0; i < len; i++) {
      out[i] = _secureRandom.nextInt(256);
    }
    return out;
  }
}
