# Test Evidence: Task work-log lifecycle and daily-limit serialization

- **Test type:** Integration
- **Requirement IDs:** `TSK-013`, `TSK-014`, `TSK-015`, `TSK-016`, `DB-008`
- **Scenario IDs:** `AC-TSK-007`, `AC-TSK-008`
- **Test class/method:** `TaskWorkLogIntegrationTest#workLogRejectsDateOutsideInternshipAndCombinedDailyLimit`, `#workLogRejectsFutureProjectAndMembershipBoundaries`, `#workLogRejectsDateAfterRetainedMembershipClosure`, `#retainedMembershipClosureDateRemainsInclusiveForCombinedTotal`, `#authorCorrectionRetainsStoredIdentityAndRejectsAnotherMember`, `#workLogRejectsInactiveAndTerminalInternLifecycle`, `#profileLockBlocksWorkLogUntilOuterTransactionCommits`, `#accountFirstProjectMutationDoesNotDeadlockWithWorkLog`, `#concurrentProjectsRejectOneOfExactly1441AttemptedMinutes`; `TaskMutationBoundaryTest#workLogLocksAccountBeforeReadingDailyTotalAndWriting`, `#workLogRejectsLockedAccountBeforeReadingDailyTotal`, `#correctionPreReadsDateThenLocksAccountBeforeProjectLogAndTask`, `#pendingExitCurrentAssigneeRetainsExistingStatusEditAndDeleteRights`, `#pendingExitCurrentAssigneeRetainsExistingWorkLogRight`; `TaskTransferServiceTest#pendingExitSourceMayTransferExistingUnfinishedTaskAway`
- **Implementation commit:** `pending local milestone`

## Protected behavior

Task work-log mutations resolve the scalar Account ID, then call the Project-owned mutation context. That context
locks all participating Account/profile rows in global order before the Project; the Account-owned locked Intern
work-window operation then re-enters the already-held actor rows. Task/work-log locks follow before lifecycle/date
validation and the combined daily-total read/save. The Account and Intern-profile pessimistic locks remain held
through validation and save, so concurrent Projects cannot over-allocate one Intern's 1,440-minute daily budget. A
pending-exit member remains eligible for existing assignment, status, edit, delete, work-log, and transfer-away rights;
only new/self-assignment and transfer recipients are excluded. The correction scenario reassigns the Task before the
original author corrects their retained log, while another member remains denied.

## Test method

The Mockito boundary test verifies production call order without importing Account persistence. PostgreSQL 18.4
Testcontainers create two active Projects and two retained membership intervals for one Intern, then exercise future,
Project, join, and leave date rejection, an inclusive closure-date total, cross-Project total enforcement, an externally
held profile lock, an Account-first inverse-order mutation, and two concurrent transactions starting together. Unit
coverage also proves that a pending-exit current assignee retains existing status/edit/delete/work-log rights and can
transfer an existing unfinished Task away, while a pending recipient remains rejected.
Production derives and filters membership intervals through the Project-owned immutable DTO; no Project entity or table
is queried by Task production code. Fixture membership IDs are used only in persisted-total assertions.

## Hand-derived expected result

An effort date of 2027-01-01 is outside the Intern's 2026 inclusive window and is rejected. Two accepted entries of
900 and 540 minutes total exactly 1,440; a further minute is rejected. A membership whose `leftAt` converts to the
work date remains included, so 60 plus 1,380 reaches exactly 1,440 and one more minute is rejected. An outer
transaction holding the Intern profile lock blocks the Task work-log transaction until release. Concurrent 900 and
541 minute entries serialize so one commits and one rejects, leaving exactly one committed allocation.

## RED

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest=TaskMutationBoundaryTest#workLogLocksAccountBeforeReadingDailyTotalAndWriting,TaskWorkLogIntegrationTest test
```

**Observed result**

```text
Compilation failed as expected: TaskService had no work-log operation or Account/work-log dependencies, and
TaskWorkLogRepository had no combined daily-total query. The test fixture compiled after removing an unrelated
missing Set import; the remaining errors were only the expected missing production behavior.

After the initial consumer GREEN, the canonical lock-order/correction RED was:

JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest=TaskMutationBoundaryTest#workLogLocksAccountBeforeReadingDailyTotalAndWriting,TaskMutationBoundaryTest#correctionPreReadsDateThenLocksAccountBeforeProjectLogAndTask test

Compilation failed as expected because the non-locking Project-scoped work-log candidate query
`TaskWorkLogRepository#findByIdAndProjectId` did not yet exist. The updated boundary assertions then required the
reviewed scalar Account → Project context → re-entered Account work-window → Task/log order
(2026-08-20T22:07:06+07:00).

