import { test, expect } from '@playwright/test';

test.describe.configure({ mode: 'serial' });

test('Iteration 3 setup and critical Admin/Intern/Mentor journeys', async ({ page, request }, testInfo) => {
  test.setTimeout(240_000);
  const stamp = Date.now();
  const dates = runtimeDates();
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
    start: dates.internshipStart,
    end: dates.internshipEnd,
  });

  await signIn(page, admin);
  await page.goto('/admin/accounts');
  await expect(page.getByRole('heading', { name: 'Accounts' })).toBeVisible();
  await page.getByLabel('Search').fill(intern.email);
  await page.getByRole('button', { name: 'Apply filters' }).click();
  await expect(page.getByText(intern.displayName)).toBeVisible();

  const internRow = page.locator('tbody tr').filter({ hasText: intern.email });
  await internRow.getByRole('link', { name: 'View' }).click();
  await page.getByRole('link', { name: 'Edit permitted details' }).click();
  await expect(page.getByRole('heading', { name: 'Account correction' })).toBeVisible();
  const correctedStudentCode = `CORR-${stamp}`;
  await page.getByLabel('Student code').fill(correctedStudentCode);
  await page.getByRole('button', { name: 'Save correction' }).click();
  await expect(page).toHaveURL(/\/admin\/accounts\/\d+$/);
  await expect(page.getByText('Account correction saved', { exact: true })).toBeVisible();

  await page.goto('/admin/attendance-policies');
  await expect(page.locator('h1').filter({ hasText: 'Attendance Policy' })).toBeVisible();
  await page.getByLabel('Effective month').fill(dates.nextMonth);
  await page.getByRole('button', { name: 'Schedule policy' }).click();
  await expect(page.getByText('Attendance policy scheduled', { exact: true })).toBeVisible();
  await expect(page.locator('#policy-history')).toContainText(formatDate(`${dates.nextMonth}-01`));

  await page.goto('/attendance/calendar');
  await page.getByLabel('Date', { exact: true }).fill(dates.today);
  await page.getByLabel('Name', { exact: true }).fill(`E2E workday ${stamp}`);
  const dayOff = page.locator('form[action="/attendance/calendar"] input[name="dayOff"]');
  if (await dayOff.isChecked()) await dayOff.uncheck();
  await page.getByRole('button', { name: 'Add event' }).click();
  await expect(page.getByText('Calendar event created', { exact: true })).toBeVisible();
  await expect(page.locator('#calendar-history')).toContainText(`E2E workday ${stamp}`);

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
  await fillProjectForm(page, projectName, dates, stamp);
  await waitForEligibleIntern(page, intern.displayName);
  await fillProjectForm(page, projectName, dates, stamp);
  await page.getByRole('button', { name: 'Choose an eligible Intern' }).click();
  await page.locator('[data-picker-option]').filter({ hasText: intern.displayName })
    .locator('input[type="radio"]').check();
  await page.getByRole('button', { name: 'Done' }).click();
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
  await page.getByLabel('Due date').fill(dates.taskDue);
  await page.getByRole('button', { name: 'Create Task' }).click();
  await expect(page).toHaveURL(new RegExp(`/projects/${projectId}/tasks/\\d+$`));
  const taskId = idFromUrl(page.url(), /\/tasks\/(\d+)$/);
  await expect(page.getByRole('heading', { name: taskTitle })).toBeVisible();
  await page.getByLabel('New status').selectOption('IN_PROGRESS');
  await page.getByRole('button', { name: 'Change status' }).click();
  await expect(page.getByText('IN_PROGRESS', { exact: true })).toBeVisible();
  await page.getByLabel('Work date').fill(dates.today);
  // The Task page also has "Estimate (minutes; …)", so match the work-log field's exact label.
  await page.getByLabel('Minutes', { exact: true }).fill('90');
  await page.getByLabel('Note').fill('Browser-created work log');
  await page.getByRole('button', { name: 'Log work' }).click();
  await expect(page.getByText('Browser-created work log')).toBeVisible();
  await page.goto(`/projects/${projectId}/history`);
  await expect(page.getByRole('heading', { name: 'Project History' })).toBeVisible();
  await page.getByRole('tab', { name: 'Task activity' }).click();
  await expect(page.getByText(taskTitle, { exact: true })).toBeVisible();
  // Each Task's retained activity is a collapsed <details> entry; open this Task's entry first.
  const taskHistoryEntry = page.locator('details.history-task-item').filter({ hasText: taskTitle });
  await taskHistoryEntry.locator('summary').click();
  await expect(taskHistoryEntry.getByText('Browser-created work log')).toBeVisible();

  await signIn(page, mentor);
  await page.goto('/reports/project-tasks');
  await expect(page.getByRole('heading', { name: 'Project and Task report' })).toBeVisible();
  await page.getByLabel('Project', { exact: true }).selectOption(String(projectId));
  await page.getByLabel('Due from').fill(dates.reportDueFrom);
  await page.getByLabel('Due to').fill(dates.reportDueTo);
  await page.getByLabel('Work date from').fill(dates.reportWorkFrom);
  await page.getByLabel('Work date to').fill(dates.reportWorkTo);
  await page.getByRole('button', { name: 'Apply filters' }).click();
  await expect(page.getByText(taskTitle)).toBeVisible();
  await assertDownload(page, 'Download XLSX', '/reports/project-tasks.xlsx',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    new RegExp(`^project-task-report-${dates.reportWorkFrom}-to-${dates.reportDueTo}\\.xlsx$`),
    (bytes) => {
      expect(bytes.subarray(0, 4).toString()).toBe('PK\u0003\u0004');
      expect(bytes.includes(Buffer.from('xl/worksheets/sheet1.xml'))).toBe(true);
    });
  await assertDownload(page, 'Download PDF', '/reports/project-tasks.pdf', 'application/pdf',
    new RegExp(`^project-task-report-${dates.reportWorkFrom}-to-${dates.reportDueTo}\\.pdf$`),
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
  await page.getByLabel('Quota month').fill(dates.month);
  await page.getByRole('button', { name: 'View balance' }).click();
  await expect(page.getByText(/reserved.*quota.*remaining/)).toBeVisible();

  await page.getByLabel('Start').fill(dates.leaveDate);
  await page.getByLabel('End').fill(dates.leaveDate);
  await page.getByLabel('Reason').fill(`E2E leave ${stamp}`);
  await page.getByRole('button', { name: 'Submit leave' }).click();
  await expect(page).toHaveURL(/\/attendance\/leave\/\d+$/);
  const leaveDetail = page.locator('section').filter({ hasText: 'Leave request detail' });
  await expect(leaveDetail.getByText('Pending decision', { exact: true })).toBeVisible();
  const leaveUrl = page.url();
  await signIn(page, mentor);
  await page.goto(leaveUrl);
  await expect(page.getByRole('heading', { name: 'Leave request detail' })).toBeVisible();
  await page.getByRole('button', { name: 'Approve' }).click();
  await expect(page.getByText('Leave request approved', { exact: true })).toBeVisible();
  await expect(leaveDetail.getByText('Approved', { exact: true })).toBeVisible();

  await signIn(page, intern);
  await page.goto('/attendance');
  await page.getByRole('button', { name: 'Check in' }).click();
  await expect(page.getByText('Checked in', { exact: true })).toBeVisible();
  const correctionLink = page.getByRole('link', { name: 'Request correction' }).first();
  await expect(correctionLink).toBeVisible();
  const correctionFormUrl = await correctionLink.getAttribute('href');
  if (!correctionFormUrl) throw new Error('Checked-in attendance row did not expose correction form');
  const attendanceRecordId = new URL(correctionFormUrl, page.url()).searchParams.get('attendanceRecordId');
  if (!attendanceRecordId) throw new Error(`Correction form did not retain attendance record: ${correctionFormUrl}`);
  const proposedCheckout = await proposedCheckoutAfterCheckIn(page, dates.today);
  await page.goto(correctionFormUrl);
  await page.getByLabel('Attendance record').fill(attendanceRecordId);
  await page.getByLabel('Proposed checkout').fill(proposedCheckout.value);
  await page.getByLabel('Reason').fill(`E2E correction ${stamp}`);
  await page.getByRole('button', { name: 'Submit correction' }).click();
  await expect(page).toHaveURL(/\/attendance\/corrections\/\d+$/);
  const correctionDetail = page.locator('section').filter({ hasText: 'Correction detail' });
  await expect(correctionDetail.getByText('Pending decision', { exact: true })).toBeVisible();
  const correctionUrl = page.url();
  await signIn(page, mentor);
  await page.goto(correctionUrl);
  await expect(page.getByRole('heading', { name: 'Correction detail' })).toBeVisible();
  const correctionDecisionForm = correctionDetail.locator('form');
  await correctionDecisionForm.getByLabel('Decision', { exact: true }).selectOption('APPROVE');
  await correctionDecisionForm.getByLabel('Note', { exact: true }).fill(`E2E correction approved ${stamp}`);
  await page.getByRole('button', { name: 'Save decision' }).click();
  await expect(page.getByText('Correction decision saved', { exact: true })).toBeVisible();
  await expect(correctionDetail.getByText('Approved', { exact: true })).toBeVisible();

  await signIn(page, intern);
  await page.goto('/attendance/corrections');
  await expect(page.getByText('Submit and review missed-checkout corrections.')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'My Corrections' })).toBeVisible();

  await page.goto(`/reports/attendance?from=${dates.reportWorkFrom}&to=${dates.reportWorkTo}`);
  await expect(page.getByRole('heading', { name: 'Attendance report' })).toBeVisible();
  await assertDownload(page, 'Download XLSX', '/reports/attendance.xlsx',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    new RegExp(`^attendance-report-${dates.reportWorkFrom}-to-${dates.reportWorkTo}\\.xlsx$`),
    (bytes) => expect(bytes.subarray(0, 4).toString()).toBe('PK\u0003\u0004'));
  await assertDownload(page, 'Download PDF', '/reports/attendance.pdf', 'application/pdf',
    new RegExp(`^attendance-report-${dates.reportWorkFrom}-to-${dates.reportWorkTo}\\.pdf$`),
    (bytes) => expect(bytes.subarray(0, 4).toString()).toBe('%PDF'));

  await signIn(page, mentor);
  await page.goto('/attendance/leave');
  await expect(page.getByRole('heading', { name: 'Leave decisions' })).toBeVisible();
  await page.goto('/attendance/corrections');
  await expect(page.getByRole('heading', { name: 'Correction decisions' })).toBeVisible();
});

