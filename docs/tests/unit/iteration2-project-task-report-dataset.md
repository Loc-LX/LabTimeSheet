# Test Evidence: Iteration 2 Project/Task HTML report dataset

- **Test type:** Unit
- **Requirement IDs:** `I2-UI-04`, `AUTH-001`, `AUTH-002`, `RPT-003`, `RPT-005`, `RPT-009`
- **Scenario IDs:** `AC-AUTH-008`; `AC-RPT-002` HTML Project/Task role-scope slice only
- **Test class/method:** `com.lab.labtimesheet.feature.reporting.service.ProjectTaskReportServiceTest`, including `currentLeaderKeepsMemberFilterAndReceivesMemberBreakdown`
- **Implementation commit:** `pending local review commit`

## Protected behavior

The HTML Project/Task dataset must use only authorized Project and Task public services, support Project/member/status/due/work-date filters, include logged minutes and status totals, and expose per-member hours only to Admins, owning Mentors, and current Leaders. Ordinary members retain aggregate progress without a detailed member-hours breakdown.

## Test method

The service test supplies authorized Project, Task, membership, and retained work-log DTOs through public service mocks. It derives expected row minutes, filtered status counts, and member-hour visibility independently from the production mapper. A companion MVC test verifies the filter controls and accessible table output.

After the reviewed Project producer was merged, the report consumes its authoritative retained membership rows directly; it does not fabricate missing membership attribution from the reduced active-member Task context. The role matrix now exercises owning Mentor, current Leader, and ordinary-member behavior for the HTML dataset.

## Hand-derived expected result

For the selected Project, the inclusive `2026-08-10` through `2026-08-20` work-date window yields 60 minutes for the DONE row and 75 minutes for the BLOCKED row; status counts are one DONE and one BLOCKED, total minutes are 135, and member totals are 105 for membership 7 and 30 for membership 8. An ordinary member's guessed membership filter is removed server-side and receives no detailed member-hours map.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=ProjectTaskReportServiceTest' test
```

**Observed result**

```text
Test compilation failed at the new production-shaped three-service constructor call:
ProjectTaskReportService required ProjectQueryService, TaskService but the report dataset now requires the accepted TaskQueryService public boundary.
Tests run: 0; BUILD FAILURE
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=ProjectTaskReportServiceTest,ProjectTaskReportControllerWebTest' test
```

**Observed result**

```text
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS on Java 25, including the current-Leader role regression.
```

## Integrated Project producer repair

The first affected run after merging reviewed Project head `ea5f8ce6788abf7cb47343dcf32ab5da1b31f2d7` failed during main compilation because the report fallback still invoked the superseded six-field `ProjectMemberView` constructor. The accepted DTO now retains add/remove actor attribution, so the reduced-context fallback was invalid. Removing that fallback and reading only `ProjectQueryService.members` is the production repair; fixtures now supply real attribution values and the current-Leader branch has an explicit regression.

```text
./mvnw -q -DskipTests compile
RED: compilation failed at ProjectTaskReportService.java:235 because the accepted eight-field ProjectMemberView constructor did not match the fabricated six-field fallback.

./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=ProjectTaskReportServiceTest,ProjectTaskReportControllerWebTest' test
GREEN: Tests run: 7, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=DashboardControllerWebTest,NotificationControllerWebTest,DashboardTemplateWebTest,Iteration2ComponentsWebTest,Iteration2WorkflowFragmentsWebTest,UiContractWebTest,ProjectTaskReportControllerWebTest,ProjectTaskReportServiceTest,AttendanceReportControllerWebTest' test

Tests run: 34, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS on Java 25 after the reviewed Project producer merge. `npm run build && npm run test:ui` also passed (5/5 Node tests before this Java-only repair).
```

## External-test boundaries

The unit test does not prove PostgreSQL query authorization, Project/Task transaction behavior, browser keyboard/focus, or XLSX/PDF export parity. Its AC-RPT-002 claim is limited to the HTML Project/Task dataset role-scope slice; exports remain outside this Iteration 2 deliverable.
