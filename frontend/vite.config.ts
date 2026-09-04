import path from "node:path";
import { fileURLToPath } from "node:url";
import react from "@vitejs/plugin-react";
import { loadEnv } from "vite";
import { defineConfig } from "vitest/config";

const root = fileURLToPath(new URL(".", import.meta.url));

export default defineConfig(({ mode }) => {
  const env = { ...loadEnv(mode, path.resolve(root, ".."), ""), ...process.env };
  const apiTarget = env.API_PROXY_TARGET || `http://localhost:${env.BACKEND_PORT || "8080"}`;
  console.info(`[vite] proxy /api → ${apiTarget}`);

  return {
    plugins: [react()],
    resolve: {
      alias: { "@": path.resolve(root, "src") },
    },
    server: {
      port: Number(env.FRONTEND_PORT || 5173),
      proxy: {
        "/api": {
          target: apiTarget,
          changeOrigin: true,
          timeout: 360_000,
          proxyTimeout: 360_000,
          configure(proxy) {
            proxy.on("error", (_err, _req, res) => {
              const httpRes = res as import("node:http").ServerResponse | undefined;
              if (httpRes && !httpRes.headersSent && typeof httpRes.writeHead === "function") {
                httpRes.writeHead(502, { "Content-Type": "application/json; charset=utf-8" });
                httpRes.end(
                  JSON.stringify({
                    success: false,
                    error: {
                      code: "BACKEND_UNREACHABLE",
                      message: `Backend injoignable (${apiTarget}). Vérifiez BACKEND_PORT dans .env.`,
                    },
                  }),
                );
              }
            });
          },
        },
      },
    },
    test: {
      environment: "jsdom",
      setupFiles: "./src/test/setup.ts",
      globals: true,
    },
  };
});
