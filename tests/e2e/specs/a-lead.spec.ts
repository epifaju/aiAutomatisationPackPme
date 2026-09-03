import { expect, test } from "@playwright/test";
import { apiGet, loginApi, waitForAudit, webhookPost } from "../helpers/api";
import { gotoNav, loginViaUi } from "../helpers/auth";
import { loadE2eEnv } from "../helpers/env";

/**
 * Scenario A — Lead webhook → création → qualification IA → stockage → audit
 */
test.describe("Scenario A — Leads", () => {
  test("webhook create then UI qualify + audit", async ({ page, request }) => {
    const env = loadE2eEnv();
    const stamp = Date.now();
    const email = `e2e.lead.${stamp}@demo.aipack.example`;
    const fullName = `E2E Lead ${stamp}`;

    const created = await webhookPost(
      request,
      "/webhook/leads/create",
      {
        companyId: env.demoCompanyId,
        fullName,
        email,
        companyName: "E2E Société",
        qualify: false,
      },
      30_000,
    );
    const leadId = created.data.id as string;
    expect(leadId).toBeTruthy();

    const token = await loginApi(request);
    await waitForAudit(request, token, { entityType: "LEAD", entityId: leadId }, 60_000);

    await loginViaUi(page);
    await gotoNav(page, "Leads");
    await expect(page.getByText(fullName)).toBeVisible({ timeout: 30_000 });

    const row = page.locator("tr").filter({ hasText: fullName });
    await row.getByRole("button", { name: "Qualifier" }).click();

    await expect
      .poll(
        async () => {
          const detail = await apiGet(request, `/api/v1/leads/${leadId}`, token);
          return detail.data.aiStatus as string;
        },
        { timeout: env.aiTimeoutMs },
      )
      .toMatch(/COMPLETED|REVIEW_REQUIRED|AI_UNAVAILABLE|AI_PARSING_ERROR/);

    await waitForAudit(
      request,
      token,
      { entityType: "LEAD", entityId: leadId, workflow: "lead-qualification" },
      env.aiTimeoutMs,
    );
  });
});
