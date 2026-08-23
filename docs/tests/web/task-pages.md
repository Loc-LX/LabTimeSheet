# Test Evidence: Task pages and server-side request boundaries

- **Test type:** Web
- **Requirement IDs:** `AUTH-001`, `AUTH-002`, `AUTH-005`, `AUTH-009`, `AUTH-011`, `PRJ-015`, `TSK-003`, `TSK-005`, `TSK-007`–`TSK-008`, `TSK-011`, `TSK-012`, `UI-014`
- **Scenario IDs:** `I1-TSK-01`–`I1-TSK-05`, `AC-AUTH-001`, `AC-AUTH-006`, `AC-AUTH-010`, `AC-PRJ-008`, `AC-TSK-002`, `AC-TSK-003`, `AC-TSK-006`, `AC-TSK-010`
- **Test class/method:** `com.lab.labtimesheet.feature.task.controller.TaskControllerTest`
- **Implementation commit:** `fb0ed7f12c9d89235c102b67f2b13f786011c9ee`

## Protected behavior

Task list/detail/create/status/comment routes require authentication, obtain actor identity from Spring Security rather than request IDs, retain CSRF protection, convert guessed-record denial to HTTP 404, validate create input, render the actual Thymeleaf pages, show `N/A` for an empty Project, display assignees, and expose create/status/comment controls only when the service-provided capability permits them. The status form exposes only direct edges from the current fixed status graph. An authorized create request with an invalid due date returns the form with the due-date field error, retained safe input, and refreshed authorized assignees; an access failure still returns non-disclosing HTTP 404.

## Test method

Fifteen `@WebMvcTest` MockMvc invocations render the real Task templates and exercise the real controller, Spring Security filter chain, CSRF filter, Bean Validation binding, redirect contracts, exception-to-status mapping, assignee output, and capability-controlled actions. A four-case parameterized test independently specifies every permitted status choice set. Dedicated create tests distinguish a due-date business validation response from a guessed-Project access response. Only the PostgreSQL-backed Task service is replaced at the controller boundary.

## Hand-derived expected result

Unauthenticated list access returns 401 under the current platform security baseline. An authorized empty list returns 200 and contains `N/A`. A denied guessed Task or Project returns 404. A valid create request passes Project 10, assignee membership 7, the supplied fields, and the authenticated email to the service, then redirects to Task 25. Blank title stays on the form with a field error and no write. An invalid due date returns 200 with the message attached to `dueDate`, keeps title, description, assignee, and date, and reloads the permitted choices. Valid status/comment posts redirect to Task 25.

When `canCreate`, `canChangeStatus`, or `canComment` is false, the corresponding control is absent. When true, it is rendered. The owning Mentor and current assignee may change status on an ACTIVE Project; other members cannot. Both list and detail output the assignee display name. The hand-derived status choices are TODO to IN_PROGRESS/BLOCKED; IN_PROGRESS to BLOCKED/DONE; BLOCKED to TODO/IN_PROGRESS; and DONE to IN_PROGRESS.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=TaskControllerTest test
```

**Observed result**

```text
[ERROR] TaskControllerTest.java:[28,13] cannot find symbol
  symbol: class TaskController
[INFO] BUILD FAILURE
```

The first sandboxed GREEN attempt then exposed an environment boundary, not an application failure: Mockito could not use Java 25 self-attach inside the restricted sandbox. The exact same command was rerun with approved escalation; one test expectation was corrected from a login redirect to the platform baseline's observed 401 response before the final GREEN run.

The later view-capability increment was observed RED at test compilation because the Task DTOs did not yet provide the required capability and assignee fields.

The review-fix increment used the same command and observed these additional production-shaped failures before the controller/form change:

```text
[ERROR] Tests run: 14, Failures: 5, Errors: 0, Skipped: 0
[ERROR] invalidDueDateRendersFieldErrorAndRetainsSafeInput: Status expected:<200> but was:<400>
[ERROR] taskDetailsExposeOnlyAllowedStatusTransitions: expected permitted subsets but was:<{TODO, IN_PROGRESS, BLOCKED, DONE}> for all four source states
[INFO] BUILD FAILURE
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=TaskControllerTest test
```

Run with approved sandbox escalation for Mockito Java 25 self-attach.

**Observed result**

```text
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=TaskDomainRulesTest,TaskPersistenceStructureTest,TaskMutationBoundaryTest,TaskQueryServiceTest,TaskDashboardServiceTest,TaskControllerTest,TaskCreationIntegrationTest test

[INFO] Tests run: 57, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS

./mvnw clean test

[INFO] Tests run: 113, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

The suite ran with approved escalation for OrbStack and Mockito self-attach.

## External-test boundaries

This slice test does not prove PostgreSQL state changes; those are covered by `TaskCreationIntegrationTest` in the affected/full commands. Shared shell styling/navigation remains owned by `work/reports-ui`. Browser journeys, notifications, Iteration 2 workflows, and narrow-screen behavior are outside this Iteration 1 Task evidence.
