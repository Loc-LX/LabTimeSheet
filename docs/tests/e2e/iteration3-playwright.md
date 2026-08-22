# Test Evidence: Iteration 3 Playwright/Chromium harness

- **Test type:** E2E
- **Requirement IDs:** `I3-UI-05`, `I3-UI-06`, `UI-002`, `UI-003`, `UI-007`, `UI-014`, `RPT-001`–`RPT-010`
- **Scenario IDs:** `AC-UI-001`, `AC-UI-002`, `AC-UI-005`, `AC-RPT-001`, `AC-RPT-002`, `AC-RPT-003`, `AC-TST-001`
- **Test class/method:** `src/test/e2e/critical-journeys.spec.mjs`; `src/test/e2e/smoke.spec.mjs`; `src/test/e2e/report-journeys.spec.mjs`
- **Implementation commit:** `51d116e`

## Protected behavior

The repository provides one pinned Chromium Playwright project with one worker, retained failure traces/screenshots, disabled video capture for portability, desktop and narrow smoke journeys, and production-shaped serial Iteration 3 journeys. Disposable credentials are supplied only through the runtime environment; activation links are read from Mailpit's local HTTP API and no password or raw token is committed.

## Test method

The critical journey serially bootstraps the first Admin when needed, configures and activates Mailpit SMTP through the real Admin forms, creates disposable Mentor/Intern accounts, consumes activation links through the Mailpit HTTP API, covers Account Directory and Admin Policy/Calendar/Holiday/SMTP navigation, exercises Intern Leave/Correction and balance plus Mentor decision pages, and performs real XLSX/PDF downloads with status, media, attachment, filename, ZIP/signature assertions. It also captures desktop light/dark screenshots, keyboard activation/focus outline, palette separation, and representative Project/attendance/notification/report routes. The smoke suite opens `/bootstrap` on a fresh installation or explicitly verifies its initialized 404 before checking the login shell, then repeats at 390×844. The older report journey remains credential-gated and skipped unless explicit Intern `E2E_EMAIL`/`E2E_PASSWORD` are supplied because its role-specific assertions are intentionally not substituted with Admin credentials.

## Hand-derived expected result

The harness must run one worker, retain failure artifacts, exercise both download endpoints rather than only checking link markup, and keep credentials/tokens out of the repository. The managed critical journey is the load-bearing Iteration 3 browser evidence; the legacy role-specific report spec is an optional explicit-credential regression.

## RED

**Command**

```text
npm run test:e2e:smoke
```

**Observed result**

```text
The first managed run was RED before browser navigation because Playwright's managed `chromium_headless_shell-1187` was incomplete; retained traces/screenshots are under `/private/tmp/labtimesheet-iteration3-playwright-artifacts-20260822/managed-chromium-failure/test-results-playwright/`. The first critical journey RED then exposed missing SMTP activation state and brittle selectors; the production-shaped journey was corrected to assert real redirects, native dialogs, and exact labels.
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
PLAYWRIGHT_BROWSERS_PATH=/private/tmp/labtimesheet-playwright-browsers \
PLAYWRIGHT_BASE_URL=http://127.0.0.1:8080 \
PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin \
npm run test:e2e
3 passed, 2 skipped with one worker: critical serial Iteration 3 journey and both smoke tests passed; the two legacy Intern credential-gated tests skipped because `E2E_EMAIL`/`E2E_PASSWORD` were not supplied. The critical journey itself performed the real Intern XLSX/PDF downloads and split Leave/Correction assertions.
```

## Affected suite

```text
Node UI contracts, managed Chromium critical journeys, and both smoke paths are GREEN. The older role-specific report spec remains intentionally skipped without explicit Intern credentials; its behavior is covered by the critical journey with runtime-created Intern credentials. Java report exporter parity/font evidence remains separately covered by the merged-dependency tests; no browser claim is made for project/task exports without representative data.
```

## External-test boundaries

The suite requires a running application at `PLAYWRIGHT_BASE_URL` (default `http://127.0.0.1:8080`), PostgreSQL, and Mailpit; it does not start services automatically. The critical journey requires a runtime-only Admin credential when bootstrap is already initialized and uses Mailpit's local API for activation links. The legacy report journey requires explicit Intern credentials. No extension, push, deployment, or raw token persistence is used.
