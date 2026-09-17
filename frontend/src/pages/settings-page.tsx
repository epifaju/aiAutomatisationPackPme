import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Button, Card, ErrorText, Field, Input, PageHeader } from "@/components/ui";
import { api } from "@/lib/api";
import { isAdmin } from "@/lib/rbac";
import { useAuthStore } from "@/stores/auth-store";
import type { RotateWebhookSecretResponse, Settings } from "@/types/api";

const tabs = ["Company", "Email", "AI", "Invoice reminders", "Notifications", "Security"] as const;

export function SettingsPage() {
  const queryClient = useQueryClient();
  const role = useAuthStore((s) => s.user?.role);
  const admin = isAdmin(role);
  const [tab, setTab] = useState<(typeof tabs)[number]>("Company");
  const query = useQuery({ queryKey: ["settings"], queryFn: () => api<Settings>("/api/v1/settings") });
  const save = useMutation({
    mutationFn: (payload: unknown) =>
      api<Settings>("/api/v1/settings", { method: "PUT", body: JSON.stringify(payload) }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["settings"] }),
  });
  const rotateSecret = useMutation({
    mutationFn: () =>
      api<RotateWebhookSecretResponse>("/api/v1/settings/webhook-secret/rotate", { method: "POST" }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["settings"] }),
  });
  const data = query.data;
  function patch(payload: unknown) {
    if (!admin) return;
    save.mutate(payload);
  }

  return (
    <div>
      <PageHeader
        title="Settings"
        subtitle={
          admin
            ? "Paramètres entreprise. Les secrets restent côté serveur."
            : "Lecture seule. Les modifications (auto-envoi, IA, secret webhook) sont réservées à un administrateur."
        }
      />
      <div className="mb-5 flex flex-wrap gap-2">
        {tabs.map((item) => (
          <button
            key={item}
            type="button"
            onClick={() => setTab(item)}
            className={`rounded-full px-3 py-1.5 text-sm ${tab === item ? "bg-pine text-white" : "bg-card text-muted"}`}
          >
            {item}
          </button>
        ))}
      </div>
      {query.error ? <ErrorText error={query.error} /> : null}
      {save.error ? <ErrorText error={save.error} /> : null}
      {rotateSecret.error ? <ErrorText error={rotateSecret.error} /> : null}
      {save.isSuccess ? <p className="mb-4 text-sm text-pine">Enregistré.</p> : null}
      {data && tab === "Company" ? (
        <Card className="grid max-w-xl gap-3">
          <Field label="Nom">
            <Input
              defaultValue={data.company.name}
              onBlur={(e) => patch({ company: { name: e.target.value, country: data.company.country, timezone: data.company.timezone } })}
              readOnly={!admin}
            />
          </Field>
          <Field label="Pays">
            <Input defaultValue={data.company.country} readOnly />
          </Field>
          <Field label="Fuseau">
            <Input
              defaultValue={data.company.timezone}
              onBlur={(e) => patch({ company: { timezone: e.target.value } })}
              readOnly={!admin}
            />
          </Field>
        </Card>
      ) : null}
      {data && tab === "Email" ? (
        <Card className="grid max-w-xl gap-3">
          <p className="text-sm text-muted">
            Variable d’environnement `AI_GENERATED_EMAIL_AUTO_SEND` : {data.email.autoSendEnv ? "on" : "off"}
          </p>
          <Button
            variant="outline"
            disabled={!admin}
            onClick={() => patch({ email: { autoSendCompany: !data.email.autoSendCompany } })}
          >
            Auto-envoi entreprise : {data.email.autoSendCompany ? "activé" : "désactivé"}
          </Button>
          <Field label="Seuil de confiance">
            <Input
              defaultValue={String(data.email.confidenceThreshold)}
              onBlur={(e) => patch({ email: { confidenceThreshold: Number(e.target.value) } })}
              readOnly={!admin}
            />
          </Field>
        </Card>
      ) : null}
      {data && tab === "AI" ? (
        <Card className="grid max-w-xl gap-3">
          <p className="text-sm text-muted">
            Runtime Docker : {data.ai.runtimeProvider} · {data.ai.runtimeOllamaBaseUrl} · {data.ai.runtimeOllamaModel}
          </p>
          <Field label="Provider entreprise">
            <Input defaultValue={data.ai.provider} readOnly={!admin} onBlur={(e) => patch({ ai: { provider: e.target.value } })} />
          </Field>
          <Field label="URL Ollama">
            <Input
              defaultValue={data.ai.ollamaBaseUrl}
              onBlur={(e) => patch({ ai: { ollamaBaseUrl: e.target.value } })}
              readOnly={!admin}
            />
          </Field>
          <Field label="Modèle">
            <Input defaultValue={data.ai.ollamaModel} readOnly={!admin} onBlur={(e) => patch({ ai: { ollamaModel: e.target.value } })} />
          </Field>
        </Card>
      ) : null}
      {data && tab === "Invoice reminders" ? (
        <Card className="grid max-w-xl gap-3">
          <p className="text-sm text-muted">
            Env `INVOICE_REMINDER_AUTO_SEND` : {data.invoices.autoSendEnv ? "on" : "off"} · Jours :{" "}
            {data.invoices.reminderDays.join(", ")}
          </p>
          <Button
            variant="outline"
            disabled={!admin}
            onClick={() => patch({ invoices: { autoSendCompany: !data.invoices.autoSendCompany } })}
          >
            Auto-envoi entreprise : {data.invoices.autoSendCompany ? "activé" : "désactivé"}
          </Button>
        </Card>
      ) : null}
      {data && tab === "Notifications" ? (
        <Card className="grid max-w-xl gap-3">
          <p className="text-sm text-muted">
            Env `DAILY_REPORT_AUTO_SEND` : {data.reports.autoSendEnv ? "on" : "off"}
          </p>
          <Field label="Email du rapport quotidien">
            <Input
              defaultValue={data.reports.dailyReportEmail ?? ""}
              onBlur={(e) => patch({ reports: { dailyReportEmail: e.target.value || null } })}
              readOnly={!admin}
            />
          </Field>
          <Button
            variant="outline"
            disabled={!admin}
            onClick={() => patch({ reports: { autoSendCompany: !data.reports.autoSendCompany } })}
          >
            Auto-envoi rapport : {data.reports.autoSendCompany ? "activé" : "désactivé"}
          </Button>
        </Card>
      ) : null}
      {data && tab === "Security" ? (
        <Card className="grid max-w-xl gap-3">
          <Field label="Rétention des données (jours)">
            <Input
              defaultValue={String(data.security.dataRetentionDays)}
              onBlur={(e) => patch({ security: { dataRetentionDays: Number(e.target.value) } })}
              readOnly={!admin}
            />
          </Field>
          <p className="text-sm text-muted">JWT, CORS et secrets restent dans l’environnement Docker — jamais dans le frontend.</p>
          <p className="text-sm text-muted">
            Secret webhook entreprise : {data.security.webhookSecretConfigured ? "configuré (hash en base)" : "absent"}
          </p>
          <Button
            variant="outline"
            onClick={() => {
              if (window.confirm("Générer un nouveau secret webhook ? L’ancien (dont WEBHOOK_SECRET n8n) cessera de fonctionner.")) {
                rotateSecret.mutate();
              }
            }}
            disabled={!admin || rotateSecret.isPending}
          >
            {rotateSecret.isPending ? "Rotation…" : "Rotation du secret webhook"}
          </Button>
          {rotateSecret.data ? (
            <Field label="Nouveau secret (affiché une seule fois — à coller dans n8n / X-Webhook-Secret)">
              <Input readOnly value={rotateSecret.data.webhookSecret} />
            </Field>
          ) : null}
        </Card>
      ) : null}
    </div>
  );
}
