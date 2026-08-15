# Test Evidence: Project lifecycle domain rules

- **Test type:** Unit
- **Requirement IDs:** `PRJ-001`–`PRJ-007`, `PRJ-012`, `PRJ-017`, `AUTH-001`–`AUTH-004`
- **Scenario IDs:** `AC-PRJ-001`, `AC-PRJ-003`, `AC-PRJ-006`, `AC-PRJ-009`
- **Test class/method:** `com.lab.labtimesheet.feature.project.model.entity.ProjectEntityTest`
- **Implementation commits:** `25a855e`, `dbf1202`

## Protected behavior

Project creation cannot produce an empty or leaderless aggregate; direct membership rejects ineligible or duplicate current members; leadership changes leave one current term; activation is owning-Mentor-only and requires an eligible active member, an eligible active current Leader, and valid current Task assignees.

## Test method

Plain JUnit drives the aggregate through its public factory and mutation methods. It asserts externally observable state and denials without Spring or database infrastructure.

## Hand-derived expected result

A planned Project starts with one current membership and one current leadership term. Adding a different eligible Intern yields two current memberships. Changing Leader closes one term and opens one term while retaining both memberships. Activation changes only `PLANNED` to `ACTIVE` when the owning Mentor acts, the supplied active-Intern set contains a current member and the current Leader, and every Task assignee guard passes.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=ProjectTest test
```

**Observed result**

```text
[ERROR] ProjectTest.java:[124,20] cannot find symbol: class Project
[ERROR] ProjectTest.java:[135,20] cannot find symbol: class EligibleIntern
[INFO] BUILD FAILURE
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=ProjectEntityTest test
```

**Observed result**

```text
[INFO] Running com.lab.labtimesheet.feature.project.model.entity.ProjectEntityTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Activation guard regression

**RED:** after strengthening the aggregate test with the active-Intern set, compilation failed because `ProjectEntity.activate` still accepted only `(long, boolean, Instant)` and could not prove that the current Leader remained eligible and active.

**GREEN:** after adding the active-Intern input and aggregate checks, `./mvnw -Dtest=ProjectEntityTest test` passed 6 tests with zero failures, errors, or skips.

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest='Project*Test' test

[INFO] Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## External-test boundaries

This unit test does not prove JPA/Flyway mappings, PostgreSQL constraints or transaction concurrency, Spring Security routing, the Task query implementation, or browser rendering; those are covered at their narrower integration and web layers.
