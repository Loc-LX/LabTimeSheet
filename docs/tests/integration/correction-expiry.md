# Test Evidence: Correction decision persistence and committed-row decision-window expiry

- **Test type:** Integration
- **Requirement IDs:** `COR-004`, `COR-005`, `COR-007`, `COR-009`
- **Scenario IDs:** `AC-COR-003`, `AC-COR-005`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.CorrectionPersistenceIntegrationTest` (extended), `com.lab.labtimesheet.feature.attendance.service.CorrectionExpiryIntegrationTest`
- **Implementation commit:** `2ace890`

## Protected behavior

Against a real PostgreSQL 18.4 Testcontainer, `CorrectionService.decide` persists
the decided state (status, Mentor, instant, note) and its immutable APPROVED or
REJECTED event; `revert` persists a REOPENED event and clears the decided fields;
repeat `decide` after a revert persists the final APPROVED event. A late decision
after the inclusive `decisionDeadline` is refused with
`DECISION_WINDOW_PASSED` without appending any event. In the separate committed
`REQUIRES_NEW` expiry scenario, the first decide/revert access past the deadline
auto-rejects and locks a still-pending correction atomically, persists one
`AUTO_REJECTED` event, and every later decide/revert returns `LOCKED` while the
event list stays unchanged (idempotent). Approval leaves the raw checkout null,
derives the proposed checkout as the effective checkout, and clears the missing
flag.

## Test method

`CorrectionPersistenceIntegrationTest` uses the shared rollback-based context:
it seeds an Intern through public account/SMTP boundaries, checks in, submits a
correction, and drives `decide`/`revert`/`decisions` at controlled clock
instants, asserting persisted entity rows and append-only events.
`CorrectionExpiryIntegrationTest` runs without a test transaction so every
service call commits independently, proving the isolated expiry transaction
survives the outer rejection with `LOCKED`.

## Hand-derived expected result

For the seeded policy (Asia/Ho_Chi_Minh, scheduled end 15:30 local, 30-minute
checkout grace) and a submission at `2026-08-14T09:00:01Z`, the decision deadline
is `2026-08-15T09:00:01Z`.

- Deciding at `2026-08-15T08:00:00Z` persists `APPROVED`,
  `decidedByMentorUserId = mentor`, `decidedAt = 08:00:00Z`, note
  `"Verified in person"`, and events `SUBMITTED` then `APPROVED`
  (`PENDING`→`APPROVED`, actor = mentor). Raw `check_out_at` stays null and the
  derived history `effectiveCheckOutAt` becomes the proposed
  `2026-08-14T08:45:00Z` with no missing-checkout violation.
- Reject → revert → approve appends events exactly
  `SUBMITTED, REJECTED, REOPENED, APPROVED` with `fromStatus`
  `null, PENDING, REJECTED, PENDING` and `toStatus`
  `PENDING, REJECTED, PENDING, APPROVED`.
- Deciding at `2026-08-15T09:00:01.001Z` throws `DECISION_WINDOW_PASSED` and
  leaves exactly the one `SUBMITTED` event.
- `decisions()` lists newest first with the Intern display name; a correction
  decided at `08-15` whose window has passed by the read instant is
  `revertable = false` even though it is not yet locked (the guard has not run),
  while a `PENDING` row shows no revert action.
- In `CorrectionExpiryIntegrationTest`, first decide access at
  `2026-08-15T09:00:01.001Z` returns `LOCKED`; the persisted row is `REJECTED`
  with `decidedAt = lockedAt = 2026-08-15T09:00:01.001Z`, one `AUTO_REJECTED`
  event (null actor), and subsequent decide/revert attempts keep returning
  `LOCKED` with the event list still at size 2.

## RED

**Command**

```text
cmd /c "mvnw.cmd -Dtest=CorrectionPersistenceIntegrationTest,CorrectionExpiryIntegrationTest test"
```

**Observed result**

```text
[ERROR] Tests run: 11, Failures: 1, Errors: 0, Skipped: 0
CorrectionPersistenceIntegrationTest.mentorDecisionsListAllCorrectionsNewestFirstWithDerivedFlags:415
org.opentest4j.AssertionFailedError:
Expecting value to be true but was false
```

The decision-listing test asserted `revertable = true` for a correction decided on
`2026-08-15` while the fixture clock had already advanced to `2026-08-17T09:00:01Z`
(to submit a second correction). At that read instant the decision window had
passed, so the correct derived flag is `false` (not yet locked, but no longer
revertable). The fixture expectation was corrected to the derived semantics; the
within-window `revertable = true` case is separately covered by the unit decision
surface.

## GREEN

**Command**

```text
cmd /c "mvnw.cmd -Dtest=CorrectionPersistenceIntegrationTest,CorrectionExpiryIntegrationTest test"
```

**Observed result**

```text
CorrectionPersistenceIntegrationTest: Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
CorrectionExpiryIntegrationTest: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
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

These tests drive the service boundary with a test-only mutable clock and
recording SMTP probe; they do not prove a cross-request optimistic-lock race at
the HTTP layer, the scheduled expiry worker (`COR-008`, I2-ATT-07), or the
rendered Mentor decision forms. Those are covered by
`docs/tests/web/mentor-corrections.md` and the unit evidence.