import 'dart:io';

/// Sonda real de latencia TCP na porta 443 (ou host:port) para eleicao do No ISP mais proximo
/// durante o Onboarding / Ping Fallback.
class TcpLatencyProbe {
  static const int defaultPort = 443;
  final Duration timeout;

  const TcpLatencyProbe({
    this.timeout = const Duration(seconds: 2),
  });

  /// Mede o tempo de handshake TCP em milissegundos ate o endereco informado.
  /// Retorna `null` se o host estiver inacessivel ou exceder o timeout.
  Future<int?> measureLatencyMs(String rawAddress) async {
    final (:String host, :int port) = parseHostAndPort(rawAddress);
    final Stopwatch stopwatch = Stopwatch()..start();
    Socket? socket;
    try {
      socket = await Socket.connect(host, port, timeout: timeout);
      stopwatch.stop();
      return stopwatch.elapsedMilliseconds;
    } catch (_) {
      return null;
    } finally {
      await socket?.close();
    }
  }

  /// Extrai host e porta (suportando IPv4, hostname e IPv6 entre colchetes `[2804:...]:443`).
  static ({String host, int port}) parseHostAndPort(String rawAddress) {
    final String trimmed = rawAddress.trim();
    if (trimmed.isEmpty) {
      throw ArgumentError('Endereco nao pode ser vazio');
    }

    if (trimmed.startsWith('[')) {
      final int closeBracket = trimmed.indexOf(']');
      if (closeBracket < 0) {
        throw FormatException('Endereco IPv6 invalido: $rawAddress');
      }
      final String host = trimmed.substring(1, closeBracket);
      if (closeBracket + 1 < trimmed.length && trimmed[closeBracket + 1] == ':') {
        final int port = int.parse(trimmed.substring(closeBracket + 2));
        return (host: host, port: port);
      }
      return (host: host, port: defaultPort);
    }

    final int firstColon = trimmed.indexOf(':');
    final int lastColon = trimmed.lastIndexOf(':');
    if (firstColon > 0 && firstColon == lastColon) {
      final String host = trimmed.substring(0, firstColon);
      final int port = int.parse(trimmed.substring(firstColon + 1));
      return (host: host, port: port);
    }

    return (host: trimmed, port: defaultPort);
  }

  /// Descobre o primeiro endereco IPv6 globalmente roteavel (nao-loopback e nao-link-local)
  /// das interfaces de rede do dispositivo para uso no Modo Doomsday (Conexao P2P Direta IPv6).
  static Future<String?> detectGlobalIpv6Address() async {
    try {
      final List<NetworkInterface> interfaces = await NetworkInterface.list(
        includeLoopback: false,
        type: InternetAddressType.IPv6,
      );
      for (final NetworkInterface iface in interfaces) {
        for (final InternetAddress addr in iface.addresses) {
          if (!addr.isLoopback && !addr.isLinkLocal && !addr.isMulticast) {
            return addr.address;
          }
        }
      }
    } catch (_) {
      // Plataforma sem permissao de listagem de interfaces
    }
    return null;
  }
}
