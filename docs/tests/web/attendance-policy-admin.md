# Test Evidence: Admin attendance-policy scheduling screen

- **Test type:** Web
- **Requirement IDs:** `ATT-001`, `ATT-002`, `ATT-003`, `ATT-004`, `ATT-005`, `ATT-006`, `SEC-013`, `UI-013`
- **Scenario IDs:** `AC-ATT-001`, `AC-SEC-005`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.controller.AttendancePolicyWebIntegrationTest`
- **Implementation commit:** `3f617fe` (tracker row `I2-ATT-01` DONE)

## Protected behavior

A persisted first Admin can open `/attendance/policy`, schedule a new future-month
policy version through the real CSRF-protected form (separate grace values, quota,
penalty, and a reduced workday set), and replace a scheduled-but-not-yet-effective
version using its optimistic version. Invalid schedule submissions re-render the
page with retained input and field-level errors without persisting a row; a
non-first-of-month or past effective date rejects with a retained-input global
error; an invalid replacement redirects with a flash error and leaves the row
unchanged; the timezone is rendered as a selectable list. Persisted Mentor and
Intern accounts, authenticated by the same form-login path, receive HTTP 403 for
both the GET page and the POST scheduling route, and no policy row is inserted.

## Test method

The test posts the actual bootstrap form, logs in through Spring Security, and
follows the resulting session to `/attendance/policy`. It configures a test-only
SMTP probe solely to activate Mentor and Intern accounts through the public
AccountService, then logs in those accounts before asserting denial. A `MutableClock`
freezes application time at `2026-08-14` so the `2026-09-01` effective date is
unambiguously a future month boundary. Spring Boot applies Flyway to PostgreSQL 18.4
through the shared Testcontainers configuration. Persistence is verified directly
through `AttendancePolicyRepository.findAllByOrderByEffectiveFromAsc()`.

## Hand-derived expected result

- Seed policy (effective `1970-01-01`, `08:30–15:30`, grace `30/30`, Mon–Fri) renders
  with the `EFFECTIVE` badge and is immutable (no save form).
- Scheduling `2026-09-01` with `check_in 20` / `checkout 0`, quota `5`, penalty `0.5`,
  workdays `{MON,WED,FRI}` persists a second row and renders with the `FUTURE` badge
  plus the flash message.
- Replacing that future row with `version 0` and grace `30/30`, quota `3`, penalty
  `0.25`, Mon–Fri advances its optimistic version to `1`.
- Invalid submissions (grace `721`, zone `Mars/Olympus`, quota `32`, penalty `1.5`,
  empty workdays, reversed schedule) each re-render HTTP 200 with the field error and
  no new row; `effectiveFrom 2026-09-15` retains the entered date with a
  "first day of a calendar month" error; replacing with zone `Mars/Olympus` + grace
  `721` redirects, flashes the error, and leaves the row at version `0`.
- The timezone control renders as a `<select>` offering the curated zone list.
- Mentor/Intern GET and POST both return HTTP 403; the timeline stays at exactly one row.

## RED

**Command**

```text
cmd /c "mvnw.cmd -Dtest=AttendancePolicyWebIntegrationTest test"
```

**Observed result**

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The pre-existing two tests (Admin journey + non-Admin 403s) already passed. The
four new validation tests target behavior that did not exist before this change:
without Bean Validation and the re-render/redirect handling they fail on content
assertions (no field errors render, the timezone stays a text input, and invalid
submissions redirect instead of re-rendering with retained input).

## GREEN

**Command**

```text
cmd /c "mvnw.cmd -Dtest=AttendancePolicyWebIntegrationTest test"
```

**Observed result**

```text
AttendancePolicyWebIntegrationTest: Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The Admin schedule+replace journey, all six invalid-input re-render/redirect paths,
the timezone dropdown, and the Mentor/Intern 403s all passed against PostgreSQL
18.4 with a real form-login session.

## Affected suite

**Command and result**

```text
cmd /c "mvnw.cmd -Dtest=com.lab.labtimesheet.feature.attendance.**.*Test test"
Tests run: 173, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

This web test uses real Spring MVC, form authentication, account identity mapping,
Flyway, and PostgreSQL 18.4. It substitutes only SMTP transport with an in-memory
probe, does not exercise a real browser, and does not cover multi-month report
regeneration, which remains the reporting feature's own integration scope.