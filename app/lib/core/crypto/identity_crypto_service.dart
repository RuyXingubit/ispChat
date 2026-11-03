import 'dart:convert';
import 'dart:typed_data';

import 'package:bip39/bip39.dart' as bip39;
import 'package:cryptography/cryptography.dart';
import 'package:uuid/uuid.dart';

/// Representa a identidade auto-soberana do usuario derivada deterministicamente
/// a partir da frase semente de 12 palavras (BIP39).
class UserIdentity {
  final String mnemonic;
  final String userUuid;
  final SimpleKeyPair ed25519KeyPair;
  final Uint8List ed25519PublicKeyBytes;
  final SimpleKeyPair x25519KeyPair;
  final Uint8List x25519PublicKeyBytes;

  const UserIdentity({
    required this.mnemonic,
    required this.userUuid,
    required this.ed25519KeyPair,
    required this.ed25519PublicKeyBytes,
    required this.x25519KeyPair,
    required this.x25519PublicKeyBytes,
  });
}

/// Dados de um contato importado via QR Code ou Deep Link (ispchat://contact?...).
class ContactBundle {
  final String userUuid;
  final Uint8List ed25519PublicKeyBytes;
  final Uint8List x25519PublicKeyBytes;
  final String? directIpv6Address;

  const ContactBundle({
    required this.userUuid,
    required this.ed25519PublicKeyBytes,
    required this.x25519PublicKeyBytes,
    this.directIpv6Address,
  });
}

/// Servico criptografico do App Flutter.
///
/// Implementa exatamente as especificacoes bit-a-bit alinhadas com o Backend Java 25 LTS:
/// 1. BIP39 (12 palavras) -> Seed de 64 bytes:
///    - seed[0..31]  -> Par de chaves Ed25519 (Assinatura Digital e Identidade)
///    - seed[32..63] -> Par de chaves X25519 (Acordo de chaves ECDH para Criptografia E2E)
/// 2. UUIDv7 Deterministico de Identidade:
///    - SHA-256(ed25519PublicKey)[0..15] com bits de versao 7 (0x70) e variante IETF (0x80).
/// 3. Assinaturas canonicas Ed25519 (ISPCHAT-DHT-V1| e ISPCHAT-MSG-V1|).
/// 4. Criptografia E2E autenticada com X25519 + HKDF-SHA256 + ChaCha20-Poly1305.
class IdentityCryptoService {
  static const int ed25519PublicKeyLength = 32;
  static const int x25519PublicKeyLength = 32;
  static const int ed25519SignatureLength = 64;
  static const int chacha20NonceLength = 12;
  static const int poly1305MacLength = 16;

  static const String dhtDomainPrefix = 'ISPCHAT-DHT-V1|';
  static const String msgDomainPrefix = 'ISPCHAT-MSG-V1|';

  final Ed25519 _ed25519 = Ed25519();
  final X25519 _x25519 = X25519();
  final Sha256 _sha256 = Sha256();
  final Chacha20 _cipher = Chacha20.poly1305Aead();
  final Uuid _uuid = const Uuid();

  /// Gera um UUIDv7 temporal (RFC 9562) para identificacao de mensagens e eventos.
  String generateTemporalUuidV7() {
    return _uuid.v7();
  }

  /// Gera uma nova frase semente de 12 palavras (128 bits de entropia BIP39)
  /// e deriva todas as chaves e o UUIDv7 deterministico.
  Future<UserIdentity> generateNewIdentity() async {
    final String mnemonic = bip39.generateMnemonic(strength: 128);
    return restoreIdentityFromMnemonic(mnemonic);
  }

