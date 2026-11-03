/// Resultado de medicao de latencia (ping) para um no ISP Chat candidato.
class NodeLatencyResult {
  final String nodeAddress;
  final int latencyMs;
  final bool reachable;

  const NodeLatencyResult({
    required this.nodeAddress,
    required this.latencyMs,
    required this.reachable,
  });
}

/// Assinatura de funcao para teste de latencia (permite injecao em testes unitarios).
typedef LatencyProbe = Future<int?> Function(String nodeAddress);

/// Servico responsavel pelo Onboarding sem atrito, eleicao do No com menor ping (Fallback)
/// e injecao de Nos via Bootstrap Social (ispchat://seed?ip=X.X.X.X).
class NodeDiscoveryService {
  static const int defaultGrpcPort = 443;

  final List<String> _candidateNodes = <String>[];
  final LatencyProbe latencyProbe;

  NodeDiscoveryService({
    List<String>? initialSeedNodes,
    required this.latencyProbe,
  }) {
    if (initialSeedNodes != null) {
      for (final String node in initialSeedNodes) {
        addCandidateNode(node);
      }
    }
  }

  List<String> get candidateNodes => List<String>.unmodifiable(_candidateNodes);

  /// Adiciona um endereco de no na lista de candidatos se ainda nao estiver presente.
  void addCandidateNode(String rawAddress) {
    final String normalized = rawAddress.trim();
    if (normalized.isEmpty) {
      return;
    }
    if (!_candidateNodes.contains(normalized)) {
      _candidateNodes.add(normalized);
    }
  }

  /// Processa um link de Bootstrap Social no formato `ispchat://seed?ip=X.X.X.X`
  /// e injeta o novo no no topo da lista de candidatos.
  String injectSocialSeedLink(String seedUriString) {
    final Uri uri = Uri.parse(seedUriString.trim());
    if (uri.scheme != 'ispchat' || uri.host != 'seed') {
      throw const FormatException('Link de seed invalido. Formato esperado: ispchat://seed?ip=X.X.X.X');
    }

    final String? ipOrHost = uri.queryParameters['ip'];
    if (ipOrHost == null || ipOrHost.trim().isEmpty) {
      throw const FormatException('Parametro ip obrigatorio no link ispchat://seed?ip=...');
    }

    final String cleaned = ipOrHost.trim();
    _candidateNodes.remove(cleaned);
    _candidateNodes.insert(0, cleaned);
    return cleaned;
  }

  /// Percorre a matriz de nos candidatos, mede a latencia de cada um e elege
  /// o no acessivel com o menor ping absoluto (Fallback de Onboarding).
  Future<NodeLatencyResult?> selectFastestNode() async {
    if (_candidateNodes.isEmpty) {
      return null;
    }

    final List<NodeLatencyResult> results = <NodeLatencyResult>[];
    for (final String address in _candidateNodes) {
      try {
        final int? rtt = await latencyProbe(address);
        if (rtt != null && rtt >= 0) {
          results.add(NodeLatencyResult(
            nodeAddress: address,
            latencyMs: rtt,
            reachable: true,
          ));
        }
      } catch (_) {
        // No inacessivel ou bloqueado; ignora e tenta o proximo candidato
      }
    }

    if (results.isEmpty) {
      return null;
    }

    results.sort((NodeLatencyResult a, NodeLatencyResult b) => a.latencyMs.compareTo(b.latencyMs));
    return results.first;
  }
}
