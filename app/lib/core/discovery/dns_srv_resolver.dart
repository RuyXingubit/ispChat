import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';
import 'dart:typed_data';

import 'package:app/core/config/network_bootstrap_config.dart';

/// Parsed DNS SRV record (RFC 2782).
class SrvRecord {
  final int priority;
  final int weight;
  final int port;
  final String target;

  const SrvRecord({
    required this.priority,
    required this.weight,
    required this.port,
    required this.target,
  });

  /// Canonical `"host:port"` endpoint string (wrapping IPv6 literals in brackets).
  String get hostPort {
    final cleanTarget = target.endsWith('.')
        ? target.substring(0, target.length - 1)
        : target;
    if (cleanTarget.contains(':') && !cleanTarget.startsWith('[')) {
      return '[$cleanTarget]:$port';
    }
    return '$cleanTarget:$port';
  }

  @override
  bool operator ==(Object other) {
    return other is SrvRecord &&
        other.priority == priority &&
        other.weight == weight &&
        other.port == port &&
        other.target == target;
  }

  @override
  int get hashCode => Object.hash(priority, weight, port, target);
}

/// Minimal HTTP response structure for injectable DoH transport testing.
class DohHttpResponse {
  final int statusCode;
  final Uint8List bodyBytes;

  const DohHttpResponse({
    required this.statusCode,
    required this.bodyBytes,
  });
}

typedef DohHttpFetcher = Future<DohHttpResponse> Function(
  Uri uri, {
  required Map<String, String> headers,
  Uint8List? postBody,
  required Duration timeout,
});

/// Resolves DNS SRV records exclusively via DNS-over-HTTPS (DoH), trying
/// DoH JSON (`application/dns-json`) and falling back to RFC 8484 wireformat
/// (`application/dns-message`).
class DnsSrvResolver {
  static const int _dnsTypeSrv = 33;
  static const int _dnsClassIn = 1;

  final List<String> endpoints;
  final Duration timeout;
  final DohHttpFetcher _fetcher;
  final Random _random;

  DnsSrvResolver({
    List<String>? endpoints,
    this.timeout = const Duration(seconds: 5),
    DohHttpFetcher? fetcher,
    Random? random,
  })  : endpoints = endpoints ?? NetworkBootstrapConfig.defaultDohResolvers,
        _fetcher = fetcher ?? _defaultHttpFetcher,
        _random = random ?? Random.secure();

  /// Resolves [srvDomain] across configured DoH endpoints and orders targets
  /// according to RFC 2782 priority and weighted selection.
  Future<List<SrvRecord>> resolveSrvTargets(String srvDomain) async {
    final cleanDomain = srvDomain.trim().replaceAll(RegExp(r'\.$'), '');
    if (cleanDomain.isEmpty) {
      return const <SrvRecord>[];
    }

    for (final endpoint in endpoints) {
      final base = Uri.tryParse(endpoint.trim());
      if (base == null || base.scheme != 'https') {
        continue;
      }

      // 1. Try DoH JSON (supported by dns.google and cloudflare-dns.com).
      try {
        final jsonUri = base.replace(
          queryParameters: <String, String>{
            ...base.queryParameters,
            'name': cleanDomain,
            'type': 'SRV',
          },
        );
        final res = await _fetcher(
          jsonUri,
          headers: const <String, String>{'Accept': 'application/dns-json'},
          timeout: timeout,
        );
        if (res.statusCode == 200) {
          final parsed = parseJsonDohSrvResponse(utf8.decode(res.bodyBytes));
          if (parsed.isNotEmpty) {
            return sortSrvRecords(parsed, random: _random);
          }
        }
      } catch (_) {
        // Fall through to RFC 8484 binary DNS wireformat.
      }

      // 2. Fallback to RFC 8484 binary wireformat POST.
      try {
        final wireQuery = buildSrvDnsWireQuery(cleanDomain);
        final res = await _fetcher(
          base,
          headers: const <String, String>{
            'Accept': 'application/dns-message',
            'Content-Type': 'application/dns-message',
          },
          postBody: wireQuery,
          timeout: timeout,
        );
        if (res.statusCode == 200) {
          final parsed = parseSrvDnsWireResponse(res.bodyBytes);
          if (parsed.isNotEmpty) {
            return sortSrvRecords(parsed, random: _random);
          }
        }
      } catch (_) {
        // Try next DoH endpoint.
      }
    }

    return const <SrvRecord>[];
  }

