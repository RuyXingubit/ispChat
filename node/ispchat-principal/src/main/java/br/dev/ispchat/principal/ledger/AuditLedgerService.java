package br.dev.ispchat.principal.ledger;

import br.dev.ispchat.common.crypto.CanonicalTranscripts;
import br.dev.ispchat.common.crypto.CanonicalTranscripts.SealedEscrowRecord;
import br.dev.ispchat.principal.config.PrincipalKeyMaterial;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Ledger imutavel de responsabilizacao (verification_audit_ledger) encadeado por SHA-256
 * (ISPCHAT-LEDGER-ROW-V1) e selado assimetricamente via X25519 + HKDF-SHA256 + ChaCha20-Poly1305
 * para a chave Root Escrow offline (air-gapped).
 *
 * Toda insercao adquire SELECT pg_advisory_xact_lock(8443001) dentro da transacao PostgreSQL,
 * impedindo forks concorrentes na cadeia de hashes.
 */
@Service
public class AuditLedgerService {

    public static final long LEDGER_ADVISORY_LOCK_ID = 8_443_001L;
    public static final byte[] GENESIS_PREV_HASH = new byte[CanonicalTranscripts.RAW_KEY_LENGTH];

    /**
     * Payload estruturado de responsabilizacao selado em JSON UTF-8 dentro de sealed_audit_blob.
     * Nunca e gravado em texto claro em disco nem exibido em logs.
     */
    public record AuditAccountabilityPayload(
            String actionType,
            String docType,
            String userDoc,
            String userUuid,
            String oldUserUuid,
            String collaboratorCpf,
            String collaboratorUuid,
            String ispCnpj,
            List<String> partnerUuids,
            String userConsentSigBase64,
            String collaboratorSigBase64,
            long attestedAtMs) {
    }

    /**
     * Representacao tipada de uma linha persistida em verification_audit_ledger.
     */
    public record LedgerEntryRecord(
            long seq,
            String entryId,
            String actionType,
            String targetUuid,
            byte[] docBlindIndex,
            String ispCnpj,
            String collaboratorUuid,
            byte[] ephemeralX25519Pub,
            byte[] sealedAuditBlob,
            byte[] prevEntryHash,
            byte[] entryHash,
            long createdAtEpochMs) {

        public ExportedLedgerEntryJson toExportedJson() {
            return new ExportedLedgerEntryJson(
                    seq,
                    entryId,
                    actionType,
                    targetUuid,
                    Base64.getEncoder().encodeToString(docBlindIndex),
                    ispCnpj,
                    collaboratorUuid != null ? collaboratorUuid : "",
                    Base64.getEncoder().encodeToString(ephemeralX25519Pub),
                    Base64.getEncoder().encodeToString(sealedAuditBlob),
                    Base64.getEncoder().encodeToString(prevEntryHash),
                    Base64.getEncoder().encodeToString(entryHash),
                    createdAtEpochMs);
        }
    }

    /**
     * Formato JSON portavel para exportacao de auditoria e verificacao offline via OfflineGovernanceCli.
     */
    public record ExportedLedgerEntryJson(
            long seq,
            String entryId,
            String actionType,
            String targetUuid,
            String docBlindIndexBase64,
            String ispCnpj,
            String collaboratorUuid,
            String ephemeralX25519PubBase64,
            String sealedAuditBlobBase64,
            String prevEntryHashBase64,
            String entryHashBase64,
            long createdAtEpochMs) {

        public LedgerEntryRecord toRecord() {
            return new LedgerEntryRecord(
                    seq,
                    entryId,
                    actionType,
                    targetUuid,
                    Base64.getDecoder().decode(docBlindIndexBase64),
                    ispCnpj,
                    collaboratorUuid != null ? collaboratorUuid : "",
                    Base64.getDecoder().decode(ephemeralX25519PubBase64),
                    Base64.getDecoder().decode(sealedAuditBlobBase64),
                    Base64.getDecoder().decode(prevEntryHashBase64),
                    Base64.getDecoder().decode(entryHashBase64),
                    createdAtEpochMs);
        }
    }

    private record TailState(long seq, byte[] entryHash) {
    }

    private final JdbcTemplate jdbcTemplate;
    private final PrincipalKeyMaterial keyMaterial;
    private final ObjectMapper objectMapper;

    public AuditLedgerService(
            JdbcTemplate jdbcTemplate,
            PrincipalKeyMaterial keyMaterial,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate nao pode ser nulo");
        this.keyMaterial = Objects.requireNonNull(keyMaterial, "keyMaterial nao pode ser nulo");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper nao pode ser nulo");
    }

    /**
     * Anexa atomicamente um registro selado ao verification_audit_ledger sob lock transacional.
     */
    @Transactional
    public LedgerEntryRecord appendSealedEntry(
            String actionType,
            String targetUuid,
            byte[] docBlindIndex,
            String ispCnpj,
            String collaboratorUuidOrNull,
            AuditAccountabilityPayload payload,
            long createdAtEpochMs) {
        Objects.requireNonNull(actionType, "actionType nao pode ser nulo");
        Objects.requireNonNull(targetUuid, "targetUuid nao pode ser nulo");
        Objects.requireNonNull(docBlindIndex, "docBlindIndex nao pode ser nulo");
        Objects.requireNonNull(ispCnpj, "ispCnpj nao pode ser nulo");
        Objects.requireNonNull(payload, "payload nao pode ser nulo");

        String normalizedCnpj = CanonicalTranscripts.normalizeAndValidateCnpj(ispCnpj);
        UUID targetId = UUID.fromString(targetUuid.trim());
        UUID collabId = (collaboratorUuidOrNull == null || collaboratorUuidOrNull.isBlank())
                ? null
                : UUID.fromString(collaboratorUuidOrNull.trim());

        // Serializa estritamente concorrencia de escrita na cadeia de hashes ate o COMMIT/ROLLBACK
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + LEDGER_ADVISORY_LOCK_ID + ")");

