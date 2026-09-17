# Sécurité

## Principes MVP

1. **Aucun secret dans Git** — `.env` gitignoré ; seuls les placeholders `.env.example`.
2. **Fail-fast production (P0.1 / P0.3)** — si `APP_ENV=production` (ou `prod`), le backend **refuse de démarrer** lorsque `JWT_SECRET`, `WEBHOOK_SECRET`, `POSTGRES_PASSWORD` / `spring.datasource.password`, `REDIS_PASSWORD`, ou `MINIO_*` sont vides, trop courts, ou égaux aux placeholders `.env.example` (ex. `change-me-…`, `aipack-dev-change-me`, `minioadmin`, `change-me-redis-password`).
3. **Seed / compte démo (P0.2)** — `classpath:db/seed` n’est chargé que si le seed démo est actif (`DEMO_SEED_ENABLED` vide = auto : on hors prod, off en prod). En prod (ou `DEMO_SEED_ENABLED=false`), le reconciler BCrypt démo ne tourne pas et les comptes `demo.admin@aipack.example` / `demo.user@aipack.example` sont **désactivés** s’ils existent encore en base.
4. **Redis authentifié (P0.3)** — `requirepass` via `REDIS_PASSWORD` ; le cache IA Lettuce envoie le mot de passe. Pas d’accès anonyme sur le port Redis.
5. **Validation humaine** — envois IA (emails, relances, rapports) désactivés par défaut via `*_AUTO_SEND=false`.
6. **JWT (P1.3)** — secret fort (`JWT_SECRET` ≥ 32 car.) ; access 15 min **en mémoire navigateur** ; refresh rotatif **7 j** dans un cookie `HttpOnly` / `SameSite=Lax` / `Path=/api/v1/auth` (jamais dans le JSON ni `localStorage`). `Secure` automatique si `APP_ENV=production` (TLS Caddy / P0.5) ; `JWT_COOKIE_SECURE=true|false` pour forcer. Réutiliser un refresh déjà révoqué invalide la session.
7. **Webhooks (P1.1)** — `X-Webhook-Secret` par entreprise (SHA-256 en base). Le `companyId` du JSON doit correspondre au secret ; le secret global env `WEBHOOK_SECRET` ne permet plus d’écrire une autre entreprise. Rotation Settings (secret affiché une fois).
8. **Rate limiting (Bucket4j)** — limites in-memory par IP sur `POST /api/v1/auth/login|refresh` et `POST /webhook/**` (429 + `Retry-After`). Réglages : `RATE_LIMIT_*` / `app.rate-limit.*`. Derrière un reverse proxy, activer `RATE_LIMIT_TRUST_FORWARDED_HEADERS=true` seulement si le proxy est de confiance.
9. **ClamAV (P1.4)** — scan antivirus des documents et PJ email **avant** MinIO. En **production** (`APP_ENV=production` ou overlay `docker-compose.prod.yml`) : service `clamav` toujours démarré, `CLAMAV_ENABLED=true`, **fail-closed** (`CLAMAV_FAIL_OPEN=false`) → clamd down = `503 VIRUS_SCAN_UNAVAILABLE`. Hors prod : off par défaut (`auto`) ; activer avec `--profile clamav` + `CLAMAV_ENABLED=true` (fail-open). Malware → `422 MALWARE_DETECTED`.
10. **Audit** — journal append-only ; métadonnées filtrées (pas de mots de passe / tokens).
11. **n8n (P0.4)** — `N8N_BLOCK_ENV_ACCESS_IN_NODE=true` (pas d’accès env depuis Code/expressions) ; secret webhook via credential `AIPACK Backend Webhook` (`httpHeaderAuth`) ; compte **owner** bootstrapé par `n8n-init` (`N8N_OWNER_*`) ; `N8N_ENCRYPTION_KEY` stable ; dossier `n8n/credentials/` non versionné.
12. **Backups** — `.env` exclu par défaut ; `INCLUDE_ENV=1` / `-IncludeEnv` uniquement si l’archive est chiffrée / stockée de façon sûre. Postgres + `n8n_data` + `minio_data` (documents) sont inclus.
14. **RBAC (P1.2)** — rôle `ADMIN` requis pour `PUT /api/v1/settings`, rotation du secret webhook, et `POST /api/v1/automations/*/auto-send`. Un `USER` peut lire settings / automations (403 `FORBIDDEN` sinon). Kill-switch env `*_AUTO_SEND` inchangé.

## ClamAV

### Production (P1.4)

L’overlay `docker-compose.prod.yml` démarre `clamav` (plus besoin de `--profile clamav`), active le scan et impose le fail-closed. Premier `up --wait` : signatures (souvent 1–3 min), healthcheck `clamdscan --ping`.

```env
# facultatif : les défauts overlay suffisent
CLAMAV_ENABLED=true
CLAMAV_HOST=clamav
CLAMAV_FAIL_OPEN=false
```

### Développement (optionnel)

```powershell
# .env — forcer le scan en local
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
- [ ] Seed démo off (automatique si `APP_ENV=production` ; ou `DEMO_SEED_ENABLED=false`) — les comptes `demo.admin@…` et `demo.user@…` sont désactivés s’ils existent encore
- [ ] Désactiver ou supprimer le compte démo
- [ ] Cookie refresh : `JWT_COOKIE_SECURE=auto` (Secure dès `APP_ENV=production`) ; ne pas servir l’UI en HTTP public
- [ ] Exposer uniquement via Caddy 80/443 : `docker compose -f docker-compose.yml -f docker-compose.prod.yml` ([proxy.md](proxy.md))
- [ ] `PROXY_SITE` = hostname public, `PROXY_ACME_EMAIL` renseigné, `APP_BASE_URL=https://…`
- [ ] Activer `RATE_LIMIT_TRUST_FORWARDED_HEADERS=true` (défaut de l’overlay prod)
- [ ] ClamAV prod (P1.4) : overlay démarre `clamav`, `CLAMAV_ENABLED=true`, `CLAMAV_FAIL_OPEN=false` (ne pas republier le port 3310)
- [ ] Ne pas republier Mailpit / MinIO / Postgres / Redis / Ollama / n8n / backend (l’overlay prod retire déjà ces `ports:`)
- [ ] Vérifier que les flags `*_AUTO_SEND` correspondent à la politique client
- [ ] Planifier `scripts/backup.*` + stockage hors machine

## Données personnelles

Le seed utilise des données **fictives**. Ne pas importer de vraies données personnelles sans conformité (RGPD) et sans politiques de rétention.

## Signalement

Traiter les fuites de secrets comme un incident : rotation immédiate des clés, invalidation des JWT (changement `JWT_SECRET`), régénération credentials n8n si besoin.
