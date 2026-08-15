# Test Evidence: Attendance and global-calendar web authorization

- **Test type:** Web
- **Requirement IDs:** `AUTH-001`, `AUTH-002`, `AUTH-003`, `ATT-007`, `ATT-010`, `ATT-016`, `CAL-001`, `CAL-007`, `RPT-004`, `UI-013`
- **Scenario IDs:** `AC-ATT-003`, `AC-ATT-004`, `AC-CAL-004`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.controller.AttendanceControllerTest`
- **Implementation commit:** `8b48e281f7e860af435ae35b16c4edeb139286dc`

## Protected behavior

Authenticated Intern punch routes use the server-resolved user ID, own history
renders attached policy details, Mentor inspection routes preserve the target
scope, and calendar management rejects non-Admin access. Calendar updates carry
the submitted optimistic version. History renders policy-local 24-hour times,
`dd/MM/yyyy` dates, and every simultaneous violation; `On time` appears only
when no violation applies.

## Test method

`@WebMvcTest` runs Spring Security filters, CSRF protection, MVC binding, route
selection, controller authorization, Thymeleaf rendering, and service-call
arguments while mocking only application-service and current-user boundaries.
The presentation regression supplies a row that is both late and early and
asserts the attached Asia/Ho_Chi_Minh timezone conversion.

## Hand-derived expected result

An Intern authenticated as user 42 can punch only ID 42. A Mentor can inspect
target 42 but receives HTTP 403 for Admin calendar management. Attached policy
grace renders as `30 min`. An event form with version 3 calls update with 3.
`2026-08-14T02:00:00.001Z` renders as local `09:00`, and a 15:00 local checkout
on that late row renders both `Late` and `Early departure`, never `On time`.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=AttendanceControllerTest test
```

**Observed result**

```text
[ERROR] cannot find symbol: class AttendanceCurrentUserService
[ERROR] cannot find symbol: class AttendanceController
[ERROR] cannot find symbol: class CalendarController
[INFO] 3 errors
[INFO] BUILD FAILURE
Process exited 1 because the required authenticated web endpoints did not exist.
```

The review presentation regression was separately observed RED:

```text
./mvnw -Dtest=AttendanceApplicationServiceTest,AttendanceControllerTest test
AttendanceControllerTest.historyRendersPolicyLocalDisplayValuesAndEveryViolation:
Expected a string containing "14/08/2026" but rendered "2026-08-14";
the same row rendered one nested-ternary result, "Early departure", and raw UTC instants.
Tests run: 13, Failures: 3, Errors: 1, Skipped: 0
BUILD FAILURE
Process exited 1. The eligible behavioral failures were the missing local presentation
values and simultaneous violation output; the checkout fixture error was corrected
before its own focused RED and is not claimed as behavioral evidence.
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=AttendanceControllerTest test
```

**Observed result**

```text
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Process exited 0.
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest='*Attendance*Test' test
Tests run: 32, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Process exited 0.
```

## External-test boundaries

This MVC slice does not prove the platform's production login/session setup,
shared shell and navigation, browser layout, or accessibility beyond semantic
labels, table headers, status roles, CSRF, and route authorization.
