# Test Evidence: PostgreSQL Task progress and retained history

- **Test type:** Integration
- **Requirement IDs:** `PRJ-015`, `PRJ-016`, `TSK-004`, `TSK-010`, `TSK-011`
- **Scenario IDs:** `AC-PRJ-008`, `AC-TSK-005`, `AC-TSK-011`
- **Test class/method:** `TaskCreationIntegrationTest#projectProgressAndHistoryReadPersistedWorkAndRetainedDeletedRows`
- **Implementation commit:** `pending`

## Protected behavior

PostgreSQL-backed Task progress reads status counts and retained work-log minutes through one
aggregate query, excludes soft-deleted rows from status counts, and retains their work-log minutes
and history projection. History returns stored Task, comment, and work-log facts without inventing
assignment or edit timelines.

## Test method

The test creates a PostgreSQL 18.4 schema, persists a soft-deleted Task, a DONE Task, and a current
Task, then saves dated work logs through the Task repository and reads the DTO-only query boundary.
Assertions independently derive the status counts, 180 retained minutes, and child-history rows.
The companion empty-Project test proves the aggregate still returns one zero-valued projection.

## Hand-derived expected result

One current TODO plus one current DONE gives two Tasks and 50% completion. Work logs on the deleted
and current rows total 60 + 120 = 180 minutes. History contains all three retained Task rows, with
the deleted row and its work log still visible.

## RED

**Command**

```text
./mvnw -Dtest=TaskCreationIntegrationTest#projectProgressAndHistoryReadPersistedWorkAndRetainedDeletedRows test
```

**Observed result**

```text
Compilation failed before TaskWorkLogRepository, TaskProjectProgress, and TaskHistoryView existed.
```

## GREEN

**Command**

```text
./mvnw -Dtest=TaskCreationIntegrationTest#projectProgressAndHistoryReadPersistedWorkAndRetainedDeletedRows test
```

**Observed result**

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
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

This test does not prove Project-owned History authorization, identity display-name resolution,
pending-exit eligibility, or cross-Project daily work-minute serialization; those remain public
Project/Account boundary and service tests.
