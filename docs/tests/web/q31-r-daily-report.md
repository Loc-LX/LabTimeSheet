# Test Evidence: Q31-R Daily Project Work Report authorization

- **Test type:** Web and supporting PostgreSQL integration
- **Requirement IDs:** `RPT-006`–`RPT-013`, with Q31-R authorization centered on `RPT-011`–`RPT-013`
- **Scenario IDs:** `AC-RPT-004`, `AC-RPT-005`
- **Test class/method:** `com.lab.labtimesheet.feature.reporting.service.Q31RDailyProjectWorkReportServiceTest`; `com.lab.labtimesheet.feature.reporting.service.DailyProjectWorkReportServiceTest`; `com.lab.labtimesheet.feature.reporting.controller.DailyProjectWorkReportControllerWebTest`; `com.lab.labtimesheet.feature.reporting.controller.DailyProjectWorkReportExportControllerWebTest`; `com.lab.labtimesheet.feature.project.controller.ProjectControllerTest`; `com.lab.labtimesheet.feature.project.service.ProjectServiceIntegrationTest`
- **Implementation commit:** `756ea6c0ed2faf9c4c53e11ee89a4c9e59ee353d4c3ff7d8b4d4997432dd14cf` (Implement Q31-R Mentor and Leader daily reporting)
- **Review/fix commit:** `774ee0bc452c29edb835991e335218ba24be5743aff66ca090b73329cde2f27b` (Harden Q31-R report denial and clean evidence index)
- **Current verification head:** `f7fb0e2051ad644cb77e8d4fc6178acb5f05d03822f185fbd146a6426d1c230e`, before this documentation-only follow-up

## Approved Q31-R scope and scenarios

Q31-R option A supersedes the earlier Daily-report Admin/Leader wording. The approved scope is:

- An owning Mentor may request the Daily Project Work Report for all owned Projects or one selected owned Project.
- A current Project Leader may request HTML, XLSX, or PDF only for one mandatory exact `projectId` that the actor currently leads. The Project may be `PLANNED` or `ACTIVE`; the report date may be today or a permitted past date; and the report includes retained Project history from before the current leadership term.
- The current-Leader entry point is the Project-detail action, not the global Reports sidebar. The global Daily sidebar entry remains Mentor-only.
- Admins, ordinary Interns, former Leaders, Leaders of another Project, completed Projects, missing `projectId` requests, and guessed Project IDs are denied before Attendance context, Task reads, HTML rendering, or exporter invocation. Membership, `ROLE_INTERN`, or possession of a Project ID is not sufficient.
- Leadership replacement transfers access immediately; Project completion ends current-Leader access. No persisted delegation request, toggle, notification, Daily-report artifact, audit event, schema, or migration is introduced. Existing Attendance and Project/Task report scopes remain unchanged.
- HTML, XLSX, and PDF continue to consume one already-authorized immutable Daily dataset. The report keeps selected-date minutes separate from lifetime Task actuals, original estimates, latest Remaining effort forecasts, and DONE-only signed variance; valid Task work is never filtered because a date is a non-workday or global day off.

The acceptance scenarios covered by this evidence are `AC-RPT-004` (Mentor all/selected and current-Leader one-project scope, historical retained work, date/calendar context, empty and deleted-work cases, and denial ordering) and `AC-RPT-005` (identical authorized Daily rows, descriptions, statuses, planning values, and totals across HTML, XLSX, and PDF).

## Protected behavior

The public Daily-report service resolves the authenticated actor and the requested Project scope before constructing the report dataset. A current Leader receives exactly one locked Project only when the Project producer confirms current leadership, open membership, and an open `PLANNED`/`ACTIVE` Project. The service rejects a missing leader `projectId` and propagates non-disclosing producer denial without touching Attendance or Task queries. The same denial ordering applies to Admin, ordinary Intern, former-Leader, other-Project-Leader, completed-Project, and guessed-ID requests.

