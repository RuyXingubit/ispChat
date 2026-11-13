package br.dev.ispchat.principal.service;

import br.dev.ispchat.common.crypto.BlindIndexService;
import br.dev.ispchat.common.crypto.CanonicalTranscripts;
import br.dev.ispchat.grpc.AuthorizeCollaboratorRequest;
import br.dev.ispchat.grpc.CollaboratorAuthCert;
import br.dev.ispchat.grpc.DeviceDelegationCert;
import br.dev.ispchat.grpc.DocumentType;
import br.dev.ispchat.grpc.GetCredentialRequest;
import br.dev.ispchat.grpc.GovernanceAck;
import br.dev.ispchat.grpc.HomeNodeClaim;
import br.dev.ispchat.grpc.IdentityReplacementNotice;
import br.dev.ispchat.grpc.PartnerSeedEntry;
import br.dev.ispchat.grpc.PortAccountRequest;
import br.dev.ispchat.grpc.RegisterPartnerIspRequest;
import br.dev.ispchat.grpc.RevocationEvent;
import br.dev.ispchat.grpc.RevokeCollaboratorRequest;
import br.dev.ispchat.grpc.RevokeDeviceRequest;
import br.dev.ispchat.grpc.SubmitCpfRebindRequest;
import br.dev.ispchat.grpc.SubmitVerificationRequest;
import br.dev.ispchat.grpc.UserVerificationConsent;
import br.dev.ispchat.grpc.VerificationBundle;
import br.dev.ispchat.grpc.VerificationCredential;
import br.dev.ispchat.node.dht.DhtStoreService;
import br.dev.ispchat.node.messaging.LocalSessionRegistry;
import br.dev.ispchat.node.security.CryptoService;
import br.dev.ispchat.node.security.TierRateLimiterService;
import br.dev.ispchat.principal.config.PrincipalKeyMaterial;
import br.dev.ispchat.principal.ledger.AuditLedgerService;
import br.dev.ispchat.principal.ledger.AuditLedgerService.AuditAccountabilityPayload;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.Status;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.cert.X509Certificate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Coordenador transacional de Governanca da Cadeia de Confianca de 4 Niveis,
 * emissao/renovacao de VerificationCredential (90d TTL), CPF Re-Bind (0h hold),
 * portabilidade entre ISPs e feed monotono de revogacoes.
 */
@Service
public class PrincipalGovernanceCoordinator {

    public static final long REVOCATION_ADVISORY_LOCK_ID = 8_443_002L;
    public static final long FRESHNESS_WINDOW_MS = Duration.ofMinutes(5).toMillis();
    public static final long MAX_COLLAB_TTL_MS = Duration.ofDays(366).toMillis();
    public static final long CREDENTIAL_TTL_MS = Duration.ofDays(90).toMillis();
    public static final long CREDENTIAL_RENEWAL_THRESHOLD_MS = Duration.ofDays(30).toMillis();
    public static final long BOOTSTRAP_MAX_AGE_MS = Duration.ofDays(7).toMillis();

    private record IspRow(UUID id, String cnpj, String tradeName, UUID corporateUuid, String nodeAddress, String status) {
    }

    private record PartnerRow(UUID id, UUID ispId, UUID partnerUuid, byte[] ed25519Pub, boolean active) {
    }

    private record CollaboratorRow(
            UUID id,
            UUID ispId,
            UUID collaboratorUuid,
            byte[] ed25519Pub,
            Instant expiresAt,
            Instant revokedAt) {
    }

    private record VerifiedAccountRow(
            UUID id,
            UUID userUuid,
            DocumentType docType,
            byte[] docBlindIndex,
            byte[] rootEd25519Pub,
            byte[] rootX25519Pub,
            String publicCnpj,
            String publicDisplayName,
            UUID verifyingIspId,
            String verifyingIspCnpj,
            String verifyingIspName,
            String homeNodeAddress,
            long routingEpoch,
            byte[] credentialBytes,
            byte[] homeClaimBytes,
            String status) {
    }

    private final JdbcTemplate jdbcTemplate;
    private final PrincipalKeyMaterial keyMaterial;
    private final BlindIndexService blindIndexService;
    private final AuditLedgerService auditLedgerService;
    private final CryptoService cryptoService;
    private final TierRateLimiterService tierRateLimiterService;
    private final DhtStoreService dhtStoreService;
    private final LocalSessionRegistry localSessionRegistry;
    private final String principalPublicAddress;
    private final Counter verificationsCounter;

    private final Map<String, Long> consumedConsentDigests = new ConcurrentHashMap<>();
    private final List<Consumer<RevocationEvent>> revocationListeners = new CopyOnWriteArrayList<>();

    @Autowired
    public PrincipalGovernanceCoordinator(
            JdbcTemplate jdbcTemplate,
            PrincipalKeyMaterial keyMaterial,
            BlindIndexService blindIndexService,
            AuditLedgerService auditLedgerService,
            CryptoService cryptoService,
            TierRateLimiterService tierRateLimiterService,
            DhtStoreService dhtStoreService,
            LocalSessionRegistry localSessionRegistry,
            @Autowired(required = false) MeterRegistry meterRegistry,
            @Value("${ispchat.node.public-address:principal.ispchat.dev.br:443}") String principalPublicAddress) {
        this.jdbcTemplate = jdbcTemplate;
        this.keyMaterial = keyMaterial;
        this.blindIndexService = blindIndexService;
        this.auditLedgerService = auditLedgerService;
        this.cryptoService = cryptoService;
        this.tierRateLimiterService = tierRateLimiterService;
        this.dhtStoreService = dhtStoreService;
        this.localSessionRegistry = localSessionRegistry;
        this.principalPublicAddress = principalPublicAddress != null ? principalPublicAddress.trim() : "";
        MeterRegistry registry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
        this.verificationsCounter = Counter.builder("ispchat_verifications_total")
                .description("Total de contas CPF/CNPJ verificadas ou re-vinculadas pelo Principal")
                .register(registry);
    }

    public Runnable subscribeRevocations(Consumer<RevocationEvent> listener) {
        revocationListeners.add(listener);
        return () -> revocationListeners.remove(listener);
    }

