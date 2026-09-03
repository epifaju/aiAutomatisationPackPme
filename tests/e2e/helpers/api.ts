import type { APIRequestContext } from "@playwright/test";
import { loadE2eEnv } from "./env";

const env = () => loadE2eEnv();

export async function webhookPost(
  request: APIRequestContext,
  path: string,
  data: unknown,
  timeoutMs = loadE2eEnv().aiTimeoutMs,
) {
  const { backendBaseUrl, webhookSecret } = env();
  const res = await request.post(`${backendBaseUrl}${path}`, {
    headers: {
      "X-Webhook-Secret": webhookSecret,
      "Content-Type": "application/json",
    },
    data,
    timeout: timeoutMs,
  });
  const text = await res.text();
  if (!res.ok()) {
    throw new Error(`Webhook ${path} failed: ${res.status()} ${text}`);
  }
  return JSON.parse(text);
}

export async function loginApi(request: APIRequestContext): Promise<string> {
  const { backendBaseUrl, demoEmail, demoPassword } = env();
  const res = await request.post(`${backendBaseUrl}/api/v1/auth/login`, {
    data: { email: demoEmail, password: demoPassword },
    timeout: 30_000,
  });
  if (!res.ok()) {
    throw new Error(`Login API failed: ${res.status()} ${await res.text()}`);
  }
  const body = await res.json();
  return body.data.accessToken as string;
}

export async function apiGet(request: APIRequestContext, path: string, token: string) {
  const { backendBaseUrl } = env();
  const res = await request.get(`${backendBaseUrl}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
    timeout: 30_000,
  });
  const text = await res.text();
  if (!res.ok()) {
    throw new Error(`GET ${path} failed: ${res.status()} ${text}`);
  }
  return JSON.parse(text);
}

export async function apiPost(
  request: APIRequestContext,
  path: string,
  token: string,
  data?: unknown,
  timeoutMs = 60_000,
) {
  const { backendBaseUrl } = env();
  const res = await request.post(`${backendBaseUrl}${path}`, {
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    data: data ?? {},
    timeout: timeoutMs,
  });
  const text = await res.text();
  if (!res.ok()) {
    throw new Error(`POST ${path} failed: ${res.status()} ${text}`);
  }
  return text ? JSON.parse(text) : null;
}

export async function waitForAudit(
  request: APIRequestContext,
  token: string,
  opts: { entityType: string; entityId?: string; action?: string; workflow?: string },
  timeoutMs = 120_000,
) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const qs = new URLSearchParams({ size: "50", entityType: opts.entityType });
    if (opts.entityId) qs.set("entityId", opts.entityId);
    if (opts.action) qs.set("action", opts.action);
    if (opts.workflow) qs.set("workflow", opts.workflow);
    const body = await apiGet(request, `/api/v1/audit?${qs}`, token);
    const content = body.data?.content ?? [];
    if (content.length > 0) {
      return content;
    }
    await new Promise((r) => setTimeout(r, 2000));
  }
  throw new Error(`Audit entry not found: ${JSON.stringify(opts)}`);
}

export async function mailpitMessages(request: APIRequestContext, query?: string) {
  const { mailpitBaseUrl } = env();
  const url = query
    ? `${mailpitBaseUrl}/api/v1/search?query=${encodeURIComponent(query)}`
    : `${mailpitBaseUrl}/api/v1/messages?limit=50`;
  const res = await request.get(url);
  if (!res.ok()) {
    throw new Error(`Mailpit failed: ${res.status()} ${await res.text()}`);
  }
  return res.json();
}
