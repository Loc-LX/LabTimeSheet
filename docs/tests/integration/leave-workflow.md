# Test Evidence: Leave submit boundary, overlap protection, decide, cancel, and edit against PostgreSQL

- **Test type:** Integration
- **Requirement IDs:** `LEV-006`, `LEV-007`, `LEV-008`, `LEV-009`, `LEV-011`
- **Scenario IDs:** `AC-LEV-003`, `AC-LEV-004`, `AC-LEV-007`, `AC-LEV-010`, `AC-LEV-011`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.LeaveWorkflowIntegrationTest`
- **Implementation commit:** `5e9e598`

## Protected behavior

Against the real PostgreSQL schema, the same-day boundary is the first counted
workday's scheduled start: a same-day submission is accepted at
`2026-08-31T01:29:59Z` and rejected with `BOUNDARY_PASSED` at exactly
`2026-08-31T01:30:00Z` without persisting anything (LEV-009). The inclusive
overlap exclusion (`ex_leave_requests_no_overlap`) rejects a sequential range
that touches an existing pending request while an adjacent touching day is
accepted (LEV-006). A Mentor `decide` approves with the deciding Mentor,
server instant, and decision note while keeping the reservation, and rejects
while releasing it (LEV-008); decisions and cancellations at or after the
boundary are rejected (LEV-009/LEV-011). `cancel` releases the reservation and
cannot be repeated or performed by another Intern (LEV-011). `edit` replaces a
pending request's range/reason/boundary and its frozen days, and fails
atomically on overlap or over-quota, leaving the original request and its days
intact (LEV-007).

## Test method

`@SpringBootTest` + `@ActiveProfiles("test")` + `@Transactional` + the shared
`AttendancePersistenceIntegrationTest.IntegrationConfiguration`
(Testcontainers PostgreSQL, MutableClock, RecordingSmtpProbe). An Admin is
bootstrapped, SMTP is activated, and an active Mentor plus an intern with
internship `2026-08-01 → 2026-12-31` are created/activated at fixed clock
`2026-08-14T00:00:00Z`. Tests drive the real service, read back persisted
request columns and frozen days through the repository, and assert rejection
codes plus post-failure row states.

## Hand-derived expected result

Same-day `2026-08-31` submits at `01:29:59Z` → `PENDING` with
`firstCountedStartAt = 2026-08-31T01:30:00Z`, editable and cancellable; at
`01:30:00Z` the same range throws `BOUNDARY_PASSED` with zero rows. After
submitting `2026-09-01 → 2026-09-02`, a `2026-09-02 → 2026-09-04` range throws
`OVERLAPS_PENDING` and the adjacent `2026-09-03` is accepted. Approving a
`2026-09-01` request keeps `reserved = 3`; rejecting the second request leaves
`reserved = 1` and `available = 2`, and `decisions()` lists the rows newest
first with the Intern display name. At `2026-09-01T01:30:00Z` both `decide` and
`cancel` throw `BOUNDARY_PASSED` and the request stays `PENDING`. Cancelling
before the boundary returns `CANCELLED` with `cancelledAt` set, drops
`reserved` to zero, then throws `INVALID_STATE` on repeat and `NOT_OWNER` for a
foreign Intern. Editing `2026-09-01` → `2026-09-05` replaces range, reason,
`submittedAt`, boundary, and the frozen day list; editing to
`2026-09-09 → 2026-09-10` (touching the other request) throws
`OVERLAPS_PENDING` and editing to `2026-09-04 → 2026-09-08` (5 days against a
3-day month with another request) throws `QUOTA_EXCEEDED`, in both cases with
the original range and frozen days unchanged.

## RED

No RED was observed at this level: the unit-RED step already drove the
implementation, and `LeaveWorkflowIntegrationTest` passed on first execution,
verifying persistence, boundary, overlap, and atomicity semantics the mocked
unit tests cannot.

## GREEN

**Command**

```text
cmd /c "mvnw.cmd -Dtest=LeaveWorkflowIntegrationTest test"
```

**Observed result**

```text
Running com.lab.labtimesheet.feature.attendance.service.LeaveWorkflowIntegrationTest
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 11.57 s
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

These tests drive the service layer against the real schema but do not prove
the rendered Mentor/Intern pages, CSRF/form binding, or the HTTP 403 role
enforcement. Those are covered by `docs/tests/web/mentor-leave.md` and
`docs/tests/web/intern-leave.md`. Concurrent serialization of overlap and
decision is covered by `docs/tests/integration/leave-concurrency.md`. The
request-time auto-reject (LEV-010) is deferred to I2-ATT-07.