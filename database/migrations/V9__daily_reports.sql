-- Phase 10 — M05 Daily Business Report (PRD §14, §18, §26).
-- Un rapport par entreprise et par jour (idempotent). Auto-envoi off par défaut.

ALTER TABLE company_settings
    ADD COLUMN daily_report_auto_send BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE company_settings
    ADD COLUMN daily_report_email VARCHAR(320);

CREATE TABLE daily_reports (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID NOT NULL REFERENCES companies (id),
    report_date     DATE NOT NULL,
    metrics         JSONB NOT NULL DEFAULT '{}'::jsonb,
    summary         TEXT,
    status          VARCHAR(32) NOT NULL DEFAULT 'GENERATED',
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(128),
    updated_by      VARCHAR(128),
    CONSTRAINT uq_daily_reports_company_date UNIQUE (company_id, report_date),
    CONSTRAINT chk_daily_reports_status CHECK (status IN ('GENERATED', 'SENT', 'ERROR'))
);

CREATE INDEX idx_daily_reports_company_date ON daily_reports (company_id, report_date DESC);
