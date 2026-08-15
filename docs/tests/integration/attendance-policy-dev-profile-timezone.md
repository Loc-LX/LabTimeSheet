# Test Evidence: development-profile attendance policy time hydration

- **Test type:** Integration
- **Requirement IDs:** `ATT-002`, `ATT-003`, `I1-ATT-01`
- **Scenario IDs:** `AC-ATT-001`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.controller.CalendarDevelopmentProfileWebIntegrationTest#v1SeededPolicyLetsFormAuthenticatedAdminOpenCalendarInAsiaHoChiMinhDevelopmentProfile`
- **Implementation commit:** pending

## Protected behavior

The unmodified V1 attendance policy must hydrate its `time` schedule as the configured local wall-clock values when the development profile runs in `Asia/Ho_Chi_Minh`. A form-authenticated Admin can therefore open the calendar without weakening the policy rule that requires the checkout cutoff to be before local midnight.

## Test method

The test starts the application with the real `dev` profile plus isolated test configuration, forces the JVM default zone to `Asia/Ho_Chi_Minh` before JPA starts, and uses PostgreSQL 18.4 Testcontainers with Flyway V1. It bootstraps an Admin through the form, logs in through the form, and requests `/attendance/calendar`, which resolves the current policy through `AttendanceApplicationService.currentBusinessDate`.

## Hand-derived expected result

V1 explicitly stores `scheduled_start = 08:30`, `scheduled_end = 15:30`, and `checkout_grace_minutes = 30`. The checkout cutoff is therefore `16:00`, which is strictly before local midnight, so the calendar request returns HTTP 200.

## RED

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=CalendarDevelopmentProfileWebIntegrationTest' test
```

**Observed result**

```text
PostgreSQL 18.4 Testcontainers applied Flyway V1, then the form-authenticated GET /attendance/calendar failed.
BUILD FAILURE: CalendarDevelopmentProfileWebIntegrationTest ... ServletException caused by
IllegalArgumentException: checkout cutoff must be before local midnight
at AttendancePolicy.java:58 via AttendancePolicyEntity.toDomain and AttendanceApplicationService.currentBusinessDate.
```

## GREEN

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=CalendarDevelopmentProfileWebIntegrationTest' test
```

**Observed result**

```text
PostgreSQL 18.4 Testcontainers applied Flyway V1.
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=CalendarAuthorizationWebIntegrationTest,CalendarDevelopmentProfileWebIntegrationTest,RoleDashboardWebIntegrationTest,AttendancePersistenceIntegrationTest,AttendancePolicyTest' test

Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=*Attendance*Test,*Calendar*Test,Dashboard*Test,RoleDashboardWebIntegrationTest,AdminDashboardWebTest' test

Tests run: 60, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Java 26 smoke

**Command and result**

```text
env JAVA_HOME=/Users/sechmachine/Library/Java/JavaVirtualMachines/corretto-26.0.2/Contents/Home PATH=/Users/sechmachine/Library/Java/JavaVirtualMachines/corretto-26.0.2/Contents/Home/bin:/opt/homebrew/bin:/usr/bin:/bin ./mvnw clean compile -DskipTests

Amazon Corretto 26.0.2 compiled 129 source files with release 25.
BUILD SUCCESS
```

## External-test boundaries

This integration test proves the fresh Flyway/JPA/real-login calendar path under the development profile and Vietnam JVM zone. It does not operate the already-running browser-gate application or exercise the Intern dashboard UI itself; both paths resolve the same policy timeline.
