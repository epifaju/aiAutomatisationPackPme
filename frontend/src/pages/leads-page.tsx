import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";
import { Badge, Button, Card, Empty, ErrorText, Field, Input, PageHeader } from "@/components/ui";
import { api } from "@/lib/api";
import type { Lead, LeadImportResponse, PageResponse } from "@/types/api";

const AI_STATUS_LABELS: Record<string, string> = {
  COMPLETED: "Terminé",
  REVIEW_REQUIRED: "À revoir",
  AI_UNAVAILABLE: "IA indisponible",
  AI_PARSING_ERROR: "Erreur parsing",
  PENDING: "En attente",
};

function aiStatusLabel(status: string | null | undefined) {
  if (!status) return "—";
  return AI_STATUS_LABELS[status] ?? status;
}

function qualifyResultMessage(lead: Lead) {
  if (lead.aiStatus === "COMPLETED") {
    return `Qualification OK : score ${lead.score}, statut ${lead.status}.`;
  }
  if (lead.aiStatus === "REVIEW_REQUIRED") {
    return `Qualification terminée, confiance faible : à revoir (score ${lead.score}).`;
  }
  if (lead.aiStatus === "AI_UNAVAILABLE") {
    return "Ollama indisponible ou timeout. Vérifiez le service IA puis réessayez.";
  }
  if (lead.aiStatus === "AI_PARSING_ERROR") {
    return "L'IA a répondu, mais le JSON n'a pas pu être interprété.";
  }
  return "Qualification terminée.";
}

