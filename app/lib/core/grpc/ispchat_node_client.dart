import 'dart:async';

import 'package:fixnum/fixnum.dart' as $fixnum;
import 'package:grpc/grpc.dart';
import 'package:app/core/config/network_bootstrap_config.dart';
import 'package:app/core/discovery/tcp_latency_probe.dart';
import 'package:app/core/grpc/generated/ispchat.pbgrpc.dart';

/// gRPC transport client for Edge/Home Nodes (`DhtClient`, `ClientStream`,
/// `TurnCredential`, `Directory`) and Principal Governance RPCs, with
/// exponential reconnect backoff (`1s -> 30s`).
class IspChatNodeClient {
  final NetworkBootstrapConfig bootstrapConfig;
  final Duration defaultCallTimeout;

  ClientChannel? _edgeChannel;
  ClientChannel? _principalChannel;
  String? _connectedAddress;
  String? _principalAddress;

  StreamController<ClientEnvelope>? _outgoingController;
  StreamSubscription<ServerEnvelope>? _incomingSub;
  final StreamController<ServerEnvelope> _incomingBroadcast =
      StreamController<ServerEnvelope>.broadcast();

  bool _manualDisconnect = false;
  bool _streamActive = false;
  int _reconnectAttempt = 0;
  Timer? _reconnectTimer;
  Future<void> Function()? _onReconnected;

  IspChatNodeClient({
    this.bootstrapConfig = const NetworkBootstrapConfig(),
    this.defaultCallTimeout = const Duration(seconds: 15),
  });

  bool get isConnected => _edgeChannel != null;
  String? get connectedNodeAddress => _connectedAddress;
  Stream<ServerEnvelope> get incomingEnvelopes => _incomingBroadcast.stream;

  CallOptions _callOptions([Duration? timeout]) {
    return CallOptions(timeout: timeout ?? defaultCallTimeout);
  }

  /// Connects using an `"host:port"` or `"[ipv6]:port"` endpoint string.
  Future<void> connectToAddress(
    String nodeAddress, {
    bool useTls = true,
    ChannelCredentials? tlsCredentials,
    String? principalAddress,
    bool routeGovernanceThroughEdgeChannel = false,
  }) async {
    final parsed = TcpLatencyProbe.parseHostAndPort(nodeAddress.trim());
    await connect(
      host: parsed.host,
      port: parsed.port,
      useTls: useTls,
      tlsCredentials: tlsCredentials,
      principalAddress: principalAddress,
      routeGovernanceThroughEdgeChannel: routeGovernanceThroughEdgeChannel,
      displayAddress: nodeAddress.trim(),
    );
  }

  /// Connects to an explicit `(host, port)` pair (preserves TLS by default).
  Future<void> connect({
    required String host,
    required int port,
    bool useTls = true,
    ChannelCredentials? tlsCredentials,
    String? principalAddress,
    bool routeGovernanceThroughEdgeChannel = false,
    String? displayAddress,
  }) async {
    await disconnect();
    _manualDisconnect = false;
    _connectedAddress = displayAddress ??
        (host.contains(':') && !host.startsWith('[')
            ? '[$host]:$port'
            : '$host:$port');

    final creds = useTls
        ? (tlsCredentials ?? const ChannelCredentials.secure())
        : const ChannelCredentials.insecure();

    _edgeChannel = ClientChannel(
      host,
      port: port,
      options: ChannelOptions(credentials: creds),
    );

    if (routeGovernanceThroughEdgeChannel) {
      _principalChannel = _edgeChannel;
      _principalAddress = _connectedAddress;
    } else {
      final targetPrincipal =
          (principalAddress ?? bootstrapConfig.principalFallbackAddress).trim();
      if (targetPrincipal.isNotEmpty) {
        try {
          final pParsed = TcpLatencyProbe.parseHostAndPort(targetPrincipal);
          _principalAddress = targetPrincipal;
          _principalChannel = ClientChannel(
            pParsed.host,
            port: pParsed.port,
            options: ChannelOptions(credentials: creds),
          );
        } catch (_) {}
      }
    }
  }

  ClientChannel get _requireEdge {
    final ch = _edgeChannel;
    if (ch == null) {
      throw StateError('IspChatNodeClient is not connected to an edge node.');
    }
    return ch;
  }

  ClientChannel get _requirePrincipal {
    final ch = _principalChannel ?? _edgeChannel;
    if (ch == null) {
      throw StateError('No Principal or fallback governance channel available.');
    }
    return ch;
  }

  Future<RegisterSessionResponse> registerSession(
    RegisterSessionRequest request,
  ) {
    final client = DhtClientServiceClient(_requireEdge, options: _callOptions());
    return client.registerSession(request);
  }

  Future<FindUserSessionsResponse> findUserSessions(String targetUuid) {
    final client = DhtClientServiceClient(_requireEdge, options: _callOptions());
    return client.findUserSessions(
      FindUserSessionsRequest()..targetUuid = targetUuid,
    );
  }

  Future<TurnCredentialResponse> getTurnCredentials(
    TurnCredentialRequest request,
  ) {
    final client =
        TurnCredentialServiceClient(_requireEdge, options: _callOptions());
    return client.getTurnCredentials(request);
  }

  Future<SearchCnpjResponse> searchVerifiedCnpj(
    String query, {
    int limit = 20,
  }) {
    final client = DirectoryServiceClient(_requireEdge, options: _callOptions());
    return client.searchVerifiedCnpj(
      SearchCnpjRequest()
        ..query = query
        ..limit = limit,
    );
  }

