# Test Evidence: Iteration 3 account administration and workflow version pairs

- **Test type:** Web
- **Requirement IDs:** `I3-UI-07`, `I3-UI-01`, `I3-UI-02`
- **Scenario IDs:** Account Directory/Detail/Edit, role-dependent account fields, `AC-TST-001`
- **Test class/method:** `AccountAdministrationControllerWebTest`; `Iteration2WorkflowFragmentsWebTest`; `account-form-contract.test.mjs`
- **Implementation commit:** `dd066a75986692fb77a6ea38582cd354187e28c6`

## Protected behavior

The Admin account directory can search and filter through the public Account service view, and the
identity correction form exposes only permitted email, Student Code, and internship dates. Intern-only
fields are cleared and disabled when the role changes; a crafted non-Intern correction still reaches
the service authorization boundary and renders a generic error. The shared transfer workflow renders
one complete immutable `taskVersions=taskId:version` pair per unfinished Task so stale writes can be
rejected by the owning service.

## Test method

The MVC tests use the Account service and producer-owned DTOs, asserting route binding, filter values,
safe editable fields, generic failure handling, and exact correction command normalization. The fragment
test supplies two Tasks and derives the expected repeated pairs independently (`41:3`, `42:5`). The
Node contract verifies that role-dependent controls are marked in the server template and that the
browser script disables and clears them for non-Intern roles.

## Hand-derived expected result

Search input is trimmed to `intern`; the role remains `INTERN`; correction input trims surrounding
whitespace; role and display name have no form controls; and the workflow emits exactly `41:3` and
`42:5` as hidden inputs.

## RED

**Commands**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest=AccountAdministrationControllerWebTest test
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run test:ui
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest=Iteration2WorkflowFragmentsWebTest test
```

**Observed result**

The account route assertions failed with missing edit routes/filtered directory behavior (404 or
unfiltered output); the Node contract failed 1 assertion because the role-dependent marker was absent;
and the workflow fragment lacked the expected `taskVersions` inputs. These failures established the
missing behavior before implementation.

## GREEN

**Commands and results**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest=AccountAdministrationControllerWebTest test
# BUILD SUCCESS — 7 tests, 0 failures, 0 errors

JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest=Iteration2WorkflowFragmentsWebTest test
# BUILD SUCCESS — 2 tests, 0 failures, 0 errors

PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run build
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run test:ui
# 11 tests passed
```

Merged Platform dependency/export verification also passed:

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest=ReportExportServiceTest,ReportingExportControllerWebTest test
# BUILD SUCCESS — 13 tests, 0 failures, 0 errors
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

This evidence does not claim the managed Chromium Playwright gate; that run remains pending the final
integrated environment. It does not claim UI-08 or UI-09, which remain blocked on exact reviewed
Attendance producer SHAs. Notification delivery is verified only through the existing public DTO/service
MVC contract; no live SMTP provider is exercised here. No Account repository/entity, foreign SQL, or
Maven dependency configuration was changed by this milestone.
