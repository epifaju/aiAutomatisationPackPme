import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ProtectedRoute } from "@/components/protected-route";
import { useAuthStore } from "@/stores/auth-store";

describe("ProtectedRoute", () => {
  beforeEach(() => useAuthStore.getState().clear());

  it("redirects anonymous users to login", () => {
    render(
      <MemoryRouter initialEntries={["/"]}>
        <Routes>
          <Route path="/login" element={<p>login-screen</p>} />
          <Route element={<ProtectedRoute />}>
            <Route path="/" element={<p>private</p>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByText("login-screen")).toBeInTheDocument();
  });
});
