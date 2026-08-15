# Test Evidence: Atomic first administrator bootstrap

- **Test type:** Integration
- **Requirement IDs:** `ACC-001–ACC-003, ACC-009, SEC-001`
- **Scenario IDs:** `AC-ACC-001, AC-ACC-002`
- **Test class/method:** `com.lab.labtimesheet.feature.account.service.BootstrapIntegrationTest`
- **Implementation commit:** `bc70db1d0d8eaa68bb8e22db44e38af27b0fa945`; restart characterization added in `8ff6ee3d873db909b1ce9df690f7a3abb2c3c79d`

## Protected behavior

Before initialization only bootstrap, bootstrap assets, health, and error rendering are reachable. Concurrent valid submissions create exactly one active Admin, atomically persist initialization, and permanently close bootstrap. A separately started Spring application context connected to the same PostgreSQL database observes the initialized state and cannot create another Admin.

## Test method

A PostgreSQL 18.4 integration test releases two Java 25 tasks onto the same service concurrently and asserts the row-locked outcomes and database state through Spring Data JPA. MockMvc checks pre/post-bootstrap route exposure. A characterization method then starts and closes an independent servlet application context against the same container datasource and verifies the durable state through the public bootstrap service.

## Hand-derived expected result

Two simultaneous submissions produce one `CREATED`, one `ALREADY_INITIALIZED`, one Admin row, and one initialized singleton. Later bootstrap requests cannot create another Admin.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=BootstrapIntegrationTest test
```

**Observed result**

```text
BootstrapIntegrationTest.java: cannot find symbol class BootstrapService
17 compilation errors
BUILD FAILURE
```

The public bootstrap behavior did not exist.

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=BootstrapIntegrationTest test
```

**Observed result**

```text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The independent-context restart assertion was added as characterization coverage for an evidence gap. No
retrospective RED is claimed because the persisted implementation already satisfied it when the test was added.

## Affected suite

**Command and result**

```text
./mvnw test
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The command used the Java 25 and OrbStack environment exports shown above.

## External-test boundaries

This test does not prove deployment-network privacy for the temporary bootstrap route. Operations must still bootstrap on a private interface before public exposure.
