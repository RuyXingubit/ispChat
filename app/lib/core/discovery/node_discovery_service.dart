import 'dart:async';
import 'dart:collection';

import 'package:app/core/config/network_bootstrap_config.dart';
import 'package:app/core/discovery/dns_srv_resolver.dart';
import 'package:app/core/discovery/tcp_latency_probe.dart';
import 'package:app/core/grpc/generated/ispchat.pb.dart';

/// Measured TCP latency result for a bootstrap or Home Node candidate.
class NodeLatencyResult {
  final String nodeAddress;
  final int latencyMs;
  final bool reachable;

  const NodeLatencyResult({
    required this.nodeAddress,
    required this.latencyMs,
    this.reachable = true,
  });
}

/// Abstracts TCP / QUIC RTT probing for hermetic testing.
abstract class LatencyProbe {
  Future<int?> measureLatencyMs(String nodeAddress, {Duration timeout});
}

/// Default TCP connect probe adapter delegating to [TcpLatencyProbe].
class DefaultTcpLatencyProbe implements LatencyProbe {
  final TcpLatencyProbe _tcpProbe = TcpLatencyProbe();

  DefaultTcpLatencyProbe();

  @override
  Future<int?> measureLatencyMs(
    String nodeAddress, {
    Duration timeout = const Duration(seconds: 2),
  }) async {
    return _tcpProbe.measureLatencyMs(nodeAddress).timeout(
          timeout,
          onTimeout: () => null,
        );
  }
}

/// Discovers, prioritizes, and latency-elects ispChat edge & Home Nodes via:
/// 1) Verified Home Node Claim / VerificationCredential pinning
/// 2) Local social/manual seed addresses
/// 3) DNS-over-HTTPS (DoH) SRV discovery (`_ispchat._tcp.ispchat.dev.br`)
/// 4) Principal fallback gateway (`principal.ispchat.dev.br:443`)
class NodeDiscoveryService {
  final LatencyProbe _probe;
  final DnsSrvResolver _dnsSrvResolver;
  final NetworkBootstrapConfig _config;
  final List<String> _socialSeeds = <String>[];

  NodeDiscoveryService({
    LatencyProbe? latencyProbe,
    DnsSrvResolver? dnsSrvResolver,
    NetworkBootstrapConfig? bootstrapConfig,
    List<String>? initialSeeds,
  })  : _probe = latencyProbe ?? DefaultTcpLatencyProbe(),
        _config = bootstrapConfig ?? const NetworkBootstrapConfig(),
        _dnsSrvResolver = dnsSrvResolver ??
            DnsSrvResolver(
              endpoints:
                  (bootstrapConfig ?? const NetworkBootstrapConfig()).dohEndpoints,
              timeout:
                  (bootstrapConfig ?? const NetworkBootstrapConfig()).dohTimeout,
            ) {
    if (initialSeeds != null) {
      for (final seed in initialSeeds) {
        injectSocialSeedLink(seed);
      }
    }
  }

  List<String> get seedNodes => List<String>.unmodifiable(_socialSeeds);

  String? _normalizeHostPort(String raw) {
    final trimmed = raw.trim();
    if (trimmed.isEmpty ||
        trimmed.contains(RegExp(r'\s')) ||
        trimmed.contains('/')) {
      return null;
    }
    if (trimmed.startsWith('[')) {
      final closeIdx = trimmed.indexOf(']');
      if (closeIdx <= 1 ||
          closeIdx + 2 > trimmed.length ||
          trimmed[closeIdx + 1] != ':') {
        return null;
      }
      final port = int.tryParse(trimmed.substring(closeIdx + 2));
      if (port == null || port < 1 || port > 65535) return null;
    } else {
      final colonIdx = trimmed.lastIndexOf(':');
      if (colonIdx <= 0 || colonIdx >= trimmed.length - 1) return null;
      if (trimmed.indexOf(':') != colonIdx) return null;
      final port = int.tryParse(trimmed.substring(colonIdx + 1));
      if (port == null || port < 1 || port > 65535) return null;
    }
    try {
      final parsed = TcpLatencyProbe.parseHostAndPort(trimmed);
      if (parsed.host.isEmpty || parsed.port < 1 || parsed.port > 65535) {
        return null;
      }
      return trimmed;
    } catch (_) {
      return null;
    }
  }

