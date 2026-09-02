# n8n

Répertoire des workflows et credentials du pack.

- `workflows/` : exports JSON des automatisations (phases ultérieures).
- `credentials/` : non versionné — aucun secret dans Git.

n8n persiste dans PostgreSQL (base `n8n` sur le même instance que l’application).
Ne pas coder de credentials en dur dans les workflows exportés.
