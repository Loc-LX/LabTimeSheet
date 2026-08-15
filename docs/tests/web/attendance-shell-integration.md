# Test Evidence: attendance shell integration

- **Test type:** Web
- **Requirement IDs:** `UI-001`, `UI-002`, `UI-003`, `UI-008`, `UI-013`, `I1-ATT-03`, `I1-UI-04`
- **Scenario IDs:** `AC-ATT-003`, `AC-ATT-004`, `AC-UI-001`, `AC-UI-005`
- **Test class/method:** `com.lab.labtimesheet.feature.reporting.controller.AttendanceTemplateIntegrationTest`
- **Implementation commit:** `3064485`

## Protected behavior

The Intern attendance-history and Admin global-calendar pages consume the role-aware shared shell while preserving their existing routes, CSRF-protected mutation forms, filter values, empty states, and local theme assets. Populated history presents `dd/MM/yyyy` dates and 24-hour times in the attached policy timezone and does not collapse simultaneous violations.

## Test method

A focused MockMvc slice supplies empty and populated production-shaped models to the two production Attendance templates and renders them with role-specific Spring Security principals. The populated fixture uses UTC instants, the attached `Asia/Ho_Chi_Minh` seeded policy, and late-plus-early and late-plus-missing combinations. The owning feature's `AttendanceControllerTest` remains the affected behavioral suite for authorization, punch actions, calendar mutation, and view selection.

## Hand-derived expected result

Both responses contain `app-shell` and `/assets/theme.js`. Intern history posts to `/attendance/check-in` and `/attendance/check-out` and renders its empty period state. Admin calendar posts to `/attendance/calendar` and renders its empty upcoming-events state. The shell highlights the real attendance/calendar route for the current role.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=AttendanceTemplateIntegrationTest test
```

**Observed result**

```text
Tests run: 2, Failures: 2, Errors: 0, Skipped: 0
AttendanceTemplateIntegrationTest.adminCalendarUsesSharedShellAndPreservesEventForm expected class="app-shell"
AttendanceTemplateIntegrationTest.internHistoryUsesSharedShellAndPreservesPunchActions expected class="app-shell"
BUILD FAILURE
Total time: 4.977 s
```

Both Attendance templates were standalone HTML documents.

## GREEN

**Command**

```text
export PATH="/opt/homebrew/opt/node@24/bin:$PATH"
npm run build
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=AttendanceTemplateIntegrationTest test
```

**Observed result**

```text
Tailwind CSS v4.3.3: Done in 72ms
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 3.832 s
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=AttendanceTemplateIntegrationTest,AttendanceControllerTest test

Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 3.974 s
```

## External-test boundaries

The checks prove server-rendered shell integration and preserve the owning controller's tested contracts. They do not exercise PostgreSQL attendance persistence, live punch timing, browser overflow, or the visual state of populated editable calendar rows; those remain covered by the Attendance feature suite and final integrated UI checks.
