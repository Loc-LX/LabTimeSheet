# Test Evidence: Iteration 2 production workflow pages

- **Test type:** Web
- **Requirement IDs:** `I2-UI-02`, `ACC-014`–`ACC-025`, `AUTH-001`–`AUTH-002`, `UI-013`–`UI-014`, `UI-016`, `I2-ATT-01`–`I2-ATT-06`, `I2-PRJ-01`–`I2-PRJ-06`, `I2-TSK-01`, `I2-TSK-03`, `I2-TSK-04`
- **Scenario IDs:** `AC-ACC-009`, `AC-ACC-010`, `AC-AUTH-001`, `AC-ATT-001`, `AC-CAL-002`, `AC-CAL-004`, `AC-COR-001`, `AC-COR-003`, `AC-LEV-003`–`AC-LEV-005`, `AC-PRJ-010`–`AC-PRJ-013`, `AC-TSK-004`, `AC-TSK-005`, `AC-TSK-007`, `AC-UI-005`
- **Test class/method:** `AccountAdministrationControllerWebTest` (3 methods), `AccountWebIntegrationTest#adminListsAndOpensInternLifecycleAdministrationWithoutDisclosingGuessedIds`, `AccountSessionInvalidationWebIntegrationTest#adminLockUnlockAndDeactivateRoutesEnforceLoginStateAndExpireSessions`, `AttendanceRequestControllerWebTest` (8 methods), `CalendarControllerWebTest` (2 methods), `AdminSettingsControllerWebTest` (7 methods), `Iteration2ProjectWorkflowWebTest` (4 methods), `Iteration2TaskWorkflowWebTest` (4 methods), `Iteration2WorkflowFragmentsWebTest` (2 methods), `AttendanceLombokBoilerplateTest#componentsExposeOnlyIntentionalPublicAndProtectedDeclaredMethods`
- **Implementation commit:** `b761290e0bb586f1a9242b52a2c669ff1f4b888f`

## Protected behavior

The supported server-rendered routes expose the accepted producer workflows without duplicating their state machines:
Admin account list/detail, manual account lifecycle, and Project/Task-guarded Intern completion/withdrawal; Intern
leave/correction requests; Mentor decisions/reopen; Admin policy, Calendar import, and redacted configuration Histories;
invitation response; membership-exit readiness and one-recipient transfer batches; retained Project History; and
capability-driven Task edit/delete/reassign/work-log actions. Malformed date, number, and enum values return the same
retained-safe-input workflow instead of a framework 400. Stored Instants render in the named business or attached
historical-policy zone, and integration/lifecycle terminal actions carry explicit consequence confirmations.

## Test method

Eight MVC/template slices invoke the real controllers, templates, request parsing, and redirects while mocking only
reviewed public service/DTO boundaries. Real PostgreSQL web tests additionally prove the Account projection, Admin-only
route, generic guessed-ID response, and lock/unlock/deactivate session behavior. Project integration tests recompute
current leadership and unfinished Tasks under the Account-before-Project lock order before the Account terminal
transition. The fragments test independently protects the accessible shared drawer/history shape.

## Hand-derived expected result

An active Admin sees non-secret account/profile facts and can lock, unlock, deactivate, complete, or withdraw only
through the owning service. Completion remains blocked for a current Leader or unfinished Task owner and succeeds only
after those facts clear. An Intern sees only their request/invitation routes; a Mentor posts decisions to the producer
state machines; Calendar import carries explicit provider identities and local decisions. One transfer POST carries
one source membership, multiple Task IDs, and one recipient membership. Histories render retained provenance without
secrets or fabricated events, independent of the JVM default timezone.

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

The final integrated review produced five additional behavior REDs:

```text
./mvnw '-Dtest=AccountAdministrationControllerWebTest' test
Test compilation failed because AccountAdministrationView and the Admin account list/detail lifecycle routes did not
exist.

./mvnw '-Dtest=Iteration2TaskWorkflowWebTest,AttendanceRequestControllerWebTest,AdminSettingsControllerWebTest' test
Seven new malformed-value assertions failed: Spring converted typed request parameters before controller handling and
returned HTTP 400 instead of the retained-input redirect flow (Task 2, Attendance 3, Admin 2).

./mvnw '-Dtest=AttendanceRequestControllerWebTest,AdminSettingsControllerWebTest,Iteration2ProjectWorkflowWebTest' test
Three timezone assertions failed under a UTC JVM: persisted Instants rendered as UTC/default-zone values despite the
page naming Asia/Ho_Chi_Minh or the correction's attached historical policy zone.

node --test src/test/js/workflow-confirmation-contract.test.mjs
Tests run: 1, Failures: 1. The SMTP and HolidayAPI activation forms had no retirement/replacement confirmation.
```

