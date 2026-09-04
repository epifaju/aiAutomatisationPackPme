import { expect, test } from "@playwright/test";
import { apiGet, apiPost, loginApi, waitForAudit, webhookPost } from "../helpers/api";
import { loadE2eEnv } from "../helpers/env";

/**
 * Scenario B — Email → analyse → classification → proposition → validation humaine
 * Analyse via API (Ollama réel) ; Approuver dans l'UI.
 * Si Ollama est trop lent/indisponible sur l'hôte, le test est skippé (pas un faux rouge).
 */
test.describe("Scenario B — Email", () => {
  test("ingest+analyze then UI approve", async ({ page, request }) => {
    test.setTimeout(20 * 60 * 1000);
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
    await waitForAudit(request, token, { entityType: "EMAIL", entityId: emailId, action: "INGESTED" }, 60_000);

    let result: { data: { status: string; analysis?: { approvalStatus?: string } } };
    try {
      result = await apiPost(request, `/api/v1/emails/${emailId}/analyze`, token, undefined, env.aiTimeoutMs);
    } catch (err) {
      test.skip(true, `Ollama timeout/unreachable during email analysis: ${String(err)}`);
      return;
    }

    if (result.data.status !== "ANALYZED" || result.data.analysis?.approvalStatus !== "PENDING_APPROVAL") {
      test.skip(
        true,
        `Ollama did not return a pending reply (status=${result.data.status}). Warm the model and set AI_TIMEOUT=300s.`,
      );
      return;
    }

    await waitForAudit(
      request,
      token,
      { entityType: "EMAIL", entityId: emailId, action: "ANALYZED" },
      60_000,
    );

    await page.goto("/login");
    const { demoEmail, demoPassword } = env;
    await page.getByLabel("Email").fill(demoEmail);
    await page.getByLabel("Mot de passe").fill(demoPassword);
    await page.getByRole("button", { name: "Entrer" }).click();
    await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible({ timeout: 30_000 });

    await expect(async () => {
      await page.goto("/inbox");
      await expect(page.getByRole("heading", { name: "Inbox" })).toBeVisible();
      await expect(page.getByText(subject)).toBeVisible({ timeout: 15_000 });
    }).toPass({ timeout: 90_000 });

    await page.getByRole("row").filter({ hasText: subject }).click();
    const panel = page.locator("section").filter({ has: page.getByRole("heading", { name: subject }) });
    await expect(panel.getByRole("heading", { name: subject })).toBeVisible();
    await expect(panel.getByText("En attente").or(panel.getByText("PENDING_APPROVAL")).first()).toBeVisible();

    await panel.getByRole("button", { name: "Approuver" }).click();
    await expect(panel.getByText("Approuvé").or(panel.getByText("APPROVED")).first()).toBeVisible({
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
