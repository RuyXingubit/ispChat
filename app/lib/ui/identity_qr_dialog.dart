import 'package:flutter/material.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../core/crypto/identity_crypto_service.dart';
import '../core/discovery/tcp_latency_probe.dart';

/// Dialogo responsivo que exibe o QR Code da identidade do usuario e permite ativar
/// o Modo Doomsday (incluindo o IPv6 global roteavel do aparelho para conexao P2P direta).
class IdentityQrDialog extends StatefulWidget {
  final UserIdentity identity;
  final IdentityCryptoService cryptoService;

  const IdentityQrDialog({
    super.key,
    required this.identity,
    required this.cryptoService,
  });

  @override
  State<IdentityQrDialog> createState() => _IdentityQrDialogState();
}

class _IdentityQrDialogState extends State<IdentityQrDialog> {
  bool _includeDoomsdayIpv6 = false;
  String? _detectedIpv6;
  final TextEditingController _customIpv6Controller = TextEditingController();

  @override
  void initState() {
    super.initState();
    _detectLocalIpv6();
  }

  @override
  void dispose() {
    _customIpv6Controller.dispose();
    super.dispose();
  }

  Future<void> _detectLocalIpv6() async {
    final String? ipv6 = await TcpLatencyProbe.detectGlobalIpv6Address();
    if (!mounted) return;
    if (ipv6 != null) {
      setState(() {
        _detectedIpv6 = ipv6;
        _customIpv6Controller.text = ipv6;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final String? ipv6ToUse = _includeDoomsdayIpv6
        ? (_customIpv6Controller.text.trim().isNotEmpty
            ? _customIpv6Controller.text.trim()
            : _detectedIpv6)
        : null;

    final String shareUri = widget.cryptoService.buildShareableContactUri(
      widget.identity,
      directIpv6Address: ipv6ToUse,
    );

    return Dialog(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 420),
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              Row(
                children: <Widget>[
                  const Icon(Icons.qr_code_2),
                  const SizedBox(width: 8),
                  const Expanded(
                    child: Text(
                      'QR Code de Identidade & Modo Doomsday',
                      style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close),
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Center(
                child: Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: QrImageView(
                    data: shareUri,
                    version: QrVersions.auto,
                    size: 200.0,
                  ),
                ),
              ),
              const SizedBox(height: 12),
              SwitchListTile(
                key: const Key('switch_doomsday_ipv6'),
                contentPadding: EdgeInsets.zero,
                title: const Text(
                  'Ativar Modo Doomsday (IPv6 P2P Direto)',
                  overflow: TextOverflow.ellipsis,
                ),
                subtitle: const Text(
                  'Embute seu IPv6 no QR Code para tunel direto sem servidores.',
                ),
                value: _includeDoomsdayIpv6,
                onChanged: (bool val) {
                  setState(() {
                    _includeDoomsdayIpv6 = val;
                  });
                },
              ),
              if (_includeDoomsdayIpv6) ...<Widget>[
                const SizedBox(height: 8),
                TextField(
                  key: const Key('input_doomsday_ipv6'),
                  controller: _customIpv6Controller,
                  decoration: const InputDecoration(
                    labelText: 'Endereco IPv6 do Dispositivo',
                    hintText: 'Ex: 2804:14d:1:2::99',
                    border: OutlineInputBorder(),
                  ),
                  onChanged: (_) => setState(() {}),
                ),
              ],
              const SizedBox(height: 12),
              const Text(
                'Link Magico Gerado:',
                style: TextStyle(fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 4),
              SelectableText(
                shareUri,
                key: const Key('txt_dialog_share_uri'),
                maxLines: 3,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
