# Test Evidence: Leave materialization, quota reservation, and release against PostgreSQL

- **Test type:** Integration
- **Requirement IDs:** `LEV-001`, `LEV-002`, `LEV-003`, `LEV-004`, `LEV-005`
- **Scenario IDs:** `AC-LEV-001`, `AC-LEV-002`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.LeaveMaterializationIntegrationTest`
- **Implementation commit:** `69b54eb`

## Protected behavior

Submitting a leave range persists a `PENDING` request plus one frozen
`leave_request_day` row per eligible workday against the real PostgreSQL schema,
each row remembering its policy version, calendar month, and monthly quota
snapshot (`LEV-003`); dates outside the internship or on weekends are dropped
and a range with no eligible day is rejected without persisting anything
(`LEV-002`). `PENDING` and `APPROVED` days both reserve quota, `REJECTED`
releases it (`LEV-004`), and quota validation includes existing
pending/approved allocations for each affected month (`LEV-005`).

## Test method

`@SpringBootTest` + `@ActiveProfiles("test")` + `@Transactional` + the shared
`AttendancePersistenceIntegrationTest.IntegrationConfiguration`
(Testcontainers PostgreSQL, MutableClock, RecordingSmtpProbe). An Admin is
bootstrapped, SMTP is activated, and an intern with internship
`2026-08-01 → 2026-12-31` is created/activated at fixed clock
`2026-08-14T00:00:00Z`. Tests assert persisted columns and frozen rows via the
repository and `EntityManager`, seed an `APPROVED` request with the
`LeaveEntityFixtures`, drive a `REJECTED` release through the same JPQL bulk
update the Mentor decide flow will use, and assert request/day row counts after
rejections.

## Hand-derived expected result

Range `2026-08-31 → 2026-09-04` with `2026-09-02` a manual day off materializes
exactly four rows (Aug 31, Sep 1, Sep 3, Sep 4) with the correct
`quota_month`/`monthly_quota_snapshot`, `submittedAt = 2026-08-14T02:00:00Z`,
and `first_counted_start_at = 2026-08-31T01:30:00Z`. One approved August day
yields `reserved = 1`, `available = 2`; adding one pending August day yields
`reserved = 2`, `available = 1`; flipping that pending request to `REJECTED`
returns to `reserved = 1`, `available = 2`. An over-quota submission throws
`QUOTA_EXCEEDED` and leaves the request/day tables unchanged. Ranges entirely
after the internship or on a weekend throw `NO_COUNTED_DAYS` with no rows.

## RED

No RED was observed at this level: the unit-RED step already drove the
implementation, and `LeaveMaterializationIntegrationTest` passed on first
execution, verifying persistence semantics the mocked unit tests cannot.

## GREEN

**Command**

```text
cmd /c "mvnw.cmd -Dtest=LeaveMaterializationIntegrationTest test"
```

**Observed result**

```text
Running com.lab.labtimesheet.feature.attendance.service.LeaveMaterializationIntegrationTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.381 s
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

These tests drive the service layer against the real schema but do not prove
the rendered Intern page, CSRF/form binding, or the HTTP 403 role enforcement.
Those are covered by `docs/tests/web/intern-leave.md`. Overlap exclusion
(`LEV-006`), same-day boundary (`LEV-009`), and decide/cancel/edit flows were
implemented in I2-ATT-04 and are covered by
`docs/tests/unit/leave-lifecycle.md`, `docs/tests/integration/leave-workflow.md`,
`docs/tests/integration/leave-concurrency.md`, and
`docs/tests/web/mentor-leave.md`.