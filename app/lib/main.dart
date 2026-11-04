import 'dart:async';

import 'package:flutter/material.dart';

import 'core/crypto/identity_crypto_service.dart';
import 'core/discovery/node_discovery_service.dart';
import 'core/discovery/tcp_latency_probe.dart';
import 'core/grpc/ispchat_node_client.dart';
import 'core/grpc/ispchat_pb.dart';
import 'core/messaging/e2e_session_manager.dart';
import 'core/security/blocklist_service.dart';
import 'core/storage/secure_local_vault.dart';
import 'ui/contact_chat_screen.dart';
import 'ui/identity_qr_dialog.dart';

void main() {
  runApp(const IspChatApp());
}

class IspChatApp extends StatelessWidget {
  final KeyValueSecureStore? customSecureStore;
  final LatencyProbe? customLatencyProbe;
  final IspChatNodeClient? customNodeClient;
  final int? testNodePortOverride;
  final bool useInsecureLocalTestTransport;

  const IspChatApp({
    super.key,
    this.customSecureStore,
    this.customLatencyProbe,
    this.customNodeClient,
    this.testNodePortOverride,
    this.useInsecureLocalTestTransport = false,
  });

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'ISP Chat',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF0F766E),
          brightness: Brightness.dark,
        ),
        useMaterial3: true,
      ),
      home: IspChatHomeScreen(
        customSecureStore: customSecureStore,
        customLatencyProbe: customLatencyProbe,
        customNodeClient: customNodeClient,
        testNodePortOverride: testNodePortOverride,
        useInsecureLocalTestTransport: useInsecureLocalTestTransport,
      ),
    );
  }
}

class IspChatHomeScreen extends StatefulWidget {
  final KeyValueSecureStore? customSecureStore;
  final LatencyProbe? customLatencyProbe;
  final IspChatNodeClient? customNodeClient;
  final int? testNodePortOverride;
  final bool useInsecureLocalTestTransport;

  const IspChatHomeScreen({
    super.key,
    this.customSecureStore,
    this.customLatencyProbe,
    this.customNodeClient,
    this.testNodePortOverride,
    this.useInsecureLocalTestTransport = false,
  });

  @override
  State<IspChatHomeScreen> createState() => _IspChatHomeScreenState();
}

class _IspChatHomeScreenState extends State<IspChatHomeScreen> {
  final IdentityCryptoService _cryptoService = IdentityCryptoService();
  final BlocklistService _blocklistService = BlocklistService();
  final TcpLatencyProbe _tcpProbe = const TcpLatencyProbe();
  late final E2eSessionManager _sessionManager;
  late final NodeDiscoveryService _discoveryService;
  late final SecureLocalVault _vault;
  late final IspChatNodeClient _nodeClient;
  StreamSubscription<DecryptedIncomingEvent>? _nodeIncomingSub;

  final TextEditingController _restoreMnemonicController = TextEditingController();
  final TextEditingController _contactUriController = TextEditingController();
  final TextEditingController _seedLinkController = TextEditingController();
  final TextEditingController _blockUuidController = TextEditingController();

  UserIdentity? _currentIdentity;
  String? _statusMessage;
  bool _isBusy = false;

  @override
  void initState() {
    super.initState();
    _sessionManager = E2eSessionManager(
      cryptoService: _cryptoService,
      blocklistService: _blocklistService,
    );
    _discoveryService = NodeDiscoveryService(
      latencyProbe: widget.customLatencyProbe ?? _tcpProbe.measureLatencyMs,
    );
    _vault = SecureLocalVault(
      store: widget.customSecureStore ?? FlutterSecureKeyValueStore(),
      cryptoService: _cryptoService,
    );
    _nodeClient = widget.customNodeClient ?? IspChatNodeClient(sessionManager: _sessionManager);
    _nodeIncomingSub = _nodeClient.incomingEvents.listen(_onIncomingNodeEvent);
    _hydrateFromSecureVault();
  }

