# Test Evidence: Correction PostgreSQL persistence, derivation, and transaction boundaries

- **Test type:** Integration
- **Requirement IDs:** `COR-001`, `COR-002`, `COR-003`, `COR-006`, `COR-009`, `ATT-016`
- **Scenario IDs:** `AC-COR-001`, `AC-COR-002`, `AC-COR-003`, `AC-COR-005`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.CorrectionPersistenceIntegrationTest`
- **Implementation commit:** `4c09382`

## Protected behavior

A real PostgreSQL 18.4 Testcontainer persists correction rows and their
append-only `SUBMITTED` event. Submission honors the exclusive window (strictly
after the attached policy's inclusive cutoff through scheduled end plus 24
hours), leaves the raw checkout null, and returns the two deadlines. A second
submission for the same record is rejected with `ALREADY_SUBMITTED` and leaves
exactly one persisted correction. `AttendanceApplicationService.history`
derives the effective checkout as the raw checkout when present, otherwise an
approved correction's proposed checkout, so an approved pending proposal
appears on the Intern's history.

## Test method

A Spring Boot integration test migrates a real PostgreSQL 18.4 Testcontainer
with a mutable clock and recording SMTP probe, creates and activates a valid
Intern exclusively through public account and SMTP service/DTO boundaries, and
drives the transactional attendance services. Assertions read persisted entity
rows through repositories and assert the derived history DTO values.

## Hand-derived expected result

The seeded single policy schedules 08:30 → 15:30 local with a 30-minute
checkout grace in Asia/Ho_Chi_Minh. For `workDate = 2026-08-14` the inclusive
cutoff is `2026-08-14T09:00:00Z` and the submission deadline is
`2026-08-15T08:30:00Z` (inclusive). A check-in at `02:00:00Z` with submission
at `09:00:01Z` persists a `PENDING` row with `requestedCheckoutAt =
2026-08-14T08:45:00Z` (15:45 local), `submissionDeadline =
2026-08-15T08:30:00Z`, `decisionDeadline = 2026-08-15T09:00:01Z`, a raw
`check_out_at` still null, and one `SUBMITTED` event. Submitting a second
correction for the same record returns `ALREADY_SUBMITTED`. A record checked in
on `2026-08-17` submitted at `2026-08-18T08:30:01Z` is rejected with
`DEADLINE_PASSED` (after the inclusive `2026-08-18T08:30:00Z` deadline).
Approving the persisted correction and reading history yields the proposed
checkout as `effectiveCheckOutAt`; a raw checkout wins over the proposal.

## RED

**Command**

```text
cmd /c "mvnw.cmd -q -Dtest=CorrectionPersistenceIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test"
```

**Observed result**

```text
[ERROR] Tests run: 7, Failures: 0, Errors: 1, Skipped: 0
[ERROR] CorrectionPersistenceIntegrationTest.enforcesAtMostOneCorrectionPerRecordAndLeavesRawCheckoutNull:210
org.hibernate.AssertionFailure: Entry for instance of
'com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEntity' has a null identifier
```

Two earlier focused failures were corrected before the final run: the deadline
test targeted a record whose work date did not match its submission date, and
the duplicate-persist assertion expected the wrong exception type. The
`AssertionFailure` came from asserting the PostgreSQL unique constraint by
persisting a duplicate inside the same transaction; PostgreSQL aborts the whole
transaction on the constraint violation, leaving the failed entity with a null
identifier. The DB constraint is proven instead by the service's stable
`ALREADY_SUBMITTED` outcome, which is backed by
`uq_attendance_corrections_record` in the V1 baseline.

## GREEN

**Command**

```text
cmd /c "mvnw.cmd -q -Dtest=CorrectionPersistenceIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test"
```

**Observed result**

```text
PostgreSQL 18.4 container started and Flyway applied V1.
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
cmd /c "mvnw.cmd test"
Tests run: 308, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

This test does not prove cross-request concurrent submission races beyond the
pre-check rejection, the decision actions and locking (`COR-004`/`COR-005`,
I2-ATT-06), or the schedulers (`COR-008`, I2-ATT-07). The rendered Intern
correction forms are covered by `docs/tests/web/intern-corrections.md`.