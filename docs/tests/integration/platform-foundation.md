# Test Evidence: Platform foundation

- **Test type:** Integration
- **Requirement IDs:** `ARC-001–ARC-008, DB-003–DB-012, OPS-003, TST-001–TST-010`
- **Scenario IDs:** `AC-DB-001, AC-OPS-002, AC-TST-001`
- **Test class/method:** `com.lab.labtimesheet.config.PlatformFoundationTest.flywayCreatesApprovedPostgresCatalog`, `com.lab.labtimesheet.config.PlatformFoundationTest.testClockIsDeterministic`
- **Implementation commit:** `4b37f8fd05804d2d76e11cec1afce52919f2eb59`

## Protected behavior

Flyway creates the approved 23-table/56-foreign-key PostgreSQL catalog and seed, and tests receive deterministic time without a developer database. Package structure is protected separately by `LayerStructureTest`.

## Test method

A full Spring context starts against a PostgreSQL 18.4 Testcontainer. JDBC is used only in this schema/catalog verification test to independently count application tables and foreign keys and inspect the seed. The injected test `Clock` is asserted exactly.

## Hand-derived expected result

The approved DDL catalog contains 23 application tables and 56 foreign keys. The seed has checkout grace 30 and five Monday–Friday rows. Test time is `2026-08-14T00:00:00Z` in `Asia/Ho_Chi_Minh`.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=PlatformFoundationTest test
```

**Observed result**

```text
Tests run: 3, Failures: 1, Errors: 1, Skipped: 0
PlatformFoundationTest.flywayCreatesApprovedPostgresCatalog: expected: 23 but was: 0
PlatformFoundationTest.applicationExposesRequiredModulePackages: ClassNotFound com.lab.labtimesheet.accounts.package-info
BUILD FAILURE
```

Flyway reported zero migrations and the first required boundary class was absent, so the failure was caused by the missing foundation.

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=PlatformFoundationTest test
```

**Observed result**

```text
Successfully applied 1 migration to schema "public", now at version v1
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw test

Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

This proves migration replay and catalog shape on an ephemeral local PostgreSQL 18.4 container. It does not prove application container, Compose, CI, external SMTP, browser, publication, or deployment behavior; those boundaries are deferred or owned elsewhere.
