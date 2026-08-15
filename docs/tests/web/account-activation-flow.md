# Test Evidence: Account creation, activation, authentication, and logout

- **Test type:** Web
- **Requirement IDs:** `ACC-008–ACC-011, ACC-014, ACC-019, AUTH-001–AUTH-002, SEC-002–SEC-004`
- **Scenario IDs:** `AC-ACC-005` (Mentor/Intern browser paths), `AC-ACC-007`
- **Test class/method:** `com.lab.labtimesheet.feature.account.controller.AccountWebIntegrationTest.adminCreatesMentorAndInternThenMentorActivatesAuthenticatesAndLogsOut`
- **Implementation commit:** `8e786ba37ba7fcff09cf88d5951acb21fbb36ea8`; validation/additional-Admin coverage added in `17fa25bb0921718f780037cd8c55a956bbdf6b19`

## Protected behavior

An authenticated Admin can use the account form to create pending Mentor and Intern accounts, the intended recipient can follow the emailed activation link and set a first password, normalized email login succeeds, a Mentor is denied the Admin account route, and logout clears authentication. Browser-submitted blank Intern fields do not prevent Mentor creation.

## Test method

MockMvc drives the production controllers, Thymeleaf templates, CSRF protection, Spring Security login/logout handlers, JPA services, and PostgreSQL 18.4. SMTP is replaced only at the network boundary by an in-memory recording probe. The test extracts the activation token from that immediate test message without logging or persisting the raw value, then exercises the public activation form.

## Hand-derived expected result

The Admin form returns 200. Mentor and Intern submissions redirect to `?created` and persist their immutable roles as pending accounts. Activation redirects to `/login?activated`; login with a case/whitespace variant authenticates the normalized Mentor identity. That session receives 403 at the Admin form and becomes unauthenticated after POST `/logout`.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=AccountWebIntegrationTest test
```

**Observed result**

```text
GET /admin/accounts/new resolved to ResourceHttpRequestHandler
Status expected:<200> but was:<404>
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE
```

After the MVC boundary first reached GREEN, the test was tightened to submit blank Intern controls exactly as the browser form does and observed a second RED:

```text
POST /admin/accounts returned accounts/new with
"Internship fields are allowed only for Intern accounts"
Range for response status value 200 expected:<REDIRECTION> but was:<SUCCESSFUL>
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=AccountWebIntegrationTest test
```

**Observed result**

```text
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw test

Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

This test does not contact Mailpit or an external SMTP server and is not a real browser/accessibility test. Hash-only persistence and exact expiry are covered by the integration test. It does not cover activation resend, password reset, account lock/deactivation, session invalidation after credential/state changes, production origin configuration, containerization, CI, or deployment.
