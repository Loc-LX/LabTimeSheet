# Test Evidence: Public assets and activation-safe response headers

- **Test type:** Web
- **Requirement IDs:** `ACC-001`, `SEC-001`, `SEC-003`, `SEC-009`
- **Scenario IDs:** No direct acceptance-scenario mapping (response-security regression)
- **Test class/method:** `com.lab.labtimesheet.config.SecurityResponseIntegrationTest`
- **Implementation commit:** `6181984cf85f184be39513d6313f9cbe8267add5`

## Protected behavior

Public `/assets/**` requests remain reachable before bootstrap in both the Spring Security chain and bootstrap access
filter. Responses use `Referrer-Policy: no-referrer` so an activation URL bearer token cannot be forwarded in a
same-origin Referer header when a user follows another link.

## Test method

MockMvc starts the production filter chain against PostgreSQL 18.4 before initialization. It requests a known static
test asset and the activation page, asserting successful resource delivery and the exact global response header.

## Hand-derived expected result

The known asset returns HTTP 200 before bootstrap. The activation response contains exactly
`Referrer-Policy: no-referrer`.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=SecurityResponseIntegrationTest test
```

**Observed result**

```text
Tests run: 2, Failures: 2, Errors: 0, Skipped: 0
The asset request returned 404 and the activation response Referrer-Policy header was null.
BUILD FAILURE
```

## GREEN

**Command**

```text
./mvnw -Dtest=SecurityResponseIntegrationTest test
```

**Observed result**

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
PostgreSQL: 18.4
```

## Affected suite

**Command and result**

```text
./mvnw -Dtest=BootstrapIntegrationTest,SmtpOnboardingWebIntegrationTest,AccountActivationIntegrationTest,AccountWebIntegrationTest,BootstrapOnboardingWebIntegrationTest,JavaMailSmtpProbeTest,SecurityResponseIntegrationTest test
Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

This verifies server response behavior through MockMvc, not browser enforcement of Referrer-Policy or Reporting's
integrated asset graph. It does not place a real activation token in logs, evidence, or request fixtures.