export function LeadsPage() {
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [q, setQ] = useState("");
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

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
      setSuccessMessage(null);
      void queryClient.invalidateQueries({ queryKey: ["leads"] });
    },
  });

  const importCsv = useMutation({
    mutationFn: (file: File) => {
      const body = new FormData();
      body.append("file", file);
      return api<LeadImportResponse>("/api/v1/leads/import", { method: "POST", body });
    },
    onSuccess: (result) => {
      const failedHint =
        result.failed > 0 && result.errors[0]
          ? ` Ex. ligne ${result.errors[0].row} : ${result.errors[0].message}`
          : "";
      setSuccessMessage(
        `Import CSV : ${result.imported} importé(s), ${result.failed} échec(s) sur ${result.totalRows} ligne(s).${failedHint}`,
      );
      void queryClient.invalidateQueries({ queryKey: ["leads"] });
    },
  });

  const qualify = useMutation({
    mutationFn: (id: string) => api<Lead>(`/api/v1/leads/${id}/qualify`, { method: "POST" }),
    onMutate: () => setSuccessMessage(null),
    onSuccess: (lead) => {
      setSelectedId(lead.id);
      setSuccessMessage(qualifyResultMessage(lead));
      void queryClient.invalidateQueries({ queryKey: ["leads"] });
    },
  });

  const selected = (list.data?.content ?? []).find((lead) => lead.id === selectedId) ?? null;
  const qualifyingId = qualify.isPending ? qualify.variables : null;

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
        <div className="flex flex-wrap items-end gap-2">
          <Button disabled={!fullName || create.isPending} onClick={() => create.mutate()}>
            Ajouter
          </Button>
          <input
            ref={fileInputRef}
            type="file"
            accept=".csv,text/csv"
            className="hidden"
            onChange={(e) => {
              const file = e.target.files?.[0];
              e.target.value = "";
              if (!file) return;
              setSuccessMessage(null);
              importCsv.mutate(file);
            }}
          />
          <Button
            variant="outline"
            disabled={importCsv.isPending}
            onClick={() => fileInputRef.current?.click()}
          >
            {importCsv.isPending ? "Import…" : "Importer CSV"}
          </Button>
        </div>
        <Field label="Recherche">
          <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Nom, email, société" />
        </Field>
      </Card>

      {create.error ? <ErrorText error={create.error} /> : null}
      {importCsv.error ? <ErrorText error={importCsv.error} /> : null}
      {qualify.error ? <ErrorText error={qualify.error} /> : null}
      {qualify.isPending ? (
        <p className="mb-4 rounded-lg border border-line bg-paper px-4 py-3 text-sm text-muted" role="status">
          Qualification en cours via Ollama… Sur CPU, cela peut prendre 1 à 3 minutes. Ne fermez pas la page.
        </p>
      ) : null}
      {successMessage ? (
        <p className="mb-4 rounded-lg border border-pine/30 bg-pine-light/50 px-4 py-3 text-sm" role="status">
          {successMessage}
        </p>
      ) : null}
      <p className="mb-4 text-xs text-muted">
        CSV UTF-8 : colonnes <code>fullName</code> (ou <code>nom</code>), optionnel <code>email</code>,{" "}
        <code>companyName</code>, <code>phone</code>, <code>summary</code> — max 500 lignes.
      </p>

      <div className="grid gap-6 xl:grid-cols-[1.15fr_0.85fr]">
        <Card className="overflow-x-auto overflow-y-hidden p-0">
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead className="bg-paper text-muted">
              <tr>
                <th className="px-4 py-3">Prospect</th>
                <th className="px-4 py-3">Score</th>
                <th className="px-4 py-3">Statut</th>
                <th className="px-4 py-3">IA</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody>
              {(list.data?.content ?? []).map((lead) => {
                const busy = qualifyingId === lead.id;
                return (
                  <tr
                    key={lead.id}
                    className={`cursor-pointer border-t border-line hover:bg-pine-light/40 ${
                      selectedId === lead.id ? "bg-pine-light/60" : ""
                    }`}
                    onClick={() => setSelectedId(lead.id)}
                  >
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
                    <td className="px-4 py-3">
                      <Badge status={lead.aiStatus}>{aiStatusLabel(lead.aiStatus)}</Badge>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <Button
                        variant="outline"
                        disabled={qualify.isPending}
                        onClick={(e) => {
                          e.stopPropagation();
                          qualify.mutate(lead.id);
                        }}
                      >
                        {busy ? "Qualification…" : "Qualifier"}
                      </Button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          {list.data?.content.length === 0 ? <Empty>Aucun prospect.</Empty> : null}
        </Card>

        <Card>
          {!selected ? (
            <p className="text-muted">Sélectionnez un prospect pour voir le détail IA.</p>
          ) : (
            <div className="grid gap-3 text-sm">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <h2 className="font-display text-2xl">{selected.fullName}</h2>
                <Button disabled={qualify.isPending} onClick={() => qualify.mutate(selected.id)}>
                  {qualifyingId === selected.id ? "Qualification…" : "Qualifier"}
                </Button>
              </div>
              <p className="text-muted">
                {selected.email ?? "—"} · {selected.companyName ?? "—"} · {selected.source}
              </p>
              <p>
                <Badge status={selected.status}>{selected.status}</Badge>{" "}
                <Badge status={selected.aiStatus}>{aiStatusLabel(selected.aiStatus)}</Badge>{" "}
                <Badge status={selected.scoreBand ?? ""}>
                  score {selected.score}
                  {selected.scoreBand ? ` (${selected.scoreBand})` : ""}
                </Badge>
              </p>
              {selected.aiStatus === "AI_UNAVAILABLE" ? (
                <p className="rounded-lg border border-rust/30 bg-rust/5 px-3 py-2 text-sm">
                  La dernière tentative a échoué (timeout ou Ollama lent). Vous pouvez relancer Qualifier ;
                  sur CPU, comptez plusieurs minutes.
                </p>
              ) : null}
              {selected.confidenceScore != null ? (
                <p className="text-muted">Confiance IA : {String(selected.confidenceScore)}</p>
              ) : null}
              {selected.summary ? (
                <div>
                  <p className="mb-1 font-semibold">Résumé</p>
                  <p className="whitespace-pre-wrap rounded-lg bg-paper p-3">{selected.summary}</p>
                </div>
              ) : null}
              {selected.probableNeed ? (
                <p>
                  <span className="font-semibold">Besoin probable :</span> {selected.probableNeed}
                </p>
              ) : null}
              {selected.urgency ? (
                <p>
                  <span className="font-semibold">Urgence :</span> {selected.urgency}
                </p>
              ) : null}
              {selected.recommendedAction ? (
                <div>
                  <p className="mb-1 font-semibold">Action recommandée</p>
                  <p className="whitespace-pre-wrap rounded-lg border border-line bg-white p-3">
                    {selected.recommendedAction}
                  </p>
                </div>
              ) : selected.aiStatus !== "AI_UNAVAILABLE" ? (
                <p className="text-muted">Pas encore de détail IA. Utilisez le bouton Qualifier ci-dessus.</p>
              ) : null}
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}
