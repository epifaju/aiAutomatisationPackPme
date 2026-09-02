# PRD — AI Automation Pack for TPE/PME

**Version :** 1.1 (optimisée)  
**Statut :** MVP  
**Cible technique :** Cursor AI  
**Type de produit :** Pack d’automatisation IA self-hosted pour TPE/PME  
**Déploiement :** Docker Compose  
**Marché initial :** TPE/PME françaises

---

# 0. Note de version — Optimisations apportées

Ce document est une version enrichie du PRD original, retravaillée pour un développement guidé par Cursor AI dans les règles de l'art (production-grade, maintenable, testable). Le contenu original est conservé intégralement ; les ajouts sont signalés par le tag **🆕**.

Principaux ajouts, avec justification :

```text
🆕 MinIO (stockage objet)       → obligatoire dès le MVP, pas en V2
   Raison : M03 Document AI et les pièces jointes email n'ont
   aujourd'hui AUCUN emplacement de stockage défini dans le PRD
   d'origine. Stocker des fichiers en base PostgreSQL (bytea) est
   une anti-pratique reconnue. MinIO est self-hosted, compatible
   S3, et cohérent avec l'esprit "self-hosted par défaut" du produit.

🆕 Redis                        → recommandé dès le MVP (pas obligatoire)
   Raison : cache des réponses IA (économie de coûts/latence sur
   Ollama ou API externe), rate-limiting, et file d'attente légère
   pour les traitements asynchrones (extraction documents, emails).

🆕 Lombok + MapStruct           → backend
   Raison : réduit le boilerplate Java (getters/setters, mapping
   Entity ↔ DTO), standard dans l'écosystème Spring Boot moderne.

🆕 Resilience4j                 → AI Gateway
   Raison : les appels IA (Ollama local ou API externe) peuvent
   timeout ou échouer. Circuit breaker + retry + fallback évitent
   qu'une panne IA bloque tout le pipeline (email, leads, documents).

🆕 Bucket4j                     → rate limiting API/webhooks
   Raison : les webhooks n8n exposés publiquement (§19) doivent être
   protégés contre les abus, en complément de l'authentification.

🆕 Apache Tika                  → M03 Document AI
   Raison : extraction fiable de texte/métadonnées depuis PDF,
   images (OCR via Tesseract), avant passage au LLM — réduit les
   erreurs d'extraction IA sur des documents mal structurés.

🆕 pgvector (extension PostgreSQL) → préparation RAG documentaire
   Raison : la RAG documentaire est déjà prévue en V2 (§43). Activer
   l'extension dès le MVP (sans l'utiliser) évite une migration
   lourde plus tard — coût d'activation quasi nul.

🆕 TanStack Query v5, Zustand, React Hook Form + Zod, shadcn/ui → frontend
   Raison : stack cohérente avec vos autres projets, typée de bout
   en bout (Zod = validation runtime + types), state management léger
   (Zustand) sans la lourdeur de Redux.

🆕 Testcontainers                → tests backend/intégration
   Raison : teste contre un vrai PostgreSQL (via Docker) plutôt que
   H2, évite les faux positifs liés aux différences SQL.

🆕 Vitest + Testing Library + Playwright → frontend
   Raison : Vitest s'intègre nativement à Vite (déjà dans le stack),
   Playwright couvre les scénarios E2E (§40) sur de vrais navigateurs.

🆕 GitHub Actions (CI/CD)        → nouvelle section
   Raison : "installation reproductible" (§4) implique une build
   validée automatiquement à chaque commit, pas seulement en local.

🆕 Logging structuré JSON (Logback)   → observabilité
   Raison : les audit_logs et workflow_runs (§33) sont exploitables
   par des outils externes (Grafana Loki, etc.) seulement si le
   format est structuré dès le départ.

🆕 Traefik ou Caddy (reverse proxy)   → reste optionnel V2, mais
   Dockerfile/config esquissée dès le MVP
   Raison : simplifie TLS local et le routing multi-services sans
   complexité ajoutée au MVP (documenté, non activé par défaut).
```

Aucun ajout ne modifie la stack imposée en §5 (Spring Boot / React) ni les principes fondamentaux du §4 — ils les renforcent uniquement.

---

# 1. Vision du produit

Construire un pack d’automatisation IA installable simplement sur un PC, serveur ou VPS permettant aux TPE/PME d’automatiser des tâches administratives répétitives sans avoir besoin de développer leurs propres workflows.

