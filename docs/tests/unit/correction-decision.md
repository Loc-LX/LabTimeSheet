# Test Evidence: Mentor correction decision, revert, and separate decision-window locking rules

- **Test type:** Unit
- **Requirement IDs:** `COR-004`, `COR-005`, `COR-007`, `COR-009`
- **Scenario IDs:** `AC-COR-003`, `AC-COR-005`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.CorrectionDecisionServiceTest`, `com.lab.labtimesheet.feature.attendance.service.CorrectionWindowGuardTest`
- **Implementation commit:** `2ace890`

## Protected behavior

`CorrectionService.decide` lets only an active Mentor approve or reject a `PENDING`
correction, appending one immutable APPROVED or REJECTED event with the deciding
Mentor, server instant, and optional note. `revert` moves a decided correction
back to `PENDING` inside its decision window and appends one REOPENED event. Both
mutations run the `CorrectionWindowGuard` first: a still-pending correction past
its inclusive decision deadline is auto-rejected and locked, an already-decided
correction is locked, and then the service refuses the requested transition with
`LOCKED`; a passed deadline that is not yet locked rejects with
`DECISION_WINDOW_PASSED`. Missing corrections, inactive Mentors, null commands,
already-decided corrections, locked outcomes, and concurrent optimistic conflicts
reject with `NOT_FOUND`, `INACTIVE_MENTOR`, `INVALID_REQUEST`,
`INVALID_STATE`, `LOCKED`, and `CONCURRENT_DECISION` respectively. The
`decisions` read derives per-row compliance, lock, and revertability flags.

## Test method

Plain JUnit and Mockito drive the transactional `CorrectionService` with a fixed
`Clock`, a mocked active-Mentor/identity `AccountService`, a mocked
`CorrectionWindowGuard`, and a mocked persisted correction carrying the seeded
single-policy timeline. The guard is separately unit-tested for the isolated
`REQUIRES_NEW` expiry transition. Each test exercises one externally observable
rule and asserts the exact rejection code and that no save or event happened
where the rule forbids it.

## Hand-derived expected result

With the fixed test clock inside the window at `2026-08-15T08:00:00Z` and the
decision deadline `2026-08-15T09:00:01Z` (submission `2026-08-14T09:00:01Z` plus
24 hours):

- `decide(mentorId, id, approved=true, "Verified in person")` yields
  `APPROVED` with `decidedByMentorUserId`, `decidedAt = 08:00:00Z`, note
  preserved, `internDisplayName = "Intern"`, and one `APPROVED` event
  (`PENDING`→`APPROVED`, actor = Mentor, note preserved).
- `decide(..., approved=false)` yields `REJECTED` with one `REJECTED` event.
- `revert` after an approve yields `PENDING` with cleared decided fields and one
  `REOPENED` event (`APPROVED`→`PENDING`).
- Missing correction → `NOT_FOUND`; inactive Mentor → `INACTIVE_MENTOR`;
  locked → `LOCKED`; now `2026-08-15T09:00:01.001Z` → `DECISION_WINDOW_PASSED`;
  deciding an already-approved correction → `INVALID_STATE`; reverting a pending
  correction → `INVALID_STATE`; null command → `INVALID_REQUEST`;
  `ObjectOptimisticLockingFailureException` on save → `CONCURRENT_DECISION`.
  In every rejection the guard is or is not invoked as the boundary requires,
  and no event or save occurs.

For the guard at `2026-08-15T09:00:01.001Z` (past the inclusive deadline):
- a `PENDING` correction is auto-rejected and locked with one `AUTO_REJECTED`
  event (null actor, `PENDING`→`REJECTED`);
- an `APPROVED` correction is locked without changing status, with one `LOCKED`
  event;
- at the inclusive deadline instant, inside the window, already-locked, or
  missing corrections are left untouched (no save, no event), proving the
  request-time guard is idempotent.

## RED

**Command**

```text
cmd /c "mvnw.cmd -Dtest=CorrectionDecisionServiceTest,CorrectionWindowGuardTest test"
```

**Observed result**

```text
[ERROR] COMPILATION ERROR :
[ERROR] ... cannot find symbol ... class CorrectionDecisionCommand ...
[ERROR] ... cannot find symbol ... class MentorCorrectionDecision ...
[ERROR] ... cannot find symbol ... class CorrectionWindowGuard ...
[ERROR] ... cannot find symbol ... method decide ...
[ERROR] ... cannot find symbol ... method revert ...
[ERROR] ... cannot find symbol ... method decisions ...
[ERROR] ... cannot find symbol ... method lock ...
[ERROR] ... cannot find symbol ... method autoReject ...
BUILD FAILURE
```

The new tests could not compile because the Mentor decision boundary
(`CorrectionDecisionCommand`, `MentorCorrectionDecision`, `CorrectionWindowGuard`,
the `decide`/`revert`/`decisions` service methods, and the entity
`lock`/`autoReject`/`reopen` mutators) did not exist yet.

## GREEN

**Command**

```text
cmd /c "mvnw.cmd -Dtest=CorrectionDecisionServiceTest,CorrectionWindowGuardTest test"
```

**Observed result**

```text
Running com.lab.labtimesheet.feature.attendance.service.CorrectionDecisionServiceTest
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
Running com.lab.labtimesheet.feature.attendance.service.CorrectionWindowGuardTest
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
cmd /c "mvnw.cmd test"
Tests run: 334, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

The unit tests mock the repositories, the guard, and JPA identity; they do not
prove the committed-row `REQUIRES_NEW` expiry transaction, the PostgreSQL
unique/index constraints, real persistence of the decided fields, or the rendered
Mentor forms. Those are covered by
`docs/tests/integration/correction-expiry.md`,
`docs/tests/integration/correction-persistence.md`, and
`docs/tests/web/mentor-corrections.md`. The scheduled expiry worker and
notification duplication (`COR-008`) belong to I2-ATT-07.