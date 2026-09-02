-- Phase 3 — tables métier MVP (PRD §16). company_id lorsque pertinent (PRD §17).

CREATE TABLE emails (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID NOT NULL REFERENCES companies (id),
    message_id      VARCHAR(998),
    from_address    VARCHAR(320) NOT NULL,
    to_address      VARCHAR(320) NOT NULL,
    subject         VARCHAR(998),
    body_text       TEXT,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    status          VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(128),
    updated_by      VARCHAR(128),
    CONSTRAINT chk_emails_status CHECK (status IN ('RECEIVED', 'ANALYZED', 'ERROR')),
    CONSTRAINT uq_emails_company_message UNIQUE (company_id, message_id)
);

CREATE INDEX idx_emails_company_received ON emails (company_id, received_at DESC);

CREATE TABLE email_analysis (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id          UUID NOT NULL REFERENCES companies (id),
    email_id            UUID NOT NULL REFERENCES emails (id) ON DELETE CASCADE,
    category            VARCHAR(32) NOT NULL,
    priority            VARCHAR(16) NOT NULL,
    intent              VARCHAR(128),
    summary             TEXT,
    suggested_reply     TEXT,
    confidence_score    NUMERIC(4, 3),
    status              VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
    approval_status     VARCHAR(32),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          VARCHAR(128),
    updated_by          VARCHAR(128),
    CONSTRAINT uq_email_analysis_email UNIQUE (email_id),
    CONSTRAINT chk_email_analysis_category CHECK (category IN (
        'CLIENT', 'PROSPECT', 'FACTURE', 'FOURNISSEUR', 'SUPPORT', 'ADMINISTRATIF', 'SPAM', 'AUTRE'
    )),
    CONSTRAINT chk_email_analysis_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT chk_email_analysis_status CHECK (status IN (
        'COMPLETED', 'REVIEW_REQUIRED', 'AI_PARSING_ERROR', 'AI_UNAVAILABLE'
    )),
    CONSTRAINT chk_email_analysis_approval CHECK (
        approval_status IS NULL OR approval_status IN (
            'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'EXECUTED'
        )
    )
);

CREATE TABLE leads (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id          UUID NOT NULL REFERENCES companies (id),
    source              VARCHAR(32) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'NEW',
    email               VARCHAR(320),
    full_name           VARCHAR(255) NOT NULL,
    company_name        VARCHAR(255),
    phone               VARCHAR(64),
    score               INTEGER NOT NULL DEFAULT 0,
    summary             TEXT,
    probable_need       TEXT,
    urgency             VARCHAR(32),
    potential_budget    VARCHAR(64),
    recommended_action  TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          VARCHAR(128),
    updated_by          VARCHAR(128),
    CONSTRAINT chk_leads_source CHECK (source IN ('WEB_FORM', 'WEBHOOK', 'EMAIL', 'CSV', 'API')),
    CONSTRAINT chk_leads_status CHECK (status IN (
        'NEW', 'QUALIFIED', 'CONTACTED', 'PROPOSAL', 'WON', 'LOST'
    )),
    CONSTRAINT chk_leads_score CHECK (score BETWEEN 0 AND 100)
);

CREATE INDEX idx_leads_company_status ON leads (company_id, status);

CREATE TABLE lead_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID NOT NULL REFERENCES companies (id),
    lead_id         UUID NOT NULL REFERENCES leads (id) ON DELETE CASCADE,
    event_type      VARCHAR(64) NOT NULL,
    payload         JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(128),
    updated_by      VARCHAR(128)
);

CREATE INDEX idx_lead_events_lead ON lead_events (lead_id, occurred_at);

CREATE TABLE documents (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id          UUID NOT NULL REFERENCES companies (id),
    original_filename   VARCHAR(512) NOT NULL,
    content_type        VARCHAR(128) NOT NULL,
    storage_key         VARCHAR(1024) NOT NULL,
    size_bytes          BIGINT NOT NULL DEFAULT 0,
    document_type       VARCHAR(32) NOT NULL DEFAULT 'AUTRE',
    status              VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',
    checksum_sha256     VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          VARCHAR(128),
    updated_by          VARCHAR(128),
    CONSTRAINT chk_documents_type CHECK (document_type IN (
        'FACTURE', 'DEVIS', 'BON_COMMANDE', 'CONTRAT', 'COURRIER', 'AUTRE'
    )),
    CONSTRAINT chk_documents_status CHECK (status IN (
        'UPLOADED', 'PROCESSING', 'EXTRACTED', 'REVIEW_REQUIRED', 'ERROR'
    )),
    CONSTRAINT uq_documents_storage_key UNIQUE (storage_key)
);

CREATE INDEX idx_documents_company_status ON documents (company_id, status);

CREATE TABLE document_extractions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id          UUID NOT NULL REFERENCES companies (id),
    document_id         UUID NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    extracted_json      JSONB NOT NULL DEFAULT '{}'::jsonb,
    confidence_score    NUMERIC(4, 3),
    status              VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          VARCHAR(128),
    updated_by          VARCHAR(128),
    CONSTRAINT uq_document_extractions_document UNIQUE (document_id),
    CONSTRAINT chk_document_extractions_status CHECK (status IN (
        'COMPLETED', 'REVIEW_REQUIRED', 'AI_PARSING_ERROR', 'AI_UNAVAILABLE'
    ))
);

