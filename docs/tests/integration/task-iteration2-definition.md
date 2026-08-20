# Test Evidence: authorized Task definition changes and reassignment

- **Test type:** Integration
- **Requirement IDs:** `TSK-009`, `TSK-010`, `TSK-019`, `PRJ-011`, `DB-007`
- **Scenario IDs:** `AC-TSK-004`, `AC-TSK-005`, `AC-TSK-011`
- **Test class/method:** `TaskCreationIntegrationTest#creatorMayEditOnlyWhileStillCurrentAssigneeAndLeaderMayEditAnyUnfinishedTask`, `#softDeleteExcludesTaskFromCurrentViewsButRetainsHistoricalRow`, `#unfinishedReassignmentPreservesCreatorStatusAndCreationAtAndDoneRequiresReopen`
- **Implementation commit:** `pending`

## Protected behavior

PostgreSQL-backed Task mutations reauthorize against the locked Project context. A creator can edit only while still current assignee; Leader edits/reassigns unfinished Tasks; soft deletion excludes normal views but retains the row; DONE transfer is blocked until reopen.

## Test method

Each test creates a fresh PostgreSQL 18.4 Testcontainer schema, inserts a PLANNED Project and memberships, executes service calls, then checks both DTO outcomes and stored rows. The tests are production-shaped because authorization, transaction, JPA, Flyway, and composite membership foreign keys are exercised together.

## Hand-derived expected result

The creator edit is allowed before transfer and denied after transfer; Leader edit is allowed. Soft deletion leaves one row with `deleted_at` and zero current-list rows. Reassignment preserves creator/status/created-at, and a DONE Task throws without changing assignment.

## RED

**Command**

```text
./mvnw -Dtest=TaskCreationIntegrationTest#creatorMayEditOnlyWhileStillCurrentAssigneeAndLeaderMayEditAnyUnfinishedTask,TaskCreationIntegrationTest#softDeleteExcludesTaskFromCurrentViewsButRetainsHistoricalRow,TaskCreationIntegrationTest#unfinishedReassignmentPreservesCreatorStatusAndCreationAtAndDoneRequiresReopen test
```

**Observed result**

```text
Compilation failed: TaskService had no edit, reassign, or softDelete methods.
```

## GREEN

**Command**

```text
./mvnw -Dtest=TaskCreationIntegrationTest#creatorMayEditOnlyWhileStillCurrentAssigneeAndLeaderMayEditAnyUnfinishedTask,TaskCreationIntegrationTest#softDeleteExcludesTaskFromCurrentViewsButRetainsHistoricalRow,TaskCreationIntegrationTest#unfinishedReassignmentPreservesCreatorStatusAndCreationAtAndDoneRequiresReopen test
```

**Observed result**

```text
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
PostgreSQL 18.4 Testcontainer; Flyway V1 applied successfully.
```

## Affected suite

**Command and result**

```text
./mvnw -Dtest=TaskDefinitionRulesTest,TaskWorkLogRulesTest,TaskProjectQueryTest,TaskTransferServiceTest,TaskCreationIntegrationTest,TaskPersistenceStructureTest,TaskDomainRulesTest,TaskQueryServiceTest,TaskMutationBoundaryTest,TaskControllerTest test
Tests run: 72, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

This milestone does not yet cover edit/delete web forms, work-log HTTP flows, daily serialization, pending-exit targets, direct-removal orchestration, or Project-owned History authorization.
