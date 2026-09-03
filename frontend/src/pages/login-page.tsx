import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Navigate, useNavigate } from "react-router-dom";
import { z } from "zod";
import { Button, ErrorText, Field, Input } from "@/components/ui";
import { api } from "@/lib/api";
import { useAuthStore } from "@/stores/auth-store";
import type { TokenResponse, UserMe } from "@/types/api";

const schema = z.object({
  email: z.string().email("Email invalide"),
  password: z.string().min(8, "Mot de passe trop court"),
});

type FormValues = z.infer<typeof schema>;

export function LoginPage() {
  const navigate = useNavigate();
  const token = useAuthStore((s) => s.accessToken);
  const setSession = useAuthStore((s) => s.setSession);
  const setUser = useAuthStore((s) => s.setUser);
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: "demo.admin@aipack.example", password: "DemoAdmin!2026" },
  });

  if (token) return <Navigate to="/" replace />;

  async function onSubmit(values: FormValues) {
    try {
      const tokens = await api<TokenResponse>("/api/v1/auth/login", {
        method: "POST",
        body: JSON.stringify(values),
      });
      setSession(tokens);
      const me = await api<UserMe>("/api/v1/auth/me");
      setUser(me);
      navigate("/");
    } catch (error) {
      form.setError("root", { message: error instanceof Error ? error.message : "Connexion impossible" });
    }
  }

  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      <section className="relative hidden overflow-hidden bg-pine-dark p-12 text-paper lg:flex lg:flex-col lg:justify-between">
        <p className="font-display text-4xl text-white">AI Pack</p>
        <div>
          <h1 className="font-display text-5xl leading-tight text-white">L’administratif, sans la corvée.</h1>
          <p className="mt-4 max-w-md text-lg text-white/70">
            Emails, prospects, documents, relances et rapport quotidien — self-hosted, pour TPE et PME françaises.
          </p>
        </div>
        <p className="text-sm text-white/50">Compte démo prérempli. Jamais de clé API dans le navigateur.</p>
      </section>
      <section className="flex items-center justify-center px-6 py-16">
        <form
          className="w-full max-w-md rounded-2xl border border-line bg-card p-8 shadow-card"
          noValidate
          onSubmit={form.handleSubmit((values) => void onSubmit(values))}
        >
          <p className="text-sm font-semibold uppercase tracking-[0.2em] text-pine">Connexion</p>
          <h2 className="mt-2 font-display text-3xl">Ouvrir le tableau de bord</h2>
          <div className="mt-8 grid gap-4">
            <Field label="Email">
              <Input type="email" autoComplete="username" {...form.register("email")} />
            </Field>
            <Field label="Mot de passe">
              <Input type="password" autoComplete="current-password" {...form.register("password")} />
            </Field>
            {form.formState.errors.email ? <ErrorText error={form.formState.errors.email} /> : null}
            {form.formState.errors.password ? <ErrorText error={form.formState.errors.password} /> : null}
            {form.formState.errors.root ? <ErrorText error={form.formState.errors.root} /> : null}
          </div>
          <Button
            className="mt-6 w-full"
            type="submit"
            disabled={form.formState.isSubmitting}
            onClick={() => form.clearErrors("root")}
          >
            {form.formState.isSubmitting ? "Connexion…" : "Entrer"}
          </Button>
        </form>
      </section>
    </div>
  );
}