Le produit doit combiner :

- automatisation n8n ;
- IA générative ;
- traitement des emails ;
- gestion des prospects ;
- extraction de données depuis des documents ;
- suivi des factures ;
- relances automatiques ;
- rapports d’activité ;
- journalisation complète des actions ;
- tableau de bord Web ;
- stockage PostgreSQL ;
- fonctionnement avec IA locale ou API externe.

Le produit doit pouvoir être installé avec une commande principale :

```bash
docker compose up -d
```

L’objectif du MVP n’est pas de devenir immédiatement un ERP ou CRM complet.

Le produit doit rester modulaire, simple à installer et facilement personnalisable.

---

# 2. Proposition de valeur

## Problème

Les petites entreprises réalisent quotidiennement de nombreuses tâches répétitives :

- lecture d’emails ;
- tri d’emails ;
- réponses répétitives ;
- saisie de données ;
- traitement de documents ;
- suivi de prospects ;
- relance de clients ;
- contrôle des factures ;
- préparation de rapports ;
- recherche d’informations.

Ces tâches prennent du temps mais ne justifient généralement pas le développement d’un logiciel spécifique.

## Solution

Fournir une infrastructure d’automatisation prête à l’emploi contenant plusieurs workflows configurables.

Le client installe le produit, configure ses paramètres puis active les modules dont il a besoin.

---

# 3. Utilisateurs cibles

## Persona A — Dirigeant de TPE

Entreprise de 1 à 10 personnes.

Besoins :

- gagner du temps ;
- suivre les prospects ;
- réduire les tâches administratives ;
- réduire les factures impayées.

Compétence informatique faible à moyenne.

---

## Persona B — PME

Entreprise de 10 à 100 salariés.

Besoins :

- automatiser les processus internes ;
- centraliser les automatisations ;
- garder une trace des actions ;
- connecter plusieurs outils.

---

## Persona C — Agence / consultant informatique

Souhaite installer et personnaliser le produit chez ses propres clients.

Besoins :

- déploiement rapide ;
- architecture documentée ;
- personnalisation ;
- workflows réutilisables.

---

# 4. Principes fondamentaux

Le produit doit respecter les principes suivants :

1. Self-hosted par défaut.
2. Architecture Docker.
3. Configuration par variables d’environnement.
4. Workflows n8n indépendants.
5. PostgreSQL comme stockage principal.
6. IA locale disponible.
7. IA cloud optionnelle.
8. Audit de toutes les automatisations.
9. Human-in-the-loop pour les actions sensibles.
10. Installation reproductible.
11. Aucun secret dans le code source.
12. Possibilité d’ajouter facilement de nouveaux modules.

---

# 5. Stack technique

## Orchestration

Docker Compose.

## Automatisation

n8n.

## Base de données

PostgreSQL.

## IA locale

Ollama.

Le système doit prévoir une abstraction permettant d’utiliser ultérieurement d’autres fournisseurs.

## Backend

Préférence :

```text
Spring Boot
Java 21+
Maven
Spring Data JPA
Spring Security
Flyway
OpenAPI
Lombok            🆕 réduction boilerplate
MapStruct         🆕 mapping Entity ↔ DTO
Resilience4j      🆕 circuit breaker / retry pour l'AI Gateway
Bucket4j          🆕 rate limiting API & webhooks
Apache Tika       🆕 extraction texte/OCR pour Document AI
```

Alternative acceptable uniquement si explicitement décidée :

```text
FastAPI
Python
SQLAlchemy
Alembic
```

Cursor ne doit PAS changer de stack sans instruction explicite.

## Frontend

```text
React
TypeScript
Vite
Tailwind CSS
React Query (TanStack Query v5)
React Router
Zustand            🆕 état global léger (session, préférences UI)
React Hook Form     🆕 gestion de formulaires (leads, factures, settings)
Zod                 🆕 validation runtime + inférence de types partagée
shadcn/ui           🆕 composants accessibles, cohérents avec Tailwind
```

## Tests emails

Mailpit dans l’environnement de développement.

## 🆕 Stockage objet et cache

```text
MinIO   → stockage des fichiers uploadés (factures, devis, contrats, PJ email)
Redis   → cache réponses IA, rate limiting, files légères (recommandé, non bloquant)
```

