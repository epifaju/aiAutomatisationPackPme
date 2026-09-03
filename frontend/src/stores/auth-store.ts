import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { UserMe } from "@/types/api";

type AuthState = {
  accessToken: string | null;
  refreshToken: string | null;
  expiresAt: number | null;
  user: UserMe | null;
  setSession: (tokens: { accessToken: string; refreshToken: string; expiresIn: number }, user?: UserMe | null) => void;
  setUser: (user: UserMe | null) => void;
  clear: () => void;
};

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      expiresAt: null,
      user: null,
      setSession: (tokens, user) =>
        set({
          accessToken: tokens.accessToken,
          refreshToken: tokens.refreshToken,
          expiresAt: Date.now() + tokens.expiresIn * 1000,
          ...(user !== undefined ? { user } : {}),
        }),
      setUser: (user) => set({ user }),
      clear: () => set({ accessToken: null, refreshToken: null, expiresAt: null, user: null }),
    }),
    { name: "aipack-auth" },
  ),
);
