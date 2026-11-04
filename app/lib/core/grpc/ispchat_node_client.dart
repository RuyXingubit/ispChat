import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/services.dart' show ByteData, rootBundle;
import 'package:grpc/grpc.dart';

import '../crypto/identity_crypto_service.dart';
import '../messaging/e2e_session_manager.dart';
import 'ispchat_pb.dart';

/// Cliente gRPC de borda do App Flutter para comunicacao com o No do Provedor na porta 443 TLS.
class IspChatNodeClient {
  final E2eSessionManager sessionManager;

  ClientChannel? _channel;
  String? _connectedHost;
  StreamController<ClientEnvelopeProto>? _outgoingController;
  StreamSubscription<ServerEnvelopeProto>? _incomingSubscription;
  final StreamController<DecryptedIncomingEvent> _decryptedEventsController =
      StreamController<DecryptedIncomingEvent>.broadcast();

  IspChatNodeClient({
    required this.sessionManager,
  });

  /// Stream de eventos recebidos, ja filtrados pela Blocklist Local,
  /// verificados com Ed25519 e descriptografados E2E com X25519.
  Stream<DecryptedIncomingEvent> get incomingEvents => _decryptedEventsController.stream;

  bool get isConnected => _channel != null && _outgoingController != null;

  String? get connectedHost => _connectedHost;

  /// Carrega o certificado da CA oficial embutido nos assets (`assets/certs/ca.crt`).
  Future<List<int>?> _loadBundledCaCert() async {
    try {
      final ByteData data = await rootBundle.load('assets/certs/ca.crt');
      return data.buffer.asUint8List();
    } catch (_) {
      return null;
    }
  }

  /// Conecta ao No ISP na porta 443 TLS, registra a sessao na DHT Kademlia
  /// e abre a stream bidirecional enviando imediatamente o pacote `AUTH_HANDSHAKE`.
  Future<RegisterSessionResponseProto> connectAndAuthenticate({
    required String nodeHost,
    required UserIdentity identity,
    int port = 443,
    List<int>? trustedCaCertificateBytes,
    String? authorityOverride,
    bool useInsecureLocalTestTransport = false,
  }) async {
    await disconnect();

    final List<int>? caBytes = trustedCaCertificateBytes ??
        (useInsecureLocalTestTransport ? null : await _loadBundledCaCert());

    final ChannelCredentials credentials = useInsecureLocalTestTransport
        ? const ChannelCredentials.insecure()
        : (caBytes != null
            ? ChannelCredentials.secure(
                certificates: caBytes,
                authority: authorityOverride,
              )
            : const ChannelCredentials.secure());

    final ClientChannel channel = ClientChannel(
      nodeHost,
      port: port,
      options: ChannelOptions(credentials: credentials),
    );
    _channel = channel;

    final int nowMillis = DateTime.now().millisecondsSinceEpoch;
    final String nodeAddress = port == 443 ? nodeHost : '$nodeHost:$port';

    final Uint8List dhtSignature = await sessionManager.cryptoService.signDhtRegistration(
      identity: identity,
      nodeAddress: nodeAddress,
      timestampMillis: nowMillis,
    );

    final RegisterSessionRequestProto regRequest = RegisterSessionRequestProto(
      userUuid: identity.userUuid,
      nodeAddress: nodeAddress,
      publicKey: identity.ed25519PublicKeyBytes,
      x25519PublicKey: identity.x25519PublicKeyBytes,
      signature: dhtSignature,
      timestamp: nowMillis,
    );

    final ClientCall<RegisterSessionRequestProto, RegisterSessionResponseProto> regCall =
        channel.createCall(
      IspChatGrpcMethods.registerSession,
      Stream<RegisterSessionRequestProto>.value(regRequest),
      CallOptions(timeout: const Duration(seconds: 5)),
    );

    final RegisterSessionResponseProto regResponse = await regCall.response.single;
    if (!regResponse.success) {
      await disconnect();
      return regResponse;
    }

    _connectedHost = nodeAddress;

    // Abre a stream bidirecional ClientStreamService/Connect
    final StreamController<ClientEnvelopeProto> outController =
        StreamController<ClientEnvelopeProto>();
    _outgoingController = outController;

    final ClientCall<ClientEnvelopeProto, ServerEnvelopeProto> streamCall = channel.createCall(
      IspChatGrpcMethods.connectStream,
      outController.stream,
      CallOptions(),
    );

    _incomingSubscription = streamCall.response.listen(
      (ServerEnvelopeProto serverEnvelope) async {
        final DecryptedIncomingEvent? event =
            await sessionManager.processIncomingServerEnvelope(
          localIdentity: identity,
          serverEnvelope: serverEnvelope,
        );
        if (event != null && !_decryptedEventsController.isClosed) {
          _decryptedEventsController.add(event);
        }
      },
      onError: (_) {},
    );

    // Envia o primeiro frame AUTH_HANDSHAKE assinado para autenticar a stream e drenar a fila offline do Redis
    final ClientEnvelopeProto handshake =
        await sessionManager.createSignedAuthHandshake(identity);
    outController.add(handshake);

    return regResponse;
  }

  /// Consulta a localizacao atual e chaves publicas de um usuario na DHT Kademlia.
  Future<FindUserResponseProto> findUserInDht(String targetUuid) async {
    final ClientChannel? channel = _channel;
    if (channel == null) {
      throw StateError('Canal gRPC nao esta conectado ao No do provedor.');
    }

    final FindUserRequestProto req = FindUserRequestProto(targetUuid: targetUuid);
    final ClientCall<FindUserRequestProto, FindUserResponseProto> call = channel.createCall(
      IspChatGrpcMethods.findUser,
      Stream<FindUserRequestProto>.value(req),
      CallOptions(timeout: const Duration(seconds: 5)),
    );
    return call.response.single;
  }

  /// Envia um `ClientEnvelopeProto` ja assinado e criptografado pela stream gRPC ativa.
  /// Retorna `true` se foi despachado na rede em tempo real, ou `false` se estiver offline.
  bool sendSignedEnvelope(ClientEnvelopeProto envelope) {
    final StreamController<ClientEnvelopeProto>? out = _outgoingController;
    if (out == null || out.isClosed) {
      return false;
    }
    out.add(envelope);
    return true;
  }

  /// Envia uma mensagem de texto E2E ou sinalizacao WebRTC pela stream ativa.
  Future<void> sendEncryptedEvent({
    required UserIdentity senderIdentity,
    required ContactBundle recipientContact,
    required ProtoMessageType type,
    required String cleartextOrJsonSignal,
  }) async {
    final StreamController<ClientEnvelopeProto>? out = _outgoingController;
    if (out == null || out.isClosed) {
      throw StateError('Stream gRPC nao esta conectada ao No do provedor.');
    }

    final ClientEnvelopeProto envelope =
        await sessionManager.buildOutgoingEncryptedEnvelope(
      senderIdentity: senderIdentity,
      recipientContact: recipientContact,
      type: type,
      cleartextOrJsonSignal: cleartextOrJsonSignal,
    );

    out.add(envelope);
  }

  /// Encerra a stream e fecha o canal gRPC.
  Future<void> disconnect() async {
    _connectedHost = null;
    await _incomingSubscription?.cancel();
    _incomingSubscription = null;

    await _outgoingController?.close();
    _outgoingController = null;

    final ClientChannel? ch = _channel;
    _channel = null;
    if (ch != null) {
      await ch.shutdown();
    }
  }

  Future<void> dispose() async {
    await disconnect();
    await _decryptedEventsController.close();
  }
}
