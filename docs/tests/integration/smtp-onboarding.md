# Test Evidence: SMTP draft, test, and activation

- **Test type:** Integration
- **Requirement IDs:** `INT-001–INT-008, ACC-011, SEC-001`
- **Scenario IDs:** `AC-INT-001, AC-INT-002`
- **Test class/method:** `com.lab.labtimesheet.feature.integration.service.SmtpIntegrationTest.failedSmtpTestNeverActivatesDraftAndSecretsRemainEncrypted`
- **Implementation commit:** `bc70db1d0d8eaa68bb8e22db44e38af27b0fa945`

## Protected behavior

SMTP credentials are AES-256-GCM encrypted, only a successfully tested draft can activate, and a failed test cannot alter the draft into an active configuration.

## Test method

The test persists a draft through Spring Data JPA against PostgreSQL 18.4 using a deterministic test-only master key and a recording SMTP boundary. It forces send failure, inspects database state, rejects activation, then allows the probe and activates the tested draft.

## Hand-derived expected result

Ciphertext must not contain the submitted password. Failure leaves `status=DRAFT` and `tested_at=null`; activation fails. A successful test sets test provenance and permits exactly that draft to become `ACTIVE`.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=SmtpIntegrationTest test
```

**Observed result**

```text
The pre-refactor RED test source, then named SmtpAccountIntegrationTest.java, reported missing
SmtpConfigurationService and SmtpProbe symbols.
17 compilation errors
BUILD FAILURE
```

The SMTP revision and controllable delivery boundaries were absent.

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=SmtpIntegrationTest test
```

**Observed result**

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
./mvnw test
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The command used the Java 25 and OrbStack environment exports shown above.

## External-test boundaries

The test intentionally does not contact Mailpit or an external SMTP server. The production adapter is compiled, while delivery semantics are exercised through the recording boundary without network or secret egress.
