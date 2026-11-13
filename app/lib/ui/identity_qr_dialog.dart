import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:app/core/crypto/identity_crypto_service.dart';
import 'package:app/core/grpc/generated/ispchat.pb.dart';
import 'package:qr_flutter/qr_flutter.dart';

/// Displays the user's cryptographically signed contact URI (`ispchat://contact?...`)
/// as a scannable QR code and copyable deep link.
class IdentityQrDialog extends StatelessWidget {
  final String contactUri;
  final String userUuid;
  final VerificationCredential? verificationCredential;

  const IdentityQrDialog({
    super.key,
    required this.contactUri,
    required this.userUuid,
    this.verificationCredential,
  });

  @override
  Widget build(BuildContext context) {
    final vc = verificationCredential;
    final hasVerified = vc != null && vc.credentialId.isNotEmpty;

    return AlertDialog(
      title: Row(
        children: [
          const Icon(Icons.qr_code_2),
          const SizedBox(width: 8),
          const Expanded(
            child: Text(
              'Cartão de Identidade',
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
      content: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 360),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              if (hasVerified)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: Chip(
                    avatar: const Icon(
                      Icons.verified,
                      color: Colors.green,
                      size: 18,
                    ),
                    label: Text(
                      vc.publicCnpj.isNotEmpty
                          ? 'CNPJ ${vc.publicCnpj} (${vc.verifyingIspName})'
                          : 'CPF Verificado (${vc.verifyingIspName})',
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ),
              Container(
                color: Colors.white,
                padding: const EdgeInsets.all(10),
                child: QrImageView(
                  data: contactUri,
                  size: 196,
                  version: QrVersions.auto,
                ),
              ),
              const SizedBox(height: 12),
              Text(
                'UUIDv7: ${CanonicalTranscripts.canonicalUuid(userUuid)}',
                style: Theme.of(context).textTheme.bodySmall,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 8),
              SelectableText(
                contactUri,
                key: const Key('txt_qr_dialog_uri'),
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      fontFamily: 'monospace',
                    ),
              ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton.icon(
          onPressed: () {
            Clipboard.setData(ClipboardData(text: contactUri));
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(content: Text('Link de contato copiado.')),
            );
          },
          icon: const Icon(Icons.copy, size: 18),
          label: const Text('Copiar URI'),
        ),
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Fechar'),
        ),
      ],
    );
  }
}
