# Test Evidence: Iteration 3 Playwright/Chromium harness

- **Test type:** E2E
- **Requirement IDs:** `I3-UI-05`, `I3-UI-06`, `UI-002`, `UI-003`, `UI-007`, `UI-014`, `RPT-001`–`RPT-010`
- **Scenario IDs:** `AC-UI-001`, `AC-UI-002`, `AC-UI-005`, `AC-RPT-001`, `AC-RPT-002`, `AC-RPT-003`, `AC-TST-001`
- **Test class/method:** `src/test/e2e/smoke.spec.mjs`; `src/test/e2e/report-journeys.spec.mjs`
- **Implementation commit:** `639d02e404cbd551ffd70def15021a1d6beb8cf9`

## Protected behavior

The repository provides one pinned Chromium Playwright project with one worker, retained failure traces/screenshots, disabled video capture for portability, desktop and narrow smoke journeys, and an authorized report-journey hook that can run with `E2E_EMAIL`/`E2E_PASSWORD` without browser extensions.

## Test method

The smoke suite opens `/bootstrap`, verifies the server-rendered first-Admin form and local stylesheet, then repeats at 390×844 to guard against catastrophic horizontal overflow. The report journey is credential-gated and skipped unless explicit local E2E credentials are supplied; it opens a bounded attendance period, initiates both downloads, and checks HTTP 200, media type, attachment metadata, deterministic filename, XLSX ZIP structure, and the PDF signature.

## Hand-derived expected result

The harness must list exactly two smoke tests and one credential-gated report test, run with one worker, and retain artifacts when Chromium or the app is unavailable. The report test must exercise both download endpoints rather than only checking link markup. No test stores a password in the repository.

## RED

**Command**

```text
npm run test:e2e:smoke
```

**Observed result**

```text
2 tests ran with 1 worker and failed before navigation because Playwright's managed chromium_headless_shell-1187 was not installed. Both trace.zip failure artifacts were retained at /private/tmp/labtimesheet-iteration3-playwright-artifacts-20260822/managed-chromium-failure/test-results-playwright/.
```

## GREEN

**Command and result**

```text
npx playwright install chromium
PLAYWRIGHT_CHANNEL=chrome npm run test:e2e:smoke
2 passed (3.8s) with one worker against a disposable PostgreSQL 18.4-backed Java process. This is a temporary installed-Chrome fallback; the default managed Chromium run remains blocked by the incomplete headless-shell download.
```

## Affected suite

```text
Node UI contracts and the disposable Chrome baseline smoke are GREEN. The credential-gated report journey now contains real download assertions but has not been run. The default managed Chromium baseline remains blocked by the incomplete headless-shell download; the report journey and broader Iteration 1/2 regression set remain pending reviewed dependencies and integrated producer/application fixtures.
```

## External-test boundaries

The smoke requires a running application at `PLAYWRIGHT_BASE_URL` (default `http://127.0.0.1:8080`); it does not start PostgreSQL, Mailpit, or Java automatically. The report journey requires credentials supplied through the environment and intentionally does not claim Account Directory, focused Admin settings, Leave, or Correction journeys until the reviewed Platform/Attendance producer contracts are delivered.
