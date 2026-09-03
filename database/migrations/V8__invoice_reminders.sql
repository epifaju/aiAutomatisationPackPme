-- Phase 9 — M04 Invoice Reminder (PRD §13, §34).
-- Délais par défaut {3,7,15,30} déjà sur company_settings.invoice_reminder_days.
-- Auto-envoi off par défaut (validation humaine, J+30 toujours manuel).

ALTER TABLE company_settings
    ADD COLUMN invoice_reminder_auto_send BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_invoice_reminders_invoice_level
    ON invoice_reminders (invoice_id, reminder_level);