  Future<void> _onIncomingNodeEvent(DecryptedIncomingEvent event) async {
    final UserIdentity? identity = _currentIdentity;
    if (identity == null) return;

    if (event.type == ProtoMessageType.text) {
      final StoredChatMessage msg = StoredChatMessage(
        messageId: event.messageId,
        senderUuid: event.senderUuid,
        receiverUuid: identity.userUuid,
        text: event.cleartextOrJsonSignal,
        timestampMillis: event.timestampMillis,
        isOutgoing: false,
      );
      await _vault.appendChatMessage(event.senderUuid, msg);
      if (!mounted) return;
      setState(() {
        _statusMessage = 'Nova mensagem E2E recebida de ${event.senderUuid.substring(0, 8)}...';
      });
    }
  }

  Future<void> _hydrateFromSecureVault() async {
    final UserIdentity? savedIdentity = await _vault.loadSavedIdentity();
    final List<ContactBundle> savedContacts = await _vault.loadContacts();
    final List<String> savedBlocks = await _vault.loadBlockedUuids();
    final List<String> savedSeeds = await _vault.loadSeedNodes();

    for (final ContactBundle c in savedContacts) {
      _sessionManager.registerVerifiedContact(c);
    }
    for (final String b in savedBlocks) {
      _blocklistService.blockUuid(b);
    }
    for (final String s in savedSeeds) {
      _discoveryService.addCandidateNode(s);
    }

    if (!mounted) return;
    if (savedIdentity != null ||
        savedContacts.isNotEmpty ||
        savedBlocks.isNotEmpty ||
        savedSeeds.isNotEmpty) {
      setState(() {
        _currentIdentity = savedIdentity;
      });
    }
  }

  @override
  void dispose() {
    _nodeIncomingSub?.cancel();
    _nodeClient.dispose();
    _restoreMnemonicController.dispose();
    _contactUriController.dispose();
    _seedLinkController.dispose();
    _blockUuidController.dispose();
    super.dispose();
  }

  Future<void> _handleGenerateIdentity() async {
    setState(() {
      _isBusy = true;
      _statusMessage = null;
    });
    try {
      final UserIdentity identity = await _cryptoService.generateNewIdentity();
      await _vault.saveIdentityMnemonic(identity.mnemonic);
      if (!mounted) return;
      setState(() {
        _currentIdentity = identity;
        _statusMessage =
            'Identidade auto-soberana gerada e salva no cofre seguro (UUIDv7 + Ed25519/X25519).';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _statusMessage = 'Erro ao gerar identidade: $e';
      });
    } finally {
      if (mounted) {
        setState(() {
          _isBusy = false;
        });
      }
    }
  }

  Future<void> _handleRestoreIdentity() async {
    final String raw = _restoreMnemonicController.text.trim();
    if (raw.isEmpty) {
      setState(() {
        _statusMessage = 'Informe as 12 palavras BIP39 para restaurar sua identidade.';
      });
      return;
    }

    setState(() {
      _isBusy = true;
      _statusMessage = null;
    });
    try {
      final UserIdentity identity = await _cryptoService.restoreIdentityFromMnemonic(raw);
      await _vault.saveIdentityMnemonic(identity.mnemonic);
      if (!mounted) return;
      setState(() {
        _currentIdentity = identity;
        _statusMessage = 'Identidade restaurada deterministicamente e salva no cofre.';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _statusMessage = 'Frase semente invalida: $e';
      });
    } finally {
      if (mounted) {
        setState(() {
          _isBusy = false;
        });
      }
    }
  }

  Future<void> _handleAddContactByUri() async {
    final String rawUri = _contactUriController.text.trim();
    if (rawUri.isEmpty) return;

    try {
      final ContactBundle bundle = await _cryptoService.parseAndVerifyContactUri(rawUri);
      _sessionManager.registerVerifiedContact(bundle);
      await _vault.saveContacts(_sessionManager.contacts);
      if (!mounted) return;
      setState(() {
        _contactUriController.clear();
        _statusMessage = 'Contato verificado matematicamente (Anti-Spoofing OK): ${bundle.userUuid}';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _statusMessage = 'Falha na validacao do Link Magico: $e';
      });
    }
  }

