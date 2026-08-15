# Test Evidence: Task public API documentation retrofit

- **Test type:** Unit
- **Requirement IDs:** `TST-009`
- **Scenario IDs:** `Iteration 1 Task Javadoc retrofit`
- **Test class/method:** `Maven compiler and Javadoc doclint (no synthetic test)`
- **Implementation commit:** `fb0ed7f12c9d89235c102b67f2b13f786011c9ee`

## Protected behavior

Every Task-owned production type and declared public or protected API carries meaningful Javadoc for its business contract. The documented contracts include authorization and lifecycle scope, Project-first/Task-row lock order, non-disclosing HTTP behavior, fixed status transitions, empty progress, actor/history/version invariants, repository filtering and locks, DTO identifier domains and capability flags, the cross-feature activation guard, and dashboard scope/order/limit.

## Test method

This is prose and API documentation, so `TST-009` forbids an artificial unit test. Java 25 compilation checks source validity. The Maven Javadoc plugin runs standard doclint against only `com.lab.labtimesheet.feature.task`, making missing or malformed Task API documentation directly observable without treating unrelated feature retrofit work as Task-owned.

## Hand-derived expected result

The Task package contains 21 production Java types. Each type has a main description. Every declared public/protected constructor and method has a contract comment; record components document their identifier domains, null/empty meanings, and capability semantics. Task-scoped Javadoc generation completes with no warnings.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -DskipTests -Ddoclint=all -Dsubpackages=com.lab.labtimesheet.feature.task javadoc:javadoc
```

**Observed result**

```text
[WARNING] Javadoc Warnings
[WARNING] Task.java: warning: no main description (12 accessors)
[WARNING] TaskComment.java: warning: no main description (5 accessors)
[WARNING] TaskStatus.java: warning: no comment (4 enum constants)
[WARNING] 21 warnings
[INFO] BUILD SUCCESS
```

This was a diagnostic documentation baseline rather than a failing behavioral test. The parent instruction explicitly required doclint/compile instead of a fake test.

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -DskipTests -Ddoclint=all -Dsubpackages=com.lab.labtimesheet.feature.task javadoc:javadoc
```

**Observed result**

```text
[INFO] --- javadoc:3.12.0:javadoc (default-cli) @ labtimesheet ---
[INFO] BUILD SUCCESS
```

No Task-scoped Javadoc warning was emitted.

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -DskipTests compile

[INFO] Compiling 112 source files with javac [debug parameters release 25] to target/classes
[INFO] BUILD SUCCESS

export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw clean test

[INFO] Tests run: 113, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## External-test boundaries

Doclint validates Javadoc structure and references, not whether prose perfectly models runtime behavior. Contract accuracy was checked by a scoped adversarial diff review against the numbered Task, authorization, Project-lifecycle, UI, and database requirements. Other feature owners retain responsibility for their own Iteration 1 Javadoc retrofits.
