import { useQuery } from "@tanstack/react-query";
import { Card, ErrorText, PageHeader } from "@/components/ui";
import { api } from "@/lib/api";
import { formatDateTime, formatMoney } from "@/lib/format";
import type { DashboardSummary } from "@/types/api";

export function DashboardPage() {
  const query = useQuery({
    queryKey: ["dashboard"],
    queryFn: () => api<DashboardSummary>("/api/v1/dashboard/summary"),
  });

  const metrics = query.data?.metrics;
  const cards = metrics
    ? [
        ["Emails aujourd’hui", metrics.emailsReceived],
        ["Emails urgents", metrics.emailsUrgent],
        ["Nouveaux prospects", metrics.newLeads],
        ["Prospects prioritaires", metrics.priorityLeads],
        ["Documents traités", metrics.documentsProcessed],
        ["Factures en retard", metrics.overdueInvoices],
        ["Montant en retard", formatMoney(metrics.overdueAmount, metrics.currency)],
        ["Automatisations OK", metrics.automationsExecuted],
        ["Automatisations en erreur", metrics.automationErrors],
      ]
    : [];

  return (
    <div>
      <PageHeader title="Dashboard" subtitle={query.data ? `Journée du ${query.data.date}` : "Vue d’activité"} />
      {query.error ? <ErrorText error={query.error} /> : null}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {cards.map(([label, value]) => (
          <Card key={label}>
            <p className="text-sm text-muted">{label}</p>
            <p className="mt-2 font-display text-3xl">{value}</p>
          </Card>
        ))}
      </div>
      <Card className="mt-6">
        <h2 className="font-display text-xl">Recent Activity</h2>
        <ul className="mt-4 divide-y divide-line">
          {(query.data?.recentActivity ?? []).map((item) => (
            <li key={item.id} className="flex flex-wrap items-center justify-between gap-2 py-3 text-sm">
              <span>
                <strong>{item.workflow}</strong> · {item.action}
              </span>
              <span className="text-muted">
                {item.status} · {formatDateTime(item.timestamp)}
              </span>
            </li>
          ))}
          {query.data?.recentActivity?.length === 0 ? <li className="py-6 text-muted">Aucune activité récente.</li> : null}
        </ul>
      </Card>
    </div>
  );
}
