# Test Evidence: Constraint-specific account uniqueness feedback

- **Test type:** Web
- **Requirement IDs:** `ACC-019`, `DB-003`
- **Scenario IDs:** `AC-ACC-005` (Intern creation uniqueness boundary)
- **Test class/method:** `com.lab.labtimesheet.feature.account.controller.AccountWebIntegrationTest#duplicateNormalizedStudentCodeIsReportedOnStudentCodeRatherThanEmail`
- **Implementation commit:** `06dba4fb13eed675cc08ff8c00fe3e3650468c3b`

## Protected behavior

A case- and whitespace-normalized duplicate Intern student code is reported on the student-code field. A distinct
email is not falsely labeled as duplicate, and unknown uniqueness constraints fall back to a non-specific conflict.

## Test method

MockMvc creates one Intern through the authenticated CSRF-protected production form and then submits a second Intern
with a distinct email and the same student code in different case with surrounding whitespace. PostgreSQL 18.4
enforces the real Flyway expression index; the controller maps Hibernate's known constraint name to the form field.

## Hand-derived expected result

The second request returns HTTP 200 on `accounts/new`, retains the safe display name, shows the student-code conflict,
and does not claim that the distinct email already exists.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=AccountWebIntegrationTest#duplicateNormalizedStudentCodeIsReportedOnStudentCodeRatherThanEmail test
```

**Observed result**

```text
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
PostgreSQL reported uq_intern_profiles_student_code_ci, but the form displayed "this email already exists".
BUILD FAILURE
PostgreSQL: 18.4
```

## GREEN

**Command**

```text
./mvnw -Dtest=AccountWebIntegrationTest#duplicateNormalizedStudentCodeIsReportedOnStudentCodeRatherThanEmail test
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
./mvnw -Dtest=TimeConfigurationTest,BootstrapIntegrationTest,SmtpOnboardingWebIntegrationTest,AccountActivationIntegrationTest,AccountWebIntegrationTest,BootstrapOnboardingWebIntegrationTest,JavaMailSmtpProbeTest,SecurityResponseIntegrationTest test
Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
PostgreSQL: 18.4
```

## External-test boundaries

The test covers the two Platform-owned normalized identity constraints. It does not enumerate later-iteration feature
constraints or perform a real-browser accessibility pass.
