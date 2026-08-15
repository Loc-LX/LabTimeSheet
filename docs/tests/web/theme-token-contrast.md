# Test Evidence: theme token contrast

- **Test type:** Web
- **Requirement IDs:** `UI-005`, `UI-006`, `UI-010`, `UI-018`, `I1-UI-02`
- **Scenario IDs:** `AC-UI-003`, `AC-UI-005`
- **Test class/method:** `com.lab.labtimesheet.ui.UiContractWebTest#themeTokensMeetTextFocusAndMeaningfulBoundaryContrast`
- **Implementation commit:** `3343745`

## Protected behavior

The committed light and dark CSS tokens provide at least 4.5:1 contrast for normal text and 3:1 for focus indicators and meaningful panel/control boundaries against their adjacent surfaces.

## Test method

The web test reads the generated classpath CSS, extracts the production light and dark custom-property values, converts sRGB colors to relative luminance, and checks WCAG contrast ratios for ink, muted/subtle text, neutral boundaries, and focus tokens.

## Hand-derived expected result

Both themes must keep normal text at or above 4.5:1. Borders and focus tokens must be at or above 3:1 against the panel, sidebar, or canvas on which they are used.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=UiContractWebTest#themeTokensMeetTextFocusAndMeaningfulBoundaryContrast test
```

**Observed result**

```text
border / canvas contrast 1.2206621853850066 is below 3.0
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
npm run build
./mvnw -Dtest=UiContractWebTest#themeTokensMeetTextFocusAndMeaningfulBoundaryContrast test
```

**Observed result**

```text
Tailwind CSS v4.3.3: Done in 73ms
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 26.385 s
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=UiContractWebTest,DashboardTemplateWebTest,ReportingArchitectureTest,LayerStructureTest test

Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 27.072 s
```

## External-test boundaries

This deterministic check proves the declared theme-token ratios used by the shared shell. It does not replace browser inspection for antialiasing, authored colors outside the token set, image contrast, zoom, high-contrast modes, or viewport-specific focus clipping.
