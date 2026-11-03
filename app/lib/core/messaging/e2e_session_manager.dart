import 'dart:convert';
import 'dart:typed_data';

import '../crypto/identity_crypto_service.dart';
import '../grpc/ispchat_pb.dart';
import '../security/blocklist_service.dart';

/// Mensagem de texto ou sinalizacao WebRTC ja validada (Ed25519) e descriptografada (X25519 E2E).
class DecryptedIncomingEvent {
  final String messageId;
  final String senderUuid;
  final ProtoMessageType type;
  final String cleartextOrJsonSignal;
  final int timestampMillis;

  const DecryptedIncomingEvent({
    required this.messageId,
    required this.senderUuid,
    required this.type,
    required this.cleartextOrJsonSignal,
    required this.timestampMillis,
  });
}

/// Coordenador de Sessao E2E (Chat de Texto, Sinalizacao WebRTC Cifrada e Filtragem Anti-Assedio).
///
/// Responsabilidades:
/// 1. Gerenciar o diretorio local de contatos verificados via QR Code / Link Magico.
/// 2. Construir o pacote inicial `AUTH_HANDSHAKE` assinado ao abrir a stream com o No do Provedor.
/// 3. Criptografar mensagens de texto (`MessageType.TEXT`) e metadados WebRTC (`RING`, `SDP_OFFER`,
///    `SDP_ANSWER`, `ICE_CANDIDATE`, `CALL_DECLINE`) de ponta a ponta (X25519 + ChaCha20-Poly1305)
///    e assina-los com `Ed25519`.
/// 4. Filtrar todo `ServerEnvelope` recebido contra:
///    - Blocklist Local (`BlocklistService`): derruba chamadas e mensagens de UUIDs bloqueados silenciosamente.
///    - Verificacao Anti-Spoofing (`UUIDv7 == Hash(Ed25519_PubKey)` + assinatura `Ed25519`).
///    - Descriptografia autenticada E2E.
class E2eSessionManager {
  final IdentityCryptoService cryptoService;
  final BlocklistService blocklistService;
  final Map<String, ContactBundle> _contactsByUuid = <String, ContactBundle>{};

  E2eSessionManager({
    required this.cryptoService,
    required this.blocklistService,
  });

  /// Salva um contato verificado localmente (importado via QR Code / Link Magico).
  void registerVerifiedContact(ContactBundle contact) {
    _contactsByUuid[contact.userUuid.trim().toLowerCase()] = contact;
  }

  ContactBundle? getContact(String userUuid) {
    return _contactsByUuid[userUuid.trim().toLowerCase()];
  }

  List<ContactBundle> get contacts => List<ContactBundle>.unmodifiable(_contactsByUuid.values);

  /// Gera o primeiro frame `AUTH_HANDSHAKE` assinado para autenticar a stream `Connect`
  /// e disparar o descarregamento da fila offline (Redis Store-and-Forward).
  Future<ClientEnvelopeProto> createSignedAuthHandshake(
    UserIdentity localIdentity, {
    int? nowMillis,
  }) async {
    final int timestamp = nowMillis ?? DateTime.now().millisecondsSinceEpoch;
    final String messageId = cryptoService.generateTemporalUuidV7();
    final Uint8List emptyPayload = Uint8List(0);

    final Uint8List signature = await cryptoService.signMessageEnvelope(
      identity: localIdentity,
      messageId: messageId,
      receiverUuid: localIdentity.userUuid,
      messageTypeNumber: ProtoMessageType.authHandshake.value,
      timestampMillis: timestamp,
      encryptedPayload: emptyPayload,
    );

    return ClientEnvelopeProto(
      messageId: messageId,
      senderUuid: localIdentity.userUuid,
      receiverUuid: localIdentity.userUuid,
      type: ProtoMessageType.authHandshake,
      encryptedPayload: emptyPayload,
      senderPublicKey: localIdentity.ed25519PublicKeyBytes,
      signature: signature,
      timestamp: timestamp,
    );
  }