    @Transactional
    public GovernanceAck registerPartnerIsp(RegisterPartnerIspRequest request, X509Certificate peerCert) {
        if (peerCert == null) {
            throw Status.UNAUTHENTICATED
                    .withDescription("Certificado X.509 mTLS obrigatorio para RegisterPartnerIsp")
                    .asRuntimeException();
        }
        long nowMs = cryptoService.getClock().millis();
        if (request.getIssuedAtMs() > nowMs + FRESHNESS_WINDOW_MS
                || request.getIssuedAtMs() < nowMs - BOOTSTRAP_MAX_AGE_MS) {
            throw Status.PERMISSION_DENIED
                    .withDescription("Timestamp do pacote de bootstrap ISP fora da janela permitida")
                    .asRuntimeException();
        }

        String normalizedCnpj = normalizeDocOrThrow(DocumentType.CNPJ, request.getIspCnpj());
        String tradeName = request.getTradeName().trim();
        String nodeAddress = request.getNodeAddress().trim().toLowerCase(Locale.ROOT);
        if (tradeName.isBlank() || nodeAddress.isBlank()) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("trade_name e node_address sao obrigatorios")
                    .asRuntimeException();
        }
        if (request.getManagingPartnersCount() == 0) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("Pelo menos 1 socio administrador (managing_partners) deve ser informado")
                    .asRuntimeException();
        }

        byte[] corpEdPub = request.getCorpRootEd25519Pub().toByteArray();
        byte[] corpXPub = request.getCorpRootX25519Pub().toByteArray();
        if (!CanonicalTranscripts.verifyIdentityBinding(request.getCorporateUuid(), corpEdPub)
                || corpXPub.length != CanonicalTranscripts.RAW_KEY_LENGTH) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("corporate_uuid nao corresponde a corp_root_ed25519_pub ou chave X25519 invalida")
                    .asRuntimeException();
        }
        UUID corporateUuid = UUID.fromString(request.getCorporateUuid().trim());

        List<String> partnerUuidStrings = new ArrayList<>();
        for (PartnerSeedEntry p : request.getManagingPartnersList()) {
            normalizeDocOrThrow(DocumentType.CPF, p.getPartnerCpf());
            if (!CanonicalTranscripts.verifyIdentityBinding(p.getPartnerUuid(), p.getPartnerEd25519Pub().toByteArray())
                    || p.getPartnerX25519Pub().size() != CanonicalTranscripts.RAW_KEY_LENGTH) {
                throw Status.INVALID_ARGUMENT
                        .withDescription("Chaves ou UUIDv7 invalidos em managing_partners: " + p.getPartnerUuid())
                        .asRuntimeException();
            }
            partnerUuidStrings.add(UUID.fromString(p.getPartnerUuid().trim()).toString());
        }

        byte[] partnersDigest = CanonicalTranscripts.computePartnersDigestSha256(request.getManagingPartnersList());
        byte[] bootstrapTranscript = CanonicalTranscripts.buildPartnerIspBootstrapTranscript(
                normalizedCnpj,
                tradeName,
                corporateUuid.toString(),
                corpEdPub,
                corpXPub,
                nodeAddress,
                partnersDigest,
                request.getIssuedAtMs());

        if (!CanonicalTranscripts.verifyEd25519(
                keyMaterial.rootEd25519Pub(),
                bootstrapTranscript,
                request.getRootSignature().toByteArray())) {
            throw Status.PERMISSION_DENIED
                    .withDescription("Assinatura Ed25519 Nivel 0 (ISPCHAT-CHAIN-ISP-V1) invalida")
                    .asRuntimeException();
        }

        // Computa blind indexes Argon2id antes de obter o advisory lock do ledger
        byte[] corpBlindIndex = blindIndexService.computeBlindIndex(DocumentType.CNPJ, normalizedCnpj);
        List<byte[]> partnerBlindIndexes = new ArrayList<>(request.getManagingPartnersCount());
        for (PartnerSeedEntry p : request.getManagingPartnersList()) {
            partnerBlindIndexes.add(blindIndexService.computeBlindIndex(DocumentType.CPF, p.getPartnerCpf()));
        }

        UUID ispId = upsertPartnerIspRow(normalizedCnpj, tradeName, corporateUuid, nodeAddress, nowMs);

        // Emite credencial corporativa CNPJ do proprio ISP Parceiro
        VerificationBundle corpBundle = issueAndUpsertInitialSeededAccount(
                corporateUuid,
                DocumentType.CNPJ,
                corpBlindIndex,
                corpEdPub,
                corpXPub,
                normalizedCnpj,
                tradeName,
                ispId,
                normalizedCnpj,
                tradeName,
                nodeAddress,
                nowMs);

        auditLedgerService.appendSealedEntry(
                "REGISTER_PARTNER_ISP_CNPJ",
                corporateUuid.toString(),
                corpBlindIndex,
                normalizedCnpj,
                null,
                new AuditAccountabilityPayload(
                        "REGISTER_PARTNER_ISP_CNPJ",
                        "CNPJ",
                        normalizedCnpj,
                        corporateUuid.toString(),
                        "",
                        "",
                        "",
                        normalizedCnpj,
                        partnerUuidStrings,
                        "",
                        Base64.getEncoder().encodeToString(request.getRootSignature().toByteArray()),
                        nowMs),
                nowMs);

        for (int i = 0; i < request.getManagingPartnersCount(); i++) {
            PartnerSeedEntry p = request.getManagingPartners(i);
            String normPartnerCpf = CanonicalTranscripts.normalizeAndValidateCpf(p.getPartnerCpf());
            UUID partnerUuid = UUID.fromString(p.getPartnerUuid().trim());
            byte[] partnerBlindIndex = partnerBlindIndexes.get(i);

            upsertManagingPartnerRow(
                    ispId,
                    partnerUuid,
                    partnerBlindIndex,
                    p.getPartnerEd25519Pub().toByteArray(),
                    p.getPartnerX25519Pub().toByteArray(),
                    nowMs);

            issueAndUpsertInitialSeededAccount(
                    partnerUuid,
                    DocumentType.CPF,
                    partnerBlindIndex,
                    p.getPartnerEd25519Pub().toByteArray(),
                    p.getPartnerX25519Pub().toByteArray(),
                    null,
                    null,
                    ispId,
                    normalizedCnpj,
                    tradeName,
                    nodeAddress,
                    nowMs);

            auditLedgerService.appendSealedEntry(
                    "REGISTER_MANAGING_PARTNER_CPF",
                    partnerUuid.toString(),
                    partnerBlindIndex,
                    normalizedCnpj,
                    null,
                    new AuditAccountabilityPayload(
                            "REGISTER_MANAGING_PARTNER_CPF",
                            "CPF",
                            normPartnerCpf,
                            partnerUuid.toString(),
                            "",
                            "",
                            "",
                            normalizedCnpj,
                            partnerUuidStrings,
                            "",
                            Base64.getEncoder().encodeToString(request.getRootSignature().toByteArray()),
                            nowMs),
                    nowMs);
        }

        verificationsCounter.increment(1 + request.getManagingPartnersCount());
        return GovernanceAck.newBuilder()
                .setAccepted(true)
                .setMessage("Provedor parceiro " + tradeName + " registrado com sucesso")
                .setCorporateBundle(corpBundle)
                .build();
    }

    @Transactional
    public CollaboratorAuthCert authorizeCollaborator(
            AuthorizeCollaboratorRequest request,
            X509Certificate peerCert,
            Set<String> peerSans) {
        if (!request.hasCert()) {
            throw Status.INVALID_ARGUMENT.withDescription("cert e obrigatorio").asRuntimeException();
        }
        CollaboratorAuthCert cert = request.getCert();
        long nowMs = cryptoService.getClock().millis();

        String normalizedIspCnpj = normalizeDocOrThrow(DocumentType.CNPJ, cert.getIspCnpj());
        IspRow isp = requireActiveIspMatchingCallerSan(normalizedIspCnpj, peerCert, peerSans);

        String normalizedCollabCpf = normalizeDocOrThrow(DocumentType.CPF, request.getCollaboratorCpf());
        byte[] expectedCpfSha256 = CanonicalTranscripts.sha256Utf8(normalizedCollabCpf);
        if (!Arrays.equals(expectedCpfSha256, cert.getCollabCpfSha256().toByteArray())) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("collab_cpf_sha256 nao confere com collaborator_cpf informado")
                    .asRuntimeException();
        }

        byte[] collabEdPub = cert.getCollabEd25519Pub().toByteArray();
        if (!CanonicalTranscripts.verifyIdentityBinding(cert.getCollaboratorUuid(), collabEdPub)) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("collaborator_uuid incompativel com collab_ed25519_pub")
                    .asRuntimeException();
        }
        if (cert.getIssuedAtMs() > nowMs + FRESHNESS_WINDOW_MS
                || cert.getExpiresAtMs() <= nowMs
                || (cert.getExpiresAtMs() - cert.getIssuedAtMs()) > MAX_COLLAB_TTL_MS) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("Validade do CollaboratorAuthCert invalida (maximo 1 ano)")
                    .asRuntimeException();
        }

        UUID partnerUuid = parseUuidOrThrow(cert.getPartnerUuid(), "partner_uuid");
        PartnerRow partner = requireActiveManagingPartner(isp.id(), partnerUuid);

        byte[] transcript = CanonicalTranscripts.buildCollaboratorAuthTranscript(cert);
        if (!CanonicalTranscripts.verifyEd25519(
                partner.ed25519Pub(),
                transcript,
                cert.getPartnerSignature().toByteArray())) {
            throw Status.PERMISSION_DENIED
                    .withDescription("Assinatura Ed25519 do Socio Administrador invalida em CollaboratorAuthCert")
                    .asRuntimeException();
        }

        byte[] collabBlindIndex = blindIndexService.computeBlindIndex(DocumentType.CPF, normalizedCollabCpf);
        UUID collabUuid = UUID.fromString(cert.getCollaboratorUuid().trim());

        jdbcTemplate.update("""
                INSERT INTO verified_collaborators (
                    id, isp_id, collaborator_uuid, cpf_blind_index, collaborator_ed25519_pub,
                    authorized_by_partner_uuid, cert_bytes, issued_at, expires_at, revoked_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?)
                ON CONFLICT (collaborator_uuid) DO UPDATE SET
                    isp_id = EXCLUDED.isp_id,
                    cpf_blind_index = EXCLUDED.cpf_blind_index,
                    collaborator_ed25519_pub = EXCLUDED.collaborator_ed25519_pub,
                    authorized_by_partner_uuid = EXCLUDED.authorized_by_partner_uuid,
                    cert_bytes = EXCLUDED.cert_bytes,
                    issued_at = EXCLUDED.issued_at,
                    expires_at = EXCLUDED.expires_at,
                    revoked_at = NULL
                """,
                CanonicalTranscripts.generateTemporalUuidV7(),
                isp.id(),
                collabUuid,
                collabBlindIndex,
                collabEdPub,
                partnerUuid,
                cert.toByteArray(),
                Timestamp.from(Instant.ofEpochMilli(cert.getIssuedAtMs())),
                Timestamp.from(Instant.ofEpochMilli(cert.getExpiresAtMs())),
                Timestamp.from(Instant.ofEpochMilli(nowMs)));

        auditLedgerService.appendSealedEntry(
                "AUTHORIZE_COLLABORATOR",
                collabUuid.toString(),
                collabBlindIndex,
                normalizedIspCnpj,
                collabUuid.toString(),
                new AuditAccountabilityPayload(
                        "AUTHORIZE_COLLABORATOR",
                        "CPF",
                        normalizedCollabCpf,
                        collabUuid.toString(),
                        "",
                        normalizedCollabCpf,
                        collabUuid.toString(),
                        normalizedIspCnpj,
                        listActivePartnerUuidStrings(isp.id()),
                        "",
                        Base64.getEncoder().encodeToString(cert.getPartnerSignature().toByteArray()),
                        nowMs),
                nowMs);

        return cert;
    }

    @Transactional
    public RevocationEvent revokeCollaborator(
            RevokeCollaboratorRequest request,
            X509Certificate peerCert,
            Set<String> peerSans) {
        long nowMs = cryptoService.getClock().millis();
        verifyTimestampFreshness(request.getTimestampMs(), nowMs, "timestamp_ms");

        String normalizedIspCnpj = normalizeDocOrThrow(DocumentType.CNPJ, request.getIspCnpj());
        IspRow isp = requireActiveIspMatchingCallerSan(normalizedIspCnpj, peerCert, peerSans);

        UUID partnerUuid = parseUuidOrThrow(request.getPartnerUuid(), "partner_uuid");
        UUID collabUuid = parseUuidOrThrow(request.getCollaboratorUuid(), "collaborator_uuid");
        String reason = request.getReason().trim().isEmpty() ? "COLLABORATOR_REVOKED" : request.getReason().trim();

        PartnerRow partner = requireActiveManagingPartner(isp.id(), partnerUuid);
        byte[] transcript = CanonicalTranscripts.buildRevokeCollaboratorTranscript(
                normalizedIspCnpj,
                partnerUuid.toString(),
                collabUuid.toString(),
                reason,
                request.getTimestampMs());

        if (!CanonicalTranscripts.verifyEd25519(
                partner.ed25519Pub(),
                transcript,
                request.getPartnerSignature().toByteArray())) {
            throw Status.PERMISSION_DENIED
                    .withDescription("Assinatura Ed25519 do Socio Administrador invalida para RevokeCollaborator")
                    .asRuntimeException();
        }

        List<byte[]> blindRows = jdbcTemplate.query(
                "SELECT cpf_blind_index FROM verified_collaborators WHERE isp_id = ? AND collaborator_uuid = ?",
                (rs, rowNum) -> rs.getBytes("cpf_blind_index"),
                isp.id(),
                collabUuid);
        if (blindRows.isEmpty()) {
            throw Status.NOT_FOUND
                    .withDescription("Colaborador nao encontrado para o provedor informado")
                    .asRuntimeException();
        }

        jdbcTemplate.update(
                "UPDATE verified_collaborators SET revoked_at = ? WHERE isp_id = ? AND collaborator_uuid = ?",
                Timestamp.from(Instant.ofEpochMilli(nowMs)),
                isp.id(),
                collabUuid);

        RevocationEvent revEvent = insertRevocationEventLocked(
                collabUuid,
                null,
                "COLLABORATOR_REVOKED:" + reason,
                null,
                nowMs);

        auditLedgerService.appendSealedEntry(
                "REVOKE_COLLABORATOR",
                collabUuid.toString(),
                blindRows.getFirst(),
                normalizedIspCnpj,
                collabUuid.toString(),
                new AuditAccountabilityPayload(
                        "REVOKE_COLLABORATOR",
                        "CPF",
                        "",
                        collabUuid.toString(),
                        "",
                        "",
                        collabUuid.toString(),
                        normalizedIspCnpj,
                        listActivePartnerUuidStrings(isp.id()),
                        "",
                        Base64.getEncoder().encodeToString(request.getPartnerSignature().toByteArray()),
                        nowMs),
                nowMs);

        publishRevocationPostCommit(revEvent);
        return revEvent;
    }

    @Transactional
    public VerificationBundle submitVerification(
            SubmitVerificationRequest request,
            X509Certificate peerCert,
            Set<String> peerSans) {
        long nowMs = cryptoService.getClock().millis();
        String normalizedIspCnpj = normalizeDocOrThrow(DocumentType.CNPJ, request.getIspCnpj());
        IspRow isp = requireActiveIspMatchingCallerSan(normalizedIspCnpj, peerCert, peerSans);

        CollaboratorRow collab = verifyActiveCollaborator(isp, request.getCollaboratorCert(), nowMs);
        UserVerificationConsent consent = request.getUserConsent();
        DocumentType docType = consent.getDocType();
        if (docType != DocumentType.CPF && docType != DocumentType.CNPJ) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("doc_type deve ser CPF ou CNPJ")
                    .asRuntimeException();
        }

        String normalizedDoc = verifyUserConsent(consent, normalizedIspCnpj, request.getNormalizedDocument(), nowMs);
        verifyTimestampFreshness(request.getAttestedAtMs(), nowMs, "attested_at_ms");

        String homeNodeAddr = resolveAuthorizedHomeNodeAddress(request.getHomeNodeAddress(), isp.nodeAddress());
        byte[] collabTranscript = CanonicalTranscripts.buildCollaboratorUserAttestationTranscript(
                normalizedIspCnpj,
                collab.collaboratorUuid().toString(),
                consent.getUserUuid(),
                docType,
                consent.getDocSha256().toByteArray(),
                homeNodeAddr,
                consent.getUserSignature().toByteArray(),
                request.getAttestedAtMs());

        if (!CanonicalTranscripts.verifyEd25519(
                collab.ed25519Pub(),
                collabTranscript,
                request.getCollaboratorSignature().toByteArray())) {
            throw Status.PERMISSION_DENIED
                    .withDescription("Assinatura Ed25519 de atestacao do Colaborador (ISPCHAT-CHAIN-USER-V1) invalida")
                    .asRuntimeException();
        }

        String publicCnpj = null;
        String publicDisplayName = null;
        if (docType == DocumentType.CNPJ) {
            publicCnpj = normalizedDoc;
            publicDisplayName = request.getPublicDisplayName().trim();
            if (publicDisplayName.isEmpty()) {
                throw Status.INVALID_ARGUMENT
                        .withDescription("public_display_name e obrigatorio para contas corporativas CNPJ")
                        .asRuntimeException();
            }
        }

        String consentReplayKey = checkAndStageConsentAntiReplay(consent.getUserSignature().toByteArray(), nowMs);
        byte[] docBlindIndex = blindIndexService.computeBlindIndex(docType, normalizedDoc);
        UUID userUuid = UUID.fromString(consent.getUserUuid().trim());

        // Verifica unicidade ativa antes de anexar ao ledger (alem do indice parcial unico uk_verified_accounts_active_doc)
        List<UUID> activeExisting = jdbcTemplate.query(
                "SELECT user_uuid FROM verified_accounts WHERE doc_blind_index = ? AND status = 'ACTIVE'",
                (rs, rowNum) -> rs.getObject("user_uuid", UUID.class),
                docBlindIndex);
        if (!activeExisting.isEmpty()) {
            throw Status.ALREADY_EXISTS
                    .withDescription("Documento ja possui conta ativa verificada; utilize SubmitCpfRebind para rotacao")
                    .asRuntimeException();
        }

        VerificationBundle bundle = signCredentialAndHomeClaim(
                userUuid,
                consent.getUserEd25519Pub().toByteArray(),
                consent.getUserX25519Pub().toByteArray(),
                docType,
                publicCnpj,
                publicDisplayName,
                normalizedIspCnpj,
                isp.tradeName(),
                homeNodeAddr,
                1L,
                nowMs);

        try {
            auditLedgerService.appendSealedEntry(
                    "SUBMIT_VERIFICATION",
                    userUuid.toString(),
                    docBlindIndex,
                    normalizedIspCnpj,
                    collab.collaboratorUuid().toString(),
                    new AuditAccountabilityPayload(
                            "SUBMIT_VERIFICATION",
                            docType.name(),
                            normalizedDoc,
                            userUuid.toString(),
                            "",
                            HexFormat.of().formatHex(request.getCollaboratorCert().getCollabCpfSha256().toByteArray()),
                            collab.collaboratorUuid().toString(),
                            normalizedIspCnpj,
                            listActivePartnerUuidStrings(isp.id()),
                            Base64.getEncoder().encodeToString(consent.getUserSignature().toByteArray()),
                            Base64.getEncoder().encodeToString(request.getCollaboratorSignature().toByteArray()),
                            request.getAttestedAtMs()),
                    nowMs);

            jdbcTemplate.update("""
                    INSERT INTO verified_accounts (
                        id, user_uuid, doc_type, doc_blind_index, root_ed25519_pub, root_x25519_pub,
                        public_cnpj, public_display_name, verifying_isp_id, home_node_address, routing_epoch,
                        credential_bytes, home_claim_bytes, status, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, 'ACTIVE', ?, ?)
                    """,
                    CanonicalTranscripts.generateTemporalUuidV7(),
                    userUuid,
                    docType.name(),
                    docBlindIndex,
                    consent.getUserEd25519Pub().toByteArray(),
                    consent.getUserX25519Pub().toByteArray(),
                    publicCnpj,
                    publicDisplayName,
                    isp.id(),
                    homeNodeAddr,
                    bundle.getCredential().toByteArray(),
                    bundle.getHomeNodeClaim().toByteArray(),
                    Timestamp.from(Instant.ofEpochMilli(nowMs)),
                    Timestamp.from(Instant.ofEpochMilli(nowMs)));
        } catch (DuplicateKeyException e) {
            throw Status.ALREADY_EXISTS
                    .withDescription("Conflito de unicidade: documento ou UUIDv7 ja registrado como ativo")
                    .asRuntimeException();
        }

        commitConsentReplayKeyAndCacheClaim(consentReplayKey, nowMs, bundle);
        verificationsCounter.increment();
        return bundle;
    }

    /**
     * Re-vinculacao imediata (0h hold) de CPF em caso de perda/roubo do celular sem backup BIP39:
     * revoga UUIDv7_old, publica IdentityReplacementNotice no StreamRevocations e ativa UUIDv7_new
     * na mesma transacao atomica PostgreSQL.
     */
    @Transactional
    public VerificationBundle submitCpfRebind(
            SubmitCpfRebindRequest request,
            X509Certificate peerCert,
            Set<String> peerSans) {
        long nowMs = cryptoService.getClock().millis();
        String normalizedIspCnpj = normalizeDocOrThrow(DocumentType.CNPJ, request.getIspCnpj());
        IspRow isp = requireActiveIspMatchingCallerSan(normalizedIspCnpj, peerCert, peerSans);

        CollaboratorRow collab = verifyActiveCollaborator(isp, request.getCollaboratorCert(), nowMs);
        UserVerificationConsent newConsent = request.getNewUserConsent();
        if (newConsent.getDocType() != DocumentType.CPF) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("SubmitCpfRebind e exclusivo para contas individuais CPF")
                    .asRuntimeException();
        }

        String normalizedCpf = verifyUserConsent(newConsent, normalizedIspCnpj, request.getNormalizedCpf(), nowMs);
        verifyTimestampFreshness(request.getAttestedAtMs(), nowMs, "attested_at_ms");

        UUID oldUserUuid = parseUuidOrThrow(request.getOldUserUuid(), "old_user_uuid");
        UUID newUserUuid = UUID.fromString(newConsent.getUserUuid().trim());
        if (oldUserUuid.equals(newUserUuid)) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("new_user_uuid deve ser uma nova chave distinta de old_user_uuid")
                    .asRuntimeException();
        }

        String homeNodeAddr = resolveAuthorizedHomeNodeAddress(request.getHomeNodeAddress(), isp.nodeAddress());
        byte[] rebindTranscript = CanonicalTranscripts.buildCpfRebindTranscript(
                normalizedIspCnpj,
                collab.collaboratorUuid().toString(),
                oldUserUuid.toString(),
                newUserUuid.toString(),
                newConsent.getDocSha256().toByteArray(),
                homeNodeAddr,
                newConsent.getUserSignature().toByteArray(),
                request.getAttestedAtMs());

        if (!CanonicalTranscripts.verifyEd25519(
                collab.ed25519Pub(),
                rebindTranscript,
                request.getCollaboratorSignature().toByteArray())) {
            throw Status.PERMISSION_DENIED
                    .withDescription("Assinatura Ed25519 do Colaborador (ISPCHAT-REBIND-V1) invalida")
                    .asRuntimeException();
        }

        String consentReplayKey = checkAndStageConsentAntiReplay(newConsent.getUserSignature().toByteArray(), nowMs);
        byte[] cpfBlindIndex = blindIndexService.computeBlindIndex(DocumentType.CPF, normalizedCpf);

        List<VerifiedAccountRow> lockedOldRows = jdbcTemplate.query("""
                SELECT va.id, va.user_uuid, va.doc_type, va.doc_blind_index, va.root_ed25519_pub, va.root_x25519_pub,
                       va.public_cnpj, va.public_display_name, va.verifying_isp_id,
                       vi.cnpj AS verifying_isp_cnpj, vi.trade_name AS verifying_isp_name,
                       va.home_node_address, va.routing_epoch, va.credential_bytes, va.home_claim_bytes, va.status
                  FROM verified_accounts va
                  JOIN verified_isps vi ON vi.id = va.verifying_isp_id
                 WHERE va.doc_blind_index = ? AND va.status = 'ACTIVE'
                   FOR UPDATE OF va
                """,
                this::mapVerifiedAccountRow,
                cpfBlindIndex);

        if (lockedOldRows.isEmpty()) {
            throw Status.NOT_FOUND
                    .withDescription("Nenhuma conta CPF ativa encontrada para re-vinculacao")
                    .asRuntimeException();
        }
        VerifiedAccountRow oldRow = lockedOldRows.getFirst();
        if (!oldRow.userUuid().equals(oldUserUuid)) {
            throw Status.FAILED_PRECONDITION
                    .withDescription("old_user_uuid informado nao corresponde a identidade ativa vinculada ao CPF")
                    .asRuntimeException();
        }

        // 1. Registra auditoria selada sob pg_advisory_xact_lock(8443001)
        auditLedgerService.appendSealedEntry(
                "SUBMIT_CPF_REBIND",
                newUserUuid.toString(),
                cpfBlindIndex,
                normalizedIspCnpj,
                collab.collaboratorUuid().toString(),
                new AuditAccountabilityPayload(
                        "SUBMIT_CPF_REBIND",
                        "CPF",
                        normalizedCpf,
                        newUserUuid.toString(),
                        oldUserUuid.toString(),
                        HexFormat.of().formatHex(request.getCollaboratorCert().getCollabCpfSha256().toByteArray()),
                        collab.collaboratorUuid().toString(),
                        normalizedIspCnpj,
                        listActivePartnerUuidStrings(isp.id()),
                        Base64.getEncoder().encodeToString(newConsent.getUserSignature().toByteArray()),
                        Base64.getEncoder().encodeToString(request.getCollaboratorSignature().toByteArray()),
                        request.getAttestedAtMs()),
                nowMs);

        // 2. Marca UUIDv7_old como REVOKED (liberando o indice parcial uk_verified_accounts_active_doc)
        jdbcTemplate.update(
                "UPDATE verified_accounts SET status = 'REVOKED', updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.ofEpochMilli(nowMs)),
                oldRow.id());

        // 3. Emite IdentityReplacementNotice + RevocationEvent monotono para corte imediato das streams antigas
        UUID revocationId = CanonicalTranscripts.generateTemporalUuidV7();
        IdentityReplacementNotice unsignedNotice = IdentityReplacementNotice.newBuilder()
                .setRevocationId(revocationId.toString())
                .setOldUserUuid(oldUserUuid.toString())
                .setNewUserUuid(newUserUuid.toString())
                .setVerifyingIspCnpj(normalizedIspCnpj)
                .setReplacedAtMs(nowMs)
                .build();
        byte[] noticeSig = keyMaterial.signWithPrincipal(
                CanonicalTranscripts.buildIdentityReplacementTranscript(unsignedNotice));
        IdentityReplacementNotice signedNotice = unsignedNotice.toBuilder()
                .setPrincipalSignature(ByteString.copyFrom(noticeSig))
                .build();

        RevocationEvent revEvent = insertRevocationEventWithIdLocked(
                revocationId,
                oldUserUuid,
                null,
                "CPF_REBIND",
                signedNotice,
                nowMs);

        // 4. Emite e persiste a nova credencial ativa para UUIDv7_new
        VerificationBundle newBundle = signCredentialAndHomeClaim(
                newUserUuid,
                newConsent.getUserEd25519Pub().toByteArray(),
                newConsent.getUserX25519Pub().toByteArray(),
                DocumentType.CPF,
                null,
                null,
                normalizedIspCnpj,
                isp.tradeName(),
                homeNodeAddr,
                1L,
                nowMs)
                .toBuilder()
                .setReplacementNotice(signedNotice)
                .build();

        jdbcTemplate.update("""
                INSERT INTO verified_accounts (
                    id, user_uuid, doc_type, doc_blind_index, root_ed25519_pub, root_x25519_pub,
                    public_cnpj, public_display_name, verifying_isp_id, home_node_address, routing_epoch,
                    credential_bytes, home_claim_bytes, status, created_at, updated_at
                ) VALUES (?, ?, 'CPF', ?, ?, ?, NULL, NULL, ?, ?, 1, ?, ?, 'ACTIVE', ?, ?)
                """,
                CanonicalTranscripts.generateTemporalUuidV7(),
                newUserUuid,
                cpfBlindIndex,
                newConsent.getUserEd25519Pub().toByteArray(),
                newConsent.getUserX25519Pub().toByteArray(),
                isp.id(),
                homeNodeAddr,
                newBundle.getCredential().toByteArray(),
                newBundle.getHomeNodeClaim().toByteArray(),
                Timestamp.from(Instant.ofEpochMilli(nowMs)),
                Timestamp.from(Instant.ofEpochMilli(nowMs)));

        commitConsentReplayKeyAndCacheClaim(consentReplayKey, nowMs, newBundle);
        publishRevocationPostCommit(revEvent);
        verificationsCounter.increment();
        return newBundle;
    }

    @Transactional
    public VerificationBundle getOrRenewCredential(GetCredentialRequest request) {
        long nowMs = cryptoService.getClock().millis();
        verifyTimestampFreshness(request.getTimestampMs(), nowMs, "timestamp_ms");

        UUID userUuid = parseUuidOrThrow(request.getUserUuid(), "user_uuid");
        UUID deviceUuid = request.getDeviceId().isBlank()
                ? userUuid
                : parseUuidOrThrow(request.getDeviceId(), "device_id");

        VerifiedAccountRow account = requireActiveVerifiedAccount(userUuid);
        if (isDeviceRevokedInDb(userUuid, deviceUuid)) {
            throw Status.PERMISSION_DENIED
                    .withDescription("Dispositivo revogado nao pode consultar ou renovar credencial")
                    .asRuntimeException();
        }

        byte[] callerPub = request.getCallerPublicKey().toByteArray();
        if (deviceUuid.equals(userUuid)) {
            if (!Arrays.equals(callerPub, account.rootEd25519Pub())) {
                throw Status.PERMISSION_DENIED
                        .withDescription("caller_public_key nao corresponde a chave raiz da conta")
                        .asRuntimeException();
            }
        } else {
            if (!request.hasDelegationCert()) {
                throw Status.PERMISSION_DENIED
                        .withDescription("DeviceDelegationCert obrigatorio para dispositivos secundarios")
                        .asRuntimeException();
            }
            DeviceDelegationCert delCert = request.getDelegationCert();
            if (!cryptoService.verifyDeviceDelegationCert(delCert, account.rootEd25519Pub())
                    || !delCert.getUserUuid().trim().equalsIgnoreCase(userUuid.toString())
                    || !delCert.getDeviceId().trim().equalsIgnoreCase(deviceUuid.toString())
                    || !Arrays.equals(delCert.getDeviceEd25519Pub().toByteArray(), callerPub)) {
                throw Status.PERMISSION_DENIED
                        .withDescription("DeviceDelegationCert invalido ou expirado")
                        .asRuntimeException();
            }
        }

        byte[] fetchTranscript = CanonicalTranscripts.buildGetOrRenewCredentialTranscript(
                userUuid.toString(), deviceUuid.toString(), request.getTimestampMs());
        if (!CanonicalTranscripts.verifyEd25519(
                callerPub, fetchTranscript, request.getSignature().toByteArray())) {
            throw Status.PERMISSION_DENIED
                    .withDescription("Assinatura Ed25519 invalida em GetOrRenewCredential")
                    .asRuntimeException();
        }

        try {
            VerificationCredential existingCred = VerificationCredential.parseFrom(account.credentialBytes());
            HomeNodeClaim existingClaim = HomeNodeClaim.parseFrom(account.homeClaimBytes());

            // Renova por mais 90 dias quando faltar <= 30 dias para expirar (ou caso o PrincipalSigningCert tenha rotacionado)
            boolean needsRenewal = (existingCred.getExpiresAtMs() - nowMs) <= CREDENTIAL_RENEWAL_THRESHOLD_MS
                    || !existingCred.getPrincipalCert().getCertId()
                            .equals(keyMaterial.principalCert().getCertId());

            if (!needsRenewal) {
                return VerificationBundle.newBuilder()
                        .setCredential(existingCred)
                        .setHomeNodeClaim(existingClaim)
                        .build();
            }

            VerificationBundle renewed = signCredentialAndHomeClaim(
                    userUuid,
                    account.rootEd25519Pub(),
                    account.rootX25519Pub(),
                    account.docType(),
                    account.publicCnpj(),
                    account.publicDisplayName(),
                    account.verifyingIspCnpj(),
                    account.verifyingIspName(),
                    account.homeNodeAddress(),
                    account.routingEpoch(),
                    nowMs);

            jdbcTemplate.update("""
                    UPDATE verified_accounts
                       SET credential_bytes = ?, home_claim_bytes = ?, updated_at = ?
                     WHERE id = ?
                    """,
                    renewed.getCredential().toByteArray(),
                    renewed.getHomeNodeClaim().toByteArray(),
                    Timestamp.from(Instant.ofEpochMilli(nowMs)),
                    account.id());

            return renewed;
        } catch (InvalidProtocolBufferException e) {
            throw Status.INTERNAL.withDescription("Falha ao decodificar credencial armazenada").asRuntimeException();
        }
    }

    @Transactional
    public RevocationEvent revokeDevice(RevokeDeviceRequest request) {
        long nowMs = cryptoService.getClock().millis();
        verifyTimestampFreshness(request.getTimestampMs(), nowMs, "timestamp_ms");

        UUID userUuid = parseUuidOrThrow(request.getUserUuid(), "user_uuid");
        UUID revokedDeviceUuid = parseUuidOrThrow(request.getRevokedDeviceId(), "revoked_device_id");
        if (userUuid.equals(revokedDeviceUuid)) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("RevokeDevice destina-se apenas a subchaves delegadas (DESKTOP/ERP_API)")
                    .asRuntimeException();
        }

        VerifiedAccountRow account = requireActiveVerifiedAccount(userUuid);
        String reason = request.getReason().trim().isEmpty() ? "DEVICE_REVOKED_BY_OWNER" : request.getReason().trim();
        byte[] transcript = CanonicalTranscripts.buildRevokeDeviceTranscript(
                userUuid.toString(),
                revokedDeviceUuid.toString(),
                reason,
                request.getTimestampMs());

        if (!CanonicalTranscripts.verifyEd25519(
                account.rootEd25519Pub(),
                transcript,
                request.getRootSignature().toByteArray())) {
            throw Status.PERMISSION_DENIED
                    .withDescription("Assinatura Ed25519 da chave raiz da conta invalida em RevokeDevice")
                    .asRuntimeException();
        }

        RevocationEvent revEvent = insertRevocationEventLocked(
                userUuid,
                revokedDeviceUuid,
                reason,
                null,
                nowMs);

        publishRevocationPostCommit(revEvent);
        return revEvent;
    }

    @Transactional
    public VerificationBundle portAccount(
            PortAccountRequest request,
            X509Certificate peerCert,
            Set<String> peerSans) {
        long nowMs = cryptoService.getClock().millis();
        verifyTimestampFreshness(request.getTimestampMs(), nowMs, "timestamp_ms");

        UUID userUuid = parseUuidOrThrow(request.getUserUuid(), "user_uuid");
        VerifiedAccountRow account = requireActiveVerifiedAccount(userUuid);

        long expectedEpoch = account.routingEpoch() + 1L;
        if (request.getExpectedRoutingEpoch() != expectedEpoch) {
            throw Status.FAILED_PRECONDITION
                    .withDescription("expected_routing_epoch invalido: esperado " + expectedEpoch)
                    .asRuntimeException();
        }

        String targetHomeNode = request.getTargetHomeNodeAddress().trim().toLowerCase(Locale.ROOT);
        if (targetHomeNode.isEmpty()) {
            throw Status.INVALID_ARGUMENT.withDescription("target_home_node_address e obrigatorio").asRuntimeException();
        }

        boolean portingToFallbackNode = targetHomeNode.equalsIgnoreCase(principalPublicAddress)
                && request.getTargetIspCnpj().trim().isEmpty();

        String normalizedTargetCnpj = portingToFallbackNode
                ? ""
                : normalizeDocOrThrow(DocumentType.CNPJ, request.getTargetIspCnpj());

        byte[] portTranscript = CanonicalTranscripts.buildPortAccountTranscript(
                userUuid.toString(),
                targetHomeNode,
                normalizedTargetCnpj,
                expectedEpoch,
                request.getTimestampMs());

        if (!CanonicalTranscripts.verifyEd25519(
                account.rootEd25519Pub(),
                portTranscript,
                request.getRootSignature().toByteArray())) {
            throw Status.PERMISSION_DENIED
                    .withDescription("Assinatura Ed25519 da chave raiz da conta invalida em PortAccount")
                    .asRuntimeException();
        }

        UUID newVerifyingIspId = account.verifyingIspId();
        String newVerifyingIspCnpj = account.verifyingIspCnpj();
        String newVerifyingIspName = account.verifyingIspName();
        String collabUuidForAudit = null;

        if (!portingToFallbackNode) {
            IspRow targetIsp = requireActiveIspMatchingCallerSan(normalizedTargetCnpj, peerCert, peerSans);
            if (!targetIsp.nodeAddress().equalsIgnoreCase(targetHomeNode)) {
                throw Status.INVALID_ARGUMENT
                        .withDescription("target_home_node_address nao corresponde ao node_address do ISP destino")
                        .asRuntimeException();
            }
            CollaboratorRow targetCollab = verifyActiveCollaborator(
                    targetIsp, request.getTargetCollaboratorCert(), nowMs);
            if (!CanonicalTranscripts.verifyEd25519(
                    targetCollab.ed25519Pub(),
                    portTranscript,
                    request.getTargetCollaboratorSignature().toByteArray())) {
                throw Status.PERMISSION_DENIED
                        .withDescription("Assinatura Ed25519 do Colaborador do ISP destino invalida em PortAccount")
                        .asRuntimeException();
            }
            newVerifyingIspId = targetIsp.id();
            newVerifyingIspCnpj = targetIsp.cnpj();
            newVerifyingIspName = targetIsp.tradeName();
            collabUuidForAudit = targetCollab.collaboratorUuid().toString();
        }

        VerificationBundle portedBundle = signCredentialAndHomeClaim(
                userUuid,
                account.rootEd25519Pub(),
                account.rootX25519Pub(),
                account.docType(),
                account.publicCnpj(),
                account.publicDisplayName(),
                newVerifyingIspCnpj,
                newVerifyingIspName,
                targetHomeNode,
                expectedEpoch,
                nowMs);

        jdbcTemplate.update("""
                UPDATE verified_accounts
                   SET verifying_isp_id = ?,
                       home_node_address = ?,
                       routing_epoch = ?,
                       credential_bytes = ?,
                       home_claim_bytes = ?,
                       updated_at = ?
                 WHERE id = ?
                """,
                newVerifyingIspId,
                targetHomeNode,
                expectedEpoch,
                portedBundle.getCredential().toByteArray(),
                portedBundle.getHomeNodeClaim().toByteArray(),
                Timestamp.from(Instant.ofEpochMilli(nowMs)),
                account.id());

        auditLedgerService.appendSealedEntry(
                "PORT_ACCOUNT",
                userUuid.toString(),
                account.docBlindIndex(),
                newVerifyingIspCnpj,
                collabUuidForAudit,
                new AuditAccountabilityPayload(
                        "PORT_ACCOUNT",
                        account.docType().name(),
                        "",
                        userUuid.toString(),
                        "",
                        "",
                        collabUuidForAudit != null ? collabUuidForAudit : "",
                        newVerifyingIspCnpj,
                        listActivePartnerUuidStrings(newVerifyingIspId),
                        Base64.getEncoder().encodeToString(request.getRootSignature().toByteArray()),
                        request.getTargetCollaboratorSignature().isEmpty()
                                ? ""
                                : Base64.getEncoder().encodeToString(request.getTargetCollaboratorSignature().toByteArray()),
                        nowMs),
                nowMs);

        String previousHomeNode = account.homeNodeAddress();
        registerAfterCommit(() -> {
            dhtStoreService.storeHomeNodeClaim(
                    portedBundle.getHomeNodeClaim(),
                    portedBundle.getCredential(),
                    account.rootEd25519Pub());
            if (previousHomeNode.equalsIgnoreCase(principalPublicAddress)
                    && !targetHomeNode.equalsIgnoreCase(principalPublicAddress)) {
                dhtStoreService.storeRedirectTombstone(userUuid.toString(), targetHomeNode);
            }
        });

        return portedBundle;
    }

    public List<RevocationEvent> queryRevocationsSince(long sinceSeq) {
        return jdbcTemplate.query("""
                SELECT revocation_id, revocation_seq, target_uuid, revoked_device_id,
                       reason, principal_signature, replacement_notice_bytes, created_at_epoch_ms
                  FROM credential_revocations
                 WHERE revocation_seq > ?
                 ORDER BY revocation_seq ASC
                """,
                this::mapRevocationRow,
                sinceSeq);
    }

    // =========================================================================
    // HELPERS PRIVADOS DE VALIDACAO, ASSINATURA E PERSISTENCIA
    // =========================================================================

    private IspRow requireActiveIspMatchingCallerSan(
            String normalizedIspCnpj,
            X509Certificate peerCert,
            Set<String> peerSans) {
        if (peerCert == null) {
            throw Status.UNAUTHENTICATED
                    .withDescription("Certificado X.509 mTLS obrigatorio para operacoes de governanca de ISP")
                    .asRuntimeException();
        }
        List<IspRow> rows = jdbcTemplate.query("""
                SELECT id, cnpj, trade_name, corporate_uuid, node_address, status
                  FROM verified_isps
                 WHERE cnpj = ?
                """,
                (rs, rowNum) -> new IspRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("cnpj"),
                        rs.getString("trade_name"),
                        rs.getObject("corporate_uuid", UUID.class),
                        rs.getString("node_address"),
                        rs.getString("status")),
                normalizedIspCnpj);

        if (rows.isEmpty() || !"ACTIVE".equalsIgnoreCase(rows.getFirst().status())) {
            throw Status.PERMISSION_DENIED
                    .withDescription("Provedor ISP nao registrado ou inativo: " + normalizedIspCnpj)
                    .asRuntimeException();
        }

        IspRow isp = rows.getFirst();
        String expectedFull = isp.nodeAddress().trim().toLowerCase(Locale.ROOT);
        String expectedHost = extractHostFromNodeAddress(expectedFull);
        Set<String> safeSans = peerSans != null ? peerSans : Set.of();

        if (!safeSans.contains(expectedFull) && !safeSans.contains(expectedHost)) {
            throw Status.PERMISSION_DENIED
                    .withDescription("SAN do certificado mTLS (" + safeSans
                            + ") nao corresponde ao node_address registrado do ISP (" + isp.nodeAddress() + ")")
                    .asRuntimeException();
        }
        return isp;
    }

    private PartnerRow requireActiveManagingPartner(UUID ispId, UUID partnerUuid) {
        List<PartnerRow> rows = jdbcTemplate.query("""
                SELECT id, isp_id, partner_uuid, partner_ed25519_pub, active
                  FROM isp_managing_partners
                 WHERE isp_id = ? AND partner_uuid = ?
                """,
                (rs, rowNum) -> new PartnerRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("isp_id", UUID.class),
                        rs.getObject("partner_uuid", UUID.class),
                        rs.getBytes("partner_ed25519_pub"),
                        rs.getBoolean("active")),
                ispId,
                partnerUuid);

        if (rows.isEmpty() || !rows.getFirst().active()) {
            throw Status.PERMISSION_DENIED
                    .withDescription("Socio Administrador nao autorizado para este provedor")
                    .asRuntimeException();
        }
        return rows.getFirst();
    }

    private CollaboratorRow verifyActiveCollaborator(IspRow isp, CollaboratorAuthCert cert, long nowMs) {
        if (cert == null || cert.getCollaboratorUuid().isBlank()) {
            throw Status.PERMISSION_DENIED
                    .withDescription("CollaboratorAuthCert ausente")
                    .asRuntimeException();
        }
        String certCnpj = normalizeDocOrThrow(DocumentType.CNPJ, cert.getIspCnpj());
        if (!certCnpj.equals(isp.cnpj())) {
            throw Status.PERMISSION_DENIED
                    .withDescription("CollaboratorAuthCert pertence a outro CNPJ de provedor")
                    .asRuntimeException();
        }
        if (cert.getExpiresAtMs() <= nowMs || cert.getIssuedAtMs() > nowMs + FRESHNESS_WINDOW_MS) {
            throw Status.PERMISSION_DENIED
                    .withDescription("CollaboratorAuthCert expirado ou fora de validade")
                    .asRuntimeException();
        }

        UUID collabUuid = parseUuidOrThrow(cert.getCollaboratorUuid(), "collaborator_uuid");
        UUID partnerUuid = parseUuidOrThrow(cert.getPartnerUuid(), "partner_uuid");
        PartnerRow partner = requireActiveManagingPartner(isp.id(), partnerUuid);

        if (!CanonicalTranscripts.verifyEd25519(
                partner.ed25519Pub(),
                CanonicalTranscripts.buildCollaboratorAuthTranscript(cert),
                cert.getPartnerSignature().toByteArray())) {
            throw Status.PERMISSION_DENIED
                    .withDescription("Assinatura do Socio Administrador invalida no CollaboratorAuthCert")
                    .asRuntimeException();
        }

        List<CollaboratorRow> rows = jdbcTemplate.query("""
                SELECT id, isp_id, collaborator_uuid, collaborator_ed25519_pub, expires_at, revoked_at
                  FROM verified_collaborators
                 WHERE isp_id = ? AND collaborator_uuid = ?
                """,
                (rs, rowNum) -> {
                    Timestamp revTs = rs.getTimestamp("revoked_at");
                    return new CollaboratorRow(
                            rs.getObject("id", UUID.class),
                            rs.getObject("isp_id", UUID.class),
                            rs.getObject("collaborator_uuid", UUID.class),
                            rs.getBytes("collaborator_ed25519_pub"),
                            rs.getTimestamp("expires_at").toInstant(),
                            revTs != null ? revTs.toInstant() : null);
                },
                isp.id(),
                collabUuid);

        if (rows.isEmpty()) {
            throw Status.PERMISSION_DENIED
                    .withDescription("Colaborador nao registrado na base do Principal")
                    .asRuntimeException();
        }
        CollaboratorRow dbRow = rows.getFirst();
        if (dbRow.revokedAt() != null) {
            throw Status.PERMISSION_DENIED
                    .withDescription("Colaborador revogado pelo Socio Administrador")
                    .asRuntimeException();
        }
        if (dbRow.expiresAt().toEpochMilli() <= nowMs) {
            throw Status.PERMISSION_DENIED
                    .withDescription("Autorizacao do Colaborador expirada")
                    .asRuntimeException();
        }
        if (!Arrays.equals(dbRow.ed25519Pub(), cert.getCollabEd25519Pub().toByteArray())) {
            throw Status.PERMISSION_DENIED
                    .withDescription("Chave publica Ed25519 do Colaborador diverge do registro ativo")
                    .asRuntimeException();
        }
        return dbRow;
    }

    private String verifyUserConsent(
            UserVerificationConsent consent,
            String expectedIspCnpj,
            String rawNormalizedDocument,
            long nowMs) {
        if (consent == null || consent.getUserUuid().isBlank()) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("UserVerificationConsent ausente")
                    .asRuntimeException();
        }
        verifyTimestampFreshness(consent.getTimestampMs(), nowMs, "user_consent.timestamp_ms");

        String consentIspCnpj = normalizeDocOrThrow(DocumentType.CNPJ, consent.getIspCnpj());
        if (!consentIspCnpj.equals(expectedIspCnpj)) {
            throw Status.PERMISSION_DENIED
                    .withDescription("Consentimento do assinante foi assinado para outro CNPJ de provedor")
                    .asRuntimeException();
        }

        String normalizedDoc = normalizeDocOrThrow(consent.getDocType(), rawNormalizedDocument);
        byte[] expectedDocSha256 = CanonicalTranscripts.sha256Utf8(normalizedDoc);
        if (!Arrays.equals(expectedDocSha256, consent.getDocSha256().toByteArray())) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("doc_sha256 do consentimento nao confere com o documento informado")
                    .asRuntimeException();
        }

        byte[] userEdPub = consent.getUserEd25519Pub().toByteArray();
        if (!CanonicalTranscripts.verifyIdentityBinding(consent.getUserUuid(), userEdPub)
                || consent.getUserX25519Pub().size() != CanonicalTranscripts.RAW_KEY_LENGTH) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("user_uuid incompativel com user_ed25519_pub ou user_x25519_pub invalida")
                    .asRuntimeException();
        }

        byte[] consentTranscript = CanonicalTranscripts.buildUserVerificationConsentTranscript(consent);
        if (!CanonicalTranscripts.verifyEd25519(
                userEdPub,
                consentTranscript,
                consent.getUserSignature().toByteArray())) {
            throw Status.PERMISSION_DENIED
                    .withDescription("Assinatura Ed25519 do assinante (ISPCHAT-VERIFY-REQ-V1) invalida")
                    .asRuntimeException();
        }
        return normalizedDoc;
    }

    private String resolveAuthorizedHomeNodeAddress(String requestedAddress, String ispNodeAddress) {
        String trimmed = requestedAddress != null ? requestedAddress.trim().toLowerCase(Locale.ROOT) : "";
        if (trimmed.isEmpty()) {
            return ispNodeAddress.toLowerCase(Locale.ROOT);
        }
        if (trimmed.equalsIgnoreCase(ispNodeAddress) || trimmed.equalsIgnoreCase(principalPublicAddress)) {
            return trimmed;
        }
        throw Status.PERMISSION_DENIED
                .withDescription("home_node_address deve ser o No do ISP verificador ou o Fallback Node Principal")
                .asRuntimeException();
    }

    private VerificationBundle signCredentialAndHomeClaim(
            UUID userUuid,
            byte[] rootEd25519Pub,
            byte[] rootX25519Pub,
            DocumentType docType,
            String publicCnpjOrNull,
            String publicDisplayNameOrNull,
            String verifyingIspCnpj,
            String verifyingIspName,
            String homeNodeAddress,
            long routingEpoch,
            long nowMs) {
        VerificationCredential unsignedCred = VerificationCredential.newBuilder()
                .setCredentialId(CanonicalTranscripts.generateTemporalUuidV7().toString())
                .setUserUuid(userUuid.toString())
                .setRootEd25519Pub(ByteString.copyFrom(rootEd25519Pub))
                .setRootX25519Pub(ByteString.copyFrom(rootX25519Pub))
                .setDocType(docType)
                .setPublicCnpj(docType == DocumentType.CNPJ && publicCnpjOrNull != null ? publicCnpjOrNull : "")
                .setPublicDisplayName(docType == DocumentType.CNPJ && publicDisplayNameOrNull != null ? publicDisplayNameOrNull : "")
                .setVerifyingIspCnpj(verifyingIspCnpj)
                .setVerifyingIspName(verifyingIspName)
                .setHomeNodeAddress(homeNodeAddress.toLowerCase(Locale.ROOT))
                .setRoutingEpoch(routingEpoch)
                .setIssuedAtMs(nowMs)
                .setExpiresAtMs(nowMs + CREDENTIAL_TTL_MS)
                .setPrincipalCert(keyMaterial.principalCert())
                .build();

        byte[] credSig = keyMaterial.signWithPrincipal(
                CanonicalTranscripts.buildVerificationCredentialTranscript(unsignedCred));
        VerificationCredential signedCred = unsignedCred.toBuilder()
                .setPrincipalSignature(ByteString.copyFrom(credSig))
                .build();

        HomeNodeClaim unsignedClaim = HomeNodeClaim.newBuilder()
                .setUserUuid(userUuid.toString())
                .setHomeNodeAddress(homeNodeAddress.toLowerCase(Locale.ROOT))
                .setRoutingEpoch(routingEpoch)
                .setIssuedAtMs(nowMs)
                .setSignedByPrincipal(true)
                .build();

        byte[] claimSig = keyMaterial.signWithPrincipal(
                CanonicalTranscripts.buildHomeNodeClaimTranscript(unsignedClaim));
        HomeNodeClaim signedClaim = unsignedClaim.toBuilder()
                .setClaimSignature(ByteString.copyFrom(claimSig))
                .build();

        return VerificationBundle.newBuilder()
                .setCredential(signedCred)
                .setHomeNodeClaim(signedClaim)
                .build();
    }

    private UUID upsertPartnerIspRow(
            String normalizedCnpj,
            String tradeName,
            UUID corporateUuid,
            String nodeAddress,
            long nowMs) {
        List<UUID> existing = jdbcTemplate.query(
                "SELECT id FROM verified_isps WHERE cnpj = ?",
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                normalizedCnpj);
        Timestamp ts = Timestamp.from(Instant.ofEpochMilli(nowMs));
        if (!existing.isEmpty()) {
            UUID ispId = existing.getFirst();
            jdbcTemplate.update("""
                    UPDATE verified_isps
                       SET trade_name = ?, corporate_uuid = ?, node_address = ?, status = 'ACTIVE', updated_at = ?
                     WHERE id = ?
                    """,
                    tradeName, corporateUuid, nodeAddress, ts, ispId);
            return ispId;
        }
        UUID ispId = CanonicalTranscripts.generateTemporalUuidV7();
        jdbcTemplate.update("""
                INSERT INTO verified_isps (id, cnpj, trade_name, corporate_uuid, node_address, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                """,
                ispId, normalizedCnpj, tradeName, corporateUuid, nodeAddress, ts, ts);
        return ispId;
    }

    private void upsertManagingPartnerRow(
            UUID ispId,
            UUID partnerUuid,
            byte[] cpfBlindIndex,
            byte[] edPub,
            byte[] xPub,
            long nowMs) {
        jdbcTemplate.update("""
                INSERT INTO isp_managing_partners (
                    id, isp_id, partner_uuid, cpf_blind_index, partner_ed25519_pub, partner_x25519_pub, active, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, TRUE, ?)
                ON CONFLICT (isp_id, partner_uuid) DO UPDATE SET
                    cpf_blind_index = EXCLUDED.cpf_blind_index,
                    partner_ed25519_pub = EXCLUDED.partner_ed25519_pub,
                    partner_x25519_pub = EXCLUDED.partner_x25519_pub,
                    active = TRUE
                """,
                CanonicalTranscripts.generateTemporalUuidV7(),
                ispId,
                partnerUuid,
                cpfBlindIndex,
                edPub,
                xPub,
                Timestamp.from(Instant.ofEpochMilli(nowMs)));
    }

    private VerificationBundle issueAndUpsertInitialSeededAccount(
            UUID userUuid,
            DocumentType docType,
            byte[] docBlindIndex,
            byte[] rootEdPub,
            byte[] rootXPub,
            String publicCnpj,
            String publicDisplayName,
            UUID ispId,
            String ispCnpj,
            String ispTradeName,
            String nodeAddress,
            long nowMs) {
        List<VerifiedAccountRow> existingActive = jdbcTemplate.query("""
                SELECT va.id, va.user_uuid, va.doc_type, va.doc_blind_index, va.root_ed25519_pub, va.root_x25519_pub,
                       va.public_cnpj, va.public_display_name, va.verifying_isp_id,
                       vi.cnpj AS verifying_isp_cnpj, vi.trade_name AS verifying_isp_name,
                       va.home_node_address, va.routing_epoch, va.credential_bytes, va.home_claim_bytes, va.status
                  FROM verified_accounts va
                  JOIN verified_isps vi ON vi.id = va.verifying_isp_id
                 WHERE va.doc_blind_index = ? AND va.status = 'ACTIVE'
                """,
                this::mapVerifiedAccountRow,
                docBlindIndex);

        long epoch = (!existingActive.isEmpty() && existingActive.getFirst().userUuid().equals(userUuid))
                ? existingActive.getFirst().routingEpoch()
                : 1L;

        VerificationBundle bundle = signCredentialAndHomeClaim(
                userUuid,
                rootEdPub,
                rootXPub,
                docType,
                publicCnpj,
                publicDisplayName,
                ispCnpj,
                ispTradeName,
                nodeAddress,
                epoch,
                nowMs);

        Timestamp ts = Timestamp.from(Instant.ofEpochMilli(nowMs));
        if (!existingActive.isEmpty()) {
            if (!existingActive.getFirst().userUuid().equals(userUuid)) {
                throw Status.ALREADY_EXISTS
                        .withDescription("Documento em RegisterPartnerIsp ja vinculado a outra identidade ativa")
                        .asRuntimeException();
            }
            jdbcTemplate.update("""
                    UPDATE verified_accounts
                       SET public_cnpj = ?, public_display_name = ?, verifying_isp_id = ?, home_node_address = ?,
                           credential_bytes = ?, home_claim_bytes = ?, updated_at = ?
                     WHERE id = ?
                    """,
                    publicCnpj,
                    publicDisplayName,
                    ispId,
                    nodeAddress,
                    bundle.getCredential().toByteArray(),
                    bundle.getHomeNodeClaim().toByteArray(),
                    ts,
                    existingActive.getFirst().id());
        } else {
            jdbcTemplate.update("""
                    INSERT INTO verified_accounts (
                        id, user_uuid, doc_type, doc_blind_index, root_ed25519_pub, root_x25519_pub,
                        public_cnpj, public_display_name, verifying_isp_id, home_node_address, routing_epoch,
                        credential_bytes, home_claim_bytes, status, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                    """,
                    CanonicalTranscripts.generateTemporalUuidV7(),
                    userUuid,
                    docType.name(),
                    docBlindIndex,
                    rootEdPub,
                    rootXPub,
                    publicCnpj,
                    publicDisplayName,
                    ispId,
                    nodeAddress,
                    epoch,
                    bundle.getCredential().toByteArray(),
                    bundle.getHomeNodeClaim().toByteArray(),
                    ts,
                    ts);
        }

        registerAfterCommit(() -> dhtStoreService.storeHomeNodeClaim(
                bundle.getHomeNodeClaim(), bundle.getCredential(), rootEdPub));
        return bundle;
    }

    private RevocationEvent insertRevocationEventLocked(
            UUID targetUuid,
            UUID revokedDeviceUuidOrNull,
            String reason,
            IdentityReplacementNotice replacementNoticeOrNull,
            long nowMs) {
        return insertRevocationEventWithIdLocked(
                CanonicalTranscripts.generateTemporalUuidV7(),
                targetUuid,
                revokedDeviceUuidOrNull,
                reason,
                replacementNoticeOrNull,
                nowMs);
    }

    private RevocationEvent insertRevocationEventWithIdLocked(
            UUID revocationId,
            UUID targetUuid,
            UUID revokedDeviceUuidOrNull,
            String reason,
            IdentityReplacementNotice replacementNoticeOrNull,
            long nowMs) {
        // Serializa revogacoes evitando visibilidade MVCC fora de ordem no cursor StreamRevocations
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + REVOCATION_ADVISORY_LOCK_ID + ")");

        Long maxSeq = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(revocation_seq), 0) FROM credential_revocations",
                Long.class);
        long nextSeq = (maxSeq != null ? maxSeq : 0L) + 1L;

        RevocationEvent.Builder unsignedBuilder = RevocationEvent.newBuilder()
                .setRevocationId(revocationId.toString())
                .setRevocationSeq(nextSeq)
                .setTargetUuid(targetUuid.toString())
                .setRevokedDeviceId(revokedDeviceUuidOrNull != null ? revokedDeviceUuidOrNull.toString() : "")
                .setReason(reason)
                .setCreatedAtMs(nowMs);
        if (replacementNoticeOrNull != null) {
            unsignedBuilder.setReplacementNotice(replacementNoticeOrNull);
        }
        RevocationEvent unsignedEvent = unsignedBuilder.build();
        byte[] sig = keyMaterial.signWithPrincipal(
                CanonicalTranscripts.buildRevocationEventTranscript(unsignedEvent));
        RevocationEvent signedEvent = unsignedEvent.toBuilder()
                .setPrincipalSignature(ByteString.copyFrom(sig))
                .build();

        jdbcTemplate.update("""
                INSERT INTO credential_revocations (
                    revocation_id, revocation_seq, target_uuid, revoked_device_id,
                    reason, principal_signature, replacement_notice_bytes, created_at_epoch_ms, created_at
                ) OVERRIDING SYSTEM VALUE VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                revocationId,
                nextSeq,
                targetUuid,
                revokedDeviceUuidOrNull,
                reason,
                sig,
                replacementNoticeOrNull != null ? replacementNoticeOrNull.toByteArray() : null,
                nowMs,
                Timestamp.from(Instant.ofEpochMilli(nowMs)));

        return signedEvent;
    }

    private void publishRevocationPostCommit(RevocationEvent event) {
        registerAfterCommit(() -> {
            // Aplica imediatamente tambem nas estruturas locais do proprio Fallback Home Node
            String targetUuid = event.getTargetUuid();
            String revokedDeviceId = event.getRevokedDeviceId();
            if (!revokedDeviceId.isBlank()) {
                tierRateLimiterService.markDeviceRevoked(targetUuid, revokedDeviceId, event.getReason());
                dhtStoreService.evictDeviceSession(targetUuid, revokedDeviceId);
                localSessionRegistry.revokeAndDisconnect(targetUuid, revokedDeviceId, event);
            } else {
                tierRateLimiterService.markAccountRevoked(targetUuid, event.getReason());
                dhtStoreService.evictAllSessions(targetUuid);
                if (event.hasReplacementNotice() && !event.getReplacementNotice().getRevocationId().isBlank()) {
                    dhtStoreService.storeReplacementNotice(event.getReplacementNotice());
                }
                localSessionRegistry.revokeAndDisconnect(targetUuid, "", event);
            }
            for (Consumer<RevocationEvent> listener : revocationListeners) {
                try {
                    listener.accept(event);
                } catch (Exception ignored) {
                }
            }
        });
    }

    private VerifiedAccountRow requireActiveVerifiedAccount(UUID userUuid) {
        List<VerifiedAccountRow> rows = jdbcTemplate.query("""
                SELECT va.id, va.user_uuid, va.doc_type, va.doc_blind_index, va.root_ed25519_pub, va.root_x25519_pub,
                       va.public_cnpj, va.public_display_name, va.verifying_isp_id,
                       vi.cnpj AS verifying_isp_cnpj, vi.trade_name AS verifying_isp_name,
                       va.home_node_address, va.routing_epoch, va.credential_bytes, va.home_claim_bytes, va.status
                  FROM verified_accounts va
                  JOIN verified_isps vi ON vi.id = va.verifying_isp_id
                 WHERE va.user_uuid = ?
                """,
                this::mapVerifiedAccountRow,
                userUuid);
        if (rows.isEmpty() || !"ACTIVE".equalsIgnoreCase(rows.getFirst().status())) {
            throw Status.PERMISSION_DENIED
                    .withDescription("Conta verificada inexistente ou revogada")
                    .asRuntimeException();
        }
        return rows.getFirst();
    }

    private boolean isDeviceRevokedInDb(UUID userUuid, UUID deviceUuid) {
        if (tierRateLimiterService.isDeviceRevoked(deviceUuid.toString())) {
            return true;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                  FROM credential_revocations
                 WHERE target_uuid = ?
                   AND (revoked_device_id IS NULL OR revoked_device_id = ?)
                """,
                Integer.class,
                userUuid,
                deviceUuid);
        return count != null && count > 0;
    }

    private List<String> listActivePartnerUuidStrings(UUID ispId) {
        return jdbcTemplate.query(
                "SELECT partner_uuid FROM isp_managing_partners WHERE isp_id = ? AND active = TRUE ORDER BY partner_uuid",
                (rs, rowNum) -> rs.getObject("partner_uuid", UUID.class).toString(),
                ispId);
    }

    private String checkAndStageConsentAntiReplay(byte[] userConsentSig, long nowMs) {
        consumedConsentDigests.entrySet().removeIf(e -> e.getValue() <= nowMs);
        String key = HexFormat.of().formatHex(CanonicalTranscripts.sha256(userConsentSig));
        Long expires = consumedConsentDigests.get(key);
        if (expires != null && expires > nowMs) {
            throw Status.PERMISSION_DENIED
                    .withDescription("Replay detectado: UserVerificationConsent ja utilizado anteriormente")
                    .asRuntimeException();
        }
        return key;
    }

    private void commitConsentReplayKeyAndCacheClaim(
            String consentReplayKey,
            long nowMs,
            VerificationBundle bundle) {
        registerAfterCommit(() -> {
            consumedConsentDigests.put(consentReplayKey, nowMs + (2L * FRESHNESS_WINDOW_MS));
            dhtStoreService.storeHomeNodeClaim(
                    bundle.getHomeNodeClaim(),
                    bundle.getCredential(),
                    bundle.getCredential().getRootEd25519Pub().toByteArray());
            if (bundle.hasReplacementNotice() && !bundle.getReplacementNotice().getRevocationId().isBlank()) {
                dhtStoreService.storeReplacementNotice(bundle.getReplacementNotice());
            }
        });
    }

    private static void registerAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private VerifiedAccountRow mapVerifiedAccountRow(ResultSet rs, int rowNum) throws SQLException {
        return new VerifiedAccountRow(
                rs.getObject("id", UUID.class),
                rs.getObject("user_uuid", UUID.class),
                DocumentType.valueOf(rs.getString("doc_type")),
                rs.getBytes("doc_blind_index"),
                rs.getBytes("root_ed25519_pub"),
                rs.getBytes("root_x25519_pub"),
                rs.getString("public_cnpj"),
                rs.getString("public_display_name"),
                rs.getObject("verifying_isp_id", UUID.class),
                rs.getString("verifying_isp_cnpj"),
                rs.getString("verifying_isp_name"),
                rs.getString("home_node_address"),
                rs.getLong("routing_epoch"),
                rs.getBytes("credential_bytes"),
                rs.getBytes("home_claim_bytes"),
                rs.getString("status"));
    }

    private RevocationEvent mapRevocationRow(ResultSet rs, int rowNum) throws SQLException {
        UUID revDev = rs.getObject("revoked_device_id", UUID.class);
        byte[] noticeBytes = rs.getBytes("replacement_notice_bytes");
        RevocationEvent.Builder builder = RevocationEvent.newBuilder()
                .setRevocationId(rs.getObject("revocation_id", UUID.class).toString())
                .setRevocationSeq(rs.getLong("revocation_seq"))
                .setTargetUuid(rs.getObject("target_uuid", UUID.class).toString())
                .setRevokedDeviceId(revDev != null ? revDev.toString() : "")
                .setReason(rs.getString("reason"))
                .setPrincipalSignature(ByteString.copyFrom(rs.getBytes("principal_signature")))
                .setCreatedAtMs(rs.getLong("created_at_epoch_ms"));
        if (noticeBytes != null && noticeBytes.length > 0) {
            try {
                builder.setReplacementNotice(IdentityReplacementNotice.parseFrom(noticeBytes));
            } catch (InvalidProtocolBufferException e) {
                throw new SQLException("Falha ao decodificar replacement_notice_bytes", e);
            }
        }
        return builder.build();
    }

    private static void verifyTimestampFreshness(long timestampMs, long nowMs, String fieldName) {
        if (Math.abs(nowMs - timestampMs) > FRESHNESS_WINDOW_MS) {
            throw Status.PERMISSION_DENIED
                    .withDescription("Timestamp fora da janela de 300s em " + fieldName)
                    .asRuntimeException();
        }
    }

    private static String normalizeDocOrThrow(DocumentType type, String rawDoc) {
        try {
            return CanonicalTranscripts.normalizeAndValidateDocument(type, rawDoc);
        } catch (IllegalArgumentException e) {
            throw Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException();
        }
    }

    private static UUID parseUuidOrThrow(String rawUuid, String fieldName) {
        try {
            return UUID.fromString(rawUuid.trim());
        } catch (Exception e) {
            throw Status.INVALID_ARGUMENT.withDescription("UUID invalido em " + fieldName).asRuntimeException();
        }
    }

    static String extractHostFromNodeAddress(String nodeAddress) {
        String trimmed = nodeAddress.trim().toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("[")) {
            int end = trimmed.indexOf(']');
            return end > 0 ? trimmed.substring(1, end) : trimmed;
        }
        int firstColon = trimmed.indexOf(':');
        int lastColon = trimmed.lastIndexOf(':');
        if (firstColon > 0 && firstColon == lastColon) {
            return trimmed.substring(0, firstColon);
        }
        return trimmed;
    }
}
