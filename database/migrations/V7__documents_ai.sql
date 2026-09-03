-- Phase 8 — M03 Document AI (PRD §12, §18, §19, §24, §30).
-- Texte Tika conservé pour relecture ; index checksum pour upload idempotent.

ALTER TABLE document_extractions
    ADD COLUMN extracted_text TEXT;

CREATE INDEX idx_documents_company_checksum
    ON documents (company_id, checksum_sha256)
    WHERE checksum_sha256 IS NOT NULL;

CREATE INDEX idx_documents_company_created ON documents (company_id, created_at DESC);
