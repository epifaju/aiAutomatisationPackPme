import { expect, test } from "@playwright/test";
import { apiGet, apiPost, loginApi, mailpitMessages, waitForAudit } from "../helpers/api";
import { gotoNav, loginViaUi } from "../helpers/auth";

/**
 * Scenario C — Facture dépassée → détection → relance → Mailpit → audit
 */
test.describe("Scenario C — Invoices", () => {
  test("create overdue invoice, detect, approve, send to Mailpit", async ({ page, request }) => {
    const token = await loginApi(request);
    const stamp = Date.now();
    const invoiceNumber = `E2E-${stamp}`;

    const customers = await apiGet(request, "/api/v1/customers?size=5", token);
    const customerId = customers.data.content[0]?.id as string;
    expect(customerId).toBeTruthy();

    const due = new Date();
    due.setUTCDate(due.getUTCDate() - 10);
    const dueDate = due.toISOString().slice(0, 10);
    const invoiceDateObj = new Date(due);
    invoiceDateObj.setUTCDate(invoiceDateObj.getUTCDate() - 20);
    const invoiceDate = invoiceDateObj.toISOString().slice(0, 10);

    const created = await apiPost(request, "/api/v1/invoices", token, {
      customerId,
      invoiceNumber,
      invoiceDate,
      dueDate,
      amountExcludingTax: 100,
      vat: 20,
      amountIncludingTax: 120,
      currency: "EUR",
      status: "SENT",
    });
    const invoiceId = created.data.id as string;
    expect(created.data.invoiceNumber).toBe(invoiceNumber);

    await loginViaUi(page);
    await gotoNav(page, "Invoices");
    await page.reload();
    await gotoNav(page, "Invoices");
    await expect(page.getByText(invoiceNumber)).toBeVisible({ timeout: 30_000 });

    await page.getByRole("button", { name: "Détecter les retards" }).click();

    await expect
      .poll(
        async () => {
          const detail = await apiGet(request, `/api/v1/invoices/${invoiceId}`, token);
          return {
            status: detail.data.status as string,
            reminders: (detail.data.reminders ?? []).length as number,
          };
        },
        { timeout: 60_000 },
      )
      .toMatchObject({ status: "OVERDUE" });

    await page.reload();
    await gotoNav(page, "Invoices");
    const card = page.locator("section").filter({ hasText: invoiceNumber });
    await expect(card.getByText("OVERDUE")).toBeVisible({ timeout: 30_000 });
    await expect(card.getByText(/Relance J\+/).first()).toBeVisible();

    const reminderRow = card.locator("li").filter({ hasText: "Relance J+3" });
    await reminderRow.getByRole("button", { name: "Approuver" }).click();
    await expect(reminderRow.getByText("APPROVED")).toBeVisible({ timeout: 30_000 });

    await reminderRow.getByRole("button", { name: "Envoyer" }).click();
    await expect(reminderRow.getByText("EXECUTED")).toBeVisible({ timeout: 30_000 });

    await expect
      .poll(
        async () => {
          const mail = await mailpitMessages(request);
          const messages = (mail.messages ?? []) as Array<{ Subject?: string }>;
          return messages.filter((m) => (m.Subject ?? "").includes(invoiceNumber)).length;
        },
        { timeout: 60_000 },
      )
      .toBeGreaterThan(0);

    await waitForAudit(request, token, { entityType: "INVOICE", entityId: invoiceId }, 60_000);
  });
});
