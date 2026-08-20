# Test Evidence: Attendance policy future-month scheduling rules

- **Test type:** Unit
- **Requirement IDs:** `ATT-001`, `ATT-002`, `ATT-003`, `ATT-004`, `ATT-005`, `ATT-006`
- **Scenario IDs:** `AC-ATT-001`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.AttendancePolicyServiceTest`
- **Implementation commit:** `3f617fe`

## Protected behavior

Admin may schedule a new attendance-policy version only when `effective_from` is
the first day of a future calendar month. Separate check-in and checkout grace
values, workday sets, quota, and penalty are preserved as a new effective-dated
version. Once `effective_from` has passed, the version is immutable; replacement
of an effective version or of a stale optimistic version is rejected.

## Test method

`AttendancePolicyService` is driven through `Clock` + `AttendancePolicyRepository`
mocks. Entities are mocked and `saveAndFlush` returns a stubbed entity whose
`toDomain()` yields the exact expected domain policy, avoiding any reliance on a
real JPA identity. Each test exercises one externally observable rule: future
first-of-month acceptance, non-first-day and past/current-month rejection,
non-Admin `AccessDeniedException`, out-of-range grace and cutoff-reaching-midnight
`IllegalArgumentException`, replace-before/after-effective, and stale optimistic
version.

## Hand-derived expected result

With the fixed test clock at `2026-08-14T00:00:00Z`, an `effective_from` of
`2026-09-01` (day 1, future) is accepted and schedules a version with the exact
command values; `2026-09-15`, `2026-08-01`, and `2026-07-01` are rejected; grace
`721` or `scheduled_end 23:30` + `grace 30` is rejected; a seed (effective)
version rejects replacement while a future version accepts it and advances its
optimistic version; an expected version different from the persisted version is
rejected.

## RED

**Command**

```text
cmd /c "mvnw.cmd -Dtest=AttendancePolicyServiceTest test"
```

**Observed result**

```text
[ERROR] COMPILATION ERROR :
[ERROR] ... cannot find symbol ... class PolicyException ...
[ERROR] ... cannot find symbol ... class SchedulePolicyCommand ...
[ERROR] ... cannot find symbol ... class AttendancePolicyService ...
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
BUILD FAILURE
```

The new `AttendancePolicyServiceTest` could not compile because the scheduling
boundary (`AttendancePolicyService`), its command (`SchedulePolicyCommand`), and
its rejection type (`PolicyException`) did not exist.

## GREEN

**Command**

```text
cmd /c "mvnw.cmd -Dtest=AttendancePolicyServiceTest test"
```

**Observed result**

```text
Running com.lab.labtimesheet.feature.attendance.service.AttendancePolicyServiceTest
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.195 s
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
cmd /c "mvnw.cmd test"
Tests run: 228, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The full suite also required the pre-existing package-by-feature structure guard
to be path-separator agnostic on Windows (`96c2974`); the guard still enforces the
approved package list.

## External-test boundaries

The unit tests mock the repository and JPA identity; they do not prove PostgreSQL
constraints, real persistence, or historical boundary resolution. Those are
covered by `docs/tests/integration/attendance-policy-scheduling.md`.