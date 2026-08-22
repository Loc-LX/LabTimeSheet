# Test Evidence: Iteration 3 shared report exports

- **Test type:** Web
- **Requirement IDs:** `I3-UI-01`, `I3-UI-02`, `RPT-001`, `RPT-006`–`RPT-010`
- **Scenario IDs:** `AC-RPT-001`, `AC-RPT-002`, `AC-RPT-003`, `AC-TST-001`
- **Test class/method:** `com.lab.labtimesheet.feature.reporting.controller.ReportingExportControllerWebTest#attendanceXlsxDownloadUsesAttachmentAndWorkbookContentType`; `#projectTaskPdfDownloadUsesAttachmentAndPdfContentType`; `#rejectsInvalidProjectTaskExportRangesBeforeDatasetConstruction`; `com.lab.labtimesheet.feature.reporting.service.ReportExportServiceTest#nonEmptyVietnameseProjectTaskDatasetKeepsActualHtmlWorkbookAndPdfParity`
- **Implementation commit:** `f0506492857288927f91e020aeba2fa0bb8550e8` (cross-format filter and metric parity fix)

## Protected behavior

Authorized attendance and Project/Task report datasets have downloadable XLSX and PDF routes. Each response is an attachment with a deterministic, path-safe filename and the media type for its format; the export controller obtains the same report DTO used by HTML.

## Test method

The MVC slice authenticates an Intern for attendance and a Mentor for Project/Task, then requests one download route for each format. The report services and byte exporter are test doubles at this response-contract boundary. The merged Platform dependency pin is consumed by the focused exporter tests. A shared-dataset test renders the actual `reports/project-tasks` application template and checks the same non-empty Vietnamese project/task, selected Project/member/status/due/work filters, status counts, completion, distinct member/total/task-row minutes (60/90/45), and `N/A` values in print HTML, parsed XLSX cells, and extracted PDF text; the PDF test also checks Vietnamese text and an embedded `/FontFile` marker.

## Hand-derived expected result

XLSX must use `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`; PDF must use `application/pdf`. Both responses must be HTTP 200 with `Content-Disposition: attachment`.

## RED

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest=ReportingExportControllerWebTest test
```

**Observed result**

```text
FAIL: /reports/attendance.xlsx and /reports/project-tasks.pdf returned 404 because no export mappings existed.
```

The first unsandboxed run was not counted as RED because Mockito could not self-attach under the sandbox; the host-permitted rerun reached the expected missing-route failures.

The new parity test first failed in the host-permitted focused run because the actual application template requires a servlet-aware Thymeleaf `WebContext`; a plain context could not resolve the layout's context-relative asset link. The test now uses the servlet-aware template context required by the application.

The round-7 parity assertions then failed in the host-permitted focused run because the XLSX filter row contained only Project/status, the PDF had no filter block, and the HTML assertion matched static help text rather than the Completion metric. The first failing assertion was `ReportExportServiceTest.java:222`, expected XLSX `Member` but observed `Status`.

## GREEN

**Command**

```text
rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=ReportExportServiceTest test
```

**Observed result**

```text
RED: Tests run: 7, Failures: 1. `nonEmptyVietnameseProjectTaskDatasetKeepsActualHtmlWorkbookAndPdfParity` failed at line 222: expected XLSX cell `Member`, observed `Status`, proving the production XLSX/PDF parity fields were absent rather than an invalid fixture.
```

**Round-7 GREEN command and result**

```text
rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=ReportExportServiceTest,ReportingExportControllerWebTest test
BUILD SUCCESS; Tests run: 16, Failures: 0, Errors: 0, Skipped: 0 (7 exporter-service and 9 bounded-controller cases). The shared dataset now proves all selected filters and distinct 60-minute member, 90-minute total, and 45-minute Task-row values in HTML/XLSX/PDF, with the empty HTML assertion scoped to `data-report-metric="completion"`.
```

## Affected suite

**Command and result**

```text
Merged-tree reporting/template regression selection with the Java 25 Byte Buddy agent: `-Dtest=ReportExportServiceTest,ReportingExportControllerWebTest,AttendanceReportControllerWebTest,ProjectTaskReportControllerWebTest,AttendanceTemplateIntegrationTest,ProjectTaskShellContractTest` passed 35/35 (7 exporter-service, 9 export-controller, 2 attendance-report-controller, 2 project-task-report-controller, 5 attendance-template, 10 shell-contract).
```

### Boundary-fix RED/GREEN

The new parameterized MVC cases first failed because null and half-open Project/Task ranges returned 200 and reached the mocked report service, while reversed and overlong ranges escaped as uncaught `IllegalArgumentException`. After the controller guard was added, the same test passed 9/9 and verified both XLSX/PDF routes return 400 without calling `ProjectTaskReportService`.

## External-test boundaries

This evidence does not prove PostgreSQL report authorization. The managed-browser report-download rerun is required after this exporter/template change; its command/result will be recorded in the E2E document before final review. The previously reviewed advancing-clock critical/full E2E remains historical evidence for the unchanged workflow.
