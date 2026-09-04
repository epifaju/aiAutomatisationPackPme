import type { Page } from "@playwright/test";
import { expect } from "@playwright/test";
import { loadE2eEnv } from "./env";

export async function loginViaUi(page: Page) {
  const { demoEmail, demoPassword } = loadE2eEnv();
  await expect(async () => {
    await page.goto("/login");
    await page.getByLabel("Email").fill(demoEmail);
    await page.getByLabel("Mot de passe").fill(demoPassword);
    await page.getByRole("button", { name: "Entrer" }).click();
    await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible({ timeout: 20_000 });
  }).toPass({ timeout: 90_000 });
}

export async function gotoNav(page: Page, name: string) {
  await page.getByRole("link", { name }).click();
}