  /// Parses `ispchat://seed?node=...` / `ispchat://seed?addr=...` or bare
  /// `"host:port"` / `"[ipv6]:port"` strings and registers valid candidates.
  String? injectSocialSeedLink(String rawInput) {
    final trimmed = rawInput.trim();
    if (trimmed.isEmpty) {
      return null;
    }
    var candidate = trimmed;
    final parsedUri = Uri.tryParse(trimmed);
    if (parsedUri != null &&
        parsedUri.scheme == 'ispchat' &&
        parsedUri.host == 'seed') {
      candidate = (parsedUri.queryParameters['node'] ??
              parsedUri.queryParameters['addr'] ??
              parsedUri.queryParameters['host'] ??
              '')
          .trim();
    }
    final normalized = _normalizeHostPort(candidate);
    if (normalized == null) {
      return null;
    }
    if (!_socialSeeds.contains(normalized)) {
      _socialSeeds.add(normalized);
    }
    return normalized;
  }

  void addSeedNodes(Iterable<String> seeds) {
    for (final s in seeds) {
      injectSocialSeedLink(s);
    }
  }

  /// Resolves an ordered, deduplicated list of node candidates.
  Future<List<String>> resolveBootstrapCandidates({
    HomeNodeClaim? cachedHomeNodeClaim,
    VerificationCredential? verificationCredential,
    List<String>? extraSeeds,
    bool includeDnsSrv = true,
    bool includePrincipalFallback = true,
  }) async {
    final ordered = <String>{};

    void addIfValid(String? address) {
      if (address == null) return;
      final normalized = _normalizeHostPort(address);
      if (normalized != null) {
        ordered.add(normalized);
      }
    }

    // Priority 1: Verified Home Node pinning.
    addIfValid(cachedHomeNodeClaim?.homeNodeAddress);
    addIfValid(verificationCredential?.homeNodeAddress);

    // Priority 2: Caller & injected manual/social seeds.
    if (extraSeeds != null) {
      for (final s in extraSeeds) {
        addIfValid(s);
      }
    }
    for (final s in _socialSeeds) {
      addIfValid(s);
    }

    // Priority 3: DNS-over-HTTPS SRV discovery.
    if (includeDnsSrv) {
      final srvRecords = await _dnsSrvResolver.resolveSrvTargets(
        _config.srvDomain,
      );
      for (final srv in srvRecords) {
        addIfValid(srv.hostPort);
      }
    }

    // Priority 4: Principal fallback address.
    if (includePrincipalFallback) {
      addIfValid(_config.principalFallbackAddress);
    }

    return ordered.toList(growable: false);
  }

  /// Measures latency across [candidates] and returns the fastest reachable
  /// node, or throws [StateError] when none succeed.
  Future<NodeLatencyResult> selectFastestNode(
    List<String> candidates, {
    Duration probeTimeout = const Duration(seconds: 2),
  }) async {
    final unique = LinkedHashSet<String>.from(
      candidates.map((c) => c.trim()).where((c) => c.isNotEmpty),
    ).toList(growable: false);
    if (unique.isEmpty) {
      throw StateError('No valid node candidates available for discovery.');
    }

    final probes = unique.map((addr) async {
      final rtt = await _probe.measureLatencyMs(addr, timeout: probeTimeout);
      if (rtt == null) {
        return null;
      }
      return NodeLatencyResult(
        nodeAddress: addr,
        latencyMs: rtt,
        reachable: true,
      );
    });

    final results = (await Future.wait(probes))
        .whereType<NodeLatencyResult>()
        .toList()
      ..sort((a, b) => a.latencyMs.compareTo(b.latencyMs));

    if (results.isEmpty) {
      throw StateError(
        'None of the ${unique.length} candidate nodes answered TCP probe.',
      );
    }
    return results.first;
  }

  /// Full discovery + latency election using Home Node Claim, seeds, DoH SRV,
  /// and Principal fallback.
  Future<NodeLatencyResult> electFastestBootstrapNode({
    HomeNodeClaim? cachedHomeNodeClaim,
    VerificationCredential? verificationCredential,
    List<String>? extraSeeds,
    bool includeDnsSrv = true,
    bool includePrincipalFallback = true,
    Duration probeTimeout = const Duration(seconds: 2),
  }) async {
    final candidates = await resolveBootstrapCandidates(
      cachedHomeNodeClaim: cachedHomeNodeClaim,
      verificationCredential: verificationCredential,
      extraSeeds: extraSeeds,
      includeDnsSrv: includeDnsSrv,
      includePrincipalFallback: includePrincipalFallback,
    );
    return selectFastestNode(candidates, probeTimeout: probeTimeout);
  }
}