  Future<VerificationBundle> submitVerification(
    SubmitVerificationRequest request,
  ) {
    final client = PrincipalGovernanceServiceClient(
      _requirePrincipal,
      options: _callOptions(),
    );
    return client.submitVerification(request);
  }

  Future<VerificationBundle> submitCpfRebind(SubmitCpfRebindRequest request) {
    final client = PrincipalGovernanceServiceClient(
      _requirePrincipal,
      options: _callOptions(),
    );
    return client.submitCpfRebind(request);
  }

  Future<VerificationBundle> getOrRenewCredential(GetCredentialRequest request) {
    final client = PrincipalGovernanceServiceClient(
      _requirePrincipal,
      options: _callOptions(),
    );
    return client.getOrRenewCredential(request);
  }

  Future<RevocationEvent> revokeDevice(RevokeDeviceRequest request) {
    final client = PrincipalGovernanceServiceClient(
      _requirePrincipal,
      options: _callOptions(),
    );
    return client.revokeDevice(request);
  }

  Future<VerificationBundle> portAccount(PortAccountRequest request) {
    final client = PrincipalGovernanceServiceClient(
      _requirePrincipal,
      options: _callOptions(),
    );
    return client.portAccount(request);
  }

  Future<CollaboratorAuthCert> authorizeCollaborator(
    AuthorizeCollaboratorRequest request,
  ) {
    final client = PrincipalGovernanceServiceClient(
      _requirePrincipal,
      options: _callOptions(),
    );
    return client.authorizeCollaborator(request);
  }

  Future<RevocationEvent> revokeCollaborator(
    RevokeCollaboratorRequest request,
  ) {
    final client = PrincipalGovernanceServiceClient(
      _requirePrincipal,
      options: _callOptions(),
    );
    return client.revokeCollaborator(request);
  }

  Stream<RevocationEvent> streamRevocations(int sinceRevocationSeq) {
    final client = PrincipalGovernanceServiceClient(_requirePrincipal);
    return client.streamRevocations(
      StreamRevocationsRequest()
        ..sinceRevocationSeq = $fixnum.Int64(sinceRevocationSeq),
    );
  }

  /// Opens (or reopens) the bidirectional `ClientStreamService.ConnectStream`
  /// with automatic reconnect backoff (`1s, 2s, 4s, 8s, 16s, 30s`).
  Future<void> openMessageStream({
    ClientEnvelope? initialHandshake,
    Future<void> Function()? onReconnected,
  }) async {
    _onReconnected = onReconnected;
    _streamActive = true;
    await _closeStreamOnly();

    final outgoing = StreamController<ClientEnvelope>();
    _outgoingController = outgoing;

    final client = ClientStreamServiceClient(_requireEdge);
    final responseStream = client.connect(outgoing.stream);

    if (initialHandshake != null) {
      outgoing.add(initialHandshake);
    }

    _incomingSub = responseStream.listen(
      (envelope) {
        _reconnectAttempt = 0;
        if (!_incomingBroadcast.isClosed) {
          _incomingBroadcast.add(envelope);
        }
      },
      onError: (_) => _scheduleReconnect(),
      onDone: () => _scheduleReconnect(),
      cancelOnError: false,
    );
  }

  Future<void> sendEnvelope(ClientEnvelope envelope) async {
    final outgoing = _outgoingController;
    if (outgoing == null || outgoing.isClosed) {
      throw StateError('Bidirectional gRPC message stream is not open.');
    }
    outgoing.add(envelope);
  }

  void _scheduleReconnect() {
    if (_manualDisconnect || !_streamActive || _edgeChannel == null) {
      return;
    }
    _reconnectTimer?.cancel();
    final steps = NetworkBootstrapConfig.defaultReconnectBackoffSeconds;
    final idx = _reconnectAttempt.clamp(0, steps.length - 1);
    final delaySeconds = steps[idx];
    _reconnectAttempt++;

    _reconnectTimer = Timer(Duration(seconds: delaySeconds), () async {
      if (_manualDisconnect || !_streamActive || _edgeChannel == null) {
        return;
      }
      try {
        await openMessageStream(onReconnected: _onReconnected);
        if (_onReconnected != null) {
          await _onReconnected!();
        }
      } catch (_) {
        _scheduleReconnect();
      }
    });
  }

  Future<void> _closeStreamOnly() async {
    await _incomingSub?.cancel();
    _incomingSub = null;
    await _outgoingController?.close();
    _outgoingController = null;
  }

  Future<void> disconnect() async {
    _manualDisconnect = true;
    _streamActive = false;
    _reconnectTimer?.cancel();
    _reconnectTimer = null;
    await _closeStreamOnly();

    final edge = _edgeChannel;
    final principal = _principalChannel;
    _edgeChannel = null;
    _principalChannel = null;
    _connectedAddress = null;
    _principalAddress = null;

    if (edge != null) {
      await edge.shutdown();
    }
    if (principal != null && !identical(principal, edge)) {
      await principal.shutdown();
    }
  }

  String? get connectedPrincipalAddress => _principalAddress;

  Future<void> dispose() async {
    await disconnect();
    if (!_incomingBroadcast.isClosed) {
      await _incomingBroadcast.close();
    }
  }
}
