# Test Evidence: Iteration 3 shared report exports

- **Test type:** Web
- **Requirement IDs:** `I3-UI-01`, `I3-UI-02`, `RPT-001`, `RPT-006`–`RPT-010`
- **Scenario IDs:** `AC-RPT-001`, `AC-RPT-002`, `AC-RPT-003`, `AC-TST-001`
- **Test class/method:** `com.lab.labtimesheet.feature.reporting.controller.ReportingExportControllerWebTest#attendanceXlsxDownloadUsesAttachmentAndWorkbookContentType`; `#projectTaskPdfDownloadUsesAttachmentAndPdfContentType`; `#rejectsInvalidProjectTaskExportRangesBeforeDatasetConstruction`
- **Implementation commit:** `639d02e404cbd551ffd70def15021a1d6beb8cf9`

## Protected behavior

Authorized attendance and Project/Task report datasets have downloadable XLSX and PDF routes. Each response is an attachment with a deterministic, path-safe filename and the media type for its format; the export controller obtains the same report DTO used by HTML.

## Test method

The MVC slice authenticates an Intern for attendance and a Mentor for Project/Task, then requests one download route for each format. The report services and byte exporter are test doubles at this response-contract boundary; workbook/PDF byte parity is covered by the focused exporter test after the Platform dependencies are delivered.

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
Temporary local dependency probe: BUILD SUCCESS for compile and 13/13 focused tests (9 bounded-route cases plus 4 exporter cases, including the actual Thymeleaf print template) with the exact requested artifacts. The pom edit was removed immediately because Maven/dependency configuration belongs to Platform.
```

## Affected suite

**Command and result**

```text
Temporary exact-dependency regression slice: `./mvnw -Dtest=AttendanceReportControllerWebTest,ProjectTaskReportControllerWebTest,AttendanceTemplateIntegrationTest,ProjectTaskShellContractTest test` passed 17/17 after the bounded-link template guard. The final reports/UI Java affected suite remains pending the reviewed Platform dependency delivery; temporary evidence is not a final branch gate.
```

### Boundary-fix RED/GREEN

The new parameterized MVC cases first failed because null and half-open Project/Task ranges returned 200 and reached the mocked report service, while reversed and overlong ranges escaped as uncaught `IllegalArgumentException`. After the controller guard was added, the same test passed 9/9 and verified both XLSX/PDF routes return 400 without calling `ProjectTaskReportService`.

## External-test boundaries

This web slice does not prove workbook cell parsing, PDF text/font extraction, PostgreSQL report authorization, or browser download behavior. Those checks remain required after the reviewed Platform dependency pin is consumed.