An authorized Leader's HTML page shows a read-only Project scope and retains the required hidden `projectId`; its XLSX/PDF links preserve that exact scope and selected date. A valid PLANNED Project with no retained work renders an explicit empty report. A valid historical date still includes retained work logged before the current leadership term and groups it by historical author. Mentor all-Projects and selected-owned-Project behavior remains available, while the earlier Admin global scope is no longer authorized.

The report/export path remains format-neutral: HTML, XLSX, and PDF use the existing immutable authorized DTO and do not re-evaluate authorization in an exporter. Existing status, description, soft-deleted-log, planning, total, date-context, and deterministic-filename rules remain protected by the adjacent Daily HTML and export evidence records.

## Test method

`Q31RDailyProjectWorkReportServiceTest` is the narrowest production-shaped authorization seam. Its seven tests prove current-Leader success, locked single-Project state, PLANNED empty state, retained history before current leadership, missing `projectId`, Admin denial, and ordinary/former/other/completed/guessed-ID denials. Mockito verification proves denial occurs before Attendance and Task reads.

`DailyProjectWorkReportServiceTest`, `DailyProjectWorkReportControllerWebTest`, and `DailyProjectWorkReportExportControllerWebTest` preserve the Mentor dataset/date behavior and exercise Leader HTML/XLSX/PDF delegation and exporter non-invocation on denial. `ProjectControllerTest` verifies the Project-detail action, locked capability fact, and navigation/template contract. `ProjectServiceIntegrationTest` supplies the PostgreSQL 18.4 producer boundary for current/former Leader replacement, ordinary/member/other-Project/guessed-ID denials, completion closure, and malformed open leadership.

## Hand-derived expected result

The actor/scope matrix is:

| Actor and request | Expected result |
|---|---|
| Owning Mentor, no `projectId` | All owned Projects, omitting empty Projects in all-Projects mode. |
| Owning Mentor, owned `projectId` | One selected owned Project, including an explicit empty state. |
| Current Leader, exact currently-led `projectId` | One locked `PLANNED` or `ACTIVE` Project in HTML, XLSX, and PDF; today/past dates and retained pre-term history are valid. |
| Admin or ordinary Intern | Denied before report data construction. |
| Former Leader, another Project's Leader, completed Project, or guessed ID | Denied before downstream reads; an ID alone cannot disclose whether the Project exists or was previously led. |
| Current Leader without `projectId` | Denied because the one-Project selection is mandatory. |

For the existing populated Daily fixture, the retained authors contribute `90 + 30 = 120` selected-date minutes. The DONE Task has `150` lifetime actual minutes against a `120`-minute estimate, so its signed DONE-only variance is `+30`; the unfinished Task remains `Pending` even with a current `120`-minute forecast. Those values are expected to remain unchanged when the same authorized DTO is rendered as HTML, XLSX, or PDF.

## RED

### Initial public-seam RED

The recorded Java 25 PowerShell session initialized the repository Maven executable as follows:

```powershell
$env:JAVA_HOME='C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11'; $env:HOME='C:\Users\dookubt'; $env:USERPROFILE='C:\Users\dookubt'; $env:MAVEN_USER_HOME='C:\Users\dookubt\.m2'; $mvn='C:\Users\dookubt\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd'; & $mvn '-Dmaven.repo.local=C:\Users\dookubt\.m2\repository' '-Dtest=Q31RDailyProjectWorkReportServiceTest' test
```

The first production-shaped test was intentionally run before the current-Leader service seams existed:

```text
COMPILATION ERROR
Q31RDailyProjectWorkReportServiceTest.java: cannot find symbol method currentLeaderProjectForDailyReport(long,long)
Q31RDailyProjectWorkReportServiceTest.java: cannot find symbol method lockedSingleProject()
8 errors
BUILD FAILURE
```

This was a feature RED caused by the missing public behavior, not by Docker, a wrong test selector, or an existing unrelated failure. Additional UI/export assertions were added before their navigation/template changes; the final focused run below is the exact recorded GREEN for that complete Q31-R slice.

