# Test Evidence: Validated bootstrap, SMTP, and account onboarding

- **Test type:** Web
- **Requirement IDs:** `ACC-005–ACC-012`, `INT-004`, `INT-006–INT-008`, `SEC-001`
- **Scenario IDs:** `AC-ACC-003`; `AC-INT-002` (Admin browser boundary)
- **Test class/method:** `com.lab.labtimesheet.feature.account.controller.BootstrapOnboardingWebIntegrationTest`, `com.lab.labtimesheet.feature.integration.controller.SmtpOnboardingWebIntegrationTest`, `com.lab.labtimesheet.feature.account.controller.AccountWebIntegrationTest#invalidAndDuplicateAccountFormsReturnActionableErrorsWithoutCreatingAnotherAccount`
- **Implementation commit:** `17fa25bb0921718f780037cd8c55a956bbdf6b19`; SMTP failure feedback added in `8ff6ee3d873db909b1ce9df690f7a3abb2c3c79d`

## Protected behavior

Bootstrap offers SMTP setup after creating the first Admin. The Admin can save a validated draft, test it, and
activate only a successful test; or traverse five distinct ordered deferral acknowledgements before finishing.
Restricted-installation warnings persist until activation. Invalid bootstrap/account/SMTP forms retain only safe
non-secret values and show actionable errors. All state-changing browser operations require CSRF.

## Test method

MockMvc drives the production controllers, Bean Validation, Thymeleaf rendering, Spring Security filter chain, JPA
services, and PostgreSQL 18.4. SMTP is replaced only at its network adapter. The tests inspect rendered status,
buttons, warnings, validation messages, password non-retention, CSRF denial, ordered deferral navigation, and the
failed-probe response while verifying that activation remains unavailable and raw adapter diagnostics are absent.

## Hand-derived expected result

Successful bootstrap lands on `/admin/smtp?onboarding`. A saved draft shows Test but not Activate; a successful test
shows Activate; activation clears the restricted warning. Deferral exposes warnings one through five in order, Back
and Configure on every screen, and Finish only on screen five. Invalid data returns HTTP 200 with field/global errors
and no submitted password. A failed SMTP probe displays fixed operator guidance and leaves the draft untested without
rendering the adapter's diagnostic.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=BootstrapOnboardingWebIntegrationTest,SmtpOnboardingWebIntegrationTest test
```

**Observed result**

```text
Tests run: 6, Failures: 5, Errors: 1, Skipped: 0
Bootstrap redirected to /login instead of SMTP onboarding; deferral returned 404; SMTP status and warning were
absent; invalid form input raised a validation exception.
BUILD FAILURE
```

The later failure-feedback regression used this focused command:

```text
./mvnw -Dtest=SmtpOnboardingWebIntegrationTest#failedSmtpTestRendersActionableFeedbackWithoutActivatingTheDraft test
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
Expected the configured connection-refusal message, but smtp/form omitted it.
BUILD FAILURE
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=BootstrapOnboardingWebIntegrationTest,SmtpOnboardingWebIntegrationTest test
```

**Observed result**

```text
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
PostgreSQL: 18.4
```

## Affected suite

**Command and result**

```text
./mvnw -Dtest=BootstrapIntegrationTest,SmtpOnboardingWebIntegrationTest,AccountActivationIntegrationTest,AccountWebIntegrationTest,BootstrapOnboardingWebIntegrationTest,JavaMailSmtpProbeTest,SecurityResponseIntegrationTest test
Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
PostgreSQL: 18.4
```

## External-test boundaries

The SMTP adapter is in-memory here, so this does not prove external Mailpit/server interoperability. MockMvc is not a
real browser or accessibility run. The test uses a non-secret diagnostic fixture only to prove that raw adapter text
is absent; it never exposes a password, integration secret, or activation bearer token.
