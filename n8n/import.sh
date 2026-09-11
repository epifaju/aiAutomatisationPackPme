#!/bin/sh
set -eu

bootstrap_owner() {
  if [ -z "${N8N_OWNER_EMAIL:-}" ] || [ -z "${N8N_OWNER_PASSWORD:-}" ]; then
    echo "N8N_OWNER_EMAIL / N8N_OWNER_PASSWORD unset — skip owner bootstrap (create owner via UI on first visit)."
    return 0
  fi
  echo "Bootstrapping n8n owner account (idempotent)..."
  node <<'NODE'
const email = process.env.N8N_OWNER_EMAIL;
const password = process.env.N8N_OWNER_PASSWORD;
const firstName = process.env.N8N_OWNER_FIRST_NAME || "Owner";
const lastName = process.env.N8N_OWNER_LAST_NAME || "AIPACK";
const base = process.env.N8N_INTERNAL_URL || "http://n8n:5678";

(async () => {
  const res = await fetch(`${base}/rest/owner/setup`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password, firstName, lastName }),
  });
  const text = await res.text();
  if (res.ok) {
    console.log("Owner account created:", email);
    return;
  }
  // Already set up / validation — treat as success for re-runs.
  if (res.status === 400 || res.status === 409 || /already|exist|owner/i.test(text)) {
    console.log("Owner setup skipped (already configured):", res.status, text.slice(0, 200));
    return;
  }
  console.error("Owner setup failed:", res.status, text);
  process.exit(1);
})().catch((err) => {
  console.error("Owner setup error:", err);
  process.exit(1);
});
NODE
}

import_webhook_credential() {
  if [ -z "${WEBHOOK_SECRET:-}" ]; then
    echo "WEBHOOK_SECRET is required to import the AIPACK Backend Webhook credential." >&2
    exit 1
  fi
  echo "Importing AIPACK Backend Webhook credential (httpHeaderAuth)..."
  node <<'NODE'
const fs = require("fs");
const secret = process.env.WEBHOOK_SECRET;
const path = "/tmp/aipack-webhook-header.json";
fs.writeFileSync(
  path,
  JSON.stringify(
    {
      id: "aipack-webhook-header",
      name: "AIPACK Backend Webhook",
      type: "httpHeaderAuth",
      data: {
        name: "X-Webhook-Secret",
        value: secret,
      },
    },
    null,
    2
  )
);
console.log("Wrote", path);
NODE
  n8n import:credentials --input=/tmp/aipack-webhook-header.json
  rm -f /tmp/aipack-webhook-header.json
}

bootstrap_owner
import_webhook_credential

echo "Importing AI Pack n8n workflows..."
n8n import:workflow --separate --input=/workflows

echo "Flagging AI Pack workflows active in DB..."
# WF091 Error Handler must stay inactive: Error Trigger is not a startable trigger.
# Other workflows call it via settings.errorWorkflow when they fail.
for id in \
  wf001emailingst \
  wf002emailanlys \
  wf003emailreply \
  wf010leadcaptur \
  wf011leadqualif \
  wf020docingesti \
  wf021docextract \
  wf030invoverdue \
  wf031invremindr \
  wf040dailyrepor \
  wf090auditloggr
do
  n8n update:workflow --id="$id" --active=true
done

n8n update:workflow --id=wf091errorhandl --active=false

echo "n8n workflows imported (WF091 Error Handler left inactive by design)."
echo "IMPORTANT: restart the running n8n container so webhooks/schedules register:"
echo "  docker compose restart n8n"
