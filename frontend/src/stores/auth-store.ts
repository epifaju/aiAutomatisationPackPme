import { create } from "zustand";
import type { UserMe } from "@/types/api";

type AuthState = {
  accessToken: string | null;
  expiresAt: number | null;
  user: UserMe | null;
  setSession: (tokens: { accessToken: string; expiresIn: number }, user?: UserMe | null) => void;
  setUser: (user: UserMe | null) => void;
  clear: () => void;
};

/** Access token en mémoire uniquement (P1.3). Le refresh vit dans un cookie HttpOnly. */
export const useAuthStore = create<AuthState>()((set) => ({
  accessToken: null,
  expiresAt: null,
  user: null,
  setSession: (tokens, user) =>
    set({
      accessToken: tokens.accessToken,
      expiresAt: Date.now() + tokens.expiresIn * 1000,
      ...(user !== undefined ? { user } : {}),
    }),
  setUser: (user) => set({ user }),
  clear: () => set({ accessToken: null, expiresAt: null, user: null }),
}));
