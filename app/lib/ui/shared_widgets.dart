import 'dart:io' show Platform;

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:app/core/grpc/generated/ispchat.pbenum.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

/// Returns true strictly on Android and iOS where camera QR scanning applies.
bool isMobileCameraSupported() {
  if (kIsWeb) return false;
  return Platform.isAndroid || Platform.isIOS;
}

/// Opens a modal QR scanner on supported mobile platforms and resolves with the
/// first scanned raw barcode value, or `null` if cancelled/unsupported.
Future<String?> openMobileQrScannerDialog(
  BuildContext context, {
  String title = 'Escanear QR Code',
}) {
  if (!isMobileCameraSupported()) {
    return Future<String?>.value(null);
  }
  var resolved = false;
  return showDialog<String>(
    context: context,
    builder: (dialogCtx) {
      return AlertDialog(
        title: Text(title, overflow: TextOverflow.ellipsis),
        content: SizedBox(
          width: 280,
          height: 280,
          child: ClipRRect(
            borderRadius: BorderRadius.circular(12),
            child: MobileScanner(
              onDetect: (BarcodeCapture capture) {
                if (resolved) return;
                for (final code in capture.barcodes) {
                  final raw = code.rawValue?.trim();
                  if (raw != null && raw.isNotEmpty) {
                    resolved = true;
                    Navigator.of(dialogCtx).pop(raw);
                    return;
                  }
                }
              },
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogCtx).pop(),
            child: const Text('Cancelar'),
          ),
        ],
      );
    },
  );
}

/// Compact, overflow-safe verification badge distinguishing Verified (CPF/CNPJ)
/// contacts from Unverified self-sovereign contacts.
class VerifiedIdentityBadge extends StatelessWidget {
  final bool isVerified;
  final DocumentType? docType;
  final String? publicCnpj;
  final String? verifyingIspName;
  final bool compact;

  const VerifiedIdentityBadge({
    super.key,
    required this.isVerified,
    this.docType,
    this.publicCnpj,
    this.verifyingIspName,
    this.compact = false,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final label = !isVerified
        ? 'Não verificado'
        : docType == DocumentType.CNPJ &&
                publicCnpj != null &&
                publicCnpj!.isNotEmpty
            ? 'CNPJ $publicCnpj'
                '${verifyingIspName != null && verifyingIspName!.isNotEmpty ? ' • $verifyingIspName' : ''}'
            : 'CPF Verificado'
                '${verifyingIspName != null && verifyingIspName!.isNotEmpty ? ' • $verifyingIspName' : ''}';

    final bgColor = isVerified
        ? Colors.green.withValues(alpha: 0.14)
        : theme.colorScheme.surfaceContainerHighest;
    final fgColor = isVerified
        ? Colors.green.shade800
        : theme.colorScheme.onSurfaceVariant;

    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: compact ? 6 : 8,
        vertical: compact ? 2 : 4,
      ),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(999),
        border: Border.all(
          color: isVerified
              ? Colors.green.withValues(alpha: 0.4)
              : theme.colorScheme.outlineVariant,
        ),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            isVerified ? Icons.verified : Icons.shield_outlined,
            size: compact ? 13 : 15,
            color: fgColor,
          ),
          const SizedBox(width: 4),
          Flexible(
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: (compact
                      ? theme.textTheme.labelSmall
                      : theme.textTheme.labelMedium)
                  ?.copyWith(color: fgColor, fontWeight: FontWeight.w600),
            ),
          ),
        ],
      ),
    );
  }
}
