import { test, expect } from '@playwright/test';

test('@smoke bootstrap page serves the local shell and styles', async ({ page }) => {
  const response = await page.goto('/bootstrap');
  expect(response?.status()).toBeLessThan(400);
  await expect(page).toHaveTitle(/Create the first administrator/);
  await expect(page.getByLabel('Email')).toBeVisible();
  await expect(page.getByLabel('Password')).toBeVisible();
  await expect(page.locator('link[rel="stylesheet"][href*="/assets/app.css"]')).toHaveCount(1);
});

test('@smoke narrow viewport keeps the bootstrap form inside the viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const response = await page.goto('/bootstrap');
  expect(response?.status()).toBeLessThan(400);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});
