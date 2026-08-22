# Test Evidence: production transport and readiness gates

- **Test type:** Unit
- **Requirement IDs:** `SEC-010`, `SEC-011`, `SEC-012`, `SEC-013`, `SEC-014`
- **Scenario IDs:** `AC-SEC-004`, `AC-SEC-005`
- **Test class/method:** `com.lab.labtimesheet.config.ProductionReadinessTest`, `com.lab.labtimesheet.config.TrustedForwardedHeaderFilterTest`, `com.lab.labtimesheet.config.OriginEnforcementFilterTest`
- **Implementation commit:** `pending`

## Protected behavior

Production rejects unsafe public-origin, PostgreSQL datasource, explicit trusted-proxy, secure-cookie, SameSite,
safe-error, and 256-bit master-key configuration before readiness. Forwarded headers are adapted only after the raw
socket peer matches the configured numeric proxy networks, and a state-changing request with a present foreign origin
is rejected. Dev/test profiles continue using their explicit HTTP/localhost and relaxed cookie settings.

## Test method

The focused tests exercise the production validation boundary without logging supplied credentials, reject malformed
proxy networks and arbitrary forwarded headers, prove adaptation for an allowed proxy, and verify configured-origin
and missing-origin state-changing requests. The tests use Spring's mock servlet request/response objects at the filter
boundary, which is the narrowest production-shaped check for these transport controls; the affected suite then boots
the application with PostgreSQL 18.4 Testcontainers under test/dev profiles.

## Hand-derived expected result

Production requires an HTTPS non-local origin, a PostgreSQL JDBC URL and non-empty credentials, `framework`
forwarded-header handling, one or more numeric trusted-proxy CIDRs, `Secure` plus `SameSite=Strict` session cookies,
`never` error message/stacktrace inclusion, and a Base64-decoded 32-byte master key. A foreign state-changing origin
must return 403; a forwarded-header request from outside the configured proxy networks must return 400 without reaching
the chain. Dev/test must retain its explicit localhost HTTP and CSRF-protected relaxed profile.

## RED

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest=ProductionReadinessTest,TrustedForwardedHeaderFilterTest,OriginEnforcementFilterTest test
```

**Observed result**

```text
2026-08-22T14:03:47+07:00 — testCompile reported 20 missing production-boundary symbols
(ProductionReadiness, TrustedProxyMatcher, TrustedForwardedHeaderFilter, and OriginEnforcementFilter); BUILD FAILURE.
This was the expected missing-behavior RED after the test fixtures were corrected.
```

## GREEN

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest=ProductionReadinessTest,TrustedForwardedHeaderFilterTest,OriginEnforcementFilterTest test
```

**Observed result**

```text
2026-08-22T14:13:02+07:00 — ProductionReadinessTest 4/4, TrustedForwardedHeaderFilterTest 2/2,
OriginEnforcementFilterTest 2/2; Tests run: 8, Failures: 0, Errors: 0, Skipped: 0; Java 25 compile and BUILD SUCCESS.
```

## Affected suite

**Command and result**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest=SecurityResponseIntegrationTest,AuthenticationWebIntegrationTest,CalendarDevelopmentProfileWebIntegrationTest test
```

```text
2026-08-22T14:11:32+07:00 — PostgreSQL 18.4 Testcontainers; SecurityResponseIntegrationTest 2/2,
CalendarDevelopmentProfileWebIntegrationTest 1/1, AuthenticationWebIntegrationTest 1/1;
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
```

The first sandbox attempt was not evidence because Docker access returned `Operation not permitted`; the identical
command was rerun with the repository's approved Docker/Testcontainers access and passed above.

## External-test boundaries

This evidence does not prove a live external reverse proxy, TLS certificate/renewal, DNS, real browser origin policy,
container startup under a production secret set, or deployment rollback. It does not expose or persist any supplied
secret. Production readiness validation is profile-gated; report/export Unicode font embedding remains the Reports/UI
owner's separate responsibility.
