import { NavLink, Outlet, useNavigate } from "react-router-dom";
import {
  Activity,
  FileText,
  Inbox,
  LayoutDashboard,
  LogOut,
  Receipt,
  Settings,
  Sparkles,
  Users,
} from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { api } from "@/lib/api";
import { cn } from "@/lib/utils";

const links = [
  { to: "/", label: "Dashboard", icon: LayoutDashboard },
  { to: "/inbox", label: "Inbox", icon: Inbox },
  { to: "/leads", label: "Leads", icon: Users },
  { to: "/documents", label: "Documents", icon: FileText },
  { to: "/invoices", label: "Invoices", icon: Receipt },
  { to: "/automations", label: "Automations", icon: Sparkles },
  { to: "/audit", label: "Audit", icon: Activity },
  { to: "/settings", label: "Settings", icon: Settings },
];

export function AppShell() {
  const user = useAuthStore((s) => s.user);
  const clear = useAuthStore((s) => s.clear);
  const navigate = useNavigate();

  async function logout() {
    try {
      await api("/api/v1/auth/logout", { method: "POST" });
    } catch {
      /* session already invalid */
    }
    clear();
    navigate("/login");
  }

  return (
    <div className="min-h-screen lg:grid lg:grid-cols-[240px_1fr]">
      <aside className="border-b border-line bg-pine-dark text-paper lg:border-b-0 lg:border-r lg:border-pine/40">
        <div className="flex items-center justify-between px-5 py-5 lg:block">
          <div>
            <p className="font-display text-2xl text-white">AI Pack</p>
            <p className="mt-1 text-xs uppercase tracking-[0.18em] text-white/60">TPE / PME</p>
          </div>
          <p className="hidden text-sm text-white/70 lg:mt-6 lg:block">{user?.companyName}</p>
        </div>
        <nav className="flex gap-1 overflow-x-auto px-3 pb-3 lg:flex-col lg:px-3">
          {links.map((link) => {
            const Icon = link.icon;
            return (
              <NavLink
                key={link.to}
                to={link.to}
                end={link.to === "/"}
                className={({ isActive }) =>
                  cn(
                    "flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium whitespace-nowrap",
                    isActive ? "bg-white/15 text-white" : "text-white/70 hover:bg-white/10 hover:text-white",
                  )
                }
              >
                <Icon size={16} />
                {link.label}
              </NavLink>
            );
          })}
        </nav>
        <div className="hidden border-t border-white/10 px-5 py-4 lg:block">
          <p className="text-sm text-white">{user?.fullName}</p>
          <p className="text-xs text-white/50">{user?.email}</p>
          <button
            type="button"
            onClick={() => void logout()}
            className="mt-3 inline-flex items-center gap-2 text-sm text-white/70 hover:text-white"
          >
            <LogOut size={14} />
            Déconnexion
          </button>
        </div>
      </aside>
      <main className="px-4 py-6 sm:px-8">
        <Outlet />
      </main>
    </div>
  );
}
