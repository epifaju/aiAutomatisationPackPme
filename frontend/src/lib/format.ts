const dateFmt = new Intl.DateTimeFormat("fr-FR", {
  dateStyle: "short",
  timeStyle: "short",
});

const dayFmt = new Intl.DateTimeFormat("fr-FR", { dateStyle: "medium" });

export function formatDateTime(value: string | null | undefined) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : dateFmt.format(date);
}

export function formatDay(value: string | null | undefined) {
  if (!value) return "—";
  const date = new Date(value.length <= 10 ? `${value}T00:00:00` : value);
  return Number.isNaN(date.getTime()) ? value : dayFmt.format(date);
}

export function formatMoney(amount: number | string | null | undefined, currency = "EUR") {
  const n = typeof amount === "number" ? amount : Number(amount ?? 0);
  return new Intl.NumberFormat("fr-FR", { style: "currency", currency }).format(Number.isFinite(n) ? n : 0);
}

export function statusTone(status: string | null | undefined) {
  const value = (status ?? "").toUpperCase();
  if (["SUCCESS", "EXTRACTED", "PAID", "EXECUTED", "SENT", "WON", "APPROVED", "GENERATED"].includes(value)) {
    return "ok";
  }
  if (["ERROR", "OVERDUE", "REJECTED", "LOST", "CANCELLED"].includes(value)) return "bad";
  if (["REVIEW_REQUIRED", "PENDING_APPROVAL", "URGENT", "HIGH"].includes(value)) return "warn";
  return "neutral";
}
