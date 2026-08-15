# Test Evidence: Completed Project member and Task history

- **Test type:** Integration
- **Requirement IDs:** `AUTH-006`, `PRJ-014`
- **Scenario IDs:** `AC-AUTH-007`
- **Test class/method:** `com.lab.labtimesheet.feature.project.service.ProjectServiceIntegrationTest#completedProjectQueriesReturnHistoricalMembersWithoutRequiringACurrentLeader`
- **Implementation commit:** `3d954dd`

## Protected behavior

After Project completion closes every membership and leadership interval, a historical member can still retrieve read-only Task context and member history. The Task context reports no current Leader and no active members, and every historical member row reports `currentLeader=false`.

## Test method

The Spring Boot integration test creates a Project and second member through public Project services, then uses direct SQL only as a fixture to reproduce the Iteration 2 completion result: all leadership and membership intervals are closed and the Project is marked `COMPLETED`. After clearing the persistence context, it calls the public Project query APIs as the former member and checks the DTOs.

## Hand-derived expected result

A completed Project cannot have a current Leader or active member. Therefore `ProjectTaskContext.currentLeaderMembershipId` is `null`, `activeMembers` is empty, both membership-history rows remain visible, both have leave timestamps, and neither is marked as current Leader.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=ProjectServiceIntegrationTest#completedProjectQueriesReturnHistoricalMembersWithoutRequiringACurrentLeader test
```

**Observed result**

```text
[ERROR] ProjectRuleViolationException: Project has no current Leader
    at com.lab.labtimesheet.feature.project.model.entity.ProjectEntity.currentLeader(ProjectEntity.java:232)
    at com.lab.labtimesheet.feature.project.service.ProjectQueryService.taskContext(ProjectQueryService.java:113)
[ERROR] Tests run: 1, Failures: 0, Errors: 1, Skipped: 0
[INFO] BUILD FAILURE
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=ProjectServiceIntegrationTest#completedProjectQueriesReturnHistoricalMembersWithoutRequiringACurrentLeader test
```

**Observed result**

```text
[INFO] Running com.lab.labtimesheet.feature.project.service.ProjectServiceIntegrationTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest='Project*Test' test

[INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## External-test boundaries

This regression proves completed-state DTO behavior against PostgreSQL 18.4. It does not implement or test the future Project-completion mutation itself, browser rendering, or Task-owned authorization and presentation; direct SQL is confined to constructing the completed aggregate fixture.
