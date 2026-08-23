# Test Evidence: UI request queues use producer-owned PostgreSQL reads

- **Test type:** Integration
- **Requirement IDs:** `I2-UI-02`, `I2-ATT-04`, `I2-ATT-05`, `I2-ATT-06`, `I2-PRJ-01`, `I2-PRJ-02`
- **Scenario IDs:** `AC-LEV-003`–`AC-LEV-005`, `AC-COR-001`, `AC-COR-003`, `AC-PRJ-010`, `AC-PRJ-011`
- **Test class/method:** `AttendancePersistenceIntegrationTest#requestQueuesUseRealPersistenceAndRemainRoleScoped`, `ProjectInvitationExitIntegrationTest#leaderInvitationAcceptsOnlyTheIntendedInternAndMentorDirectAddSupersedesIt`
- **Implementation commit:** `pending local independent review`

## Protected behavior

The two new server-rendered list boundaries are producer-owned and authorization-aware. Attendance returns only an
Intern's own leave/correction summaries or the active Mentor's decision queues. Projects returns only pending
invitations addressed to the authenticated active Intern. Reporting/UI never imports a foreign repository/entity.

## Test method

PostgreSQL 18.4 Testcontainers persists requests/invitations through real services, then queries the new public DTO
boundaries as multiple roles. The assertions prove role scoping, stable ordering/display facts, and that the accepted
invitation/direct-add state no longer appears as an actionable pending invitation.

## Hand-derived expected result

Each Intern sees only their own rows; an active Mentor sees the global decision queues; unauthorized/global-role
shapes are rejected by the producer. Only the intended active Intern sees the pending invitation, and accepted or
superseded invitations remain historical rather than actionable.

## RED

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw '-DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' '-Dtest=Iteration2ProjectWorkflowWebTest,AttendanceRequestControllerWebTest' test
```

**Observed result**

```text
Test compilation failed because `LeaveRequestSummary`, `CorrectionSummary`, `PendingProjectInvitationView`, and the
corresponding producer list methods did not exist. Controller-side repository access was deliberately not introduced.
```

## GREEN

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' '-Dtest=ProjectInvitationExitIntegrationTest#leaderInvitationAcceptsOnlyTheIntendedInternAndMentorDirectAddSupersedesIt,AttendancePersistenceIntegrationTest#requestQueuesUseRealPersistenceAndRemainRoleScoped' test
```

**Observed result**

```text
PostgreSQL 18.4; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
```

## Affected suite

**Command and result**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' '-Dtest=Attendance*Test,CalendarImportServiceTest,LeaveApplicationServiceTest,Project*Test,Task*Test,*Reporting*Test,*Dashboard*Test,*Template*Test,*Shell*Test,*Accessibility*Test,*UiContractWebTest,AdminSettingsControllerWebTest,Iteration2*WebTest' test

PostgreSQL 18.4 where applicable; Tests run: 334, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
```

## External-test boundaries

These methods do not replay every producer mutation, concurrency rule, notification, or HTML/browser interaction.
Those remain protected by the reviewed Platform/Projects/Tasks/Attendance evidence and the final integrated exit gate.
