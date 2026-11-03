package br.dev.ispchat.node.dht;

import br.dev.ispchat.grpc.UserSessionInfo;
import br.dev.ispchat.node.security.CryptoService;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Armazenamento local da DHT Kademlia para mapeamento [UUIDv7 -> Array de Sessoes Ativas (Multi-Device)].
 * Toda escrita exige validacao criptografica anti-spoofing (deriveUuidV7(publicKey) == userUuid + assinatura Ed25519).
 */
@Service
public class DhtStoreService {

    private static final Logger log = LoggerFactory.getLogger(DhtStoreService.class);

    private final CryptoService cryptoService;

    /**
     * Mapa: userUuid (lowercase) -> (nodeAddress -> SessionRecord)
     * Suporta multiplos dispositivos/nos simultaneos para o mesmo UUIDv7.
     */
    private final Map<String, Map<String, SessionRecord>> userSessions = new ConcurrentHashMap<>();

    public DhtStoreService(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    /**
     * Valida matematicamente a assinatura Ed25519 e o vinculo do UUIDv7 antes de armazenar a sessao na DHT.
     */
    public boolean storeVerifiedSession(
            String userUuid,
            String nodeAddress,
            byte[] ed25519PublicKey,
            byte[] x25519PublicKey,
            byte[] signature,
            long timestampMillis) {

        boolean valid = cryptoService.verifyDhtSessionRegistration(
                userUuid, nodeAddress, timestampMillis, ed25519PublicKey, signature);

        if (!valid) {
            log.warn("Pacote DHT Store/Register rejeitado por falha na verificacao criptografica (UUID={})", userUuid);
            return false;
        }

        String normalizedUuid = userUuid.trim().toLowerCase();
        String normalizedAddress = nodeAddress.trim();
        byte[] safeX25519 = x25519PublicKey != null ? x25519PublicKey.clone() : new byte[0];

        SessionRecord candidate = new SessionRecord(
                normalizedUuid,
                normalizedAddress,
                ed25519PublicKey.clone(),
                safeX25519,
                signature.clone(),
                timestampMillis
        );

        userSessions.computeIfAbsent(normalizedUuid, k -> new ConcurrentHashMap<>())
                .compute(normalizedAddress, (addr, existing) -> {
                    if (existing != null && existing.timestampMillis() > candidate.timestampMillis()) {
                        return existing; // Ignora atualizacao mais antiga (protecao contra reordenacao de pacotes)
                    }
                    return candidate;
                });

        return true;
    }

    /**
     * Retorna todas as sessoes ativas conhecidas localmente para um UUIDv7.
     */
    public List<UserSessionInfo> getSessions(String targetUuid) {
        if (targetUuid == null || targetUuid.isBlank()) {
            return List.of();
        }
        Map<String, SessionRecord> sessionsByNode = userSessions.get(targetUuid.trim().toLowerCase());
        if (sessionsByNode == null || sessionsByNode.isEmpty()) {
            return List.of();
        }
        return sessionsByNode.values().stream()
                .map(record -> UserSessionInfo.newBuilder()
                        .setNodeAddress(record.nodeAddress())
                        .setEd25519PublicKey(ByteString.copyFrom(record.ed25519PublicKey()))
                        .setX25519PublicKey(ByteString.copyFrom(record.x25519PublicKey()))
                        .setTimestamp(record.timestampMillis())
                        .build())
                .toList();
    }

    public record SessionRecord(
            String userUuid,
            String nodeAddress,
            byte[] ed25519PublicKey,
            byte[] x25519PublicKey,
            byte[] signature,
            long timestampMillis) {}
}
