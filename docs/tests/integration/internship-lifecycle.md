# Test Evidence: internship scheduled start and terminal lifecycle

- **Test type:** Integration
- **Requirement IDs:** `ACC-020`–`ACC-025`
- **Scenario IDs:** `AC-ACC-010`
- **Test class/method:** `com.lab.labtimesheet.feature.account.service.InternshipLifecycleIntegrationTest#scheduledStartIsIdempotentAndTerminalActionsApplyGuardsAndPreserveCompletedAuthentication`
- **Implementation commit:** pending local milestone

## Protected behavior

Scheduled and request-time internship activation share the same date/state guard. Admin terminal actions preserve completed authentication while withdrawing immediately deactivates the account; both terminal actions require producer-owned facts that no current leadership or unfinished Task remains.

## Test method

The test uses PostgreSQL 18.4 Testcontainers and the real SMTP-gated account path to create three Interns. It invokes the scheduled guard twice, checks idempotence, exercises both terminal guard failures, completes one Intern, and withdraws another while inspecting only Account-owned repositories.

## Hand-derived expected result

The due profile transitions once from `NOT_STARTED` to `ACTIVE`; a second scheduler invocation changes nothing. Completion produces `COMPLETED` plus an `ACTIVE` account. Withdrawal produces `WITHDRAWN` plus `DEACTIVATED`, retaining the profile row.

## RED

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=InternshipLifecycleIntegrationTest#scheduledStartIsIdempotentAndTerminalActionsApplyGuardsAndPreserveCompletedAuthentication' test
```

**Observed result**

Test compilation failed as expected because `InternshipLifecycleGuard` was absent (the first missing public lifecycle contract type). This is the missing Platform lifecycle boundary, not a fixture or environment failure; the compiler stops before reporting the dependent AccountService methods.

## GREEN

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest=InternshipLifecycleIntegrationTest#scheduledStartIsIdempotentAndTerminalActionsApplyGuardsAndPreserveCompletedAuthentication test
```

**Observed result**

`BUILD SUCCESS`; PostgreSQL 18.4 Testcontainers started and `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`.

## Affected suite

**Command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=AccountActivationIntegrationTest,AccountLifecycleIntegrationTest,AccountRecoveryIntegrationTest,AccountRecoveryLockOrderIntegrationTest,EligibleInternOptionIntegrationTest,InternMutationEligibilityIntegrationTest,InternWorkWindowIntegrationTest,InternshipLifecycleIntegrationTest,PasswordRecoveryWebIntegrationTest,ActivationResendWebIntegrationTest,AccountSessionInvalidationWebIntegrationTest,AccountWebIntegrationTest,PasswordResetIntegrationTest' test
```

**Observed result**

`BUILD SUCCESS`; 34 tests passed across the account-focused affected suite on PostgreSQL 18.4 Testcontainers.

## External-test boundaries

The test supplies only an immutable guard DTO; it does not query Project memberships or Tasks. Project and Task owners must calculate those facts under their own locks and call this Account boundary in the same transaction. Read-only completed authorization across every mutation controller is covered by the integrated authorization suite.
