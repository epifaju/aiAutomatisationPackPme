-- Phase 7 — M01 Email Assistant (PRD §10, §24, §32).
-- Seuil de confiance IA dédié ; index pour filtres liste.

ALTER TABLE company_settings
    ADD COLUMN email_confidence_threshold NUMERIC(4, 3) NOT NULL DEFAULT 0.700;

ALTER TABLE company_settings
    ADD CONSTRAINT chk_email_confidence_threshold CHECK (
        email_confidence_threshold >= 0 AND email_confidence_threshold <= 1
    );

CREATE INDEX idx_emails_company_status ON emails (company_id, status);
CREATE INDEX idx_email_analysis_company_priority ON email_analysis (company_id, priority);
