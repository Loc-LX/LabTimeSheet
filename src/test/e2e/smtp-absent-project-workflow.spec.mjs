import { test, expect } from '@playwright/test';
import { execFileSync } from 'node:child_process';

/**
 * Exercises the invitation and voluntary exit workflow with no active SMTP.
 * Protects NOT-002, NOT-004, NOT-005, NOT-010, AC-NOT-004 and AC-NOT-007:
 * the domain actions complete, required recipients receive in-app notices, and
 * the SMTP-unavailable state remains terminal without retroactive delivery.
 */
test('SMTP-absent invitation and member exit remain in-app and complete', async ({ page }) => {
  test.setTimeout(120_000);
  const container = process.env.E2E_DB_CONTAINER;
  test.skip(!container, 'Set E2E_DB_CONTAINER to the disposable seeded E2E PostgreSQL container.');

  const projectId = Number(sql(container, `
    SELECT id FROM projects WHERE name = 'Lab Timesheet Platform';
  `));
  expect(projectId).toBeGreaterThan(0);
  const baselineNotificationId = Number(sql(container, 'SELECT coalesce(max(id), 0) FROM notifications;'));
  expect(Number(sql(container, "SELECT count(*) FROM smtp_configurations WHERE status = 'ACTIVE';"))).toBe(0);

  await signIn(page, 'intern1@example.com');
  await page.goto(`/projects/${projectId}/workflows`);
  await expect(page.getByRole('heading', { name: 'Project Workflows' })).toBeVisible();
  await page.getByRole('button', { name: 'Choose eligible Interns' }).click();
  const inviteeOption = page.locator('[data-picker-option]').filter({ hasText: 'Khanh Vo' });
  await expect(inviteeOption).toBeVisible();
  await inviteeOption.locator('input[type="checkbox"]').check();
  await page.getByRole('button', { name: 'Done' }).click();
  const inviteForm = page.locator(`form[action="/projects/${projectId}/invitations"]`);
  await expect(inviteForm).toBeVisible();
  await inviteForm.getByRole('button', { name: 'Issue invitations' }).click();
  await expect(page.getByText('Project workflow updated', { exact: true })).toBeVisible();
  expect(sql(container, "SELECT count(*) FROM smtp_configurations WHERE status = 'ACTIVE';")).toBe('0');

  await signIn(page, 'intern6@example.com');
  await page.goto('/projects/invitations');
  const invitationRow = page.locator('tbody tr').filter({ hasText: 'Lab Timesheet Platform' });
  await expect(invitationRow).toBeVisible();
  await invitationRow.getByRole('button', { name: 'Accept' }).click();
  await expect(page.getByText('Invitation response saved', { exact: true })).toBeVisible();
  expect(sql(container, "SELECT count(*) FROM smtp_configurations WHERE status = 'ACTIVE';")).toBe('0');

  await page.goto(`/projects/${projectId}/workflows`);
  await page.getByLabel('Reason').fill('Requesting to leave the project.');
  await page.getByRole('button', { name: 'Request Project exit' }).click();
  await expect(page.getByText('Project workflow updated', { exact: true })).toBeVisible();
  expect(sql(container, "SELECT count(*) FROM smtp_configurations WHERE status = 'ACTIVE';")).toBe('0');

  await signIn(page, 'mentor1@example.com');
  await page.goto(`/projects/${projectId}/workflows`);
  const exitRow = page.locator('.workflow-request-row').filter({ hasText: 'Khanh Vo' });
  await expect(exitRow).toBeVisible();
  page.on('dialog', dialog => dialog.accept());
  await exitRow.getByRole('button', { name: 'Approve exit' }).click();
  await expect(page.getByText('Project workflow updated', { exact: true })).toBeVisible();

  const invitationId = sql(container, `
    SELECT id FROM project_invitations
    WHERE project_id = ${projectId}
      AND invited_intern_user_id = (SELECT id FROM app_users WHERE email = 'intern6@example.com')
      AND status = 'ACCEPTED'
    ORDER BY id DESC LIMIT 1;
  `);
  expect(invitationId).not.toBe('');
  const exitState = sql(container, `
    SELECT status FROM project_membership_exit_requests
    WHERE project_id = ${projectId}
      AND requester_membership_id = (
        SELECT id FROM project_memberships
        WHERE project_id = ${projectId}
          AND intern_user_id = (SELECT id FROM app_users WHERE email = 'intern6@example.com')
        ORDER BY id DESC LIMIT 1
      )
    ORDER BY id DESC LIMIT 1;
  `);
  expect(exitState).toBe('APPROVED');

  const smtpActive = sql(container, "SELECT count(*) FROM smtp_configurations WHERE status = 'ACTIVE';");
  expect(smtpActive).toBe('0');
  const expectedTypes = [
    'PROJECT_INVITATION_CREATED', 'PROJECT_INVITATION_RESOLVED',
    'MEMBERSHIP_EXIT_REQUESTED', 'MEMBERSHIP_EXIT_RESOLVED',
  ];
  for (const type of expectedTypes) {
    const count = Number(sql(container, `
      SELECT count(*) FROM notifications
      WHERE id > ${baselineNotificationId} AND notification_type = '${type}' AND email_status = 'UNAVAILABLE';
    `));
    expect(count, `${type} must have an UNAVAILABLE row`).toBeGreaterThan(0);
  }
  expect(Number(sql(container, `
    SELECT count(*) FROM notifications
    WHERE id > ${baselineNotificationId}
      AND notification_type IN ('PROJECT_INVITATION_CREATED','PROJECT_INVITATION_RESOLVED',
                                'MEMBERSHIP_EXIT_REQUESTED','MEMBERSHIP_EXIT_RESOLVED')
      AND email_status <> 'UNAVAILABLE';
  `))).toBe(0);
  const recipientRows = sql(container, `
    SELECT notification_type || ':' || recipient_user_id
    FROM notifications
    WHERE id > ${baselineNotificationId}
      AND notification_type IN ('PROJECT_INVITATION_CREATED','PROJECT_INVITATION_RESOLVED',
                                'MEMBERSHIP_EXIT_REQUESTED','MEMBERSHIP_EXIT_RESOLVED')
    ORDER BY notification_type, recipient_user_id;
  `).split('\n').filter(Boolean);
  const ids = sql(container, `
    SELECT email || ':' || id FROM app_users
    WHERE email IN ('mentor1@example.com','intern1@example.com','intern6@example.com')
    ORDER BY email;
  `).split('\n').filter(Boolean);
  const userId = Object.fromEntries(ids.map(row => row.split(':')));
  const expectedRecipients = [
    ['PROJECT_INVITATION_CREATED', userId['intern6@example.com']],
    ['PROJECT_INVITATION_RESOLVED', userId['intern1@example.com']],
    ['PROJECT_INVITATION_RESOLVED', userId['mentor1@example.com']],
    ['MEMBERSHIP_EXIT_REQUESTED', userId['intern1@example.com']],
    ['MEMBERSHIP_EXIT_REQUESTED', userId['mentor1@example.com']],
    ['MEMBERSHIP_EXIT_RESOLVED', userId['intern1@example.com']],
    ['MEMBERSHIP_EXIT_RESOLVED', userId['intern6@example.com']],
  ].map(([type, recipient]) => `${type}:${recipient}`);
  expect(recipientRows.sort()).toEqual(expectedRecipients.sort());

  await signIn(page, 'intern6@example.com');
  await page.goto('/notifications');
  await expect(page.getByText('Membership exit request updated', { exact: true })).toBeVisible();
  const remainingActiveSmtp = sql(container, "SELECT count(*) FROM smtp_configurations WHERE status = 'ACTIVE';");
  expect(remainingActiveSmtp).toBe('0');
});

function sql(container, query) {
  return execFileSync('docker', [
    'exec', '-i', container, 'psql', '-U', process.env.E2E_DB_USER || 'labtimesheet',
    '-d', process.env.E2E_DB_NAME || 'labtimesheet', '-t', '-A', '-c', query,
  ], { encoding: 'utf8' }).trim();
}

async function signIn(page, email) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill('DemoPassword123!');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/dashboard|\/admin\/smtp/, { timeout: 15_000 });
}
