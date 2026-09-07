-- Email attachments stored in MinIO (metadata in Postgres; no bytea).

CREATE TABLE email_attachments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id          UUID NOT NULL REFERENCES companies (id),
    email_id            UUID NOT NULL REFERENCES emails (id) ON DELETE CASCADE,
    original_filename   VARCHAR(512) NOT NULL,
    content_type        VARCHAR(128) NOT NULL,
    storage_key         VARCHAR(1024) NOT NULL,
    size_bytes          BIGINT NOT NULL DEFAULT 0,
    checksum_sha256     VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          VARCHAR(128),
    updated_by          VARCHAR(128),
    CONSTRAINT uq_email_attachments_storage_key UNIQUE (storage_key),
    CONSTRAINT chk_email_attachments_size CHECK (size_bytes >= 0)
);

CREATE INDEX idx_email_attachments_email ON email_attachments (email_id);
CREATE INDEX idx_email_attachments_company_email ON email_attachments (company_id, email_id);
