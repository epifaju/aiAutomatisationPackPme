import { useQuery } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import { Badge, Card, Empty, ErrorText, Field, Input, PageHeader, Select } from "@/components/ui";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import type { AuditLog, PageResponse } from "@/types/api";

export function AuditPage() {
  const [params, setParams] = useSearchParams();
  const workflow = params.get("workflow") ?? "";
  const status = params.get("status") ?? "";
  const list = useQuery({
    queryKey: ["audit", workflow, status],
    queryFn: () => {
      const query = new URLSearchParams({ size: "50" });
      if (workflow) query.set("workflow", workflow);
      if (status) query.set("status", status);
      return api<PageResponse<AuditLog>>(`/api/v1/audit?${query}`);
    },
  });

  function patch(next: Record<string, string>) {
    const copy = new URLSearchParams(params);
    for (const [key, value] of Object.entries(next)) {
      if (value) copy.set(key, value);
      else copy.delete(key);
    }
    setParams(copy);
  }

  return (
    <div>
      <PageHeader title="Audit" subtitle="Journal append-only, sans secret." />
      <div className="mb-4 grid gap-3 md:grid-cols-2">
        <Field label="Workflow">
          <Input value={workflow} onChange={(e) => patch({ workflow: e.target.value })} placeholder="invoice-reminder" />
        </Field>
        <Field label="Statut">
          <Select value={status} onChange={(e) => patch({ status: e.target.value })}>
            <option value="">Tous</option>
            <option value="SUCCESS">SUCCESS</option>
            <option value="ERROR">ERROR</option>
            <option value="SKIPPED">SKIPPED</option>
          </Select>
        </Field>
      </div>
      {list.error ? <ErrorText error={list.error} /> : null}
      <Card className="overflow-hidden p-0">
        <table className="w-full text-left text-sm">
          <thead className="bg-paper text-muted">
            <tr>
              <th className="px-4 py-3">Quand</th>
              <th className="px-4 py-3">Workflow</th>
              <th className="px-4 py-3">Action</th>
              <th className="px-4 py-3">Entité</th>
              <th className="px-4 py-3">Statut</th>
            </tr>
          </thead>
          <tbody>
            {(list.data?.content ?? []).map((item) => (
              <tr key={item.id} className="border-t border-line">
                <td className="px-4 py-3">{formatDateTime(item.timestamp)}</td>
                <td className="px-4 py-3">{item.workflow}</td>
                <td className="px-4 py-3">{item.action}</td>
                <td className="px-4 py-3 text-muted">
                  {item.entityType} {item.entityId ?? ""}
                </td>
                <td className="px-4 py-3">
                  <Badge status={item.status}>{item.status}</Badge>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {list.data?.content.length === 0 ? <Empty>Aucun événement.</Empty> : null}
      </Card>
    </div>
  );
}
