import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:app/core/config/network_bootstrap_config.dart';
import 'package:app/core/crypto/identity_crypto_service.dart';
import 'package:app/core/discovery/dns_srv_resolver.dart';
import 'package:app/core/discovery/node_discovery_service.dart';
import 'package:app/core/grpc/generated/ispchat.pb.dart';
import 'package:app/core/grpc/ispchat_node_client.dart';
import 'package:app/core/messaging/e2e_session_manager.dart';
import 'package:app/core/security/blocklist_service.dart';
import 'package:app/core/storage/secure_local_vault.dart';
import 'package:app/core/webrtc/webrtc_call_coordinator.dart';
import 'package:app/ui/contact_chat_screen.dart';
import 'package:app/ui/desktop_pairing_screen.dart';
import 'package:app/ui/identity_qr_dialog.dart';
import 'package:app/ui/shared_widgets.dart';
import 'package:app/ui/verification_and_directory_screen.dart';

void main() {
  runApp(const IspChatApp());
}

class IspChatApp extends StatelessWidget {
  final KeyValueSecureStore? customSecureStore;
  final LatencyProbe? customLatencyProbe;
  final IspChatNodeClient? customNodeClient;
  final DnsSrvResolver? customDnsSrvResolver;
  final NetworkBootstrapConfig bootstrapConfig;
  final int? testNodePortOverride;
  final bool useInsecureLocalTestTransport;
  final Uint8List? pinnedRootPubOverride;
  final WebRtcMediaEngine? customWebRtcEngine;

  const IspChatApp({
    super.key,
    this.customSecureStore,
    this.customLatencyProbe,
    this.customNodeClient,
    this.customDnsSrvResolver,
    this.bootstrapConfig = const NetworkBootstrapConfig(),
    this.testNodePortOverride,
    this.useInsecureLocalTestTransport = false,
    this.pinnedRootPubOverride,
    this.customWebRtcEngine,
  });

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'ispChat E2EE',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorSchemeSeed: Colors.indigo,
        brightness: Brightness.light,
      ),
      home: IspChatHomePage(
        customSecureStore: customSecureStore,
        customLatencyProbe: customLatencyProbe,
        customNodeClient: customNodeClient,
        customDnsSrvResolver: customDnsSrvResolver,
        bootstrapConfig: bootstrapConfig,
        testNodePortOverride: testNodePortOverride,
        useInsecureLocalTestTransport: useInsecureLocalTestTransport,
        pinnedRootPubOverride: pinnedRootPubOverride,
        customWebRtcEngine: customWebRtcEngine,
      ),
    );
  }
}

class IspChatHomePage extends StatefulWidget {
  final KeyValueSecureStore? customSecureStore;
  final LatencyProbe? customLatencyProbe;
  final IspChatNodeClient? customNodeClient;
  final DnsSrvResolver? customDnsSrvResolver;
  final NetworkBootstrapConfig bootstrapConfig;
  final int? testNodePortOverride;
  final bool useInsecureLocalTestTransport;
  final Uint8List? pinnedRootPubOverride;
  final WebRtcMediaEngine? customWebRtcEngine;

  const IspChatHomePage({
    super.key,
    this.customSecureStore,
    this.customLatencyProbe,
    this.customNodeClient,
    this.customDnsSrvResolver,
    this.bootstrapConfig = const NetworkBootstrapConfig(),
    this.testNodePortOverride,
    this.useInsecureLocalTestTransport = false,
    this.pinnedRootPubOverride,
    this.customWebRtcEngine,
  });

  @override
  State<IspChatHomePage> createState() => _IspChatHomePageState();
}

class _IspChatHomePageState extends State<IspChatHomePage> {
  late final IdentityCryptoService _crypto;
  late final SecureLocalVault _vault;
  late final BlocklistService _blocklist;
  late final NodeDiscoveryService _discovery;
  late final IspChatNodeClient _nodeClient;

  final TextEditingController _restoreMnemonicController =
      TextEditingController();
  final TextEditingController _contactUriController = TextEditingController();
  final TextEditingController _seedLinkController = TextEditingController();
  final TextEditingController _blockUuidController = TextEditingController();

  IdentityBundle? _rootIdentity;
  StoredLocalDeviceRecord? _pairedDesktopRecord;
  VerificationCredential? _credential;
  HomeNodeClaim? _homeClaim;
  String? _myContactUri;
  List<ContactBundle> _contacts = const <ContactBundle>[];
  List<String> _seeds = const <String>[];
  String? _connectedNodeHost;
  String? _statusMessage;
  bool _busy = false;

