# Workflows n8n

Les définitions versionnées vivent dans `n8n/workflows/`. L’import est automatisé par le service Compose `n8n-init` (`n8n/import.sh`).

## Catalogue MVP

| ID | Fichier | Rôle |
| --- | --- | --- |
| WF001 | `wf001emailingst.json` | Ingestion email |
| WF002 | `wf002emailanlys.json` | Analyse IA |
| WF003 | `wf003emailreply.json` | Proposition de réponse |
| WF010 | `wf010leadcaptur.json` | Capture lead |
| WF011 | `wf011leadqualif.json` | Qualification IA |
| WF020 | `wf020docingesti.json` | Ingestion document |
| WF021 | `wf021docextract.json` | Extraction IA |
| WF030 | `wf030invoverdue.json` | Détection factures en retard |
| WF031 | `wf031invremindr.json` | Relance |
| WF040 | `wf040dailyrepor.json` | Rapport quotidien |
| WF090 | `wf090auditloggr.json` | Journalisation audit |
| WF091 | `wf091errorhandl.json` | Handler d’erreurs (**laisser inactif**) |

Préfixe UI : `[AIPACK]…`.

## Rebuild des JSON

```bash
node n8n/build-workflows.mjs
```

## Credentials

Le dossier `n8n/credentials/` est gitignoré. Les secrets d’exécution sont chiffrés par `N8N_ENCRYPTION_KEY` dans le volume `n8n_data`. Ne pas committer de exports contenant des secrets en clair.

## Où tester

- UI n8n → Executions
- Application React : Inbox / Leads / Documents / Invoices (actions métier → backend ; n8n complète les chaînes webhook / cron selon config)
- Smoke : `scripts/smoke-n8n-ollama.ps1`

Plus de détail : `n8n/README.md`.
