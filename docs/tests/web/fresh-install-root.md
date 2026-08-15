# Test Evidence: Fresh-install root navigation

- **Test type:** Web
- **Requirement IDs:** `ACC-001`
- **Scenario IDs:** `N/A — user-reported fresh-install navigation regression`
- **Test class/method:** `com.lab.labtimesheet.feature.account.service.BootstrapIntegrationTest.rootGuidesFreshInstallToBootstrapWhileOtherRoutesRemainHidden`
- **Implementation commit:** `531c6078521341b156d69cb1013e54f65c092311`

## Protected behavior

Before the first Admin exists, opening `/` redirects to the public one-time
bootstrap workflow instead of rendering a Whitelabel 404 page. Other protected
application routes remain concealed with HTTP 404 until bootstrap completes.

## Test method

The production Spring Security and bootstrap filter chain runs against a fresh
PostgreSQL 18.4 Testcontainer. MockMvc requests `/bootstrap`, health, `/`, and
`/dashboard`, then verifies that only the root receives the new navigation
redirect while the protected dashboard remains hidden.

## Hand-derived expected result

On an uninitialized installation, GET `/` returns a 3xx response with Location
`/bootstrap`. GET `/dashboard` still returns 404. The bootstrap form and health
endpoint remain available.

## RED

**Command**

```text
export JAVA_HOME=/Users/sechmachine/Library/Java/JavaVirtualMachines/corretto-26.0.2/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=BootstrapIntegrationTest#rootGuidesFreshInstallToBootstrapWhileOtherRoutesRemainHidden test
```

**Observed result**

```text
GET / returned 404.
Expected a 3xx redirect to /bootstrap.
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE
```

## GREEN

**Command**

```text
./mvnw -Dtest=BootstrapIntegrationTest#rootGuidesFreshInstallToBootstrapWhileOtherRoutesRemainHidden test
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
./mvnw -Dtest=BootstrapIntegrationTest,AuthenticationWebIntegrationTest,BootstrapOnboardingWebIntegrationTest,SecurityResponseIntegrationTest test

Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
PostgreSQL: 18.4
```

## External-test boundaries

MockMvc verifies server routing, security, and persistence-backed initialization
state. It does not prove browser rendering or exercise the user's IntelliJ-run
process. The existing browser screenshot independently established the original
Whitelabel 404 symptom.
