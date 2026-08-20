# Test Evidence: LeaveService eligibility, materialization, and quota reservation rules

- **Test type:** Unit
- **Requirement IDs:** `LEV-001`, `LEV-002`, `LEV-003`, `LEV-005`
- **Scenario IDs:** `AC-LEV-001`, `AC-LEV-002`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.LeaveServiceTest`
- **Implementation commit:** `69b54eb`

## Protected behavior

`LeaveService.submit` validates an inclusive full-day range with a non-blank
reason (`LEV-001`), drops any date outside the Intern's internship interval,
configured non-workdays, and global days off, rejecting the request when no
eligible workday remains (`LEV-002`). Each eligible date is frozen with its own
calendar month and monthly-quota snapshot, and cross-month ranges allocate dates
to their respective months (`LEV-003`). Quota is validated as existing
pending/approved reservations plus the candidate for every affected month and
serialized on the Intern profile row via `AccountService.lockActiveInternship`
before any reservation read (`LEV-005`). `overview` reports monthly quota,
reserved and available days, plus prior requests with their counted dates.

## Test method

`LeaveService` is driven through mocked `AccountService`,
`AttendancePolicyRepository`, `CalendarApplicationService`, and the two leave
repositories, with a fixed `Clock` at `2026-08-14T02:00:00Z` and the seeded
single-policy timeline (quota 3). Each test exercises one externally observable
rule: cross-month materialization skipping a global day off, the no-eligible-day
reject, inverted-range and blank-reason rejects, quota-exceeded reject, a
separate-month quota acceptance, the profile-lock serialization, the inactive
Intern rejection before locking, and the overview read.

## Hand-derived expected result

For range `2026-08-31 → 2026-09-04` with `2026-09-02` a global day off and
weekends excluded, exactly `2026-08-31`, `2026-09-01`, `2026-09-03`, and
`2026-09-04` materialize: August dates carry `quotaMonth = 2026-08-01` and
September dates `2026-09-01`, each with snapshot `3`. The persisted request is
`PENDING` with `submittedAt = 2026-08-14T02:00:00Z` and
`firstCountedStartAt = 2026-08-31T01:30:00Z` (the seeded policy's `08:30`
scheduled start in Asia/Ho_Chi_Minh). With 2 August days already reserved, a
2-day August candidate is rejected (`QUOTA_EXCEEDED`) while a range spanning
August and September is accepted because each affected month fits independently.

## RED

**Command**

```text
cmd /c "mvnw.cmd -Dtest=LeaveServiceTest test"
```

**Observed result**

```text
[ERROR] COMPILATION ERROR :
[ERROR] ... cannot find symbol ... class LeaveService
[ERROR] ... cannot find symbol ... class LeaveSubmission
[ERROR] ... cannot find symbol ... class LeaveSubmissionCommand
[ERROR] ... cannot find symbol ... class LeaveRejection
[ERROR] ... cannot find symbol ... class LeaveException
[ERROR] ... cannot find symbol ... class CountedLeaveDay
[ERROR] ... cannot find symbol ... class LeaveOverview
[ERROR] ... cannot find symbol ... class MonthReservation
[ERROR] ... cannot find symbol ... class PriorLeaveRequest
[ERROR] ... cannot find symbol ... method pending ...
[ERROR] ... cannot find symbol ... constructor LeaveRequestDayEntity ...
[ERROR] ... cannot find symbol ... method leaveDate ...
[ERROR] ... cannot find symbol ... method lockActiveInternship ...
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
BUILD FAILURE
```

The new `LeaveServiceTest` could not compile because the leave boundary, its
rejection types, DTOs, entity construction/getters, and the
`AccountService.lockActiveInternship` serialization lock did not exist yet.

## GREEN

**Command**

```text
cmd /c "mvnw.cmd -Dtest=LeaveServiceTest test"
```

**Observed result**

```text
Running com.lab.labtimesheet.feature.attendance.service.LeaveServiceTest
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.465 s
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

The unit tests mock the repositories and lock; they do not prove the real
PostgreSQL schema, the frozen `leave_request_days` rows, quota release on
rejection, the `@Transactional` wiring, or the rendered Intern form. Those are
covered by `docs/tests/integration/leave-materialization.md` and
`docs/tests/web/intern-leave.md`.