import 'dart:convert';
import 'dart:typed_data';

/// Immutable network bootstrap and trust-root configuration for ispChat clients.
///
/// Production builds inject the Root Offline Ed25519 public key via
/// `--dart-define=ISPCHAT_ROOT_PUBKEY_B64=...`. When unset, client verification
/// stays fail-closed unless an explicit key is supplied (for example in tests).
class NetworkBootstrapConfig {
  static const String _envRootPubKeyBase64 = String.fromEnvironment(
    'ISPCHAT_ROOT_PUBKEY_B64',
    defaultValue: '',
  );
  static const String _envSrvDomain = String.fromEnvironment(
    'ISPCHAT_SRV_DOMAIN',
    defaultValue: '_ispchat._tcp.ispchat.dev.br',
  );
  static const String _envPrincipalFallback = String.fromEnvironment(
    'ISPCHAT_PRINCIPAL_ADDRESS',
    defaultValue: 'principal.ispchat.dev.br:443',
  );

  static const List<String> defaultDohResolvers = <String>[
    'https://cloudflare-dns.com/dns-query',
    'https://dns.google/resolve',
  ];

  static const List<int> defaultReconnectBackoffSeconds = <int>[
    1,
    2,
    4,
    8,
    16,
    30,
  ];

  final String pinnedRootEd25519PublicKeyBase64;
  final String srvDomain;
  final String principalFallbackAddress;
  final List<String> dohEndpoints;
  final Duration dohTimeout;
  final Duration credentialRenewWindow;

  const NetworkBootstrapConfig({
    this.pinnedRootEd25519PublicKeyBase64 = _envRootPubKeyBase64,
    this.srvDomain = _envSrvDomain,
    this.principalFallbackAddress = _envPrincipalFallback,
    this.dohEndpoints = defaultDohResolvers,
    this.dohTimeout = const Duration(seconds: 5),
    this.credentialRenewWindow = const Duration(days: 30),
  });

  /// Returns whether a 32-byte pinned Root Ed25519 public key is configured.
  bool get hasPinnedRootKey => pinnedRootEd25519PublicKeyBase64.trim().isNotEmpty;

  /// Decodes the pinned Root Ed25519 public key into 32 raw bytes, or returns
  /// `null` if no Root public key was configured.
  ///
  /// Throws [FormatException] if a configured key does not decode to 32 bytes.
  Uint8List? decodePinnedRootPublicKeyBytes() {
    final trimmed = pinnedRootEd25519PublicKeyBase64.trim();
    if (trimmed.isEmpty) {
      return null;
    }
    final normalized = base64.normalize(
      trimmed.replaceAll('-', '+').replaceAll('_', '/'),
    );
    final decoded = base64Decode(normalized);
    if (decoded.length != 32) {
      throw FormatException(
        'Root Ed25519 public key must be 32 bytes (got ${decoded.length}).',
      );
    }
    return Uint8List.fromList(decoded);
  }
}