CREATE TABLE customers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID NOT NULL REFERENCES companies (id),
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(320),
    phone           VARCHAR(64),
    address         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(128),
    updated_by      VARCHAR(128)
);

CREATE INDEX idx_customers_company ON customers (company_id);

CREATE TABLE invoices (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id              UUID NOT NULL REFERENCES companies (id),
    customer_id             UUID NOT NULL REFERENCES customers (id),
    invoice_number          VARCHAR(64) NOT NULL,
    invoice_date            DATE NOT NULL,
    due_date                DATE NOT NULL,
    amount_excluding_tax    NUMERIC(12, 2) NOT NULL DEFAULT 0,
    vat                     NUMERIC(12, 2) NOT NULL DEFAULT 0,
    amount_including_tax    NUMERIC(12, 2) NOT NULL DEFAULT 0,
    currency                CHAR(3) NOT NULL DEFAULT 'EUR',
    status                  VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by              VARCHAR(128),
    updated_by              VARCHAR(128),
    CONSTRAINT chk_invoices_status CHECK (status IN ('DRAFT', 'SENT', 'PAID', 'OVERDUE', 'CANCELLED')),
    CONSTRAINT uq_invoices_company_number UNIQUE (company_id, invoice_number)
);

CREATE INDEX idx_invoices_company_status_due ON invoices (company_id, status, due_date);

CREATE TABLE invoice_reminders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID NOT NULL REFERENCES companies (id),
    invoice_id      UUID NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    reminder_level  INTEGER NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING_APPROVAL',
    scheduled_at    TIMESTAMPTZ,
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(128),
    updated_by      VARCHAR(128),
    CONSTRAINT chk_invoice_reminders_level CHECK (reminder_level IN (3, 7, 15, 30)),
    CONSTRAINT chk_invoice_reminders_status CHECK (status IN (
        'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'EXECUTED'
    )),
    CONSTRAINT uq_invoice_reminders_level UNIQUE (invoice_id, reminder_level)
);

CREATE TABLE workflow_runs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID NOT NULL REFERENCES companies (id),
    workflow        VARCHAR(128) NOT NULL,
    execution_id    VARCHAR(128),
    trigger         VARCHAR(64),
    status          VARCHAR(32) NOT NULL,
    error_type      VARCHAR(128),
    error_message   TEXT,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at     TIMESTAMPTZ,
    metadata        JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(128),
    updated_by      VARCHAR(128),
    CONSTRAINT chk_workflow_runs_status CHECK (status IN ('RUNNING', 'SUCCESS', 'ERROR'))
);

CREATE INDEX idx_workflow_runs_company ON workflow_runs (company_id, started_at DESC);

CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID NOT NULL REFERENCES companies (id),
    workflow        VARCHAR(128),
    action          VARCHAR(64) NOT NULL,
    entity_type     VARCHAR(64) NOT NULL,
    entity_id       VARCHAR(64),
    status          VARCHAR(32) NOT NULL,
    metadata        JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(128),
    updated_by      VARCHAR(128),
    CONSTRAINT chk_audit_logs_status CHECK (status IN ('SUCCESS', 'ERROR', 'SKIPPED'))
);

CREATE INDEX idx_audit_logs_company_created ON audit_logs (company_id, created_at DESC);

CREATE TABLE ai_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID NOT NULL REFERENCES companies (id),
    provider        VARCHAR(64) NOT NULL,
    model           VARCHAR(128),
    purpose         VARCHAR(64) NOT NULL,
    prompt_hash     VARCHAR(64),
    status          VARCHAR(32) NOT NULL,
    latency_ms      INTEGER,
    cache_hit       BOOLEAN NOT NULL DEFAULT FALSE,
    error_message   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(128),
    updated_by      VARCHAR(128),
    CONSTRAINT chk_ai_requests_status CHECK (status IN (
        'SUCCESS', 'ERROR', 'AI_UNAVAILABLE', 'AI_PARSING_ERROR'
    ))
);

CREATE INDEX idx_ai_requests_company ON ai_requests (company_id, created_at DESC);

CREATE TABLE notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID NOT NULL REFERENCES companies (id),
    user_id         UUID REFERENCES users (id) ON DELETE SET NULL,
    channel         VARCHAR(32) NOT NULL DEFAULT 'IN_APP',
    title           VARCHAR(255) NOT NULL,
    body            TEXT,
    payload         JSONB NOT NULL DEFAULT '{}'::jsonb,
    read_at         TIMESTAMPTZ,
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(128),
    updated_by      VARCHAR(128),
    CONSTRAINT chk_notifications_channel CHECK (channel IN ('EMAIL', 'IN_APP'))
);

CREATE INDEX idx_notifications_company_user ON notifications (company_id, user_id, created_at DESC);
