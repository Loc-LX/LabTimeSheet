# Test Evidence: Project Task progress, member totals, and retained history boundary

- **Test type:** Unit
- **Requirement IDs:** `PRJ-015`, `PRJ-016`, `TSK-004`, `TSK-010`, `TSK-011`, `TSK-013`, `AUTH-010`
- **Scenario IDs:** `AC-PRJ-008`, `AC-TSK-005`, `AC-TSK-011`, `AC-AUTH-008`
- **Test class/method:** `TaskProjectQueryTest#returnsHandCheckableStatusProgressAndMinutes`, `#returnsMemberTotalsInStableMembershipOrder`
- **Implementation commit:** `15920f38ae40e8457dce1eb6e1624483e2d1edf6`

## Protected behavior

Task exposes DTO-only Project query operations: one aggregate read of current non-deleted status counts and retained total work minutes, plus deterministic per-membership totals. Empty current Task denominators use `OptionalDouble.empty()` through `TaskProjectProgress`.

## Test method

The test stubs one repository aggregate projection independently of the implementation, verifies no legacy per-status/work-log reads, then checks hand-derived six-Task/450-minute totals and two membership totals. No Project repository/entity is imported.

## Hand-derived expected result

Counts `1 TODO + 2 IN_PROGRESS + 1 BLOCKED + 2 DONE = 6`; `2 / 6 * 100 = 33.333333333333336%`; total minutes are `450`; member totals preserve membership order.

## RED

**Command**

```text
./mvnw -Dtest=TaskProjectQueryTest test
```

**Observed result**

```text
Compilation failed — the review regression test could not compile because TaskRepository had no single-snapshot `projectProgress(...)` projection.
```

## GREEN

**Command**

```text
./mvnw -Dtest=TaskProjectQueryTest test
```

**Observed result**

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
./mvnw -Dtest=TaskDefinitionRulesTest,TaskWorkLogRulesTest,TaskProjectQueryTest,TaskTransferServiceTest,TaskCreationIntegrationTest,TaskPersistenceStructureTest,TaskDomainRulesTest,TaskQueryServiceTest,TaskMutationBoundaryTest,TaskControllerTest test
Tests run: 72, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

The query unit tests do not prove caller authorization, Project History visibility, or PostgreSQL aggregation over persisted rows; those are integration/Project-owner gates.
