# Test Evidence: Admin account directory and identity correction

- **Test type:** Integration
- **Requirement IDs:** `ACC-009`, `ACC-017`–`ACC-019`, `ACC-018`
- **Scenario IDs:** `AC-ACC-012`, `AC-AUTH-001`
- **Test class/method:** `com.lab.labtimesheet.feature.account.service.AccountIdentityCorrectionIntegrationTest`
- **Implementation commit:** `9348b6fabe9fd904a2aaf8cdbabc4890467f06ed`

## Protected behavior

The Account-owned Admin directory searches normalized display name, email, and Student Code and filters only on the
immutable global role. The correction boundary permits only email, Student Code, and lifecycle-allowed Intern dates;
each omitted date is independently retained from the locked profile. Email changes require tested SMTP and commit
atomically with the required notice or replacement activation. Account/profile constraints are flushed before SMTP or
session expiry, so uniqueness failure has no irreversible side effect. Pending activation tokens are replaced,
active/locked sessions are expired, and deactivated/terminal profile boundaries remain read-only without inventing
session history.

## Test method

The PostgreSQL 18.4 Testcontainers test bootstraps an Admin, configures a recording tested SMTP adapter, and exercises
the concrete AccountService boundary with isolated display-name search, pending/active/locked corrections, omitted
date fields, uniqueness failure, non-Admin/guessed targets, and completed/withdrawn terminal profiles. Assertions
inspect only Account-owned projections, account/profile state, token terminal state, and SessionRegistry expiration.
The delivery-failure and uniqueness-failure cases prove profile/email changes roll back before irreversible delivery or
session side effects.

## Hand-derived expected result

Directory text is trimmed and case-folded before matching; an isolated display-name term does not depend on email
matching, and role filtering returns only the selected immutable role. The old pending activation token is unusable
after a successful email correction and the newly delivered token activates the corrected account. Active/locked
corrections notify the new address and expire sessions registered under the old identity. SMTP and PostgreSQL
uniqueness failures leave persisted identity/profile state, delivery, and sessions unchanged. Student Code is editable
only before terminal internship state, omitted date fields retain the locked value, dates only while `NOT_STARTED`,
and non-Admin, guessed-target, deactivated, completed, or withdrawn corrections are denied.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=AccountIdentityCorrectionIntegrationTest test
```

**Observed result**

```text
2026-08-22T13:53:05+07:00 — BUILD FAILURE during testCompile: the new regression fixture returned void from
createPendingMentor while preparing guessed-target coverage. After fixing that fixture-only compile error, the
behavioral RED ran against PostgreSQL 18.4: Tests run: 9, Failures: 1, Errors: 1. The uniqueness test observed a
delivery message before the database Student-Code uniqueness failure, and the omitted-date test rejected the null
end date. These were the expected review-fix REDs.
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=AccountIdentityCorrectionIntegrationTest test
```

**Observed result**

```text
2026-08-22T13:55:39+07:00 — PostgreSQL 18.4 Testcontainers; Tests run: 9, Failures: 0, Errors: 0, Skipped: 0;
BUILD SUCCESS with Java 25.0.4.
```

## Affected suite

**Command and result**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest=AccountActivationIntegrationTest,AccountIdentityCorrectionIntegrationTest,AccountLifecycleIntegrationTest,AccountRecoveryIntegrationTest,AccountRecoveryLockOrderIntegrationTest,AccountScalarLookupIntegrationTest,ActiveMentorIdentityIntegrationTest,BootstrapIntegrationTest,EligibleInternOptionIntegrationTest,InternMutationEligibilityIntegrationTest,InternWorkWindowIntegrationTest,InternshipLifecycleIntegrationTest,LoginThrottleTest,PasswordResetIntegrationTest,UserActionTokenCleanupIntegrationTest test

2026-08-22T13:59:29+07:00 — PostgreSQL 18.4 Testcontainers; Tests run: 46, Failures: 0, Errors: 0, Skipped: 0;
BUILD SUCCESS with Java 25.0.4.
```

## External-test boundaries

This evidence does not prove the Account Directory Thymeleaf form or desktop accessibility; the reports-ui owner
consumes the reviewed AccountService DTO boundary for those screens. It does not expose or persist raw activation
tokens, exercise a real SMTP server, or authorize Project/Task/Attendance data.
