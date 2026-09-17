import { useEffect, useState, type ReactNode } from "react";
import { restoreSession } from "@/lib/api";

export function AuthSessionGate({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);

  useEffect(() => {
    void restoreSession().finally(() => setReady(true));
  }, []);

  if (!ready) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-paper text-sm text-ink/60">
        Restauration de la session…
      </div>
    );
  }
  return children;
}
