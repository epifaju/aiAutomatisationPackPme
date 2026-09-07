import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Badge, Button, Card, Empty, ErrorText, Field, PageHeader, Select } from "@/components/ui";
import { api } from "@/lib/api";
import type { Document, PageResponse } from "@/types/api";

function processResultMessage(doc: Document) {
  if (doc.status === "EXTRACTED") {
    return `Traitement terminé : ${doc.originalFilename} extrait.`;
  }
  if (doc.status === "REVIEW_REQUIRED") {
    return `Traitement terminé : ${doc.originalFilename} à revoir (confiance faible ou ambigu).`;
  }
  if (doc.status === "ERROR") {
    const reason = doc.extraction?.status ?? "ERROR";
    return `Traitement échoué pour ${doc.originalFilename} (${reason}). Vous pouvez réessayer.`;
  }
  return `Traitement terminé : ${doc.originalFilename} · statut ${doc.status}.`;
}

export function DocumentsPage() {
  const queryClient = useQueryClient();
  const [file, setFile] = useState<File | null>(null);
  const [documentType, setDocumentType] = useState("FACTURE");
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  const list = useQuery({
    queryKey: ["documents"],
    queryFn: () => api<PageResponse<Document>>("/api/v1/documents?size=50"),
  });

  const upload = useMutation({
    mutationFn: async () => {
      if (!file) throw new Error("Fichier obligatoire");
      const body = new FormData();
      body.append("file", file);
      body.append("documentType", documentType);
      body.append("process", "true");
      return api<Document>("/api/v1/documents", { method: "POST", body });
    },
    onMutate: () => setStatusMessage(null),
    onSuccess: (doc) => {
      setFile(null);
      setStatusMessage(
        `Document déposé : ${doc.originalFilename} · statut ${doc.status}` +
          (doc.extraction?.status ? ` (extraction ${doc.extraction.status})` : "") +
          ".",
      );
      void queryClient.invalidateQueries({ queryKey: ["documents"] });
    },
  });

  const act = useMutation({
    mutationFn: ({ id, action }: { id: string; action: "process" | "approve" | "reject" }) =>
      api<Document>(`/api/v1/documents/${id}/${action}`, { method: "POST" }),
    onMutate: () => setStatusMessage(null),
    onSuccess: (doc, variables) => {
      if (variables.action === "process") {
        setStatusMessage(processResultMessage(doc));
      } else if (variables.action === "approve") {
        setStatusMessage(`Document approuvé : ${doc.originalFilename}.`);
      } else {
        setStatusMessage(`Document rejeté : ${doc.originalFilename}.`);
      }
      void queryClient.invalidateQueries({ queryKey: ["documents"] });
    },
  });

  const busyAction = act.isPending ? act.variables?.action : null;
  const busyId = act.isPending ? act.variables?.id : null;
  const processing = busyAction === "process";
  const approving = busyAction === "approve";
  const rejecting = busyAction === "reject";

  const pendingBanner = upload.isPending
    ? "Dépôt et extraction en cours via Ollama… Sur CPU, cela peut prendre 30 secondes à quelques minutes."
    : processing
      ? "Traitement IA en cours… Ne fermez pas la page. Sur CPU, comptez souvent 30 s à 2 min."
      : approving
        ? "Approbation en cours… Veuillez patienter."
        : rejecting
          ? "Rejet en cours… Veuillez patienter."
          : null;

  return (
    <div>
      <PageHeader title="Documents" subtitle="Upload MinIO, extraction Tika + IA, revue humaine." />
      <Card className="mb-6 flex flex-wrap items-end gap-3">
        <Field label="Fichier">
          <input type="file" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
        </Field>
        <Field label="Type">
          <Select value={documentType} onChange={(e) => setDocumentType(e.target.value)}>
            {["FACTURE", "DEVIS", "BON_COMMANDE", "CONTRAT", "COURRIER", "AUTRE"].map((type) => (
              <option key={type}>{type}</option>
            ))}
          </Select>
        </Field>
        <Button disabled={!file || upload.isPending || act.isPending} onClick={() => upload.mutate()}>
          {upload.isPending ? "Dépôt…" : "Déposer"}
        </Button>
      </Card>

      {upload.error ? <ErrorText error={upload.error} /> : null}
      {act.error ? <ErrorText error={act.error} /> : null}
      {pendingBanner ? (
        <p className="mb-4 rounded-lg border border-line bg-paper px-4 py-3 text-sm text-muted" role="status">
          {pendingBanner}
        </p>
      ) : null}
      {statusMessage && !upload.isPending && !act.isPending ? (
        <p className="mb-4 rounded-lg border border-pine/30 bg-pine-light/50 px-4 py-3 text-sm" role="status">
          {statusMessage}
        </p>
      ) : null}

      <div className="grid gap-4">
        {(list.data?.content ?? []).map((doc) => {
          const thisProcessing = busyId === doc.id && busyAction === "process";
          const thisApproving = busyId === doc.id && busyAction === "approve";
          const thisRejecting = busyId === doc.id && busyAction === "reject";
          const pendingBadge = thisProcessing
            ? "PROCESSING"
            : thisApproving
              ? "APPROVING"
              : thisRejecting
                ? "REJECTING"
                : null;
          return (
            <Card key={doc.id}>
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="font-medium">{doc.originalFilename}</p>
                  <p className="text-sm text-muted">
                    {doc.documentType} · {(doc.sizeBytes / 1024).toFixed(1)} Ko
                  </p>
                </div>
                <Badge status={pendingBadge ?? doc.status}>{pendingBadge ?? doc.status}</Badge>
              </div>
              {doc.extraction?.extractedJson ? (
                <pre className="mt-3 max-h-40 overflow-auto rounded-lg bg-paper p-3 text-xs">
                  {JSON.stringify(doc.extraction.extractedJson, null, 2)}
                </pre>
              ) : null}
              <div className="mt-3 flex flex-wrap gap-2">
                <Button
                  variant="outline"
                  disabled={
                    upload.isPending ||
                    act.isPending ||
                    doc.status === "PROCESSING" ||
                    doc.status === "EXTRACTED"
                  }
                  onClick={() => act.mutate({ id: doc.id, action: "process" })}
                >
                  {thisProcessing ? "Traitement…" : "Traiter"}
                </Button>
                <Button
                  variant="outline"
                  disabled={upload.isPending || act.isPending || doc.status !== "REVIEW_REQUIRED"}
                  onClick={() => act.mutate({ id: doc.id, action: "approve" })}
                >
                  {thisApproving ? "Approbation…" : "Approuver"}
                </Button>
                <Button
                  variant="ghost"
                  disabled={upload.isPending || act.isPending || doc.status !== "REVIEW_REQUIRED"}
                  onClick={() => act.mutate({ id: doc.id, action: "reject" })}
                >
                  {thisRejecting ? "Rejet…" : "Rejeter"}
                </Button>
              </div>
            </Card>
          );
        })}
        {list.data?.content.length === 0 ? <Empty>Aucun document.</Empty> : null}
      </div>
    </div>
  );
}
