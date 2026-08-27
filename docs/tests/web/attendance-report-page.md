# Test Evidence: Attendance report page

- **Test type:** Web (`@WebMvcTest`)
- **Requirement IDs:** `UI-011`, `UI-012`, `UI-017`, `RPT-001`, `RPT-009`
- **Scenario IDs:** `AC-UI-004`, `AC-UI-005`
- **Test class/method:** `src/test/java/.../reporting/controller/AttendanceReportPageWebTest.java` (five cases)
- **Implementation commit:** pending

> **Supersession notice — 27 August 2026:** This is historical page evidence. Its
> `Mentor/Admin inspection` wording records the pre-correction contract and is not current
> authorization. The latest RPT-004 decision removes Admin from Attendance report navigation,
> HTML, XLSX, PDF, service, and dataset scope; active Intern own-history and active Mentor
> detailed-Intern behavior remain. The historical fixtures and results below are retained without
> rewriting them as evidence of the new Admin denial, which requires a separate route/service
> guard test before target enumeration and export.

## Protected behavior

The `GET /reports/attendance` page must render the classified period with shared totals (rate and
period compliance as `N/A` when the denominator is zero), scope the target Intern to the actor,
apply Mentor/Admin inspection only, default the period to the current business-date month, and emit a
meaningful daily-compliance chart alongside the day table as its text alternative (UI-011, UI-012,
UI-017, RPT-001, RPT-009).

## Test method

`@WebMvcTest(AttendanceReportController.class)` with the real `AttendanceReportService` imported
(`@Import`) so the page runs the actual formulas, and `@MockitoBean` for the dataset boundary
(`AttendanceReportDataProvider`), the attendance current-user and application services, and SMTP
status. Each request uses the MockMvc security test user post-processor for the role. The
`AttendanceReportDataProvider` is resolved through an `ObjectProvider`; when no provider bean exists
the page must return `404` instead of fabricating rows (integration gate for the Attendance branch).

## Hand-derived expected results

- Fixture A (Intern 5, 2026-08-03..07: 3 present, 1 absent, 1 leave): expected workdays 4,
  present 3, absent 1, leave 1; rate 3/4 = `0.75` → `75.00%`; compliance
  (1.0 + 0.75 + 0.75)/4 = `0.625` → `62.50%`; daily scores 1.0 / 0.75 / 0.75; chart labels
  2026-08-03..07.
- Fixture B (Mentor inspecting Intern 7, 2 present with late+early departure, 3 off-days):
  rate 2/2 = `1.0` → `100.00%`; compliance (0.5 + 1.0)/2 = `0.75` → `75.00%`; day table shows the
  violation badges; no Intern punch actions.
- Fixture C (only off-days): expected workdays 0 → rate and compliance render `N/A`.
- Intern requesting another intern's id → `403 Forbidden`, provider never invoked.
- No filter params → defaults to first-of-month through the attendance business date.

## RED

**Command**

```text
mvnw test -Dtest=AttendanceReportPageWebTest,ProjectTaskReportPageWebTest
```

**Observed result**

```text
COMPILATION ERROR
cannot find symbol: class AttendanceReportDataProvider
cannot find symbol: class AttendanceReportController
```

The tests fail to compile because no report page or dataset boundary existed.

## GREEN

**Command**

```text
mvnw test -Dtest=AttendanceReportPageWebTest,ProjectTaskReportPageWebTest
```

**Observed result**

```text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- AttendanceReportPageWebTest
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
mvnw test "-Dtest=com.lab.labtimesheet.feature.reporting.**,com.lab.labtimesheet.architecture.**,com.lab.labtimesheet.ui.**"
Tests run: 83, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Includes the `@SpringBootTest` reporting integration tests (full context boots with the
`ObjectProvider` integration gate) and the template source contract covering the new table pages.

## External-test boundaries

This test proves the server-rendered page contract (scope, totals, `N/A`, filters, chart JSON)
against the mocked dataset boundary. It does not verify the Attendance feature's classification feed
(leave/off-day/eligibility queries) — that is the blocked domain contract the Attendance branch will
implement behind `AttendanceReportDataProvider`. Until that bean is registered, the production page
returns `404 Not Found` rather than stub data.