  Future<void> _handleInjectSeed() async {
    final String link = _seedLinkController.text.trim();
    if (link.isEmpty) return;
    try {
      final String added = _discoveryService.injectSocialSeedLink(link);
      await _vault.saveSeedNodes(_discoveryService.candidateNodes);
      if (!mounted) return;
      _seedLinkController.clear();
      setState(() {
        _statusMessage = 'No semente injetado e persistido: $added';
      });
    } catch (e) {
      setState(() {
        _statusMessage = 'Link de seed invalido: $e';
      });
    }
  }

  Future<void> _handleBlockUuid() async {
    final String uuid = _blockUuidController.text.trim();
    if (uuid.isEmpty) return;
    _blocklistService.blockUuid(uuid);
    await _vault.saveBlockedUuids(_blocklistService.blockedUuids);
    if (!mounted) return;
    setState(() {
      _blockUuidController.clear();
      _statusMessage = 'UUID bloqueado localmente no aparelho.';
    });
  }

  Future<void> _handleElectAndConnectNode() async {
    final UserIdentity? identity = _currentIdentity;
    if (identity == null) {
      setState(() {
        _statusMessage = 'Gere ou restaure sua identidade antes de conectar ao No gRPC.';
      });
      return;
    }

    setState(() {
      _isBusy = true;
      _statusMessage = null;
    });

    try {
      final int portToUse = widget.testNodePortOverride ?? 443;
      final NodeLatencyResult? winner = await _discoveryService.selectFastestNode();
      if (winner == null) {
        if (!mounted) return;
        setState(() {
          _statusMessage = 'Nenhum No candidato respondeu ao teste de latencia TCP.';
        });
        return;
      }

      final RegisterSessionResponseProto reg = await _nodeClient.connectAndAuthenticate(
        nodeHost: winner.nodeAddress,
        identity: identity,
        port: portToUse,
        useInsecureLocalTestTransport: widget.useInsecureLocalTestTransport,
      );

      if (!mounted) return;
      setState(() {
        _statusMessage = reg.success
            ? 'Conectado e autenticado no No ${winner.nodeAddress} (${winner.latencyMs}ms) — DHT + Stream E2E ativos.'
            : 'Falha ao registrar sessao no No ${winner.nodeAddress}: ${reg.errorMessage}';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _statusMessage = 'Erro ao conectar ao No gRPC: $e';
      });
    } finally {
      if (mounted) {
        setState(() {
          _isBusy = false;
        });
      }
    }
  }

  void _openQrDialog(UserIdentity identity) {
    showDialog<void>(
      context: context,
      builder: (BuildContext ctx) => IdentityQrDialog(
        identity: identity,
        cryptoService: _cryptoService,
      ),
    );
  }