  /// Empacota, criptografa E2E (X25519 + ChaCha20-Poly1305) e assina (Ed25519)
  /// uma mensagem de texto ou pacote de sinalizacao WebRTC para envio ao No.
  Future<ClientEnvelopeProto> buildOutgoingEncryptedEnvelope({
    required UserIdentity senderIdentity,
    required ContactBundle recipientContact,
    required ProtoMessageType type,
    required String cleartextOrJsonSignal,
    int? nowMillis,
  }) async {
    final int timestamp = nowMillis ?? DateTime.now().millisecondsSinceEpoch;
    final String messageId = cryptoService.generateTemporalUuidV7();
    final List<int> plaintextBytes = utf8.encode(cleartextOrJsonSignal);

    final Uint8List encryptedPayload = await cryptoService.encryptPayloadE2E(
      senderX25519KeyPair: senderIdentity.x25519KeyPair,
      recipientX25519PublicKeyBytes: recipientContact.x25519PublicKeyBytes,
      plaintext: plaintextBytes,
    );

    final Uint8List signature = await cryptoService.signMessageEnvelope(
      identity: senderIdentity,
      messageId: messageId,
      receiverUuid: recipientContact.userUuid,
      messageTypeNumber: type.value,
      timestampMillis: timestamp,
      encryptedPayload: encryptedPayload,
    );

    return ClientEnvelopeProto(
      messageId: messageId,
      senderUuid: senderIdentity.userUuid,
      receiverUuid: recipientContact.userUuid,
      type: type,
      encryptedPayload: encryptedPayload,
      senderPublicKey: senderIdentity.ed25519PublicKeyBytes,
      signature: signature,
      timestamp: timestamp,
    );
  }

  /// Processa um `ServerEnvelopeProto` recebido da stream do No:
  /// 1. Se o remetente estiver na Blocklist Local, retorna `null` silenciosamente (derruba RING/TEXT).
  /// 2. Valida matematicamente a assinatura Ed25519 e o vinculo `UUIDv7 == Hash(PublicKey)`.
  /// 3. Descriptografa o payload E2E usando a chave X25519 do contato.
  Future<DecryptedIncomingEvent?> processIncomingServerEnvelope({
    required UserIdentity localIdentity,
    required ServerEnvelopeProto serverEnvelope,
  }) async {
    final String normalizedSender = serverEnvelope.senderUuid.trim().toLowerCase();

    // 1. Filtro silencioso da Blocklist Local (Anti-Assedio)
    if (!blocklistService.shouldAllowIncomingEvent(normalizedSender)) {
      return null;
    }

    // 2. Verificacao criptografica Ed25519 + Anti-Spoofing do UUIDv7
    final bool signatureValid = await cryptoService.verifyReceivedEnvelope(
      messageId: serverEnvelope.messageId,
      senderUuid: normalizedSender,
      receiverUuid: localIdentity.userUuid,
      messageTypeNumber: serverEnvelope.type.value,
      timestampMillis: serverEnvelope.timestamp,
      encryptedPayload: serverEnvelope.encryptedPayload,
      senderEd25519PublicKey: serverEnvelope.senderPublicKey,
      signatureBytes: serverEnvelope.signature,
    );

    if (!signatureValid) {
      return null;
    }

    // 3. Busca a chave X25519 do contato para descriptografia E2E
    final ContactBundle? senderContact = _contactsByUuid[normalizedSender];
    if (senderContact == null) {
      return null;
    }

    final Uint8List decryptedBytes = await cryptoService.decryptPayloadE2E(
      recipientX25519KeyPair: localIdentity.x25519KeyPair,
      senderX25519PublicKeyBytes: senderContact.x25519PublicKeyBytes,
      encryptedBlob: serverEnvelope.encryptedPayload,
    );

    return DecryptedIncomingEvent(
      messageId: serverEnvelope.messageId,
      senderUuid: normalizedSender,
      type: serverEnvelope.type,
      cleartextOrJsonSignal: utf8.decode(decryptedBytes),
      timestampMillis: serverEnvelope.timestamp,
    );
  }
}
