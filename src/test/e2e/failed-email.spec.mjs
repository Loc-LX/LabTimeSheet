import { test, expect } from '@playwright/test';
import { execFileSync } from 'node:child_process';

// Serial: bootstrap admin once, then run the failed-email inspection and retry flow.
test.describe.configure({ mode: 'serial' });

/**
 * End-to-end browser coverage for NOT-007 (Admin failed-email inspection and manual retry).
 *
 * Covers AC-NOT-002 and AC-NOT-007:
 *   1. System bootstraps first administrator.
 *   2. Seeds a designated FAILED ordinary email via E2E_DB_CONTAINER.
 *   3. Admin opens the "Failed email" menu and sees the failed email in the list.
 *   4. Admin clicks "Retry", sees flash message "Email retry started.", and the row disappears from the list.
 *   5. DB checks verify the row is no longer FAILED and total notification rows remain unchanged.
 *   6. Unauthenticated requests to /admin/notifications/failed-email redirect to /login.
 */
test('NOT-007 Admin failed-email inspection and retry browser flow with database verification', async ({ page, browser }) => {
  test.setTimeout(120_000);
  const container = process.env.E2E_DB_CONTAINER;
  test.skip(!container, 'Set E2E_DB_CONTAINER to the disposable end-to-end PostgreSQL container; this journey seeds its precondition there.');

  const stamp = Date.now();
  const admin = { email: `failed-admin-${stamp}@e2e.test`, displayName: `FE Admin ${stamp}`, password: 'AdminPass!2026' };

  // 1. Bootstrap first administrator
  await page.goto('/bootstrap');
  if (await page.getByRole('heading', { name: 'Create the first administrator' }).isVisible()) {
    await page.getByLabel('Email').fill(admin.email);
    await page.getByLabel('Display name').fill(admin.displayName);
    await page.getByLabel('Password').fill(admin.password);
    await page.getByRole('button', { name: 'Create administrator' }).click();
    await expect(page).toHaveURL(/\/login|\/dashboard/);
  } else {
    const envEmail = process.env.E2E_ADMIN_EMAIL;
    const envPass = process.env.E2E_ADMIN_PASSWORD;
    test.skip(!envEmail || !envPass, 'Bootstrap already done; provide E2E_ADMIN_EMAIL/E2E_ADMIN_PASSWORD.');
    admin.email = envEmail;
    admin.password = envPass;
  }

  // 2. Seed a FAILED ordinary email in notifications table
  const failedRecipient = `failed-${stamp}@e2e.test`;
  const failedTitle = `Failed E2E Notice ${stamp}`;
  const seedSql = `
    INSERT INTO notifications (
      recipient_user_id, notification_type, title, body, action_url, email_status,
      email_to, email_subject, email_body, email_attempts, email_last_error, created_at, updated_at, version
    )
    SELECT u.id, 'PROJECT_INVITATION_CREATED', '${failedTitle}', 'E2E notification body', '/projects/1', 'FAILED',
           '${failedRecipient}', 'E2E Subject', 'E2E Email Body', 6, 'ConnectException', NOW(), NOW(), 0
    FROM app_users u WHERE u.email = '${admin.email}'
    RETURNING id;
  `;
  const failedIdStr = execSql(container, seedSql);
  const failedId = Number.parseInt(failedIdStr.trim(), 10);
  expect(failedId).toBeGreaterThan(0);

  const initialCountStr = execSql(container, 'SELECT count(*) FROM notifications;');
  const initialCount = Number.parseInt(initialCountStr.trim(), 10);

  // 3. Sign in as Admin
  await signIn(page, admin);

  // 4. Open "Failed email" from sidebar navigation
  await page.goto('/dashboard');
  const failedEmailLink = page.getByRole('link', { name: 'Failed email', exact: true });
  await expect(failedEmailLink).toBeVisible();
  await failedEmailLink.click();
  await expect(page).toHaveURL(/\/admin\/notifications\/failed-email/);
  await expect(page.getByRole('heading', { name: 'Failed email', exact: true })).toBeVisible();

  // 5. Verify the seeded failed email is visible in the table
  const failedRow = page.locator('tbody tr').filter({ hasText: failedRecipient });
  await expect(failedRow).toBeVisible();
  await expect(failedRow.getByText(failedTitle)).toBeVisible();
  await expect(failedRow.getByText('ConnectException')).toBeVisible();

  // 6. Click "Retry", see flash message, and confirm row disappears from list
  await failedRow.getByRole('button', { name: 'Retry' }).click();
  await expect(page).toHaveURL(/\/admin\/notifications\/failed-email/);
  await expect(page.getByText('Email retry started.', { exact: true })).toBeVisible();
  await expect(page.locator('tbody tr').filter({ hasText: failedRecipient })).toHaveCount(0);

  // 7. Verify database: row is no longer FAILED, and total row count is unchanged
  const currentStatus = execSql(container, `SELECT email_status FROM notifications WHERE id = ${failedId};`);
  expect(currentStatus.trim()).not.toBe('FAILED');

  const finalCountStr = execSql(container, 'SELECT count(*) FROM notifications;');
  const finalCount = Number.parseInt(finalCountStr.trim(), 10);
  expect(finalCount).toBe(initialCount);

  // 8. Verify unauthenticated access redirects to /login
  const unauthContext = await browser.newContext();
  const unauthPage = await unauthContext.newPage();
  await unauthPage.goto('/admin/notifications/failed-email');
  await expect(unauthPage).toHaveURL(/\/login/);
  await unauthContext.close();
});

// ── helpers ──────────────────────────────────────────────────────────────────

function execSql(container, sql) {
  return execFileSync('docker', [
    'exec', '-i', container,
    'psql', '-U', process.env.E2E_DB_USER || 'labtimesheet', '-d', process.env.E2E_DB_NAME || 'labtimesheet',
    '-t', '-A', '-c', sql,
  ], { encoding: 'utf8' }).trim();
}

async function signIn(page, acct) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(acct.email);
  await page.getByLabel('Password').fill(acct.password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/dashboard|\/admin\/smtp/, { timeout: 15_000 });
}
