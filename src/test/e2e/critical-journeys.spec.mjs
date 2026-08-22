import { test, expect } from '@playwright/test';

test.describe.configure({ mode: 'serial' });

test('Iteration 3 setup and critical Admin/Intern/Mentor journeys', async ({ page, request }, testInfo) => {
  test.setTimeout(120_000);
  const stamp = Date.now();
  const admin = account(`admin-${stamp}@e2e.test`, `E2E Admin ${stamp}`, 'AdminPass!2026');
  const mentor = account(`mentor-${stamp}@e2e.test`, `E2E Mentor ${stamp}`, 'MentorPass!2026');
  const intern = account(`intern-${stamp}@e2e.test`, `E2E Intern ${stamp}`, 'InternPass!2026');
  globalAdmin = admin;

  await page.goto('/bootstrap');
  if (await page.getByRole('heading', { name: 'Create the first administrator' }).isVisible()) {
    await page.getByLabel('Email').fill(admin.email);
    await page.getByLabel('Display name').fill(admin.displayName);
    await page.getByLabel('Password').fill(admin.password);
    await page.getByRole('button', { name: 'Create administrator' }).click();
    await expect(page).toHaveURL(/\/login|\/dashboard/);
  } else {
    admin.email = process.env.E2E_ADMIN_EMAIL;
    admin.password = process.env.E2E_ADMIN_PASSWORD;
    test.skip(!admin.email || !admin.password, 'Bootstrap is already initialized; provide disposable E2E_ADMIN_EMAIL/E2E_ADMIN_PASSWORD.');
  }

  await signIn(page, admin);
  await ensureSmtp(page);
  await createAccount(page, request, mentor, 'MENTOR');
  await signIn(page, admin);
  await createAccount(page, request, intern, 'INTERN', {
    studentCode: `STU-${stamp}`,
    start: '2026-08-01',
    end: '2026-12-31',
  });

  await signIn(page, admin);
  await page.goto('/admin/accounts');
  await expect(page.getByRole('heading', { name: 'Accounts' })).toBeVisible();
  await page.getByLabel('Search').fill(intern.email);
  await page.getByRole('button', { name: 'Apply filters' }).click();
  await expect(page.getByText(intern.displayName)).toBeVisible();

  for (const [route, heading] of [
    ['/admin/attendance-policies', 'Attendance Policy'],
    ['/attendance/calendar', 'Global calendar'],
    ['/admin/settings#holiday-api-history', 'Attendance and integration settings'],
    ['/admin/smtp', 'SMTP configuration'],
  ]) {
    await page.goto(route);
    await expect(page.locator('h1').filter({ hasText: heading })).toBeVisible();
  }
  await assertDesktopThemeAndKeyboard(page, testInfo);

  // The production scheduler promotes a newly activated Intern before Project eligibility is evaluated.
  await signIn(page, intern);
  await signIn(page, mentor);
  const projectName = `E2E Project ${stamp}`;
  await page.goto('/projects/new');
  await waitForEligibleIntern(page, intern.displayName);
  await page.getByRole('textbox', { name: 'Name', exact: true }).fill(projectName);
  await page.getByLabel('Description').fill(`Browser-created project ${stamp}`);
  await page.getByLabel('Start date').fill('2026-08-01');
  await page.getByLabel('End date').fill('2026-12-31');
  await page.getByRole('button', { name: 'Choose an eligible Intern' }).click();
  await page.locator('[data-picker-option]').filter({ hasText: intern.displayName })
    .locator('input[type="radio"]').check();
  await page.getByRole('button', { name: 'Use selection' }).click();
  await page.getByRole('button', { name: 'Create Project' }).click();
  await expect(page).toHaveURL(/\/projects\/\d+$/);
  const projectId = idFromUrl(page.url(), /\/projects\/(\d+)$/);
  await expect(page.getByRole('heading', { name: projectName, exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Activate' }).click();
  await expect(page.getByText('ACTIVE', { exact: true })).toBeVisible();
  await page.getByRole('link', { name: 'View Tasks' }).click();
  await expect(page.getByRole('heading', { name: 'Project Tasks' })).toBeVisible();
  await signIn(page, intern);
  await page.goto(`/projects/${projectId}/tasks`);
  await expect(page.getByRole('heading', { name: 'Project Tasks' })).toBeVisible();
  await page.getByRole('link', { name: 'Create Task' }).click();
  const taskTitle = `E2E Task ${stamp}`;
  await page.getByLabel('Title').fill(taskTitle);
  await page.getByLabel('Description').fill(`Browser-created task ${stamp}`);
  await page.getByLabel('Assignee').selectOption({ label: intern.displayName });
  await page.getByLabel('Due date').fill('2026-09-30');
  await page.getByRole('button', { name: 'Create Task' }).click();
  await expect(page).toHaveURL(new RegExp(`/projects/${projectId}/tasks/\\d+$`));
  const taskId = idFromUrl(page.url(), /\/tasks\/(\d+)$/);
  await expect(page.getByRole('heading', { name: taskTitle })).toBeVisible();
  await page.getByLabel('New status').selectOption('IN_PROGRESS');
  await page.getByRole('button', { name: 'Change status' }).click();
  await expect(page.getByText('IN_PROGRESS', { exact: true })).toBeVisible();
  await page.getByLabel('Work date').fill('2026-08-22');
  await page.getByLabel('Minutes').fill('90');
  await page.getByLabel('Note').fill('Browser-created work log');
  await page.getByRole('button', { name: 'Log work' }).click();
  await expect(page.getByText('Browser-created work log')).toBeVisible();
  await page.goto(`/projects/${projectId}/workflows`);
  await expect(page.getByRole('heading', { name: 'Project History' })).toBeVisible();
  await expect(page.getByRole('heading', { name: new RegExp(taskTitle) })).toBeVisible();
  await expect(page.getByText('Browser-created work log')).toBeVisible();

  await signIn(page, mentor);
  await page.goto('/reports/project-tasks');
  await expect(page.getByRole('heading', { name: 'Project and Task report' })).toBeVisible();
  await page.getByLabel('Project', { exact: true }).selectOption(String(projectId));
  await page.getByLabel('Due from').fill('2026-09-01');
  await page.getByLabel('Due to').fill('2026-09-30');
  await page.getByLabel('Work date from').fill('2026-08-01');
  await page.getByLabel('Work date to').fill('2026-08-31');
  await page.getByRole('button', { name: 'Apply filters' }).click();
  await expect(page.getByText(taskTitle)).toBeVisible();
  await assertDownload(page, 'Download XLSX', '/reports/project-tasks.xlsx',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    /^project-task-report-2026-08-01-to-2026-09-30\.xlsx$/,
    (bytes) => {
      expect(bytes.subarray(0, 4).toString()).toBe('PK\u0003\u0004');
      expect(bytes.includes(Buffer.from('xl/worksheets/sheet1.xml'))).toBe(true);
    });
  await assertDownload(page, 'Download PDF', '/reports/project-tasks.pdf', 'application/pdf',
    /^project-task-report-2026-08-01-to-2026-09-30\.pdf$/,
    (bytes) => expect(bytes.subarray(0, 4).toString()).toBe('%PDF'));

  await signIn(page, intern);
  for (const [route, heading] of [
    ['/projects', 'Projects'],
    ['/attendance', 'My attendance'],
    ['/notifications', 'Notifications'],
    ['/reports/project-tasks', 'Project and Task report'],
  ]) {
    await page.goto(route);
    await expect(page.locator('h1').filter({ hasText: heading })).toBeVisible();
  }
  await page.goto('/attendance/leave');
  await expect(page.getByText('Submit and review full-day leave requests.')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'My Leave' })).toBeVisible();
  await page.getByLabel('Quota month').fill('2026-08');
  await page.getByRole('button', { name: 'View balance' }).click();
  await expect(page.getByText(/reserved.*quota.*remaining/)).toBeVisible();
  await page.goto('/attendance/corrections');
  await expect(page.getByText('Submit and review missed-checkout corrections.')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'My Corrections' })).toBeVisible();

  await page.goto('/reports/attendance?from=2026-08-01&to=2026-08-31');
  await expect(page.getByRole('heading', { name: 'Attendance report' })).toBeVisible();
  await assertDownload(page, 'Download XLSX', '/reports/attendance.xlsx',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    /^attendance-report-2026-08-01-to-2026-08-31\.xlsx$/,
    (bytes) => expect(bytes.subarray(0, 4).toString()).toBe('PK\u0003\u0004'));
  await assertDownload(page, 'Download PDF', '/reports/attendance.pdf', 'application/pdf',
    /^attendance-report-2026-08-01-to-2026-08-31\.pdf$/,
    (bytes) => expect(bytes.subarray(0, 4).toString()).toBe('%PDF'));

  await signIn(page, mentor);
  await page.goto('/attendance/leave');
  await expect(page.getByRole('heading', { name: 'Leave decisions' })).toBeVisible();
  await page.goto('/attendance/corrections');
  await expect(page.getByRole('heading', { name: 'Correction decisions' })).toBeVisible();
});

