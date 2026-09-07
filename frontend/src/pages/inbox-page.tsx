import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Badge, Button, Card, Empty, ErrorText, PageHeader, Select } from "@/components/ui";
import { api, downloadAuthenticated } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import type { Email, PageResponse } from "@/types/api";

const APPROVAL_LABELS: Record<string, string> = {
  PENDING_APPROVAL: "En attente",
  APPROVED: "Approuvé",
  REJECTED: "Rejeté",
  EXECUTED: "Envoyé",
};

function approvalLabel(status: string | null | undefined) {
  if (!status) return "—";
  return APPROVAL_LABELS[status] ?? status;
}

function canApprove(email: Email) {
  const analysis = email.analysis;
  return Boolean(
    analysis
      && analysis.approvalStatus === "PENDING_APPROVAL"
      && analysis.category !== "SPAM"
      && analysis.suggestedReply,
  );
}

function canSend(email: Email) {
  return email.analysis?.approvalStatus === "APPROVED";
}

function statusHint(email: Email) {
  const status = email.analysis?.approvalStatus;
  if (status === "EXECUTED") {
    return "Cette réponse a déjà été envoyée. Ouvrez Mailpit pour la consulter.";
  }
  if (status === "APPROVED") {
    return "Réponse approuvée. Cliquez sur Envoyer pour la transmettre via Mailpit.";
  }
  if (status === "REJECTED") {
    return "Cette proposition de réponse a été rejetée.";
  }
  if (email.analysis?.category === "SPAM") {
    return "Le spam n’a pas de réponse à valider.";
  }
  if (!canApprove(email) && !canSend(email)) {
    return "Choisissez un message « En attente », puis Approuver, puis Envoyer.";
  }
  return null;
}

function successHint(action: "analyze" | "approve" | "reject" | "send") {
  if (action === "approve") return "Réponse approuvée. Vous pouvez maintenant cliquer sur Envoyer.";
  if (action === "send") return "Réponse envoyée. Vérifiez-la dans Mailpit (boîte de test SMTP).";
  if (action === "reject") return "Proposition de réponse rejetée.";
  return "Analyse relancée.";
}

