import 'package:app/core/discovery/node_discovery_service.dart';
import 'package:app/core/security/blocklist_service.dart';
import 'package:flutter_test/flutter_test.dart';

class _MapLatencyProbe implements LatencyProbe {
  final Map<String, int?> rtts;

  _MapLatencyProbe(this.rtts);

  @override
  Future<int?> measureLatencyMs(
    String nodeAddress, {
    Duration timeout = const Duration(seconds: 2),
  }) async {
    return rtts[nodeAddress];
  }
}

void main() {
  test('NodeDiscoveryService parses social seed links and selects lowest RTT',
      () async {
    final discovery = NodeDiscoveryService(
      latencyProbe: _MapLatencyProbe(<String, int?>{
        'slow.isp.br:8443': 140,
        '[2001:db8::1]:8443': 14,
        'dead.isp.br:8443': null,
      }),
    );

    expect(
      discovery.injectSocialSeedLink('ispchat://seed?node=slow.isp.br:8443'),
      'slow.isp.br:8443',
    );
    expect(
      discovery.injectSocialSeedLink('[2001:db8::1]:8443'),
      '[2001:db8::1]:8443',
    );
    expect(discovery.injectSocialSeedLink('not-a-valid-host'), isNull);

    final elected = await discovery.selectFastestNode([
      'dead.isp.br:8443',
      'slow.isp.br:8443',
      '[2001:db8::1]:8443',
    ]);
    expect(elected.nodeAddress, '[2001:db8::1]:8443');
    expect(elected.latencyMs, 14);
  });

  test('BlocklistService blocks and unblocks UUIDs deterministically', () {
    final blocklist = BlocklistService();
    const peer = '018f0000-0000-7000-8000-000000000099';
    expect(blocklist.isBlocked(peer), isFalse);

    blocklist.blockUuid('  $peer  ');
    expect(blocklist.isBlocked(peer), isTrue);
    expect(blocklist.isBlocked(peer.toUpperCase()), isTrue);

    blocklist.unblockUuid(peer);
    expect(blocklist.isBlocked(peer), isFalse);
  });
}
