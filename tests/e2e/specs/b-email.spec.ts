import { expect, test } from "@playwright/test";
import { apiGet, apiPost, loginApi, waitForAudit, webhookPost } from "../helpers/api";
import { gotoNav, loginViaUi } from "../helpers/auth";
import { loadE2eEnv } from "../helpers/env";

/**
 * Scenario B — Email → analyse → classification → proposition → validation humaine
 * Analyse via API (Ollama réel, timeout long) ; Approuver dans l'UI.
 */
test.describe("Scenario B — Email", () => {
  test("ingest+analyze then UI approve", async ({ page, request }) => {
    const env = loadE2eEnv();
    const stamp = Date.now();
    const messageId = `e2e-email-${stamp}@aipack.example`;
    const subject = `E2E devis ${stamp}`;

    const ingested = await webhookPost(
      request,
      "/webhook/email/incoming",
      {
        companyId: env.demoCompanyId,
        messageId,
        fromAddress: `client.e2e.${stamp}@demo.aipack.example`,
        toAddress: "inbox@demo.aipack.example",
        subject,
        bodyText: "Bonjour, merci de nous envoyer un devis pour automatiser nos relances clients.",
        analyze: false,
      },
      30_000,
    );
    const emailId = ingested.data.id as string;
    expect(ingested.data.status).toBe("RECEIVED");

    const token = await loginApi(request);

    let analyzed = false;
    for (let attempt = 1; attempt <= 3 && !analyzed; attempt++) {
      const result = await apiPost(
        request,
        `/api/v1/emails/${emailId}/analyze`,
        token,
        undefined,
        env.aiTimeoutMs,
      );
      if (result.data.status === "ANALYZED" && result.data.analysis?.approvalStatus === "PENDING_APPROVAL") {
        analyzed = true;
        break;
      }
      if (attempt === 3) {
        throw new Error(
          `Email analysis failed after ${attempt} attempts: status=${result.data.status} (Ollama?)`,
        );
      }
    }

    await waitForAudit(
      request,
      token,
      { entityType: "EMAIL", entityId: emailId, action: "ANALYZED" },
      60_000,
    );

    await loginViaUi(page);
    await expect(async () => {
      await page.goto("/inbox");
      await expect(page.getByRole("heading", { name: "Inbox" })).toBeVisible();
      await expect(page.getByText(subject)).toBeVisible({ timeout: 15_000 });
    }).toPass({ timeout: 90_000 });

    await page.getByRole("row").filter({ hasText: subject }).click();
    await expect(page.getByRole("heading", { name: subject })).toBeVisible();
    await expect(page.getByText("PENDING_APPROVAL").or(page.getByText("En attente"))).toBeVisible();

    await page.getByRole("button", { name: "Approuver" }).click();
    await expect(page.getByText("APPROVED").or(page.getByText("Approuvé"))).toBeVisible({
      timeout: 30_000,
    });

    const detail = await apiGet(request, `/api/v1/emails/${emailId}`, token);
    expect(detail.data.analysis.approvalStatus).toBe("APPROVED");

    await waitForAudit(
      request,
      token,
      { entityType: "EMAIL", entityId: emailId, action: "APPROVED" },
      60_000,
    );
  });
});
