import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Badge, Button, Card, Empty, ErrorText, Field, Input, PageHeader, Select } from "@/components/ui";
import { api } from "@/lib/api";
import { formatDay, formatMoney } from "@/lib/format";
import type { Customer, Invoice, PageResponse } from "@/types/api";

export function InvoicesPage() {
  const queryClient = useQueryClient();
  const [customerId, setCustomerId] = useState("");
  const [invoiceNumber, setInvoiceNumber] = useState("");
  const [dueDate, setDueDate] = useState("");
  const invoices = useQuery({
    queryKey: ["invoices"],
    queryFn: () => api<PageResponse<Invoice>>("/api/v1/invoices?size=50"),
  });
  const customers = useQuery({
    queryKey: ["customers"],
    queryFn: () => api<PageResponse<Customer>>("/api/v1/customers?size=50"),
  });
  const create = useMutation({
    mutationFn: () =>
      api<Invoice>("/api/v1/invoices", {
        method: "POST",
        body: JSON.stringify({
          customerId,
          invoiceNumber,
          invoiceDate: dueDate,
          dueDate,
          amountExcludingTax: 100,
          vat: 20,
          amountIncludingTax: 120,
          currency: "EUR",
          status: "SENT",
        }),
      }),
    onSuccess: () => {
      setInvoiceNumber("");
      void queryClient.invalidateQueries({ queryKey: ["invoices"] });
    },
  });
  const detect = useMutation({
    mutationFn: () => api("/api/v1/invoices/overdue/detect", { method: "POST" }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["invoices"] }),
  });
  const reminder = useMutation({
    mutationFn: ({ id, level, action }: { id: string; level: number; action: "approve" | "reject" | "send" }) =>
      api<Invoice>(`/api/v1/invoices/${id}/reminders/${level}/${action}`, { method: "POST" }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["invoices"] }),
  });

  return (
    <div>
      <PageHeader
        title="Invoices"
        subtitle="Clients, factures, retards et relances avec validation."
        actions={
          <Button variant="outline" onClick={() => detect.mutate()}>
            Détecter les retards
          </Button>
        }
      />
      <Card className="mb-6 grid gap-3 md:grid-cols-4">
        <Field label="Client">
          <Select value={customerId} onChange={(e) => setCustomerId(e.target.value)}>
            <option value="">Choisir…</option>
            {(customers.data?.content ?? []).map((customer) => (
              <option key={customer.id} value={customer.id}>
                {customer.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Numéro">
          <Input value={invoiceNumber} onChange={(e) => setInvoiceNumber(e.target.value)} />
        </Field>
        <Field label="Échéance">
          <Input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
        </Field>
        <div className="flex items-end">
          <Button disabled={!customerId || !invoiceNumber || !dueDate} onClick={() => create.mutate()}>
            Créer
          </Button>
        </div>
      </Card>
      {create.error ? <ErrorText error={create.error} /> : null}
      {detect.error ? <ErrorText error={detect.error} /> : null}
      {reminder.error ? <ErrorText error={reminder.error} /> : null}
      <div className="grid gap-4">
        {(invoices.data?.content ?? []).map((invoice) => (
          <Card key={invoice.id}>
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="font-medium">
                  {invoice.invoiceNumber} · {invoice.customer.name}
                </p>
                <p className="text-sm text-muted">
                  Échéance {formatDay(invoice.dueDate)} · {formatMoney(invoice.amountIncludingTax, invoice.currency)}
                  {invoice.daysOverdue > 0 ? ` · J+${invoice.daysOverdue}` : ""}
                </p>
              </div>
              <Badge status={invoice.status}>{invoice.status}</Badge>
            </div>
            {(invoice.reminders ?? []).length > 0 ? (
              <ul className="mt-3 space-y-2 text-sm">
                {invoice.reminders!.map((item) => (
                  <li key={item.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-paper px-3 py-2">
                    <span>
                      Relance J+{item.reminderLevel} · <Badge status={item.status}>{item.status}</Badge>
                    </span>
                    <span className="flex gap-2">
                      <Button
                        variant="outline"
                        onClick={() => reminder.mutate({ id: invoice.id, level: item.reminderLevel, action: "approve" })}
                      >
                        Approuver
                      </Button>
                      <Button
                        variant="ghost"
                        onClick={() => reminder.mutate({ id: invoice.id, level: item.reminderLevel, action: "reject" })}
                      >
                        Rejeter
                      </Button>
                      <Button onClick={() => reminder.mutate({ id: invoice.id, level: item.reminderLevel, action: "send" })}>
                        Envoyer
                      </Button>
                    </span>
                  </li>
                ))}
              </ul>
            ) : null}
          </Card>
        ))}
        {invoices.data?.content.length === 0 ? <Empty>Aucune facture.</Empty> : null}
      </div>
    </div>
  );
}