MinIO passe en service **obligatoire du MVP** (voir §7) : le PRD d'origine ne définissait
aucun emplacement de stockage pour les fichiers de M03 (Document AI), alors que les tables
`documents` / `document_extractions` (§16) supposent des fichiers persistés quelque part.
Stocker des binaires directement en PostgreSQL n'est pas recommandé au-delà de quelques Mo.

---

# 5bis. 🆕 Patterns architecturaux imposés (backend)

Pour garder une base de code cohérente et facilement automatisable par Cursor, tout le
code backend doit suivre ces conventions dès la Phase 2 :

```text
ApiResponse<T>       enveloppe uniforme de toutes les réponses REST
                      { success, data, error, timestamp }

PageResponse<T>       enveloppe des réponses paginées
                      { content, page, size, totalElements, totalPages }

BaseEntity            classe mère JPA : id (UUID), createdAt, updatedAt,
                       createdBy, updatedBy — héritée par toutes les entités

Flyway                migrations versionnées, nommage V{n}__description.sql,
                       jamais de modification d'une migration déjà appliquée

JWT + refresh          access token court (15 min), refresh token rotatif,
                       stocké hashé en base, invalidation à la déconnexion

MapStruct              tout mapping Entity → DTO passe par un mapper dédié,
                       jamais de mapping manuel dispersé dans les services
```

Règle de non-régression : toute nouvelle fonctionnalité s'ajoute dans de nouveaux
fichiers plutôt que par modification extensive de fichiers existants, sauf
correction de bug justifiée.

---

# 6. Architecture

```text
                         USER
                           │
                           ▼
                  ┌─────────────────┐
                  │ React Dashboard │
                  └────────┬────────┘
                           │ REST
                           ▼
                  ┌─────────────────┐
                  │   Backend API   │
                  └───────┬─────────┘
                          │
               ┌──────────┼─────────────┬────────────┐
               │          │             │            │
               ▼          ▼             ▼            ▼
          PostgreSQL     n8n          Ollama      MinIO 🆕
                           │                    (fichiers)
             ┌─────────────┼───────────────┐
             ▼             ▼               ▼
           Email       Documents        External
                                         APIs

        Redis 🆕 (cache IA / rate limit, recommandé) ── transversal
```

---

# 7. Services Docker

Le fichier `docker-compose.yml` doit contenir au minimum :

```text
postgres
n8n
backend
frontend
ollama
mailpit
minio        🆕 obligatoire — stockage des documents/factures/PJ
```

Fortement recommandé dès le MVP (non bloquant, désactivable) :

```text
redis        🆕 cache IA + rate limiting
```

Prévoir ultérieurement :

```text
reverse-proxy   (Traefik ou Caddy — config esquissée mais non activée)
worker          (extraction de la queue Ollama du process de requête HTTP)
```

Redis ne doit pas être obligatoire pour le démarrage minimal (`docker compose up -d`
doit fonctionner sans Redis configuré), mais le service doit être présent dans le
`docker-compose.yml` par défaut car le cache IA réduit significativement les coûts
et la latence dès les premiers usages réels.

---

# 8. Structure du repository

Cursor doit respecter l’arborescence suivante :

```text
ai-automation-pack/
│
├── README.md
├── LICENSE
├── docker-compose.yml
├── docker-compose.dev.yml
├── .env.example
├── .gitignore
│
├── .github/                    🆕
│   └── workflows/
│       ├── backend-ci.yml
│       ├── frontend-ci.yml
│       └── e2e.yml
│
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│
├── frontend/
│   ├── package.json
│   ├── Dockerfile
│   └── src/
│
├── database/
│   ├── migrations/
│   └── seed/
│
├── n8n/
│   ├── workflows/
│   ├── credentials/
│   └── README.md
│
├── ollama/
│   ├── models/
│   └── prompts/
│
├── scripts/
│   ├── install.sh
│   ├── install.ps1
│   ├── backup.sh
│   ├── restore.sh
│   └── healthcheck.sh
│
├── docs/
│   ├── installation.md
│   ├── configuration.md
│   ├── architecture.md
│   ├── security.md
│   ├── troubleshooting.md
│   └── workflows.md
│
└── tests/
    ├── integration/
    └── e2e/
```

---

# 9. Modules du MVP

Le MVP comprend 6 modules.