function account(email, displayName, password) {
  return { email, displayName, password };
}

function idFromUrl(url, pattern) {
  const match = url.match(pattern);
  if (!match) throw new Error(`Expected numeric identifier in ${url}`);
  return Number.parseInt(match[1], 10);
}

async function signIn(page, account) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(account.email);
  await page.getByLabel('Password').fill(account.password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  if (account.role === 'INTERN') await waitForInternLanding(page);
  else await expect(page).toHaveURL(/dashboard|\/admin\/smtp/);
  if (account === globalAdmin && page.url().includes('/admin/smtp')) await activateMailpitSmtp(page);
}

async function waitForInternLanding(page) {
  for (let attempt = 0; attempt < 15; attempt += 1) {
    if (page.url().includes('/dashboard')) return;
    await page.waitForTimeout(5000);
    await page.goto('/dashboard');
  }
  await expect(page).toHaveURL(/dashboard/);
}

async function waitForEligibleIntern(page, displayName) {
  const openPicker = page.getByRole('button', { name: 'Choose an eligible Intern' });
  const option = page.locator('[data-picker-option]').filter({ hasText: displayName });
  for (let attempt = 0; attempt < 15; attempt += 1) {
    await openPicker.click();
    if (await option.count() > 0) {
      await page.getByRole('button', { name: 'Cancel' }).click();
      return;
    }
    await page.getByRole('button', { name: 'Cancel' }).click();
    if (attempt === 14) {
      throw new Error(`Intern ${displayName} did not become Project-eligible within 75 seconds`);
    }
    await page.waitForTimeout(5000);
    await page.reload();
  }
}

