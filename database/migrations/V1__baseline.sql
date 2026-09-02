-- Baseline Flyway (Phase 2).
-- Les tables métier sont ajoutées à partir de la Phase 3.
-- Idempotent : l'init Docker active déjà pgvector sur le volume Postgres.

CREATE EXTENSION IF NOT EXISTS vector;
