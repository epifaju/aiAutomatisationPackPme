import { config as loadDotenv } from "dotenv";
import { existsSync } from "node:fs";
import { resolve } from "node:path";

export type E2eEnv = {
  appBaseUrl: string;
  backendBaseUrl: string;
  mailpitBaseUrl: string;
  webhookSecret: string;
  demoCompanyId: string;
  demoEmail: string;
  demoPassword: string;
  aiTimeoutMs: number;
};

let cached: E2eEnv | null = null;

export function loadE2eEnv(): E2eEnv {
  if (cached) return cached;

  const rootCandidates = [
    resolve(process.cwd(), ".env"),
    resolve(process.cwd(), "../../.env"),
    resolve(__dirname, "../../../.env"),
  ];
  for (const envPath of rootCandidates) {
    if (existsSync(envPath)) {
      loadDotenv({ path: envPath });
      break;
    }
  }

  const backendPort = process.env.BACKEND_PORT || "8080";
  const frontendPort = process.env.FRONTEND_PORT || "5173";
  const mailpitUiPort = process.env.MAILPIT_UI_PORT || "8025";

  cached = {
    appBaseUrl: process.env.APP_BASE_URL || `http://localhost:${frontendPort}`,
    backendBaseUrl: process.env.E2E_BACKEND_URL || `http://localhost:${backendPort}`,
    mailpitBaseUrl: process.env.E2E_MAILPIT_URL || `http://localhost:${mailpitUiPort}`,
    webhookSecret: process.env.WEBHOOK_SECRET || "change-me-webhook-secret-min-16-chars",
    demoCompanyId: process.env.DEMO_COMPANY_ID || "aaaaaaaa-0000-4000-8000-000000000001",
    demoEmail: "demo.admin@aipack.example",
    demoPassword: "DemoAdmin!2026",
    aiTimeoutMs: Number(process.env.E2E_AI_TIMEOUT_MS || 300_000),
  };
  return cached;
}
