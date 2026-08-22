import { test, expect } from '@playwright/test';

const email = process.env.E2E_EMAIL;
const password = process.env.E2E_PASSWORD;

test.describe('Iteration 3 report journeys', () => {
  test.beforeEach(async () => {
    test.skip(!email || !password, 'Set E2E_EMAIL and E2E_PASSWORD for an authorized report journey.');
  });

  test('authorized Intern can open the attendance report and export controls', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Password').fill(password);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await page.goto('/reports/attendance');
    await expect(page.getByRole('heading', { name: 'Attendance report' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Download XLSX' })).toHaveAttribute('href', /attendance\.xlsx/);
    await expect(page.getByRole('link', { name: 'Download PDF' })).toHaveAttribute('href', /attendance\.pdf/);
  });
});
