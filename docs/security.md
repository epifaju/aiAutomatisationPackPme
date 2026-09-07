# Sécurité

## Principes MVP

1. **Aucun secret dans Git** — `.env` gitignoré ; seuls les placeholders `.env.example`.
2. **Validation humaine** — envois IA (emails, relances, rapports) désactivés par défaut via `*_AUTO_SEND=false`.
3. **JWT** — secret fort (`JWT_SECRET` ≥ 32 car.) ; refresh rotatif ; réutilisation d’un refresh révoqué invalide la session.
4. **Webhooks** — `X-Webhook-Secret` (`WEBHOOK_SECRET`).
5. **Rate limiting (Bucket4j)** — limites in-memory par IP sur `POST /api/v1/auth/login|refresh` et `POST /webhook/**` (429 + `Retry-After`). Réglages : `RATE_LIMIT_*` / `app.rate-limit.*`. Derrière un reverse proxy, activer `RATE_LIMIT_TRUST_FORWARDED_HEADERS=true` seulement si le proxy est de confiance.
6. **Audit** — journal append-only ; métadonnées filtrées (pas de mots de passe / tokens).
7. **n8n** — `N8N_ENCRYPTION_KEY` stable ; dossier `n8n/credentials/` non versionné.
8. **Backups** — `.env` exclu par défaut ; `INCLUDE_ENV=1` / `-IncludeEnv` uniquement si l’archive est chiffrée / stockée de façon sûre. Postgres + `n8n_data` + `minio_data` (documents) sont inclus.

## Checklist avant production

- [ ] Changer `POSTGRES_PASSWORD`, `JWT_SECRET`, `WEBHOOK_SECRET`, `N8N_ENCRYPTION_KEY`, `MINIO_*`
- [ ] Désactiver ou supprimer le compte démo
- [ ] Exposer uniquement via reverse proxy HTTPS (Caddy profile `proxy` — [proxy.md](proxy.md))
- [ ] Activer `RATE_LIMIT_TRUST_FORWARDED_HEADERS=true` derrière le proxy de confiance
- [ ] Ne pas publier Mailpit / MinIO console sur Internet
- [ ] Restreindre les ports hôte (firewall) ; retirer les ports `frontend`/`backend` une fois Caddy en place
- [ ] Vérifier que les flags `*_AUTO_SEND` correspondent à la politique client
- [ ] Planifier `scripts/backup.*` + stockage hors machine

## Données personnelles

Le seed utilise des données **fictives**. Ne pas importer de vraies données personnelles sans conformité (RGPD) et sans politiques de rétention.

## Signalement

Traiter les fuites de secrets comme un incident : rotation immédiate des clés, invalidation des JWT (changement `JWT_SECRET`), régénération credentials n8n si besoin.
