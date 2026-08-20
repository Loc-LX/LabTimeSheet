# Test Evidence: Scoped Project/Task work summary authorization boundary

- **Test type:** Unit
- **Requirement IDs:** AUTH-010, RPT-005, PRJ-015, PRJ-016
- **Scenario IDs:** RPT-005.1, RPT-005.2, PRJ-015.1
- **Test class/method:** `com.lab.labtimesheet.feature.task.service.TaskProjectWorkSummaryScopeTest` (all four methods)
- **Implementation commit:** pending

## Protected behavior

`TaskQueryService.projectWorkSummary(actorEmail, projectId)` always returns aggregate status counts, completion percentage (empty `OptionalDouble` = N/A when the Project has no non-deleted Tasks), and total logged minutes. The per-member hour breakdown is populated only for an Admin, the owning Mentor, or the current Leader (AUTH-010/RPT-005); an ordinary visible member receives aggregate totals with no member detail. Non-member actors are denied by the Project visibility gate.

## Test method

Mocked `TaskRepository`, `TaskWorkLogRepository`, and `ProjectQueryService` (which provides `authenticatedActor` role + `taskContext` authorization facts). Four cases: owning Mentor sees per-member rows; current Leader sees per-member rows; ordinary member sees empty per-member list with `perMemberVisible=false` and correct totals; empty Project yields N/A completeness with zero minutes and zero progress. These are the narrowest production-shaped tests because they pin scope decisions that combine role, ownership, and leadership.

## Hand-derived expected result

- Mentor/Leader: `perMemberVisible=true`, non-empty breakdown, `totalMinutes` = log sum.
- Ordinary member: `perMemberVisible=false`, empty breakdown, totals and progress still correct; TODO=1/DONE=1 ΓåÆ `50.0`.
- Empty Project: percentage empty, `totalMinutes=0`, `progress().total()=0`.

## RED

**Command**

```text
.\mvnw.cmd "-Dtest=TaskProjectWorkSummaryScopeTest" test
```

**Observed result**

```text
[ERROR] COMPILATION ERROR :
[ERROR] no suitable method found for willReturn(java.util.List<java.lang.Object>)
```

Test fixture compile failure (raw `List.of(new Object[] {...})` type inference), not a production failure; fixed with `List.<Object[]>of(...)`.

## GREEN

**Command**

```text
.\mvnw.cmd "-Dtest=TaskTransferServiceTest,TaskQueryServiceTest,TaskProjectWorkSummaryScopeTest" test
```

**Observed result**

```text
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
.\mvnw.cmd "-Dtest=Task*" test
Tests run: 119, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## External-test boundaries

Scope is proven with mocked authorization facts, not against real Project queries. Hand-checkable PostgreSQL totals, N/A semantics, soft-deleted exclusion from progress counts, and the ProjectAccessDeniedException path are proven in `docs/tests/integration/task-work-summary.md`.
