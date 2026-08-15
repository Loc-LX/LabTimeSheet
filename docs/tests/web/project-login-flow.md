# Test Evidence: Project-owned login flow

- **Test type:** Web
- **Requirement IDs:** `ACC-009, SEC-001, I1-UI-04`
- **Scenario IDs:** `I1-UI-04 authentication integration follow-up`
- **Test class/method:** `com.lab.labtimesheet.feature.account.controller.AuthenticationWebIntegrationTest.projectLoginPageSupportsFailureNormalizedSuccessAndLogout`
- **Implementation commit:** `a18d8e1d3dd02c8978033f09563d2ec9341926c7`

## Protected behavior

After bootstrap, GET `/login` renders the project's `accounts/login` Thymeleaf view rather than Spring Security's generated page. Invalid credentials remain unauthenticated with generic feedback, a case-and-whitespace variant of the account email authenticates successfully, and POST `/logout` clears the authenticated session. Existing CSRF-protected form processing and server-side authorization remain enabled.

## Test method

MockMvc drives the production Spring Security filter chain, account-backed `UserDetailsService`, Thymeleaf view resolution, CSRF handling, session authentication, logout handler, JPA persistence, and PostgreSQL 18.4. The test creates only the first Admin through the production bootstrap service; no authentication component is mocked.

## Hand-derived expected result

GET `/login` returns 200 with view name `accounts/login` and a POST form targeting `/login`. A wrong password redirects to `/login?error` without authentication and the rendered page shows the same generic error. Login with ` ADMIN@EXAMPLE.COM ` and the correct password redirects to `/`, stores normalized username `admin@example.com`, and logout redirects to `/login?logout`, clears authentication, and renders a signed-out message.

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
GET /login returned Spring Security's generated HTML with no ModelAndView.
AuthenticationWebIntegrationTest.java:48 No ModelAndView found
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

This is a server-side MockMvc test, not a real-browser or accessibility run. It does not validate the future shared-shell styling, login throttling, production transport/cookie configuration, or external identity providers. The milestone does not change activation token creation, persistence, or email delivery.
