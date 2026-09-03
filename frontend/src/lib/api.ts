import { useAuthStore } from "@/stores/auth-store";
import type { ApiResponse, TokenResponse } from "@/types/api";

export class ApiClientError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

async function parseBody<T>(response: Response): Promise<T | null> {
  if (response.status === 204) return null;
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text) as T;
  } catch {
    return null;
  }
}

let refreshInFlight: Promise<boolean> | null = null;

async function refreshTokens(): Promise<boolean> {
  const refreshToken = useAuthStore.getState().refreshToken;
  if (!refreshToken) return false;
  const response = await fetch("/api/v1/auth/refresh", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken }),
  });
  if (!response.ok) {
    useAuthStore.getState().clear();
    return false;
  }
  const payload = (await response.json()) as ApiResponse<TokenResponse>;
  if (!payload.success || !payload.data) {
    useAuthStore.getState().clear();
    return false;
  }
  useAuthStore.getState().setSession(payload.data);
  return true;
}

function ensureFreshToken() {
  const { accessToken, expiresAt } = useAuthStore.getState();
  if (!accessToken || !expiresAt) return Promise.resolve(Boolean(accessToken));
  if (expiresAt - Date.now() > 30_000) return Promise.resolve(true);
  if (!refreshInFlight) {
    refreshInFlight = refreshTokens().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

export async function api<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  if (!path.startsWith("/api/v1/auth/login") && !path.startsWith("/api/v1/auth/refresh")) {
    await ensureFreshToken();
  }
  const headers = new Headers(init.headers);
  const token = useAuthStore.getState().accessToken;
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (init.body && !(init.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  const response = await fetch(path, { ...init, headers });
  if (response.status === 401 && retry && !path.startsWith("/api/v1/auth/")) {
    const refreshed = await refreshTokens();
    if (refreshed) return api<T>(path, init, false);
  }
  const payload = await parseBody<ApiResponse<T> | T>(response);
  if (!response.ok) {
    const error = payload && typeof payload === "object" && "error" in payload ? payload.error : null;
    throw new ApiClientError(
      response.status,
      error?.code ?? "HTTP_ERROR",
      error?.message ?? `Erreur ${response.status}`,
    );
  }
  if (payload && typeof payload === "object" && "success" in payload) {
    const envelope = payload as ApiResponse<T>;
    if (!envelope.success) {
      throw new ApiClientError(response.status, envelope.error?.code ?? "ERROR", envelope.error?.message ?? "Erreur");
    }
    return envelope.data;
  }
  return payload as T;
}

export function apiQuery<T>(path: string) {
  return api<T>(path);
}