async function ensureSmtp(page) {
  await page.goto('/admin/smtp');
  await expect(page).toHaveURL(/\/admin\/smtp/);
  const active = page.getByRole('status').filter({ hasText: 'SMTP is active' });
  if (await active.count() === 0) await activateMailpitSmtp(page);
  await expect(page.getByRole('status')).toContainText('SMTP is active');
}

let globalAdmin;

async function activateMailpitSmtp(page) {
  page.once('dialog', (dialog) => dialog.accept());
  await page.getByLabel('Host').fill('localhost');
  await page.getByLabel('Port').fill('1025');
  await page.getByLabel('Security').selectOption('NONE');
  await page.getByLabel('From address').fill('system@labtimesheet.test');
  await page.getByLabel('From name').fill('Lab Timesheet E2E');
  await page.getByRole('button', { name: 'Save draft' }).click();
  await expect(page.getByRole('button', { name: 'Test connection' })).toBeVisible();
  await page.getByRole('button', { name: 'Test connection' }).click();
  await expect(page.getByRole('button', { name: 'Activate SMTP' })).toBeEnabled();
  await page.getByRole('button', { name: 'Activate SMTP' }).click();
  await expect(page).toHaveURL(/\/admin\/smtp\?activated/);
  await expect(page.getByRole('status')).toContainText('SMTP is active');
  await page.goto('/dashboard');
  await expect(page).toHaveURL(/dashboard/);
}

