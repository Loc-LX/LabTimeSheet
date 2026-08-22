# Test Evidence: Iteration 3 focused Attendance UI workflows

- **Test type:** Web
- **Requirement IDs:** `I3-UI-08`, `I3-UI-09`, `I3-ATT-06`, `I3-ATT-07`
- **Scenario IDs:** focused Policy/Calendar navigation; split Leave/Correction workflows and monthly balance
- **Test class/method:** `UiContractWebTest#mentorShellRendersOnlyReachableAuthorizedNavigation`; `UiContractWebTest#internShellLinksToTheReachableOwnAttendanceRoute`; `AttendanceTemplateIntegrationTest#focusedAdminPolicyUsesNativeMonthFormAndRetainedHistoryPanel`; `AttendanceTemplateIntegrationTest#leaveAndCorrectionPagesRemainSeparateAndHumanizeStatuses`
- **Implementation commit:** `85eec694632a99c58838e9503983790e2e47db08`; navigation/E2E follow-up `8c9a9e5181802181786bd00a892106573dc232be`

## Protected behavior

The reviewed Attendance producer exposes separate Policy, Calendar, Leave, and Correction
routes. Reports/UI navigation must expose those public workflows by role without the superseded
combined request link. Intern Leave displays the selected monthly reservation balance; Mentor pages
display global decision queues; statuses are human-readable and the focused forms remain native,
labelled, and server-rendered.

## Test method

The shell contract renders the shared layout as Admin, Mentor, and Intern users and checks only
role-reachable links. Template integration fixtures bind the reviewed producer DTOs directly,
rendering the policy month form, retained history panel, split Leave/Correction pages, balance
values, and pending status label without importing Attendance repositories or entities.

## Hand-derived expected result

The Intern sees `My Leave` and `My Corrections`; the Mentor sees `Leave decisions` and
`Correction decisions`; Admin sees `Attendance Policy`, `Global calendar`, and `Holiday Import`.
The fixture balance is 3 reserved / 12 quota / 9 remaining, and `PENDING` renders as
`Pending decision`.

## RED

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=UiContractWebTest test
```

**Observed result**

The new shell assertions failed 3/7 because the shared layout still rendered the combined
`/attendance/requests` link and did not expose the focused Leave/Correction, Attendance Policy,
or Holiday Import labels. This established missing UI behavior before template changes.

## GREEN

**Commands and results**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=UiContractWebTest test
# BUILD SUCCESS — 7 tests, 0 failures, 0 errors

JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=AttendanceTemplateIntegrationTest test
# BUILD SUCCESS — 5 tests, 0 failures, 0 errors
```

## Affected suite

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest=AttendancePolicyControllerWebTest,CalendarControllerWebTest,AttendanceRequestControllerWebTest,AttendanceTemplateIntegrationTest,CalendarAuthorizationWebIntegrationTest test
# BUILD SUCCESS — 31 tests, 0 failures, 0 errors

npm run test:ui
# 12 tests passed

npm run build
# CSS, icon, and chart asset builds completed successfully
```

The post-Attendance architecture/dependency/Flyway selection passed 14/14. The focused report
export route/service selection passed 13/13, and the new exporter evidence proves shared totals,
Vietnamese extraction, and an embedded `/FontFile` marker in `2a26ceb661c065e5a7908a0b76603df967f36d33`.

## External-test boundaries

The MVC/template fixtures do not prove live PostgreSQL authorization, live SMTP/HolidayAPI
delivery, or browser interaction. The exact pinned Chromium artifact is installed, but Playwright
cannot find its `chromium_headless_shell-1187` companion on this host and fallback system Chrome
aborts under the sandbox. The Java service also cannot start because documented `LAB_SMTP_HOST` and
`LAB_DB_*` environment-backed development services were not configured; no disposable database,
SMTP service, or credential was created or changed. The credential-gated Leave/Correction and
XLSX/PDF Playwright journeys are implemented in `src/test/e2e/report-journeys.spec.mjs` and remain
unexecuted until managed browser artifacts, services, and authorized credentials are supplied.
