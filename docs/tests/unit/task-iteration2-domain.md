# Test Evidence: Task definition, work-log, and transfer domain rules

- **Test type:** Unit
- **Requirement IDs:** `TSK-004`, `TSK-009`, `TSK-010`, `TSK-013`, `TSK-016`, `TSK-019`, `PRJ-010`, `PRJ-011`, `DB-007`
- **Scenario IDs:** `AC-TSK-004`, `AC-TSK-005`, `AC-TSK-007`, `AC-TSK-011`, `AC-PRJ-012`
- **Test class/method:** `TaskDefinitionRulesTest`, `TaskWorkLogRulesTest`, `TaskTransferServiceTest`
- **Implementation commit:** `15920f38ae40e8457dce1eb6e1624483e2d1edf6`

## Protected behavior

Task definition edits update only editable fields; reassignment changes only current assignment attribution; soft deletion retains the row and deletion actor; work-log correction retains author/date/creation attribution; a selected transfer batch locks and validates every unfinished row before one flush.

## Test method

The tests use real Task and TaskWorkLog entities for mutation invariants and mocked repository rows for the transfer boundary. The transfer test supplies a locked Project DTO context, selects two unfinished Tasks in reverse order, and asserts deterministic locking plus one recipient.

## Hand-derived expected result

Creator, status, creation instant, work date, and author remain unchanged. Two selected unfinished rows transfer to membership `72`, with assignment actor `70` and one result count; any invalid row would throw before `saveAllAndFlush`.

## RED

**Command**

```text
./mvnw -Dtest=TaskDefinitionRulesTest test
./mvnw -Dtest=TaskWorkLogRulesTest test
./mvnw -Dtest=TaskTransferServiceTest test
```

**Observed result**

```text
TaskDefinitionRulesTest: compilation failed — Task had no updateDefinition/reassign/softDelete or retained mutation getters.
TaskWorkLogRulesTest: compilation failed — TaskWorkLog entity was absent.
TaskTransferServiceTest: compilation failed — TaskTransferService and TaskTransferResult were absent.
```

## GREEN

**Command**

```text
./mvnw -Dtest=TaskDefinitionRulesTest,TaskWorkLogRulesTest,TaskTransferServiceTest test
```

**Observed result**

```text
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
./mvnw -Dtest=TaskDefinitionRulesTest,TaskWorkLogRulesTest,TaskProjectQueryTest,TaskTransferServiceTest,TaskCreationIntegrationTest,TaskPersistenceStructureTest,TaskDomainRulesTest,TaskQueryServiceTest,TaskMutationBoundaryTest,TaskControllerTest test
Tests run: 72, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

Focused correction/recipient-boundary check:

```text
./mvnw -Dtest=TaskWorkLogRulesTest,TaskTransferServiceTest test
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
```

## External-test boundaries

These unit tests do not prove PostgreSQL constraints, profile locking, cross-Project daily totals, pending-exit eligibility, or browser rendering. Those require the pending Account and Project public DTO/service pins and PostgreSQL integration tests.
