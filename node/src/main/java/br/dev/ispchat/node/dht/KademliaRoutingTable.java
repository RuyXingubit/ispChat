package br.dev.ispchat.node.dht;

import br.dev.ispchat.grpc.NodeInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tabela de roteamento Kademlia baseada na metrica de distancia XOR de 128 bits.
 */
@Component
public class KademliaRoutingTable {

    public static final int DEFAULT_K_BUCKET_SIZE = 20;

    private final String localNodeId;
    private final Map<String, NodeContact> knownNodes = new ConcurrentHashMap<>();

    public KademliaRoutingTable(
            @Value("${ispchat.dht.node-id:01950000-0000-7000-8000-000000000001}") String localNodeId) {
        this.localNodeId = Objects.requireNonNull(localNodeId, "localNodeId nao pode ser nulo");
    }

    public String getLocalNodeId() {
        return localNodeId;
    }

    /**
     * Registra ou atualiza um no par na tabela de roteamento Kademlia.
     */
    public void registerOrUpdateNode(String nodeId, String nodeAddress) {
        if (nodeId == null || nodeId.isBlank() || nodeAddress == null || nodeAddress.isBlank()) {
            return;
        }
        String normalizedId = nodeId.trim();
        if (normalizedId.equalsIgnoreCase(localNodeId)) {
            return; // Nao adiciona o proprio no na lista de pares remotos
        }
        knownNodes.put(normalizedId, new NodeContact(normalizedId, nodeAddress.trim(), System.currentTimeMillis()));
    }

    /**
     * Remove um no par que falhou repetidamente nas conexoes.
     */
    public void evictNode(String nodeId) {
        if (nodeId != null) {
            knownNodes.remove(nodeId.trim());
        }
    }

    /**
     * Retorna ate K nos mais proximos do targetId segundo a metrica de distancia XOR Kademlia.
     */
    public List<NodeInfo> findClosestNodes(String targetId, int limit) {
        if (targetId == null || targetId.isBlank() || limit <= 0) {
            return List.of();
        }
        BigInteger targetKey = to128BitKey(targetId);

        return knownNodes.values().stream()
                .sorted(Comparator.comparing(contact -> targetKey.xor(to128BitKey(contact.nodeId()))))
                .limit(limit)
                .map(contact -> NodeInfo.newBuilder()
                        .setNodeId(contact.nodeId())
                        .setNodeAddress(contact.nodeAddress())
                        .build())
                .toList();
    }

    public List<NodeInfo> findClosestNodes(String targetId) {
        return findClosestNodes(targetId, DEFAULT_K_BUCKET_SIZE);
    }

    /**
     * Calcula a distancia XOR (128 bits unsigned) entre dois identificadores (UUID ou string).
     */
    public BigInteger calculateXorDistance(String idA, String idB) {
        return to128BitKey(idA).xor(to128BitKey(idB));
    }

    /**
     * Converte um UUID (ou string arbitraria via SHA-256 truncado em 128 bits) em um inteiro positivo de 128 bits.
     */
    public static BigInteger to128BitKey(String identifier) {
        Objects.requireNonNull(identifier, "identifier nao pode ser nulo");
        String trimmed = identifier.trim();
        try {
            UUID uuid = UUID.fromString(trimmed);
            ByteBuffer buffer = ByteBuffer.allocate(16);
            buffer.putLong(uuid.getMostSignificantBits());
            buffer.putLong(uuid.getLeastSignificantBits());
            return new BigInteger(1, buffer.array());
        } catch (IllegalArgumentException e) {
            try {
                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                byte[] digest = sha256.digest(trimmed.getBytes(StandardCharsets.UTF_8));
                return new BigInteger(1, Arrays.copyOf(digest, 16));
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException("SHA-256 indisponivel", ex);
            }
        }
    }

    public int size() {
        return knownNodes.size();
    }

    public record NodeContact(String nodeId, String nodeAddress, long lastSeenMillis) {}
}
