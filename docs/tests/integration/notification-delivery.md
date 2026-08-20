# Test Evidence: Notification persistence and ordinary-email delivery

- **Test type:** Integration
- **Requirement IDs:** `UC-11`, `NOT-001`–`NOT-010`, `AC-NOT-001`, `AC-NOT-002`, `AC-NOT-004`
- **Scenario IDs:** `I2-PLAT-06`
- **Test class/method:** `com.lab.labtimesheet.feature.notification.service.NotificationServiceIntegrationTest`
- **Implementation commits:** `9ace250` (production), `a1e04ea` (tests)

## Protected behavior

Project invitation and membership-exit notifications are recipient-scoped and deduplicated before persistence. The domain notification is committed independently of SMTP availability: no SMTP records `UNAVAILABLE`, successful ordinary delivery records `SENT`, and a transient provider failure records a bounded retry state without persisting the provider diagnostic. An empty recipient set is a deliberate no-op for self-Task events, and a rolled-back domain transaction must not send email or leave an in-app notification.

## Test method

The test starts the application against the repository PostgreSQL Testcontainer, creates active Mentor and Intern recipients through the existing account services, and replaces only the external `SmtpProbe` with an in-memory recording probe. It exercises the public notification service with four required Project workflow types, duplicate recipient IDs, an empty recipient set, successful SMTP, failing SMTP, and a transaction rollback. Assertions inspect persisted notification rows and recorded SMTP messages.

## Hand-derived expected result

The four Project workflow types produce exactly one row per distinct recipient and type, for eight rows total. Without SMTP, all email states are `UNAVAILABLE` and no probe message is recorded. A successful send reaches `SENT` with one attempt at the fixed test instant. A failed first attempt remains `PENDING` with the one-minute retry time. A rolled-back transaction leaves neither a notification row nor an email message.

## RED

**Command**

```text
$env:JAVA_HOME = 'C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11'; $env:Path = "$env:JAVA_HOME\bin;$env:Path"; .\mvnw.cmd '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=NotificationServiceIntegrationTest' test
```

**Observed result**

```text
BUILD FAILURE during test compilation: notification model, DTO, entity, repository, and service packages/classes do not exist (7 compilation errors).
```

## GREEN

**Command**

```text
$env:JAVA_HOME = 'C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11'; $env:Path = "$env:JAVA_HOME\bin;$env:Path"; .\mvnw.cmd '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=NotificationServiceIntegrationTest' test
```

**Observed result**

```text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
```

## Affected suite

**Command and result**

```text
$env:JAVA_HOME = 'C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11'; $env:Path = "$env:JAVA_HOME\bin;$env:Path"; .\mvnw.cmd '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=NotificationServiceIntegrationTest,SmtpIntegrationTest,AccountActivationIntegrationTest,AccountRecoveryIntegrationTest,LayerStructureTest' test

Tests run: 16, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
```

## External-test boundaries

This test does not prove Project or Task controllers, browser rendering, background retry-worker scheduling, administrator retry UI, or delivery through a real SMTP server. It verifies the notification service seam and the database transaction boundary while the SMTP provider boundary is represented by the existing `SmtpProbe` abstraction.

## Full repository verification

**Commands and results**

```text
$env:JAVA_HOME = 'C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11'; $env:Path = "$env:JAVA_HOME\bin;$env:Path"; .\mvnw.cmd '-Duser.timezone=Asia/Ho_Chi_Minh' test

Tests run: 282, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.

$env:JAVA_HOME = 'C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11'; $env:Path = "$env:JAVA_HOME\bin;$env:Path"; .\mvnw.cmd '-Duser.timezone=Asia/Ho_Chi_Minh' package

Tests run: 282, Failures: 0, Errors: 0, Skipped: 0; WAR assembled; BUILD SUCCESS.

$env:JAVA_HOME = 'C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11'; $env:Path = "$env:JAVA_HOME\bin;$env:Path"; .\mvnw.cmd '-Duser.timezone=Asia/Ho_Chi_Minh' javadoc:javadoc

BUILD SUCCESS; existing repository Javadoc warnings only.

git diff --check

No whitespace errors.
```
