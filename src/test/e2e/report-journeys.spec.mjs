import { test, expect } from '@playwright/test';
import { readFile } from 'node:fs/promises';

const email = process.env.E2E_EMAIL;
const password = process.env.E2E_PASSWORD;

test.describe('Iteration 3 report journeys', () => {
  test.beforeEach(async () => {
    test.skip(!email || !password, 'Set E2E_EMAIL and E2E_PASSWORD for an authorized report journey.');
  });

  test('authorized Intern can download attendance XLSX and PDF reports', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Password').fill(password);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await page.goto('/reports/attendance?from=2026-08-01&to=2026-08-31');
    await expect(page.getByRole('heading', { name: 'Attendance report' })).toBeVisible();

    await assertDownload(page, 'Download XLSX', '/reports/attendance.xlsx',
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      /^attendance-report-2026-08-01-to-2026-08-31\.xlsx$/, (bytes) => {
        expect(bytes.subarray(0, 4).toString()).toBe('PK\u0003\u0004');
        expect(bytes.includes(Buffer.from('xl/worksheets/sheet1.xml'))).toBe(true);
      });
    await assertDownload(page, 'Download PDF', '/reports/attendance.pdf', 'application/pdf',
      /^attendance-report-2026-08-01-to-2026-08-31\.pdf$/, (bytes) => {
        expect(bytes.subarray(0, 4).toString()).toBe('%PDF');
      });
  });
});

async function assertDownload(page, linkName, route, mediaType, filename, inspect) {
  const responsePromise = page.waitForResponse((response) =>
    response.url().includes(route) && response.request().method() === 'GET');
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('link', { name: linkName }).click();
  const [response, download] = await Promise.all([responsePromise, downloadPromise]);

  expect(response.status()).toBe(200);
  expect(response.headers()['content-type']).toContain(mediaType);
  expect(response.headers()['content-disposition']).toMatch(/attachment/i);
  expect(download.failure()).resolves.toBeNull();
  expect(download.suggestedFilename()).toMatch(filename);

  const path = await download.path();
  if (!path) throw new Error(`${linkName} did not expose a local download path`);
  inspect(await readFile(path));
}