  E2eSessionManager? _sessionManager;
  WebRtcCallCoordinator? _callCoordinator;
  StreamSubscription<RevocationEvent>? _revocationSub;
  StreamSubscription<IdentityReplacementNotice>? _replacementSub;

  @override
  void initState() {
    super.initState();
    _crypto = IdentityCryptoService(bootstrapConfig: widget.bootstrapConfig);
    _vault = SecureLocalVault(
      store: widget.customSecureStore,
      cryptoService: _crypto,
    );
    _blocklist = BlocklistService();
    _discovery = NodeDiscoveryService(
      latencyProbe: widget.customLatencyProbe,
      dnsSrvResolver: widget.customDnsSrvResolver,
      bootstrapConfig: widget.bootstrapConfig,
    );
    _nodeClient = widget.customNodeClient ??
        IspChatNodeClient(bootstrapConfig: widget.bootstrapConfig);
    unawaited(_bootstrapFromVault());
  }

  @override
  void dispose() {
    _revocationSub?.cancel();
    _replacementSub?.cancel();
    _callCoordinator?.dispose();
    _sessionManager?.dispose();
    if (widget.customNodeClient == null) {
      _nodeClient.dispose();
    }
    _restoreMnemonicController.dispose();
    _contactUriController.dispose();
    _seedLinkController.dispose();
    _blockUuidController.dispose();
    super.dispose();
  }

  String? get _activeUserUuid =>
      _rootIdentity?.userUuid ?? _pairedDesktopRecord?.pairedUserUuid;

  Uint8List? get _effectiveRootPub =>
      widget.pinnedRootPubOverride ??
      widget.bootstrapConfig.decodePinnedRootPublicKeyBytes();

  Future<void> _bootstrapFromVault() async {
    final blocked = await _vault.loadBlockedUuids();
    for (final u in blocked) {
      _blocklist.blockUuid(u);
    }
    final savedSeeds = await _vault.loadBootstrapSeeds();
    _discovery.addSeedNodes(savedSeeds);

    final restoredRoot = await _vault.restoreSavedIdentity(_crypto);
    final pairedDevice = await _vault.loadLocalDeviceRecord();
    final cred = await _vault.loadVerificationCredential();
    final claim = await _vault.loadHomeNodeClaim();
    final contacts = await _vault.loadContacts();

    String? uri;
    if (restoredRoot != null) {
      uri = await _crypto.buildSignedContactUri(
        identity: restoredRoot,
        verificationCredential: cred,
      );
    }

    if (!mounted) return;
    setState(() {
      _rootIdentity = restoredRoot;
      _pairedDesktopRecord =
          pairedDevice?.delegationCert != null ? pairedDevice : null;
      _credential = cred;
      _homeClaim = claim;
      _myContactUri = uri;
      _contacts = contacts;
      _seeds = _discovery.seedNodes;
    });
  }