  /// Parses JSON DoH SRV responses (`"Answer": [{"type": 33, "data": "10 50 8443 node1.isp.br."}]`).
  static List<SrvRecord> parseJsonDohSrvResponse(String jsonBody) {
    final decoded = jsonDecode(jsonBody);
    if (decoded is! Map<String, dynamic>) {
      return const <SrvRecord>[];
    }
    final answers = decoded['Answer'];
    if (answers is! List) {
      return const <SrvRecord>[];
    }
    final records = <SrvRecord>[];
    for (final item in answers) {
      if (item is! Map<String, dynamic>) continue;
      if (item['type'] != _dnsTypeSrv) continue;
      final data = item['data'];
      if (data is! String) continue;
      final record = parseSrvRdataText(data);
      if (record != null) {
        records.add(record);
      }
    }
    return records;
  }

  static SrvRecord? parseSrvRdataText(String raw) {
    final parts = raw.trim().split(RegExp(r'\s+'));
    if (parts.length != 4) {
      return null;
    }
    final priority = int.tryParse(parts[0]);
    final weight = int.tryParse(parts[1]);
    final port = int.tryParse(parts[2]);
    final rawTarget = parts[3].trim();
    if (priority == null ||
        weight == null ||
        port == null ||
        port <= 0 ||
        port > 65535 ||
        rawTarget.isEmpty ||
        rawTarget == '.') {
      return null;
    }
    final cleanTarget = rawTarget.endsWith('.')
        ? rawTarget.substring(0, rawTarget.length - 1)
        : rawTarget;
    return SrvRecord(
      priority: priority,
      weight: weight,
      port: port,
      target: cleanTarget,
    );
  }

  /// Builds an RFC 1035 / RFC 8484 DNS query packet for `QTYPE=SRV (33)`.
  static Uint8List buildSrvDnsWireQuery(String domain) {
    final out = BytesBuilder(copy: false);
    // Header: ID=0, Flags=0x0100 (RD=1), QDCOUNT=1, AN/NS/AR=0
    out.add(const <int>[
      0x00, 0x00,
      0x01, 0x00,
      0x00, 0x01,
      0x00, 0x00,
      0x00, 0x00,
      0x00, 0x00,
    ]);
    final labels = domain
        .trim()
        .replaceAll(RegExp(r'\.$'), '')
        .split('.')
        .where((l) => l.isNotEmpty);
    for (final label in labels) {
      final bytes = utf8.encode(label);
      if (bytes.length > 63) {
        throw ArgumentError('DNS label exceeds 63 bytes: $label');
      }
      out.addByte(bytes.length);
      out.add(bytes);
    }
    out.addByte(0); // root label
    // QTYPE=33 (SRV), QCLASS=1 (IN)
    out.add(const <int>[0x00, _dnsTypeSrv, 0x00, _dnsClassIn]);
    return out.toBytes();
  }

  /// Parses an RFC 8484 binary DNS response packet extracting SRV records.
  static List<SrvRecord> parseSrvDnsWireResponse(Uint8List packet) {
    if (packet.length < 12) {
      return const <SrvRecord>[];
    }
    final data = ByteData.sublistView(packet);
    final qdCount = data.getUint16(4, Endian.big);
    final anCount = data.getUint16(6, Endian.big);
    var offset = 12;

    // Skip Question section.
    for (var q = 0; q < qdCount; q++) {
      final nameRes = _readDnsName(packet, offset);
      offset = nameRes.nextOffset + 4; // QTYPE + QCLASS
      if (offset > packet.length) {
        return const <SrvRecord>[];
      }
    }

    final records = <SrvRecord>[];
    for (var a = 0; a < anCount; a++) {
      if (offset >= packet.length) break;
      final nameRes = _readDnsName(packet, offset);
      offset = nameRes.nextOffset;
      if (offset + 10 > packet.length) break;

      final rrType = data.getUint16(offset, Endian.big);
      final rdLength = data.getUint16(offset + 8, Endian.big);
      offset += 10;
      if (offset + rdLength > packet.length) break;

      if (rrType == _dnsTypeSrv && rdLength >= 7) {
        final priority = data.getUint16(offset, Endian.big);
        final weight = data.getUint16(offset + 2, Endian.big);
        final port = data.getUint16(offset + 4, Endian.big);
        final targetRes = _readDnsName(packet, offset + 6);
        final target = targetRes.name.endsWith('.')
            ? targetRes.name.substring(0, targetRes.name.length - 1)
            : targetRes.name;
        if (port > 0 && port <= 65535 && target.isNotEmpty) {
          records.add(
            SrvRecord(
              priority: priority,
              weight: weight,
              port: port,
              target: target,
            ),
          );
        }
      }
      offset += rdLength;
    }

    return records;
  }

