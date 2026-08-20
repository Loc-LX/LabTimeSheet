# Test Evidence: Leadership term history and stale handoffs

- **Test type:** Integration
- **Requirement IDs:** `PRJ-005`, `PRJ-006`, `AUTH-004`, `DB-003`, `DB-007`
- **Scenario IDs:** `AC-PRJ-002`, `AC-PRJ-003`
- **Test class/method:** `com.lab.labtimesheet.feature.project.service.ProjectServiceIntegrationTest#staleLeaderChangeCannotReplaceTheCurrentTermAfterAnotherChangeCommits`, `#concurrentLeaderChangesWithTheSameTermAllowOneWinner`
- **Implementation commit:** `pending`

## Protected behavior

A `PLANNED` or `ACTIVE` Project keeps exactly one current Leader. A leadership change closes the submitted current term and opens one replacement term atomically. A form carrying an older term identifier is rejected after another change commits, so it cannot restore a former Leader or create a second current term.

## Test method

The Spring Boot integration test uses the repository's PostgreSQL 18.4 Testcontainer and public `ProjectService` API. The stale-form test reads a current term, performs another valid change, then submits the old term identifier and verifies that the current Leader and term counts are unchanged. The concurrency test suspends the test transaction, starts two service transactions with the same term identifier, and verifies that exactly one request succeeds while the other receives `ProjectRuleViolationException`. SQL is used only for independent committed-shape assertions and fixture cleanup.

## Hand-derived expected result

The initial fixture has one current term. After one successful handoff, there are two terms total: one closed and one current. Two requests using the same original term cannot both close it; therefore the concurrent result must be one success, one stale failure, one current term, and two total terms.

## RED

**Command**

```text
& .\mvnw.cmd '-Dtest=ProjectServiceIntegrationTest#staleLeaderChangeCannotReplaceTheCurrentTermAfterAnotherChangeCommits' test
```

**Observed result**

```text
[ERROR] method changeLeader in class com.lab.labtimesheet.feature.project.service.ProjectService
        cannot be applied to given types
[ERROR] required: long,long,long
[ERROR] found:    long,long,long,long
[INFO] BUILD FAILURE
```

The failing test intentionally introduced the expected-term call before the service accepted a stale-write token.

## GREEN

**Command**

```text
& .\mvnw.cmd '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=ProjectServiceIntegrationTest' -q test
```

**Observed result**

```text
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
```

The timezone property is a Windows test-harness setting; PostgreSQL 18.4 rejects the local `Asia/Saigon` alias during Flyway startup, while the application still persists UTC instants.

## Affected suite

**Command and result**

```text
& .\mvnw.cmd '-Dtest=ProjectEntityTest,ProjectControllerTest' test

Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

These tests do not replace browser-level rendering or full HTTP authorization tests, and they do not implement other Iteration 2 Project items. The PostgreSQL exclusion and partial-unique constraints are existing schema safeguards; this change exercises the service/domain stale-token check and the locked transaction boundary without adding a migration.
