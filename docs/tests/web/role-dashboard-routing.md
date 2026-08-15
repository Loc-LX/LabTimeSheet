# Test Evidence: role dashboard routing and service composition

- **Test type:** Web and unit
- **Requirement IDs:** `AUTH-003`, `UI-003`, `UI-013`, `I1-UI-03`
- **Scenario IDs:** `AC-AUTH-002`, `AC-UI-005`
- **Test class/method:** `com.lab.labtimesheet.feature.reporting.service.DashboardServiceTest`, `com.lab.labtimesheet.feature.reporting.controller.DashboardControllerWebTest`, `com.lab.labtimesheet.feature.reporting.controller.AdminDashboardWebTest`, `com.lab.labtimesheet.feature.reporting.controller.RoleDashboardWebIntegrationTest`, `com.lab.labtimesheet.feature.reporting.ReportingArchitectureTest`
- **Implementation and integration-test commits:** `b1c6b17`, `cbdbd8e`

## Protected behavior

`/dashboard` selects exactly one role template from the authenticated authority, while all displayed data is authorized again from the persisted account identity. Reporting composes public Account, Project, Task, and Attendance service DTOs; it owns no shadow account entity, repository, direct SQL, or business date calculation.

## Test method

The unit test supplies mocked concrete public feature services to the reporting coordinator and independently checks the exact Admin, Mentor, and Intern view DTOs, including Task-status and attendance-state translation. Negative cases prove that a forged authority, locked account, missing account, or inactive internship cannot produce a dashboard. The MVC slice proves role-to-template routing and authentication. PostgreSQL web tests bootstrap a real Admin and create/activate Mentor and Intern identities, SMTP configuration, a Project, and a Task only through public application services; they then exercise all three authenticated dashboard roles without repository, entity, JDBC, or SQL fixtures.

## Hand-derived expected result

An active Admin sees account totals plus active Project count. An active Mentor sees their display name, visible active Project count, distinct active eligible member count, and blocked Task count. An eligible Intern sees the server-authoritative attendance state, active Project count, assigned Task count, and the Task service's ordered priority list. Unsupported roles and identities that do not satisfy the persisted role/lifecycle checks receive HTTP 403.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=DashboardServiceTest,DashboardControllerWebTest test
```

**Observed result**

```text
DashboardService constructor required DashboardRepository and did not accept TaskDashboardService or AttendanceApplicationService.
DashboardService.intern required a caller-supplied LocalDate instead of using AttendanceApplicationService.currentState.
Tests failed during compilation with 5 errors.
BUILD FAILURE
Total time: 6.645 s
```

The focused contract could not compile against the temporary reporting-owned persistence implementation, which is the intended missing behavior.

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=DashboardServiceTest,DashboardControllerWebTest,ReportingArchitectureTest test
```

**Observed result**

```text
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 6.145 s
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=DashboardServiceTest,DashboardControllerWebTest,AdminDashboardWebTest,ReportingArchitectureTest,DashboardTemplateWebTest test

PostgreSQL 18.4 via Testcontainers
Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 11.409 s

Production-shaped Mentor/Intern query journey:

./mvnw -Dtest=RoleDashboardWebIntegrationTest test
PostgreSQL 18.4 via Testcontainers
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 11.119 s
```

## External-test boundaries

The focused tests prove reporting composition, route selection, denial behavior, and production-shaped Admin, Mentor, and Intern journeys. Feature-owned suites separately prove additional Project, Task, Attendance, and Account query semantics. Browser viewport behavior remains an external UI boundary.