```text
M01 Email Assistant
M02 Lead Management
M03 Document AI
M04 Invoice Reminder
M05 Daily Business Report
M06 Audit & Monitoring
```

---

# 10. M01 — Assistant Email

## Objectif

Analyser automatiquement les emails entrants.

## Workflow

```text
Email reçu
   ↓
Extraction
   ↓
Classification IA
   ↓
Détection priorité
   ↓
Détection intention
   ↓
Génération résumé
   ↓
Suggestion réponse
   ↓
Validation humaine
   ↓
Envoi éventuel
   ↓
Audit
```

## Catégories

Minimum :

```text
CLIENT
PROSPECT
FACTURE
FOURNISSEUR
SUPPORT
ADMINISTRATIF
SPAM
AUTRE
```

## Priorités

```text
LOW
NORMAL
HIGH
URGENT
```

## Règle de sécurité

Le MVP ne doit PAS envoyer automatiquement une réponse générée par IA sans activation explicite.

Par défaut :

```text
AI_GENERATED_EMAIL_AUTO_SEND=false
```

---

# 11. M02 — Gestion des prospects

## Sources

Leads provenant de :

- formulaire Web ;
- webhook ;
- email ;
- import CSV ;
- API.

## Pipeline

```text
NEW
QUALIFIED
CONTACTED
PROPOSAL
WON
LOST
```

## IA

L’IA peut produire :

```text
score
résumé
besoin probable
urgence
budget potentiel
action recommandée
```

## Lead scoring

Score :

```text
0 → 100
```

Exemple :

```text
0-30 = faible
31-60 = moyen
61-80 = intéressant
81-100 = prioritaire
```

Le scoring doit être configurable.

---

# 12. M03 — Document AI

## Objectif

Extraire automatiquement les informations contenues dans des documents.

## Formats MVP

```text
PDF
PNG
JPEG
TXT
```

DOCX pourra être ajouté ultérieurement.

## Types de documents

```text
FACTURE
DEVIS
BON_COMMANDE
CONTRAT
COURRIER
AUTRE
```

## Exemple facture

Extraire :

```json
{
  "supplier": "",
  "invoiceNumber": "",
  "invoiceDate": "",
  "dueDate": "",
  "amountExcludingTax": 0,
  "vat": 0,
  "amountIncludingTax": 0,
  "currency": "EUR"
}
```

## Confiance

Chaque extraction IA doit contenir :

```text
confidence_score
```

Si :

```text
confidence_score < threshold
```

le document doit être marqué :

```text
REVIEW_REQUIRED
```

---

# 13. M04 — Relance factures

## Statuts

```text
DRAFT
SENT
PAID
OVERDUE
CANCELLED
```

## Workflow

```text
Scheduler
   ↓
Chercher factures impayées
   ↓
Calcul retard
   ↓
Choisir scénario
   ↓
Créer relance
   ↓
Validation éventuelle
   ↓
Email
   ↓
Journaliser
```

## Scénarios par défaut

```text
J+3  → rappel cordial
J+7  → deuxième rappel
J+15 → rappel ferme
J+30 → alerte manuelle
```

Les délais doivent être configurables.

## Protection

Aucune relance ne doit être envoyée si :

```text
invoice.status = PAID
```

---

# 14. M05 — Rapport quotidien

Chaque jour, le système peut générer un rapport contenant :

```text
emails reçus
emails urgents
nouveaux leads
leads prioritaires
documents traités
documents en erreur
factures en retard
relances envoyées
erreurs d’automatisation
```

Le rapport doit pouvoir être envoyé :

```text
EMAIL
```

Prévoir ultérieurement :

```text
Slack
Teams
Telegram
```

---

# 15. M06 — Audit

Toutes les automatisations importantes doivent créer une entrée d’audit.

Exemple :

```json
{
  "workflow": "invoice-reminder",
  "action": "EMAIL_SENT",
  "entityType": "INVOICE",
  "entityId": "123",
  "status": "SUCCESS",
  "timestamp": "...",
  "metadata": {}
}
```

Ne jamais enregistrer :

```text
password
API key
access token
refresh token
secret
```

---

# 16. Modèle PostgreSQL

Tables principales :

```text
users
companies
company_settings

emails
email_analysis

leads
lead_events

documents
document_extractions

customers
invoices
invoice_reminders

workflow_runs
audit_logs

ai_requests
notifications
```

---

# 17. Multi-tenant readiness

