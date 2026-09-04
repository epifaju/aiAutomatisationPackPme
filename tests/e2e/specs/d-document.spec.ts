import { expect, test } from "@playwright/test";
import { apiGet, loginApi, waitForAudit } from "../helpers/api";
import { gotoNav, loginViaUi } from "../helpers/auth";
import { loadE2eEnv } from "../helpers/env";

/**
 * Scenario D — Document → extraction → validation → stockage MinIO
 */
test.describe("Scenario D — Documents", () => {
  test("upload invoice text, process, appear with storage+extraction", async ({ page, request }) => {
    const env = loadE2eEnv();
    const stamp = Date.now();
    const fileName = `e2e-facture-${stamp}.txt`;

    await loginViaUi(page);
    await expect(async () => {
      await page.goto("/documents");
      await expect(page.getByRole("heading", { name: "Documents" })).toBeVisible({ timeout: 15_000 });
    }).toPass({ timeout: 60_000 });

    await page.locator('input[type="file"]').setInputFiles({
      name: fileName,
      mimeType: "text/plain",
      buffer: Buffer.from(
        [
          `FACTURE N° E2E-${stamp}`,
          "Fournisseur: Fournisseur E2E SAS",
          "Date: 2026-08-01",
          "Échéance: 2026-08-31",
          "HT: 100.00 EUR",
          "TVA: 20.00 EUR",
          "TTC: 120.00 EUR",
        ].join("\n"),
        "utf8",
      ),
    });
    await page.locator("select").first().selectOption("FACTURE");

    await Promise.all([
      page.waitForResponse(
        (res) => res.url().includes("/api/v1/documents") && res.request().method() === "POST",
        { timeout: env.aiTimeoutMs },
      ),
      page.getByRole("button", { name: /Déposer|Dépôt/ }).click(),
    ]);

    await expect(page.getByText(fileName)).toBeVisible({ timeout: 30_000 });

    const token = await loginApi(request);
    let docId: string | null = null;
    let status = "";
    await expect
      .poll(
        async () => {
          const list = await apiGet(request, "/api/v1/documents?size=20", token);
          const match = (
            list.data.content as Array<{ id: string; originalFilename: string; status: string }>
          ).find((d) => d.originalFilename === fileName);
          if (match) {
            docId = match.id;
            status = match.status;
          }
          return status;
        },
        { timeout: env.aiTimeoutMs },
      )
      .toMatch(/EXTRACTED|REVIEW_REQUIRED|ERROR/);

    expect(docId).toBeTruthy();
    await waitForAudit(request, token, { entityType: "DOCUMENT", entityId: docId! }, 60_000);

    if (status === "REVIEW_REQUIRED") {
      await page.reload();
      await gotoNav(page, "Documents");
      const card = page.locator("section").filter({ hasText: fileName });
      await card.getByRole("button", { name: "Approuver" }).click();
      await expect(card.getByText("EXTRACTED")).toBeVisible({ timeout: 30_000 });
    }
  });
});
