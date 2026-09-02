-- Données de démonstration (PRD §38). Aucune donnée personnelle réelle.
-- Identifiant admin de démo : demo.admin@aipack.example / DemoAdmin!2026
-- Mot de passe hashé via pgcrypto (bcrypt). Ne pas utiliser en production.

INSERT INTO companies (
    id, name, siret, country, timezone, created_at, updated_at, created_by
) VALUES (
    'aaaaaaaa-0000-4000-8000-000000000001',
    'Demo SAS',
    '12345678900011',
    'FR',
    'Europe/Paris',
    now(), now(),
    'seed'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO users (
    id, company_id, email, password_hash, full_name, role, enabled,
    created_at, updated_at, created_by
) VALUES (
    'bbbbbbbb-0000-4000-8000-000000000001',
    'aaaaaaaa-0000-4000-8000-000000000001',
    'demo.admin@aipack.example',
    crypt('DemoAdmin!2026', gen_salt('bf', 10)),
    'Admin Démo',
    'ADMIN',
    TRUE,
    now(), now(),
    'seed'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO company_settings (
    id, company_id, created_at, updated_at, created_by
) VALUES (
    'aaaaaaaa-0000-4000-8000-000000000002',
    'aaaaaaaa-0000-4000-8000-000000000001',
    now(), now(),
    'seed'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO leads (
    id, company_id, source, status, email, full_name, company_name, phone,
    score, summary, probable_need, urgency, recommended_action,
    created_at, updated_at, created_by
)
SELECT
    ('cccccccc-0000-4000-8000-' || lpad(i::text, 12, '0'))::uuid,
    'aaaaaaaa-0000-4000-8000-000000000001',
    (ARRAY['WEB_FORM', 'WEBHOOK', 'EMAIL', 'CSV', 'API'])[1 + ((i - 1) % 5)],
    (ARRAY['NEW', 'QUALIFIED', 'CONTACTED', 'PROPOSAL', 'WON', 'LOST'])[1 + ((i - 1) % 6)],
    'lead' || lpad(i::text, 2, '0') || '@demo.aipack.example',
    'Prospect Démo ' || lpad(i::text, 2, '0'),
    'Société Démo ' || lpad(i::text, 2, '0'),
    '+33000000' || lpad(i::text, 2, '0'),
    least(100, i * 10),
    'Résumé fictif du besoin n°' || i,
    'Automatisation administrative',
    (ARRAY['LOW', 'NORMAL', 'HIGH'])[1 + ((i - 1) % 3)],
    'Relancer par email',
    now(), now(),
    'seed'
FROM generate_series(1, 10) AS g(i)
ON CONFLICT (id) DO NOTHING;

INSERT INTO lead_events (
    id, company_id, lead_id, event_type, payload, occurred_at, created_at, updated_at, created_by
)
SELECT
    ('ccccccc1-0000-4000-8000-' || lpad(i::text, 12, '0'))::uuid,
    'aaaaaaaa-0000-4000-8000-000000000001',
    ('cccccccc-0000-4000-8000-' || lpad(i::text, 12, '0'))::uuid,
    'CREATED',
    jsonb_build_object('source', 'seed'),
    now(), now(), now(),
    'seed'
FROM generate_series(1, 10) AS g(i)
ON CONFLICT (id) DO NOTHING;

INSERT INTO emails (
    id, company_id, message_id, from_address, to_address, subject, body_text,
    received_at, status, created_at, updated_at, created_by
)
SELECT
    ('eeeeeeee-0000-4000-8000-' || lpad(i::text, 12, '0'))::uuid,
    'aaaaaaaa-0000-4000-8000-000000000001',
    'demo-message-' || lpad(i::text, 2, '0') || '@aipack.example',
    'expediteur' || lpad(i::text, 2, '0') || '@demo.aipack.example',
    'inbox@demo.aipack.example',
    'Sujet démo ' || lpad(i::text, 2, '0'),
    'Corps fictif de l''email n°' || i || '.',
    now() - (i || ' hours')::interval,
    'ANALYZED',
    now(), now(),
    'seed'
FROM generate_series(1, 10) AS g(i)
ON CONFLICT (id) DO NOTHING;

INSERT INTO email_analysis (
    id, company_id, email_id, category, priority, intent, summary, suggested_reply,
    confidence_score, status, approval_status, created_at, updated_at, created_by
)
SELECT
    ('eeeee001-0000-4000-8000-' || lpad(i::text, 12, '0'))::uuid,
    'aaaaaaaa-0000-4000-8000-000000000001',
    ('eeeeeeee-0000-4000-8000-' || lpad(i::text, 12, '0'))::uuid,
    (ARRAY['CLIENT', 'PROSPECT', 'FACTURE', 'FOURNISSEUR', 'SUPPORT', 'ADMINISTRATIF', 'SPAM', 'AUTRE'])[1 + ((i - 1) % 8)],
    (ARRAY['LOW', 'NORMAL', 'HIGH', 'URGENT'])[1 + ((i - 1) % 4)],
    'demande-info',
    'Résumé IA fictif de l''email n°' || i,
    'Proposition de réponse fictive n°' || i,
    0.50 + (i * 0.04),
    CASE WHEN i = 10 THEN 'REVIEW_REQUIRED' ELSE 'COMPLETED' END,
    CASE WHEN i IN (9, 10) THEN 'PENDING_APPROVAL' ELSE NULL END,
    now(), now(),
    'seed'
FROM generate_series(1, 10) AS g(i)
ON CONFLICT (id) DO NOTHING;

INSERT INTO customers (
    id, company_id, name, email, phone, address, created_at, updated_at, created_by
) VALUES
    (
        'dddddddd-0000-4000-8000-000000000001',
        'aaaaaaaa-0000-4000-8000-000000000001',
        'Client Démo Alpha',
        'client.alpha@demo.aipack.example',
        '+33000000001',
        '1 rue de la Démo, 75001 Paris',
        now(), now(), 'seed'
    ),
    (
        'dddddddd-0000-4000-8000-000000000002',
        'aaaaaaaa-0000-4000-8000-000000000001',
        'Client Démo Beta',
        'client.beta@demo.aipack.example',
        '+33000000002',
        '2 avenue Fictive, 69001 Lyon',
        now(), now(), 'seed'
    ),
    (
        'dddddddd-0000-4000-8000-000000000003',
        'aaaaaaaa-0000-4000-8000-000000000001',
        'Client Démo Gamma',
        'client.gamma@demo.aipack.example',
        '+33000000003',
        '3 boulevard Exemple, 33000 Bordeaux',
        now(), now(), 'seed'
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO invoices (
    id, company_id, customer_id, invoice_number, invoice_date, due_date,
    amount_excluding_tax, vat, amount_including_tax, currency, status,
    created_at, updated_at, created_by
)
SELECT
    ('ffff0000-0000-4000-8000-' || lpad(i::text, 12, '0'))::uuid,
    'aaaaaaaa-0000-4000-8000-000000000001',
    ('dddddddd-0000-4000-8000-' || lpad((1 + ((i - 1) % 3))::text, 12, '0'))::uuid,
    'DEMO-2026-' || lpad(i::text, 3, '0'),
    DATE '2026-08-01' + (i || ' days')::interval,
    DATE '2026-08-15' + (i || ' days')::interval,
    100.00 * i,
    20.00 * i,
    120.00 * i,
    'EUR',
    (ARRAY['DRAFT', 'SENT', 'PAID', 'OVERDUE', 'CANCELLED'])[1 + ((i - 1) % 5)],
    now(), now(),
    'seed'
FROM generate_series(1, 10) AS g(i)
ON CONFLICT (id) DO NOTHING;

INSERT INTO invoice_reminders (
    id, company_id, invoice_id, reminder_level, status, scheduled_at,
    created_at, updated_at, created_by
)
SELECT
    ('ffff1111-0000-4000-8000-' || lpad(i::text, 12, '0'))::uuid,
    'aaaaaaaa-0000-4000-8000-000000000001',
    ('ffff0000-0000-4000-8000-' || lpad((i * 4)::text, 12, '0'))::uuid,
    7,
    'PENDING_APPROVAL',
    now(),
    now(), now(),
    'seed'
FROM generate_series(1, 2) AS g(i)
ON CONFLICT (id) DO NOTHING;

INSERT INTO documents (
    id, company_id, original_filename, content_type, storage_key, size_bytes,
    document_type, status, checksum_sha256, created_at, updated_at, created_by
)
SELECT
    ('abcde000-0000-4000-8000-' || lpad(i::text, 12, '0'))::uuid,
    'aaaaaaaa-0000-4000-8000-000000000001',
    'document-demo-' || lpad(i::text, 2, '0') || '.pdf',
    'application/pdf',
    'demo/aaaaaaaa-0000-4000-8000-000000000001/documents/doc-' || lpad(i::text, 2, '0') || '.pdf',
    1024 * i,
    (ARRAY['FACTURE', 'DEVIS', 'BON_COMMANDE', 'CONTRAT', 'COURRIER'])[i],
    CASE WHEN i = 5 THEN 'REVIEW_REQUIRED' ELSE 'EXTRACTED' END,
    repeat('a', 64),
    now(), now(),
    'seed'
FROM generate_series(1, 5) AS g(i)
ON CONFLICT (id) DO NOTHING;

INSERT INTO document_extractions (
    id, company_id, document_id, extracted_json, confidence_score, status,
    created_at, updated_at, created_by
)
SELECT
    ('abcde001-0000-4000-8000-' || lpad(i::text, 12, '0'))::uuid,
    'aaaaaaaa-0000-4000-8000-000000000001',
    ('abcde000-0000-4000-8000-' || lpad(i::text, 12, '0'))::uuid,
    jsonb_build_object(
        'supplier', 'Fournisseur Démo',
        'invoiceNumber', 'DEMO-DOC-' || i,
        'currency', 'EUR'
    ),
    CASE WHEN i = 5 THEN 0.40 ELSE 0.92 END,
    CASE WHEN i = 5 THEN 'REVIEW_REQUIRED' ELSE 'COMPLETED' END,
    now(), now(),
    'seed'
FROM generate_series(1, 5) AS g(i)
ON CONFLICT (id) DO NOTHING;

INSERT INTO audit_logs (
    id, company_id, workflow, action, entity_type, entity_id, status, metadata,
    created_at, updated_at, created_by
)
SELECT
    ('a0d17000-0000-4000-8000-' || lpad(i::text, 12, '0'))::uuid,
    'aaaaaaaa-0000-4000-8000-000000000001',
    (ARRAY['email-ingestion', 'lead-capture', 'document-ingestion', 'invoice-reminder'])[1 + ((i - 1) % 4)],
    (ARRAY['CREATED', 'ANALYZED', 'EMAIL_SENT', 'EXTRACTED'])[1 + ((i - 1) % 4)],
    (ARRAY['EMAIL', 'LEAD', 'DOCUMENT', 'INVOICE'])[1 + ((i - 1) % 4)],
    i::text,
    CASE WHEN i % 7 = 0 THEN 'ERROR' ELSE 'SUCCESS' END,
    jsonb_build_object('demo', true, 'index', i),
    now() - (i || ' minutes')::interval,
    now(),
    'seed'
FROM generate_series(1, 20) AS g(i)
ON CONFLICT (id) DO NOTHING;

INSERT INTO workflow_runs (
    id, company_id, workflow, execution_id, trigger, status, error_type, error_message,
    started_at, finished_at, created_at, updated_at, created_by
)
VALUES
    (
        'a0f10000-0000-4000-8000-000000000001',
        'aaaaaaaa-0000-4000-8000-000000000001',
        'WF001_email_ingestion',
        'exec-demo-1',
        'webhook',
        'SUCCESS',
        NULL,
        NULL,
        now() - interval '1 hour',
        now() - interval '59 minutes',
        now(), now(),
        'seed'
    ),
    (
        'a0f10000-0000-4000-8000-000000000002',
        'aaaaaaaa-0000-4000-8000-000000000001',
        'WF091_error_handler',
        'exec-demo-2',
        'error',
        'ERROR',
        'TIMEOUT',
        'Échec fictif de démonstration (aucun secret)',
        now() - interval '30 minutes',
        now() - interval '29 minutes',
        now(), now(),
        'seed'
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO ai_requests (
    id, company_id, provider, model, purpose, prompt_hash, status, latency_ms, cache_hit,
    created_at, updated_at, created_by
) VALUES (
    'a1a10000-0000-4000-8000-000000000001',
    'aaaaaaaa-0000-4000-8000-000000000001',
    'OLLAMA',
    'llama3.2',
    'email-classification',
    repeat('b', 64),
    'SUCCESS',
    120,
    FALSE,
    now(), now(),
    'seed'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO notifications (
    id, company_id, user_id, channel, title, body, sent_at, created_at, updated_at, created_by
) VALUES (
    'a1010000-0000-4000-8000-000000000001',
    'aaaaaaaa-0000-4000-8000-000000000001',
    'bbbbbbbb-0000-4000-8000-000000000001',
    'IN_APP',
    'Bienvenue sur le pack démo',
    'Jeu de données fictif chargé (Phase 3).',
    now(),
    now(), now(),
    'seed'
) ON CONFLICT (id) DO NOTHING;
