/// Servico de Bloqueio Local Privado (Anti-Assedio / Anti-Spam).
///
/// Conforme o PRD_MVP.md, o bloqueio ocorre exclusivamente no aparelho do usuario:
/// o provedor nao sabe quem foi bloqueado, e chamadas (RING) ou mensagens vindas
/// de um UUIDv7 bloqueado sao descartadas silenciosamente antes de tocar ou notificar.
class BlocklistService {
  final Set<String> _blockedUuids = <String>{};

  Set<String> get blockedUuids => Set<String>.unmodifiable(_blockedUuids);

  /// Adiciona um UUIDv7 a blocklist local.
  void blockUuid(String userUuid) {
    final String normalized = userUuid.trim().toLowerCase();
    if (normalized.isNotEmpty) {
      _blockedUuids.add(normalized);
    }
  }

  /// Remove um UUIDv7 da blocklist local.
  void unblockUuid(String userUuid) {
    final String normalized = userUuid.trim().toLowerCase();
    _blockedUuids.remove(normalized);
  }

  /// Verifica se um UUIDv7 esta bloqueado.
  bool isBlocked(String userUuid) {
    return _blockedUuids.contains(userUuid.trim().toLowerCase());
  }

  /// Decide se um evento de entrada (RING, TEXT, SDP_OFFER) deve ser processado
  /// ou descartado silenciosamente.
  bool shouldAllowIncomingEvent(String senderUuid) {
    if (senderUuid.trim().isEmpty) {
      return false;
    }
    return !isBlocked(senderUuid);
  }
}
