# Test Evidence: Sanitized SMTP failure feedback

- **Test type:** Web
- **Requirement IDs:** `INT-005`, `INT-008`
- **Scenario IDs:** `AC-INT-002` (failed-draft browser boundary)
- **Test class/method:** `com.lab.labtimesheet.feature.integration.controller.SmtpOnboardingWebIntegrationTest#failedSmtpTestRendersActionableFeedbackWithoutActivatingTheDraft`
- **Implementation commit:** `06dba4fb13eed675cc08ff8c00fe3e3650468c3b`

## Protected behavior

An SMTP test failure renders fixed actionable guidance but never renders the external adapter's arbitrary diagnostic.
The failed draft remains untested and cannot be activated.

## Test method

MockMvc saves a valid SMTP draft, configures the in-memory network adapter to throw a distinctive non-secret raw
diagnostic, and submits the authenticated CSRF-protected test action. It checks the production controller and
Thymeleaf response for the fixed message, absence of the raw diagnostic, and absence of the activation action.

## Hand-derived expected result

The response is HTTP 200 on `smtp/form`, contains the fixed operator message, omits the adapter diagnostic, and does
not offer Activate SMTP.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=SmtpOnboardingWebIntegrationTest#failedSmtpTestRendersActionableFeedbackWithoutActivatingTheDraft test
```

**Observed result**

```text
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
The fixed guidance was absent and the rendered smtpActionError contained the adapter's distinctive diagnostic.
BUILD FAILURE
PostgreSQL: 18.4
```

## GREEN

**Command**

```text
./mvnw -Dtest=SmtpOnboardingWebIntegrationTest#failedSmtpTestRendersActionableFeedbackWithoutActivatingTheDraft test
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

The SMTP adapter is in-memory, so this does not prove live server interoperability. The diagnostic is a deterministic
non-secret fixture; no password, credential, or activation token is logged or recorded.
