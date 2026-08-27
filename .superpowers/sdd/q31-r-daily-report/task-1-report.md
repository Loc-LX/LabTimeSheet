# Task 1 — Q31-R Mentor and Current-Leader Daily Reporting

## Scope and starting state

Implementation was performed only in:

`C:\Users\dookubt\IdeaProjects\lab-timesheet\.worktrees\partial-jira-tempo`

The requested base and branch checks were:

```text
PS> git branch --show-current
codex/partial-jira-tempo

PS> git rev-parse HEAD
ef130981a74fabe4177d3892b4af2a77ca2957f5f2861a822efdcf1108cdf9dd
```

Before editing, the only existing changes were the protected assets and unrelated files named in
the brief. They were not staged, edited, or committed:

```text
src/main/resources/static/assets/app.css
src/main/resources/static/assets/chart.umd.min.js
src/main/resources/static/assets/icons.svg
docs/adr/for-clankers.rar
scripts/demo-seed-v2.sql
```

No schema, migration, notification, audit, persisted delegation, or report-artifact changes were
made. No push or merge was performed.

## TDD evidence

### RED

The public service seam was established in
`src/test/java/com/lab/labtimesheet/feature/reporting/service/Q31RDailyProjectWorkReportServiceTest.java`
before the production seam was implemented. With the repository Java 25 toolchain selected, the
initial focused invocation was:

```powershell
$env:JAVA_HOME='C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11'; $env:HOME='C:\Users\dookubt'; $env:USERPROFILE='C:\Users\dookubt'; $env:MAVEN_USER_HOME='C:\Users\dookubt\.m2'; $mvn='C:\Users\dookubt\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd'; & $mvn '-Dmaven.repo.local=C:\Users\dookubt\.m2\repository' '-Dtest=Q31RDailyProjectWorkReportServiceTest' test
```

The exact feature RED was a production/test compilation failure:

```text
COMPILATION ERROR
Q31RDailyProjectWorkReportServiceTest.java: cannot find symbol method currentLeaderProjectForDailyReport(long,long)
Q31RDailyProjectWorkReportServiceTest.java: cannot find symbol method lockedSingleProject()
8 errors
BUILD FAILURE
```

The first production slice then turned the service seams green. Subsequent UI and export tests
were added before their template/navigation changes; the initial UI RED included the missing
`Generate Daily Report`/locked scope assertions and a Mockito matcher setup error, all resolved in
the final focused run below.

### GREEN

Focused Q31-R service, HTML/navigation, export, and Project-detail tests:

```powershell
& $mvn '-Dmaven.repo.local=C:\Users\dookubt\.m2\repository' '-Dtest=Q31RDailyProjectWorkReportServiceTest,DailyProjectWorkReportServiceTest,DailyProjectWorkReportControllerWebTest,DailyProjectWorkReportExportControllerWebTest,ProjectControllerTest' test
```

Output summary:

```text
ProjectControllerTest                         Tests run: 34, Failures: 0, Errors: 0
DailyProjectWorkReportControllerWebTest       Tests run: 7,  Failures: 0, Errors: 0
DailyProjectWorkReportExportControllerWebTest Tests run: 7,  Failures: 0, Errors: 0
DailyProjectWorkReportServiceTest             Tests run: 7,  Failures: 0, Errors: 0
Q31RDailyProjectWorkReportServiceTest         Tests run: 7,  Failures: 0, Errors: 0
Tests run: 62, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The corrected affected reporting gate (including the existing Project/Task exporter controller
tests) was:

```powershell
& $mvn '-Dmaven.repo.local=C:\Users\dookubt\.m2\repository' '-Dtest=DailyProjectWorkReportControllerWebTest,DailyProjectWorkReportExportControllerWebTest,DailyProjectWorkReportServiceTest,Q31RDailyProjectWorkReportServiceTest,DailyProjectWorkReportExportServiceTest,ProjectTaskReportServiceTest,ProjectTaskReportControllerWebTest,ReportingExportControllerWebTest,ReportingArchitectureTest,ProjectControllerTest,Iteration2ProjectWorkflowWebTest' test
```

Its result was:

```text
Tests run: 87, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The focused Project query integration test, including current/former Leader replacement,
ordinary/member/other-Project/guessed-ID denials, and completion closure, was run with the
repository-supported PostgreSQL timezone:

```powershell
& $mvn '-Dmaven.repo.local=C:\Users\dookubt\.m2\repository' '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=ProjectServiceIntegrationTest' test
```

