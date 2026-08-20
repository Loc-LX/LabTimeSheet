# Test Evidence: Iteration 2 production workflow pages

- **Test type:** Web
- **Requirement IDs:** `I2-UI-02`, `I2-ATT-01`–`I2-ATT-06`, `I2-PRJ-01`–`I2-PRJ-06`, `I2-TSK-01`, `I2-TSK-03`, `I2-TSK-04`
- **Scenario IDs:** `AC-ATT-001`, `AC-CAL-002`, `AC-CAL-004`, `AC-COR-001`, `AC-COR-003`, `AC-LEV-003`–`AC-LEV-005`, `AC-PRJ-010`–`AC-PRJ-013`, `AC-TSK-004`, `AC-TSK-005`, `AC-TSK-007`, `AC-UI-005`
- **Test class/method:** `AttendanceRequestControllerWebTest` (4 methods), `AdminSettingsControllerWebTest` (5 methods), `Iteration2ProjectWorkflowWebTest` (3 methods), `Iteration2TaskWorkflowWebTest` (2 methods), `Iteration2WorkflowFragmentsWebTest` (2 methods)
- **Implementation commit:** `pending local independent review`

## Protected behavior

The supported server-rendered routes expose the accepted producer workflows without duplicating their state machines:
Intern leave/correction requests; Mentor decisions/reopen; Admin policy, Calendar import, and redacted configuration
Histories; invitation response; membership-exit readiness and one-recipient transfer batches; retained Project History;
and capability-driven Task edit/delete/reassign/work-log actions. Admin Calendar import submits only explicit provider UUID
and local decision pairs. Shared navigation and Project warnings keep the actions reachable.

## Test method

Five MVC slices invoke the real controllers, templates, CSRF/security configuration, request binding, and redirects
while mocking only the reviewed public service/DTO boundaries. Positive and denied cases assert role-specific actions,
non-empty retained histories, persistent warnings, transfer form shape, explicit HolidayAPI candidate decisions, and
every Iteration 2 Task action. The fragments test independently protects the accessible shared drawer/history shape.

## Hand-derived expected result

An Intern sees only their request/invitation routes; a Mentor posts decisions to the producer state machines; an Admin
can select each provider candidate as day off, working day, or skipped without a positional/index guess. One transfer
POST carries one source membership, multiple Task IDs, and one recipient membership. History renders retained actor
and provenance fields without secrets or fabricated previous-assignee/status/edit events.

## RED

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw '-DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' '-Dtest=Iteration2ProjectWorkflowWebTest,Iteration2TaskWorkflowWebTest,AttendanceRequestControllerWebTest,AdminSettingsControllerWebTest' test
```

**Observed result**

```text
Test compilation failed with missing `AttendanceRequestController`, `AdminSettingsController`, `LeaveRequestSummary`,
`CorrectionSummary`, and `PendingProjectInvitationView`. The expected production routes/read DTOs did not exist.
```

The first implementation review then exposed two production-shaped UI contract gaps:

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw '-DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' '-Dtest=AttendanceRequestControllerWebTest,AdminSettingsControllerWebTest,Iteration2ProjectWorkflowWebTest,Iteration2TaskWorkflowWebTest' test

Tests run: 13, Failures: 4. Each new negative assertion showed that a rejected Attendance, Admin, Project, or Task
submission discarded safe user input instead of retaining it with the rendered inline error.

env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw '-DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' '-Dtest=AttendanceRequestControllerWebTest,AdminSettingsControllerWebTest,Iteration2ProjectWorkflowWebTest' test

Tests run: 11, Failures: 3. The Attendance, Admin History, and Project History pages still emitted ISO machine values
instead of the required `dd/MM/yyyy` business dates and 24-hour Asia/Ho_Chi_Minh time context.
```

## GREEN

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw '-DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' '-Dtest=AttendanceRequestControllerWebTest,AdminSettingsControllerWebTest,Iteration2ProjectWorkflowWebTest,Iteration2TaskWorkflowWebTest,Iteration2WorkflowFragmentsWebTest' test
```

**Observed result**

```text
Java 25.0.4; Tests run: 16, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS. Safe non-secret
input is retained with inline errors, business dates use `dd/MM/yyyy`, and terminal actions carry consequence
confirmations. HolidayAPI secrets are deliberately never retained.
```

## Affected suite

**Command and result**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' '-Dtest=Attendance*Test,CalendarImportServiceTest,LeaveApplicationServiceTest,Project*Test,Task*Test,*Reporting*Test,*Dashboard*Test,*Template*Test,*Shell*Test,*Accessibility*Test,*UiContractWebTest,AdminSettingsControllerWebTest,Iteration2*WebTest' test

PostgreSQL 18.4 where applicable; Tests run: 334, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
```

The first affected run correctly failed two Attendance public-contract inventory assertions for the newly added
`list(AttendanceActor)` method and `attendanceRecordId` component. Updating that explicit inventory produced a
focused 4/4 GREEN and the final affected-suite rerun above; no production behavior was weakened.

All 19 MVC slices were also selected explicitly after the final repairs: 100 tests, 0 failures, 0 errors, 0 skipped;
BUILD SUCCESS. `npm run build` and `npm run test:ui` passed on Node 24/npm 11 (7/7 Node tests). Java 25 compile,
repository-wide Javadoc/doclint, `git diff --check`, and `git diff --cached --check` passed.

## External-test boundaries

These slices do not prove producer transaction/locking rules, persistence authorization, scheduler equivalence,
notification delivery, or browser keyboard/visual behavior. Reviewed producer suites protect mutations; focused real
PostgreSQL read-boundary evidence protects the two new lists. A final integrated browser demonstration remains part of
the Iteration 2 exit gate.
