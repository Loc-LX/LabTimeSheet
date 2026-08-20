# Test Evidence: Atomic Project workflows

- **Test type:** Integration
- **Requirement IDs:** `PRJ-001`–`PRJ-007`, `PRJ-012`, `PRJ-017`, `AUTH-001`–`AUTH-004`, `AUTH-011`, `DB-003`, `DB-007`
- **Scenario IDs:** `AC-AUTH-001`, `AC-AUTH-007`, `AC-AUTH-010`, `AC-PRJ-001`, `AC-PRJ-003`, `AC-PRJ-006`, `AC-PRJ-009`
- **Test class/method:** `com.lab.labtimesheet.feature.project.service.ProjectServiceIntegrationTest`
- **Implementation commits:** `25a855e`, `dbf1202`, `af0eb3c`

## Protected behavior

PostgreSQL transactions persist a planned Project with its initial membership and leadership term, reject unauthorized or duplicate direct additions, change exactly one Leader without moving Task assignments, enforce role/membership visibility without ID disclosure, and activate only when current eligible membership/leadership and live-Task assignee guards pass.

## Test method

A Spring Boot integration test uses the platform-owned PostgreSQL 18.4 Testcontainer and Flyway V1 schema. It calls the public Project service and verifies committed-shape rows and negative-case non-mutation with independent SQL.

## Hand-derived expected result

Creation yields one Project, one active membership, and one current leadership term. Direct addition yields one membership per Project/Intern pair while allowing the same Intern in a second Project. Leader change yields one closed and one current term while the Task assignee ID remains unchanged. Activation persists `ACTIVE` and `activated_at` when every live Task is assigned to a current eligible membership; a live Task assigned to a closed membership leaves the Project `PLANNED` and the Task intact. Admin and owner visibility is allowed. Intern visibility requires a current membership while the Project is `PLANNED` or `ACTIVE`; a closed membership becomes visible again only after the Project is `COMPLETED`. Unrelated and former-member open-Project IDs are denied uniformly. Completed detail has no current Leader and no mutation capability for Admin, owner, or former members.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=ProjectServiceIntegrationTest test
```

**Observed result**

```text
[ERROR] cannot find symbol: class CreateProjectCommand
[ERROR] cannot find symbol: class ProjectService
[INFO] BUILD FAILURE
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=ProjectServiceIntegrationTest test
```

**Observed result**

```text
[INFO] Running com.lab.labtimesheet.feature.project.service.ProjectServiceIntegrationTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Activation transaction regression

**RED:** the focused PostgreSQL activation tests failed at test compilation because `ProjectService.activate(long, long)` did not exist.

**GREEN:** after wiring the locked Project aggregate to Account eligibility and `TaskQueryService.countCurrentTasksAssignedOutside`, both focused activation tests passed. The valid Project became `ACTIVE`; the former-member assignee case threw `ProjectRuleViolationException`, retained `PLANNED`, and preserved its live Task.

## Review round 1 visibility and completed-detail regression

**RED command:**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=ProjectServiceIntegrationTest#listAndDetailQueriesEnforceRoleOwnershipAndMembershipWithoutIdDisclosure+completedProjectQueriesReturnHistoricalMembersWithoutRequiringACurrentLeader test
```

**Observed RED:** `Tests run: 2, Failures: 1, Errors: 1`. The former member still received the active Project in `listVisible`, and completed `detail` threw `ProjectRuleViolationException: Project has no current Leader`.

**Observed GREEN:** the same command completed with `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS` against PostgreSQL 18.4. The test closes a real membership and, for completed history, closes all membership and leadership intervals before querying Admin, owner, and former-member detail DTOs.

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest='Project*Test' test

[INFO] Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## External-test boundaries

This test does not prove MockMvc authorization, Thymeleaf rendering, browser accessibility, or a two-transaction leadership race. The Iteration 2 leadership race and stale-term proof is recorded separately in `project-leadership-terms.md`; this baseline workflow evidence remains limited to its listed scenarios. Iteration 2 invitations/removals/completion services are also out of scope; SQL is used only to shape the already specified completed-history fixture. Task query semantics have their own Task-owned unit evidence; this integration proves Project consumes that public service boundary atomically without importing Task persistence.
