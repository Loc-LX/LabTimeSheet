# Test Evidence: Daily Project Work Report XLSX/PDF exports

- **Test type:** Web
- **Requirement IDs:** `RPT-013`, `AC-RPT-005`, `RPT-001`, `RPT-006`–`RPT-010`, `AC-RPT-001`–`AC-RPT-003`
- **Scenario IDs:** `AC-RPT-005`
- **Test class/method:** `com.lab.labtimesheet.feature.reporting.service.DailyProjectWorkReportExportServiceTest`, `com.lab.labtimesheet.feature.reporting.controller.DailyProjectWorkReportExportControllerWebTest`, `com.lab.labtimesheet.feature.reporting.controller.DailyProjectWorkReportControllerWebTest`
- **Historical implementation commit:** `4870889415183380a92a03991b94afb62427a6ca6e186e4514391ec1d793f64d`
- **Content-equivalent rewritten commit:** `51bc22b6293069506570799bd128292ca1ed6a19a8cb990a29eec278260be86b`

> **Historical Q31-R scope (retained evidence):** This file preserves the I4-UI-02 XLSX/PDF parity
> evidence and its historical results; its earlier optional/global/Admin wording is superseded. The
> historical Daily contract permitted an owning Mentor to export all owned Projects when `projectId`
> is omitted or one selected owned Project when it is supplied. A current Project Leader may export HTML, XLSX,
> or PDF only with one exact mandatory `projectId` for a PLANNED or ACTIVE Project that the actor
> currently leads, entered from Project detail. For that Leader scope, a missing, guessed,
> other-Project, or completed-Project ID is denied; Admin, ordinary Intern, former Leader, and
> other-Project Leaders are denied regardless of ID. The historical 32/32 and related parser
> results below remain factual for the cases they exercised; current authorization evidence is in
> [`q31-r-daily-report.md`](q31-r-daily-report.md).

> **Latest reporting-role revision — 27 August 2026:** Admin has no dedicated Attendance,
> Project/Task, or Daily report scope, navigation, HTML/XLSX/PDF export, or report dataset; the
> Admin dashboard is account/configuration-only and has no Active Projects metric. Current-Leader
> Daily navigation is now conditionally discoverable for active Interns who currently lead one or
> more open Projects, with one-Project redirect, multi-Project selector, and no-eligible-Project
> `Project unavailable` behavior. The historical export tests above do not prove these new
> navigation or Admin Attendance/Project/Task denial requirements.

## Protected behavior

The historical authenticated Daily Project Work Report test downloads `/reports/daily.xlsx` and
`/reports/daily.pdf`. Its historical Q31-R scope let an owning Mentor omit `projectId` for all
owned Projects or provide one owned Project; a current Project Leader had to provide the exact
currently-led PLANNED/ACTIVE `projectId`. Each request constructs one authorized immutable
`DailyProjectWorkReportView`, and the exporter only renders that DTO. The attachment name is
deterministic and date-only, and unsupported roles, unauthorized Projects, future dates, and
malformed dates cannot reach byte rendering.

Both formats preserve the independently derived Project → retained author → Task hierarchy,
repeated work descriptions, current status, soft-deleted Task marker, selected-date minutes,
lifetime actual minutes, original estimate, latest Remaining Effort forecast values, DONE-only
signed variance, local day-context label, and member/Project/overall totals. XLSX numeric fields
remain numeric cells while `N/A` and `Pending` remain text; numeric DONE variance cells use the
explicit `+0;-0;0` format so positive, negative, and zero values display with their sign semantics.
The PDF uses a dedicated print-safe
Thymeleaf template with the existing embedded Liberation Sans font and keeps Vietnamese text
extractable.

## Test method

