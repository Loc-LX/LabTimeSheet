# Test Evidence: Iteration 3 shared report exports

- **Test type:** Web
- **Requirement IDs:** `I3-UI-01`, `I3-UI-02`, `RPT-001`, `RPT-006`–`RPT-010`
- **Scenario IDs:** `AC-RPT-001`, `AC-RPT-002`, `AC-RPT-003`, `AC-TST-001`
- **Test class/method:** `com.lab.labtimesheet.feature.reporting.controller.ReportingExportControllerWebTest#attendanceXlsxDownloadUsesAttachmentAndWorkbookContentType`; `#projectTaskPdfDownloadUsesAttachmentAndPdfContentType`; `#rejectsInvalidProjectTaskExportRangesBeforeDatasetConstruction`
- **Implementation commit:** `639d02e404cbd551ffd70def15021a1d6beb8cf9` (bounded export production fix)

## Protected behavior

Authorized attendance and Project/Task report datasets have downloadable XLSX and PDF routes. Each response is an attachment with a deterministic, path-safe filename and the media type for its format; the export controller obtains the same report DTO used by HTML.

## Test method

The MVC slice authenticates an Intern for attendance and a Mentor for Project/Task, then requests one download route for each format. The report services and byte exporter are test doubles at this response-contract boundary. The merged Platform dependency pin is consumed by the focused exporter tests. A shared-dataset test now checks the same totals in print HTML, parsed XLSX summary cells, and PDF text extraction; the PDF test also checks Vietnamese text and an embedded `/FontFile` marker.

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

## GREEN

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest=ReportingExportControllerWebTest test
```

**Observed result**

```text
Merged Platform dependency tree: BUILD SUCCESS for 13/13 focused tests (9 bounded-route cases plus 4 exporter cases, including the actual Thymeleaf print template) with POI 5.5.1 and OpenPDF HTML/fonts-extra 3.0.3. The additional parity/font extraction tests pass individually and are recorded in `2a26ceb661c065e5a7908a0b76603df967f36d33`; managed-browser results remain unclaimed.
```

## Affected suite

**Command and result**

```text
Merged-tree regression slice: `./mvnw -Dtest=AttendanceReportControllerWebTest,ProjectTaskReportControllerWebTest,AttendanceTemplateIntegrationTest,ProjectTaskShellContractTest test` passed 17/17 after the bounded-link template guard. The focused exporter route/service suite passed 13/13; the parity/font extraction tests are in `ReportExportServiceTest`.
```

### Boundary-fix RED/GREEN

The new parameterized MVC cases first failed because null and half-open Project/Task ranges returned 200 and reached the mocked report service, while reversed and overlong ranges escaped as uncaught `IllegalArgumentException`. After the controller guard was added, the same test passed 9/9 and verified both XLSX/PDF routes return 400 without calling `ProjectTaskReportService`.

## External-test boundaries

This evidence does not prove PostgreSQL report authorization or live browser download behavior. The credential-gated Playwright journey is implemented but remains unexecuted because the local Java service could not start without `LAB_SMTP_HOST` and `LAB_DB_*` configuration.
