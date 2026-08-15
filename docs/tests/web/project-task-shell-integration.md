# Test Evidence: Project and Task shared-shell integration

- **Test type:** Web
- **Requirement IDs:** `UI-003`, `UI-004`, `UI-007`, `UI-009`, `UI-013`, `I1-UI-04`
- **Scenario IDs:** `AC-UI-002`, `AC-UI-003`, `AC-UI-005`
- **Test class/method:** `com.lab.labtimesheet.feature.reporting.controller.ProjectTaskShellContractTest#projectAndTaskPageUsesSharedDesktopShell`, `com.lab.labtimesheet.feature.project.controller.ProjectControllerTest`, `com.lab.labtimesheet.feature.task.controller.TaskControllerTest`
- **Implementation and final-Project integration commits:** `401f676`, `4849e0b`

## Protected behavior

Every Iteration 1 Project and Task page uses the same authenticated desktop shell, local assets, role-aware Project navigation, table containment, form controls, empty states, status badges, and `dd/MM/yyyy` date presentation. Existing capability-gated actions, server routes, validation, authentication, and CSRF contracts remain unchanged.
Project and Task forms provide both an error summary and inline field errors for failed server validation. Every inline error has a stable ID and every invalid control references that ID through `aria-describedby`.

## Test method

The focused parameterized contract checks all five Project and three Task production templates for shared-shell composition and the active Project navigation marker. The affected Project and Task MVC slices then render the production templates through their real controllers while mocking only their feature service boundary, exercising route selection, authorization, form binding, validation, and action visibility.

## Hand-derived expected result

All eight templates reference `fragments/layout :: shell`, identify `projects` as the active navigation section, and contain no duplicate page `<head>`. Mentor-only Project and Task creation controls remain capability-gated; Project members and leadership management stay hidden from non-managers; Task status/comment controls stay hidden when their capability flag is false. Empty Task progress remains `N/A`.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=ProjectTaskShellContractTest test
```

**Observed result**

```text
Tests run: 8, Failures: 8, Errors: 0, Skipped: 0
Each standalone Project and Task template was missing "fragments/layout :: shell(".
BUILD FAILURE
Total time: 3.455 s
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
npm run build
./mvnw -Dtest=ProjectTaskShellContractTest test
```

**Observed result**

```text
Tailwind CSS v4.3.3: Done in 63ms
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=ProjectTaskShellContractTest,ProjectControllerTest,TaskControllerTest test

Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 4.020 s
```

The first affected attempt additionally caught Thymeleaf trying to resolve a `null` action fragment on five pages. Replacing `null` with Thymeleaf's empty fragment token made the identical 25-test command green. After merging the final Project activation pin, the one overlapping detail template retained both the shell and the capability-gated activation form; the focused Project/shell set passed 25 tests.

Final-review form-summary regression:

```text
./mvnw -Dtest=ProjectTaskShellContractTest test
RED: Tests run: 10, Failures: 2, Errors: 0, Skipped: 0
Both forms were missing #fields.hasAnyErrors() and #fields.allErrors().

./mvnw -Dtest=ProjectTaskShellContractTest,ProjectControllerTest,TaskControllerTest test
GREEN: Tests run: 29, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 4.214 s
```

## Final delivery gate

```text
npm ci
added 34 packages; audited 35 packages; 0 vulnerabilities

npm run build
Tailwind CSS v4.3.3: Done in 68ms

./mvnw test
PostgreSQL 18.4 via Testcontainers
Tests run: 152, Failures: 0, Errors: 0, Skipped: 0
36 Surefire reports
BUILD SUCCESS
```

## External-test boundaries

The MVC slices verify server-rendered markup and security/control contracts but do not emulate a browser viewport or visually compare illustrative mockups. PostgreSQL query and mutation behavior remains covered by the feature-owned integration suites; final asset reproducibility and the full PostgreSQL suite are separate delivery gates.
