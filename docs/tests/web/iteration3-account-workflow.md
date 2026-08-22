# Test Evidence: Iteration 3 account administration and workflow version pairs

- **Test type:** Web
- **Requirement IDs:** `ACC-017`–`ACC-019`, `UI-014`
- **Scenario IDs:** `AC-ACC-011`, `AC-ACC-012`
- **Test class/method:** `AccountAdministrationControllerWebTest`; `Iteration2WorkflowFragmentsWebTest`; `account-form-contract.test.mjs`
- **Implementation commit:** `dd066a75986692fb77a6ea38582cd354187e28c6`

## Protected behavior

The Admin account directory can search and filter through the public Account service view, and the
identity correction form exposes only permitted lifecycle fields: email for pending/active/locked
accounts, Student Code for not-started/active Interns, and dates only for not-started Interns. A
deactivated account and terminal profile expose no correction surface. Omitted controls map to null;
duplicate constraints retain safe input and render field errors without SQL diagnostics. Intern-only
fields are cleared and disabled when the role changes, and crafted non-Intern corrections still reach
the service authorization boundary.

## Test method

The MVC tests use the Account service and producer-owned DTOs, asserting route binding, filter values,
safe lifecycle fields, generic failure handling, duplicate constraint mapping, and exact correction
command normalization. The directory fixture derives all four internship labels independently. The
Node contract verifies role-dependent controls and simulates native successful-control serialization for
two Tasks, selecting only `41` and submitting only `taskVersions=41:3`.

## Hand-derived expected result

Search input is trimmed to `intern`; role remains `INTERN`; active corrections omit dates; terminal
corrections omit profile fields; duplicate Student Code maps to a field error; all four labels are
human-readable; and selecting Task `41` submits exactly `taskIds=41` plus `taskVersions=41:3`.

## RED

**Commands**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest=AccountAdministrationControllerWebTest test
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run test:ui
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest=Iteration2WorkflowFragmentsWebTest test
```

**Observed result**

The focused review regressions failed because transfer versions were unconditional, active/terminal
correction controls were not lifecycle-shaped, duplicate corrections escaped as server errors, and
NOT_STARTED rendered as Withdrawn. The Node contract also failed because the transfer controls lacked
selection synchronization. These failures established the missing behavior before implementation.

## GREEN

**Commands and results**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest=AccountAdministrationControllerWebTest test
# BUILD SUCCESS — 11 tests, 0 failures, 0 errors

JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest=Iteration2WorkflowFragmentsWebTest test
# BUILD SUCCESS — 2 tests, 0 failures, 0 errors

PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run build
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run test:ui
# 12 tests passed
```

## Affected suite

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest=AccountAdministrationControllerWebTest,AccountTemplateIntegrationTest,NotificationControllerWebTest,Iteration2WorkflowFragmentsWebTest,ReportingExportControllerWebTest,ReportExportServiceTest,ProjectTaskReportControllerWebTest,AttendanceReportControllerWebTest test
# BUILD SUCCESS — 34 tests, 0 failures, 0 errors

JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest=AccountIdentityCorrectionIntegrationTest,Iteration2ProjectWorkflowWebTest,ProjectControllerTest test
# BUILD SUCCESS — 40 tests, 0 failures, 0 errors; PostgreSQL 18.4 Testcontainers
```

Compile passed with `./mvnw -DskipTests compile`. Javadoc/doclint completed with `BUILD SUCCESS`; the
repository still reports pre-existing warnings in unrelated DTOs/controllers, while this milestone's
new form documentation is warning-free.

## External-test boundaries

This evidence does not claim report export parity, PDF text/font extraction, the managed Chromium
Playwright gate, UI-08, or UI-09. Notification delivery is verified only through the existing public
DTO/service MVC contract; no live SMTP provider is exercised here. No Account repository/entity, foreign
SQL, or Maven dependency configuration was changed by this milestone.
