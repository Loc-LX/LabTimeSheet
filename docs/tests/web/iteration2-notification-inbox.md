# Test Evidence: Iteration 2 authenticated notification inbox and dashboard access

- **Test type:** Web
- **Requirement IDs:** `I2-UI-01`, `AUTH-001`, `AUTH-002`, `NOT-009`, `UI-004`, `UI-010`
- **Scenario IDs:** `AC-AUTH-001`
- **Test class/method:** `com.lab.labtimesheet.feature.reporting.controller.DashboardControllerWebTest#dashboardLoadsRecipientScopedNotificationsAndHeaderAccess`, `com.lab.labtimesheet.feature.reporting.controller.NotificationControllerWebTest`
- **Implementation commit:** `96c6cf2866e00062860a0c0b45cb010be6ca30b1`

## Protected behavior

Authenticated dashboards and the notification inbox consume the Platform-owned recipient-scoped `NotificationInbox` DTO. The server resolves the recipient from the authenticated account rather than a request parameter, exposes only an accessible header/inbox entry point, and routes mark-read through the authenticated recipient boundary. Unknown or foreign notification identifiers remain non-disclosing and idempotent at the producer service boundary.

## Test method

The dashboard MVC slice supplies an active Admin identity and one concrete `NotificationInbox`, then asserts the model, rendered notification title, unread accessible label, and authenticated mark-read action. The notification MVC slice exercises the GET inbox and CSRF-protected POST mark-read route with the same account lookup boundary. These are the narrowest production-shaped checks for routing/model composition; producer persistence and foreign-ID behavior remain covered by Platform integration tests.

## Hand-derived expected result

Account `9` receives exactly one unread notification (`Task comment`, ID `41`). The dashboard renders `Notifications, 1 unread` and a POST action for `/notifications/41/read`; no caller-supplied recipient identifier is accepted.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=DashboardControllerWebTest#dashboardLoadsRecipientScopedNotificationsAndHeaderAccess' test
```

**Observed result**

```text
The test reached DashboardController and rendered dashboard/admin, but failed the production-shaped assertion because model attribute `notifications` was null; the concrete NotificationInbox was not yet loaded and the header/read action were absent.
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=DashboardControllerWebTest,NotificationControllerWebTest,DashboardTemplateWebTest' test
```

**Observed result**

```text
`Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS` on Java 25.
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=DashboardControllerWebTest,NotificationControllerWebTest,DashboardTemplateWebTest,Iteration2ComponentsWebTest,Iteration2WorkflowFragmentsWebTest,UiContractWebTest,ProjectTaskReportControllerWebTest,ProjectTaskReportServiceTest,AttendanceReportControllerWebTest' test

Tests run: 34, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS on Java 25 after the reviewed Project producer merge and report-fixture repair. The PostgreSQL-backed AdminDashboardWebTest also passed separately: 3/3 against PostgreSQL 18.4 via OrbStack.
```

## External-test boundaries

This MVC evidence does not prove PostgreSQL notification persistence, Platform delivery/retry state, the producer's foreign-recipient mark-read no-op, real browser keyboard/focus behavior, or cross-feature notification publication. Those remain covered by Platform tests and a later authorized desktop/E2E boundary.
