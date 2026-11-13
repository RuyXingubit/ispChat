import 'package:app/core/storage/secure_local_vault.dart';
import 'package:app/main.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('IspChatApp renders clean empty state without fake data or overflow',
      (tester) async {
    final store = InMemorySecureKeyValueStore();

    for (final size in const [Size(320, 568), Size(1024, 768)]) {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1.0;

      await tester.pumpWidget(IspChatApp(customSecureStore: store));
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('btn_generate_identity')), findsOneWidget);
      expect(find.text('Nenhum contato adicionado ainda.'), findsOneWidget);
      expect(find.byKey(const Key('txt_connected_node_host')), findsOneWidget);
      expect(tester.takeException(), isNull);
    }

    tester.view.resetPhysicalSize();
    tester.view.resetDevicePixelRatio();
  });
}
