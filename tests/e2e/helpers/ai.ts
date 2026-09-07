import { loadE2eEnv } from "./env";

export function ollamaBaseUrl(): string {
  const port = process.env.OLLAMA_PORT || "11434";
  return process.env.E2E_OLLAMA_URL || `http://localhost:${port}`;
}

export function ollamaModel(): string {
  return process.env.OLLAMA_MODEL || "llama3.2:1b";
}

/** In CI, AI failures fail the build. Locally, callers may skip. */
export function strictAi(): boolean {
  return process.env.CI === "true" || process.env.E2E_STRICT_AI === "1" || process.env.E2E_STRICT_AI === "true";
}

export async function warmOllama(timeoutMs = 180_000): Promise<void> {
  const model = ollamaModel();
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(`${ollamaBaseUrl()}/api/generate`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        model,
        prompt: "Reponds uniquement: OK",
        stream: false,
        keep_alive: -1,
        options: { num_predict: 8 },
      }),
      signal: controller.signal,
    });
    if (!res.ok) {
      throw new Error(`Ollama warm failed: HTTP ${res.status} ${await res.text()}`);
    }
  } finally {
    clearTimeout(timer);
  }
}

export async function ensureOllamaModelPresent(): Promise<void> {
  const model = ollamaModel();
  const res = await fetch(`${ollamaBaseUrl()}/api/tags`);
  if (!res.ok) {
    throw new Error(`Ollama /api/tags failed: HTTP ${res.status}`);
  }
  const body = (await res.json()) as { models?: Array<{ name?: string }> };
  const names = (body.models ?? []).map((m) => m.name ?? "");
  const needle = model.includes(":") ? model : `${model}:`;
  const ok = names.some((n) => n === model || n.startsWith(needle) || n.startsWith(model));
  if (!ok) {
    throw new Error(
      `Ollama model "${model}" missing (have: ${names.join(", ") || "none"}). Run: docker compose up ollama-init`,
    );
  }
}

export function analyzeReady(data: {
  status?: string;
  analysis?: {
    approvalStatus?: string | null;
    category?: string | null;
    suggestedReply?: string | null;
    status?: string | null;
  } | null;
}): boolean {
  return (
    data.status === "ANALYZED" &&
    data.analysis != null &&
    data.analysis.approvalStatus === "PENDING_APPROVAL" &&
    data.analysis.category !== "SPAM" &&
    Boolean(data.analysis.suggestedReply)
  );
}
