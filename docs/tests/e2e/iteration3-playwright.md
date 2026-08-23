# Test Evidence: Iteration 3 Playwright/Chromium harness

- **Test type:** E2E
- **Requirement IDs:** `I3-UI-05`, `I3-UI-06`, `UI-002`, `UI-003`, `UI-007`, `UI-014`, `RPT-001`–`RPT-010`
- **Scenario IDs:** `AC-UI-001`, `AC-UI-002`, `AC-UI-005`, `AC-RPT-001`, `AC-RPT-002`, `AC-RPT-003`, `AC-TST-001`
- **Test class/method:** `src/test/e2e/critical-journeys.spec.mjs`; `src/test/e2e/smoke.spec.mjs`; `src/test/e2e/report-journeys.spec.mjs`
- **Implementation commit:** `075ba0015b1340276bda1be779012c4849cd80a9`
- **Latest exporter parity rerun:** `f0506492857288927f91e020aeba2fa0bb8550e8`
- **Latest null-project parity rerun:** `d0c7dc60ac8c9925c4ea7621fef519c60ed2f9b3`

## Protected behavior

The repository provides one pinned Chromium Playwright project with one worker, retained failure traces/screenshots, disabled video capture for portability, desktop and narrow smoke journeys, and production-shaped serial Iteration 3 journeys. Disposable credentials are supplied only through the runtime environment; activation links are read from Mailpit's local HTTP API and no password or raw token is committed.

## Test method

The critical journey serially bootstraps the first Admin when needed, configures and activates Mailpit SMTP through the real Admin forms, creates disposable Mentor/Intern accounts, consumes activation links through the Mailpit HTTP API, and retries Intern dashboard landing for up to 75 seconds while the production lifecycle scheduler moves a newly activated account from `NOT_STARTED`. Its dates are derived from one Asia/Ho_Chi_Minh runtime business-date fixture, with validated `E2E_BUSINESS_DATE` available for the reviewed advancing-clock stack. It covers Account Directory correction, future Admin Policy scheduling, Global Calendar workday mutation, Intern Leave submission plus Mentor approval, real missed-checkout check-in/correction submission/Mentor approval, and the real Project/Task/History/report flow with XLSX/PDF downloads and status/media/filename/ZIP/signature assertions. It also captures desktop light/dark screenshots, keyboard activation/focus outline, palette separation, and representative Project/attendance/notification/report routes. The smoke suite opens `/bootstrap` on a fresh installation or explicitly verifies its initialized 404 before checking the login shell, then repeats at 390×844. The older report journey remains credential-gated and skipped unless explicit Intern `E2E_EMAIL`/`E2E_PASSWORD` are supplied because its role-specific assertions are intentionally not substituted with Admin credentials.

## Hand-derived expected result

The harness must run one worker, retain failure artifacts, exercise both download endpoints rather than only checking link markup, and keep credentials/tokens out of the repository. The managed critical journey is the load-bearing Iteration 3 browser evidence; the legacy role-specific report spec is an optional explicit-credential regression. The Project/Task flow must create a non-empty dataset before exercising filtered report downloads.

## RED

**Command**

```text
npm run test:e2e:smoke
```

**Observed result**

```text
The first managed run was RED before browser navigation because Playwright's managed `chromium_headless_shell-1187` was incomplete; retained traces/screenshots are under `/private/tmp/labtimesheet-iteration3-playwright-artifacts-20260822/managed-chromium-failure/test-results-playwright/`. The first critical journey RED then exposed missing SMTP activation state and brittle selectors; the Project/Task extension exposed role-derived Task creation, scheduler eligibility, and exact report selectors. The production-shaped journey now opens the picker on each bounded eligibility attempt and asserts real redirects, native dialogs, and exact labels.

The review-fix RED sequence exposed and corrected three browser-harness issues: the picker retry needed to tolerate native disabled state, eligibility reloads needed the required Project fields refilled, and correction proposals needed the server-rendered check-in time rather than machine wall time. No direct database fixture, test-only endpoint, or production bypass was added. Failure artifacts remain under `test-results/playwright/critical-journeys-Iteratio-89edd-dmin-Intern-Mentor-journeys-chromium/`.
```

