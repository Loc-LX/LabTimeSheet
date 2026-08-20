# Test Evidence: Attendance policy scheduling against PostgreSQL with stable history

- **Test type:** Integration
- **Requirement IDs:** `ATT-001`, `ATT-002`, `ATT-003`, `ATT-004`, `ATT-005`, `ATT-006`
- **Scenario IDs:** `AC-ATT-001`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.AttendancePolicySchedulingIntegrationTest`
- **Implementation commit:** `3f617fe`

## Protected behavior

A scheduled future-month policy version persists with its separate grace values,
quota, penalty, and workday set. The timeline resolves the new version only from
its `effective_from` onward: an attendance row created before that date keeps its
old checkout cutoff and reports, and a post-cutoff normal checkout on the new date
is rejected. Replacement is allowed before `effective_from` and rejected after it
becomes effective. The DB enforces quota/penalty ranges, and PostgreSQL rejects
persisting an out-of-range quota.

## Test method

Spring Boot migrates PostgreSQL 18.4 and creates an Admin plus an active Intern
only through public Account and SMTP services. A `MutableClock` bean freezes
application time. The test schedules versions through `AttendancePolicyService`,
punches through `AttendanceApplicationService`, and reads raw rows through
`AttendanceRecordRepository` to assert the attached historical policy, old-vs-new
checkout cutoffs, workday gating, immutability, and DB constraint behavior.

## Hand-derived expected result

Seed policy is `checkout_grace 30`; scheduled version for `2026-09-01` is
`check_in_grace 20`, `checkout_grace 0`, `quota 5`, `penalty 0.5`, Mon-Fri.

- Row on `2026-08-14` (seed cutoff `16:00` local): `09:00:00Z` is not
  `MISSING_CHECKOUT`; `09:00:00.001Z` is.
- New cutoff `15:30` local under the `2026-09-01` version: checkout at `16:00`
  local (`09:00Z`) is `CHECKOUT_CUTOFF_PASSED`; checkout at `15:30` local
  (`08:30Z`) succeeds.
- `2026-09-01` is a Tuesday: workday set `{MON,WED,FRI}` rejects check-in;
  `2026-09-02` (Wednesday) accepts it.
- `grace 721` and `scheduled_end 23:30 + grace 30` are rejected by the service;
  quota `99` violates `ck_attendance_policy_versions_quota`.

## RED

**Command**

```text
cmd /c "mvnw.cmd -Dtest=AttendancePolicySchedulingIntegrationTest test"
```

**Observed result**

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Authored after the scheduling boundary was implemented, this test guards the
regression surface; the genuine behavior-missing RED is the unit test in
`docs/tests/unit/attendance-policy-scheduling.md`.

## GREEN

**Command**

```text
cmd /c "mvnw.cmd -Dtest=AttendancePolicySchedulingIntegrationTest test"
```

**Observed result**

```text
AttendancePolicySchedulingIntegrationTest: Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The expected `DataIntegrityViolationException` for quota `99` surfaced as
`ck_attendance_policy_versions_quota` violation, and the 16:00-local checkout was
rejected as `CHECKOUT_CUTOFF_PASSED` while 15:30-local was accepted and persisted.

## Affected suite

**Command and result**

```text
cmd /c "mvnw.cmd test"
Tests run: 228, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

The test does not cover the later policy-editing UI, multi-month report
regeneration, or the HolidayAPI/notification integration items. It proves exact
PostgreSQL 18.4 constraint semantics, effective-history resolution, workday
gating, and immutable replacement against a real containerized database.