async function createAccount(page, request, account, role, internDetails = {}) {
  account.role = role;
  await page.goto('/admin/accounts/new');
  await page.getByLabel('Email').fill(account.email);
  await page.getByLabel('Display name').fill(account.displayName);
  await page.getByLabel('Role').selectOption(role);
  if (role === 'INTERN') {
    await page.getByLabel('Student code').fill(internDetails.studentCode);
    await page.getByLabel('Internship start').fill(internDetails.start);
    await page.getByLabel('Internship end').fill(internDetails.end);
  }
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page).toHaveURL(/\/admin\/accounts\/new\?created/);
  const activationUrl = await activationLink(request, account.email);
  await page.goto(activationUrl);
  await expect(page.getByRole('heading', { name: 'Choose your password' })).toBeVisible();
  await page.getByRole('textbox', { name: 'Password', exact: true }).fill(account.password);
  await page.getByLabel('Confirm password').fill(account.password);
  await page.getByRole('button', { name: 'Activate account' }).click();
  await expect(page).toHaveURL(/login|dashboard/);
}

async function activationLink(request, recipient) {
  for (let attempt = 0; attempt < 30; attempt += 1) {
    const response = await request.get('http://127.0.0.1:8025/api/v1/messages');
    const payload = await response.json();
    for (const message of payload.messages ?? []) {
      if (!message.To?.some((to) => to.Address === recipient)) continue;
      const detail = await (await request.get(`http://127.0.0.1:8025/api/v1/message/${message.ID}`)).json();
      const body = `${detail.Text ?? ''}\n${detail.HTML ?? ''}`;
      const match = body.match(/https?:\/\/[^\s<>"']+\/activate\?token=[^\s<>"']+/);
      if (match) return match[0].replace(/[).,]+$/, '');
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`Activation email was not delivered to ${recipient}`);
}

async function assertDownload(page, linkName, route, mediaType, filename, inspect) {
  const responsePromise = page.waitForResponse((response) =>
    response.url().includes(route) && response.request().method() === 'GET');
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('link', { name: linkName }).click();
  const [response, download] = await Promise.all([responsePromise, downloadPromise]);
  expect(response.status()).toBe(200);
  expect(response.headers()['content-type']).toContain(mediaType);
  expect(response.headers()['content-disposition']).toMatch(/attachment/i);
  await expect(download.failure()).resolves.toBeNull();
  expect(download.suggestedFilename()).toMatch(filename);
  const path = await download.path();
  if (!path) throw new Error(`${linkName} did not expose a local download path`);
  inspect(await (await import('node:fs/promises')).readFile(path));
}

async function assertDesktopThemeAndKeyboard(page, testInfo) {
  await page.getByLabel('Theme').selectOption('dark');
  await expect.poll(() => page.locator('html').getAttribute('data-theme')).toBe('dark');
  await page.screenshot({ path: testInfo.outputPath('desktop-dark.png'), fullPage: true });
  const darkPalette = await page.locator('html').evaluate((root) => ({
    canvas: getComputedStyle(root).getPropertyValue('--canvas').trim(),
    ink: getComputedStyle(root).getPropertyValue('--ink').trim(),
  }));
  expect(darkPalette.canvas).not.toBe(darkPalette.ink);

  await page.getByLabel('Theme').selectOption('light');
  await expect.poll(() => page.locator('html').getAttribute('data-theme')).toBe('light');
  await page.screenshot({ path: testInfo.outputPath('desktop-light.png'), fullPage: true });
  const lightPalette = await page.locator('html').evaluate((root) => ({
    canvas: getComputedStyle(root).getPropertyValue('--canvas').trim(),
    ink: getComputedStyle(root).getPropertyValue('--ink').trim(),
  }));
  expect(lightPalette.canvas).not.toBe(lightPalette.ink);

  const accountsLink = page.getByRole('link', { name: 'Accounts', exact: true });
  await accountsLink.focus();
  await expect(accountsLink).toBeFocused();
  const focusStyle = await accountsLink.evaluate((element) => {
    const style = getComputedStyle(element);
    return { outlineStyle: style.outlineStyle, outlineWidth: style.outlineWidth };
  });
  expect(focusStyle.outlineStyle).toBe('solid');
  expect(Number.parseFloat(focusStyle.outlineWidth)).toBeGreaterThanOrEqual(3);
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/admin\/accounts/);
}
