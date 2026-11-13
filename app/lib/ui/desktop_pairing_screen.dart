import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:app/core/config/network_bootstrap_config.dart';
import 'package:app/core/crypto/identity_crypto_service.dart';
import 'package:app/core/grpc/generated/ispchat.pb.dart';
import 'package:app/core/grpc/ispchat_node_client.dart';
import 'package:app/core/storage/secure_local_vault.dart';
import 'package:app/ui/shared_widgets.dart';
import 'package:qr_flutter/qr_flutter.dart';

/// Multi-device QR & URI pairing + `RevokeDevice` management screen.
///
/// Works safely on both Desktop (QR display + URI paste) and Mobile Root
/// (optional hardware camera scanner via `mobile_scanner` + URI paste fallback).
class DesktopPairingScreen extends StatefulWidget {
  final SecureLocalVault vault;
  final IdentityCryptoService crypto;
  final IspChatNodeClient nodeClient;
  final IdentityBundle? rootIdentity;
  final VerificationCredential? currentCredential;
  final HomeNodeClaim? currentHomeClaim;
  final NetworkBootstrapConfig bootstrapConfig;
  final Uint8List? pinnedRootPubOverride;
  final Future<void> Function(DevicePairGrantBundle grant)? onPairedGrantApplied;

  const DesktopPairingScreen({
    super.key,
    required this.vault,
    required this.crypto,
    required this.nodeClient,
    this.rootIdentity,
    this.currentCredential,
    this.currentHomeClaim,
    this.bootstrapConfig = const NetworkBootstrapConfig(),
    this.pinnedRootPubOverride,
    this.onPairedGrantApplied,
  });

  @override
  State<DesktopPairingScreen> createState() => _DesktopPairingScreenState();
}

class _DesktopPairingScreenState extends State<DesktopPairingScreen> {
  final TextEditingController _grantInputController = TextEditingController();
  final TextEditingController _pairReqInputController = TextEditingController();
  final TextEditingController _deviceLabelController = TextEditingController();