After removing caller-supplied daily membership IDs from the production-shaped test calls, test compilation failed
with 15 expected errors because `TaskService` still exposed the old work-log signatures (2026-08-20T23:47:25+07:00).
The errors were the intended missing public behavior, not fixture or environment failures.
```

## GREEN

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=TaskMutationBoundaryTest#workLogLocksAccountBeforeReadingDailyTotalAndWriting,TaskMutationBoundaryTest#workLogRejectsLockedAccountBeforeReadingDailyTotal,TaskMutationBoundaryTest#correctionPreReadsDateThenLocksAccountBeforeProjectLogAndTask test

JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=TaskWorkLogIntegrationTest test
```

**Observed result**

```text
Focused lock-order/lifecycle/correction boundary tests: Tests run: 3, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS (2026-08-20T23:53:52+07:00), with the explicit Byte Buddy agent required by the host JVM.
Final focused Task unit/transfer suite: Tests run: 11, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS (2026-08-21T00:07:15+07:00) after the final source cleanup.
PostgreSQL 18.4 Testcontainers work-log tests: Tests run: 9, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS (2026-08-21T00:00:44+07:00), including the inverse Account-first Project mutation deadlock regression, 1,441-minute concurrent allocation, and inclusive retained-membership closure-date proof.
The fixture uses the valid coupled account lifecycle state (LOCKED with locked_at, ACTIVE with locked_at cleared);
date/lifecycle rejection, correction identity retention, profile-lock blocking, and concurrent daily-limit
serialization all passed.
Correction discovers the date through the immutable `TaskWorkLogCandidate` projection and loads the managed entity
for the first time through the pessimistic query after Account and Project locks; the candidate fields are then
rechecked before correction.

Review-fix focused GREEN: `TaskMutationBoundaryTest,TaskTransferServiceTest` — Tests run: 14, Failures: 0,
Errors: 0, Skipped: 0; BUILD SUCCESS (2026-08-21T00:20:09+07:00). This includes positive pending-exit current-assignee
rights, pending-exit source transfer-away, and the existing pending-recipient rejection. PostgreSQL 18.4
Testcontainers `TaskWorkLogIntegrationTest` — Tests run: 9, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS
(2026-08-21T00:20:32+07:00), including reassignment before original-author correction and the preserved denial for
another member.
```

## Affected suite

**Command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=TaskDefinitionRulesTest,TaskWorkLogRulesTest,TaskProjectQueryTest,TaskTransferServiceTest,TaskWorkLogIntegrationTest,TaskCreationIntegrationTest,TaskPersistenceStructureTest,TaskDomainRulesTest,TaskQueryServiceTest,TaskMutationBoundaryTest,TaskControllerTest test

Tests run: 86, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS (2026-08-21T00:04:38+07:00) after the 9-test PostgreSQL work-log class and pending-recipient boundary coverage.

Current affected Task suite after review-fix coverage: same command and class list — Tests run: 89, Failures: 0,
Errors: 0, Skipped: 0; BUILD SUCCESS (2026-08-21T00:21:05+07:00). The increase is the three positive pending-exit
unit cases (two boundary methods and one transfer method); the PostgreSQL correction test now also executes the
production-shaped reassignment step without changing the class count.

Current full Maven suite on the merged Platform/Project/Task tree: Tests run: 309, Failures: 0, Errors: 0, Skipped: 0;
BUILD SUCCESS (2026-08-21T00:24:45+07:00), using PostgreSQL 18.4 Testcontainers and the required Byte Buddy agent.
```

## Compile and Javadoc

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -DskipTests compile
BUILD SUCCESS (2026-08-21T00:26:22+07:00).

JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -DskipTests -Ddoclint=all javadoc:javadoc
BUILD SUCCESS (2026-08-21T00:26:27+07:00); repository-wide warnings are pre-existing, with no Task doclint errors.
```

## External-test boundaries

The production API has no caller-supplied daily membership-ID parameter. It consumes and filters the authoritative
retained `ProjectMembershipIntervalView` DTO from `ProjectQueryService#membershipIntervals(long)` while the Project and
Account/profile locks are held; no Project entity/repository or fabricated interval is used here. Instants are converted
to the injected server clock zone and both joined and left dates are inclusive. The test does not cover browser forms or
attendance time.

The reviewed producer routes are consumed in this order: scalar `AccountService#requireAccountIdByEmail(String)`,
`ProjectService#taskMutationContext(long, long)` (globally ordered Account/profile locks then Project), existing
ID-based `lockedInternWorkWindow(long, LocalDate)`, then Task/work-log locks. Pending-exit readiness/approval and
direct-removal orchestration remain Project-owned; Task only rejects pending recipients/new assignments while
preserving existing rights.

Pending-exit coverage is deliberately positive for existing rights and transfer-away, while pending recipients and
new/self-assignment remain rejected. The correction test now reassigns the Task before the original author corrects
the log and still verifies another member is denied. Final `git diff --check` and clean-scope status are recorded in
the branch report; the complete consumer/producer delta remains uncommitted for same-reviewer re-review.
