-- Phase 6 — qualification IA des leads + scoring configurable (PRD §11, §22, §24).

ALTER TABLE leads
    ADD COLUMN ai_status VARCHAR(32),
    ADD COLUMN confidence_score NUMERIC(4, 3);

ALTER TABLE leads
    ADD CONSTRAINT chk_leads_ai_status CHECK (
        ai_status IS NULL OR ai_status IN (
            'COMPLETED', 'REVIEW_REQUIRED', 'AI_PARSING_ERROR', 'AI_UNAVAILABLE'
        )
    );

ALTER TABLE leads
    ADD CONSTRAINT chk_leads_urgency CHECK (
        urgency IS NULL OR urgency IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')
    );

CREATE INDEX idx_leads_company_created ON leads (company_id, created_at DESC);

ALTER TABLE company_settings
    ADD COLUMN lead_score_low_max INTEGER NOT NULL DEFAULT 30,
    ADD COLUMN lead_score_medium_max INTEGER NOT NULL DEFAULT 60,
    ADD COLUMN lead_score_high_max INTEGER NOT NULL DEFAULT 80,
    ADD COLUMN lead_confidence_threshold NUMERIC(4, 3) NOT NULL DEFAULT 0.700;

ALTER TABLE company_settings
    ADD CONSTRAINT chk_lead_score_bands CHECK (
        lead_score_low_max >= 0
        AND lead_score_medium_max > lead_score_low_max
        AND lead_score_high_max > lead_score_medium_max
        AND lead_score_high_max < 100
    );

ALTER TABLE company_settings
    ADD CONSTRAINT chk_lead_confidence_threshold CHECK (
        lead_confidence_threshold >= 0 AND lead_confidence_threshold <= 1
    );