Même si la V1 est principalement self-hosted mono-entreprise, le modèle doit permettre une évolution SaaS.

Les tables métier doivent donc contenir lorsque pertinent :

```text
company_id
```

Exemple :

```text
companies
   │
   ├── users
   ├── leads
   ├── customers
   ├── invoices
   ├── documents
   └── emails
```

Ne PAS implémenter une architecture multi-tenant complexe dans le MVP.

Préparer simplement le modèle.

---

# 18. API REST

Base :

```text
/api/v1
```

## Authentication

```text
POST /api/v1/auth/login
POST /api/v1/auth/refresh
GET  /api/v1/auth/me
```

## Dashboard

```text
GET /api/v1/dashboard/summary
```

## Leads

```text
GET    /api/v1/leads
GET    /api/v1/leads/{id}
POST   /api/v1/leads
PUT    /api/v1/leads/{id}
DELETE /api/v1/leads/{id}
```

## Documents

```text
GET  /api/v1/documents
POST /api/v1/documents
GET  /api/v1/documents/{id}
POST /api/v1/documents/{id}/process
```

## Invoices

```text
GET  /api/v1/invoices
POST /api/v1/invoices
GET  /api/v1/invoices/{id}
PUT  /api/v1/invoices/{id}
```

## Emails

```text
GET /api/v1/emails
GET /api/v1/emails/{id}
```

## Workflows

```text
GET  /api/v1/workflows
POST /api/v1/workflows/{id}/execute
```

## Audit

```text
GET /api/v1/audit
```

---

# 19. Webhooks n8n

Les appels internes doivent utiliser des routes clairement identifiables.

Exemples :

```text
/webhook/email/incoming
/webhook/leads/create
/webhook/documents/process
/webhook/invoices/reminder
```

Les webhooks exposés publiquement doivent être authentifiés lorsque nécessaire.

---

# 20. Workflows n8n

Créer au minimum :

```text
WF001_email_ingestion
WF002_email_ai_analysis
WF003_email_reply_generation

WF010_lead_capture
WF011_lead_ai_qualification

WF020_document_ingestion
WF021_document_ai_extraction

WF030_invoice_overdue_detection
WF031_invoice_reminder

WF040_daily_report

WF090_audit_logger
WF091_error_handler
```

Chaque workflow doit avoir :

```text
description
version
trigger
inputs
outputs
error handling
logging
```

---

# 21. Convention n8n

Nommage :

```text
[AIPACK][EMAIL] Analyse Email
[AIPACK][LEAD] Qualification
[AIPACK][DOC] Extraction
[AIPACK][INVOICE] Reminder
[AIPACK][REPORT] Daily Report
```

Les workflows ne doivent PAS contenir de credentials codés en dur.

---

# 22. AI Gateway

Ne pas disperser les appels IA directement partout dans le code.

Créer une abstraction :

```text
AIProvider
```

Interface conceptuelle :

```java
interface AIProvider {

    AIResponse generate(AIRequest request);

}
```

Implémentations :

```text
OllamaAIProvider
```

Préparer l’extension :

```text
OpenAIProvider
AnthropicProvider
MistralProvider
```

sans obligation de les implémenter dans la première itération.

## 🆕 Résilience des appels IA

L'`AIProvider` doit être enveloppé par Resilience4j :

```text
timeout          → échec rapide plutôt que blocage du workflow
retry            → 2 tentatives max, backoff exponentiel
circuit breaker  → bascule en erreur contrôlée si le provider est indisponible
fallback         → status = AI_UNAVAILABLE, action placée en file de retraitement
```

Objectif : une panne d'Ollama ou d'une API IA externe ne doit jamais faire échouer
silencieusement un workflow n8n complet (email, lead, document, relance).

## 🆕 Cache des réponses IA

Pour les requêtes IA déterministes ou répétitives (ex. classification email similaire),
mettre en cache la réponse dans Redis avec une clé basée sur un hash du prompt + du
contexte, TTL configurable. Réduit coûts et latence sans changer le comportement métier.

---

# 23. Prompts IA

Les prompts doivent être stockés séparément du code.

Exemple :

```text
ollama/prompts/
```

Contenu :

```text
email-classification.txt
email-response.txt
lead-qualification.txt
document-extraction.txt
daily-report.txt
```

Les prompts doivent demander autant que possible des réponses JSON structurées.

---

