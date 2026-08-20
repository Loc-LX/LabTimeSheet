# Test Evidence: Locked Project context for Task mutations

- **Test type:** Unit
- **Requirement IDs:** `AUTH-001`, `AUTH-011`, `PRJ-012`, `PRJ-020`, `PRJ-021`
- **Scenario IDs:** `AC-AUTH-001`, `AC-AUTH-010`, `AC-PRJ-006`, `AC-PRJ-012`
- **Test class/method:** `com.lab.labtimesheet.feature.project.service.ProjectTaskMutationContextTest`
- **Implementation commit:** `pending`

## Protected behavior

Task mutations obtain their Project authorization and current lifecycle, Leader, owning-Mentor,
active-member, and pending-exit target facts from a DTO-only Project service boundary after
Account rows and Intern profiles for the actor and every current member have been locked in
ascending order, followed by the Project row. The current-member ID set is compared after the
Project lock before the DTO is built. Missing and unauthorized Projects retain the same
non-disclosing denial behavior; pending targets remain in active membership context for existing
rights but are separately identified for new/self-assignment exclusion. The bounded lock set is
the current membership cardinality plus the actor; Task must resolve any email or display-name
data through public Account DTOs rather than Project persistence.

## Test method

The isolated service tests invoke `ProjectService.taskMutationContext(actorUserId, projectId)`,
verify scalar routing and current-member IDs are read before Account locking, Account locks precede
`ProjectRepository.findLockedById`, the ordinary `findById` path is not used, and only the locked
entity plus immutable pending membership IDs are passed to the DTO mapper. The negative test proves
an unrelated actor is rejected before any Account lifecycle lock is requested. PostgreSQL
integration coverage additionally exercises authorized, unauthorized, current-member, and
former-member data and checks joined-at/left-at interval fields through Project-owned DTOs.

## Hand-derived expected result

The scalar current-member snapshot is acquired before Account locks; exactly one pessimistic Project
lookup then establishes the authoritative member set, followed by the pending target-ID read in
the same transaction. The returned `ProjectTaskContext` exposes scalar/DTO facts only; no Project
repository or entity crosses the feature boundary. When called from Task's active transaction,
Spring's default `REQUIRED` propagation keeps the Account/profile locks, Project row lock, and
pending-exit snapshot in that transaction through its commit or rollback.

## RED

**Command and observed result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest='ProjectTaskMutationContextTest#loadsTheProjectForUpdateBeforeBuildingTheTaskMutationContext' test

[ERROR] constructor ProjectTaskContext ... cannot be applied to given types
[ERROR] no suitable method found for taskContext(long,ProjectEntity,Set<Long>)
[INFO] BUILD FAILURE
```

The test failed to compile because the DTO had no pending-exit membership-ID field and the locked
mapper had no immutable pending-state handoff. The unrelated-actor preauthorization regression was
then added against the scalar route/current-member boundary.

A scoped invitation-response regression was captured before the scalar route actor check was
implemented:

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$PATH ./mvnw -Dtest='ProjectTaskMutationContextTest#rejectsInvitationActorMismatchBeforeTakingLifecycleOrProjectLocks' test
[ERROR] Unexpected exception type: expected ProjectAccessDeniedException but was java.lang.IllegalStateException
[ERROR] Caused by: Locked Account snapshot is missing
```

This RED established that an invitation actor mismatch could reach the lock path. The GREEN
implementation rejects the mismatch immediately after the scalar invitation route lookup and
verifies that no Account or Project lock interaction occurs.

## GREEN

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=ProjectTaskMutationContextTest,TaskMutationBoundaryTest,TaskDashboardServiceTest test

[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$PATH ./mvnw -Dtest='ProjectTaskMutationContextTest#rejectsInvitationActorMismatchBeforeTakingLifecycleOrProjectLocks' test

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

[INFO] Tests run: 71, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## External-test boundaries

The unit tests prove the scalar-snapshot, Account-before-Project lock order and DTO-only handoff,
while integration coverage proves current Project authorization/member mapping and lock retention
against PostgreSQL 18.4. Task still owns assignment exclusion, transfer batches, completion
eligibility, and retained history; this boundary only supplies stable Project context facts.
