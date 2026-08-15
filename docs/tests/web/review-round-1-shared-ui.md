# Test Evidence: round-one shared UI corrections

- **Test type:** Web
- **Requirement IDs:** `AUTH-002`, `UI-003`, `UI-004`, `UI-010`, `UI-013`, `UI-014`, `ERR-001`, `I1-UI-01`, `I1-UI-02`, `I1-UI-04`
- **Scenario IDs:** `AC-AUTH-001`, `AC-UI-002`, `AC-UI-003`, `AC-UI-005`
- **Test class/method:** `com.lab.labtimesheet.ui.UiContractWebTest`, `com.lab.labtimesheet.feature.reporting.controller.AttendanceTemplateIntegrationTest#populatedHistoryUsesPolicyLocalPresentationAndListsEveryViolation`, `com.lab.labtimesheet.feature.reporting.controller.SharedErrorTemplateWebTest`, `com.lab.labtimesheet.feature.reporting.controller.ProjectTaskFormAccessibilityWebTest`, `com.lab.labtimesheet.feature.reporting.controller.RoleDashboardWebIntegrationTest#mentorAndInternDashboardsRenderRealScopedProjectTaskAndAttendanceData`
- **Implementation commit:** `c81c0df` with deterministic asset follow-up `cb76bea`

## Protected behavior

The authenticated shell exposes only reachable role-authorized links. Intern attendance uses `/attendance`; Mentor attendance, profile, and notification links remain hidden until their authorized destination flows exist. Every rendered role-navigation link resolves through an actual authenticated GET. Attendance history uses the row's attached policy timezone for 24-hour times, formats business dates as `dd/MM/yyyy`, and renders every simultaneous violation. Project and Task field errors have stable IDs associated to invalid controls. Generic 404 and 409 pages use the shared shell and safe caller-supplied copy without rendering exception details. The collapsed desktop sidebar exposes its current state, keeps every control within its rail, and gives icon-only navigation a visible keyboard-focus tooltip. Both shared shells explicitly reference a local favicon so browser console checks do not depend on an unmapped `/favicon.ico` request.

## Test method

MockMvc renders the production shell and templates with real Spring Security principals and production-shaped Attendance DTOs. Project and Task invalid POSTs pass through their real controllers and validation, with only feature services replaced at the slice boundary. The full Spring/PostgreSQL role journey creates accounts, internship, Project, and Task through public services, renders each role's real dashboard, extracts every visible shell link, and performs an authenticated GET against each extracted path. A desktop browser then exercises the real local Java process at 1365x900 for all three roles, inspecting focus, tooltip pseudo-content, runtime `aria-expanded`, theme persistence/head ordering, console output, and document overflow.

## Hand-derived expected result

Mentor navigation contains only overview and owned Projects; Intern navigation contains overview, `/attendance`, and Projects; Admin navigation contains overview, account creation, and global calendar. No role receives `/attendance/me`, `/profile`, `/notifications`, or a selector-less Mentor attendance destination. `2026-08-14T02:05:00Z` under `Asia/Ho_Chi_Minh` renders as `14/08/2026 09:05`; `09:00:00Z` renders as `16:00`. Late plus early-departure and late plus missing-checkout labels are both retained. Every rendered validation message has a stable referenced ID. Error pages expose only status and generic copy. At 1365x900, root and body scroll widths remain 1365, the collapsed toggle reports `aria-expanded=false`, expanding reports `true`, and keyboard focus exposes the corresponding control name without horizontal overflow.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=UiContractWebTest,AttendanceTemplateIntegrationTest,SharedErrorTemplateWebTest,ProjectTaskFormAccessibilityWebTest test
./mvnw -Dtest=RoleDashboardWebIntegrationTest test
./mvnw -Dtest=ProjectControllerTest,AttendanceControllerTest,TaskControllerTest test
./mvnw -Dtest=UiContractWebTest#collapsedSidebarExposesStateAndKeyboardVisibleControlNames test
./mvnw -Dtest=UiContractWebTest#mentorShellRendersOnlyReachableAuthorizedNavigation test
```

**Observed result**

```text
Focused templates: Tests run: 13, Failures: 6, Errors: 2, Skipped: 0
Navigation exposed /attendance/me, selector-less Mentor attendance, /profile, and /notifications.
Attendance rendered ISO dates/raw UTC instants and only one violation.
error/generic did not exist.
Invalid controls had no aria-describedby and inline errors had no stable IDs.

