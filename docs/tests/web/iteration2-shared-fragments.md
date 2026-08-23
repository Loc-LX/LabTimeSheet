# Test Evidence: Iteration 2 shared accessible fragments

- **Test type:** Web
- **Requirement IDs:** `UI-001`, `UI-002`, `UI-005`, `UI-013`, `UI-019`, `RPT-008`, `RPT-009`
- **Scenario IDs:** `AC-UI-001`, `AC-UI-004`, `AC-UI-009`, `AC-RPT-004`
- **Test class/method:** `com.lab.labtimesheet.feature.reporting.controller.Iteration2ComponentsWebTest#sharedIteration2FragmentsExposeKeyboardSafeErrorsDrawersAndChartAlternative`
- **Implementation commit:** `49c9075aa6c33656201acc5c654e243d830028eb`

## Protected behavior

Shared server-rendered fragments provide an error summary that links to invalid controls, a named keyboard-operable drawer, and a chart canvas with an adjacent equivalent tabular data alternative. The missing baseline behavior is that these cross-product accessibility contracts are not available from the existing fragments.

## Test method

The test controller supplies representative field issues and two hand-derived chart points, then renders the production `fragments/components` template through MockMvc and Thymeleaf. Assertions verify the summary field anchor, drawer labels/close hook, chart accessible name, and exact table values.

## Hand-derived expected result

The chart contains exactly two rows: `08/08/2026` at `92.0%` and `09/08/2026` at `100.0%`. The first field issue links to `#displayName`; the form-level issue has no fabricated field target.

## RED

**Command**

```text
./mvnw '-Dtest=Iteration2ComponentsWebTest' test
```

**Observed result**

```text
The focused test reached Thymeleaf and failed with `TemplateInputException: Error resolving fragment: fragments/components :: errorSummary`; the baseline fragments did not define the Iteration 2 error-summary, drawer, or report-chart contracts.
```

## GREEN

**Command**

```text
./mvnw '-Dtest=Iteration2ComponentsWebTest' test
```

**Observed result**

```text
`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS` (Java 25/PostgreSQL-independent MVC slice).
```

## Affected suite

**Command and result**

```text
./mvnw '-Dtest=*Reporting*Test,*Dashboard*Test,*Template*Test,*Shell*Test,*Accessibility*Test,*UiContractWebTest' test

`Tests run: 51, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS` on Java 25 with PostgreSQL 18.4 Testcontainers where required.
```

## External-test boundaries

This test does not prove producer authorization, report calculations, Chart.js runtime rendering, real keyboard interaction in a browser, or cross-module exit/transfer behavior. Those remain dependent on the producer service/DTO contracts and browser evidence.
