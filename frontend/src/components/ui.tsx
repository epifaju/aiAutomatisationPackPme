import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from "react";
import { cn } from "@/lib/utils";
import { statusTone } from "@/lib/format";

export function Button({
  variant = "primary",
  className,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: "primary" | "ghost" | "danger" | "outline" }) {
  return (
    <button
      className={cn(
        "inline-flex items-center justify-center gap-2 rounded-md px-3.5 py-2 text-sm font-semibold transition disabled:cursor-not-allowed disabled:opacity-50",
        variant === "primary" && "bg-pine text-white hover:bg-pine-dark",
        variant === "ghost" && "text-ink hover:bg-white/70",
        variant === "outline" && "border border-line bg-card text-ink hover:border-pine",
        variant === "danger" && "bg-rust text-white hover:bg-rust/90",
        className,
      )}
      {...props}
    />
  );
}

export function Card({ className, children }: { className?: string; children: ReactNode }) {
  return <section className={cn("rounded-2xl border border-line bg-card p-5 shadow-card", className)}>{children}</section>;
}

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="grid gap-1.5 text-sm">
      <span className="font-medium text-muted">{label}</span>
      {children}
    </label>
  );
}

export function Input(props: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      {...props}
      className={cn(
        "h-10 w-full rounded-md border border-line bg-paper px-3 text-ink outline-none ring-pine/30 focus:bg-white focus:ring-2",
        props.className,
      )}
    />
  );
}

export function Textarea(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <textarea
      {...props}
      className={cn(
        "min-h-28 w-full rounded-md border border-line bg-paper px-3 py-2 text-ink outline-none ring-pine/30 focus:bg-white focus:ring-2",
        props.className,
      )}
    />
  );
}

export function Select(props: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select
      {...props}
      className={cn(
        "h-10 w-full rounded-md border border-line bg-paper px-3 text-ink outline-none ring-pine/30 focus:bg-white focus:ring-2",
        props.className,
      )}
    />
  );
}

export function Badge({ status, children }: { status?: string | null; children: ReactNode }) {
  const tone = statusTone(status ?? String(children));
  return (
    <span
      className={cn(
        "inline-flex rounded-full px-2.5 py-0.5 text-xs font-semibold tracking-wide",
        tone === "ok" && "bg-pine-light text-pine-dark",
        tone === "bad" && "bg-rose-100 text-rust",
        tone === "warn" && "bg-orange-100 text-clay",
        tone === "neutral" && "bg-line/70 text-muted",
      )}
    >
      {children}
    </span>
  );
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="font-display text-3xl tracking-tight text-ink">{title}</h1>
        {subtitle ? <p className="mt-1 text-muted">{subtitle}</p> : null}
      </div>
      {actions}
    </div>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <p className="rounded-xl border border-dashed border-line px-4 py-10 text-center text-muted">{children}</p>;
}

export function ErrorText({ error }: { error: unknown }) {
  const message =
    error instanceof Error
      ? error.message
      : error && typeof error === "object" && "message" in error
        ? String((error as { message?: string }).message)
        : "Une erreur est survenue";
  return <p className="rounded-md bg-rose-50 px-3 py-2 text-sm text-rust">{message}</p>;
}
