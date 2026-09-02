-- Phase 1 — initialisation infrastructure PostgreSQL.
-- Exécuté uniquement au premier démarrage (volume postgres_data vide).
-- Les migrations applicatives Flyway iront dans database/migrations (Phase 3).

CREATE EXTENSION IF NOT EXISTS vector;

CREATE DATABASE n8n;