The exporter test builds one immutable Vietnamese fixture independently of the HTML renderer:
`Dự án Hà Nội` has two retained authors, repeated `Mô tả lặp` logs, an unfinished Task with
selected/lifetime/planning values `90/150/480`, a `120`-minute Remaining Effort forecast and
`270`-minute forecast total, and a soft-deleted DONE Task with `30/150/120` minutes and `+30`
DONE variance. Apache POI parses the workbook and checks hierarchy, descriptions, every subtotal,
the raw numeric DONE variance value `30`, and its `+0;-0;0` number-format contract; unavailable
planning states remain text. OpenPDF parses the dedicated PDF and checks the same facts plus
Vietnamese names/descriptions, exactly two occurrences of repeated `Mô tả lặp`, each member heading
before its matching `Member subtotal` (`90` and `30`), the Project subtotal (`120`), and the
overall total (`120`). The PDF bytes prove an embedded font marker.

Separate service tests render selected-Project empty and globally empty DTOs with the date and
`Global day off` context, asserting truthful headings, descriptions, and zero totals in both
formats. MVC tests assert visible keyboard-usable XLSX/PDF links preserve selected Project/date.
Controller tests assert one service/exporter call, safe content types/filenames, null-date
delegation for the current-business-date default, and pre-export rejection for unsupported roles,
unauthorized Projects, future dates, and malformed dates.

## Hand-derived expected result

The two sample authors contribute `90 + 30 = 120` selected-date minutes, so the member subtotals,
Project subtotal, and overall total are `90`, `30`, and `120`. The unfinished Task has lifetime
actual `150`, original estimate `480`, and forecast total `150 + 120 = 270`; its variance remains
`Pending`. The DONE Task has lifetime actual `150` against estimate `120`, hence signed DONE
variance `+30`, and no applicable forecast, hence textual `N/A`. Repeated descriptions stay as two
separate log facts, and the deleted marker is retained.

## RED

**Command**

```text
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\jbr'; & 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:/Users/dookubt/.m2/repository' '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=DailyProjectWorkReportExportServiceTest,DailyProjectWorkReportExportControllerWebTest,DailyProjectWorkReportControllerWebTest' test
```

**Observed result**

```text
After correcting the OpenPDF parser helper in the test fixture, test compilation failed with 8
cannot-find-symbol errors for the missing public ReportExportService.dailyXlsx(...) and
ReportExportService.dailyPdf(...) methods. The controller/template assertions also represented
the missing download endpoints and controls. This was the production-shaped public-seam RED;
the accepted baseline failures were not used as feature RED evidence.
```

## GREEN

**Command**

```text
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\jbr'; & 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:/Users/dookubt/.m2/repository' '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=DailyProjectWorkReportExportServiceTest,DailyProjectWorkReportExportControllerWebTest,DailyProjectWorkReportControllerWebTest,ReportExportServiceTest,ReportingExportControllerWebTest' test
```

**Observed result**

```text
Tests run: 32, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

The Daily export tests cover 4 byte-level service cases, 6 controller cases, and 5 HTML cases;
the existing 8 ReportExportService and 9 ReportingExportController tests also remain green.
```

## Follow-up parity corrections

The signed-variance RED added a public Apache POI assertion for the existing numeric DONE value:
the raw value was `30`, but its format was `General` rather than the required `+0;-0;0`. The
implementation now applies that explicit format only to numeric variance cells, preserving the
numeric cell type while allowing Excel to display `+30`, `-30`, or `0` according to the value.
The focused 32-test GREEN result below includes this assertion.

Signed-variance RED command:

```text
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\jbr'; & 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:/Users/dookubt/.m2/repository' '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=DailyProjectWorkReportExportServiceTest#dailyXlsxPreservesIndependentHierarchyFactsAndNumericCells' test
```

The first sandbox attempt was blocked during javac cleanup by an external Maven-cache access
denial; the same command then reached the intended assertion under elevated test access:
`Tests run: 1, Failures: 1, Errors: 0`, expected `+0;-0;0` but observed `General`.

The PDF parity assertion was strengthened at the public parsed-text seam. Its final form counts
the repeated description exactly twice, verifies each named member heading precedes its matching
`Member subtotal` value (`90` and `30`), and verifies `Project subtotal: 120 minutes` and
`Overall total minutes 120`; it does not rely on loose unrelated number presence. The corrected
PDF parser regression passed 1/1.

Corrected PDF parser command and result:

