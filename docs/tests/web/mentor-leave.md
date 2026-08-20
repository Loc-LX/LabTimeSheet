# Test Evidence: Mentor leave decisions page and approve/reject enforcement

- **Test type:** Web
- **Requirement IDs:** `LEV-008`
- **Scenario IDs:** `AC-LEV-010`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.controller.MentorLeaveWebIntegrationTest`
- **Implementation commit:** `5e9e598`

## Protected behavior

The Mentor leave page renders every request newest-first from
`LeaveService.decisions`, including the Intern display name, dates, reason,
counted days, status, and the decision boundary. A Mentor can approve or reject
a pending request before its boundary with an optional note; the re-rendered
page shows the stable flash message and the decided status. Only Mentors may
open the page — Intern and Admin sessions receive 403.

## Test method

`@SpringBootTest` + `@AutoConfigureMockMvc` + `TestcontainersConfiguration`
(fixed clock `2026-08-14T00:00:00Z`) + form-login via
`SecurityMockMvcRequestPostProcessors`, with a `@TestConfiguration @Primary`
`RecordingSmtpProbe` replacing the mail transport. An Admin is bootstrapped,
SMTP is activated, and an active Mentor plus an intern with internship
`2026-08-01 → 2026-12-31` are created/activated. The intern submits a request
through the real `/intern/leave` POST; the mentor logs in, sees it, approves
with a note, then rejects a second request. A second test asserts 403 for
Admin and Intern sessions on the Mentor page.

## Hand-derived expected result

After the intern submits `2026-09-01 → 2026-09-02` ("Family trip"), the Mentor
GET renders "Pending decisions", "Mentor Leave Intern", "Family trip", and
"PENDING". POSTing `/{id}/approve` with `decisionNote=Family first` redirects
to `/mentor/leave`; the re-rendered page contains
`Request {id} approved` and "APPROVED", and the persisted request has the note
and a deciding Mentor. Rejecting a second request shows `Request {id} rejected`
and "REJECTED". Admin and Intern sessions receive 403 on the Mentor page GET.

## RED

No RED was observed at this level: the service and integration RED steps drove
the implementation, and `MentorLeaveWebIntegrationTest` passed on first
execution, verifying the controller routes, CSRF binding, layout shell, and
role enforcement that the service tests cannot.

## GREEN

**Command**

```text
cmd /c "mvnw.cmd -Dtest=MentorLeaveWebIntegrationTest test"
```

**Observed result**

```text
Running com.lab.labtimesheet.feature.attendance.controller.MentorLeaveWebIntegrationTest
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 14.25 s
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
cmd /c "mvnw.cmd test"
Tests run: 285, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

MockMvc exercises the servlet, CSRF, form binding, and the layout shell but not
a real browser. The `RecordingSmtpProbe` replaces the real transport. The
corrections-focused Mentor workflow (revert and separate decision-window
locking) is deferred to I2-ATT-06.