-- Phase 3 — identité (multi-tenant readiness, sans isolation SaaS).
-- BaseEntity : id UUID, created_at, updated_at, created_by, updated_by.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE companies (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    siret           VARCHAR(14),
    country         CHAR(2) NOT NULL DEFAULT 'FR',
    timezone        VARCHAR(64) NOT NULL DEFAULT 'Europe/Paris',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(128),
    updated_by      VARCHAR(128)
);

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID NOT NULL REFERENCES companies (id),
    email           VARCHAR(320) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255) NOT NULL,
    role            VARCHAR(32) NOT NULL DEFAULT 'USER',
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(128),
    updated_by      VARCHAR(128),
    CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'USER')),
    CONSTRAINT uq_users_company_email UNIQUE (company_id, email)
);

CREATE INDEX idx_users_company_id ON users (company_id);

CREATE TABLE company_settings (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id                      UUID NOT NULL REFERENCES companies (id),
    ai_provider                     VARCHAR(64) NOT NULL DEFAULT 'OLLAMA',
    ollama_base_url                 VARCHAR(512) NOT NULL DEFAULT 'http://ollama:11434',
    ollama_model                    VARCHAR(128) NOT NULL DEFAULT 'llama3.2',
    ai_generated_email_auto_send    BOOLEAN NOT NULL DEFAULT FALSE,
    document_confidence_threshold   NUMERIC(4, 3) NOT NULL DEFAULT 0.700,
    invoice_reminder_days           INTEGER[] NOT NULL DEFAULT '{3,7,15,30}',
    data_retention_days             INTEGER NOT NULL DEFAULT 365,
    extra                           JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                      VARCHAR(128),
    updated_by                      VARCHAR(128),
    CONSTRAINT uq_company_settings_company UNIQUE (company_id),
    CONSTRAINT chk_company_settings_retention CHECK (data_retention_days > 0),
    CONSTRAINT chk_company_settings_threshold CHECK (
        document_confidence_threshold >= 0 AND document_confidence_threshold <= 1
    )
);
