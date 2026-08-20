# Test Evidence: Concurrent leave overlap and decision serialization

- **Test type:** Integration
- **Requirement IDs:** `LEV-006`, `LEV-008`
- **Scenario IDs:** `AC-LEV-003`, `AC-LEV-010`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.LeaveConcurrencyIntegrationTest`
- **Implementation commit:** `5e9e598`

## Protected behavior

Two Interns submitting identical overlapping ranges concurrently must resolve
to exactly one `SUCCESS` and one `OVERLAPS_PENDING`: the pessimistic write lock
on the Intern profile row (`AccountService.lockActiveInternship`,
`InternProfileRepository.findForUpdateByUserId`) serializes the overlap
re-read (DB-008) so the loser observes the winner's committed request. Two
Mentors deciding the same pending request concurrently must resolve to exactly
one `SUCCESS` and one `INVALID_STATE`: the `@Version` optimistic lock on
`leave_requests` fails the loser's `saveAndFlush`, which the service translates
to `INVALID_STATE`.

## Test method

`@SpringBootTest` + `@ActiveProfiles("test")` + `@DirtiesContext(AFTER_CLASS)`
(non-transactional) + the shared
`AttendancePersistenceIntegrationTest.IntegrationConfiguration`. An Admin is
bootstrapped once per class with SMTP activated; each test method creates its
own intern/mentor with unique emails. Two worker threads are gated by
`CountDownLatch`es and call the Spring-proxied `LeaveService` methods; each
call runs in its own transaction. The overlap test asserts the two outcomes and
that exactly one request row persists; the decision test first submits a target
request, then asserts the two outcomes and that the persisted request is
`APPROVED`.

## Hand-derived expected result

Both concurrent submitters pass the pre-lock intern eligibility check; the
profile lock then orders them. The first commits its `PENDING` request, the
second re-reads overlap after acquiring the lock and throws
`OVERLAPS_PENDING`. Both concurrent deciders read the `PENDING` entity at
version 0; the first `saveAndFlush` commits version 1, the second's UPDATE
matches 0 rows and throws `ObjectOptimisticLockingFailureException`, which
`LeaveService.decide` converts to `INVALID_STATE`. Final state: one request row
in the overlap test, one `APPROVED` row in the decision test.

## RED

No RED was observed at this level: the unit-RED step drove the implementation,
and `LeaveConcurrencyIntegrationTest` passed on first execution. The only
fixes during bring-up were test-scaffolding, not production code: the SMTP
configuration must be activated exactly once per context
(`uq_smtp_configurations_one_active`) and each test method must use unique
account emails because the non-transactional context retains committed rows.

## GREEN

**Command**

```text
cmd /c "mvnw.cmd -Dtest=LeaveConcurrencyIntegrationTest test"
```

**Observed result**

```text
Running com.lab.labtimesheet.feature.attendance.service.LeaveConcurrencyIntegrationTest
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 26.79 s
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

These tests prove the database-level serialization but not the rendered pages,
CSRF/form binding, or HTTP role enforcement (covered in
`docs/tests/web/mentor-leave.md` and `docs/tests/web/intern-leave.md`).
They also do not cover the scheduled auto-reject or request-time deadline
guards, which are deferred to I2-ATT-07.