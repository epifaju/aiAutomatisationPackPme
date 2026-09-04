import { loadE2eEnv } from "./helpers/env";

async function fetchOk(url: string, attempts = 8): Promise<Response> {
  let lastError: unknown;
  for (let i = 1; i <= attempts; i++) {
    try {
      const res = await fetch(url);
      if (res.ok) return res;
      lastError = new Error(`HTTP ${res.status} for ${url}`);
    } catch (err) {
      lastError = err;
    }
    await new Promise((r) => setTimeout(r, 2000));
  }
  throw lastError instanceof Error ? lastError : new Error(String(lastError));
}

/**
 * Verify backend + Ollama model before E2E AI scenarios.
 */
async function globalSetup() {
  const env = loadE2eEnv();
  const ollamaPort = process.env.OLLAMA_PORT || "11434";

  await fetchOk(`${env.backendBaseUrl}/actuator/health`);

  const tags = await fetchOk(`http://localhost:${ollamaPort}/api/tags`);
  const tagBody = (await tags.json()) as { models?: Array<{ name?: string }> };
  const hasModel = (tagBody.models ?? []).some((m) => (m.name ?? "").includes("llama3.2"));
  if (!hasModel) {
    throw new Error("Ollama model llama3.2 missing. Run: docker compose up ollama-init");
  }
}

export default globalSetup;