function runtimeDates() {
  const today = zonedToday();
  const internshipStart = addDays(today, -7);
  const internshipEnd = addDays(today, 90);
  const taskDue = addDays(today, 30);
  const reportDueFrom = addDays(taskDue, -7);
  const reportWorkFrom = addDays(today, -7);
  const [year, month] = today.slice(0, 7).split('-').map(Number);
  const nextMonthDate = new Date(Date.UTC(year, month, 1));
  const nextMonth = nextMonthDate.toISOString().slice(0, 7);
  return {
    today,
    internshipStart,
    internshipEnd,
    projectStart: internshipStart,
    projectEnd: internshipEnd,
    taskDue,
    reportDueFrom,
    reportDueTo: taskDue,
    reportWorkFrom,
    reportWorkTo: today,
    month: today.slice(0, 7),
    nextMonth,
    leaveDate: nextWeekday(today),
  };
}

function zonedToday() {
  const configuredBusinessDate = process.env.E2E_BUSINESS_DATE;
  if (configuredBusinessDate !== undefined) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(configuredBusinessDate)
        || Number.isNaN(Date.parse(`${configuredBusinessDate}T00:00:00Z`))
        || new Date(`${configuredBusinessDate}T00:00:00Z`).toISOString().slice(0, 10) !== configuredBusinessDate) {
      throw new Error(`E2E_BUSINESS_DATE must be a valid YYYY-MM-DD date: ${configuredBusinessDate}`);
    }
    return configuredBusinessDate;
  }
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(new Date());
  const values = Object.fromEntries(parts.filter(({ type }) => type !== 'literal').map(({ type, value }) => [type, value]));
  return `${values.year}-${values.month}-${values.day}`;
}

