# Test Evidence: Iteration 3 theme, accessibility, and narrow-screen contracts

- **Test type:** Web
- **Requirement IDs:** `I3-UI-03`, `I3-UI-04`, `I3-UI-06`, `UI-002`, `UI-007`, `UI-010`–`UI-015`, `UI-018`
- **Scenario IDs:** `AC-UI-001`, `AC-UI-002`, `AC-UI-003`, `AC-UI-004`, `AC-UI-005`, `AC-TST-001`
- **Test class/method:** `src/test/js/narrow-screen-contract.test.mjs#desktop shell includes a narrow-screen overflow safeguard`; existing `src/test/js/chart-contract.test.mjs`
- **Implementation commit:** pending

## Protected behavior

The shared shell keeps the desktop information hierarchy while allowing a best-effort narrow viewport: the page no longer imposes a global 64rem minimum, data tables retain deliberate local scrolling, and compact controls wrap rather than corrupt the viewport. Existing pre-paint theme selection and accessible chart alternatives remain in the shared assets.

## Test method

The Node contract reads the source asset that Tailwind compiles and requires the narrow-screen media boundary plus the local table overflow safeguard. Existing chart and workflow contracts continue to exercise accessible names, text/table alternatives, and reduced-motion-safe chart behavior.

## Hand-derived expected result

At widths below 64rem, the shell uses the icon rail and one-column form fallback; at widths below 40rem, metrics and download/form actions stack. The global document can shrink below desktop width while each wide data table remains independently scrollable.

## RED

**Command**

```text
npm run test:ui -- --test-name-pattern='Playwright harness|narrow-screen'
```

**Observed result**

```text
FAIL: no @media (max-width: 64rem) contract existed; the source still declared html { min-width: 64rem; }.
```

## GREEN

**Command and result**

```text
npm run test:ui -- --test-name-pattern='Playwright harness|narrow-screen'
9 passed, 0 failed
```

## Affected suite

```text
Pending final Node 24/npm 11 build and browser smoke on the integrated tree.
```

## External-test boundaries

The source contract does not measure actual browser contrast ratios, layout screenshots, or every role page. The Playwright narrow smoke and representative desktop accessibility journeys remain the final browser evidence.
