import { expect, test } from "@playwright/test";
import { analyzeReady, strictAi, warmOllama } from "../helpers/ai";
import { apiGet, apiPost, loginApi, mailpitMessages, waitForAudit, webhookPost } from "../helpers/api";
import { loadE2eEnv } from "../helpers/env";

/**
 * Scenario B — Email → analyse → classification → proposition → Approuver → Envoyer → Mailpit
 *
 * Localement : skip si Ollama KO (machine CPU variable).
 * En CI (ou E2E_STRICT_AI=1) : échec dur — le warm global + llama3.2:1b doivent suffire.
 */
test.describe("Scenario B — Email", () => {
  test("ingest+analyze then UI approve+send to Mailpit", async ({ page, request }) => {
    test.setTimeout(20 * 60 * 1000);
    const env = loadE2eEnv();
    const stamp = Date.now();
    const messageId = `e2e-email-${stamp}@aipack.example`;
    const subject = `E2E devis ${stamp}`;

    // Warm again right before the dual LLM call (classification + reply).
    try {
      await warmOllama(180_000);
    } catch (err) {
      if (strictAi()) throw err;
      test.skip(true, `Ollama warm failed: ${String(err)}`);
      return;
    }

    const ingested = await webhookPost(
      request,
      "/webhook/email/incoming",
      {
        companyId: env.demoCompanyId,
        messageId,
        fromAddress: `client.e2e.${stamp}@demo.aipack.example`,
        toAddress: "inbox@demo.aipack.example",
        subject,
        bodyText:
          "Bonjour, merci de nous envoyer un devis pour automatiser nos relances clients. Nous sommes une TPE de 12 personnes.",
        analyze: false,
      },
      30_000,
    );
    const emailId = ingested.data.id as string;
    expect(ingested.data.status).toBe("RECEIVED");

    const token = await loginApi(request);
    await waitForAudit(request, token, { entityType: "EMAIL", entityId: emailId, action: "INGESTED" }, 60_000);

    let result: {
      data: {
        status: string;
        analysis?: { approvalStatus?: string | null; category?: string | null; suggestedReply?: string | null };
      };
    };
    try {
      result = await apiPost(request, `/api/v1/emails/${emailId}/analyze`, token, undefined, env.aiTimeoutMs);
      // One retry if first response is soft-failure (circuit / cold start).
      if (!analyzeReady(result.data)) {
        await warmOllama(120_000);
        result = await apiPost(request, `/api/v1/emails/${emailId}/analyze`, token, undefined, env.aiTimeoutMs);
      }
    } catch (err) {
      if (strictAi()) throw err;
      test.skip(true, `Ollama timeout/unreachable during email analysis: ${String(err)}`);
      return;
    }

    if (!analyzeReady(result.data)) {
      const msg = `Ollama did not return a pending reply (status=${result.data.status}, approval=${result.data.analysis?.approvalStatus}).`;
      if (strictAi()) throw new Error(msg);
      test.skip(true, `${msg} Warm the model and check AI_TIMEOUT / OLLAMA_MODEL=llama3.2:1b.`);
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

    const afterApprove = await apiGet(request, `/api/v1/emails/${emailId}`, token);
    expect(afterApprove.data.analysis.approvalStatus).toBe("APPROVED");

    await waitForAudit(
      request,
      token,
      { entityType: "EMAIL", entityId: emailId, action: "APPROVED" },
      60_000,
    );

    const mailBefore = await mailpitMessages(request);
    const totalBefore = Number(mailBefore.total ?? 0);

    await panel.getByRole("button", { name: "Envoyer" }).click();
    await expect(panel.getByText("Envoyé").or(panel.getByText("EXECUTED")).first()).toBeVisible({
      timeout: 30_000,
    });

    const afterSend = await apiGet(request, `/api/v1/emails/${emailId}`, token);
    expect(afterSend.data.analysis.approvalStatus).toBe("EXECUTED");

    await expect
      .poll(
        async () => {
          const mail = await mailpitMessages(request);
          return Number(mail.total ?? 0);
        },
        { timeout: 30_000 },
      )
      .toBeGreaterThan(totalBefore);

    await waitForAudit(request, token, { entityType: "EMAIL", entityId: emailId, action: "SENT" }, 60_000);
  });
});