  /// Restaura a exata mesma identidade (Ed25519, X25519 e UUIDv7) a partir das 12 palavras BIP39.
  Future<UserIdentity> restoreIdentityFromMnemonic(String mnemonic) async {
    final String normalized = mnemonic.trim().toLowerCase().replaceAll(RegExp(r'\s+'), ' ');
    if (!bip39.validateMnemonic(normalized)) {
      throw ArgumentError('Frase semente BIP39 invalida.');
    }

    final Uint8List seed64 = bip39.mnemonicToSeed(normalized);
    if (seed64.length < 64) {
      throw StateError('Seed BIP39 deve conter 64 bytes.');
    }

    final Uint8List ed25519Seed = Uint8List.sublistView(seed64, 0, 32);
    final Uint8List x25519Seed = Uint8List.sublistView(seed64, 32, 64);

    final SimpleKeyPair ed25519KeyPair = await _ed25519.newKeyPairFromSeed(ed25519Seed);
    final SimplePublicKey ed25519PubKey = await ed25519KeyPair.extractPublicKey();
    final Uint8List ed25519PubBytes = Uint8List.fromList(ed25519PubKey.bytes);

    final SimpleKeyPair x25519KeyPair = await _x25519.newKeyPairFromSeed(x25519Seed);
    final SimplePublicKey x25519PubKey = await x25519KeyPair.extractPublicKey();
    final Uint8List x25519PubBytes = Uint8List.fromList(x25519PubKey.bytes);

    final String userUuid = await deriveIdentityUuidV7(ed25519PubBytes);

    return UserIdentity(
      mnemonic: normalized,
      userUuid: userUuid,
      ed25519KeyPair: ed25519KeyPair,
      ed25519PublicKeyBytes: ed25519PubBytes,
      x25519KeyPair: x25519KeyPair,
      x25519PublicKeyBytes: x25519PubBytes,
    );
  }

  /// Deriva deterministicamente o UUIDv7 de identidade a partir dos 32 bytes da chave publica Ed25519.
  /// Produz resultado bit-a-bit identico ao CryptoService.deriveIdentityUuidV7 do Backend Java 25.
  Future<String> deriveIdentityUuidV7(List<int> ed25519PublicKey32Bytes) async {
    if (ed25519PublicKey32Bytes.length != ed25519PublicKeyLength) {
      throw ArgumentError('Chave publica Ed25519 deve ter exatamente 32 bytes.');
    }

    final Hash hash = await _sha256.hash(ed25519PublicKey32Bytes);
    final Uint8List uuidBytes = Uint8List.fromList(hash.bytes.sublist(0, 16));

    // Bits de versao 7 (RFC 9562): 0111xxxx
    uuidBytes[6] = (uuidBytes[6] & 0x0F) | 0x70;
    // Bits de variante IETF (RFC 9562): 10xxxxxx
    uuidBytes[8] = (uuidBytes[8] & 0x3F) | 0x80;

    return _formatUuidBytes(uuidBytes);
  }

  /// Verifica se o userUuid corresponde matematicamente a chave publica Ed25519 informada.
  Future<bool> verifyIdentityBinding(String userUuid, List<int> ed25519PublicKey32Bytes) async {
    if (userUuid.trim().isEmpty || ed25519PublicKey32Bytes.length != ed25519PublicKeyLength) {
      return false;
    }
    try {
      final String derived = await deriveIdentityUuidV7(ed25519PublicKey32Bytes);
      return derived == userUuid.trim().toLowerCase();
    } catch (_) {
      return false;
    }
  }

  /// Constroi os bytes canonicos para assinatura de registro na DHT:
  /// "ISPCHAT-DHT-V1|" + userUuid + "|" + nodeAddress + "|" + timestampMillis
  Uint8List buildCanonicalDhtPayload({
    required String userUuid,
    required String nodeAddress,
    required int timestampMillis,
  }) {
    final String canonical =
        '$dhtDomainPrefix${userUuid.trim().toLowerCase()}|${nodeAddress.trim()}|$timestampMillis';
    return Uint8List.fromList(utf8.encode(canonical));
  }

  /// Constroi os bytes canonicos para assinatura de envelope de mensagem / sinalizacao:
  /// "ISPCHAT-MSG-V1|" + messageId + "|" + senderUuid + "|" + receiverUuid + "|" + messageTypeNumber + "|" + timestampMillis + "|" + encryptedPayload
  Uint8List buildCanonicalMessagePayload({
    required String messageId,
    required String senderUuid,
    required String receiverUuid,
    required int messageTypeNumber,
    required int timestampMillis,
    required List<int> encryptedPayload,
  }) {
    final String header = '$msgDomainPrefix'
        '${messageId.trim().toLowerCase()}|'
        '${senderUuid.trim().toLowerCase()}|'
        '${receiverUuid.trim().toLowerCase()}|'
        '$messageTypeNumber|'
        '$timestampMillis|';
    final List<int> headerBytes = utf8.encode(header);

    final BytesBuilder builder = BytesBuilder(copy: false);
    builder.add(headerBytes);
    builder.add(encryptedPayload);
    return builder.toBytes();
  }

  /// Assina um registro de sessao da DHT com a chave privada Ed25519 do usuario.
  Future<Uint8List> signDhtRegistration({
    required UserIdentity identity,
    required String nodeAddress,
    required int timestampMillis,
  }) async {
    final Uint8List payload = buildCanonicalDhtPayload(
      userUuid: identity.userUuid,
      nodeAddress: nodeAddress,
      timestampMillis: timestampMillis,
    );
    final Signature signature = await _ed25519.sign(payload, keyPair: identity.ed25519KeyPair);
    return Uint8List.fromList(signature.bytes);
  }

