import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:app/core/crypto/identity_crypto_service.dart';
import 'package:app/core/discovery/dns_srv_resolver.dart';
import 'package:app/core/discovery/node_discovery_service.dart';
import 'package:app/core/grpc/generated/ispchat.pbgrpc.dart';
import 'package:app/core/grpc/ispchat_node_client.dart';
import 'package:app/core/storage/secure_local_vault.dart';
import 'package:app/ui/verification_and_directory_screen.dart';
import 'package:cryptography/cryptography.dart';
import 'package:fixnum/fixnum.dart' as $fixnum;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

Uint8List _buildMockDnsWireSrvResponse({
  required String queryDomain,
  required List<SrvRecord> records,
}) {
  final out = BytesBuilder();
  // Header: id=0, flags=0x8180, qd=1, an=records.length, ns=0, ar=0
  out.add(<int>[
    0x00, 0x00,
    0x81, 0x80,
    0x00, 0x01,
    (records.length >> 8) & 0xFF, records.length & 0xFF,
    0x00, 0x00,
    0x00, 0x00,
  ]);
  for (final label in queryDomain.split('.').where((e) => e.isNotEmpty)) {
    final b = utf8.encode(label);
    out.addByte(b.length);
    out.add(b);
  }
  out.add(<int>[0x00, 0x00, 33, 0x00, 1]);

  for (final r in records) {
    // Compression pointer back to QNAME at offset 12 (0xC00C)
    out.add(const <int>[0xC0, 0x0C]);
    // TYPE=33 (SRV), CLASS=1 (IN), TTL=60
    out.add(const <int>[0x00, 33, 0x00, 0x01, 0x00, 0x00, 0x00, 0x3C]);
    final targetBuf = BytesBuilder();
    targetBuf.add(<int>[
      (r.priority >> 8) & 0xFF, r.priority & 0xFF,
      (r.weight >> 8) & 0xFF, r.weight & 0xFF,
      (r.port >> 8) & 0xFF, r.port & 0xFF,
    ]);
    for (final label in r.target.split('.').where((e) => e.isNotEmpty)) {
      final b = utf8.encode(label);
      targetBuf.addByte(b.length);
      targetBuf.add(b);
    }
    targetBuf.addByte(0x00);
    final rdata = targetBuf.toBytes();
    out.add(<int>[(rdata.length >> 8) & 0xFF, rdata.length & 0xFF]);
    out.add(rdata);
  }
  return out.toBytes();
}

class _InProcessDirectoryNodeClient extends IspChatNodeClient {
  final List<VerifiedCorporateEntry> entries;

  _InProcessDirectoryNodeClient(this.entries);

  @override
  bool get isConnected => true;

  @override
  String? get connectedNodeAddress => 'node1.isp.br:8443';

  @override
  Future<SearchCnpjResponse> searchVerifiedCnpj(
    String query, {
    int limit = 20,
  }) async {
    final q = query.trim().toLowerCase();
    final filtered = entries.where(
      (e) =>
          e.cnpj.toLowerCase().contains(q) ||
          e.displayName.toLowerCase().contains(q),
    );
    return SearchCnpjResponse()..entries.addAll(filtered);
  }
}

class _FakeLatencyProbe implements LatencyProbe {
  final Map<String, int?> latencies;

  _FakeLatencyProbe(this.latencies);

  @override
  Future<int?> measureLatencyMs(
    String nodeAddress, {
    Duration timeout = const Duration(seconds: 2),
  }) async {
    return latencies[nodeAddress];
  }
}

