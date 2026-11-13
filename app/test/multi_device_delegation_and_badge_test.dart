import 'package:app/core/crypto/identity_crypto_service.dart';
import 'package:app/core/grpc/generated/ispchat.pbgrpc.dart';
import 'package:app/core/grpc/ispchat_node_client.dart';
import 'package:app/core/storage/secure_local_vault.dart';
import 'package:app/main.dart';
import 'package:app/ui/contact_chat_screen.dart';
import 'package:app/ui/desktop_pairing_screen.dart';
import 'package:app/ui/shared_widgets.dart';
import 'package:app/ui/verification_and_directory_screen.dart';
import 'package:cryptography/cryptography.dart';
import 'package:fixnum/fixnum.dart' as $fixnum;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

class _InProcessGovernanceNodeClient extends IspChatNodeClient {
  final SimpleKeyPair prinEd;
  final PrincipalSigningCert pCert;
  final List<String> revokedDevices = <String>[];
  String? lastPortedHome;

  _InProcessGovernanceNodeClient({required this.prinEd, required this.pCert});

  @override
  bool get isConnected => true;

  @override
  String? get connectedNodeAddress => 'node1.isp.br:8443';

  @override
  Future<VerificationBundle> getOrRenewCredential(
    GetCredentialRequest request,
  ) async {
    return VerificationBundle();
  }

  @override
  Future<RevocationEvent> revokeDevice(RevokeDeviceRequest request) async {
    revokedDevices.add(request.revokedDeviceId);
    return RevocationEvent()
      ..revocationId = IdentityCryptoService.generateTemporalUuidV7()
      ..revocationSeq = $fixnum.Int64(revokedDevices.length)
      ..targetUuid = request.userUuid
      ..revokedDeviceId = request.revokedDeviceId
      ..reason = request.reason
      ..createdAtMs = request.timestampMs;
  }

  @override
  Future<VerificationBundle> portAccount(PortAccountRequest request) async {
    lastPortedHome = request.targetHomeNodeAddress;
    final now = DateTime.now().millisecondsSinceEpoch;
    final cred = VerificationCredential()
      ..credentialId = IdentityCryptoService.generateTemporalUuidV7()
      ..userUuid = request.userUuid
      ..rootEd25519Pub = List<int>.filled(32, 1)
      ..rootX25519Pub = List<int>.filled(32, 2)
      ..docType = DocumentType.CPF
      ..verifyingIspCnpj = request.targetIspCnpj
      ..verifyingIspName = 'Novo ISP'
      ..homeNodeAddress = request.targetHomeNodeAddress
      ..routingEpoch = request.expectedRoutingEpoch
      ..issuedAtMs = $fixnum.Int64(now)
      ..expiresAtMs = $fixnum.Int64(now + 90 * 86400000)
      ..principalCert = pCert;
    const crypto = IdentityCryptoService();
    cred.principalSignature = await crypto.signRaw(
      prinEd,
      CanonicalTranscripts.buildVerificationCredentialTranscript(cred),
    );
    return VerificationBundle()
      ..credential = cred
      ..homeNodeClaim = (HomeNodeClaim()
        ..userUuid = request.userUuid
        ..homeNodeAddress = request.targetHomeNodeAddress
        ..routingEpoch = request.expectedRoutingEpoch
        ..issuedAtMs = $fixnum.Int64(now)
        ..signedByPrincipal = true);
  }
}

