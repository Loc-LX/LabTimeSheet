# Test Evidence: Daily work total concurrency proof

- **Test type:** Integration
- **Requirement IDs:** `TSK-015`, `DB-008`
- **Scenario IDs:** `I2-TSK-02`, `AC-TSK-008`
- **Test class/method:** `com.lab.labtimesheet.feature.task.service.TaskWorkLogDailyTotalConcurrencyTest`
- **Implementation commit:** `pending`

## Protected behavior

On PostgreSQL 18.4, two concurrent `logWork` transactions in different Projects for the same Intern are serialized by the Intern-profile write lock. When both would together exceed 1440 minutes, exactly one commits and the other is rejected with `TaskValidationException`; no partial write remains.

## Test method

The non-`@Transactional` class inserts two ACTIVE Projects, two memberships and leadership terms for one Intern, and one Task per Project via committed JDBC fixtures. Two threads await a `CyclicBarrier`, then each calls `logWork` with 800 minutes on the same work date in its own Project. Each thread counts accept/reject. Because the two Projects are distinct, the only shared serialization point is the Intern profile lock (DB-008), which forces one thread to read the other's committed 800-minute total and reject. Assertions verify exactly one accept, one reject, and a persisted total of exactly 800 minutes.

## Hand-derived expected result

Two independent 800-minute transactions target 1600 combined minutes. Serialization forces the second reader to see the first writer's committed row, so `currentTotal + 800 > 1440` holds for exactly one thread. Accepted 1, rejected 1, `sum(minutes) = 800`, with no second `task_work_logs` row.

## RED

**Command**

```text
.\mvnw.cmd "-Dtest=TaskWorkLogDailyTotalConcurrencyTest" test
```

**Observed result**

```text
[ERROR] TaskWorkLogDailyTotalConcurrencyTest.concurrentCrossProjectLogsAreSerializedOnInternProfileAndRejectOverAllocation: ERROR!
[ERROR] com.lab.labtimesheet.feature.task.service.TaskWorkLogDailyTotalConcurrencyTest ... TaskNotFoundException
```

Initial fixture failure: the test project lacked the mandatory current leadership term, so `ProjectService.taskMutationContext` rejected task creation. Fixture defect, not a production gap.

## GREEN

**Command**

```text
.\mvnw.cmd "-Dtest=TaskWorkLogIntegrationTest,TaskWorkLogDailyTotalConcurrencyTest" test
```

**Observed result**

```text
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- TaskWorkLogDailyTotalConcurrencyTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0 -- TaskWorkLogIntegrationTest
[INFO] BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
.\mvnw.cmd "-Dtest=Task*,TaskCreationIntegrationTest,TaskWorkLogIntegrationTest,TaskWorkLogDailyTotalConcurrencyTest" test

[INFO] Tests run: 81, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## External-test boundaries

This test proves cross-Project serialization with two threads on a real PostgreSQL instance. It does not stress lock-ordering against the attendance leave path or other features, and it does not exercise the browser UI. The shared Testcontainers database is cleaned in `@AfterEach` so the committed fixtures never leak into subsequent test classes.
