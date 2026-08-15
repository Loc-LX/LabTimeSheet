# Test Evidence: Authenticated dashboard landing

- **Test type:** Web
- **Requirement IDs:** `I1-UI-03, I1-UI-04`
- **Scenario IDs:** `I1-UI-04 authentication integration follow-up`
- **Test class/method:** `com.lab.labtimesheet.feature.account.controller.AuthenticationWebIntegrationTest.projectLoginPageSupportsFailureNormalizedSuccessAndLogout`
- **Implementation commit:** `c4656a88806a92cb59b2e588035a4124854feb92`

## Protected behavior

Successful database authentication retains the established `/` success target, and an authenticated GET `/` immediately redirects to the shared role-dashboard route `/dashboard` instead of rendering a standalone dead-end page. Login failure, normalized-email authentication, CSRF, and logout remain covered by the same production-shaped flow.

## Test method

MockMvc logs in through the production Spring Security filter chain using a case-and-whitespace variant of the bootstrapped Admin email. It reuses the resulting authenticated session for GET `/` and asserts the redirect target. The same test continues through the production logout handler. PostgreSQL 18.4 backs the account and session authentication setup.

## Hand-derived expected result

The successful form login redirects to `/`. Following that landing URL with the authenticated session returns a 3xx response whose location is `/dashboard`; it does not resolve `home.html`. Logout still redirects to `/login?logout` and clears authentication.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=AuthenticationWebIntegrationTest test
```

**Observed result**

```text
Authenticated GET / invoked HomeController#home and rendered view "home".
Response status was 200; expected a 3xx redirect to /dashboard.
AuthenticationWebIntegrationTest.java:76 expected:<REDIRECTION> but was:<SUCCESSFUL>
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=AuthenticationWebIntegrationTest test
```

**Observed result**

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
PostgreSQL: 18.4
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw test

Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
PostgreSQL: 18.4
```

## External-test boundaries

The `/dashboard` endpoint and its role-specific content remain owned and tested by Reporting. This test proves only the authenticated platform handoff to that route. It is not a real-browser/accessibility test and does not change dashboard styling, authorization, account activation, or email delivery.
