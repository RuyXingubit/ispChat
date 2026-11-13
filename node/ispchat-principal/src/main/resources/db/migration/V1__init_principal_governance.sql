-- ============================================================================
-- V1__init_principal_governance.sql
-- Esquema PostgreSQL 17 para Governanca da Cadeia de Confianca, Diretorio CNPJ,
-- Ledger de Auditoria Hash-Chained com Escrow X25519 e Feed Monotono de Revogacoes.
-- Todas as chaves primarias sao UUIDv7 (RFC 9562) geradas na aplicacao.
-- ============================================================================

CREATE TABLE verified_isps (
    id              UUID PRIMARY KEY,
    cnpj            VARCHAR(14) NOT NULL UNIQUE,
    trade_name      VARCHAR(255) NOT NULL,
    corporate_uuid  UUID NOT NULL UNIQUE,
    node_address    VARCHAR(255) NOT NULL UNIQUE,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE isp_managing_partners (
    id                    UUID PRIMARY KEY,
    isp_id                UUID NOT NULL REFERENCES verified_isps(id),
    partner_uuid          UUID NOT NULL,
    cpf_blind_index       BYTEA NOT NULL,
    partner_ed25519_pub   BYTEA NOT NULL,
    partner_x25519_pub    BYTEA NOT NULL,
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_isp_managing_partner UNIQUE (isp_id, partner_uuid)
);

CREATE TABLE verified_collaborators (
    id                          UUID PRIMARY KEY,
    isp_id                      UUID NOT NULL REFERENCES verified_isps(id),
    collaborator_uuid           UUID NOT NULL UNIQUE,
    cpf_blind_index             BYTEA NOT NULL,
    collaborator_ed25519_pub    BYTEA NOT NULL,
    authorized_by_partner_uuid  UUID NOT NULL,
    cert_bytes                  BYTEA NOT NULL,
    issued_at                   TIMESTAMPTZ NOT NULL,
    expires_at                  TIMESTAMPTZ NOT NULL,
    revoked_at                  TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_verified_collaborators_isp ON verified_collaborators(isp_id);

CREATE TABLE verified_accounts (
    id                   UUID PRIMARY KEY,
    user_uuid            UUID NOT NULL UNIQUE,
    doc_type             VARCHAR(16) NOT NULL CHECK (doc_type IN ('CPF', 'CNPJ')),
    doc_blind_index      BYTEA NOT NULL,
    root_ed25519_pub     BYTEA NOT NULL,
    root_x25519_pub      BYTEA NOT NULL,
    public_cnpj          VARCHAR(14),
    public_display_name  VARCHAR(255),
    verifying_isp_id     UUID NOT NULL REFERENCES verified_isps(id),
    home_node_address    VARCHAR(255) NOT NULL,
    routing_epoch        BIGINT NOT NULL DEFAULT 1,
    credential_bytes     BYTEA NOT NULL,
    home_claim_bytes     BYTEA NOT NULL,
    status               VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED')),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indice parcial unico: garante 1 documento ativo = 1 UUIDv7, permitindo que contas
-- historicas REVOKED coexistam sem conflito apos SubmitCpfRebind (0h hold).
CREATE UNIQUE INDEX uk_verified_accounts_active_doc
    ON verified_accounts(doc_blind_index)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_verified_accounts_active_cnpj
    ON verified_accounts(public_cnpj, public_display_name)
    WHERE status = 'ACTIVE' AND doc_type = 'CNPJ';

CREATE TABLE verification_audit_ledger (
    entry_id              UUID PRIMARY KEY,
    seq                   BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE NOT NULL,
    action_type           VARCHAR(64) NOT NULL,
    target_uuid           UUID NOT NULL,
    doc_blind_index       BYTEA NOT NULL,
    isp_cnpj              VARCHAR(14) NOT NULL,
    collaborator_uuid     UUID,
    ephemeral_x25519_pub  BYTEA NOT NULL,
    sealed_audit_blob     BYTEA NOT NULL,
    prev_entry_hash       BYTEA NOT NULL UNIQUE,
    entry_hash            BYTEA NOT NULL UNIQUE,
    created_at_epoch_ms   BIGINT NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE credential_revocations (
    revocation_id             UUID PRIMARY KEY,
    revocation_seq            BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE NOT NULL,
    target_uuid               UUID NOT NULL,
    revoked_device_id         UUID,
    reason                    VARCHAR(255) NOT NULL,
    principal_signature       BYTEA NOT NULL,
    replacement_notice_bytes  BYTEA,
    created_at_epoch_ms       BIGINT NOT NULL,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_credential_revocations_target
    ON credential_revocations(target_uuid, revoked_device_id);
