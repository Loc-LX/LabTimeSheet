# Test Evidence: Iteration 2 Project/Task report filtering

- **Test type:** Unit
- **Requirement IDs:** `I2-UI-04`, `RPT-008`, `RPT-009`
- **Scenario IDs:** `AC-RPT-004`, `AC-RPT-005`
- **Test class/method:** `com.lab.labtimesheet.feature.reporting.service.ProjectTaskReportServiceTest#filtersAuthorizedCurrentTasksByMemberStatusAndDueDate`
- **Implementation commit:** `6c869b65c99903d8b0ac0db19a3c10b06c920e01`

## Protected behavior

Reporting filters only the current Task DTOs returned by the authorized Task service, retaining Project/member/status/date visibility boundaries and explicit `N/A` progress for an empty result.

## Test method

The unit test supplies visible Project and current Task DTOs through public service mocks, applies member/status/due-date bounds, and asserts that only the hand-selected DONE Task remains. A second test verifies the project-picker state does not query Tasks before a Project is selected.

## Hand-derived expected result

The selected member/status/date window contains exactly `Finish report`, yielding one Task, one DONE Task, and `100.0%` completion; the undated Task is excluded when a due-date bound is active.

## RED

**Command**

```text
./mvnw '-Dtest=ProjectTaskReportControllerWebTest' test
```

**Observed result**

```text
Compilation failed because ProjectTaskReportController and ProjectTaskReportService were absent from the baseline.
```

## GREEN

**Command**

```text
./mvnw '-Dtest=ProjectTaskReportControllerWebTest,ProjectTaskReportServiceTest' test
```

**Observed result**

```text
`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS`.
```

## Affected suite

**Command and result**

```text
./mvnw '-Dtest=*Reporting*Test,*Dashboard*Test,*Template*Test,*Shell*Test,*Accessibility*Test,*UiContractWebTest,*AttendanceReport*Test,*ProjectTaskReport*Test' test

`Tests run: 60, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS` on Java 25 with PostgreSQL 18.4 Testcontainers.
```

## External-test boundaries

The unit test does not prove producer PostgreSQL queries, cross-module historical Task rows, transfer actions, browser keyboard/focus behavior, or future producer filter DTOs.