# 24. Robustesse IA

Ne jamais considérer directement une réponse LLM comme fiable.

Pipeline :

```text
LLM
 ↓
JSON parsing
 ↓
Schema validation
 ↓
Business validation
 ↓
Persist
```

Si parsing impossible :

```text
status = AI_PARSING_ERROR
```

Si confiance insuffisante :

```text
status = REVIEW_REQUIRED
```

---

# 25. Interface Web

Navigation :

```text
Dashboard

Inbox
Leads
Documents
Invoices
Automations
Audit

Settings
```

---

# 26. Dashboard

Afficher au minimum :

```text
Emails aujourd’hui
Emails urgents
Nouveaux prospects
Prospects prioritaires
Documents traités
Factures en retard
Montant total en retard
Automatisations exécutées
Automatisations en erreur
```

Ajouter une section :

```text
Recent Activity
```

---

# 27. Page Automations

Afficher chaque automatisation sous forme de carte.

Exemple :

```text
Assistant Email

Status: ENABLED

Executions today: 37
Errors: 1

[Disable]
[Run]
[History]
```

---

# 28. Settings

Paramètres :

```text
Company
Email
AI
Invoice reminders
Notifications
Security
```

Configuration IA :

```text
Provider: Ollama

URL:
http://ollama:11434

Model:
configurable
```

---

# 29. Variables d’environnement

Créer `.env.example`.

Minimum :

```text
POSTGRES_DB=
POSTGRES_USER=
POSTGRES_PASSWORD=

N8N_HOST=
N8N_PORT=
N8N_ENCRYPTION_KEY=

BACKEND_PORT=
FRONTEND_PORT=

JWT_SECRET=

OLLAMA_BASE_URL=
OLLAMA_MODEL=

SMTP_HOST=
SMTP_PORT=
SMTP_USER=
SMTP_PASSWORD=

MINIO_ENDPOINT=            🆕
MINIO_ACCESS_KEY=          🆕
MINIO_SECRET_KEY=          🆕
MINIO_BUCKET=              🆕

REDIS_HOST=                🆕 (optionnel — vide = cache désactivé)
REDIS_PORT=                🆕

APP_BASE_URL=
APP_ENV=
```

Aucune valeur sensible réelle ne doit être commitée.

---

# 30. Sécurité

Minimum obligatoire :

- mots de passe hashés ;
- JWT ;
- contrôle d’accès backend ;
- validation des entrées ;
- limitation de taille des uploads ;
- validation MIME ;
- protection des webhooks ;
- gestion CORS ;
- secrets via environnement ;
- journalisation ;
- aucune clé API dans frontend ;
- aucune clé API dans workflow exporté ;
- 🆕 rate limiting sur les endpoints publics et les webhooks (Bucket4j) ;
- 🆕 validation MIME **par analyse du contenu réel** du fichier (magic bytes),
  pas uniquement l'extension ou le `Content-Type` déclaré par le client ;
- 🆕 en-têtes de sécurité HTTP (CSP, X-Content-Type-Options, HSTS en prod) ;
- 🆕 scan antivirus des documents uploadés recommandé en V2 si le volume le justifie
  (ClamAV), non bloquant pour le MVP.

---

# 31. RGPD

Prévoir :

- suppression des données ;
- export utilisateur ;
- durée de conservation configurable ;
- logs limités aux données nécessaires ;
- possibilité d’utiliser uniquement l’IA locale ;
- documentation des traitements.

Ne pas présenter le produit comme « certifié RGPD ».

---

# 32. Human-in-the-loop

Toute action présentant un impact externe important doit pouvoir nécessiter validation.

Exemples :

```text
envoyer email
relancer facture
modifier statut critique
supprimer données
```

Modèle :

```text
PENDING_APPROVAL
APPROVED
REJECTED
EXECUTED
```

---

# 33. Gestion des erreurs

Chaque workflow n8n doit utiliser une stratégie d’erreur.

Une erreur doit générer :

```text
workflow_run
audit_log
```

Informations :

```text
workflow
execution
error_type
error_message
timestamp
```

Les secrets doivent être masqués.

---

# 34. Idempotence

Les automatisations critiques doivent être idempotentes.

Exemple :

Une facture ne doit pas recevoir deux relances J+7 parce qu’un workflow a été exécuté deux fois.

Créer une clé logique telle que :

```text
invoice_id + reminder_level
```