  /// Assina um envelope de mensagem/sinalizacao com a chave privada Ed25519 do usuario.
  Future<Uint8List> signMessageEnvelope({
    required UserIdentity identity,
    required String messageId,
    required String receiverUuid,
    required int messageTypeNumber,
    required int timestampMillis,
    required List<int> encryptedPayload,
  }) async {
    final Uint8List canonical = buildCanonicalMessagePayload(
      messageId: messageId,
      senderUuid: identity.userUuid,
      receiverUuid: receiverUuid,
      messageTypeNumber: messageTypeNumber,
      timestampMillis: timestampMillis,
      encryptedPayload: encryptedPayload,
    );
    final Signature signature = await _ed25519.sign(canonical, keyPair: identity.ed25519KeyPair);
    return Uint8List.fromList(signature.bytes);
  }

  /// Verifica a autenticidade e integridade de um envelope recebido (Anti-Spoofing + Ed25519).
  Future<bool> verifyReceivedEnvelope({
    required String messageId,
    required String senderUuid,
    required String receiverUuid,
    required int messageTypeNumber,
    required int timestampMillis,
    required List<int> encryptedPayload,
    required List<int> senderEd25519PublicKey,
    required List<int> signatureBytes,
  }) async {
    if (signatureBytes.length != ed25519SignatureLength ||
        senderEd25519PublicKey.length != ed25519PublicKeyLength) {
      return false;
    }

    final bool bound = await verifyIdentityBinding(senderUuid, senderEd25519PublicKey);
    if (!bound) {
      return false;
    }

    final Uint8List canonical = buildCanonicalMessagePayload(
      messageId: messageId,
      senderUuid: senderUuid,
      receiverUuid: receiverUuid,
      messageTypeNumber: messageTypeNumber,
      timestampMillis: timestampMillis,
      encryptedPayload: encryptedPayload,
    );

    final SimplePublicKey publicKey = SimplePublicKey(
      senderEd25519PublicKey,
      type: KeyPairType.ed25519,
    );
    final Signature signature = Signature(signatureBytes, publicKey: publicKey);
    return _ed25519.verify(canonical, signature: signature);
  }

  /// Criptografa um payload em texto claro de ponta a ponta (E2E) usando X25519 (ECDH)
  /// + HKDF-SHA256 + ChaCha20-Poly1305.
  /// Formato do blob retornado: [nonce (12 bytes) | mac (16 bytes) | ciphertext (N bytes)].
  Future<Uint8List> encryptPayloadE2E({
    required SimpleKeyPair senderX25519KeyPair,
    required List<int> recipientX25519PublicKeyBytes,
    required List<int> plaintext,
  }) async {
    final SecretKey symmetricKey = await _deriveSharedSymmetricKey(
      localKeyPair: senderX25519KeyPair,
      remotePublicKeyBytes: recipientX25519PublicKeyBytes,
    );

    final List<int> nonce = _cipher.newNonce();
    final SecretBox secretBox = await _cipher.encrypt(
      plaintext,
      secretKey: symmetricKey,
      nonce: nonce,
    );

    final BytesBuilder builder = BytesBuilder(copy: false);
    builder.add(secretBox.nonce);
    builder.add(secretBox.mac.bytes);
    builder.add(secretBox.cipherText);
    return builder.toBytes();
  }

  /// Descriptografa um payload E2E recebido usando a chave privada X25519 local
  /// e a chave publica X25519 do remetente.
  Future<Uint8List> decryptPayloadE2E({
    required SimpleKeyPair recipientX25519KeyPair,
    required List<int> senderX25519PublicKeyBytes,
    required List<int> encryptedBlob,
  }) async {
    if (encryptedBlob.length < chacha20NonceLength + poly1305MacLength) {
      throw ArgumentError('Payload criptografado invalido ou truncado.');
    }

    final SecretKey symmetricKey = await _deriveSharedSymmetricKey(
      localKeyPair: recipientX25519KeyPair,
      remotePublicKeyBytes: senderX25519PublicKeyBytes,
    );

    final List<int> nonce = encryptedBlob.sublist(0, chacha20NonceLength);
    final List<int> macBytes = encryptedBlob.sublist(
      chacha20NonceLength,
      chacha20NonceLength + poly1305MacLength,
    );
    final List<int> cipherText = encryptedBlob.sublist(chacha20NonceLength + poly1305MacLength);

    final SecretBox secretBox = SecretBox(
      cipherText,
      nonce: nonce,
      mac: Mac(macBytes),
    );

    final List<int> clearBytes = await _cipher.decrypt(
      secretBox,
      secretKey: symmetricKey,
    );
    return Uint8List.fromList(clearBytes);
  }