        List<TailState> tail = jdbcTemplate.query(
                "SELECT seq, entry_hash FROM verification_audit_ledger ORDER BY seq DESC LIMIT 1",
                (rs, rowNum) -> new TailState(rs.getLong("seq"), rs.getBytes("entry_hash")));

        long nextSeq = tail.isEmpty() ? 1L : tail.getFirst().seq() + 1L;
        byte[] prevEntryHash = tail.isEmpty() ? GENESIS_PREV_HASH.clone() : tail.getFirst().entryHash();

        byte[] plaintextJsonBytes = null;
        try {
            String jsonStr = objectMapper.writeValueAsString(payload);
            plaintextJsonBytes = jsonStr.getBytes(StandardCharsets.UTF_8);

            SealedEscrowRecord sealed = CanonicalTranscripts.sealAuditEscrowBlob(
                    keyMaterial.escrowX25519Pub(),
                    prevEntryHash,
                    plaintextJsonBytes);

            UUID entryId = CanonicalTranscripts.generateTemporalUuidV7();
            byte[] canonicalRowBytes = CanonicalTranscripts.buildAuditLedgerRowTranscript(
                    nextSeq,
                    entryId.toString(),
                    actionType.trim(),
                    targetId.toString(),
                    docBlindIndex,
                    normalizedCnpj,
                    collabId != null ? collabId.toString() : "",
                    sealed.ephemeralX25519Pub(),
                    sealed.sealedAuditBlob(),
                    createdAtEpochMs);

            byte[] entryHash = CanonicalTranscripts.computeLedgerEntryHash(prevEntryHash, canonicalRowBytes);

            jdbcTemplate.update("""
                    INSERT INTO verification_audit_ledger (
                        entry_id, seq, action_type, target_uuid, doc_blind_index, isp_cnpj,
                        collaborator_uuid, ephemeral_x25519_pub, sealed_audit_blob,
                        prev_entry_hash, entry_hash, created_at_epoch_ms, created_at
                    ) OVERRIDING SYSTEM VALUE VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    entryId,
                    nextSeq,
                    actionType.trim(),
                    targetId,
                    docBlindIndex,
                    normalizedCnpj,
                    collabId,
                    sealed.ephemeralX25519Pub(),
                    sealed.sealedAuditBlob(),
                    prevEntryHash,
                    entryHash,
                    createdAtEpochMs,
                    Timestamp.from(Instant.ofEpochMilli(createdAtEpochMs)));

            return new LedgerEntryRecord(
                    nextSeq,
                    entryId.toString(),
                    actionType.trim(),
                    targetId.toString(),
                    docBlindIndex.clone(),
                    normalizedCnpj,
                    collabId != null ? collabId.toString() : "",
                    sealed.ephemeralX25519Pub(),
                    sealed.sealedAuditBlob(),
                    prevEntryHash,
                    entryHash,
                    createdAtEpochMs);
        } catch (GeneralSecurityException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Falha ao selar registro no verification_audit_ledger", e);
        } finally {
            if (plaintextJsonBytes != null) {
                Arrays.fill(plaintextJsonBytes, (byte) 0);
            }
        }
    }

    public List<LedgerEntryRecord> listAllEntriesOrdered() {
        return jdbcTemplate.query("""
                SELECT seq, entry_id, action_type, target_uuid, doc_blind_index, isp_cnpj,
                       collaborator_uuid, ephemeral_x25519_pub, sealed_audit_blob,
                       prev_entry_hash, entry_hash, created_at_epoch_ms
                  FROM verification_audit_ledger
                 ORDER BY seq ASC
                """,
                this::mapRow);
    }

    public String exportLedgerChainJson() {
        try {
            List<ExportedLedgerEntryJson> exported = listAllEntriesOrdered().stream()
                    .map(LedgerEntryRecord::toExportedJson)
                    .toList();
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(exported);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Falha ao exportar verification_audit_ledger em JSON", e);
        }
    }

    private LedgerEntryRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        UUID collab = rs.getObject("collaborator_uuid", UUID.class);
        return new LedgerEntryRecord(
                rs.getLong("seq"),
                rs.getObject("entry_id", UUID.class).toString(),
                rs.getString("action_type"),
                rs.getObject("target_uuid", UUID.class).toString(),
                rs.getBytes("doc_blind_index"),
                rs.getString("isp_cnpj"),
                collab != null ? collab.toString() : "",
                rs.getBytes("ephemeral_x25519_pub"),
                rs.getBytes("sealed_audit_blob"),
                rs.getBytes("prev_entry_hash"),
                rs.getBytes("entry_hash"),
                rs.getLong("created_at_epoch_ms"));
    }
}
