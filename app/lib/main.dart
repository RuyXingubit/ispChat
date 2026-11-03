import 'package:flutter/material.dart';

import 'core/crypto/identity_crypto_service.dart';
import 'core/discovery/node_discovery_service.dart';
import 'core/security/blocklist_service.dart';

void main() {
  runApp(const IspChatApp());
}

class IspChatApp extends StatelessWidget {
  const IspChatApp({super.key});

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
      home: const IspChatHomeScreen(),
    );
  }
}

class IspChatHomeScreen extends StatefulWidget {
  const IspChatHomeScreen({super.key});

  @override
  State<IspChatHomeScreen> createState() => _IspChatHomeScreenState();
}

class _IspChatHomeScreenState extends State<IspChatHomeScreen> {
  final IdentityCryptoService _cryptoService = IdentityCryptoService();
  final BlocklistService _blocklistService = BlocklistService();
  late final NodeDiscoveryService _discoveryService;

  final TextEditingController _restoreMnemonicController = TextEditingController();
  final TextEditingController _seedLinkController = TextEditingController();
  final TextEditingController _blockUuidController = TextEditingController();

  UserIdentity? _currentIdentity;
  String? _statusMessage;
  bool _isBusy = false;

  @override
  void initState() {
    super.initState();
    _discoveryService = NodeDiscoveryService(
      latencyProbe: (_) async => null,
    );
  }

  @override
  void dispose() {
    _restoreMnemonicController.dispose();
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
      if (!mounted) return;
      setState(() {
        _currentIdentity = identity;
        _statusMessage = 'Identidade auto-soberana gerada localmente (UUIDv7 + Ed25519/X25519).';
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
      if (!mounted) return;
      setState(() {
        _currentIdentity = identity;
        _statusMessage = 'Identidade restaurada deterministicamente com sucesso.';
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

  void _handleInjectSeed() {
    final String link = _seedLinkController.text.trim();
    if (link.isEmpty) return;
    try {
      final String added = _discoveryService.injectSocialSeedLink(link);
      _seedLinkController.clear();
      setState(() {
        _statusMessage = 'No semente injetado: $added';
      });
    } catch (e) {
      setState(() {
        _statusMessage = 'Link de seed invalido: $e';
      });
    }
  }

  void _handleBlockUuid() {
    final String uuid = _blockUuidController.text.trim();
    if (uuid.isEmpty) return;
    setState(() {
      _blocklistService.blockUuid(uuid);
      _blockUuidController.clear();
      _statusMessage = 'UUID bloqueado localmente no aparelho.';
    });
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
            ],
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
              '2. Descoberta de Nos & Bootstrap Social',
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
            else
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
              '3. Blocklist Local Privada (Anti-Assedio)',
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
                              onPressed: () {
                                setState(() {
                                  _blocklistService.unblockUuid(uuid);
                                });
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
