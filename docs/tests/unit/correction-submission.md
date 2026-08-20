# Test Evidence: Missed-checkout correction submission window and proposed-time validation

- **Test type:** Unit
- **Requirement IDs:** `COR-001`, `COR-002`, `COR-003`, `COR-006`, `ATT-016`
- **Scenario IDs:** `AC-COR-001`, `AC-COR-002`, `AC-COR-003`, `AC-COR-005`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.CorrectionServiceTest`
- **Implementation commit:** `4c09382`

## Protected behavior

`CorrectionService.submit` persists a pending missed-checkout correction with
its two deadlines only inside the exclusive window that opens strictly after
the attached policy's inclusive checkout cutoff and closes inclusively at
scheduled end plus 24 hours. Submissions require an active eligible Intern, an
existing attendance record with no raw checkout, and at most one correction per
record. The proposed checkout is derived from the policy-local work date and
time and must be strictly after check-in and not in the future. A concurrent
unique-conflict race on the record is translated to the stable
`ALREADY_SUBMITTED` rejection. The read path derives the effective checkout as
the raw checkout when present, otherwise the approved correction's proposed
checkout.

## Test method

Plain JUnit and Mockito drive the transactional `CorrectionService` with a
fixed `Clock`, a stubbed eligible `AccountService`, a persisted record carrying
the seeded policy (Asia/Ho_Chi_Minh, scheduled end 15:30 local, 30-minute
checkout grace), and repository collaborators. Each test exercises one
externally observable rule and asserts the exact rejection code and that no
mutation happened where the rule forbids it. The effective-checkout derivation
is exercised through `AttendanceApplicationService.history` with the raw
checkout null (approved correction) and non-null (raw wins) cases.

## Hand-derived expected result

For `workDate = 2026-08-14`, scheduled end is `2026-08-14T08:30:00Z` and the
inclusive cutoff is `2026-08-14T09:00:00Z` (15:30 + 30 min local).
`submit` is rejected with `TOO_EARLY` at `09:00:00Z` and accepted at
`09:00:01Z`. The inclusive submission deadline is `2026-08-15T08:30:00Z`:
accepted at exactly that instant and rejected with `DEADLINE_PASSED` at
`2026-08-15T08:30:01Z`. A proposed time at or before check-in is rejected with
`PROPOSED_BEFORE_CHECKIN`; a proposed instant after the server instant is
rejected with `PROPOSED_IN_FUTURE`. Missing/null command values, blank reason,
missing record, raw checkout present, existing correction, and inactive Intern
map to `INVALID_REQUEST`, `NO_ATTENDANCE_RECORD`, `HAS_RAW_CHECKOUT`,
`ALREADY_SUBMITTED`, and `INACTIVE_INTERN` respectively, with no save on any
rejection. A `DataIntegrityViolationException` from the save is returned as
`ALREADY_SUBMITTED`. An approved correction with no raw checkout yields the
proposed checkout as effective; a raw checkout wins over any correction.

## RED

**Command**

```text
cmd /c "mvnw.cmd -q -Dtest=CorrectionServiceTest -Dsurefire.failIfNoSpecifiedTests=false test"
```

**Observed result**

```text
[ERROR] COMPILATION ERROR :
[ERROR] CorrectionServiceTest.java:[13,57] cannot find symbol
[ERROR] CorrectionServiceTest.java:[14,57] cannot find symbol
[ERROR] CorrectionServiceTest.java:[18,57] cannot find symbol
[ERROR] CorrectionServiceTest.java:[19,57] cannot find symbol
[ERROR] CorrectionServiceTest.java:[20,60] cannot find symbol
[ERROR] CorrectionServiceTest.java:[21,60] cannot find symbol
[ERROR] CorrectionServiceTest.java:[23,58] cannot find symbol
[ERROR] CorrectionServiceTest.java:[24,58] cannot find symbol
[ERROR] CorrectionServiceTest.java:[48,19] cannot find symbol
[ERROR] CorrectionServiceTest.java:[49,19] cannot find symbol
[ERROR] CorrectionServiceTest.java:[52,13] cannot find symbol
[ERROR] CorrectionServiceTest.java:[231,48] cannot find symbol
[ERROR] CorrectionServiceTest.java:[236,26] cannot find symbol
[ERROR] CorrectionServiceTest.java:[236,63] cannot find symbol
[ERROR] CorrectionServiceTest.java:[243,20] cannot find symbol
[ERROR] CorrectionServiceTest.java:[247,13] cannot find symbol
BUILD FAILURE
```

The new test could not compile because the correction surface
(`CorrectionException`, `CorrectionRejection`, the submission DTOs, the two
correction entities, their repositories, and `CorrectionService`) did not exist
yet. The full production surface was added in `4c09382`.

## GREEN

**Command**

```text
cmd /c "mvnw.cmd -q -Dtest=CorrectionServiceTest -Dsurefire.failIfNoSpecifiedTests=false test"
```

**Observed result**

```text
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
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

The unit tests mock the repositories and the eligibility service; they do not
prove the PostgreSQL `uq_attendance_corrections_record` constraint, the
append-only event rows, transactional atomicity of a submission, or the
rendered Intern forms. Those are covered by
`docs/tests/integration/correction-persistence.md` and
`docs/tests/web/intern-corrections.md`. The decision-window locking
(`COR-004`/`COR-005`) and schedulers (`COR-008`) belong to I2-ATT-06/I2-ATT-07.