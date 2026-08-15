# Test Evidence: Fixed Task status graph and initial Project progress

- **Test type:** Unit
- **Requirement IDs:** `TSK-007`, `TSK-008`, `PRJ-015`, `PRJ-016`
- **Scenario IDs:** `I1-TSK-03`, `I1-TSK-05`, `AC-TSK-003`, `AC-PRJ-008`
- **Test class/method:** `com.lab.labtimesheet.feature.task.model.TaskDomainRulesTest`
- **Implementation commit:** `17a3c5d`

## Protected behavior

The Task status graph accepts exactly the seven specified directed edges. Initial Project progress counts each current Task status and represents a Project without current Tasks as no percentage rather than zero percent.

## Test method

One parameterized test checks all 16 source/target status pairs against a hand-written allowed-edge table. Two focused tests check empty progress and a four-Task example with two `DONE` Tasks.

## Hand-derived expected result

Allowed edges are `TODO` to `IN_PROGRESS` or `BLOCKED`; `IN_PROGRESS` to `DONE` or `BLOCKED`; `BLOCKED` to `TODO` or `IN_PROGRESS`; and `DONE` to `IN_PROGRESS`. All other pairs are forbidden. Zero Tasks has no percentage. Two `DONE` among four Tasks is 50%, with counts 1 `TODO`, 1 `IN_PROGRESS`, 0 `BLOCKED`, and 2 `DONE`.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=TaskDomainRulesTest test
```

**Observed result**

```text
[ERROR] COMPILATION ERROR :
TaskDomainRulesTest.java:[16,30] cannot find symbol
  symbol:   class TaskStatus
[INFO] BUILD FAILURE
```

The test could not compile because the required Task status and progress domain types did not exist.

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=TaskDomainRulesTest test
```

**Observed result**

```text
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw test

[INFO] Tests run: 107, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## External-test boundaries

This unit evidence does not prove current-assignee authorization, Project lifecycle enforcement, PostgreSQL persistence/query filtering, non-deleted selection, HTTP authorization, or rendered `N/A`. Those require the platform/Project foundation and PostgreSQL/web tests.
