# Test Evidence: internship scheduled start and terminal lifecycle

- **Test type:** Integration
- **Requirement IDs:** `ACC-020`–`ACC-025`
- **Scenario IDs:** `AC-ACC-010`
- **Test class/method:** `InternshipLifecycleIntegrationTest#scheduledStartIsIdempotentAndTerminalActionsApplyGuardsAndPreserveCompletedAuthentication`, `ProjectServiceIntegrationTest#adminTerminalReadinessComposesCurrentLeadershipAndUnfinishedTasksBeforeCompletion`, `#terminalCompletionRecomputesAndRejectsLockedLeaderTaskFacts`, `AccountSessionInvalidationWebIntegrationTest#adminLockUnlockAndDeactivateRoutesEnforceLoginStateAndExpireSessions`
- **Implementation commit:** pending local milestone

## Protected behavior

Scheduled and request-time internship activation share the same date/state guard. Admin terminal actions preserve
completed authentication while withdrawing immediately deactivates the account. The Project owner locks the active
Admin and target Account/profile rows, then current Projects in ascending ID order, and recomputes leadership and
unfinished-Task facts before calling the Account transition in the same transaction.

## Test method

The Account test uses PostgreSQL 18.4 Testcontainers and the real SMTP-gated path to exercise scheduled activation and
terminal state outcomes. The Project tests create real membership/leadership and Task rows, prove the read-only Admin
preview changes after leader transfer and Task completion, then exercise both the successful locked completion and a
fresh locked rejection. The web integration test performs lock, unlock, and deactivate through the Admin routes and
checks session/login behavior.

## Hand-derived expected result

The due profile transitions once from `NOT_STARTED` to `ACTIVE`; a second scheduler invocation changes nothing.
Current leadership blocks completion even with an unfinished Task also present. After leadership transfer and Task
completion, the terminal call produces `COMPLETED` plus an `ACTIVE` account. Withdrawal produces `WITHDRAWN` plus
`DEACTIVATED`, retaining attribution.

## RED

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=InternshipLifecycleIntegrationTest#scheduledStartIsIdempotentAndTerminalActionsApplyGuardsAndPreserveCompletedAuthentication' test
```

**Observed result**

Test compilation failed as expected because `InternshipLifecycleGuard` was absent (the first missing public lifecycle contract type). This is the missing Platform lifecycle boundary, not a fixture or environment failure; the compiler stops before reporting the dependent AccountService methods.

The final UI review additionally produced a compile RED for `AccountAdministrationView` and the missing Admin account
routes. The new Project regression initially observed the service boundary was absent; after the boundary existed, its
unchanged assertions protected the locked current-Leader rejection and successful recomputation path.

## GREEN

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest=InternshipLifecycleIntegrationTest#scheduledStartIsIdempotentAndTerminalActionsApplyGuardsAndPreserveCompletedAuthentication test
```

**Observed result**

`BUILD SUCCESS`; PostgreSQL 18.4 Testcontainers started and the original scheduled/terminal lifecycle test passed 1/1.

**Final-review regression command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=ProjectServiceIntegrationTest#terminalCompletionRecomputesAndRejectsLockedLeaderTaskFacts' test
```

`BUILD SUCCESS`; PostgreSQL 18.4 Testcontainers started and the locked current-Leader/unfinished-Task recomputation
regression passed 1/1.

## Affected suite

**Command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=ProjectServiceIntegrationTest,AccountWebIntegrationTest,AccountSessionInvalidationWebIntegrationTest' test
```

**Observed result**

`BUILD SUCCESS`; 18/18 tests passed across the Project/Account lifecycle focus on PostgreSQL 18.4. The complete repaired
candidate then passed 441/441 on PostgreSQL 18.4 where applicable.

## External-test boundaries

The UI preview is deliberately not an authorization or concurrency guarantee. Only `ProjectService` recomputes the
guard under the shared Account/profile-before-Project lock order. The focused test proves current-state rejection and
success, but a separate full concurrency interleaving is covered by the established Project/Task lock-order suites.
