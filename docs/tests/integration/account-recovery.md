# Test Evidence: Activation resend and forgot/reset-password recovery flows

- **Test type:** Integration
- **Requirement IDs:** `ACC-011`, `ACC-012`, `ACC-013`, `ACC-018`, `NOT-008`, `SEC-002`, `SEC-003`, `SEC-004`, `SEC-005`
- **Scenario IDs:** `AC-ACC-006`, `AC-SEC-002`
- **Test class/method:** `com.lab.labtimesheet.feature.account.service.AccountRecoveryIntegrationTest`
- **Implementation commit:** `a7a20fe` (GREEN); RED `fcb869e`

## Protected behavior

`AccountService` must support two recovery workflows on top of the hashed, single-use token ledger:

- **Activation resend (ACC-012/013):** resending a pending account's activation email invalidates the prior live token so only the newest link can activate (`AC-ACC-006`). Resend is valid only for `PENDING_ACTIVATION` accounts and only when an active SMTP revision exists. A delivery failure invalidates the newly issued token and reports `deliverySucceeded=false`.
- **Forgot/reset password (ACC-011, UI-11/12):** a reset link is issued only for `ACTIVE` accounts; any other email (unknown, `LOCKED`, `PENDING_ACTIVATION`) receives the same generic no-op with no token row and no message (`SEC-005`, `NOT-008`). The reset token is hashed, single-use, and expires after 30 minutes (`SEC-002/003/004`). A successful reset writes new credentials, leaves role and status unchanged, and expires the user's authenticated sessions (`ACC-018`). Password length is enforced at 12–128 characters (`AC-SEC-002`).

## Test method

A real PostgreSQL Testcontainer hosts the JPA entities and Flyway schema. The setup bootstraps an Admin, activates SMTP, and creates and activates a Mentor and an Intern. Tests drive the missing `AccountService.resendActivation`, `requestPasswordReset`, and `resetPassword` methods, inspect the `user_action_tokens` rows directly, and read the Spring Security `SessionRegistry`.

## Hand-derived expected result

- `resendActivation(pendingUserId, adminId)` returns `deliverySucceeded=true`, invalidates the previous activation token, and its fresh token alone activates the account.
- `resendActivation` throws `IllegalArgumentException` for a non-`PENDING` account and `IllegalStateException` when no active SMTP configuration exists; a simulated delivery failure returns `deliverySucceeded=false` and invalidates the newly issued token.
- `requestPasswordReset("mentor@example.com")` returns `true`, records one `PASSWORD_RESET` token with `expiresAt` exactly 30 minutes after the fixed test clock (`2026-08-14T00:30:00Z`), and the derived bearer token resets once and fails on a second use.
- `requestPasswordReset` returns `false` with no message and no token row for unknown, locked, and pending emails.
- After a successful reset the Mentor remains `ACTIVE` with role `MENTOR`, `passwords.matches(fresh, hash)` is `true`, the old password no longer matches, and the Mentor's registered session is expired.
- `resetPassword` returns `false` for an unknown raw token, throws `IllegalArgumentException` for 11- and 129-character passwords, and succeeds with a valid 12-character password.

## RED

**Command**

```text
env JAVA_HOME=C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11 PATH=<JDK bin>;... .\mvnw.cmd -Dtest=AccountRecoveryIntegrationTest "-DargLine=-Duser.timezone=Asia/Ho_Chi_Minh" test
```

**Observed result**

```text
[ERROR] COMPILATION ERROR :
.../AccountRecoveryIntegrationTest.java:[105,30] cannot find symbol
  symbol:   method resendActivation(long,long)
  location: variable accounts of type com.lab.labtimesheet.feature.account.service.AccountService
.../AccountRecoveryIntegrationTest.java:[140,28] cannot find symbol
  symbol:   method requestPasswordReset(java.lang.String)
.../AccountRecoveryIntegrationTest.java:[152,28] cannot find symbol
  symbol:   method resetPassword(java.lang.String,java.lang.String)
[ERROR] 15 errors
[INFO] BUILD FAILURE
```

The test could not compile because `AccountService` exposes no `resendActivation`, `requestPasswordReset`, or `resetPassword` — exactly the missing I2-PLAT-02 recovery API.

## GREEN

**Command**

```text
env JAVA_HOME=C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11 PATH=<JDK bin>;... .\mvnw.cmd -Dtest=AccountRecoveryIntegrationTest "-DargLine=-Duser.timezone=Asia/Ho_Chi_Minh" test
```

**Observed result**

```text
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 34.11 s -- in com.lab.labtimesheet.feature.account.service.AccountRecoveryIntegrationTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Notes: the first GREEN run exposed two real defects that the RED compile could not: Hibernate flushed the replacement-token INSERT before the prior-token invalidation UPDATE, violating the partial unique index `uq_user_action_tokens_one_live`; this was fixed by an immediate bulk invalidation (`invalidateLive`) inside the issuing transaction. The web-layer GET also needed a non-consuming usability probe (`isResetTokenUsable`) so an invalid reset link renders the form error directly.

## Affected suite

**Command and result**

```text
env JAVA_HOME=C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11 PATH=<JDK bin>;... .\mvnw.cmd -Dtest="AccountManagementWebIntegrationTest,AccountLifecycleIntegrationTest,AccountWebIntegrationTest,AccountActivationIntegrationTest,AuthenticationWebIntegrationTest,BootstrapOnboardingWebIntegrationTest,EligibleInternOptionIntegrationTest,BootstrapIntegrationTest,AccountRecoveryIntegrationTest,PasswordRecoveryWebIntegrationTest" "-DargLine=-Duser.timezone=Asia/Ho_Chi_Minh" test
[INFO] Tests run: 36, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## External-test boundaries

These tests prove the token ledger semantics (invalidation, single-use, 30-minute expiry, purpose separation), the enumeration-safe generic behavior, session expiry through the `SessionRegistry`, and password-length enforcement against PostgreSQL. They do not prove the rendered forgot/reset forms, the public controller routes, the SMTP-disabled page states, or the Admin resend button on the account detail page.