# Test Evidence: Attendance report page

- **Test type:** Web (`@WebMvcTest`)
- **Requirement IDs:** `UI-011`, `UI-012`, `UI-017`, `RPT-001`, `RPT-009`
- **Scenario IDs:** `AC-UI-004`, `AC-UI-005`
- **Test class/method:** `src/test/java/.../reporting/controller/AttendanceReportPageWebTest.java` (five cases)
- **Implementation commit:** pending

> **Reporting-role correction — 29 August 2026:** Active Admin detailed-Intern Attendance scope,
> navigation, HTML, XLSX, PDF, service, and dataset access are restored. The 27 August Admin
> Attendance removal was accidental. Intern own-history and active Mentor behavior remain, while
> Admin Project/Task and Daily report scope remains denied.

## Protected behavior

The `GET /reports/attendance` page must render the classified period with shared totals (rate and
period compliance as `N/A` when the denominator is zero), scope the target Intern to the actor,
apply Mentor/Admin inspection only, default the period to the current business-date month, and emit a
meaningful daily-compliance chart alongside the day table as its text alternative (UI-011, UI-012,
UI-017, RPT-001, RPT-009).

## Test method

`@WebMvcTest(AttendanceReportController.class)` with the real `AttendanceReportService` imported
(`@Import`) so the page runs the actual target checks, date defaults, DTO-to-row mapping, aggregate
display, and trend mapping. The MVC slice mocks the Attendance current-user/application services,
the Attendance-owned `AttendanceReportQueryService`, `AccountService`, and SMTP status. Each
request uses the MockMvc security test user post-processor for its role. The query and account
mocks are public feature seams: the service asks the query boundary for one immutable
`AttendanceReport`, then reloads the target identity and formats it for Thymeleaf; it does not
mock the service or an obsolete reporting-owned provider.

## Hand-derived expected results

- Fixture A (Intern 5, 2026-08-03..07: 3 present, 1 absent, 1 leave): expected workdays 4,
  present 3, absent 1, leave 1; rate 3/4 = `0.75` → `75.00%`; compliance
  (1.0 + 0.75 + 0.75)/4 = `0.625` → `62.50%`; daily scores 1.0 / 0.75 / 0.75; chart labels
  2026-08-03..07.
- Fixture B (Mentor inspecting Intern 7, 2 present with late+early departure, 3 off-days):
  rate 2/2 = `1.0` → `100.00%`; compliance (0.5 + 1.0)/2 = `0.75` → `75.00%`; day table shows the
  violation badges; no Intern punch actions.
- Fixture C (only off-days): expected workdays 0 → rate and compliance render `N/A`.
- Intern requesting another intern's id → `403 Forbidden`, Attendance query never invoked.
- No filter params → defaults to first-of-month through the attendance business date.

## Current 29 August 2026 verification

The current page slice passed after aligning its MVC mocks and fixtures with the real imported
`AttendanceReportService`:

```powershell
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\jbr'; & 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:/Users/dookubt/.m2/repository' '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=AttendanceReportPageWebTest' test
```

```text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The full-context Admin dashboard/security slice also passed. It proves the empty Admin selection
renders XLSX/PDF links without `internId=0`, both omitted-target export URLs return `200`, mixed
`ADMIN`+`INTERN` malformed Project/Task HTML/XLSX/PDF requests return `403` before report/export
interactions, and the combined-role dashboard shows Attendance while hiding Project/Task and Daily
navigation:

```powershell
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\jbr'; & 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:/Users/dookubt/.m2/repository' '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=AdminDashboardWebTest' test
```

```text
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The direct query-service identity-gate slice passed both new negative cases: a persisted
non-ACTIVE Admin and an Admin actor whose persisted immutable role is different are denied before
target identity resolution and before Attendance, correction, policy, leave, or calendar reads:

```powershell
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\jbr'; & 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:/Users/dookubt/.m2/repository' '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=AttendanceReportQueryServiceAuthorizationTest' test
```

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The complete 29 August scoped command and final aggregate result are recorded after the final
verification run. The selected classes include this page slice, the Admin dashboard/security
slice, the new query-service identity-gate slice, Attendance persistence/query tests, Attendance
HTML/export/controller seams, and the retained negative Project/Task and Daily guards.

## Historical 27 August 2026 provider-era evidence

The following RED/GREEN, affected-suite, and external-boundary record is retained verbatim as
historical evidence. Its old provider/ObjectProvider seam and result counts describe the earlier
implementation and must not be read as the current production method.

### RED

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

### GREEN

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

### Affected suite

**Command and result**

```text
mvnw test "-Dtest=com.lab.labtimesheet.feature.reporting.**,com.lab.labtimesheet.architecture.**,com.lab.labtimesheet.ui.**"
Tests run: 83, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Includes the `@SpringBootTest` reporting integration tests (full context boots with the
`ObjectProvider` integration gate) and the template source contract covering the new table pages.

### External-test boundaries

This test proves the server-rendered page contract (scope, totals, `N/A`, filters, chart JSON)
against the mocked dataset boundary. It does not verify the Attendance feature's classification feed
(leave/off-day/eligibility queries) — that is the blocked domain contract the Attendance branch will
implement behind `AttendanceReportDataProvider`. Until that bean is registered, the production page
returns `404 Not Found` rather than stub data.
