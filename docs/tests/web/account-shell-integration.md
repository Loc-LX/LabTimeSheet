# Test Evidence: account shell integration

- **Test type:** Web
- **Requirement IDs:** `UI-001`, `UI-002`, `UI-004`, `UI-009`, `I1-PLAT-06`, `I1-UI-04`
- **Scenario IDs:** `AC-UI-001`, `AC-UI-002`, `AC-UI-005`
- **Test class/method:** `com.lab.labtimesheet.feature.reporting.controller.AccountTemplateIntegrationTest`
- **Implementation commits:** `7dd61b9`, `f48fc63`, `f9ddef6`

## Protected behavior

The authenticated account-creation page consumes the shared role-aware desktop shell and posts to the real account endpoint. The public bootstrap, activation, and login pages consume the local themed authentication shell while preserving their first-Admin, raw-token, and Spring Security form contracts. The Admin dashboard and navigation link to the implemented `/admin/accounts/new` route, and logout remains a CSRF-protected POST in the shared shell.

## Test method

A focused MockMvc slice renders the production bootstrap, account-creation, activation, and login templates through a test-only controller. It asserts the authenticated and public shell markers, local pre-paint theme and CSS assets, real form actions, accessible error status, activation token retention, and the real account-creation URL. The existing PostgreSQL Bootstrap, Account, and Authentication flows then exercise one-time initialization, account creation, activation, normalized login, failed login, authorization, and logout through the production controllers and services.

## Hand-derived expected result

The account-creation response contains `app-shell`, posts to `/admin/accounts`, and exposes `/admin/accounts/new` as the account navigation target. The activation response contains `auth-shell`, posts to `/activate`, retains `raw-token`, and loads `/assets/theme.js` before `/assets/app.css`. Login contains `auth-shell`, posts the expected `username` and `password` fields to `/login`, and exposes a live error announcement. Existing account lifecycle and Admin dashboard requests remain successful on PostgreSQL.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=AccountTemplateIntegrationTest test
```

**Observed result**

```text
Tests run: 2, Failures: 2, Errors: 0, Skipped: 0
AccountTemplateIntegrationTest.accountCreationUsesAuthenticatedShellAndRealAccountRoute expected class="app-shell"
AccountTemplateIntegrationTest.activationUsesPublicAuthShellAndLocalAssets expected class="auth-shell"
BUILD FAILURE
Total time: 4.763 s
```

The account-creation and activation templates were standalone documents and did not consume either shared layout.

After the custom login page landed, its focused pre-change contract also failed as expected:

```text
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
AccountTemplateIntegrationTest.loginUsesPublicAuthShellAndPreservesAuthenticationContract expected class="auth-shell"
BUILD FAILURE
Total time: 5.343 s
```

The first-Admin bootstrap page then established its own layout RED:

```text
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
AccountTemplateIntegrationTest.bootstrapUsesPublicAuthShellAndPreservesFirstAdminContract expected class="auth-shell"
BUILD FAILURE
Total time: 4.771 s
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=AccountTemplateIntegrationTest,DashboardTemplateWebTest,UiContractWebTest test
```

**Observed result**

```text
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 3.710 s
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=AccountTemplateIntegrationTest,AuthenticationWebIntegrationTest,AccountWebIntegrationTest test

PostgreSQL 18.4
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 18.361 s
```

Bootstrap-specific affected suite:

```text
./mvnw -Dtest=AccountTemplateIntegrationTest,BootstrapIntegrationTest test
PostgreSQL 18.4
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 17.350 s
```

## External-test boundaries

The checks prove server rendering, local asset wiring, security-aware account navigation, and the complete account lifecycle through MockMvc/PostgreSQL. They do not replace a real-browser visual check of theme paint timing, password-manager behavior, or desktop overflow.