### Review-fix RED

Review found that an open Project with a current member but no open leadership term leaked the domain invariant exception instead of the non-disclosing access denial. The regression was added first and run with the canonical repository timezone:

```powershell
$env:JAVA_HOME='C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11'; $env:HOME='C:\Users\dookubt'; $env:USERPROFILE='C:\Users\dookubt'; $env:MAVEN_USER_HOME='C:\Users\dookubt\.m2'; $mvn='C:\Users\dookubt\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd'; & $mvn '-Dmaven.repo.local=C:\Users\dookubt\.m2\repository' '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=ProjectServiceIntegrationTest#dailyReportProjectQueryDeniesOpenProjectWithoutCurrentLeadershipTerm' test
```

```text
Tests run: 1, Failures: 1, Errors: 0
Unexpected exception type thrown, expected: ProjectAccessDeniedException
but was: ProjectRuleViolationException: Project has no current Leader
BUILD FAILURE
```

## GREEN

The final focused Q31-R service, HTML/navigation, export, and Project-detail tests were:

```powershell
& $mvn '-Dmaven.repo.local=C:\Users\dookubt\.m2\repository' '-Dtest=Q31RDailyProjectWorkReportServiceTest,DailyProjectWorkReportServiceTest,DailyProjectWorkReportControllerWebTest,DailyProjectWorkReportExportControllerWebTest,ProjectControllerTest' test
```

```text
ProjectControllerTest                         Tests run: 34, Failures: 0, Errors: 0
DailyProjectWorkReportControllerWebTest       Tests run: 7,  Failures: 0, Errors: 0
DailyProjectWorkReportExportControllerWebTest Tests run: 7, Failures: 0, Errors: 0
DailyProjectWorkReportServiceTest             Tests run: 7, Failures: 0, Errors: 0
Q31RDailyProjectWorkReportServiceTest         Tests run: 7, Failures: 0, Errors: 0
Tests run: 62, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The malformed-leadership review regression was then fixed without weakening the Project aggregate invariant:

```powershell
& $mvn '-Dmaven.repo.local=C:\Users\dookubt\.m2\repository' '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=ProjectServiceIntegrationTest#dailyReportProjectQueryDeniesOpenProjectWithoutCurrentLeadershipTerm' test
```

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

The current affected reporting verification is the recorded `87/87` gate on the implementation candidate, with no failures, errors, or skips:

```powershell
& $mvn '-Dmaven.repo.local=C:\Users\dookubt\.m2\repository' '-Dtest=DailyProjectWorkReportControllerWebTest,DailyProjectWorkReportExportControllerWebTest,DailyProjectWorkReportServiceTest,Q31RDailyProjectWorkReportServiceTest,DailyProjectWorkReportExportServiceTest,ProjectTaskReportServiceTest,ProjectTaskReportControllerWebTest,ReportingExportControllerWebTest,ReportingArchitectureTest,ProjectControllerTest,Iteration2ProjectWorkflowWebTest' test
```

```text
Tests run: 87, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The gate covers the Q31-R service/controller/export seams, the existing Daily and Project/Task report paths, exporter delegation, reporting architecture, and the Project-detail navigation contract. The separate focused Project producer integration verification at the current pre-documentation head `f7fb0e2` passed `13/13` against PostgreSQL 18.4 Testcontainers:

```powershell
& $mvn '-Dmaven.repo.local=C:\Users\dookubt\.m2\repository' '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=ProjectServiceIntegrationTest' test
```

