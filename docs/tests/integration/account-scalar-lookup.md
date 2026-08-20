# Test Evidence: Scalar authenticated email-to-Account-ID routing

- **Test type:** Integration
- **Requirement IDs:** `I2-PLAT-03 follow-up`; root-authorized Tasks Account-routing dependency (no additional numbered requirement claimed)
- **Scenario IDs:** scalar email normalization; no Account entity hydration; routing-only boundary
- **Test class/method:** `com.lab.labtimesheet.feature.account.service.AccountScalarLookupIntegrationTest#resolvesNormalizedEmailThroughScalarQueryWithoutHydratingAccountEntity`
- **Implementation commit:** `pending (uncommitted producer patch; base 89b99e3d161a63f5b9f140da7e6d8b89a7ebdbc8)`

## Protected behavior

Account exposes a concrete public routing method that normalizes an authenticated email and returns only the matching
Account ID through an explicit scalar repository query. The method does not hydrate `AppUser`, authorize lifecycle
state, or acquire a row lock. Consumers must pass the returned ID to a subsequent Account-owned operation that
acquires and retains the required lifecycle locks before authorization and mutation.

## Test method

The PostgreSQL 18.4 Testcontainers test bootstraps one real Admin, then constructs the real `AccountService` with a
test-only dynamic proxy around the real `AppUserRepository`. The proxy delegates the explicit scalar query, records
its use, and fails if entity-producing email/ID/lifecycle lookup methods are called. The service receives an email
with surrounding whitespace and uppercase characters; a positive ID and scalar-call marker prove normalization and
the no-hydration boundary without importing a persistence type into a consumer.

## Hand-derived expected result

The normalized email identifies the bootstrapped account and returns a positive database ID. The scalar repository
method is invoked exactly on the routing path, while no `AppUser`-returning repository lookup is invoked. No lifecycle
status is inspected and no pessimistic lock is acquired by this method.

## RED

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest=AccountScalarLookupIntegrationTest test
```

**Observed result**

```text
2026-08-20T23:19:45+07:00 — BUILD FAILURE during testCompile; AccountScalarLookupIntegrationTest could not find symbol AccountService.requireAccountIdByEmail(String).
```

This was the expected missing public producer boundary; no container or behavioral execution occurred.

## GREEN

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest=AccountScalarLookupIntegrationTest test
```

**Observed result**

```text
2026-08-20T23:20:40+07:00 — PostgreSQL 18.4 Testcontainers; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
```

## Affected suite

**Command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest='**/feature/account/**/*Test' test
```

```text
2026-08-20T23:23:54+07:00 — PostgreSQL 18.4 Testcontainers; Tests run: 44, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
```

The full branch suite was then run on the same uncommitted tree:

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw test
```

```text
2026-08-20T23:27:11+07:00 — PostgreSQL 18.4 Testcontainers; Tests run: 256, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
```

## Additional verification

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -DskipTests compile
2026-08-20T23:27:34+07:00 — BUILD SUCCESS.

JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -DskipTests -Ddoclint=all javadoc:javadoc
2026-08-20T23:27:46+07:00 — BUILD SUCCESS.

JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest=LayerStructureTest,PlatformFoundationTest,AttendanceLayerStructureTest,ReportingArchitectureTest,ProjectPersistenceStructureTest,TaskPersistenceStructureTest test
2026-08-20T23:28:12+07:00 — PostgreSQL 18.4/Flyway replay and architecture checks; Tests run: 12, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.

git diff --check
2026-08-20T23:28:45+07:00 — no whitespace errors.
```

## External-test boundaries

This evidence proves only the Account service/repository routing boundary and does not authorize or implement any
consumer mutation, lifecycle lock, Project/Task query, browser flow, authentication filter, or notification behavior.
The test-only proxy adds no production hook or dependency. No migration, direct SQL, foreign repository/entity import,
or external service is used.
