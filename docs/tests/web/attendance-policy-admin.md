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
version using its optimistic version. Persisted Mentor and Intern accounts,
authenticated by the same form-login path, receive HTTP 403 for both the GET page
and the POST scheduling route, and no policy row is inserted.

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

Authored after the controller/template were implemented as the binding
regression surface. The genuine behavior-missing RED is the earlier service-level
evidence in `docs/tests/unit/attendance-policy-scheduling.md`, which drove the
scheduling boundary into existence.

## GREEN

**Command**

```text
cmd /c "mvnw.cmd -Dtest=AttendancePolicyWebIntegrationTest test"
```

**Observed result**

```text
AttendancePolicyWebIntegrationTest: Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The Admin schedule+replace journey and the Mentor/Intern 403s both passed against
PostgreSQL 18.4 with a real form-login session.

## Affected suite

**Command and result**

```text
cmd /c "mvnw.cmd -Dtest=AttendancePolicyWebIntegrationTest,AttendanceControllerTest,AttendanceTemplateIntegrationTest,LayerStructureTest,AttendanceLayerStructureTest,AttendancePolicyServiceTest,AttendancePolicySchedulingIntegrationTest,AttendanceLombokBoilerplateTest test"
Tests run: 32, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

This web test uses real Spring MVC, form authentication, account identity mapping,
Flyway, and PostgreSQL 18.4. It substitutes only SMTP transport with an in-memory
probe, does not exercise a real browser, and does not cover multi-month report
regeneration, which remains the reporting feature's own integration scope.