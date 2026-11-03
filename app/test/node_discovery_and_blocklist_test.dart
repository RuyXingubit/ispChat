import 'package:app/core/discovery/node_discovery_service.dart';
import 'package:app/core/security/blocklist_service.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('NodeDiscoveryService (Ping Fallback & Social Bootstrap)', () {
    test('Deve eleger o no com a menor latencia (ping) e ignorar nos inacessiveis', () async {
      final Map<String, int?> simulatedPings = <String, int?>{
        'node-offline.isp.com': null,
        'node-slow.isp.com': 145,
        'node-fast.isp.com': 12,
        'node-medium.isp.com': 48,
      };

      final NodeDiscoveryService discovery = NodeDiscoveryService(
        initialSeedNodes: simulatedPings.keys.toList(),
        latencyProbe: (String address) async => simulatedPings[address],
      );

      final NodeLatencyResult? winner = await discovery.selectFastestNode();
      expect(winner, isNotNull);
      expect(winner!.nodeAddress, equals('node-fast.isp.com'));
      expect(winner.latencyMs, equals(12));
    });

    test('Deve injetar novo IP no topo dos candidatos via link social ispchat://seed?ip=...', () {
      final NodeDiscoveryService discovery = NodeDiscoveryService(
        initialSeedNodes: <String>['203.0.113.10'],
        latencyProbe: (_) async => 20,
      );

      final String injected = discovery.injectSocialSeedLink('ispchat://seed?ip=198.51.100.77');
      expect(injected, equals('198.51.100.77'));
      expect(discovery.candidateNodes.first, equals('198.51.100.77'));
    });
  });

  group('BlocklistService (Anti-Assedio Local)', () {
    test('Deve bloquear silenciosamente chamadas e mensagens de UUIDv7 na blocklist local', () {
      final BlocklistService blocklist = BlocklistService();
      const String spammerUuid = '01950000-0000-7000-8000-000000000666';
      const String friendUuid = '01950000-0000-7000-8000-000000000777';

      blocklist.blockUuid(spammerUuid);

      expect(blocklist.isBlocked(spammerUuid), isTrue);
      expect(blocklist.shouldAllowIncomingEvent(spammerUuid), isFalse);
      expect(blocklist.shouldAllowIncomingEvent(friendUuid), isTrue);

      blocklist.unblockUuid(spammerUuid);
      expect(blocklist.shouldAllowIncomingEvent(spammerUuid), isTrue);
    });
  });
}