  /// Gera o Link Magico / payload de QR Code para compartilhamento seguro de identidade
  /// (sem depender de @aliases globais) e suporte opcional ao Modo Doomsday (IPv6 direto).
  String buildShareableContactUri(UserIdentity identity, {String? directIpv6Address}) {
    final Map<String, String> queryParams = <String, String>{
      'uuid': identity.userUuid,
      'ed25519': base64UrlEncode(identity.ed25519PublicKeyBytes),
      'x25519': base64UrlEncode(identity.x25519PublicKeyBytes),
      if (directIpv6Address != null && directIpv6Address.trim().isNotEmpty)
        'ipv6': directIpv6Address.trim(),
    };
    return Uri(
      scheme: 'ispchat',
      host: 'contact',
      queryParameters: queryParams,
    ).toString();
  }

  /// Valida e extrai um ContactBundle a partir de um Link Magico / QR Code,
  /// verificando matematicamente que o UUIDv7 pertence a chave Ed25519 do link.
  Future<ContactBundle> parseAndVerifyContactUri(String rawUri) async {
    final Uri uri = Uri.parse(rawUri.trim());
    if (uri.scheme != 'ispchat' || uri.host != 'contact') {
      throw const FormatException('URI de contato deve iniciar com ispchat://contact');
    }

    final String? uuid = uri.queryParameters['uuid'];
    final String? ed25519B64 = uri.queryParameters['ed25519'];
    final String? x25519B64 = uri.queryParameters['x25519'];
    final String? ipv6 = uri.queryParameters['ipv6'];

    if (uuid == null || ed25519B64 == null || x25519B64 == null) {
      throw const FormatException('URI de contato incompleta (uuid, ed25519 e x25519 sao obrigatorios).');
    }

    final Uint8List ed25519Bytes = Uint8List.fromList(base64Url.decode(ed25519B64));
    final Uint8List x25519Bytes = Uint8List.fromList(base64Url.decode(x25519B64));

    if (x25519Bytes.length != x25519PublicKeyLength) {
      throw const FormatException('Chave X25519 invalida no link de contato.');
    }

    final bool validBinding = await verifyIdentityBinding(uuid, ed25519Bytes);
    if (!validBinding) {
      throw const FormatException('Alerta Anti-Spoofing: O UUIDv7 do link nao pertence a chave publica Ed25519 informada.');
    }

    return ContactBundle(
      userUuid: uuid.trim().toLowerCase(),
      ed25519PublicKeyBytes: ed25519Bytes,
      x25519PublicKeyBytes: x25519Bytes,
      directIpv6Address: (ipv6 != null && ipv6.trim().isNotEmpty) ? ipv6.trim() : null,
    );
  }

  Future<SecretKey> _deriveSharedSymmetricKey({
    required SimpleKeyPair localKeyPair,
    required List<int> remotePublicKeyBytes,
  }) async {
    if (remotePublicKeyBytes.length != x25519PublicKeyLength) {
      throw ArgumentError('Chave publica X25519 deve ter 32 bytes.');
    }
    final SimplePublicKey remotePubKey = SimplePublicKey(
      remotePublicKeyBytes,
      type: KeyPairType.x25519,
    );
    final SecretKey sharedSecret = await _x25519.sharedSecretKey(
      keyPair: localKeyPair,
      remotePublicKey: remotePubKey,
    );

    final Hkdf hkdf = Hkdf(hmac: Hmac.sha256(), outputLength: 32);
    return hkdf.deriveKey(
      secretKey: sharedSecret,
      nonce: utf8.encode('ISPCHAT-E2E-X25519-HKDF-SALT-V1'),
      info: utf8.encode('ISPCHAT-CHACHA20-POLY1305-KEY'),
    );
  }

  static String _formatUuidBytes(Uint8List bytes) {
    final StringBuffer sb = StringBuffer();
    for (int i = 0; i < 16; i++) {
      if (i == 4 || i == 6 || i == 8 || i == 10) {
        sb.write('-');
      }
      sb.write(bytes[i].toRadixString(16).padLeft(2, '0'));
    }
    return sb.toString();
  }
}
