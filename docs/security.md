# Sécurité

## Principes MVP

1. **Aucun secret dans Git** — `.env` gitignoré ; seuls les placeholders `.env.example`.
2. **Fail-fast production (P0.1 / P0.3)** — si `APP_ENV=production` (ou `prod`), le backend **refuse de démarrer** lorsque `JWT_SECRET`, `WEBHOOK_SECRET`, `POSTGRES_PASSWORD` / `spring.datasource.password`, `REDIS_PASSWORD`, ou `MINIO_*` sont vides, trop courts, ou égaux aux placeholders `.env.example` (ex. `change-me-…`, `aipack-dev-change-me`, `minioadmin`, `change-me-redis-password`).
3. **Seed / compte démo (P0.2)** — `classpath:db/seed` n’est chargé que si le seed démo est actif (`DEMO_SEED_ENABLED` vide = auto : on hors prod, off en prod). En prod (ou `DEMO_SEED_ENABLED=false`), le reconciler BCrypt démo ne tourne pas et le compte `demo.admin@aipack.example` est **désactivé** s’il existe encore en base.
4. **Redis authentifié (P0.3)** — `requirepass` via `REDIS_PASSWORD` ; le cache IA Lettuce envoie le mot de passe. Pas d’accès anonyme sur le port Redis.
5. **Validation humaine** — envois IA (emails, relances, rapports) désactivés par défaut via `*_AUTO_SEND=false`.
6. **JWT** — secret fort (`JWT_SECRET` ≥ 32 car.) ; refresh rotatif ; réutilisation d’un refresh révoqué invalide la session.
7. **Webhooks** — `X-Webhook-Secret` (`WEBHOOK_SECRET`).
8. **Rate limiting (Bucket4j)** — limites in-memory par IP sur `POST /api/v1/auth/login|refresh` et `POST /webhook/**` (429 + `Retry-After`). Réglages : `RATE_LIMIT_*` / `app.rate-limit.*`. Derrière un reverse proxy, activer `RATE_LIMIT_TRUST_FORWARDED_HEADERS=true` seulement si le proxy est de confiance.
9. **ClamAV (optionnel)** — scan antivirus des documents et pièces jointes email avant MinIO. Profile Compose `clamav` + `CLAMAV_ENABLED=true`. Malware → `422 MALWARE_DETECTED`. Si clamd down : `CLAMAV_FAIL_OPEN=true` (défaut) laisse passer avec un warning ; `false` → `503 VIRUS_SCAN_UNAVAILABLE`.
10. **Audit** — journal append-only ; métadonnées filtrées (pas de mots de passe / tokens).
11. **n8n (P0.4)** — `N8N_BLOCK_ENV_ACCESS_IN_NODE=true` (pas d’accès env depuis Code/expressions) ; secret webhook via credential `AIPACK Backend Webhook` (`httpHeaderAuth`) ; compte **owner** bootstrapé par `n8n-init` (`N8N_OWNER_*`) ; `N8N_ENCRYPTION_KEY` stable ; dossier `n8n/credentials/` non versionné.
12. **Backups** — `.env` exclu par défaut ; `INCLUDE_ENV=1` / `-IncludeEnv` uniquement si l’archive est chiffrée / stockée de façon sûre. Postgres + `n8n_data` + `minio_data` (documents) sont inclus.
13. **Surface réseau (P0.5)** — en production, overlay `docker-compose.prod.yml` : seuls **80/443** (Caddy) sont publiés. Postgres, Redis, MinIO, Ollama, n8n, Mailpit, backend et frontend restent sur le réseau Compose interne — [proxy.md](proxy.md).

## Activer ClamAV

```powershell
# .env
CLAMAV_ENABLED=true
CLAMAV_HOST=clamav
CLAMAV_FAIL_OPEN=true

docker compose --profile clamav up -d clamav --wait
docker compose up -d --force-recreate backend --wait
```

Premier démarrage : téléchargement des signatures (souvent 1–3 min). Healthcheck `clamdscan --ping`.

## Checklist avant production

- [ ] Définir `APP_ENV=production` **après** avoir remplacé tous les placeholders (sinon le backend ne démarre pas)
- [ ] Changer `POSTGRES_PASSWORD`, `JWT_SECRET`, `WEBHOOK_SECRET`, `REDIS_PASSWORD`, `N8N_ENCRYPTION_KEY`, `N8N_OWNER_PASSWORD`, `MINIO_*`
- [ ] Créer / vérifier le compte owner n8n (ou laisser `n8n-init` le bootstrapper via `N8N_OWNER_*`)
- [ ] Confirmer `N8N_BLOCK_ENV_ACCESS_IN_NODE=true` et credential webhook importé
- [ ] Seed démo off (automatique si `APP_ENV=production` ; ou `DEMO_SEED_ENABLED=false`) — le compte `demo.admin@…` est désactivé s’il existe encore
- [ ] Désactiver ou supprimer le compte démo
- [ ] Exposer uniquement via Caddy 80/443 : `docker compose -f docker-compose.yml -f docker-compose.prod.yml` ([proxy.md](proxy.md))
- [ ] `PROXY_SITE` = hostname public, `PROXY_ACME_EMAIL` renseigné, `APP_BASE_URL=https://…`
- [ ] Activer `RATE_LIMIT_TRUST_FORWARDED_HEADERS=true` (défaut de l’overlay prod)
- [ ] Activer ClamAV (`--profile clamav`, `CLAMAV_ENABLED=true`) ; envisager `CLAMAV_FAIL_OPEN=false` en prod
- [ ] Ne pas republier Mailpit / MinIO / Postgres / Redis / Ollama / n8n / backend (l’overlay prod retire déjà ces `ports:`)
- [ ] Vérifier que les flags `*_AUTO_SEND` correspondent à la politique client
- [ ] Planifier `scripts/backup.*` + stockage hors machine

## Données personnelles

Le seed utilise des données **fictives**. Ne pas importer de vraies données personnelles sans conformité (RGPD) et sans politiques de rétention.

## Signalement

Traiter les fuites de secrets comme un incident : rotation immédiate des clés, invalidation des JWT (changement `JWT_SECRET`), régénération credentials n8n si besoin.
