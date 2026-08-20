# Test Evidence: Leave lifecycle boundary, overlap, decide, cancel, and edit rules

- **Test type:** Unit
- **Requirement IDs:** `LEV-006`, `LEV-007`, `LEV-008`, `LEV-009`, `LEV-011`
- **Scenario IDs:** `AC-LEV-003`, `AC-LEV-004`, `AC-LEV-007`, `AC-LEV-010`, `AC-LEV-011`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.LeaveLifecycleTest`
- **Implementation commit:** `5e9e598`

## Protected behavior

`LeaveService.submit` rejects a range at or after its first counted workday's
scheduled start (`BOUNDARY_PASSED`, LEV-009) and rejects any inclusive range
that touches an existing pending/approved request for the same Intern
(`OVERLAPS_PENDING`, LEV-006). `decide` (LEV-008) requires an active Mentor and
a `PENDING` request still before its boundary, then records APPROVED/REJECTED
with the deciding Mentor, server instant, and optional note. `cancel` (LEV-011)
lets only the owner release a pending/approved request before the boundary.
`edit` (LEV-007) replaces a pending request's range/reason only after every
revalidation (internship, counted days, overlap, boundary, quota) passes, so a
failed edit leaves the original request and its frozen days untouched.

## Test method

`LeaveService` is driven through mocked collaborators with a fixed `Clock`,
the seeded single-policy timeline (quota 3, Asia/Ho_Chi_Minh, 08:30 → 01:30Z),
and Mockito `spy` over the real `LeaveRequestEntity.pending` factory so
`id()` is controlled without losing real state transitions. Each test exercises
one externally observable rule and asserts the exact rejection code and that no
mutation happened where the rule forbids it.

## Hand-derived expected result

For a first counted start `2026-09-01T01:30:00Z`, `submit` succeeds at
`2026-08-31T01:29:59Z` (range `2026-08-31`) and throws `BOUNDARY_PASSED` at
`2026-08-31T01:30:00Z`. A range `2026-09-02 → 2026-09-04` overlapping a
pending `2026-09-01 → 2026-09-03` request throws `OVERLAPS_PENDING` without
saving. `decide` approves with `decidedByMentorUserId`, `decidedAt = NOW`, and
the note; rejects with the note; throws `BOUNDARY_PASSED` exactly at the
boundary, `INVALID_STATE` on an already-decided request, `NOT_FOUND` for a
missing request, and `INACTIVE_MENTOR` when the account is not an active
Mentor. `cancel` sets `CANCELLED` with `cancelledAt = NOW` before the boundary,
and throws `BOUNDARY_PASSED`, `NOT_OWNER`, or `INVALID_STATE` (resolved
request) otherwise. `edit` to a single replacement day deletes the old frozen
days, saves exactly one replacement day, updates range/reason/boundary, and
throws `BOUNDARY_PASSED`/`OVERLAPS_PENDING`/`NOT_OWNER` before any mutation.

## RED

**Command**

```text
cmd /c "mvnw.cmd -Dtest=LeaveLifecycleTest test"
```

**Observed result**

```text
[ERROR] COMPILATION ERROR :
[ERROR] LeaveLifecycleTest.java:[19,57] cannot find symbol
[ERROR] LeaveLifecycleTest.java:[177,39] cannot find symbol
[ERROR] LeaveLifecycleTest.java:[300,36] cannot find symbol
BUILD FAILURE
```

The new `LeaveLifecycleTest` could not compile because the leave decision
command (`LeaveDecisionCommand`), the lifecycle service methods
(`decide`/`cancel`/`edit`), the new rejection codes, and the fixture helper did
not exist yet. The full production surface (entity decision/cancellation
mutators, overlap query, reservation-exclusion aggregate, and
`AccountService.requireActiveMentorId`) was added in `5e9e598`; the fixture
import was then restored, and the Mockito test scaffolding was corrected
(unfinished-stubbing from nesting the spy construction inside `thenReturn` and
one `NotAMock` on a non-spied fixture).

## GREEN

**Command**

```text
cmd /c "mvnw.cmd -Dtest=LeaveLifecycleTest test"
```

**Observed result**

```text
Running com.lab.labtimesheet.feature.attendance.service.LeaveLifecycleTest
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.303 s
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

The unit tests mock the repositories and the profile lock; they do not prove
the PostgreSQL exclusion constraint, the `@Version` optimistic-lock outcome,
transactional atomicity of an edit, or the rendered forms. Those are covered by
`docs/tests/integration/leave-workflow.md`,
`docs/tests/integration/leave-concurrency.md`,
`docs/tests/web/mentor-leave.md`, and `docs/tests/web/intern-leave.md`.