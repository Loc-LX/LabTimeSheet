# Test Evidence: Public forgot/reset-password flow and Admin activation resend

- **Test type:** Web (MockMvc over real PostgreSQL Testcontainer)
- **Requirement IDs:** `ACC-011`, `ACC-012`, `ACC-013`, `SEC-005`, `NOT-008`
- **Scenario IDs:** `AC-ACC-006`
- **Test classes/methods:** `com.lab.labtimesheet.feature.account.controller.PasswordRecoveryWebIntegrationTest`, `com.lab.labtimesheet.feature.account.controller.AccountManagementWebIntegrationTest#adminResendsActivationEmailAndOnlyAdminMayDoSo`
- **Implementation commit:** `a7a20fe` (GREEN); RED `fcb869e`

## Protected behavior

The browser layer renders and drives the recovery workflows: a public `GET/POST /forgot-password` that always answers with a generic confirmation (`?sent`) regardless of account existence so the endpoint cannot enumerate accounts, a public `GET/POST /reset-password` that renders the new-password form for a usable single-use token and consumes it once, and an Admin-only `POST /admin/accounts/{id}/resend-activation` that mails a fresh activation link to a pending account and redirects to its detail page.

## Test method

A real PostgreSQL Testcontainer hosts the JPA entities and Flyway schema. Setup bootstraps an Admin, activates SMTP, creates and activates a Mentor through the real `/activate` web flow, then performs each flow through MockMvc with CSRF, reading the rendered HTML and the emitted messages from the recording SMTP probe.

## Hand-derived expected result

- `GET /forgot-password` renders `accounts/forgot-password` containing "Reset your password".
- `POST /forgot-password` for a known active email and for an unknown email both redirect to `/forgot-password?sent`; only the known email yields one outgoing message.
- After a reset request, `GET /reset-password?token=<valid>` renders `accounts/reset-password` containing "Choose a new password".
- `POST /reset-password` with a valid token and matching passwords redirects to `/login?reset` and the persisted password hash matches the new value.
- `GET /reset-password?token=<bogus>` renders the form with "invalid or no longer usable"; `POST /reset-password` with mismatched passwords renders the form with "Passwords do not match".
- `POST /admin/accounts/{id}/resend-activation` by an Admin redirects to `/admin/accounts/{id}?resent` and emits one message whose body contains an activation token; a non-Admin caller receives `403`.

## RED

**Command**

```text
env JAVA_HOME=C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11 PATH=<JDK bin>;... .\mvnw.cmd -Dtest="PasswordRecoveryWebIntegrationTest,AccountManagementWebIntegrationTest" "-DargLine=-Duser.timezone=Asia/Ho_Chi_Minh" test
```

**Observed result**

```text
[ERROR] COMPILATION ERROR :
.../AccountRecoveryIntegrationTest.java ... cannot find symbol
  symbol:   method resendActivation(long,long)
  symbol:   method requestPasswordReset(java.lang.String)
  symbol:   method resetPassword(java.lang.String,java.lang.String)
[ERROR] 15 errors
[INFO] BUILD FAILURE
```

Compilation of the whole test module was blocked by the missing `AccountService` recovery API, so the web tests could not run at all; the `PasswordRecoveryWebIntegrationTest` and the resend test exercise routes and controllers that did not exist (no `/forgot-password`, `/reset-password`, or resend mapping), which would otherwise return 404/405.

## GREEN

**Command**

```text
env JAVA_HOME=C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11 PATH=<JDK bin>;... .\mvnw.cmd -Dtest="PasswordRecoveryWebIntegrationTest,AccountManagementWebIntegrationTest" "-DargLine=-Duser.timezone=Asia/Ho_Chi_Minh" test
```

**Observed result**

```text
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 31.40 s -- in com.lab.labtimesheet.feature.account.controller.AccountManagementWebIntegrationTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 9.842 s -- in com.lab.labtimesheet.feature.account.controller.PasswordRecoveryWebIntegrationTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
env JAVA_HOME=C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11 PATH=<JDK bin>;... .\mvnw.cmd -Dtest="AccountManagementWebIntegrationTest,AccountLifecycleIntegrationTest,AccountWebIntegrationTest,AccountActivationIntegrationTest,AuthenticationWebIntegrationTest,BootstrapOnboardingWebIntegrationTest,EligibleInternOptionIntegrationTest,BootstrapIntegrationTest,AccountRecoveryIntegrationTest,PasswordRecoveryWebIntegrationTest" "-DargLine=-Duser.timezone=Asia/Ho_Chi_Minh" test
[INFO] Tests run: 36, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## External-test boundaries

These tests prove the routes, authorization, CSRF handling, rendering, generic enumeration-safe confirmation, single-use token consumption, and the resend redirect through a real servlet stack and PostgreSQL. They do not prove a real SMTP server round trip (the recording probe substitutes for the adapter), nor the Spring Security `SessionRegistry` invalidation on password reset, which is proven at the integration layer (`AccountRecoveryIntegrationTest#resetPasswordUpdatesCredentialsAndExpiresSessions`).