void main() {
  test('DnsSrvResolver parses DoH JSON & sorts RFC 2782 records', () async {
    final resolver = DnsSrvResolver(
      endpoints: const ['https://doh.mock/dns-query'],
      random: Random(0),
      fetcher: (uri, {required headers, postBody, required timeout}) async {
        expect(headers['Accept'], 'application/dns-json');
        final jsonStr = jsonEncode(<String, dynamic>{
          'Status': 0,
          'Answer': <Map<String, dynamic>>[
            <String, dynamic>{'type': 33, 'data': '20 10 8443 backup.isp.br.'},
            <String, dynamic>{'type': 33, 'data': '10 80 8443 primary.isp.br.'},
            <String, dynamic>{'type': 16, 'data': 'ignored-txt'},
          ],
        });
        return DohHttpResponse(
          statusCode: 200,
          bodyBytes: Uint8List.fromList(utf8.encode(jsonStr)),
        );
      },
    );

    final targets = await resolver.resolveSrvTargets('_ispchat._tcp.ispchat.dev.br');
    expect(targets.map((e) => e.hostPort).toList(), [
      'primary.isp.br:8443',
      'backup.isp.br:8443',
    ]);
  });

  test('DnsSrvResolver falls back to RFC 8484 DNS wireformat when JSON unavailable',
      () async {
    final wirePacket = _buildMockDnsWireSrvResponse(
      queryDomain: '_ispchat._tcp.ispchat.dev.br',
      records: const [
        SrvRecord(priority: 5, weight: 100, port: 443, target: 'edge-a.isp.br'),
        SrvRecord(priority: 15, weight: 10, port: 8443, target: 'edge-b.isp.br'),
      ],
    );

    final resolver = DnsSrvResolver(
      endpoints: const ['https://doh.wire/dns-query'],
      random: Random(1),
      fetcher: (uri, {required headers, postBody, required timeout}) async {
        if (postBody == null) {
          return DohHttpResponse(statusCode: 406, bodyBytes: Uint8List(0));
        }
        expect(headers['Content-Type'], 'application/dns-message');
        return DohHttpResponse(statusCode: 200, bodyBytes: wirePacket);
      },
    );

    final targets = await resolver.resolveSrvTargets('_ispchat._tcp.ispchat.dev.br');
    expect(targets.length, 2);
    expect(targets.first.hostPort, 'edge-a.isp.br:443');
    expect(targets.last.hostPort, 'edge-b.isp.br:8443');
  });

  test('NodeDiscoveryService preserves HomeNode > Seed > DoH SRV > Fallback order',
      () async {
    final resolver = DnsSrvResolver(
      endpoints: const ['https://doh.mock/dns-query'],
      fetcher: (uri, {required headers, postBody, required timeout}) async {
        final jsonStr = jsonEncode(<String, dynamic>{
          'Status': 0,
          'Answer': <Map<String, dynamic>>[
            <String, dynamic>{'type': 33, 'data': '10 50 8443 srv1.isp.br.'},
          ],
        });
        return DohHttpResponse(
          statusCode: 200,
          bodyBytes: Uint8List.fromList(utf8.encode(jsonStr)),
        );
      },
    );

    final discovery = NodeDiscoveryService(
      dnsSrvResolver: resolver,
      latencyProbe: _FakeLatencyProbe(<String, int?>{
        'home.isp.br:8443': 90,
        'seed.isp.br:8443': 40,
        'srv1.isp.br:8443': 18,
        'principal.ispchat.dev.br:443': 120,
      }),
    );
    discovery.injectSocialSeedLink('ispchat://seed?node=seed.isp.br:8443');

    final candidates = await discovery.resolveBootstrapCandidates(
      cachedHomeNodeClaim: HomeNodeClaim()..homeNodeAddress = 'home.isp.br:8443',
    );
    expect(candidates, [
      'home.isp.br:8443',
      'seed.isp.br:8443',
      'srv1.isp.br:8443',
      'principal.ispchat.dev.br:443',
    ]);

    final fastest = await discovery.selectFastestNode(candidates);
    expect(fastest.nodeAddress, 'srv1.isp.br:8443');
    expect(fastest.latencyMs, 18);
  });

  testWidgets('Corporate CNPJ Directory searches live gRPC and adds verified contact',
      (tester) async {
    const crypto = IdentityCryptoService();
    final govRoot = await Ed25519().newKeyPair();
    final govRootPub =
        Uint8List.fromList((await govRoot.extractPublicKey()).bytes);
    final prinEd = await Ed25519().newKeyPair();
    final corp = await crypto.generateNewIdentity();
    final now = DateTime.now().millisecondsSinceEpoch;

    final pCert = PrincipalSigningCert()
      ..certId = IdentityCryptoService.generateTemporalUuidV7()
      ..principalEd25519Pub = (await prinEd.extractPublicKey()).bytes
      ..escrowX25519Pub = List<int>.filled(32, 7)
      ..issuedAtMs = $fixnum.Int64(now)
      ..expiresAtMs = $fixnum.Int64(now + 365 * 86400000);
    pCert.rootSignature = await crypto.signRaw(
      govRoot,
      CanonicalTranscripts.buildPrincipalSigningCertTranscript(pCert),
    );

    final vc = VerificationCredential()
      ..credentialId = IdentityCryptoService.generateTemporalUuidV7()
      ..userUuid = corp.userUuid
      ..rootEd25519Pub = corp.ed25519PublicKeyBytes
      ..rootX25519Pub = corp.x25519PublicKeyBytes
      ..docType = DocumentType.CNPJ
      ..publicCnpj = '12ABC34501DE35'
      ..publicDisplayName = 'Fibra Sul Ltda'
      ..verifyingIspCnpj = '11222333000181'
      ..verifyingIspName = 'Fibra Verifier'
      ..homeNodeAddress = 'node1.isp.br:8443'
      ..routingEpoch = $fixnum.Int64(1)
      ..issuedAtMs = $fixnum.Int64(now)
      ..expiresAtMs = $fixnum.Int64(now + 90 * 86400000)
      ..principalCert = pCert;
    vc.principalSignature = await crypto.signRaw(
      prinEd,
      CanonicalTranscripts.buildVerificationCredentialTranscript(vc),
    );

    final entry = VerifiedCorporateEntry()
      ..corporateUuid = corp.userUuid
      ..cnpj = '12ABC34501DE35'
      ..displayName = 'Fibra Sul Ltda'
      ..homeNodeAddress = 'node1.isp.br:8443'
      ..rootEd25519Pub = corp.ed25519PublicKeyBytes
      ..rootX25519Pub = corp.x25519PublicKeyBytes
      ..verificationCredential = vc;

    final client = _InProcessDirectoryNodeClient(<VerifiedCorporateEntry>[entry]);
    final vault = SecureLocalVault(store: InMemorySecureKeyValueStore());

    try {
      await tester.pumpWidget(
        MaterialApp(
          home: VerificationAndDirectoryScreen(
            nodeClient: client,
            vault: vault,
            crypto: crypto,
            pinnedRootPubOverride: govRootPub,
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('txt_directory_empty')), findsOneWidget);

      await tester.enterText(
        find.byKey(const Key('input_cnpj_search')),
        '12ABC345',
      );
      await tester.tap(find.byKey(const Key('btn_search_cnpj')));
      await tester.pumpAndSettle();

      expect(find.text('Fibra Sul Ltda'), findsOneWidget);
      await tester.tap(
        find.byKey(Key('btn_add_directory_contact_${corp.userUuid}')),
      );
      await tester.pumpAndSettle();

      final saved = await vault.loadContacts();
      expect(saved.length, 1);
      expect(saved.first.isVerified, isTrue);
      expect(saved.first.publicCnpj, '12ABC34501DE35');
      expect(tester.takeException(), isNull);
    } finally {
      await client.dispose();
    }
  });
}
