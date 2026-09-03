import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { LoginPage } from "@/pages/login-page";
import { useAuthStore } from "@/stores/auth-store";

describe("LoginPage", () => {
  beforeEach(() => {
    useAuthStore.getState().clear();
  });

  it("shows the product login form", () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    );
    expect(screen.getByRole("heading", { name: /ouvrir le tableau de bord/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/email/i)).toHaveValue("demo.admin@aipack.example");
  });

  it("rejects an invalid email before calling the API", async () => {
    const user = userEvent.setup();
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    );
    await user.clear(screen.getByLabelText(/email/i));
    await user.type(screen.getByLabelText(/email/i), "pas-un-email");
    await user.click(screen.getByRole("button", { name: /entrer/i }));
    expect(await screen.findByText(/email invalide/i)).toBeInTheDocument();
    expect(fetchSpy).not.toHaveBeenCalled();
    fetchSpy.mockRestore();
  });
});