  LocalDeviceIdentity? _localDesktopIdentity;
  String? _desktopPairReqUri;
  String? _generatedGrantUri;
  List<PairedDeviceRecord> _pairedDevices = const <PairedDeviceRecord>[];
  String? _statusMessage;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _loadInitialState();
  }

  @override
  void dispose() {
    _grantInputController.dispose();
    _pairReqInputController.dispose();
    _deviceLabelController.dispose();
    super.dispose();
  }

  Future<void> _loadInitialState() async {
    final deviceId = await widget.vault.loadOrCreateLocalDeviceIdentity(
      role: DeviceRole.DESKTOP,
      cryptoOverride: widget.crypto,
    );
    final devices = await widget.vault.loadPairedDevices();
    if (!mounted) return;
    setState(() {
      _localDesktopIdentity = deviceId;
      _desktopPairReqUri = widget.crypto.buildDevicePairRequestUri(
        desktopIdentity: deviceId,
      );
      _pairedDevices = devices;
    });
  }

  Future<void> _applyPairGrant() async {
    final raw = _grantInputController.text.trim();
    if (raw.isEmpty || _localDesktopIdentity == null) return;
    setState(() {
      _busy = true;
      _statusMessage = null;
    });
    try {
      final rootPub = widget.pinnedRootPubOverride ??
          widget.bootstrapConfig.decodePinnedRootPublicKeyBytes();
      final grant = await widget.crypto.parseAndVerifyDevicePairGrantUri(
        raw,
        pinnedRootEd25519Pub: rootPub,
      );
      if (CanonicalTranscripts.canonicalUuid(grant.delegationCert.deviceId) !=
          CanonicalTranscripts.canonicalUuid(_localDesktopIdentity!.deviceId)) {
        throw const FormatException(
          'Este código pair-grant pertence a outro ID de dispositivo.',
        );
      }

      final edSeed =
          await _localDesktopIdentity!.ed25519KeyPair.extractPrivateKeyBytes();
      final xSeed =
          await _localDesktopIdentity!.x25519KeyPair.extractPrivateKeyBytes();
      await widget.vault.saveLocalDeviceRecord(
        StoredLocalDeviceRecord(
          deviceId: _localDesktopIdentity!.deviceId,
          role: grant.delegationCert.role,
          ed25519Seed: Uint8List.fromList(edSeed),
          x25519Seed: Uint8List.fromList(xSeed),
          delegationCert: grant.delegationCert,
          pairedUserUuid: grant.delegationCert.userUuid,
          rootEd25519Pub: grant.rootEd25519Pub,
          rootX25519Pub: grant.rootX25519Pub,
        ),
      );
      if (grant.verificationCredential != null) {
        await widget.vault.saveVerificationCredential(
          grant.verificationCredential!,
        );
      }
      if (grant.homeNodeAddress != null &&
          grant.homeNodeAddress!.trim().isNotEmpty) {
        await widget.vault.saveBootstrapSeeds([grant.homeNodeAddress!.trim()]);
      }
      if (widget.onPairedGrantApplied != null) {
        await widget.onPairedGrantApplied!(grant);
      }

      if (!mounted) return;
      setState(() {
        _statusMessage =
            'Dispositivo pareado com a conta ${grant.delegationCert.userUuid}.';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _statusMessage = 'Falha ao aplicar pair-grant: $e';
      });
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  Future<void> _authorizeDesktopFromUri() async {
    final root = widget.rootIdentity;
    if (root == null) return;
    final raw = _pairReqInputController.text.trim();
    if (raw.isEmpty) return;

    setState(() {
      _busy = true;
      _statusMessage = null;
    });
    try {
      final parsed = widget.crypto.parseDevicePairRequestUri(raw);
      final nowMs = DateTime.now().millisecondsSinceEpoch;
      final cert = await widget.crypto.signDeviceDelegationCert(
        rootIdentity: root,
        targetDeviceId: parsed.deviceId,
        role: parsed.role,
        deviceEd25519Pub: parsed.deviceEd25519Pub,
        deviceX25519Pub: parsed.deviceX25519Pub,
        nowMs: nowMs,
      );
      final homeAddr = widget.currentHomeClaim?.homeNodeAddress ??
          widget.currentCredential?.homeNodeAddress ??
          widget.nodeClient.connectedNodeAddress;
      final grantUri = widget.crypto.buildDevicePairGrantUri(
        delegationCert: cert,
        rootEd25519Pub: root.ed25519PublicKeyBytes,
        rootX25519Pub: root.x25519PublicKeyBytes,
        homeNodeAddress: homeAddr,
        verificationCredential: widget.currentCredential,
      );

      final label = _deviceLabelController.text.trim().isEmpty
          ? 'Desktop ${parsed.deviceId.substring(0, 8)}'
          : _deviceLabelController.text.trim();
      await widget.vault.savePairedDevice(
        PairedDeviceRecord(
          deviceId: parsed.deviceId,
          label: label,
          role: parsed.role,
          deviceEd25519Pub: parsed.deviceEd25519Pub,
          deviceX25519Pub: parsed.deviceX25519Pub,
          issuedAtMs: cert.issuedAtMs.toInt(),
          expiresAtMs: cert.expiresAtMs.toInt(),
        ),
      );
      final updatedList = await widget.vault.loadPairedDevices();
      if (!mounted) return;
      setState(() {
        _generatedGrantUri = grantUri;
        _pairedDevices = updatedList;
        _statusMessage = 'Certificado DeviceDelegationCert gerado para $label.';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _statusMessage = 'Erro ao autorizar dispositivo: $e';
      });
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  Future<void> _revokeDevice(PairedDeviceRecord device) async {
    final root = widget.rootIdentity;
    if (root == null) {
      setState(() {
        _statusMessage =
            'A chave Root (mnemonic) precisa estar desbloqueada para revogar dispositivos.';
      });
      return;
    }
    setState(() {
      _busy = true;
      _statusMessage = null;
    });
    try {
      if (widget.nodeClient.isConnected) {
        final req = await widget.crypto.buildSignedRevokeDeviceRequest(
          rootIdentity: root,
          revokedDeviceId: device.deviceId,
          reason: 'user_revoked_from_root',
        );
        await widget.nodeClient.revokeDevice(req);
      }
      await widget.vault.markPairedDeviceRevoked(device.deviceId);
      final updated = await widget.vault.loadPairedDevices();
      if (!mounted) return;
      setState(() {
        _pairedDevices = updated;
        _statusMessage = 'Dispositivo ${device.label} revogado.';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _statusMessage = 'Erro na revogação: $e';
      });
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  Future<void> _scanIntoController(TextEditingController ctrl) async {
    final scanned = await openMobileQrScannerDialog(
      context,
      title: 'Escanear QR de Pareamento',
    );
    if (scanned != null && mounted) {
      setState(() => ctrl.text = scanned);
    }
  }

  @override
  Widget build(BuildContext context) {
    final reqUri = _desktopPairReqUri;
    final hasRoot = widget.rootIdentity != null;

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Dispositivos & Pareamento',
          overflow: TextOverflow.ellipsis,
        ),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 760),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (_statusMessage != null)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: MaterialBanner(
                      content: Text(_statusMessage!),
                      actions: [
                        TextButton(
                          onPressed: () =>
                              setState(() => _statusMessage = null),
                          child: const Text('OK'),
                        ),
                      ],
                    ),
                  ),
                _buildDesktopPairSection(context, reqUri),
                const SizedBox(height: 16),
                if (hasRoot) ...[
                  _buildRootAuthorizeSection(context),
                  const SizedBox(height: 16),
                ],
                _buildPairedDevicesList(context, hasRoot),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildDesktopPairSection(BuildContext context, String? reqUri) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '1. Vincular este dispositivo (Desktop / Secundário)',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            Text(
              'Exiba o QR ou copie o link pair-req no celular Root para receber um DeviceDelegationCert assinado sem exportar sua frase BIP39.',
              style: Theme.of(context).textTheme.bodySmall,
            ),
            const SizedBox(height: 12),
            if (reqUri != null) ...[
              Center(
                child: Container(
                  color: Colors.white,
                  padding: const EdgeInsets.all(8),
                  child: QrImageView(data: reqUri, size: 156),
                ),
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(
                    child: SelectableText(
                      reqUri,
                      key: const Key('txt_pair_req_uri'),
                      maxLines: 2,
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                            fontFamily: 'monospace',
                          ),
                    ),
                  ),
                  IconButton(
                    tooltip: 'Copiar pair-req URI',
                    onPressed: () {
                      Clipboard.setData(ClipboardData(text: reqUri));
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('URI pair-req copiada.')),
                      );
                    },
                    icon: const Icon(Icons.copy),
                  ),
                ],
              ),
            ],
            const SizedBox(height: 12),
            TextField(
              key: const Key('input_pair_grant_uri'),
              controller: _grantInputController,
              decoration: InputDecoration(
                labelText: 'Colar resposta ispchat://pair-grant?...',
                border: const OutlineInputBorder(),
                suffixIcon: isMobileCameraSupported()
                    ? IconButton(
                        tooltip: 'Escanear QR pair-grant',
                        icon: const Icon(Icons.qr_code_scanner),
                        onPressed: () =>
                            _scanIntoController(_grantInputController),
                      )
                    : null,
              ),
            ),
            const SizedBox(height: 8),
            FilledButton.icon(
              key: const Key('btn_apply_pair_grant'),
              onPressed: _busy ? null : _applyPairGrant,
              icon: const Icon(Icons.link),
              label: const Text('Aplicar Autorização'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildRootAuthorizeSection(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '2. Autorizar um novo Desktop (Celular Root)',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            TextField(
              controller: _deviceLabelController,
              decoration: const InputDecoration(
                labelText: 'Nome do dispositivo (opcional)',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 8),
            TextField(
              key: const Key('input_pair_req_uri'),
              controller: _pairReqInputController,
              decoration: InputDecoration(
                labelText: 'Colar ou escanear ispchat://pair-req?...',
                border: const OutlineInputBorder(),
                suffixIcon: isMobileCameraSupported()
                    ? IconButton(
                        tooltip: 'Escanear QR do Desktop',
                        icon: const Icon(Icons.qr_code_scanner),
                        onPressed: () =>
                            _scanIntoController(_pairReqInputController),
                      )
                    : null,
              ),
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                FilledButton.icon(
                  key: const Key('btn_authorize_desktop'),
                  onPressed: _busy ? null : _authorizeDesktopFromUri,
                  icon: const Icon(Icons.verified_user),
                  label: const Text('Assinar Delegação (Ed25519)'),
                ),
                if (isMobileCameraSupported())
                  OutlinedButton.icon(
                    onPressed: () =>
                        _scanIntoController(_pairReqInputController),
                    icon: const Icon(Icons.camera_alt),
                    label: const Text('Escanear QR'),
                  ),
              ],
            ),
            if (_generatedGrantUri != null) ...[
              const SizedBox(height: 12),
              const Divider(),
              Center(
                child: Container(
                  color: Colors.white,
                  padding: const EdgeInsets.all(8),
                  child: QrImageView(data: _generatedGrantUri!, size: 156),
                ),
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(
                    child: SelectableText(
                      _generatedGrantUri!,
                      key: const Key('txt_pair_grant_uri'),
                      maxLines: 2,
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                            fontFamily: 'monospace',
                          ),
                    ),
                  ),
                  IconButton(
                    tooltip: 'Copiar pair-grant URI',
                    onPressed: () {
                      Clipboard.setData(
                        ClipboardData(text: _generatedGrantUri!),
                      );
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('URI pair-grant copiada.')),
                      );
                    },
                    icon: const Icon(Icons.copy),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildPairedDevicesList(BuildContext context, bool hasRoot) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '3. Dispositivos Delegados',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            if (_pairedDevices.isEmpty)
              Text(
                'Nenhum dispositivo secundário pareado.',
                key: const Key('txt_no_paired_devices'),
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
              )
            else
              ..._pairedDevices.map(
                (device) => ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: Icon(
                    device.revoked ? Icons.block : Icons.laptop_mac,
                    color: device.revoked ? Colors.red : null,
                  ),
                  title: Text(
                    device.label,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  subtitle: Text(
                    '${device.deviceId} • ${device.revoked ? 'REVOGADO' : 'Ativo'}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  trailing: device.revoked || !hasRoot
                      ? null
                      : TextButton(
                          key: Key('btn_revoke_device_${device.deviceId}'),
                          onPressed:
                              _busy ? null : () => _revokeDevice(device),
                          child: const Text(
                            'Revogar',
                            style: TextStyle(color: Colors.red),
                          ),
                        ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