  /// Applies RFC 2782 priority sorting and weighted probabilistic ordering
  /// within equal-priority groups.
  static List<SrvRecord> sortSrvRecords(
    List<SrvRecord> input, {
    Random? random,
  }) {
    if (input.length <= 1) {
      return List<SrvRecord>.from(input);
    }
    final rng = random ?? Random.secure();
    final byPriority = <int, List<SrvRecord>>{};
    for (final rec in input) {
      byPriority.putIfAbsent(rec.priority, () => <SrvRecord>[]).add(rec);
    }
    final sortedPriorities = byPriority.keys.toList()..sort();
    final ordered = <SrvRecord>[];

    for (final priority in sortedPriorities) {
      final pool = List<SrvRecord>.from(byPriority[priority]!);
      while (pool.isNotEmpty) {
        var totalWeight = 0;
        for (final rec in pool) {
          totalWeight += rec.weight > 0 ? rec.weight : 0;
        }
        if (totalWeight <= 0) {
          final pick = pool.length == 1 ? 0 : rng.nextInt(pool.length);
          ordered.add(pool.removeAt(pick));
          continue;
        }
        var target = rng.nextInt(totalWeight) + 1;
        var chosenIdx = 0;
        for (var i = 0; i < pool.length; i++) {
          target -= pool[i].weight > 0 ? pool[i].weight : 0;
          if (target <= 0) {
            chosenIdx = i;
            break;
          }
        }
        ordered.add(pool.removeAt(chosenIdx));
      }
    }
    return ordered;
  }

  static _DnsNameReadResult _readDnsName(Uint8List packet, int startOffset) {
    final labels = <String>[];
    var offset = startOffset;
    var jumped = false;
    var returnOffset = startOffset;
    var pointerHops = 0;

    while (offset < packet.length) {
      final len = packet[offset];
      if (len == 0) {
        if (!jumped) {
          returnOffset = offset + 1;
        }
        break;
      }
      if ((len & 0xC0) == 0xC0) {
        if (offset + 1 >= packet.length) {
          throw const FormatException('Truncated DNS compression pointer.');
        }
        pointerHops++;
        if (pointerHops > 16) {
          throw const FormatException('DNS compression pointer loop detected.');
        }
        final ptr = ((len & 0x3F) << 8) | packet[offset + 1];
        if (!jumped) {
          returnOffset = offset + 2;
        }
        offset = ptr;
        jumped = true;
        continue;
      }
      if ((len & 0xC0) != 0) {
        throw const FormatException('Invalid DNS label tag.');
      }
      offset++;
      if (offset + len > packet.length) {
        throw const FormatException('Truncated DNS label.');
      }
      labels.add(utf8.decode(packet.sublist(offset, offset + len)));
      offset += len;
      if (!jumped) {
        returnOffset = offset;
      }
    }

    return _DnsNameReadResult(
      name: labels.join('.'),
      nextOffset: returnOffset,
    );
  }

  static Future<DohHttpResponse> _defaultHttpFetcher(
    Uri uri, {
    required Map<String, String> headers,
    Uint8List? postBody,
    required Duration timeout,
  }) async {
    final client = HttpClient()..connectionTimeout = timeout;
    try {
      final request = postBody != null
          ? await client.postUrl(uri).timeout(timeout)
          : await client.getUrl(uri).timeout(timeout);
      headers.forEach((k, v) => request.headers.set(k, v));
      if (postBody != null) {
        request.add(postBody);
      }
      final response = await request.close().timeout(timeout);
      final builder = BytesBuilder(copy: false);
      await for (final chunk in response.timeout(timeout)) {
        builder.add(chunk);
      }
      return DohHttpResponse(
        statusCode: response.statusCode,
        bodyBytes: builder.toBytes(),
      );
    } finally {
      client.close(force: true);
    }
  }
}

class _DnsNameReadResult {
  final String name;
  final int nextOffset;

  const _DnsNameReadResult({
    required this.name,
    required this.nextOffset,
  });
}
