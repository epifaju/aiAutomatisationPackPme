# Sécurité

## Principes MVP

1. **Aucun secret dans Git** — `.env` gitignoré ; seuls les placeholders `.env.example`.
2. **Fail-fast production (P0.1)** — si `APP_ENV=production` (ou `prod`), le backend **refuse de démarrer** lorsque `JWT_SECRET`, `WEBHOOK_SECRET`, `POSTGRES_PASSWORD` / `spring.datasource.password`, ou `MINIO_*` sont vides, trop courts, ou égaux aux placeholders `.env.example` (ex. `change-me-…`, `aipack-dev-change-me`, `minioadmin`).
3. **Seed / compte démo (P0.2)** — `classpath:db/seed` n’est chargé que si le seed démo est actif (`DEMO_SEED_ENABLED` vide = auto : on hors prod, off en prod). En prod (ou `DEMO_SEED_ENABLED=false`), le reconciler BCrypt démo ne tourne pas et le compte `demo.admin@aipack.example` est **désactivé** s’il existe encore en base.
4. **Validation humaine** — envois IA (emails, relances, rapports) désactivés par défaut via `*_AUTO_SEND=false`.
5. **JWT** — secret fort (`JWT_SECRET` ≥ 32 car.) ; refresh rotatif ; réutilisation d’un refresh révoqué invalide la session.
6. **Webhooks** — `X-Webhook-Secret` (`WEBHOOK_SECRET`).
7. **Rate limiting (Bucket4j)** — limites in-memory par IP sur `POST /api/v1/auth/login|refresh` et `POST /webhook/**` (429 + `Retry-After`). Réglages : `RATE_LIMIT_*` / `app.rate-limit.*`. Derrière un reverse proxy, activer `RATE_LIMIT_TRUST_FORWARDED_HEADERS=true` seulement si le proxy est de confiance.
8. **ClamAV (optionnel)** — scan antivirus des documents et pièces jointes email avant MinIO. Profile Compose `clamav` + `CLAMAV_ENABLED=true`. Malware → `422 MALWARE_DETECTED`. Si clamd down : `CLAMAV_FAIL_OPEN=true` (défaut) laisse passer avec un warning ; `false` → `503 VIRUS_SCAN_UNAVAILABLE`.
9. **Audit** — journal append-only ; métadonnées filtrées (pas de mots de passe / tokens).
10. **n8n** — `N8N_ENCRYPTION_KEY` stable ; dossier `n8n/credentials/` non versionné.
11. **Backups** — `.env` exclu par défaut ; `INCLUDE_ENV=1` / `-IncludeEnv` uniquement si l’archive est chiffrée / stockée de façon sûre. Postgres + `n8n_data` + `minio_data` (documents) sont inclus.

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
- [ ] Changer `POSTGRES_PASSWORD`, `JWT_SECRET`, `WEBHOOK_SECRET`, `N8N_ENCRYPTION_KEY`, `MINIO_*`
- [ ] Seed démo off (automatique si `APP_ENV=production` ; ou `DEMO_SEED_ENABLED=false`) — le compte `demo.admin@…` est désactivé s’il existe encore
- [ ] Désactiver ou supprimer le compte démo
- [ ] Exposer uniquement via reverse proxy HTTPS (Caddy profile `proxy` — [proxy.md](proxy.md))
- [ ] Activer `RATE_LIMIT_TRUST_FORWARDED_HEADERS=true` derrière le proxy de confiance
- [ ] Activer ClamAV (`--profile clamav`, `CLAMAV_ENABLED=true`) ; envisager `CLAMAV_FAIL_OPEN=false` en prod
- [ ] Ne pas publier Mailpit / MinIO console / ClamAV sur Internet
- [ ] Restreindre les ports hôte (firewall) ; retirer les ports `frontend`/`backend` une fois Caddy en place
- [ ] Vérifier que les flags `*_AUTO_SEND` correspondent à la politique client
- [ ] Planifier `scripts/backup.*` + stockage hors machine

## Données personnelles

Le seed utilise des données **fictives**. Ne pas importer de vraies données personnelles sans conformité (RGPD) et sans politiques de rétention.

## Signalement

Traiter les fuites de secrets comme un incident : rotation immédiate des clés, invalidation des JWT (changement `JWT_SECRET`), régénération credentials n8n si besoin.
