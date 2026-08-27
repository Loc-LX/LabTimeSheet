# Test Evidence: Project/Task report formulas and visibility

- **Test type:** Unit
- **Requirement IDs:** `RPT-003`, `RPT-005`, `RPT-009`, `AUTH-010`
- **Scenario IDs:** `AC-AUTH-008`, `AC-PRJ-008`
- **Test class/method:** `com.lab.labtimesheet.feature.reporting.service.ProjectTaskReportFormulasTest`
- **Implementation commit:** pending

> **Supersession notice — 27 August 2026:** The five-row visibility matrix below records the
> historical Admin-positive formula contract. The latest RPT-005/AUTH-010 decision removes Admin
> from Project/Task report scope and per-member hours; owning Mentors and current Leaders retain
> detail, and ordinary members retain aggregate-only output. Historical test results are kept
> unchanged and do not prove the revised Admin denial.

## Protected behavior

The shared Project/Task report dataset must aggregate completion percentage, status counts, blocked
Task count, and total logged minutes for a filtered set of Tasks, and must decide per-member hour
visibility so ordinary members receive only aggregate totals (RPT-003, RPT-005, AUTH-010). A failure
here would leak another member's detailed hours to an ordinary member or drift totals across the
shared HTML/Excel/PDF output (RPT-001, RPT-009).

## Test method

Pure, repository-free `ProjectTaskReportService` invoked directly with constructed
`ProjectTaskReportTask` rows (no Spring context, no database). Cases: aggregation across four Tasks in
three statuses with logged minutes; the empty Project case matching AC-PRJ-008's zero-Task `N/A`
progress; and the five-row per-member visibility matrix for Admin, owning Mentor, current Leader, and
ordinary member. This is the narrowest production-shaped test because the aggregation and visibility
decision are the entire protected behavior.

## Hand-derived expected result

- Four Tasks: DONE×2 (10 + 5 min), IN_PROGRESS×1 (30 min), BLOCKED×1 (0 min) → progress total 4,
  DONE 2, IN_PROGRESS 1, BLOCKED 1; completion = 2/4 = 50.0%; total minutes = 10+5+30+0 = 45;
  blocked = 1.
- Empty Project: progress total 0, completion empty (`N/A`), total minutes 0, blocked 0.
- Per-member detail visible iff Admin or owning Mentor or current Leader: (T,F,F)→yes, (F,T,F)→yes,
  (F,F,T)→yes, (F,T,T)→yes, (F,F,F)→no.

## RED

**Command**

```text
.\mvnw.cmd -q test -Dtest=ProjectTaskReportFormulasTest
```

**Observed result**

```text
[ERROR] .../ProjectTaskReportFormulasTest.java:[5,56] cannot find symbol
  symbol:   class ProjectTaskReportSummary
  location: package com.lab.labtimesheet.feature.reporting.model.dto
[ERROR] .../ProjectTaskReportFormulasTest.java:[6,56] cannot find symbol
  symbol:   class ProjectTaskReportTask
[ERROR] .../ProjectTaskReportFormulasTest.java:[13,26] cannot find symbol
  symbol:   class ProjectTaskReportService
[ERROR] Failed to execute goal maven-compiler-plugin:3.15.0:testCompile ...
  Compilation failure
```

The test fails to compile because the shared Project/Task dataset records and formula service do not
exist yet — the expected missing production behavior.

## GREEN

**Command**

```text
.\mvnw.cmd -q test -Dtest=ProjectTaskReportFormulasTest
```

**Observed result**

```text
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
-- in com.lab.labtimesheet.feature.reporting.service.ProjectTaskReportFormulasTest
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
.\mvnw.cmd test "-Dtest=com.lab.labtimesheet.feature.reporting.**,com.lab.labtimesheet.feature.task.**,com.lab.labtimesheet.architecture.**"
Tests run: 110, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

This test does not prove the report page authorization, the Project/member/status/date filter query,
or the per-member work-log feed (logged minutes must be supplied by the Task feature). Those are
covered by the web report test and the Task work-log contract; this file deliberately tests only the
shared aggregation and visibility decision.
