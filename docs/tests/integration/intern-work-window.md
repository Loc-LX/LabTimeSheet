# Test Evidence: locked Intern work-window account contract

- **Test type:** Integration
- **Requirement IDs:** `ACC-019`–`ACC-021`
- **Scenario IDs:** `AC-ACC-010`
- **Test class/method:** `com.lab.labtimesheet.feature.account.service.InternWorkWindowIntegrationTest`
- **Implementation commit:** pending local milestone

## Protected behavior

Account consumers receive an immutable work-window DTO rather than an Intern JPA entity. The account and profile are
locked before the service reads one server `Instant`; that post-lock time, converted with the server clock zone,
controls idempotent activation only when the real current date is inclusively inside the internship interval. The
caller-supplied business date remains solely the DTO's evaluation date.

## Test method

The test boots the real account and SMTP paths on PostgreSQL 18.4 with a mutable server clock. It requests a future
business date before the real start, a historical business date after the real start, and a first request after the
internship end. The after-end case also runs the scheduler guard first and asserts that it leaves the profile
`NOT_STARTED`. Each assertion checks that the DTO retains the requested date while lifecycle activation follows only
the locked request-time server date.

## Hand-derived expected result

Before start, a future requested date cannot activate the profile. After start, a historical requested date can still
observe an ACTIVE profile, but remains ineligible because that requested date is outside the inclusive work window.
After end, both a missed scheduler run and first locked access leave a `NOT_STARTED` profile unchanged. The start and
end boundaries are inclusive for request-time activation and scheduler activation.

## RED

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=InternWorkWindowIntegrationTest' test
```

**Observed result**

The production-shaped review regression reached PostgreSQL 18.4 and failed `3/3` assertions: the historical-after-
start case returned `NOT_STARTED` instead of `ACTIVE`, the future-before-start case returned `ACTIVE` instead of
`NOT_STARTED`, and first access after end returned `ACTIVE` instead of `NOT_STARTED`. These are lifecycle-time defects,
not fixture or environment failures.

**Scheduler lock-contention RED**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=InternWorkWindowIntegrationTest#schedulerRechecksClockAfterWaitingForAccountAndProfileLocks' test
```

The PostgreSQL 18.4 Testcontainers run deterministically held both account and Intern-profile locks in an outer
transaction, observed the scheduler's candidate selection and blocked account-lock attempt, advanced the mutable
clock from August 14 into August 17 after the internship ended, then released the locks. The unchanged scheduler
activated with its stale pre-lock timestamp: the assertion expected `0` but observed `1` at
`InternWorkWindowIntegrationTest.java:158`. This is the expected production RED; the contention and clock barriers
completed before the assertion failed.

## GREEN

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=InternWorkWindowIntegrationTest' test
```

**Observed result**

```text
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

PostgreSQL 18.4 Testcontainers passed all three requested-date/server-date cases and the deterministic scheduler
lock-contention case. The scheduler released from the account/profile lock wait after the mutable clock had crossed
the internship end date and returned zero activations.

**Scheduler lock-contention GREEN**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=InternWorkWindowIntegrationTest#schedulerRechecksClockAfterWaitingForAccountAndProfileLocks' test
```

```text
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

The production-shaped PostgreSQL 18.4 test observed the scheduler waiting on the held account lock, advanced the
mutable clock beyond the inclusive end boundary, released both locks, and verified that the post-lock current date
prevented activation.

## Affected suite

**Command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=AccountActivationIntegrationTest,AccountLifecycleIntegrationTest,AccountRecoveryIntegrationTest,AccountRecoveryLockOrderIntegrationTest,EligibleInternOptionIntegrationTest,InternMutationEligibilityIntegrationTest,InternWorkWindowIntegrationTest,InternshipLifecycleIntegrationTest,PasswordRecoveryWebIntegrationTest,ActivationResendWebIntegrationTest,AccountSessionInvalidationWebIntegrationTest,AccountWebIntegrationTest,PasswordResetIntegrationTest' test
```

```text
[INFO] Tests run: 35, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## External-test boundaries

The test proves the Account-owned lock/time/DTO contract, including one deterministic scheduler lock-contention
boundary. It does not authorize Project, Task, or attendance mutations, and it does not claim a real SMTP/browser
journey.

## Final-tree verification

The final Platform tree also passed the affected account suite at `35/35` and the full PostgreSQL 18.4
Testcontainers suite at `244/244` with `BUILD SUCCESS`. The full-suite command was:

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw test
```

The host-level run finished at `2026-08-20T21:21:16+07:00`. A sandbox-only attempt was not a test RED: it stopped
before behavioral execution because the configured Docker socket returned `Operation not permitted`.

Final source checks passed with `./mvnw -DskipTests compile` (`BUILD SUCCESS`, `21:21:25+07:00`) and
`./mvnw -DskipTests -Ddoclint=all javadoc:javadoc` (`BUILD SUCCESS`, `21:21:33+07:00`, 90 existing warnings and no
doclint errors). `git diff --check` passed with no output.
