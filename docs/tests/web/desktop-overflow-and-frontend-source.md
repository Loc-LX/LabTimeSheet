# Test Evidence: desktop overflow containment and Tailwind source integrity

- **Test type:** Web
- **Requirement IDs:** `UI-003`, `UI-004`, `UI-005`, `UI-006`, `I1-UI-01`, `I1-UI-02`
- **Scenario IDs:** `AC-UI-003`, `AC-UI-005`
- **Test class/method:** `com.lab.labtimesheet.ui.FrontendSourceContractTest`
- **Implementation commit:** `pending` (see tracker row for the final SHA)

## Protected behavior

The committed Tailwind v4 source (`src/main/frontend/app.css`) must remain a valid
stylesheet that declares the shared design-token set and the desktop
page-level-overflow containment contract, and every production template that
renders a table must scroll it inside a `table-scroll` region instead of
spilling into the page. These tests catch a clobbered or non-CSS frontend source
(which breaks `npm run build` and regenerates an incorrect committed stylesheet)
and any table placed outside its bounded horizontal scroll region.

## Test method

A plain JUnit test reads the Tailwind source and the production templates from
the repository working tree (the same convention as
`ProjectTaskShellContractTest`). It asserts the source starts with the Tailwind
import, declares the `@theme` color tokens, and contains the `min-width: 64rem`
shell baseline, `overflow-x: hidden` body rule, and `max-width: 100%` /
`overflow-x: auto` scroll region. It then walks every `.html` template and
asserts any template containing `<table` also carries the `table-scroll` wrapper
class. Finally, the affected-suite build runs `npm ci && npm run build` and the
`git diff --exit-code` guard to prove the committed compiled assets are
regenerated unchanged.

## Hand-derived expected result

`src/main/frontend/app.css` is Tailwind v4 CSS (starts with
`@import "tailwindcss"`) with the 11 shared `@theme` tokens (ink, canvas,
sidebar, panel, panel-muted, border, border-strong, muted, accent, success,
warning, danger; `subtle` exists only as a root CSS variable). The desktop
layout constrains the page (`min-width: 64rem`, `body { overflow-x: hidden }`)
and lets tables scroll inside their own region
(`.table-scroll { max-width: 100%; overflow-x: auto; }`). Every one of the 9
table-bearing templates (projects list, members, leadership; tasks list,
detail; attendance calendar, history; dashboard intern; components table
fragment) is wrapped in `table-scroll`.

## RED

**Command**

```text
.\mvnw.cmd -Dtest=FrontendSourceContractTest test
```

**Observed result**

```text
[ERROR] Tests run: 12, Failures: 3, Errors: 0, Skipped: 0
[ERROR]   FrontendSourceContractTest.tailwindSourceIsValidAndDeclaresTheDesignTokenSet:23
  to start with:
    "@import "tailwindcss""
[ERROR]   FrontendSourceContractTest.desktopLayoutContainsPageLevelOverflowContainment:37
  to contain:
    "html { min-width: 64rem; }"
[ERROR]   FrontendSourceContractTest.tableContentScrollsInsideItsRegionWithoutCreatingPageLevelOverflow:46
  to contain:
    ".table-scroll"
```

The three CSS assertions failed because commit `93cd5cb` had overwritten the
Tailwind source with DEVELOPMENT.md prose. The broken source also fails the
asset build directly:

```text
npx tailwindcss -i src/main/frontend/app.css -o "$env:TEMP\broken-app.css" --minify
CssSyntaxError: D:\SWP_BL5\labtimesheet\src\main\frontend\app.css:174:45:
Unterminated string: 's web inbox shows activation and'
exit code 1
```

## GREEN

**Command**

```text
git restore --source=93cd5cb^ -- src/main/frontend/app.css
npm ci
npm run build
git diff --exit-code -- src/main/resources/static/assets/app.css src/main/resources/static/assets/icons.svg
.\mvnw.cmd -Dtest=FrontendSourceContractTest test
```

**Observed result**

```text
Tailwind CSS v4.3.3
Done in 344ms
(git diff --exit-code exits 0: compiled assets regenerated unchanged)
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
.\mvnw.cmd "-Dtest=UiContractWebTest,FrontendSourceContractTest,ProjectTaskShellContractTest,AccountTemplateIntegrationTest,AttendanceTemplateIntegrationTest,DashboardTemplateWebTest,AdminDashboardWebTest,DashboardControllerWebTest,DashboardServiceTest,SharedErrorTemplateWebTest" test
Tests run: 56, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

.\mvnw.cmd "-Dtest=RoleDashboardWebIntegrationTest,AccountWebIntegrationTest,AuthenticationWebIntegrationTest,BootstrapOnboardingWebIntegrationTest,SmtpOnboardingWebIntegrationTest" test
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

.\mvnw.cmd test
Tests run: 226, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE (single pre-existing Windows failure, see boundaries)
```

## External-test boundaries

The full Windows `mvnw test` reports one pre-existing failure in the
platform-owned `com.lab.labtimesheet.config.LayerStructureTest`: it compares
`Path.subpath(...).toString()` (backslash-separated on Windows, e.g.
`model\dto`) against the forward-slash approved list (`model/dto`). The test is
unchanged by this work, passes on the macOS baseline where the iteration-1
evidence was produced, and is tracked separately with the platform owner. This
record also does not replace browser inspection of scrollbars, focus clipping,
zoom, or keyboard tooltip positioning; it proves the committed source and
markup-level overflow contract deterministically.