void main() {
  testWidgets(
      'Mobile Root authorizes Desktop QR pairing and revokes delegated device',
      (tester) async {
    const crypto = IdentityCryptoService();
    final mobileRoot = await crypto.generateNewIdentity();
    final govRoot = await Ed25519().newKeyPair();
    final prinEd = await Ed25519().newKeyPair();
    final now = DateTime.now().millisecondsSinceEpoch;
    final pCert = PrincipalSigningCert()
      ..certId = IdentityCryptoService.generateTemporalUuidV7()
      ..principalEd25519Pub = (await prinEd.extractPublicKey()).bytes
      ..escrowX25519Pub = List<int>.filled(32, 3)
      ..issuedAtMs = $fixnum.Int64(now)
      ..expiresAtMs = $fixnum.Int64(now + 365 * 86400000);
    pCert.rootSignature = await crypto.signRaw(
      govRoot,
      CanonicalTranscripts.buildPrincipalSigningCertTranscript(pCert),
    );

    final nodeClient = _InProcessGovernanceNodeClient(
      prinEd: prinEd,
      pCert: pCert,
    );
    final mobileVault = SecureLocalVault(store: InMemorySecureKeyValueStore());
    final desktopVault = SecureLocalVault(store: InMemorySecureKeyValueStore());

    try {
      // 1. Desktop generates its pair-req URI.
      await tester.pumpWidget(
        MaterialApp(
          home: DesktopPairingScreen(
            vault: desktopVault,
            crypto: crypto,
            nodeClient: nodeClient,
          ),
        ),
      );
      await tester.pumpAndSettle();

      final reqText = tester
          .widget<SelectableText>(find.byKey(const Key('txt_pair_req_uri')))
          .data!;
      expect(reqText.startsWith('ispchat://pair-req?'), isTrue);

      // 2. Mobile Root signs DeviceDelegationCert via authorize button.
      await tester.pumpWidget(
        MaterialApp(
          home: DesktopPairingScreen(
            vault: mobileVault,
            crypto: crypto,
            nodeClient: nodeClient,
            rootIdentity: mobileRoot,
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(
        find.byKey(const Key('input_pair_req_uri')),
        reqText,
      );
      await tester.ensureVisible(
        find.byKey(const Key('btn_authorize_desktop')),
      );
      await tester.tap(find.byKey(const Key('btn_authorize_desktop')));
      await tester.pumpAndSettle();

      final grantText = tester
          .widget<SelectableText>(find.byKey(const Key('txt_pair_grant_uri')))
          .data!;
      expect(grantText.startsWith('ispchat://pair-grant?'), isTrue);

      // Verify revoke button revokes via gRPC PrincipalGovernanceService.
      final pairedList = await mobileVault.loadPairedDevices();
      expect(pairedList.length, 1);
      final targetDeviceId = pairedList.first.deviceId;
      await tester.ensureVisible(
        find.byKey(Key('btn_revoke_device_$targetDeviceId')),
      );
      await tester.tap(find.byKey(Key('btn_revoke_device_$targetDeviceId')));
      await tester.pumpAndSettle();

      expect(nodeClient.revokedDevices, [targetDeviceId]);
      expect((await mobileVault.loadPairedDevices()).first.revoked, isTrue);

      // 3. Desktop applies pair-grant and persists StoredLocalDeviceRecord.
      await tester.pumpWidget(
        MaterialApp(
          home: DesktopPairingScreen(
            vault: desktopVault,
            crypto: crypto,
            nodeClient: nodeClient,
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(
        find.byKey(const Key('input_pair_grant_uri')),
        grantText,
      );
      await tester.ensureVisible(
        find.byKey(const Key('btn_apply_pair_grant')),
      );
      await tester.tap(find.byKey(const Key('btn_apply_pair_grant')));
      await tester.pumpAndSettle();

      final savedDesktop = await desktopVault.loadLocalDeviceRecord();
      expect(savedDesktop?.pairedUserUuid, mobileRoot.userUuid);
      expect(savedDesktop?.delegationCert?.deviceId, targetDeviceId);
    } finally {
      await nodeClient.dispose();
    }
  });

  testWidgets('VerifiedIdentityBadge renders distinct Verified vs Unverified',
      (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: Column(
            children: [
              VerifiedIdentityBadge(
                isVerified: true,
                docType: DocumentType.CNPJ,
                publicCnpj: '12ABC34501DE35',
                verifyingIspName: 'Fibra Sul',
              ),
              VerifiedIdentityBadge(isVerified: false),
            ],
          ),
        ),
      ),
    );
    expect(find.text('CNPJ 12ABC34501DE35 • Fibra Sul'), findsOneWidget);
    expect(find.text('Não verificado'), findsOneWidget);
  });

  testWidgets('Zero RenderFlex overflow on 320x568 phone & 1024x768 desktop',
      (tester) async {
    const crypto = IdentityCryptoService();
    final peer = await crypto.generateNewIdentity();
    final store = InMemorySecureKeyValueStore();
    final vault = SecureLocalVault(store: store);
    final nodeClient = IspChatNodeClient();
    final contact = ContactBundle(
      uuid: peer.userUuid,
      ed25519PublicKey: peer.ed25519PublicKeyBytes,
      x25519PublicKey: peer.x25519PublicKeyBytes,
      displayName: 'Empresa Muito Longa Telecomunicações do Brasil S.A.',
      isVerified: true,
      docType: DocumentType.CNPJ,
      publicCnpj: '12ABC34501DE35',
      verifyingIspName: 'Provedor Regional Fibra Óptica',
    );
    await vault.saveContact(contact);

    for (final size in const [Size(320, 568), Size(1024, 768)]) {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1.0;

      await tester.pumpWidget(IspChatApp(customSecureStore: store));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);

      await tester.pumpWidget(
        MaterialApp(
          home: ContactChatScreen(contact: contact, vault: vault),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);

      await tester.pumpWidget(
        MaterialApp(
          home: VerificationAndDirectoryScreen(
            nodeClient: nodeClient,
            vault: vault,
            crypto: crypto,
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
    }

    tester.view.resetPhysicalSize();
    tester.view.resetDevicePixelRatio();
    await nodeClient.dispose();
  });

  testWidgets('Subscriber verification consent URI signs validly from UI form',
      (tester) async {
    const crypto = IdentityCryptoService();
    final user = await crypto.generateNewIdentity();
    final vault = SecureLocalVault(store: InMemorySecureKeyValueStore());
    final nodeClient = IspChatNodeClient();

    await tester.pumpWidget(
      MaterialApp(
        home: VerificationAndDirectoryScreen(
          nodeClient: nodeClient,
          vault: vault,
          crypto: crypto,
          rootIdentity: user,
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Minha Verificação'));
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byKey(const Key('input_verify_doc')),
      '529.982.247-25',
    );
    await tester.enterText(
      find.byKey(const Key('input_verify_isp_cnpj')),
      '12.ABC.345/01DE-35',
    );
    await tester.ensureVisible(
      find.byKey(const Key('btn_generate_verify_consent')),
    );
    await tester.tap(find.byKey(const Key('btn_generate_verify_consent')));
    await tester.pumpAndSettle();

    final consentUri = tester
        .widget<SelectableText>(find.byKey(const Key('txt_verify_consent_uri')))
        .data!;
    final parsed = await crypto.parseAndVerifyVerificationConsentUri(consentUri);
    expect(parsed.consent.userUuid, user.userUuid);
    expect(parsed.consent.ispCnpj, '12ABC34501DE35');

    await nodeClient.dispose();
  });
}
