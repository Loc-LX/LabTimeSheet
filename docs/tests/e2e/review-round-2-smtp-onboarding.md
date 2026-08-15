# Test Evidence: Edge SMTP onboarding and persistent restriction journey

- **Test type:** E2E
- **Requirement IDs:** `ACC-005`, `ACC-006`, `ACC-007`, `UI-002`, `UI-004`, `UI-007`, `UI-010`, `I1-UI-01`, `I1-UI-02`, `I1-UI-04`
- **Scenario IDs:** `AC-ACC-003`, `AC-UI-001`, `AC-UI-002`, `AC-UI-003`
- **Test class/method:** `Manual Edge journey: bootstrap -> login -> SMTP -> five deferrals -> dashboard warning -> configure SMTP`
- **Implementation commit:** `ddf688a5336421762ff970499bafb09505474fca`

## Protected behavior

A first Admin can bootstrap and authenticate, then defer SMTP only after five sequential warnings. The fifth Finish returns to the Admin dashboard without hiding the restricted-installation state, and the persistent warning provides a working path back to SMTP configuration. SMTP and deferral pages use the same authenticated desktop shell, pre-paint theme, keyboard focus, collapsed-sidebar tooltip, local assets, and overflow containment as other Admin pages.

## Test method

A disposable `postgres:18.4` container exposed an empty `labtimesheet_round2` database on local port `55433`. The real Java 25 Spring process connected to that database, applied Flyway V1, and listened on local port `8080`. Microsoft Edge with the Chromium extension used an explicit 1365x900 viewport. The browser created a non-production test Admin through `/bootstrap`, signed in, opened SMTP onboarding, exercised keyboard/theme/sidebar behavior, traversed all five server-owned deferral POSTs, finished to `/dashboard`, and followed the persistent warning action back to `/admin/smtp`. Browser DOM, computed styles, URLs, scroll widths, and console logs were inspected directly. The browser viewport override was reset, its test tab finalized, the Java process gracefully stopped, and the disposable PostgreSQL container removed.

## Hand-derived expected result

The warning sequence is account onboarding, activation resend, password recovery, reduced workflow-email immediacy, and restricted-installation acknowledgement. Every step has Back and Configure SMTP; steps one through four have no Finish, and step five has exactly one Finish. The resulting dashboard warning links to `/admin/smtp`. At 1365x900, `documentElement.scrollWidth` and `body.scrollWidth` equal `innerWidth`; keyboard focus has a 3px solid indicator; collapsed navigation exposes tooltip text and `aria-expanded=false`, then returns to `true`; the saved dark theme is present after reload with `theme.js` before `app.css`.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=DashboardControllerWebTest,SmtpOnboardingWebIntegrationTest,BootstrapOnboardingWebIntegrationTest test
```

**Observed result**

```text
Tests run: 13, Failures: 3, Errors: 0, Skipped: 0
The rendered Admin dashboard omitted the persistent warning/action.
The rendered SMTP form and deferral pages omitted /assets/theme.js because they were standalone pages.
BUILD FAILURE
```

No pre-change Edge journey was executed; the production-shaped MockMvc RED above was the intentional failing gate before implementation. The pre-change templates were also manually inspected and contained standalone `<head>`/`<body>` documents rather than the shared shell. This record does not relabel those observations as a browser run.

## GREEN

**Command**

```text
docker run -d --rm --name labtimesheet-ui-round2-pg -e POSTGRES_DB=labtimesheet_round2 -e POSTGRES_USER=lab_ui_round2 -e POSTGRES_PASSWORD=<local-test-placeholder> -p 55433:5432 postgres:18.4
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
export LAB_DB_URL=jdbc:postgresql://localhost:55433/labtimesheet_round2
export LAB_DB_USERNAME=lab_ui_round2
export LAB_DB_PASSWORD=<local-test-placeholder>
./mvnw spring-boot:run
```

**Observed result**

```text
Edge/Chromium explicit viewport: 1365x900
Java: 25.0.4
PostgreSQL: 18.4; Flyway V1 applied to an empty disposable database

Bootstrap created the first Admin and redirected to /login.
Login redirected to /admin/smtp?onboarding&continue.
SMTP onboarding rendered the shared Admin shell and persistent warning.
Steps 1-5 displayed all five required warnings in order; every step exposed Back and two visible Configure SMTP links (page action plus persistent warning); Finish counts were 0,0,0,0,1.
Finish navigated to /dashboard. The warning remained visible and its href was /admin/smtp.
Following the warning navigated to /admin/smtp with the warning still visible.

Every sampled SMTP, deferral, and dashboard page reported innerWidth=1365 and documentElement.scrollWidth=body.scrollWidth=1365.
Keyboard focus rendered outline 3px solid rgb(49, 87, 231).
Collapsed sidebar reported aria-expanded=false and exposed tooltip content "Overview" on keyboard focus; expanding restored aria-expanded=true.
Dark theme persisted across reload with data-theme=dark and body background rgb(11, 12, 14); theme.js head index 4 preceded app.css index 5. No flash was practically observed during the reload.
Edge console error/warning log: []

The Spring process ended through graceful shutdown with BUILD SUCCESS. The disposable PostgreSQL container stopped and was removed; ports 8080 and 55433 were no longer listening.
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=SecurityResponseIntegrationTest,BootstrapOnboardingWebIntegrationTest,AccountWebIntegrationTest,SmtpOnboardingWebIntegrationTest,RoleDashboardWebIntegrationTest,UiContractWebTest,AccountTemplateIntegrationTest,AttendanceTemplateIntegrationTest,DashboardControllerWebTest,DashboardTemplateWebTest,ProjectTaskFormAccessibilityWebTest,SharedErrorTemplateWebTest,ProjectControllerTest,TaskControllerTest,AttendanceControllerTest test

PostgreSQL 18.4 via Testcontainers
Tests run: 81, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 50.418 s
```

The final integrated PostgreSQL 18.4 suite also passed 197/197 tests with no failures, errors, or skips in 01:24. Java compile and full Javadoc/doclint each completed with `BUILD SUCCESS`.

## External-test boundaries

The real browser run proves the specified local Edge/Chromium desktop journey and observable shell behavior against Java and PostgreSQL. The practical theme-flash observation is not a frame-by-frame measurement. It does not test mobile/tablet layouts, real SMTP transport, production TLS configuration, or non-Edge engines. Automated integration tests separately prove active-SMTP suppression and non-Admin warning suppression without creating additional browser fixture accounts.
