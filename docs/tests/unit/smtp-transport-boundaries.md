# Test Evidence: Bounded SMTP transport and configured sender name

- **Test type:** Unit
- **Requirement IDs:** `INT-005`, `INT-007`, `NOT-008`
- **Scenario IDs:** No direct acceptance-scenario mapping (transport-adapter regression)
- **Test class/method:** `com.lab.labtimesheet.feature.integration.service.JavaMailSmtpProbeTest`
- **Implementation commit:** `6181984cf85f184be39513d6313f9cbe8267add5`

## Protected behavior

Immediate SMTP calls configure finite connection, read, and write timeouts for SMTP and SMTPS, and apply both the
configured From address and human-readable From name to the MIME message.

## Test method

The test injects a local JavaMail sender factory, exercises both STARTTLS and TLS connections, and inspects the
resulting JavaMail properties and MIME From header without opening a network connection or exposing a real secret.

## Hand-derived expected result

STARTTLS uses `mail.smtp.*` timeout properties; TLS uses `mail.smtps.*`. Each timeout is 5000 milliseconds and the
encoded From header contains the configured address and display name.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=JavaMailSmtpProbeTest test
```

**Observed result**

```text
BUILD FAILURE during test compilation: JavaMailSmtpProbe had no injectable sender-factory constructor needed to
inspect production message construction without network I/O.
```

## GREEN

**Command**

```text
./mvnw -Dtest=JavaMailSmtpProbeTest test
```

**Observed result**

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
./mvnw -Dtest=BootstrapIntegrationTest,SmtpOnboardingWebIntegrationTest,AccountActivationIntegrationTest,AccountWebIntegrationTest,BootstrapOnboardingWebIntegrationTest,JavaMailSmtpProbeTest,SecurityResponseIntegrationTest test
Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

This is a network-free adapter construction test. It does not prove DNS, TLS negotiation, authentication, Mailpit,
or production SMTP interoperability. Test values are non-secret fixtures.