avec contrainte d’unicité appropriée.

---

# 35. Observabilité

Créer :

```text
GET /actuator/health
```

et des healthchecks Docker.

Vérifier :

```text
PostgreSQL
Backend
n8n
Ollama
MinIO 🆕
```

## 🆕 Logging structuré

Les logs applicatifs (backend) doivent être émis en JSON (Logback + `logstash-logback-encoder`
ou équivalent), avec au minimum : `timestamp`, `level`, `logger`, `message`, `traceId`,
`companyId` (si applicable). Facilite l'ingestion ultérieure par un outil externe
(Grafana Loki, ELK) sans nécessiter de refonte du logging.

---

# 36. Sauvegarde

Créer :

```text
scripts/backup.sh
scripts/restore.sh
```

Sauvegarder au minimum :

```text
PostgreSQL
n8n data
configuration persistante
```

Les secrets ne doivent pas être exportés en clair dans des archives non protégées.

---

# 37. Mailpit

En développement :

```text
SMTP → Mailpit
```

Tous les tests d’envoi doivent être réalisables sans envoyer de vrais emails.

---

# 38. Seed data

Créer des données de démonstration :

```text
1 entreprise
1 admin
10 leads
10 emails
10 factures
5 documents
20 audit logs
```

Ne pas utiliser de vraies données personnelles.

---

# 39. Tests backend

Minimum :

```text
unit tests
repository tests
service tests
controller tests
security tests
```

🆕 Les tests `repository`/`service` impliquant PostgreSQL doivent utiliser **Testcontainers**
plutôt qu'une base H2 en mémoire, afin de tester contre le même moteur SQL que la production
(types PostgreSQL spécifiques, contraintes, JSONB, etc.).

## 🆕 Tests frontend

```text
Vitest + React Testing Library   → tests unitaires composants/hooks
Playwright                        → tests E2E (voir §40), navigateurs réels
```

---

# 40. Tests d’intégration

Scénarios obligatoires :

### Scenario A

```text
Webhook lead
→ création lead
→ qualification IA
→ stockage
→ audit
```

### Scenario B

```text
Email
→ analyse
→ classification
→ proposition réponse
→ validation
```

### Scenario C

```text
Facture dépassée
→ détection
→ génération relance
→ Mailpit
→ audit
```

### Scenario D

```text
Document
→ extraction
→ validation
→ stockage
```

---

# 40bis. 🆕 Intégration continue (CI)

Cursor doit créer, en Phase 13 (Packaging) ou dès que le backend/frontend compilent,
des workflows GitHub Actions minimaux :

```text
.github/workflows/backend-ci.yml
   → mvn verify (build + tests unitaires + tests Testcontainers)

.github/workflows/frontend-ci.yml
   → npm ci, lint, build, vitest

.github/workflows/e2e.yml
   → docker compose up -d, attente healthchecks, playwright test
```

Ces workflows ne déploient rien : ils valident uniquement que
`main` reste dans un état buildable et testé, conformément au principe
d'« installation reproductible » (§4).

---

# 41. Critères d’acceptation MVP

Le MVP est considéré fonctionnel lorsque :

1. `docker compose up -d` démarre les services.
2. PostgreSQL est initialisé automatiquement.
3. Le backend répond.
4. Le frontend est accessible.
5. n8n est accessible.
6. Ollama est accessible.
7. Mailpit fonctionne.
8. 🆕 MinIO est accessible et un fichier peut y être stocké.
9. Un lead peut être créé.
10. Un lead peut être qualifié.
11. Un email peut être analysé.
12. Un document peut être traité.
13. Une facture peut être créée.
14. Une facture dépassée peut générer une relance.
15. Mailpit reçoit la relance.
16. Un rapport quotidien peut être généré.
17. Les actions apparaissent dans l’audit.
18. Une erreur de workflow est enregistrée.
19. Aucun secret n’est présent dans Git.

---

# 42. Non-objectifs MVP

Ne PAS développer immédiatement :

```text
ERP
CRM complet
comptabilité complète
application mobile
marketplace
multi-tenant SaaS complet
Kubernetes
microservices complexes
SSO entreprise
facturation Stripe
application native
```

---

# 43. Évolution V2

Après validation commerciale :

```text
SaaS multi-tenant
Stripe
RBAC avancé
SSO
Microsoft 365
Google Workspace
Slack
Teams
WhatsApp
CRM connectors
accounting connectors
object storage
RAG documentaire
mobile/PWA
marketplace workflows
```

