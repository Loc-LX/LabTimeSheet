# Test Evidence: persistent Admin SMTP settings navigation

- **Test type:** Web
- **Requirement IDs:** `ACC-007`, `INT-001`, `AUTH-002`, `UI-003`, `UI-008`, `UI-009`, `UI-010`
- **Scenario IDs:** `AC-ACC-003`, `AC-UI-002`, `AC-UI-003`
- **Test class/method:** `com.lab.labtimesheet.feature.reporting.controller.DashboardControllerWebTest`, `com.lab.labtimesheet.feature.reporting.controller.RoleDashboardWebIntegrationTest#mentorAndInternDashboardsRenderRealScopedProjectTaskAndAttendanceData`
- **Implementation commit:** pending

## Protected behavior

An Admin always receives an SMTP settings destination in the shared sidebar, whether SMTP is restricted or active. The restricted-installation warning remains conditional. Mentor and Intern sidebars never expose the Admin-only destination, and the Admin link uses the local settings sprite plus the established collapsed-sidebar tooltip.

## Test method

The MVC slice renders the real dashboard controller, Spring Security Thymeleaf dialect, and shared layout with only the SMTP state and dashboard query services mocked at their public boundaries. It checks both Admin SMTP states and the active-SMTP Mentor/Intern views. The PostgreSQL 18.4 integration test activates SMTP through the real service, extracts rendered navigation links, requires the Admin SMTP route only for Admin, and follows every discovered link through the real controller/security stack.

## Hand-derived expected result

With SMTP restricted, an Admin dashboard contains the existing warning and a sidebar link to `/admin/smtp` identified by `data-tooltip="SMTP settings"`. After SMTP activation, the warning is absent but that same sidebar link remains. Mentor and Intern dashboards omit the SMTP-settings tooltip and route. The activated Admin link resolves successfully when followed.

## RED

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/opt/node@24/bin:$PATH ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=DashboardControllerWebTest test
```

**Observed result**

```text
Tests run: 6, Failures: 2, Errors: 0, Skipped: 0
DashboardControllerWebTest.adminRendersAdminDashboardForAuthenticatedIdentity: expected data-tooltip="SMTP settings" but it was absent.
DashboardControllerWebTest.activeSmtpKeepsAdminDashboardFreeOfTheRestrictedInstallationWarning: expected href="/admin/smtp" data-tooltip="SMTP settings" but it was absent.
BUILD FAILURE
```

The Mentor and Intern active-SMTP assertions passed in this RED run, so the failures establish the missing Admin navigation rather than an incorrect role fixture.

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/opt/node@24/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=RoleDashboardWebIntegrationTest test
```

**Observed result**

```text
PostgreSQL: 18.4 Testcontainer
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
Expected Admin visible navigation paths to contain /admin/smtp, but rendered paths were /dashboard, /admin/accounts/new, /attendance/calendar.
BUILD FAILURE
```

## GREEN

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/opt/node@24/bin:$PATH ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=DashboardControllerWebTest test

JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/opt/node@24/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=RoleDashboardWebIntegrationTest test
```

**Observed result**

```text
DashboardControllerWebTest: Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
RoleDashboardWebIntegrationTest: PostgreSQL 18.4 Testcontainer; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
PATH=/opt/homebrew/opt/node@24/bin:$PATH node --version && PATH=/opt/homebrew/opt/node@24/bin:$PATH npm --version && PATH=/opt/homebrew/opt/node@24/bin:$PATH npm ci && PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run build
Node v24.19.0; npm 11.17.0; Tailwind CSS v4.3.3
BUILD SUCCESS

JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/opt/node@24/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=SecurityResponseIntegrationTest,BootstrapOnboardingWebIntegrationTest,AccountWebIntegrationTest,SmtpOnboardingWebIntegrationTest,RoleDashboardWebIntegrationTest,UiContractWebTest,AccountTemplateIntegrationTest,AttendanceTemplateIntegrationTest,DashboardControllerWebTest,DashboardTemplateWebTest,ProjectTaskFormAccessibilityWebTest,SharedErrorTemplateWebTest,ProjectControllerTest,TaskControllerTest,AttendanceControllerTest test
PostgreSQL 18.4 Testcontainers; Tests run: 81, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

MockMvc proves rendered role/state visibility, while the PostgreSQL integration test proves the real Admin route follow. They do not render the collapsed rail or inspect pixels, browser focus placement, or tooltip positioning; the existing CSS and local settings sprite are reused unchanged. Server-side direct-URL authorization remains the existing `/admin/**` Admin-only security rule and is not broadened by this layout-only change.