PostgreSQL 18.4 role journey: Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
Following the Admin shell's visible /profile link returned 404 instead of 200.

After the four reviewed producer pins were merged, the three producer WebMvc slices ran 39 tests with 39 context errors. The merged Platform SmtpWarningAdvice required SmtpConfigurationService, which was absent only from those narrow slice fixtures; no behavior assertion ran.

The collapsed-sidebar regression failed 1/1 at the missing aria-expanded assertion. The favicon regression failed 1/1 because the rendered shared shell had no explicit local icon link; the real browser independently logged /favicon.ico as 404.

The first post-Maven deterministic asset check changed app.css because Tailwind automatic source discovery included generated target output; a generated ring token changed the production bundle without any source-template change.
BUILD FAILURE
```

The failures occurred after real template rendering and controller validation, or at an exact missing merged slice dependency; they identify the missing reviewed behavior or fixture boundary rather than an unrelated environment failure.

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw clean -Dtest=AccountTemplateIntegrationTest,AttendanceTemplateIntegrationTest,DashboardControllerWebTest,DashboardTemplateWebTest,ProjectTaskFormAccessibilityWebTest,SharedErrorTemplateWebTest,UiContractWebTest test
./mvnw -Dtest=RoleDashboardWebIntegrationTest test
./mvnw -Dtest=ProjectControllerTest,AttendanceControllerTest,TaskControllerTest test
./mvnw -Dtest=UiContractWebTest#collapsedSidebarExposesStateAndKeyboardVisibleControlNames test
./mvnw -Dtest=UiContractWebTest#mentorShellRendersOnlyReachableAuthorizedNavigation test
```

**Observed result**

```text
Post-merge clean Reporting/UI slices: Tests run: 26, Failures: 0, Errors: 0, Skipped: 0
PostgreSQL 18.4 role journey: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
Producer WebMvc slices: Tests run: 39, Failures: 0, Errors: 0, Skipped: 0
Collapsed sidebar: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
Local favicon contract: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
npm run build
./mvnw -Dtest=SecurityResponseIntegrationTest,BootstrapOnboardingWebIntegrationTest,AccountWebIntegrationTest,SmtpOnboardingWebIntegrationTest,RoleDashboardWebIntegrationTest,UiContractWebTest,AccountTemplateIntegrationTest,AttendanceTemplateIntegrationTest,DashboardControllerWebTest,DashboardTemplateWebTest,ProjectTaskFormAccessibilityWebTest,SharedErrorTemplateWebTest,ProjectControllerTest,TaskControllerTest,AttendanceControllerTest test
./mvnw -DskipTests compile
./mvnw -DskipTests -Ddoclint=all javadoc:javadoc

Node v24.19.0; npm 11.17.0
Tailwind CSS v4.3.3: Done
Two consecutive builds produced app.css SHA-256 7bd351d0f2cae97af70532e8ee0e0248265782b508d378fd7c277cbb4f373946 and icons.svg SHA-256 001f72c93967f816fdd56f3f9b34cb5e5831b8b8c572d051669c6a3aae2c3cda.
Merged affected web suite on PostgreSQL 18.4: Tests run: 79, Failures: 0, Errors: 0, Skipped: 0
Full PostgreSQL 18.4 suite: Tests run: 195, Failures: 0, Errors: 0, Skipped: 0
Compile: success
Full Javadoc/doclint: success (producer-owned missing-comment warnings remain non-fatal)
BUILD SUCCESS
```

## External-test boundaries

The automated checks prove rendering, controller validation, role-scoped navigation targets, attached-policy formatting, generic error copy, public local assets, safe Referrer-Policy, and retained safe form fields. Edge/Chromium desktop checks against the real local Java/PostgreSQL process covered Admin dashboard, Mentor dashboard/Projects, and Intern dashboard/attendance: every representative page had `documentElement.scrollWidth == body.scrollWidth == innerWidth == 1365`; keyboard focus showed a solid focus ring and tooltip; collapse/expand synchronized `aria-expanded`; the theme bootstrap preceded CSS and survived reload; console checks were empty after the explicit local favicon link. A human-observed no-flash check is inherently practical rather than deterministic, and mobile remains outside Iteration 1 scope.
