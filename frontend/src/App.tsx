import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { AppShell } from "@/components/app-shell";
import { ProtectedRoute } from "@/components/protected-route";
import { AuditPage } from "@/pages/audit-page";
import { AutomationsPage } from "@/pages/automations-page";
import { DashboardPage } from "@/pages/dashboard-page";
import { DocumentsPage } from "@/pages/documents-page";
import { InboxPage } from "@/pages/inbox-page";
import { InvoicesPage } from "@/pages/invoices-page";
import { LeadsPage } from "@/pages/leads-page";
import { LoginPage } from "@/pages/login-page";
import { SettingsPage } from "@/pages/settings-page";

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
});

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<ProtectedRoute />}>
            <Route element={<AppShell />}>
              <Route path="/" element={<DashboardPage />} />
              <Route path="/inbox" element={<InboxPage />} />
              <Route path="/leads" element={<LeadsPage />} />
              <Route path="/documents" element={<DocumentsPage />} />
              <Route path="/invoices" element={<InvoicesPage />} />
              <Route path="/automations" element={<AutomationsPage />} />
              <Route path="/audit" element={<AuditPage />} />
              <Route path="/settings" element={<SettingsPage />} />
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