```text
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

An unmodified-timezone attempt was also made first and exposed the known independent baseline
environment issue:

```text
FATAL: invalid value for parameter "TimeZone": "Asia/Saigon"
Tests run: 12, Failures: 0, Errors: 12
```

Additional verification:

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

No whitespace errors were reported. Git emitted only the existing Windows line-ending conversion
warnings.

The direct UI unit command was also checked because the normal npm launcher is broken on this
machine:

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

The three failures are the pre-existing CRLF-sensitive container workflow, deployment template,
and deterministic E2E clock tests; none exercises the Q31-R templates or Java seams. The frontend
build was deliberately not run because it would overwrite the protected dirty generated assets
(`app.css`, `chart.umd.min.js`, and `icons.svg`).

The known baseline `LayerStructureTest` was not changed:

```text
LayerStructureTest.applicationUsesOnlyApprovedPackageByFeatureStructure
Tests run: 1, Failures: 1, Errors: 0
Cause: Windows model\dto path separator differs from the expected slash form.
```

Adding the existing `ProjectTaskReportPageWebTest` to the affected web selection likewise remains
a baseline failure: 4 context errors because no `ProjectQueryService` bean is present in that
test slice. The corrected affected gate above excludes that independent, unrelated fixture while
covering the Q31-R and existing report/export paths.

## Implementation and files changed

Production and documentation changes:

- `docs/requirements/partial-jira-tempo-decision-record.md`: replaced Q31 with Q31-R in the
  decision table, detailed role scope, checklist, and traceability; recorded Mentor/current-Leader
  scope, transition rules, denials, and out-of-system Mentor demand.
- `src/main/java/com/lab/labtimesheet/feature/project/model/dto/ProjectDetail.java`: added the
  server-derived `viewerIsCurrentLeader` fact while retaining the existing constructor shape for
  unaffected callers.
- `src/main/java/com/lab/labtimesheet/feature/project/service/ProjectQueryService.java`: added
  the DTO-only current-Leader Project producer for exact active Intern/current leadership/open
  PLANNED or ACTIVE Projects; populated the detail capability fact; restricted the Daily global
  scope producer to owning Mentors.
- `src/main/java/com/lab/labtimesheet/feature/reporting/model/dto/DailyProjectWorkReportView.java`:
  added the explicit `lockedSingleProject` indicator and preserved the existing constructor for
  unlocked callers.
- `src/main/java/com/lab/labtimesheet/feature/reporting/service/DailyProjectWorkReportService.java`:
  retained Mentor all-owned/optional-filter behavior; made Intern access require an exact
  current-Leader `projectId`; denied Admin/default actors before Attendance, Task, or export data;
  reused the immutable Daily dataset for both role scopes.
- `src/main/java/com/lab/labtimesheet/feature/reporting/controller/DailyProjectWorkReportController.java`
  and `ReportExportController.java`: aligned public documentation with Mentor/current-Leader
  authorization; controller/exporter flow remains on the shared Daily DTO.
- `src/main/resources/templates/fragments/layout.html`: made the global Daily sidebar entry
  Mentor-only without changing Attendance or Project/Task report entries.
- `src/main/resources/templates/projects/detail.html`: added the current-Leader-only accessible
  `Generate Daily Report` action linking to `/reports/daily?projectId={id}`.
- `src/main/resources/templates/reports/daily.html`: renders a fixed read-only Leader Project,
  hidden required `projectId`, and preserved XLSX/PDF links; Mentor retains the selector.

Test changes:

- Added public service seam coverage in
  `src/test/java/com/lab/labtimesheet/feature/reporting/service/Q31RDailyProjectWorkReportServiceTest.java`
  for locked current-Leader success, PLANNED empty state, retained history, missing ID, Admin,
  ordinary/former/other/completed/guessed denials, and downstream-read ordering.
- Updated `DailyProjectWorkReportServiceTest` and export/controller tests to supersede the old
  Admin Daily success expectation with Mentor and to cover Leader HTML/XLSX/PDF parity and
  exporter non-invocation on denial.
- Updated `ProjectControllerTest` and `ProjectServiceIntegrationTest` for the detail action,
  current/former transitions, ordinary/other/guessed IDs, completion closure, and derived detail
  capability.

## Commit

Implementation commit:

```text
756ea6c Implement Q31-R Mentor and Leader daily reporting
```

The evidence report is intentionally added as the follow-up local commit after this implementation
commit. Neither commit was pushed or merged.

## Self-review

- Authorization is resolved at the Project producer before `AttendanceApplicationService`,
  `TaskQueryService`, or exporters are reached. Admin, ordinary Intern, former/current-other
  Project Leaders, completed Projects, missing IDs, and guessed IDs receive the same non-disclosing
  access exception.
- Stored current leadership and open membership/status are authoritative; templates consume only
  `viewerIsCurrentLeader` and cannot infer capability from role, membership, or an ID.
- Leader scope is exactly one selected Project, including retained historical authors/logs and
  explicit empty PLANNED reports. Mentor global and selected-owned scopes retain prior semantics.
- HTML, XLSX, and PDF all consume the existing immutable Daily DTO path; no exporter imports a
  repository/entity or re-evaluates authorization.
- Existing Attendance and Project/Task report scope/navigation entries were left unchanged.
- No schema, migration, persisted request/toggle/notification/audit, or generated asset change was
  introduced. Protected dirty assets and unrelated untracked files remain outside the commit.
- The public template tests verify semantic accessibility hooks (labels, read-only Project, hidden
  `projectId`, and download controls). A live browser screenshot/render inspection was not run.

## Concerns and limits

- The npm launcher is broken and the direct UI unit suite remains 17/20 because of three known
  CRLF-sensitive baseline tests. The production frontend build was skipped to protect the user's
  dirty generated assets.
- The default PostgreSQL test timezone alias `Asia/Saigon` is rejected by the current container;
  the focused integration test passes with the canonical `Asia/Ho_Chi_Minh` override.
- `LayerStructureTest` and `ProjectTaskReportPageWebTest` retain the independent baseline failures
  described above. No unrelated repair was attempted.
- No live browser visual QA was performed; web behavior is covered by MockMvc and the affected
  Maven gate.
