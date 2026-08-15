# Test Evidence: Sanitized production mail exception feedback

- **Test type:** Web
- **Requirement IDs:** `INT-005`, `INT-008`
- **Scenario IDs:** `AC-INT-002` (production mail-failure boundary)
- **Test class/method:** `com.lab.labtimesheet.feature.integration.controller.SmtpOnboardingWebIntegrationTest#failedSmtpTestRendersActionableFeedbackWithoutActivatingTheDraft`
- **Implementation commit:** `bf6f9af78b42151f2c26ef206978e3a55f75594a`

## Protected behavior

Spring Mail delivery failures from the production SMTP adapter return the fixed Admin guidance instead of escaping
the MVC request or exposing provider diagnostics. A failed probe does not mark the draft tested or enable activation.

## Test method

MockMvc saves a valid draft, then the test SMTP boundary throws Spring's production-shaped `MailSendException` with a
distinctive deterministic diagnostic. The authenticated CSRF-protected request crosses the real controller and SMTP
configuration service, and the rendered Thymeleaf response is inspected for the fixed message, raw-text absence, and
absence of the activation action.

## Hand-derived expected result

The response is HTTP 200 on `smtp/form`, contains the fixed operator guidance, omits the exception diagnostic, and
does not offer Activate SMTP because `markTested` was never reached.

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
Tests run: 1, Failures: 0, Errors: 1, Skipped: 0
MailSendException escaped as ServletException with the distinctive diagnostic instead of rendering smtp/form.
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

The test exercises the production Spring Mail exception type without contacting an external SMTP server. It does not
prove live Mailpit/provider interoperability and contains no real credential or activation token.
