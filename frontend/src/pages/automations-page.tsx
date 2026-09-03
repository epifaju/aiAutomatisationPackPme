import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { Badge, Button, Card, ErrorText, PageHeader } from "@/components/ui";
import { api } from "@/lib/api";
import type { Automation } from "@/types/api";

type RunResult = { id: string; message: string };

export function AutomationsPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const list = useQuery({
    queryKey: ["automations"],
    queryFn: () => api<Automation[]>("/api/v1/automations"),
  });
  const run = useMutation({
    mutationFn: (id: string) => api<RunResult>(`/api/v1/automations/${id}/run`, { method: "POST" }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["automations"] }),
  });
  const autoSend = useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) =>
      api<Automation>(`/api/v1/automations/${id}/auto-send`, {
        method: "POST",
        body: JSON.stringify({ enabled }),
      }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["automations"] }),
  });

  return (
    <div>
      <PageHeader title="Automations" subtitle="Cartes d’exécution. L’envoi externe reste opt-in." />
      {run.error ? <ErrorText error={run.error} /> : null}
      {autoSend.error ? <ErrorText error={autoSend.error} /> : null}
      {run.data ? <p className="mb-4 rounded-md bg-pine-light px-3 py-2 text-sm text-pine-dark">{run.data.message}</p> : null}
      <div className="grid gap-4 lg:grid-cols-2">
        {(list.data ?? []).map((item) => (
          <Card key={item.id}>
            <div className="flex items-start justify-between gap-3">
              <div>
                <h2 className="font-display text-2xl">{item.name}</h2>
                <p className="mt-1 text-sm text-muted">{item.description}</p>
              </div>
              <Badge status={item.enabled ? "SUCCESS" : "ERROR"}>{item.enabled ? "ENABLED" : "DISABLED"}</Badge>
            </div>
            <dl className="mt-4 grid grid-cols-2 gap-2 text-sm">
              <div>
                <dt className="text-muted">Executions today</dt>
                <dd className="font-display text-2xl">{item.executionsToday}</dd>
              </div>
              <div>
                <dt className="text-muted">Errors</dt>
                <dd className="font-display text-2xl">{item.errorsToday}</dd>
              </div>
            </dl>
            {["email-assistant", "invoice-reminder", "daily-report"].includes(item.id) ? (
              <p className="mt-3 text-xs text-muted">
                Envoi auto entreprise : {item.autoSendCompany ? "oui" : "non"}
                {item.autoSendCompany && !item.autoSendEffective ? " (bloqué par la variable d’environnement)" : ""}
              </p>
            ) : null}
            <div className="mt-4 flex flex-wrap gap-2">
              {["email-assistant", "invoice-reminder", "daily-report"].includes(item.id) ? (
                <Button
                  variant="outline"
                  onClick={() => autoSend.mutate({ id: item.id, enabled: !item.autoSendCompany })}
                >
                  {item.autoSendCompany ? "Disable auto-send" : "Enable auto-send"}
                </Button>
              ) : null}
              {item.runnable ? (
                <Button onClick={() => run.mutate(item.id)} disabled={run.isPending}>
                  Run
                </Button>
              ) : null}
              <Button variant="ghost" onClick={() => navigate(item.historyPath)}>
                History
              </Button>
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}