function formatBytes(size: number) {
  if (size < 1024) return `${size} o`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} Ko`;
  return `${(size / (1024 * 1024)).toFixed(1)} Mo`;
}

export function InboxPage() {
  const queryClient = useQueryClient();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [approval, setApproval] = useState("");
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const list = useQuery({
    queryKey: ["emails", approval],
    queryFn: () =>
      api<PageResponse<Email>>(`/api/v1/emails?size=50${approval ? `&approvalStatus=${approval}` : ""}`),
  });
  const detail = useQuery({
    queryKey: ["email", selectedId],
    queryFn: () => api<Email>(`/api/v1/emails/${selectedId}`),
    enabled: Boolean(selectedId),
  });

  const act = useMutation({
    mutationFn: ({ id, action }: { id: string; action: "analyze" | "approve" | "reject" | "send" }) =>
      api<Email>(`/api/v1/emails/${id}/${action}`, { method: "POST" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["emails"] });
      void queryClient.invalidateQueries({ queryKey: ["email", selectedId] });
    },
  });

  const selected = detail.data;

  return (
    <div>
      <PageHeader title="Inbox" subtitle="Analyse IA, validation humaine, puis envoi Mailpit." />
      <div className="mb-4 max-w-xs">
        <Select value={approval} onChange={(e) => setApproval(e.target.value)}>
          <option value="">Tous les statuts d’approbation</option>
          <option value="PENDING_APPROVAL">En attente</option>
          <option value="APPROVED">Approuvé</option>
          <option value="REJECTED">Rejeté</option>
          <option value="EXECUTED">Envoyé</option>
        </Select>
      </div>
      {list.error ? <ErrorText error={list.error} /> : null}
      <div className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
        <Card className="overflow-hidden p-0">
          <table className="w-full text-left text-sm">
            <thead className="bg-paper text-muted">
              <tr>
                <th className="px-4 py-3 font-medium">Sujet</th>
                <th className="px-4 py-3 font-medium">De</th>
                <th className="px-4 py-3 font-medium">Priorité</th>
                <th className="px-4 py-3 font-medium">Validation</th>
              </tr>
            </thead>
            <tbody>
              {(list.data?.content ?? []).map((email) => (
                <tr
                  key={email.id}
                  className={`cursor-pointer border-t border-line hover:bg-pine-light/40 ${selectedId === email.id ? "bg-pine-light/60" : ""}`}
                  onClick={() => {
                    act.reset();
                    setDownloadError(null);
                    setSelectedId(email.id);
                  }}
                >
                  <td className="px-4 py-3">
                    {email.subject}
                    {(email.attachments?.length ?? 0) > 0 ? (
                      <span className="ml-2 text-xs text-muted">PJ</span>
                    ) : null}
                  </td>
                  <td className="px-4 py-3 text-muted">{email.fromAddress}</td>
                  <td className="px-4 py-3">
                    <Badge status={email.analysis?.priority}>{email.analysis?.priority ?? email.status}</Badge>
                  </td>
                  <td className="px-4 py-3">
                    <Badge status={email.analysis?.approvalStatus}>
                      {approvalLabel(email.analysis?.approvalStatus)}
                    </Badge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {list.data?.content.length === 0 ? <Empty>Aucun email.</Empty> : null}
        </Card>
        <Card>
          {!selected ? (
            <p className="text-muted">Sélectionnez un message.</p>
          ) : (
            <div className="grid gap-3">
              <h2 className="font-display text-2xl">{selected.subject}</h2>
              <p className="text-sm text-muted">
                {selected.fromAddress} · {formatDateTime(selected.receivedAt)}
              </p>
              <p className="whitespace-pre-wrap rounded-lg bg-paper p-3 text-sm">{selected.bodyText}</p>
              {(selected.attachments?.length ?? 0) > 0 ? (
                <div className="grid gap-2 rounded-lg border border-line bg-white p-3 text-sm">
                  <p className="font-medium">Pièces jointes</p>
                  <ul className="grid gap-2">
                    {selected.attachments!.map((file) => (
                      <li key={file.id} className="flex flex-wrap items-center justify-between gap-2">
                        <span>
                          {file.originalFilename}{" "}
                          <span className="text-muted">
                            ({file.contentType}, {formatBytes(file.sizeBytes)})
                          </span>
                        </span>
                        <Button
                          variant="outline"
                          onClick={() => {
                            setDownloadError(null);
                            void downloadAuthenticated(
                              `/api/v1/emails/${selected.id}/attachments/${file.id}/content`,
                              file.originalFilename,
                            ).catch((err: unknown) => {
                              setDownloadError(err instanceof Error ? err.message : "Téléchargement impossible");
                            });
                          }}
                        >
                          Télécharger
                        </Button>
                      </li>
                    ))}
                  </ul>
                </div>
              ) : null}
              {selected.analysis ? (
                <div className="grid gap-2 text-sm">
                  <p>
                    <Badge status={selected.analysis.category}>{selected.analysis.category}</Badge>{" "}
                    <Badge status={selected.analysis.priority}>{selected.analysis.priority}</Badge>{" "}
                    <Badge status={selected.analysis.status}>{selected.analysis.status}</Badge>{" "}
                    <Badge status={selected.analysis.approvalStatus}>
                      {approvalLabel(selected.analysis.approvalStatus)}
                    </Badge>
                  </p>
                  <p>{selected.analysis.summary}</p>
                  {selected.analysis.suggestedReply ? (
                    <pre className="whitespace-pre-wrap rounded-lg border border-line bg-white p-3 font-sans text-sm">
                      {selected.analysis.suggestedReply}
                    </pre>
                  ) : null}
                </div>
              ) : null}
              {act.error ? <ErrorText error={act.error} /> : null}
              {downloadError ? <p className="text-sm text-red-700">{downloadError}</p> : null}
              {act.isSuccess && act.variables ? (
                <p className="rounded-md bg-pine-light px-3 py-2 text-sm text-pine-dark">
                  {successHint(act.variables.action)}
                </p>
              ) : null}
              {statusHint(selected) ? <p className="text-sm text-muted">{statusHint(selected)}</p> : null}
              <div className="flex flex-wrap gap-2">
                <Button variant="outline" onClick={() => act.mutate({ id: selected.id, action: "analyze" })}>
                  Analyser
                </Button>
                <Button
                  variant="outline"
                  disabled={!canApprove(selected) || act.isPending}
                  onClick={() => act.mutate({ id: selected.id, action: "approve" })}
                >
                  Approuver
                </Button>
                <Button
                  variant="ghost"
                  disabled={!canApprove(selected) || act.isPending}
                  onClick={() => act.mutate({ id: selected.id, action: "reject" })}
                >
                  Rejeter
                </Button>
                <Button
                  variant={canSend(selected) ? "primary" : "outline"}
                  disabled={!canSend(selected) || act.isPending}
                  onClick={() => act.mutate({ id: selected.id, action: "send" })}
                >
                  Envoyer
                </Button>
              </div>
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}