The same-reviewer pass over the frozen repair commit then found two remaining gate failures:

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw -DskipTests -Ddoclint=all javadoc:javadoc
BUILD FAILURE with 7 errors. The standard Javadoc tool rejected `@implNote` as an unregistered block tag in
AttendanceCorrectionApplicationService and LeaveApplicationService.

JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=Iteration2ProjectWorkflowWebTest,CalendarControllerWebTest' test
Tests run: 6, Failures: 2, Errors: 1. Malformed Project removal and Calendar date values returned framework HTTP 400,
and a Calendar service conflict escaped the retained-input redirect boundary.
```

The same-reviewer pass then found that the raw Project values reached the flash map but numeric template comparisons
still discarded invitation, removal, and transfer selections. The production-shaped retry assertion reproduced it:

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=Iteration2ProjectWorkflowWebTest' test

Tests run: 4, Failures: 1, Errors: 0, Skipped: 0; BUILD FAILURE. After a producer rejection, the first rendered-state
assertion found the retained invitee option without `selected`; the same raw/numeric mismatch also covered removal,
transfer source/error association, Task checkboxes, and recipient radio selection.
```

The first complete candidate gate then exposed one stale source-audit expectation after Calendar's malformed-value
repair deliberately changed typed MVC parameters to retained raw strings:

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar test

PostgreSQL 18.4 where applicable; Tests run: 444, Failures: 1, Errors: 0, Skipped: 0; BUILD FAILURE.
AttendanceLombokBoilerplateTest still expected CalendarController's former LocalDate/boolean/long request signature.
All other 443 tests passed.
```

## GREEN

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=AccountAdministrationControllerWebTest,AccountTemplateIntegrationTest,AttendanceRequestControllerWebTest,AdminSettingsControllerWebTest,CalendarControllerWebTest,Iteration2ProjectWorkflowWebTest,Iteration2TaskWorkflowWebTest,Iteration2WorkflowFragmentsWebTest' test
```

**Observed result**

```text
Java 25.0.4; Tests run: 34, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS. Safe non-secret
input is retained with inline errors across Account, Attendance, Calendar, Project, and Task workflows; explicit zones
are rendered; Admin account controls use Account/Project owners; and terminal actions carry consequence confirmations.
HolidayAPI secrets are deliberately never retained. The Project retry rendering asserts the rejected invitation and
removal options are selected, both requested transfer Tasks and the one recipient are checked, and the transfer error
remains associated with its form.

JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw -DskipTests -Ddoclint=all javadoc:javadoc
BUILD SUCCESS with 100 pre-existing warnings and no errors after replacing the unsupported block tags with standard
Javadoc prose.
```

## Affected suite

**Command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar test

PostgreSQL 18.4 where applicable; Tests run: 444, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
```

The first affected run correctly failed two Attendance public-contract inventory assertions for the newly added
`list(AttendanceActor)` method and `attendanceRecordId` component. Updating that explicit inventory produced a
focused 4/4 GREEN and a 441/441 rerun; no production behavior was weakened. The later exact integrated candidate at
`b761290e0bb586f1a9242b52a2c669ff1f4b888f` includes the reviewed Project/Calendar/Javadoc repairs and the final
Calendar surface-inventory repair, and passed the 444/444 command above.

The latest repair's producer and authorization focus passed on PostgreSQL 18.4:

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=CalendarAuthorizationWebIntegrationTest,CalendarDevelopmentProfileWebIntegrationTest,ProjectInvitationExitIntegrationTest,ProjectLifecycleLockIntegrationTest,ProjectTaskMutationContextTest' test

Tests run: 34, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
```

The explicit Calendar public-contract inventory was updated to the already-reviewed raw request signature and passed
its focused audit:

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw -Dtest=AttendanceLombokBoilerplateTest test

Tests run: 4, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
```

The lifecycle persistence focus (`ProjectServiceIntegrationTest`, `AccountWebIntegrationTest`, and
`AccountSessionInvalidationWebIntegrationTest`) passed 18/18 on PostgreSQL 18.4. `npm run build` and
`npm run test:ui` passed on Node 24/npm 11 (7/7 Node tests). Java 25 compile and `git diff --check` passed. The exact
standard repository-wide Javadoc/doclint command passes with 100 warnings and no errors.

## External-test boundaries

The MVC slices do not prove every producer state machine, scheduler equivalence, notification delivery, or browser
keyboard/visual behavior. The focused PostgreSQL lifecycle tests prove the new terminal-composition boundary, while
the complete producer suites protect the remaining mutations. A final integrated browser demonstration remains part
of the Iteration 2 exit gate.