```text
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\jbr'; & 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:/Users/dookubt/.m2/repository' '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=DailyProjectWorkReportExportServiceTest#dailyPdfPreservesVietnameseHierarchyAndPlanningFactsAsExtractableText' test
```

`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS`.

## Presentation-contract correction

The frozen full-suite check exposed one packet-induced source-contract failure because the new
dedicated print template contained tables without the repository's required `table-scroll`
region. The focused RED was:

```text
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\jbr'; & 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:/Users/dookubt/.m2/repository' '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=FrontendSourceContractTest#everyTemplateTableIsWrappedInAScrollRegion' test
```

Before the correction this ran 25 tests with two failures: the new `reports/daily-print.html`
parameter `[19]` and the pre-existing `reports/print.html` parameter `[21]`. The Daily print
tables are now wrapped in `table-scroll`, with `overflow: visible` in the dedicated print CSS so
the wrapper cannot clip PDF output. The corrected PDF behavior was rerun through the public
export seam:

```text
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\jbr'; & 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:/Users/dookubt/.m2/repository' '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=DailyProjectWorkReportExportServiceTest#dailyPdfPreservesVietnameseHierarchyAndPlanningFactsAsExtractableText' test
```

Result: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS`. A post-correction
source-contract run reported 25 tests with one failure only, the accepted pre-existing
`reports/print.html` `[21]` case.

## Affected suite

**Command and result**

```text
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\jbr'; & 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:/Users/dookubt/.m2/repository' '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=ProjectServiceIntegrationTest,AttendanceApplicationServiceTest,TaskCreationIntegrationTest#dailyReportReadsPostgresRetainedDeletedLogsAndLatestForecastSnapshot,TaskQueryServiceTest,DailyProjectWorkReportServiceTest,DailyProjectWorkReportControllerWebTest,DailyProjectWorkReportExportControllerWebTest,DailyProjectWorkReportExportServiceTest,ProjectTaskReportServiceTest,ProjectTaskReportControllerWebTest,AttendanceReportServiceTest,AttendanceReportControllerWebTest,ReportingExportControllerWebTest,ReportExportServiceTest,ReportingArchitectureTest' test
```

```text
Tests run: 77, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

The gate includes the PostgreSQL 18.4/Flyway v2 retained Daily dataset proof, producer/service
queries, Project/Task/Attendance/reporting regressions, all Daily HTML/export seams, existing
export services/controllers, and ReportingArchitectureTest.
```

## Full-suite comparison

The one required broad Maven run was executed on the frozen packet:

```text
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\jbr'; & 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:/Users/dookubt/.m2/repository' '-Duser.timezone=Asia/Ho_Chi_Minh' test
```

Observed result: `Tests run: 696, Failures: 6, Errors: 21, Skipped: 0; BUILD FAILURE`.
The authorized independent baseline is 684 tests, 5 failures, and 21 errors. All 21 errors
matched baseline families (`ProjectQueryIndexIntegrationTest` 1, `ProjectLifecycleLockIntegrationTest`
9, `AttendanceReportPageWebTest` 5, `Iteration2WorkflowFragmentsWebTest` 1,
`ProjectTaskReportPageWebTest` 4, `RoleDashboardWebIntegrationTest` 1). Baseline failures were
`LayerStructureTest` 1, `ProjectTaskShellContractTest` 2, and `FrontendSourceContractTest` 2.
The sixth failure was the packet-induced Daily print `[19]` source-contract case described above;
it was removed by the isolated wrapper correction. The full suite was not rerun after that
correction, so 696/6/21 is the observed broad result and the targeted post-correction checks are
the final evidence for the packet fix.

## External-test boundaries

The exporter tests prove parsed workbook/PDF content and public MVC delegation, not browser visual
layout or a live HTTP download through a deployed server. The PostgreSQL producer/persistence
semantics are covered by the existing `TaskCreationIntegrationTest` evidence under
`docs/tests/integration/daily-project-work-report.md`; this packet intentionally adds no duplicate
database model or repository access in reporting. Frontend source/build, Javadoc/doclint,
architecture, and the broad-suite baseline comparison are recorded above and in the coordination
report. Generated frontend outputs were restored and are excluded from the packet.
