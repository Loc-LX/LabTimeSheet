# Test Evidence: dark icon sprite presentation

- **Test type:** Web
- **Requirement IDs:** `UI-006`, `UI-009`, `UI-010`, `UI-018`
- **Scenario IDs:** `AC-UI-003`, `AC-UI-005`
- **Test class/method:** `com.lab.labtimesheet.ui.UiContractWebTest#generatedLucideSymbolsRetainCurrentColorStrokePresentation`
- **Implementation commit:** `pending`

## Protected behavior

Every local Lucide sprite symbol retains the source presentation attributes so icons referenced with `<use>` inherit `currentColor` rather than rendering with the SVG default black fill on dark surfaces.

## Test method

The focused web contract reads the generated classpath sprite, scans every emitted `<symbol>`, and checks the five presentation attributes on each symbol. It checks the deployable generated artifact rather than generator source text.

## Hand-derived expected result

Lucide 1.27.0 line icons use `fill="none"`, `stroke="currentColor"`, `stroke-width="2"`, `stroke-linecap="round"`, and `stroke-linejoin="round"` on their SVG root. Each selected generated symbol must preserve those values.

## RED

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw '-Dtest=UiContractWebTest#generatedLucideSymbolsRetainCurrentColorStrokePresentation' test
```

**Observed result**

```text
UiContractWebTest.generatedLucideSymbolsRetainCurrentColorStrokePresentation
Missing fill on  id="bell" viewBox="0 0 24 24" ==> expected: <true> but was: <false>
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE
```

## GREEN

**Command**

```text
env PATH=/opt/homebrew/opt/node@24/bin:$PATH npm ci
env PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run build
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw '-Dtest=UiContractWebTest#generatedLucideSymbolsRetainCurrentColorStrokePresentation' test
```

**Observed result**

```text
Node v24.19.0 and npm 11.17.0 installed the locked dependencies.
Tailwind CSS v4.3.3 rebuilt app.css and build-icons regenerated icons.svg.
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw '-Dtest=UiContractWebTest' test

Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

The deterministic asset contract proves the generated sprite carries theme-aware Lucide presentation attributes. It does not replace the taskmaster-owned integrated browser/detector pass for rendered layout and interactive states.