## GREEN

**Command and result**

```text
PLAYWRIGHT_BROWSERS_PATH=/private/tmp/labtimesheet-playwright-browsers \
PLAYWRIGHT_BASE_URL=http://127.0.0.1:8080 \
PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin \
npm run test:e2e:smoke
2 passed with one worker against the live Java/PostgreSQL/Mailpit stack using managed Chromium.

E2E_ADMIN_EMAIL=<runtime-only disposable Admin> \
E2E_ADMIN_PASSWORD=<runtime-only disposable password> \
E2E_BUSINESS_DATE=2026-08-21 \
PLAYWRIGHT_BROWSERS_PATH=/private/tmp/labtimesheet-playwright-browsers \
PLAYWRIGHT_BASE_URL=http://127.0.0.1:8080 \
PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin \
npm run test:e2e
3 passed, 2 skipped with one worker in 1.8 minutes: the serial critical Iteration 3 journey (including Account correction, future Policy/Calendar mutations, real Leave submit/Mentor approval, missed-checkout Correction submit/Mentor approval, Project/Task creation/status/work-log, Project History attribution, and Project/Task XLSX/PDF downloads) and both smoke tests passed; the two legacy Intern credential-gated tests skipped because `E2E_EMAIL`/`E2E_PASSWORD` were not supplied. `E2E_BUSINESS_DATE=2026-08-21` was validated by the journey and the advancing application clock supplied the server check-in time. Desktop evidence retained at `test-results/playwright/critical-journeys-Iteratio-89edd-dmin-Intern-Mentor-journeys-chromium/desktop-light.png` and `desktop-dark.png`; automated focus/keyboard assertions are separate from visual sign-off. Orchestrator manual visual sign-off at 1280x720 found complete navigation/form layout without clipping or overlap, with distinguishable text, controls, borders, selected navigation, statuses, and actions in both themes.
```

After the reporting exporter and print-template parity fix, the same real browser report-download journey was rerun against the live Java 25/PostgreSQL/Mailpit stack:

```text
rtk env PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin PLAYWRIGHT_BROWSERS_PATH=/private/tmp/labtimesheet-playwright-browsers PLAYWRIGHT_BASE_URL=http://127.0.0.1:8080 E2E_BUSINESS_DATE=2026-08-21 E2E_ADMIN_EMAIL=<runtime-only disposable Admin> E2E_ADMIN_PASSWORD=<runtime-only disposable password> npx playwright test src/test/e2e/critical-journeys.spec.mjs --reporter=line
1 passed with one worker in 2.1 minutes. The journey created a real Project/Task/work-log dataset and completed successful XLSX and PDF downloads with filename, media-type, ZIP, and PDF signature assertions.
```

## Affected suite

```text
Current evidence: Node UI contracts passed 14/14; frontend build passed; managed Chromium critical/full journeys passed as recorded above, including the post-exporter-change critical rerun. The older role-specific report spec remains intentionally skipped without explicit Intern credentials; Java report exporter parity/font evidence remains separately covered by merged-dependency tests and Project/Task browser exports passed with a real non-empty dataset.
```

## External-test boundaries

The suite requires a running application at `PLAYWRIGHT_BASE_URL` (default `http://127.0.0.1:8080`), PostgreSQL, and Mailpit; it does not start services automatically. The critical journey requires a runtime-only Admin credential when bootstrap is already initialized and uses Mailpit's local API for activation links. Its Project/Task flow uses existing public server-rendered forms and role contracts without producer endpoints or new dependencies. The legacy report journey requires explicit Intern credentials. No extension, push, deployment, or raw token persistence is used.