```text
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The unmodified-timezone attempt is retained as a baseline limitation rather than feature RED:

```text
FATAL: invalid value for parameter "TimeZone": "Asia/Saigon"
Tests run: 12, Failures: 0, Errors: 12
```

The canonical `Asia/Ho_Chi_Minh` override is the supported passing invocation above. The `13/13` run includes the malformed-leadership denial regression added during review.

## Additional verification

Compilation, Javadoc/doclint, and the reporting dependency/architecture checks were recorded as follows:

```powershell
& $mvn '-Dmaven.repo.local=C:\Users\dookubt\.m2\repository' '-DskipTests' compile
```

```text
BUILD SUCCESS
```

```powershell
& $mvn '-Dmaven.repo.local=C:\Users\dookubt\.m2\repository' '-DskipTests' '-Ddoclint=all' javadoc:javadoc
```

```text
BUILD SUCCESS
```

```powershell
& $mvn '-Dmaven.repo.local=C:\Users\dookubt\.m2\repository' '-Dtest=ReportingDependencyContractTest,ReportingArchitectureTest' test
```

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

```powershell
git diff --check
```

No whitespace errors were reported; Git emitted only the existing Windows line-ending conversion warnings.

The normal UI launcher was unavailable on this machine:

```powershell
npm run test:ui
```

```text
Cannot find module 'C:\Users\dookubt\AppData\Roaming\npm\node_modules\npm\bin\npm-cli.js'
```

The direct npm CLI fallback was:

```powershell
node 'C:\Program Files\nodejs\node_modules\npm\bin\npm-cli.js' run test:ui
```

```text
tests 20
pass 17
fail 3
```

The three failures are the pre-existing CRLF-sensitive container workflow, deployment template, and deterministic E2E clock tests. None exercises the Q31-R templates or Java seams. The frontend build was deliberately not run because it would overwrite the protected dirty generated assets `app.css`, `chart.umd.min.js`, and `icons.svg`.

## Baselines and full-suite comparison

The known `LayerStructureTest` baseline remains unchanged:

```text
LayerStructureTest.applicationUsesOnlyApprovedPackageByFeatureStructure
Tests run: 1, Failures: 1, Errors: 0
Cause: Windows model\dto path separator differs from the expected slash form.
```

Adding the existing `ProjectTaskReportPageWebTest` to the affected selection remains an independent fixture baseline: four context errors because that test slice has no `ProjectQueryService` bean. It is excluded from the corrected `87/87` gate so the Q31-R affected evidence is not conflated with an unrelated fixture defect.

The frozen broad Maven comparison was not rerun after the isolated review fix. Its exact observed result was:

```text
Tests run: 696, Failures: 6, Errors: 21, Skipped: 0; BUILD FAILURE
```

The authorized independent baseline was `684` tests, `5` failures, and `21` errors. The 21 errors matched baseline families (`ProjectQueryIndexIntegrationTest` 1, `ProjectLifecycleLockIntegrationTest` 9, `AttendanceReportPageWebTest` 5, `Iteration2WorkflowFragmentsWebTest` 1, `ProjectTaskReportPageWebTest` 4, and `RoleDashboardWebIntegrationTest` 1). Baseline failures were `LayerStructureTest` 1, `ProjectTaskShellContractTest` 2, and `FrontendSourceContractTest` 2. The sixth failure was the packet-induced Daily print `reports/daily-print.html` table-wrapper case; the wrapper was corrected and its targeted source-contract envelope returned to only the accepted pre-existing `reports/print.html` failure. Do not interpret `696/6/21` as a post-fix full-suite result.

## External-test boundaries

This record proves the service, MVC, exporter-delegation, Project-detail, and PostgreSQL producer boundaries through unit, MockMvc, and Testcontainers tests. It does not prove a live browser screenshot/render, keyboard traversal in a real browser, or a deployed HTTP download. No browser visual QA was performed. The direct UI unit fallback remained `17/20` with the three unrelated CRLF-sensitive baselines, and the frontend build was skipped to preserve the user's dirty generated assets.

The report does not claim a clean repository-wide Maven suite: the independent baseline failures/errors remain attributed above, and the broad run was not repeated after the isolated print-wrapper correction. The evidence task introduced no production, test, migration, configuration, generated-asset, push, or merge change; only this tracked record and the I4-UI-03 plan evidence link are intended to be committed.
