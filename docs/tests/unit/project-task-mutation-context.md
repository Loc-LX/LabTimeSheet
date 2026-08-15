# Test Evidence: Locked Project context for Task mutations

- **Test type:** Unit
- **Requirement IDs:** `AUTH-001`, `AUTH-011`, `PRJ-012`
- **Scenario IDs:** `AC-AUTH-001`, `AC-AUTH-010`, `AC-PRJ-006`
- **Test class/method:** `com.lab.labtimesheet.feature.project.service.ProjectTaskMutationContextTest#loadsTheProjectForUpdateBeforeBuildingTheTaskMutationContext`
- **Implementation commit:** `19a3518`

## Protected behavior

Task mutations obtain their Project authorization and current lifecycle, Leader, owning-Mentor, and active-member facts from a DTO-only Project service boundary after the Project row has been locked for update. Missing and unauthorized Projects retain the same non-disclosing denial behavior.

## Test method

The isolated service test invokes `ProjectService.taskMutationContext(actorUserId, projectId)`, verifies that `ProjectRepository.findLockedById` is used and the ordinary `findById` path is not used, and verifies that only the locked entity is passed to the existing Project-owned authorization and DTO mapper. The PostgreSQL integration test additionally exercises the public API with authorized, unauthorized, current-member, and former-member data.

## Hand-derived expected result

Exactly one pessimistic Project lookup occurs before context evaluation. The returned `ProjectTaskContext` exposes scalar/DTO facts only; no Project repository or entity crosses the feature boundary. When called from Task's active transaction, Spring's default `REQUIRED` propagation keeps the row lock in that transaction through its commit or rollback.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=ProjectTaskMutationContextTest test
```

**Observed result**

```text
[ERROR] constructor ProjectService ... cannot be applied to given types
[ERROR] incompatible types: ProjectEntity cannot be converted to long
[INFO] BUILD FAILURE
```

The test failed to compile because Project had no mutation-context API and its context mapper accepted only an unlocked Project ID lookup.

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=ProjectTaskMutationContextTest test
```

**Observed result**

```text
[INFO] Running com.lab.labtimesheet.feature.project.service.ProjectTaskMutationContextTest
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

[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## External-test boundaries

The unit test proves the locked repository path and DTO-only handoff, while the integration coverage proves current Project authorization/member mapping against PostgreSQL 18.4. It does not orchestrate two concurrent database transactions; the lock-retention guarantee relies on the public method's `@Transactional` default `REQUIRED` propagation and the Task caller retaining its outer transaction.
