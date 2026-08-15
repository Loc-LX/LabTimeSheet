# Test Evidence: Task Lombok boilerplate retrofit

- **Test type:** Unit
- **Requirement IDs:** `AUTH-011`, `TSK-001`–`TSK-012`, `DB-004`
- **Scenario IDs:** `AC-TSK-001`–`AC-TSK-006`, `AC-TSK-010`
- **Test class/method:** Source audit plus existing Task unit, web, and PostgreSQL integration suites
- **Implementation commit:** `7c1a26d`

## Protected behavior

The Task feature uses the configured Lombok processor for mechanical dependency-injection constructors, JPA no-argument constructors, and existing entity getters. Explicit Task constructors and mutation methods remain responsible for initial state, attribution, timestamps, and workflow invariants. The retrofit must not add entity equality, hash, string, or setter behavior and must not alter the existing public getter contract.

## Test method

The source audit counts the eligible handwritten constructors and getter methods before and after the retrofit. Existing Task tests then exercise Spring injection, MVC binding, JPA materialization, entity getters, authorization, status transitions, comments, and Project/attendance boundaries through the same public behavior used before the source-only change.

## Hand-derived expected result

Four Spring components have injection-only constructors, so all four may use `@RequiredArgsConstructor`. `Task` and `TaskComment` need protected JPA no-argument constructors and may use targeted Lombok generation. The 17 existing entity getters may be generated, but `Task.deletedByMembershipId`, `Task.updatedAt`, and `Task.version` must remain without newly exposed getters. Domain constructors and `Task.changeStatus` must remain explicit.

## RED

**Command**

```text
task_component_boilerplate=$(rg -n 'public (TaskController|TaskService|TaskQueryService|TaskDashboardService)\(' src/main/java/com/lab/labtimesheet/feature/task | wc -l | tr -d ' ')
task_entity_boilerplate=$(rg -n 'protected (Task|TaskComment)\(\)|public (Long|long|String|Instant|LocalDate|TaskStatus) get[A-Z][A-Za-z0-9]*\(\)' src/main/java/com/lab/labtimesheet/feature/task/model/entity | wc -l | tr -d ' ')
printf 'component_boilerplate=%s entity_boilerplate=%s\n' "$task_component_boilerplate" "$task_entity_boilerplate"
test "$task_component_boilerplate" -eq 0 -a "$task_entity_boilerplate" -eq 0
```

**Observed result**

```text
component_boilerplate=4 entity_boilerplate=19
Exit status 1. The Task package still contained all eligible handwritten boilerplate.
```

## GREEN

**Command**

```text
task_component_boilerplate=$(rg -n 'public (TaskController|TaskService|TaskQueryService|TaskDashboardService)\(' src/main/java/com/lab/labtimesheet/feature/task | wc -l | tr -d ' ')
task_entity_boilerplate=$(rg -n 'protected (Task|TaskComment)\(\)|public (Long|long|String|Instant|LocalDate|TaskStatus) get[A-Z][A-Za-z0-9]*\(\)' src/main/java/com/lab/labtimesheet/feature/task/model/entity | wc -l | tr -d ' ')
printf 'component_boilerplate=%s entity_boilerplate=%s\n' "$task_component_boilerplate" "$task_entity_boilerplate"
test "$task_component_boilerplate" -eq 0 -a "$task_entity_boilerplate" -eq 0

export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -DskipTests compile

javap -classpath target/classes -p \
  com.lab.labtimesheet.feature.task.model.entity.Task \
  com.lab.labtimesheet.feature.task.model.entity.TaskComment \
  com.lab.labtimesheet.feature.task.service.TaskService \
  com.lab.labtimesheet.feature.task.service.TaskQueryService \
  com.lab.labtimesheet.feature.task.service.TaskDashboardService \
  com.lab.labtimesheet.feature.task.controller.TaskController
```

**Observed result**

```text
component_boilerplate=0 entity_boilerplate=0
Maven compile: BUILD SUCCESS; 127 production source files compiled with Java 25.
Bytecode inspection retained the four public component constructors, both protected JPA constructors, and all 17 existing entity getters. No getter exists for deletedByMembershipId, updatedAt, or version; domain constructors and Task.changeStatus remain explicit.
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest='TaskDomainRulesTest,TaskPersistenceStructureTest,TaskQueryServiceTest,TaskDashboardServiceTest' test
# BUILD SUCCESS: 26 tests, 0 failures, 0 errors, 0 skipped.

./mvnw -Dtest='TaskControllerTest,ProjectTaskShellContractTest,ProjectTaskFormAccessibilityWebTest' test
# BUILD SUCCESS: 28 tests, 0 failures, 0 errors, 0 skipped.

export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest='TaskCreationIntegrationTest,TaskMutationBoundaryTest' test
# BUILD SUCCESS against PostgreSQL 18.4: 16 tests, 0 failures, 0 errors, 0 skipped.

./mvnw -DskipTests -Ddoclint=all javadoc:javadoc
# BUILD SUCCESS. Existing warnings were outside feature.task; the Task package emitted no warning.
```

## External-test boundaries

This source audit does not prove runtime behavior by itself. The affected Task tests and compilation/Javadoc gates cover the behavior-preserving contract; no browser walkthrough or production database is required because templates, mappings, schema, and business logic are unchanged. Unprivileged sandbox attempts could not attach Mockito's Java agent or reach the host Docker socket; the same commands passed outside that sandbox, with the verified OrbStack socket supplied for Testcontainers.
