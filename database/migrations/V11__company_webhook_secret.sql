-- P1.1 — secret webhook par entreprise (hash SHA-256 hex, unique).
-- Le secret en clair n’est jamais stocké. Binding initial : WEBHOOK_SECRET → entreprise démo au boot.

ALTER TABLE companies
    ADD COLUMN webhook_secret_hash VARCHAR(64);

CREATE UNIQUE INDEX uq_companies_webhook_secret_hash
    ON companies (webhook_secret_hash)
    WHERE webhook_secret_hash IS NOT NULL;

COMMENT ON COLUMN companies.webhook_secret_hash IS
    'SHA-256 hex du secret X-Webhook-Secret de l’entreprise (P1.1).';