function addDays(isoDate, days) {
  const date = new Date(`${isoDate}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

function nextWeekday(isoDate) {
  let date = addDays(isoDate, 1);
  while ([0, 6].includes(new Date(`${date}T00:00:00Z`).getUTCDay())) date = addDays(date, 1);
  return date;
}

function formatDate(isoDate) {
  const [year, month, day] = isoDate.split('-');
  return `${day}/${month}/${year}`;
}

async function proposedCheckoutAfterCheckIn(page, businessDate) {
  const row = page.locator('tbody tr').first();
  const workDateDisplay = (await row.locator('td').nth(0).textContent()).trim();
  const checkInDisplay = (await row.locator('td').nth(1).textContent()).trim();
  const [day, month, year] = workDateDisplay.split('/');
  const [hour, minute] = checkInDisplay.split(':').map(Number);
  if (!day || !month || !year || !Number.isInteger(hour) || !Number.isInteger(minute)) {
    throw new Error(`Attendance row did not expose a parseable server check-in: ${workDateDisplay} ${checkInDisplay}`);
  }
  const workDate = `${year}-${month}-${day}`;
  if (workDate !== businessDate) {
    throw new Error(`Attendance business date ${workDate} did not match E2E_BUSINESS_DATE ${businessDate}`);
  }
  const proposal = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day), hour, minute + 1));
  const pad = (value) => String(value).padStart(2, '0');
  await page.waitForTimeout(61_000);
  return {
    value: `${proposal.getUTCFullYear()}-${pad(proposal.getUTCMonth() + 1)}-${pad(proposal.getUTCDate())}T${pad(proposal.getUTCHours())}:${pad(proposal.getUTCMinutes())}`,
  };
}

async function fillProjectForm(page, projectName, dates, stamp) {
  await page.getByRole('textbox', { name: 'Name', exact: true }).fill(projectName);
  await page.getByLabel('Description').fill(`Browser-created project ${stamp}`);
  await page.getByLabel('Start date').fill(dates.projectStart);
  await page.getByLabel('End date').fill(dates.projectEnd);
}

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
    if (await openPicker.isDisabled()) {
      if (attempt === 14) {
        throw new Error(`Intern ${displayName} did not become Project-eligible within 75 seconds`);
      }
      await page.waitForTimeout(5000);
      await page.reload();
      continue;
    }
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
