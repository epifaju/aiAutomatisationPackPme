import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Badge, Button, Card, Empty, ErrorText, Field, Input, PageHeader } from "@/components/ui";
import { api } from "@/lib/api";
import type { Lead, PageResponse } from "@/types/api";

export function LeadsPage() {
  const queryClient = useQueryClient();
  const [q, setQ] = useState("");
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const list = useQuery({
    queryKey: ["leads", q],
    queryFn: () => api<PageResponse<Lead>>(`/api/v1/leads?size=50${q ? `&q=${encodeURIComponent(q)}` : ""}`),
  });
  const create = useMutation({
    mutationFn: () =>
      api<Lead>("/api/v1/leads", {
        method: "POST",
        body: JSON.stringify({ fullName, email: email || undefined, source: "WEB_FORM" }),
      }),
    onSuccess: () => {
      setFullName("");
      setEmail("");
      void queryClient.invalidateQueries({ queryKey: ["leads"] });
    },
  });
  const qualify = useMutation({
    mutationFn: (id: string) => api<Lead>(`/api/v1/leads/${id}/qualify`, { method: "POST" }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["leads"] }),
  });

  return (
    <div>
      <PageHeader title="Leads" subtitle="Capture, qualification IA, pipeline." />
      <Card className="mb-6 grid gap-3 md:grid-cols-4">
        <Field label="Nom">
          <Input value={fullName} onChange={(e) => setFullName(e.target.value)} />
        </Field>
        <Field label="Email">
          <Input value={email} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <div className="flex items-end">
          <Button disabled={!fullName || create.isPending} onClick={() => create.mutate()}>
            Ajouter
          </Button>
        </div>
        <Field label="Recherche">
          <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Nom, email, société" />
        </Field>
      </Card>
      {create.error ? <ErrorText error={create.error} /> : null}
      {qualify.error ? <ErrorText error={qualify.error} /> : null}
      <Card className="overflow-hidden p-0">
        <table className="w-full text-left text-sm">
          <thead className="bg-paper text-muted">
            <tr>
              <th className="px-4 py-3">Prospect</th>
              <th className="px-4 py-3">Score</th>
              <th className="px-4 py-3">Statut</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {(list.data?.content ?? []).map((lead) => (
              <tr key={lead.id} className="border-t border-line">
                <td className="px-4 py-3">
                  <div className="font-medium">{lead.fullName}</div>
                  <div className="text-muted">{lead.email ?? lead.companyName}</div>
                </td>
                <td className="px-4 py-3">
                  {lead.score} <Badge status={lead.scoreBand ?? ""}>{lead.scoreBand ?? "—"}</Badge>
                </td>
                <td className="px-4 py-3">
                  <Badge status={lead.status}>{lead.status}</Badge>
                </td>
                <td className="px-4 py-3 text-right">
                  <Button variant="outline" onClick={() => qualify.mutate(lead.id)}>
                    Qualifier
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {list.data?.content.length === 0 ? <Empty>Aucun prospect.</Empty> : null}
      </Card>
    </div>
  );
}
