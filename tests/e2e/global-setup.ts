import { loadE2eEnv } from "./helpers/env";

/**
 * Verify backend + Ollama model before E2E AI scenarios.
 */
async function globalSetup() {
  const env = loadE2eEnv();
  const ollamaPort = process.env.OLLAMA_PORT || "11434";

  const health = await fetch(`${env.backendBaseUrl}/actuator/health`);
  if (!health.ok) {
    throw new Error(`Backend not healthy at ${env.backendBaseUrl}/actuator/health`);
  }

  const tags = await fetch(`http://localhost:${ollamaPort}/api/tags`);
  if (!tags.ok) {
    throw new Error(`Ollama not reachable on :${ollamaPort}`);
  }
  const tagBody = (await tags.json()) as { models?: Array<{ name?: string }> };
  const hasModel = (tagBody.models ?? []).some((m) => (m.name ?? "").includes("llama3.2"));
  if (!hasModel) {
    throw new Error("Ollama model llama3.2 missing. Run: docker compose up ollama-init");
  }
}

export default globalSetup;
