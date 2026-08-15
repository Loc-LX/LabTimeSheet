# Test Evidence: Internship cannot activate before its business start date

- **Test type:** Integration
- **Requirement IDs:** `ACC-019`, `ACC-020`
- **Scenario IDs:** `AC-ACC-010` (start-date transition only)
- **Test class/method:** `com.lab.labtimesheet.feature.account.service.AccountActivationIntegrationTest#internshipCannotActivateBeforeItsBusinessStartDate`
- **Implementation commit:** `6181984cf85f184be39513d6313f9cbe8267add5`

## Protected behavior

An active Intern account cannot move its separately stored internship from `NOT_STARTED` to `ACTIVE` before the
configured inclusive start date in the application's injected business timezone.

## Test method

The PostgreSQL 18.4 test creates and activates an Intern account through the production SMTP/account services. Its
internship starts one business day after the fixed test clock. The Admin attempts the lifecycle transition and the
test reloads the profile through the owning feature repository.

## Hand-derived expected result

The service throws an actionable start-date error and the persisted internship remains `NOT_STARTED`.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=AccountActivationIntegrationTest#internshipCannotActivateBeforeItsBusinessStartDate test
```

**Observed result**

```text
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
Expected code to raise a throwable, but the internship activated before its start date.
BUILD FAILURE
```

## GREEN

**Command**

```text
./mvnw -Dtest=AccountActivationIntegrationTest#internshipCannotActivateBeforeItsBusinessStartDate test
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
./mvnw -Dtest=BootstrapIntegrationTest,SmtpOnboardingWebIntegrationTest,AccountActivationIntegrationTest,AccountWebIntegrationTest,BootstrapOnboardingWebIntegrationTest,JavaMailSmtpProbeTest,SecurityResponseIntegrationTest test
Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

This covers only the early-activation guard. It does not claim the later scheduler, completion, withdrawal, transfer,
or session-lifecycle portions of AC-ACC-010.
