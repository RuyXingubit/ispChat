import 'package:app/main.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('Deve renderizar a interface sem overflow em tela estreita e permitir bloqueio e seed injection',
      (WidgetTester tester) async {
    // Simula tela estreita (320x568) para testar tolerancia zero a overflow
    tester.view.physicalSize = const Size(320, 568);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(const IspChatApp());

    expect(find.text('1. Identidade Auto-Soberana (UUIDv7 + BIP39)'), findsOneWidget);
    expect(find.text('Nenhum UUIDv7 bloqueado localmente.'), findsOneWidget);

    // Injeta um link social de seed
    await tester.ensureVisible(find.byKey(const Key('input_seed_link')));
    await tester.enterText(
      find.byKey(const Key('input_seed_link')),
      'ispchat://seed?ip=203.0.113.50',
    );
    await tester.ensureVisible(find.byKey(const Key('btn_inject_seed')));
    await tester.tap(find.byKey(const Key('btn_inject_seed')));
    await tester.pump();

    expect(find.textContaining('203.0.113.50'), findsWidgets);

    // Bloqueia um UUIDv7 na blocklist local
    const String targetUuid = '01950000-0000-7000-8000-000000000999';
    await tester.ensureVisible(find.byKey(const Key('input_block_uuid')));
    await tester.enterText(find.byKey(const Key('input_block_uuid')), targetUuid);
    await tester.ensureVisible(find.byKey(const Key('btn_block_uuid')));
    await tester.tap(find.byKey(const Key('btn_block_uuid')));
    await tester.pump();

    expect(find.text(targetUuid), findsOneWidget);
  });
}