---

# 44. Stratégie commerciale prévue

Le code doit permettre plusieurs éditions.

### Starter

```text
Email
Leads
Basic automation
```

### Professional

```text
Starter
+
Documents
Invoices
Reports
Local AI
```

### Business

```text
Professional
+
advanced automation
advanced audit
multiple users
premium integrations
```

Les limitations commerciales ne doivent pas être codées prématurément dans le MVP.

---

# 45. Instructions impératives pour Cursor

Cursor doit travailler par petites itérations.

NE PAS générer toute l’application en une seule opération.

Avant chaque phase :

1. analyser le PRD ;
2. analyser le code existant ;
3. proposer les fichiers concernés ;
4. implémenter uniquement la phase demandée ;
5. compiler ;
6. exécuter les tests ;
7. corriger les erreurs ;
8. mettre à jour la documentation.

Ne jamais :

- supprimer une fonctionnalité existante pour résoudre une erreur sans justification ;
- modifier la stack technique sans autorisation ;
- stocker un secret dans Git ;
- inventer des API externes ;
- ignorer une erreur de compilation ;
- masquer un test défaillant ;
- désactiver un contrôle de sécurité pour faire fonctionner un test.

---

# 46. Plan de développement Cursor

## Phase 0 — Initialisation

Créer :

```text
repository
directories
README
.env.example
.gitignore
docker-compose
```

Validation :

```bash
docker compose config
```

---

## Phase 1 — Infrastructure

Implémenter :

```text
PostgreSQL
n8n
Mailpit
Ollama
MinIO      🆕
Redis      🆕 (optionnel, non bloquant)
```

Tester chaque service.

---

## Phase 2 — Backend

Créer Spring Boot.

Ajouter :

```text
JPA
Flyway
Security
Validation
Actuator
OpenAPI
```

---

## Phase 3 — Database

Créer les migrations.

Puis seed de démonstration.

---

## Phase 4 — Authentication

Implémenter :

```text
login
JWT
refresh
/me
```

---

## Phase 5 — Audit

Développer AuditService avant les modules métier.

---

## Phase 6 — Leads

Implémenter :

```text
CRUD
webhook
AI qualification
audit
```

---

## Phase 7 — Email

Implémenter :

```text
ingestion
classification
summary
response suggestion
approval
```

---

## Phase 8 — Documents

Implémenter :

```text
upload
storage (MinIO)         🆕
extraction (Apache Tika) 🆕
AI parsing
validation
review
```

---

## Phase 9 — Invoices

Implémenter :

```text
customers
invoices
overdue detection
reminders
Mailpit
```

---

## Phase 10 — Reports

Créer le rapport quotidien.

---

## Phase 11 — Frontend

Construire :

```text
Login
Dashboard
Inbox
Leads
Documents
Invoices
Automations
Audit
Settings
```

---

## Phase 12 — Tests E2E

Tester les scénarios complets.

---

## Phase 13 — Packaging

Créer :

```text
install scripts
backup
restore
documentation
demo data
release package
```

---

# 47. Definition of Done

Une fonctionnalité n’est terminée que si :

```text
code écrit
+
compilation OK
+
tests OK
+
validation des erreurs
+
audit si nécessaire
+
documentation
+
aucun secret
+
CI verte (si le pipeline existe déjà)   🆕
```

---

# 48. Première instruction à donner à Cursor

Utilise le fichier PRD comme source de vérité du projet.

Commence UNIQUEMENT par la Phase 0 — Initialisation.

Avant d’écrire du code :

1. analyse les exigences de la Phase 0 ;
2. propose l’arborescence finale du repository ;
3. identifie les décisions techniques nécessaires ;
4. indique les fichiers que tu vas créer ;
5. n’implémente aucune fonctionnalité métier ;
6. n’anticipe pas les phases suivantes.

Après validation de l’architecture, crée uniquement les fichiers nécessaires à la Phase 0.

Vérifie ensuite :

```bash
docker compose config
```

et corrige toutes les erreurs détectées.

Termine par un compte rendu contenant :

- fichiers créés ;
- décisions prises ;
- commandes de test ;
- résultats ;
- points restant à traiter.

STOP après la Phase 0 et attends mon autorisation avant de commencer la Phase 1.