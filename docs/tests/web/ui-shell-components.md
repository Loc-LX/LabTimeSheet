# Test Evidence: shared UI shell and components

- **Test type:** Web
- **Requirement IDs:** `ARC-004`, `UI-001`–`UI-010`, `UI-013`–`UI-018`, `I1-UI-01`, `I1-UI-02`, `I1-UI-04`
- **Scenario IDs:** `AC-UI-001`, `AC-UI-002`, `AC-UI-003`, `AC-UI-005`
- **Test class/method:** `com.lab.labtimesheet.ui.UiContractWebTest`
- **Implementation commit:** `bac3981`

## Protected behavior

Domain-owned Thymeleaf pages can render inside one desktop shell with role-filtered navigation, accessible controls/states, pre-paint local theme loading, and committed local CSS/JavaScript/Lucide assets. The tests catch missing fragments, unauthorized or dead navigation links, inaccessible shared form/status markup, remote icon references, or a theme bootstrap loaded after CSS.

## Test method

A test-only domain page consumes the production layout fragment through MockMvc with a real Spring Security principal. A second page renders representative production fragments. The asset test reads the committed classpath artifacts produced by the pinned Node build.

## Hand-derived expected result

A Mentor sees `Owned Projects`, account identity, theme, and logout, but not Admin `Accounts`, Intern `My attendance`, or selector-less Intern attendance. An Intern's attendance link targets the real `/attendance` route. Unimplemented profile and notification destinations are not exposed. The theme script occurs before the stylesheet. Form label/control IDs match, errors use `role="alert"`, status includes a textual accessible name, confirmation copy is described, and the reduced sprite contains the selected symbols without remote resource references.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=UiContractWebTest test
```

**Observed result**

```text
Tests run: 2, Failures: 1, Errors: 1, Skipped: 0
UiContractWebTest.compiledAssetsAreLocalAndContainOnlyTheSelectedIconSprite expected: <true> but was: <false>
UiContractWebTest.sharedShellRendersAuthorizedDesktopNavigationBeforeDomainPagesIntegrate: Request processing failed: Error resolving template [fragments/layout]
BUILD FAILURE
```

The asset assertion failed because the committed build artifacts did not exist, and the rendering request reached the test controller but could not resolve the missing production layout.

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=UiContractWebTest test
```

**Observed result**

```text
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 4.514 s
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
npm ci
npm run build
git diff --exit-code -- src/main/resources/static/assets/app.css src/main/resources/static/assets/icons.svg
./mvnw test

added 34 packages, audited 35 packages, found 0 vulnerabilities
Tailwind CSS v4.3.3: Done in 45ms
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 7.697 s
```

## External-test boundaries

The tests prove server-rendered authorization-aware markup and reproducible local assets. They do not replace manual browser checks for zero-flash paint timing, measured WCAG contrast, keyboard tooltip behavior, or page-level overflow at 1365×900; those remain final integrated UI gates.
