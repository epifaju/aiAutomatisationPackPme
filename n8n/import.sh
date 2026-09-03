#!/bin/sh
set -eu

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
