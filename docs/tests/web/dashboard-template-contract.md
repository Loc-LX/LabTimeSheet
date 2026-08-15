# Test Evidence: role dashboard template contract

- **Test type:** Web
- **Requirement IDs:** `AUTH-003`, `UI-003`, `UI-013`, `I1-UI-03`
- **Scenario IDs:** `AC-AUTH-002`, `AC-UI-005`
- **Test class/method:** `com.lab.labtimesheet.feature.reporting.ReportingArchitectureTest`, `com.lab.labtimesheet.feature.reporting.controller.DashboardTemplateWebTest`
- **Implementation commit:** `5638286`, `f8db8a3`

## Protected behavior

Reporting-owned Java starts under `com.lab.labtimesheet.feature.reporting` rather than global layer packages or a placeholder module marker. The Admin, Mentor, and Intern dashboard templates consume typed view DTOs and render role-correct metrics, actions, attendance state, and `dd/MM/yyyy` dates without illustrative production data.

## Test method

The architecture test loads the reporting view contract and rejects the superseded global-layer and module-boundary classes. A narrow MVC test controller supplies explicit DTO fixtures to the production Thymeleaf templates so their rendering contract can be verified before cross-feature service APIs are integrated. It does not replace the later database-backed `/dashboard` test.

## Hand-derived expected result

Admin markup contains system metrics and `Create account`; Mentor markup contains owned Project, eligible-member, and blocked-Task summaries plus `Create Project`; Intern markup contains attendance, active-Project and assigned-Task summaries, only the supplied assigned Task, `Check out`, and due date `18/08/2026`. Unsupported pending-decision and unread-notification metrics are absent. Actions belonging to other roles are absent.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
./mvnw clean -Dtest=ReportingArchitectureTest test
./mvnw clean -Dtest=DashboardTemplateWebTest test
```

**Observed result**

```text
ReportingArchitectureTest: ClassNotFoundException: com.lab.labtimesheet.feature.reporting.model.dto.DashboardView
Tests run: 1, Failures: 0, Errors: 1, Skipped: 0

DashboardTemplateWebTest: Error resolving template [dashboard/admin], [dashboard/mentor], and [dashboard/intern]
Tests run: 3, Failures: 0, Errors: 3, Skipped: 0
BUILD FAILURE
```

The final feature package DTO and the three production dashboard templates were absent in the respective pre-implementation states.

After cross-feature I1 service boundaries were established, a second focused RED caught two metrics without I1 service authority:

```text
./mvnw -Dtest=DashboardTemplateWebTest test
Tests run: 3, Failures: 2, Errors: 0, Skipped: 0
mentorTemplateRendersOwnedScopeAndOnlyMentorAction expected not "Pending decisions"
internTemplateRendersOwnWorkAndAttendanceAction expected not "Unread notifications"
BUILD FAILURE
Total time: 5.215 s
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
./mvnw clean -Dtest=ReportingArchitectureTest,DashboardTemplateWebTest test
```

**Observed result**

```text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 25.380 s
```

Unsupported-metric correction:

```text
npm run build
./mvnw -Dtest=DashboardTemplateWebTest test
Tailwind CSS v4.3.3: Done in 72ms
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 6.254 s
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
npm run build
./mvnw -Dtest=UiContractWebTest,ReportingArchitectureTest,DashboardTemplateWebTest test

v24.19.0 / npm 11.17.0
Tailwind CSS v4.3.3: Done in 56ms
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 24.823 s
```

## External-test boundaries

These tests prove package and template contracts with controlled view DTOs. The separate role-dashboard routing evidence covers the now-complete cross-feature service composition and PostgreSQL-backed Admin route. Browser viewport, contrast, and pre-paint behavior remain integrated UI gates.