  Future<void> _generateNewIdentity() async {
    setState(() {
      _busy = true;
      _statusMessage = null;
    });
    try {
      final identity = await _crypto.generateNewIdentity();
      await _vault.saveMnemonic(identity.mnemonic);
      await _vault.clearVerificationCredential();
      final uri = await _crypto.buildSignedContactUri(identity: identity);
      if (!mounted) return;
      setState(() {
        _rootIdentity = identity;
        _credential = null;
        _myContactUri = uri;
        _statusMessage = 'Identidade raiz UUIDv7 gerada com sucesso.';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _statusMessage = 'Erro ao gerar identidade: $e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _restoreIdentityFromMnemonic() async {
    final mnemonic = _restoreMnemonicController.text.trim();
    if (mnemonic.isEmpty) return;
    setState(() {
      _busy = true;
      _statusMessage = null;
    });
    try {
      final identity = await _crypto.restoreIdentityFromMnemonic(mnemonic);
      await _vault.saveMnemonic(identity.mnemonic);
      final cred = await _vault.loadVerificationCredential();
      final validCred = cred != null &&
              CanonicalTranscripts.canonicalUuid(cred.userUuid) ==
                  CanonicalTranscripts.canonicalUuid(identity.userUuid)
          ? cred
          : null;
      final uri = await _crypto.buildSignedContactUri(
        identity: identity,
        verificationCredential: validCred,
      );
      if (!mounted) return;
      setState(() {
        _rootIdentity = identity;
        _credential = validCred;
        _myContactUri = uri;
        _restoreMnemonicController.clear();
        _statusMessage = 'Identidade restaurada via BIP39.';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _statusMessage = 'Frase BIP39 inválida: $e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _addContactFromUri() async {
    final raw = _contactUriController.text.trim();
    if (raw.isEmpty) return;
    setState(() {
      _busy = true;
      _statusMessage = null;
    });
    try {
      final bundle = await _crypto.parseAndVerifyContactUri(
        raw,
        pinnedRootEd25519Pub: _effectiveRootPub,
      );
      await _vault.saveContact(bundle);
      final contacts = await _vault.loadContacts();
      if (!mounted) return;
      setState(() {
        _contacts = contacts;
        _contactUriController.clear();
        _statusMessage = 'Contato ${bundle.uuid} adicionado.';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _statusMessage = 'Link de contato inválido: $e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _injectSeedLink() async {
    final added = _discovery.injectSocialSeedLink(_seedLinkController.text);
    if (added == null) {
      setState(() => _statusMessage = 'Endereço de nó semente inválido.');
      return;
    }
    await _vault.saveBootstrapSeeds(_discovery.seedNodes);
    if (!mounted) return;
    setState(() {
      _seeds = _discovery.seedNodes;
      _seedLinkController.clear();
      _statusMessage = 'Nó semente adicionado: $added';
    });
  }

  Future<void> _electAndConnectNode() async {
    setState(() {
      _busy = true;
      _statusMessage = null;
    });
    try {
      final extra = widget.testNodePortOverride != null
          ? <String>['127.0.0.1:${widget.testNodePortOverride}']
          : null;
      final candidates = await _discovery.resolveBootstrapCandidates(
        cachedHomeNodeClaim: _homeClaim,
        verificationCredential: _credential,
        extraSeeds: extra,
        includeDnsSrv: widget.testNodePortOverride == null,
      );
      final elected = await _discovery.selectFastestNode(candidates);

      await _nodeClient.connectToAddress(
        elected.nodeAddress,
        useTls: !widget.useInsecureLocalTestTransport,
        routeGovernanceThroughEdgeChannel: widget.useInsecureLocalTestTransport,
      );

      await _startActiveSessionIfUnlocked(elected.nodeAddress);

      if (!mounted) return;
      setState(() {
        _connectedNodeHost = elected.nodeAddress;
        _statusMessage =
            'Conectado ao nó ${elected.nodeAddress} (${elected.latencyMs}ms).';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _statusMessage = 'Falha na descoberta/conexão: $e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _startActiveSessionIfUnlocked(String connectedAddr) async {
    final root = _rootIdentity;
    final desktop = _pairedDesktopRecord;
    if (root == null && desktop == null) return;

    await _revocationSub?.cancel();
    await _replacementSub?.cancel();
    await _callCoordinator?.dispose();
    await _sessionManager?.dispose();

    E2eSessionManager manager;
    if (root != null) {
      manager = await E2eSessionManager.createForRootIdentity(
        identity: root,
        nodeClient: _nodeClient,
        vault: _vault,
        blocklist: _blocklist,
        crypto: _crypto,
        bootstrapConfig: widget.bootstrapConfig,
        pinnedRootPubOverride: widget.pinnedRootPubOverride,
      );
    } else {
      final restoredDevice = await _crypto.restoreDeviceIdentityFromSeeds(
        deviceId: desktop!.deviceId,
        role: desktop.role,
        ed25519Seed: desktop.ed25519Seed,
        x25519Seed: desktop.x25519Seed,
        delegationCert: desktop.delegationCert,
      );
      manager = E2eSessionManager(
        deviceIdentity: restoredDevice,
        activeUserUuid: desktop.pairedUserUuid!,
        activeRootEd25519Pub: desktop.rootEd25519Pub!,
        nodeClient: _nodeClient,
        vault: _vault,
        blocklist: _blocklist,
        crypto: _crypto,
        bootstrapConfig: widget.bootstrapConfig,
        pinnedRootPubOverride: widget.pinnedRootPubOverride,
      );
    }

    await manager.initializeAndRegister(nodeAddressOverride: connectedAddr);

    final coordinator = WebRtcCallCoordinator(
      sessionManager: manager,
      mediaEngine: widget.customWebRtcEngine,
    );

    _revocationSub = manager.revocationEvents.listen((ev) {
      if (!mounted) return;
      setState(() {
        _statusMessage =
            'Aviso de revogação recebido para ${ev.targetUuid} (${ev.reason}).';
      });
    });
    _replacementSub = manager.replacementEvents.listen((notice) async {
      final updatedContacts = await _vault.loadContacts();
      if (!mounted) return;
      setState(() {
        _contacts = updatedContacts;
        _statusMessage =
            'Identidade atualizada via rebind: ${notice.newUserUuid}.';
      });
    });

    _sessionManager = manager;
    _callCoordinator = coordinator;
  }

  Future<void> _blockUuid() async {
    final target = _blockUuidController.text.trim();
    if (target.isEmpty) return;
    _blocklist.blockUuid(target);
    await _vault.saveBlockedUuids(_blocklist.blockedUuids);
    if (!mounted) return;
    setState(() {
      _blockUuidController.clear();
      _statusMessage = 'UUID $target adicionado à lista local de bloqueio.';
    });
  }

  Future<void> _scanContactUri() async {
    final scanned = await openMobileQrScannerDialog(
      context,
      title: 'Escanear QR de Contato',
    );
    if (scanned != null && mounted) {
      setState(() => _contactUriController.text = scanned);
    }
  }

  void _openQrDialog() {
    final uri = _myContactUri;
    final uuid = _activeUserUuid;
    if (uri == null || uuid == null) return;
    showDialog<void>(
      context: context,
      builder: (_) => IdentityQrDialog(
        contactUri: uri,
        userUuid: uuid,
        verificationCredential: _credential,
      ),
    );
  }

  Future<void> _openVerificationDirectory() async {
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => VerificationAndDirectoryScreen(
          nodeClient: _nodeClient,
          vault: _vault,
          crypto: _crypto,
          rootIdentity: _rootIdentity,
          deviceIdentity: _sessionManager?.deviceIdentity,
          currentCredential: _credential,
          bootstrapConfig: widget.bootstrapConfig,
          pinnedRootPubOverride: widget.pinnedRootPubOverride,
          onContactAdded: (_) async {
            final updated = await _vault.loadContacts();
            if (mounted) setState(() => _contacts = updated);
          },
          onCredentialUpdated: (bundle) async {
            final root = _rootIdentity;
            String? updatedUri;
            if (root != null && bundle.hasCredential()) {
              updatedUri = await _crypto.buildSignedContactUri(
                identity: root,
                verificationCredential: bundle.credential,
              );
            }
            if (!mounted) return;
            setState(() {
              if (bundle.hasCredential()) _credential = bundle.credential;
              if (bundle.hasHomeNodeClaim()) _homeClaim = bundle.homeNodeClaim;
              if (updatedUri != null) _myContactUri = updatedUri;
            });
          },
        ),
      ),
    );
  }

  Future<void> _openDesktopPairing() async {
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => DesktopPairingScreen(
          vault: _vault,
          crypto: _crypto,
          nodeClient: _nodeClient,
          rootIdentity: _rootIdentity,
          currentCredential: _credential,
          currentHomeClaim: _homeClaim,
          bootstrapConfig: widget.bootstrapConfig,
          pinnedRootPubOverride: widget.pinnedRootPubOverride,
          onPairedGrantApplied: (grant) async {
            await _bootstrapFromVault();
          },
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('ispChat P2P E2EE', overflow: TextOverflow.ellipsis),
        actions: [
          IconButton(
            key: const Key('btn_open_verification_directory'),
            tooltip: 'Verificação & Diretório CNPJ',
            onPressed: _openVerificationDirectory,
            icon: const Icon(Icons.verified_user_outlined),
          ),
          IconButton(
            key: const Key('btn_open_desktop_pairing'),
            tooltip: 'Dispositivos & Pareamento Desktop',
            onPressed: _openDesktopPairing,
            icon: const Icon(Icons.devices),
          ),
        ],
      ),
      body: LayoutBuilder(
        builder: (context, constraints) {
          final isDesktopWide = constraints.maxWidth >= 760;
          return Column(
            children: [
              if (_statusMessage != null)
                MaterialBanner(
                  content: Text(_statusMessage!),
                  actions: [
                    TextButton(
                      onPressed: () => setState(() => _statusMessage = null),
                      child: const Text('OK'),
                    ),
                  ],
                ),
              Expanded(
                child: isDesktopWide
                    ? Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Expanded(
                            child: SingleChildScrollView(
                              padding: const EdgeInsets.all(16),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.stretch,
                                children: [
                                  _buildIdentityCard(context),
                                  const SizedBox(height: 16),
                                  _buildNetworkCard(context),
                                ],
                              ),
                            ),
                          ),
                          Expanded(
                            child: SingleChildScrollView(
                              padding: const EdgeInsets.all(16),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.stretch,
                                children: [
                                  _buildContactsCard(context),
                                  const SizedBox(height: 16),
                                  _buildBlocklistCard(context),
                                ],
                              ),
                            ),
                          ),
                        ],
                      )
                    : SingleChildScrollView(
                        padding: const EdgeInsets.all(12),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            _buildIdentityCard(context),
                            const SizedBox(height: 12),
                            _buildNetworkCard(context),
                            const SizedBox(height: 12),
                            _buildContactsCard(context),
                            const SizedBox(height: 12),
                            _buildBlocklistCard(context),
                          ],
                        ),
                      ),
              ),
            ],
          );
        },
      ),
    );
  }

  Widget _buildIdentityCard(BuildContext context) {
    final activeUuid = _activeUserUuid;
    final mnemonic = _rootIdentity?.mnemonic;
    final desktopPaired = _rootIdentity == null && _pairedDesktopRecord != null;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.fingerprint),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Identidade Criptográfica',
                    style: Theme.of(context).textTheme.titleMedium,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            VerifiedIdentityBadge(
              isVerified: _credential != null,
              docType: _credential?.docType,
              publicCnpj: _credential?.publicCnpj,
              verifyingIspName: _credential?.verifyingIspName,
            ),
            const SizedBox(height: 10),
            if (activeUuid != null) ...[
              SelectableText(
                activeUuid,
                key: const Key('txt_user_uuid'),
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      fontFamily: 'monospace',
                      fontWeight: FontWeight.w600,
                    ),
              ),
              if (desktopPaired)
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Text(
                    'Modo Desktop Delegado (${_pairedDesktopRecord!.deviceId})',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ),
              if (mnemonic != null) ...[
                const SizedBox(height: 6),
                SelectableText(
                  mnemonic,
                  key: const Key('txt_user_mnemonic'),
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
              if (_myContactUri != null) ...[
                const SizedBox(height: 6),
                SelectableText(
                  _myContactUri!,
                  key: const Key('txt_contact_uri'),
                  maxLines: 2,
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        fontFamily: 'monospace',
                      ),
                ),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    OutlinedButton.icon(
                      key: const Key('btn_show_qr_code'),
                      onPressed: _openQrDialog,
                      icon: const Icon(Icons.qr_code),
                      label: const Text('Exibir QR'),
                    ),
                  ],
                ),
              ],
            ] else ...[
              Text(
                'Nenhuma identidade configurada neste dispositivo.',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  FilledButton.icon(
                    key: const Key('btn_generate_identity'),
                    onPressed: _busy ? null : _generateNewIdentity,
                    icon: const Icon(Icons.add_moderator),
                    label: const Text('Gerar Identidade'),
                  ),
                  OutlinedButton.icon(
                    onPressed: _openDesktopPairing,
                    icon: const Icon(Icons.laptop),
                    label: const Text('Parear Desktop'),
                  ),
                ],
              ),
            ],
            const SizedBox(height: 12),
            TextField(
              key: const Key('input_restore_mnemonic'),
              controller: _restoreMnemonicController,
              decoration: const InputDecoration(
                labelText: 'Restaurar frase BIP39 (12 palavras)',
                border: OutlineInputBorder(),
                isDense: true,
              ),
            ),
            const SizedBox(height: 8),
            OutlinedButton(
              key: const Key('btn_restore_identity'),
              onPressed: _busy ? null : _restoreIdentityFromMnemonic,
              child: const Text('Restaurar por Mnemonic'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildNetworkCard(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.hub_outlined),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Descoberta de Nó & Rede DoH',
                    style: Theme.of(context).textTheme.titleMedium,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 6),
            Text(
              _connectedNodeHost ?? 'Desconectado (DoH SRV: ${widget.bootstrapConfig.srvDomain})',
              key: const Key('txt_connected_node_host'),
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    fontFamily: 'monospace',
                    fontWeight: FontWeight.w600,
                  ),
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    key: const Key('input_seed_link'),
                    controller: _seedLinkController,
                    decoration: const InputDecoration(
                      labelText: 'Semente manual (host:port ou ispchat://seed)',
                      border: OutlineInputBorder(),
                      isDense: true,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                OutlinedButton(
                  key: const Key('btn_inject_seed'),
                  onPressed: _injectSeedLink,
                  child: const Text('Adicionar'),
                ),
              ],
            ),
            if (_seeds.isNotEmpty) ...[
              const SizedBox(height: 8),
              Wrap(
                spacing: 6,
                runSpacing: 6,
                children: _seeds
                    .map(
                      (s) => Chip(
                        label: Text(s, overflow: TextOverflow.ellipsis),
                      ),
                    )
                    .toList(growable: false),
              ),
            ],
            const SizedBox(height: 10),
            FilledButton.icon(
              key: const Key('btn_elect_and_connect_node'),
              onPressed: _busy ? null : _electAndConnectNode,
              icon: const Icon(Icons.bolt),
              label: const Text('Descobrir & Conectar'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildContactsCard(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.people_outline),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Contatos E2EE',
                    style: Theme.of(context).textTheme.titleMedium,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    key: const Key('input_contact_uri'),
                    controller: _contactUriController,
                    decoration: InputDecoration(
                      labelText: 'Colar link ispchat://contact?...',
                      border: const OutlineInputBorder(),
                      isDense: true,
                      suffixIcon: isMobileCameraSupported()
                          ? IconButton(
                              tooltip: 'Escanear QR de Contato',
                              icon: const Icon(Icons.qr_code_scanner),
                              onPressed: _scanContactUri,
                            )
                          : null,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                FilledButton(
                  key: const Key('btn_add_contact'),
                  onPressed: _busy ? null : _addContactFromUri,
                  child: const Text('Salvar'),
                ),
              ],
            ),
            const SizedBox(height: 12),
            if (_contacts.isEmpty)
              Text(
                'Nenhum contato adicionado ainda.',
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
              )
            else
              ..._contacts.map(
                (contact) => ListTile(
                  key: Key('contact_tile_${contact.uuid}'),
                  contentPadding: EdgeInsets.zero,
                  leading: Icon(
                    contact.isVerified ? Icons.verified_user : Icons.person,
                    color: contact.isVerified ? Colors.green : null,
                  ),
                  title: Text(
                    contact.publicDisplayName ??
                        contact.displayName ??
                        contact.uuid,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  subtitle: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        contact.uuid,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context)
                            .textTheme
                            .bodySmall
                            ?.copyWith(fontFamily: 'monospace'),
                      ),
                      const SizedBox(height: 2),
                      VerifiedIdentityBadge(
                        isVerified: contact.isVerified,
                        docType: contact.docType,
                        publicCnpj: contact.publicCnpj,
                        verifyingIspName: contact.verifyingIspName,
                        compact: true,
                      ),
                    ],
                  ),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () {
                    Navigator.of(context).push(
                      MaterialPageRoute<void>(
                        builder: (_) => ContactChatScreen(
                          contact: contact,
                          vault: _vault,
                          sessionManager: _sessionManager,
                          callCoordinator: _callCoordinator,
                        ),
                      ),
                    );
                  },
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildBlocklistCard(BuildContext context) {
    final blocked = _blocklist.blockedUuids;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Bloqueio Local (Privacidade)',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    key: const Key('input_block_uuid'),
                    controller: _blockUuidController,
                    decoration: const InputDecoration(
                      labelText: 'UUIDv7 a bloquear',
                      border: OutlineInputBorder(),
                      isDense: true,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                OutlinedButton(
                  key: const Key('btn_block_uuid'),
                  onPressed: _blockUuid,
                  child: const Text('Bloquear'),
                ),
              ],
            ),
            if (blocked.isNotEmpty) ...[
              const SizedBox(height: 8),
              Wrap(
                spacing: 6,
                runSpacing: 6,
                children: blocked
                    .map(
                      (u) => InputChip(
                        label: Text(u, overflow: TextOverflow.ellipsis),
                        onDeleted: () async {
                          _blocklist.unblockUuid(u);
                          await _vault.saveBlockedUuids(_blocklist.blockedUuids);
                          if (mounted) setState(() {});
                        },
                      ),
                    )
                    .toList(growable: false),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
