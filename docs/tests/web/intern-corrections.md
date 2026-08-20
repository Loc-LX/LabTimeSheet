# Test Evidence: Intern correction web form, flash feedback, and role authorization

- **Test type:** Web
- **Requirement IDs:** `COR-001`, `COR-002`, `COR-003`, `AUTH-001`, `AUTH-002`, `AUTH-003`, `UI-013`
- **Scenario IDs:** `AC-COR-001`, `AC-COR-003`, `AC-COR-005`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.controller.InternCorrectionWebIntegrationTest`
- **Implementation commit:** `4c09382`

## Protected behavior

Authenticated Interns can open `/intern/corrections`, submit a missed-checkout
correction with a policy-local work date, checkout time, and reason, and see the
submission flash message plus the prior-correction row (policy-local `dd/MM/yyyy`
date, `HH:mm` checkout, reason, and `PENDING` status). A duplicate submission
flows back as a stable `ALREADY_SUBMITTED` error flash message. Mentor and Admin
roles receive HTTP 403 on both the form and the submit route.

## Test method

A full `@SpringBootTest` with MockMvc, a real PostgreSQL 18.4 Testcontainer, a
mutable clock, and a recording SMTP probe seeds the Admin through `/bootstrap`,
configures SMTP, creates and activates a valid Intern through the public
account service, and checks in via the attendance service at a controlled
instant. MockMvc drives login, CSRF, MVC binding, route authorization,
redirects, and Thymeleaf rendering with the mutable clock advanced past the
daily cutoff.

## Hand-derived expected result

The Intern checks in at `2026-08-14T02:00:00Z`; the clock advances to
`2026-08-14T09:00:01Z` (past the 16:00 local cutoff). POSTing `workDate =
2026-08-14`, `proposedCheckoutTime = 15:45`, `reason = "Forgot to check out"`
redirects to `/intern/corrections`, and the next GET renders `Correction
submitted for 14/08/2026 at 15:45`, the reason, and `PENDING`. A second POST
for the same date renders the `ALREADY_SUBMITTED` error flash. An Admin or
Mentor session gets HTTP 403 on GET and POST `/intern/corrections`.

## RED

**Command**

```text
cmd /c "mvnw.cmd -q -Dtest=InternCorrectionWebIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test"
```

**Observed result**

```text
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

This test was written after the production surface and service-level GREEN, so
no RED failure was captured; its behavioral claims are anchored to the
service-level evidence in `docs/tests/unit/correction-submission.md`.

## GREEN

**Command**

```text
cmd /c "mvnw.cmd -q -Dtest=InternCorrectionWebIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test"
```

**Observed result**

```text
PostgreSQL 18.4 container started and Flyway applied V1.
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
cmd /c "mvnw.cmd test"
Tests run: 308, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

This test does not prove the Mentor decision routes (I2-ATT-06), the scheduler
deadline guards (I2-ATT-07), real SMTP delivery, or browser layout and
accessibility beyond semantic labels, table headers, status roles, CSRF, and
route authorization. The shared shell and navigation are covered by
`docs/tests/web/attendance-shell-integration.md`.