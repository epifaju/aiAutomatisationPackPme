import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Badge, Button, Card, Empty, ErrorText, Field, PageHeader, Select } from "@/components/ui";
import { api } from "@/lib/api";
import type { Document, PageResponse } from "@/types/api";

export function DocumentsPage() {
  const queryClient = useQueryClient();
  const [file, setFile] = useState<File | null>(null);
  const [documentType, setDocumentType] = useState("FACTURE");
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
    onSuccess: () => {
      setFile(null);
      void queryClient.invalidateQueries({ queryKey: ["documents"] });
    },
  });
  const act = useMutation({
    mutationFn: ({ id, action }: { id: string; action: "process" | "approve" | "reject" }) =>
      api<Document>(`/api/v1/documents/${id}/${action}`, { method: "POST" }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["documents"] }),
  });

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
        <Button disabled={!file || upload.isPending} onClick={() => upload.mutate()}>
          Déposer
        </Button>
      </Card>
      {upload.error ? <ErrorText error={upload.error} /> : null}
      {act.error ? <ErrorText error={act.error} /> : null}
      <div className="grid gap-4">
        {(list.data?.content ?? []).map((doc) => (
          <Card key={doc.id}>
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="font-medium">{doc.originalFilename}</p>
                <p className="text-sm text-muted">
                  {doc.documentType} · {(doc.sizeBytes / 1024).toFixed(1)} Ko
                </p>
              </div>
              <Badge status={doc.status}>{doc.status}</Badge>
            </div>
            {doc.extraction?.extractedJson ? (
              <pre className="mt-3 max-h-40 overflow-auto rounded-lg bg-paper p-3 text-xs">
                {JSON.stringify(doc.extraction.extractedJson, null, 2)}
              </pre>
            ) : null}
            <div className="mt-3 flex flex-wrap gap-2">
              <Button variant="outline" onClick={() => act.mutate({ id: doc.id, action: "process" })}>
                Traiter
              </Button>
              <Button variant="outline" onClick={() => act.mutate({ id: doc.id, action: "approve" })}>
                Approuver
              </Button>
              <Button variant="ghost" onClick={() => act.mutate({ id: doc.id, action: "reject" })}>
                Rejeter
              </Button>
            </div>
          </Card>
        ))}
        {list.data?.content.length === 0 ? <Empty>Aucun document.</Empty> : null}
      </div>
    </div>
  );
}
