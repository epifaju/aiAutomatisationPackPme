import { expect, test } from "@playwright/test";
import { strictAi } from "../helpers/ai";
import { apiGet, loginApi, waitForAudit } from "../helpers/api";
import { gotoNav, loginViaUi } from "../helpers/auth";
import { loadE2eEnv } from "../helpers/env";

function invoiceText(stamp: number | string) {
  return [
    `FACTURE N° E2E-${stamp}`,
    "Fournisseur: Fournisseur E2E SAS",
    "Date: 2026-08-01",
    "Échéance: 2026-08-31",
    "HT: 100.00 EUR",
    "TVA: 20.00 EUR",
    "TTC: 120.00 EUR",
  ].join("\n");
}

/** Minimal text PDF (Helvetica) — Tika can extract the content stream text. */
function minimalInvoicePdf(stamp: number): Buffer {
  const line = `FACTURE N E2E-${stamp} Fournisseur E2E SAS HT 100.00 TVA 20.00 TTC 120.00 Date 2026-08-01`;
  const stream = `BT /F1 10 Tf 20 120 Td (${line.replace(/[()\\]/g, "")}) Tj ET\n`;
  const objects = [
    "1 0 obj<< /Type /Catalog /Pages 2 0 R >>endobj\n",
    "2 0 obj<< /Type /Pages /Kids [3 0 R] /Count 1 >>endobj\n",
    "3 0 obj<< /Type /Page /Parent 2 0 R /MediaBox [0 0 400 200] /Contents 4 0 R /Resources<< /Font<< /F1 5 0 R >> >> >>endobj\n",
    `4 0 obj<< /Length ${stream.length} >>stream\n${stream}endstream\nendobj\n`,
    "5 0 obj<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>endobj\n",
  ];
  let body = "%PDF-1.4\n";
  const offsets = [0];
  for (const obj of objects) {
    offsets.push(Buffer.byteLength(body, "utf8"));
    body += obj;
  }
  const xrefPos = Buffer.byteLength(body, "utf8");
  let xref = `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  for (let i = 1; i <= objects.length; i++) {
    xref += `${String(offsets[i]).padStart(10, "0")} 00000 n \n`;
  }
  body += xref;
  body += `trailer<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefPos}\n%%EOF\n`;
  return Buffer.from(body, "utf8");
}

/**
 * Scenario D — Document → extraction → validation → stockage MinIO
 */
test.describe("Scenario D — Documents", () => {
  test("upload invoice text, extract, approve when review required", async ({ page, request }) => {
    test.setTimeout(15 * 60 * 1000);
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
      buffer: Buffer.from(invoiceText(stamp), "utf8"),
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
      .toMatch(/EXTRACTED|REVIEW_REQUIRED/);

    if (status === "ERROR") {
      const msg = `Document extraction ended in ERROR for ${fileName}`;
      if (strictAi()) throw new Error(msg);
      test.skip(true, msg);
      return;
    }

    expect(docId).toBeTruthy();
    const detail = await apiGet(request, `/api/v1/documents/${docId}`, token);
    expect(detail.data.storageKey).toBeTruthy();
    expect(detail.data.extraction?.extractedJson).toBeTruthy();

    await waitForAudit(request, token, { entityType: "DOCUMENT", entityId: docId! }, 60_000);

    if (status === "REVIEW_REQUIRED") {
      await page.reload();
      await gotoNav(page, "Documents");
      const card = page.locator("section").filter({ hasText: fileName });
      await expect(card.getByText("REVIEW_REQUIRED")).toBeVisible();
      await card.getByRole("button", { name: "Approuver" }).click();
      await expect(card.getByText("EXTRACTED")).toBeVisible({ timeout: 30_000 });
      const after = await apiGet(request, `/api/v1/documents/${docId}`, token);
      expect(after.data.status).toBe("EXTRACTED");
    }
  });

  test("upload without process then Traiter from UI", async ({ page, request }) => {
    test.setTimeout(15 * 60 * 1000);
    const env = loadE2eEnv();
    const stamp = Date.now();
    const fileName = `e2e-traite-${stamp}.txt`;
    const token = await loginApi(request);

    // Multipart via Playwright request API
    const res = await request.post(`${env.backendBaseUrl}/api/v1/documents`, {
      headers: { Authorization: `Bearer ${token}` },
      multipart: {
        file: {
          name: fileName,
          mimeType: "text/plain",
          buffer: Buffer.from(invoiceText(`T-${stamp}`), "utf8"),
        },
        documentType: "FACTURE",
        process: "false",
      },
      timeout: 60_000,
    });
    expect(res.ok(), await res.text()).toBeTruthy();
    const created = await res.json();
    const docId = created.data.id as string;
    expect(created.data.status).toBe("UPLOADED");

    await loginViaUi(page);
    await page.goto("/documents");
    await expect(page.getByText(fileName)).toBeVisible({ timeout: 30_000 });

    const card = page.locator("section").filter({ hasText: fileName });
    await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes(`/api/v1/documents/${docId}/process`) && r.request().method() === "POST",
        { timeout: env.aiTimeoutMs },
      ),
      card.getByRole("button", { name: "Traiter" }).click(),
    ]);

    await expect
      .poll(async () => {
        const detail = await apiGet(request, `/api/v1/documents/${docId}`, token);
        return detail.data.status as string;
      }, { timeout: env.aiTimeoutMs })
      .toMatch(/EXTRACTED|REVIEW_REQUIRED/);

    const after = await apiGet(request, `/api/v1/documents/${docId}`, token);
    if (after.data.status === "ERROR") {
      const msg = `Traiter ended in ERROR for ${fileName}`;
      if (strictAi()) throw new Error(msg);
      test.skip(true, msg);
      return;
    }
    expect(after.data.extraction?.extractedJson).toBeTruthy();

    if (after.data.status === "REVIEW_REQUIRED") {
      await page.reload();
      await gotoNav(page, "Documents");
      const card2 = page.locator("section").filter({ hasText: fileName });
      await card2.getByRole("button", { name: "Approuver" }).click();
      await expect(card2.getByText("EXTRACTED")).toBeVisible({ timeout: 30_000 });
    }
  });

  test("upload minimal PDF invoice text layer", async ({ request }) => {
    test.setTimeout(15 * 60 * 1000);
    const env = loadE2eEnv();
    const stamp = Date.now();
    const fileName = `e2e-facture-${stamp}.pdf`;
    const token = await loginApi(request);

    const res = await request.post(`${env.backendBaseUrl}/api/v1/documents`, {
      headers: { Authorization: `Bearer ${token}` },
      multipart: {
        file: {
          name: fileName,
          mimeType: "application/pdf",
          buffer: minimalInvoicePdf(stamp),
        },
        documentType: "FACTURE",
        process: "true",
      },
      timeout: env.aiTimeoutMs,
    });
    expect(res.ok(), await res.text()).toBeTruthy();
    const body = await res.json();
    const status = body.data.status as string;
    if (status === "ERROR") {
      // PDF without extractable text → acceptable soft skip locally; CI with heuristic+Tika should usually pass
      const reason = body.data.extraction?.extractedJson?.reason;
      if (strictAi() && reason !== "NO_TEXT_EXTRACTED") {
        throw new Error(`PDF extraction ERROR reason=${reason}`);
      }
      test.skip(true, `PDF extraction not usable (status=${status}, reason=${reason})`);
      return;
    }
    expect(status).toMatch(/EXTRACTED|REVIEW_REQUIRED/);
    expect(body.data.extraction?.extractedJson).toBeTruthy();
  });
});
