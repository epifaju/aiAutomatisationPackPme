import { ensureOllamaModelPresent, ollamaModel, warmOllama } from "./helpers/ai";
import { loadE2eEnv } from "./helpers/env";

async function fetchOk(url: string, attempts = 12): Promise<Response> {
  let lastError: unknown;
  for (let i = 1; i <= attempts; i++) {
    try {
      const res = await fetch(url);
      if (res.ok) return res;
      lastError = new Error(`HTTP ${res.status} for ${url}`);
    } catch (err) {
      lastError = err;
    }
    await new Promise((r) => setTimeout(r, 2500));
  }
  throw lastError instanceof Error ? lastError : new Error(String(lastError));
}

/**
 * Verify backend + Ollama model, then warm the model before AI scenarios.
 */
async function globalSetup() {
  const env = loadE2eEnv();

  await fetchOk(`${env.backendBaseUrl}/actuator/health`);
  await ensureOllamaModelPresent();

  console.info(`[e2e] warming Ollama model ${ollamaModel()}…`);
  const started = Date.now();
  await warmOllama(Math.min(env.aiTimeoutMs, 300_000));
  console.info(`[e2e] Ollama warm done in ${Date.now() - started}ms`);
}

export default globalSetup;
