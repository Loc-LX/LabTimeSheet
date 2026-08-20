# Test Evidence: Intern leave form, submission, and role enforcement

- **Test type:** Web
- **Requirement IDs:** `LEV-001`, `LEV-002`, `LEV-003`, `LEV-005`
- **Scenario IDs:** `AC-LEV-001`, `AC-LEV-002`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.controller.InternLeaveWebIntegrationTest`
- **Implementation commit:** `69b54eb`

## Protected behavior

The Intern leave form renders the monthly quota, reservation, and prior-request
sections from `LeaveService.overview`; submitting a full-day inclusive range
with a non-blank reason materializes the counted days through
`LeaveService.submit` and redirects with stable feedback; the re-rendered page
lists the prior request with its dates and `PENDING` status. Only Interns may
open the form or submit — Mentor and Admin sessions receive 403 on both GET and
POST.

## Test method

`@SpringBootTest` + `@AutoConfigureMockMvc` + `TestcontainersConfiguration`
(fixed clock `2026-08-14T00:00:00Z`) + form-login via
`SecurityMockMvcRequestPostProcessors`, with a `@TestConfiguration @Primary`
`RecordingSmtpProbe` replacing the mail transport. An Admin is bootstrapped,
SMTP is activated, and an intern with internship `2026-08-01 → 2026-12-31` is
created, activated, and given an active internship before form login.

## Hand-derived expected result

The intern GET renders "New request", "Monthly quota", and "Prior requests".
POSTing `startDate=2026-09-01`, `endDate=2026-09-03`, `reason=Family trip`
redirects to `/intern/leave`, and the re-rendered form contains "Family trip",
"PENDING", and the formatted range `01/09/2026 → 03/09/2026`. Admin and Mentor
sessions are denied with 403 on both the form GET and the submission POST.

## RED

**Command**

```text
cmd /c "mvnw.cmd -Dtest=InternLeaveWebIntegrationTest,AttendanceLombokBoilerplateTest test"
```

**Observed result**

```text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 (AttendanceLombokBoilerplateTest)
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 (InternLeaveWebIntegrationTest)
FAILURE -- in InternLeaveWebIntegrationTest
jakarta.servlet.ServletException: Request processing failed:
com.lab.labtimesheet.feature.attendance.exception.LeaveException: INACTIVE_INTERN
  at ... InternLeaveController.form(InternLeaveController.java:48)
Caused by: com.lab.labtimesheet.feature.attendance.exception.LeaveException: INACTIVE_INTERN
  at ... LeaveService.overview(LeaveService.java:129)
```

The web test exposed a test-setup gap: the intern was activated but their
internship had not been activated, so `LeaveService.overview` correctly threw
`INACTIVE_INTERN`. The helper now calls `accounts.activateInternship(...)`
after activation, matching the service-level seed pattern. A second run then
hit a Thymeleaf failure:

```text
Error resolving fragment: "${primaryAction}": template or fragment could not
be resolved (template: "fragments/layout" - line 55, col 78)
```

The new template passed the literal string `primaryAction=null,` to the shell
fragment; `th:replace="${primaryAction}"` interpreted "null" as a fragment
name. The template now passes the empty fragment `primaryAction=~{}` like the
other pages without a primary action.

## GREEN

**Command**

```text
cmd /c "mvnw.cmd -Dtest=InternLeaveWebIntegrationTest,AttendanceLombokBoilerplateTest test"
```

**Observed result**

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 (InternLeaveWebIntegrationTest)
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 (AttendanceLombokBoilerplateTest)
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
cmd /c "mvnw.cmd test"
Tests run: 257, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

MockMvc exercises the servlet, CSRF, form binding, and the layout shell but not
a real browser. The `RecordingSmtpProbe` replaces the real transport; SMTP
credentials and platform-side storage are out of scope here.

## Extension (I2-ATT-04)

`5e9e598` extended the Intern page with an Actions column and two routes:
`POST /intern/leave/{id}/edit` and `POST /intern/leave/{id}/cancel`, driven by
the new `PriorLeaveRequest.editable`/`cancellable` flags (pending/approved and
before the first counted start). The web test now additionally verifies that
editing to `2026-09-07` re-renders "Rescheduled" and the range
`07/09/2026 → 07/09/2026` with the "Leave updated to 1 counted day(s)" message,
and that cancelling re-renders "CANCELLED". The intern web suite is now
`Tests run: 3`; the decision flows are covered by
`docs/tests/web/mentor-leave.md`, and the boundary/overlap/decide/cancel/edit
service semantics by `docs/tests/unit/leave-lifecycle.md` and
`docs/tests/integration/leave-workflow.md`.