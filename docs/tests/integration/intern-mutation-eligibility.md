# Test Evidence: locked multi-user Account/Intern mutation eligibility

- **Test type:** Integration
- **Requirement IDs:** `ACC-019`–`ACC-023`, `AUTH-001`, `AUTH-002`
- **Scenario IDs:** `AC-ACC-010`, `AC-AUTH-001`
- **Test class/method:** `com.lab.labtimesheet.feature.account.service.InternMutationEligibilityIntegrationTest`
- **Implementation commit:** pending local milestone

## Protected behavior

Project invitation and membership-exit mutations receive only an immutable Account-owned lifecycle snapshot. The
Account service locks every requested account—including the Mentor/Admin actor—in ascending account-ID order, then
locks Intern profiles in that same order, retains both lock sets through the caller's transaction, and reports
completed or otherwise inactive Interns as ineligible. Non-Intern accounts have an explicit empty profile-status
field. A due `NOT_STARTED` Intern is activated from one post-lock server time before the snapshot is built.

## Test method

The PostgreSQL 18.4 Testcontainers tests create a Mentor actor and Intern accounts through the SMTP-gated public
service, complete one Intern, and request IDs in reverse order with a duplicate. A second case calls the boundary on a
due Intern without a scheduler call. A third opens an outer transaction, returns from the boundary, and starts two
independent pessimistic probes—one for the account row and one for the Intern profile row. Each probe is required to
time out on a bounded `Future.get` while the outer transaction remains open, then complete after the outer commit.

## Hand-derived expected result

Requested IDs are deduplicated and returned in ascending order, including any non-Intern actor. Completed profiles
remain retained but are ineligible. A due profile becomes ACTIVE and eligible on first locked boundary access. Both
the account and profile probes remain blocked until the outer transaction completes, proving lock retention rather
than merely method-local locking.

## RED

**Exact-base replay command**

```text
cd /private/tmp/labtimesheet-iteration2/replay-mutation && JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=InternMutationEligibilityIntegrationTest' test
```

**Observed result**

At exact base `58a087b118cc955748d7df1aa47d2bbc3ca0371b`, test compilation failed with two missing Account-owned
boundary symbols: `InternshipLifecycleGuard` and `LockedAccountMutationEligibility`. The replay stopped before
Testcontainers startup; this is the genuine missing producer boundary, not an environment failure.

**Lifecycle RED on the current tree before the request-time activation correction**

```text
cd /private/tmp/labtimesheet-iteration2/platform && JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=InternMutationEligibilityIntegrationTest' test
```

The due-Intern assertion failed `1/3` with `expected: ACTIVE but was: NOT_STARTED`; the retention contention test
already demonstrated its blocking behavior. This isolated the missing request-time activation behavior.

## GREEN

**Command**

```text
cd /private/tmp/labtimesheet-iteration2/platform && JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=InternMutationEligibilityIntegrationTest' test
```

**Observed result**

```text
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

PostgreSQL 18.4 Testcontainers passed ascending multi-user ordering, role-specific profile status, due activation,
completed-Intern rejection, and account/profile lock retention through outer commit.

## Affected suite

**Command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=AccountActivationIntegrationTest,AccountLifecycleIntegrationTest,AccountRecoveryIntegrationTest,AccountRecoveryLockOrderIntegrationTest,EligibleInternOptionIntegrationTest,InternMutationEligibilityIntegrationTest,InternWorkWindowIntegrationTest,InternshipLifecycleIntegrationTest,PasswordRecoveryWebIntegrationTest,ActivationResendWebIntegrationTest,AccountSessionInvalidationWebIntegrationTest,AccountWebIntegrationTest,PasswordResetIntegrationTest' test
```

```text
[INFO] Tests run: 34, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## External-test boundaries

This proves producer lock/order, retention, lifecycle facts, and immutable DTO state only. Project owns aggregate
authorization and must call this method inside its own invitation/exit transaction; this test does not import Project
persistence or claim a browser workflow. It does not claim a generic audit/event store or any history table.