  void _openContactChat(ContactBundle contact) {
    final UserIdentity? identity = _currentIdentity;
    if (identity == null) {
      setState(() {
        _statusMessage = 'Gere ou restaure sua identidade antes de iniciar uma conversa E2E.';
      });
      return;
    }

    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (BuildContext ctx) => ContactChatScreen(
          localIdentity: identity,
          contact: contact,
          sessionManager: _sessionManager,
          vault: _vault,
          nodeClient: _nodeClient,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final UserIdentity? identity = _currentIdentity;

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'ISP Chat — Identidade & Rede P2P',
          overflow: TextOverflow.ellipsis,
        ),
      ),
      body: SafeArea(
        child: LayoutBuilder(
          builder: (BuildContext context, BoxConstraints constraints) {
            return SingleChildScrollView(
              padding: const EdgeInsets.all(16),
              child: ConstrainedBox(
                constraints: BoxConstraints(minWidth: constraints.maxWidth - 32),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: <Widget>[
                    if (_statusMessage != null) ...<Widget>[
                      Card(
                        child: Padding(
                          padding: const EdgeInsets.all(12),
                          child: Row(
                            children: <Widget>[
                              const Icon(Icons.security, size: 20),
                              const SizedBox(width: 8),
                              Expanded(
                                child: Text(
                                  _statusMessage!,
                                  overflow: TextOverflow.ellipsis,
                                  maxLines: 3,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                      const SizedBox(height: 12),
                    ],
                    _buildIdentitySection(identity),
                    const SizedBox(height: 16),
                    _buildContactsSection(),
                    const SizedBox(height: 16),
                    _buildSeedDiscoverySection(),
                    const SizedBox(height: 16),
                    _buildBlocklistSection(),
                  ],
                ),
              ),
            );
          },
        ),
      ),
    );
  }

  Widget _buildIdentitySection(UserIdentity? identity) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            const Text(
              '1. Identidade Auto-Soberana (UUIDv7 + BIP39)',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
              overflow: TextOverflow.ellipsis,
            ),
            const SizedBox(height: 8),
            if (identity == null) ...<Widget>[
              const Text(
                'Nenhuma identidade ativa neste dispositivo. Gere uma nova chave local ou restaure suas 12 palavras.',
              ),
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: <Widget>[
                  FilledButton.icon(
                    key: const Key('btn_generate_identity'),
                    onPressed: _isBusy ? null : _handleGenerateIdentity,
                    icon: const Icon(Icons.key),
                    label: const Text('Gerar Nova Identidade (12 Palavras)'),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              TextField(
                key: const Key('input_restore_mnemonic'),
                controller: _restoreMnemonicController,
                decoration: const InputDecoration(
                  labelText: 'Restaurar Frase Semente (12 palavras BIP39)',
                  border: OutlineInputBorder(),
                ),
                minLines: 1,
                maxLines: 2,
              ),
              const SizedBox(height: 8),
              OutlinedButton.icon(
                key: const Key('btn_restore_identity'),
                onPressed: _isBusy ? null : _handleRestoreIdentity,
                icon: const Icon(Icons.restore),
                label: const Text('Restaurar Conta'),
              ),
            ] else ...<Widget>[
              const Text(
                'UUIDv7 Deterministico (Atrelado a sua Chave Publica Ed25519):',
                style: TextStyle(fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 4),
              SelectableText(
                identity.userUuid,
                key: const Key('txt_user_uuid'),
              ),
              const SizedBox(height: 12),
              const Text(
                'Frase Semente de Backup (12 Palavras BIP39):',
                style: TextStyle(fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 4),
              SelectableText(
                identity.mnemonic,
                key: const Key('txt_user_mnemonic'),
              ),
              const SizedBox(height: 12),
              const Text(
                'Link Magico de Compartilhamento Seguro (QR / Deep Link):',
                style: TextStyle(fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 4),
              SelectableText(
                _cryptoService.buildShareableContactUri(identity),
                key: const Key('txt_contact_uri'),
                maxLines: 3,
              ),
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: <Widget>[
                  FilledButton.icon(
                    key: const Key('btn_show_qr_code'),
                    onPressed: () => _openQrDialog(identity),
                    icon: const Icon(Icons.qr_code),
                    label: const Text('Exibir QR Code / Modo Doomsday IPv6'),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildContactsSection() {
    final List<ContactBundle> contacts = _sessionManager.contacts;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            const Text(
              '2. Contatos Verificados (QR Code / Link Magico)',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
              overflow: TextOverflow.ellipsis,
            ),
            const SizedBox(height: 8),
            Row(
              children: <Widget>[
                Expanded(
                  child: TextField(
                    key: const Key('input_contact_uri'),
                    controller: _contactUriController,
                    decoration: const InputDecoration(
                      labelText: 'Colar Link Magico (ispchat://contact?...)',
                      border: OutlineInputBorder(),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                FilledButton(
                  key: const Key('btn_add_contact'),
                  onPressed: _handleAddContactByUri,
                  child: const Text('Validar'),
                ),
              ],
            ),
            const SizedBox(height: 8),
            if (contacts.isEmpty)
              const Text(
                'Nenhum contato salvo localmente. Adicione amigos via QR Code ou Link Magico.',
              )
            else
              Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: contacts
                    .map((ContactBundle c) => ListTile(
                          key: Key('contact_tile_${c.userUuid}'),
                          contentPadding: EdgeInsets.zero,
                          leading: const Icon(Icons.verified_user_outlined),
                          title: Text(
                            c.userUuid,
                            overflow: TextOverflow.ellipsis,
                          ),
                          subtitle: Text(
                            c.directIpv6Address != null
                                ? 'Ed25519 + X25519 OK • IPv6: ${c.directIpv6Address}'
                                : 'Ed25519 + X25519 verificados',
                            overflow: TextOverflow.ellipsis,
                          ),
                          trailing: const Icon(Icons.chat_bubble_outline),
                          onTap: () => _openContactChat(c),
                        ))
                    .toList(),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildSeedDiscoverySection() {
    final List<String> candidates = _discoveryService.candidateNodes;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            const Text(
              '3. Descoberta de Nos & Bootstrap Social',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
              overflow: TextOverflow.ellipsis,
            ),
            const SizedBox(height: 8),
            Row(
              children: <Widget>[
                Expanded(
                  child: TextField(
                    key: const Key('input_seed_link'),
                    controller: _seedLinkController,
                    decoration: const InputDecoration(
                      labelText: 'Injetar link social (ispchat://seed?ip=...)',
                      border: OutlineInputBorder(),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                FilledButton(
                  key: const Key('btn_inject_seed'),
                  onPressed: _handleInjectSeed,
                  child: const Text('Adicionar'),
                ),
              ],
            ),
            const SizedBox(height: 8),
            if (candidates.isEmpty)
              const Text(
                'Aguardando descoberta DNS SRV (_ispchat._tcp.ispchat.dev.br) ou injecao manual de seed.',
              )
            else ...<Widget>[
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: candidates
                    .map((String node) => Padding(
                          padding: const EdgeInsets.symmetric(vertical: 2),
                          child: Text(
                            '• $node (Porta 443 TLS)',
                            overflow: TextOverflow.ellipsis,
                          ),
                        ))
                    .toList(),
              ),
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: <Widget>[
                  FilledButton.tonalIcon(
                    key: const Key('btn_elect_and_connect_node'),
                    onPressed: _isBusy ? null : _handleElectAndConnectNode,
                    icon: const Icon(Icons.bolt),
                    label: const Text('Eleger & Conectar ao Melhor No'),
                  ),
                  if (_nodeClient.isConnected && _nodeClient.connectedHost != null)
                    Text(
                      'Ativo: ${_nodeClient.connectedHost}',
                      key: const Key('txt_connected_node_host'),
                      style: const TextStyle(fontWeight: FontWeight.w600),
                      overflow: TextOverflow.ellipsis,
                    ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildBlocklistSection() {
    final List<String> blocked = _blocklistService.blockedUuids.toList();
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            const Text(
              '4. Blocklist Local Privada (Anti-Assedio)',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
              overflow: TextOverflow.ellipsis,
            ),
            const SizedBox(height: 8),
            Row(
              children: <Widget>[
                Expanded(
                  child: TextField(
                    key: const Key('input_block_uuid'),
                    controller: _blockUuidController,
                    decoration: const InputDecoration(
                      labelText: 'Bloquear UUIDv7 silenciosamente no aparelho',
                      border: OutlineInputBorder(),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                OutlinedButton(
                  key: const Key('btn_block_uuid'),
                  onPressed: _handleBlockUuid,
                  child: const Text('Bloquear'),
                ),
              ],
            ),
            const SizedBox(height: 8),
            if (blocked.isEmpty)
              const Text('Nenhum UUIDv7 bloqueado localmente.')
            else
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: blocked
                    .map((String uuid) => Row(
                          children: <Widget>[
                            Expanded(
                              child: Text(
                                uuid,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                            IconButton(
                              icon: const Icon(Icons.delete_outline, size: 20),
                              tooltip: 'Desbloquear',
                              onPressed: () async {
                                _blocklistService.unblockUuid(uuid);
                                await _vault.saveBlockedUuids(_blocklistService.blockedUuids);
                                if (!mounted) return;
                                setState(() {});
                              },
                            ),
                          ],
                        ))
                    .toList(),
              ),
          ],
        ),
      ),
    );
  }
}
