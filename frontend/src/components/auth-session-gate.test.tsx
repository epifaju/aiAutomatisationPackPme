import { render, screen, waitFor } from "@testing-library/react";
import { AuthSessionGate } from "@/components/auth-session-gate";
import { useAuthStore } from "@/stores/auth-store";

describe("AuthSessionGate", () => {
  beforeEach(() => {
    useAuthStore.getState().clear();
  });

  it("restores the access token from the HttpOnly refresh cookie", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url = String(input);
      if (url.includes("/api/v1/auth/refresh")) {
        return new Response(
          JSON.stringify({
            success: true,
            data: { accessToken: "access-from-cookie", tokenType: "Bearer", expiresIn: 900 },
            error: null,
            timestamp: new Date().toISOString(),
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        );
      }
      if (url.includes("/api/v1/auth/me")) {
        return new Response(
          JSON.stringify({
            success: true,
            data: {
              id: "u1",
              email: "demo.admin@aipack.example",
              fullName: "Demo Admin",
              role: "ADMIN",
              companyId: "c1",
              companyName: "Demo",
            },
            error: null,
            timestamp: new Date().toISOString(),
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        );
      }
      return new Response("{}", { status: 404 });
    });

    render(
      <AuthSessionGate>
        <p>ready</p>
      </AuthSessionGate>,
    );
    expect(screen.getByText(/restauration de la session/i)).toBeInTheDocument();
    expect(await screen.findByText("ready")).toBeInTheDocument();
    await waitFor(() => {
      expect(useAuthStore.getState().accessToken).toBe("access-from-cookie");
      expect(useAuthStore.getState().user?.email).toBe("demo.admin@aipack.example");
    });
    expect(fetchSpy).toHaveBeenCalledWith(
      "/api/v1/auth/refresh",
      expect.objectContaining({ credentials: "include" }),
    );
    fetchSpy.mockRestore();
  });
});
