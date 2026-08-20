# Test Evidence: Hand-checkable Project work summary on PostgreSQL

- **Test type:** Integration
- **Requirement IDs:** AUTH-010, RPT-005, PRJ-014, PRJ-015, PRJ-016
- **Scenario IDs:** RPT-005.1, RPT-005.2, PRJ-015.1, PRJ-016.1
- **Test class/method:** `com.lab.labtimesheet.feature.task.service.TaskWorkSummaryIntegrationTest` (all four methods)
- **Implementation commit:** pending

## Protected behavior

`TaskQueryService.projectWorkSummary(actorEmail, projectId)` returns real, hand-checkable status counts and total logged minutes from PostgreSQL, with completion percentage computed as DONE/non-deleted (N/A when zero). Per-member hour breakdown is visible to the owning Mentor and the current Leader but hidden from an ordinary member; non-members are denied at the Project visibility gate. Soft-deleted Tasks drop out of progress counts but their work logs remain permanent history in the totals.

## Test method

Real PostgreSQL (Testcontainers 18.4) with Mentor/Leader/member/other-member/bystander, an active Project and leadership term. Tasks created through `TaskService`; minutes inserted directly as `task_work_logs` rows; summary read through the production service. Assertions compare status counts, the percentage (`100.0/3`), total minutes (90+45=135), per-member rows by membership, and the scoped/hidden behaviors; the bystander path asserts `ProjectAccessDeniedException`.

## Hand-derived expected result

- 3 tasks (TODO, IN_PROGRESS, DONE), minutes 60+30 on member + 45 on other ΓåÆ TODO=1, IN_PROGRESS=1, DONE=1, total=3, 33.333%, minutes=135, member=90, other=45.
- Member actor after soft-deleting an IN_PROGRESS task with 30 min ΓåÆ TODO=1, IN_PROGRESS=0, DONE=0, 0.0%, minutes=90 (log history retained), per-member hidden.
- Empty Project ΓåÆ N/A percentage, 0 minutes, 0 progress, Mentor sees breakdown.
- Bystander ΓåÆ `ProjectAccessDeniedException`.

## RED

**Command**

```text
.\mvnw.cmd "-Dtest=TaskWorkSummaryIntegrationTest" test
```

**Observed result**

```text
Tests run: 4, Failures: 0, Errors: 2
TaskValidation Task status transition is not allowed
TaskNotFound Task or Project was not found
```

Fixture errors only: an illegal direct TODOΓåÆDONE edge and a `softDelete` on a DONE Task (which the rule forbids). Fixed by routing through IN_PROGRESS and soft-deleting an IN_PROGRESS Task.

## GREEN

**Command**

```text
.\mvnw.cmd "-Dtest=TaskTransferIntegrationTest,TaskWorkSummaryIntegrationTest" test
```

**Observed result**

```text
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
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

Minutes are inserted directly rather than through the work-log service; the per-member scope is exercised at the query boundary, not through a rendered report page. Browser/report rendering is a reporting-module concern out of scope here.
