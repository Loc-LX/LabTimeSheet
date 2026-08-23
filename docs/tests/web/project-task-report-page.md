# Test Evidence: Project/Task report page

- **Test type:** Web (`@WebMvcTest`)
- **Requirement IDs:** `UI-011`, `UI-017`, `RPT-001`, `RPT-005`, `RPT-009`
- **Test class/method:** `src/test/java/.../reporting/controller/ProjectTaskReportPageWebTest.java` (four cases)
- **Implementation commit:** pending

## Protected behavior

The `GET /reports/projects?projectId=..` page must render aggregate progress and logged hours, apply
per-member detail only to the Admin, owning Mentor, or current Leader (AUTH-010, RPT-005), render
`N/A` for completion when the Project has no current Tasks, and emit the per-member hour chart only
when the viewer is authorized for detail (never for ordinary members, UI-011).

## Test method

`@WebMvcTest(ProjectTaskReportController.class)` with the real `ProjectTaskReportService` imported
(`@Import`) so the page runs the actual aggregation, and `@MockitoBean` for the dataset boundary
(`ProjectTaskReportDataProvider`), the Account service (viewer identity), the Attendance application
service (business-date default), and SMTP status. The provider is resolved through an `ObjectProvider`;
when absent the page must return `404` instead of fabricating rows (integration gate for the Project
and Task branches).

## Hand-derived expected results

- Admin fixture (project 10 "Titan", tasks TODO 0 / IN_PROGRESS 120 / BLOCKED 60 / DONE 180 min;
  members Alice 180 min / Bob 180 min): progress 1/1/1/1, completion 25% → `25.00%`, total 360 min
  → `6.00` hours, per-member `3.00` hours each, bar chart with labels Alice/Bob.
- Intern fixture (2 DONE tasks, no members returned): completion 100% → `100.00%`, no member rows,
  no chart, no "Member hours" heading.
- Leader fixture (owningMentor=false, currentLeader=true, 60 min): per-member detail and chart
  rendered.
- Missing `projectId` param → `400 Bad Request`, provider never invoked.

## RED

**Command**

```text
mvnw test -Dtest=AttendanceReportPageWebTest,ProjectTaskReportPageWebTest
```

**Observed result**

```text
COMPILATION ERROR
cannot find symbol: class ProjectTaskReportData
cannot find symbol: class ProjectTaskReportMember
cannot find symbol: class ProjectTaskReportDataProvider
cannot find symbol: class ProjectTaskReportController
```

The tests fail to compile because no report page, dataset boundary, or row DTOs existed.

## GREEN

**Command**

```text
mvnw test -Dtest=AttendanceReportPageWebTest,ProjectTaskReportPageWebTest
```

**Observed result**

```text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- ProjectTaskReportPageWebTest
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
mvnw test "-Dtest=com.lab.labtimesheet.feature.reporting.**,com.lab.labtimesheet.architecture.**,com.lab.labtimesheet.ui.**"
Tests run: 83, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

Also covered by the shared chart contract: `docs/tests/web/chart-accessibility-and-theme.md` verifies
`app-charts.js` renders the exact JSON shape emitted for the member-hour and compliance charts.
```

## External-test boundaries

This test proves the server-rendered page contract (aggregate totals, per-member gating, `N/A`,
chart gating) against the mocked dataset boundary. It does not verify the Project membership/
ownership/leadership queries or the Task work-log query — those are the blocked domain contracts the
Project and Task branches will implement behind `ProjectTaskReportDataProvider`. Until those beans are
registered, the production page returns `404 Not Found` rather than stub data.