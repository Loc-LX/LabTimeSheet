# Project Lifecycle Plan

**Owner:** Loc-LX · technical design for the [Project lifecycle SPEC](SPEC.md). Progress belongs in [`plan.md`](../../../../../plan.md); tasks are in [TASKS.md](TASKS.md). This draft inherits the module boundaries and constraints of [`project/MODULE.md`](../../MODULE.md). Where this plan and a SPEC disagree, the SPEC wins.

| Part | Subject | Rules | State |
|---|---|---|---|
| P1 | Project lifecycle behavior and its schema contract | `PRJ-001`, `PRJ-002`, `PRJ-012`–`PRJ-016`, `PRJ-023`, `PRJ-024`, `DB-019`, `AC-PRJ-009`, `AUTH-007`, `AC-AUTH-003`; C-06 | Approved on 26 September 2026 after independent review; updated on 30 September 2026 for traceability |

The rules `AUTH-007` and `AC-AUTH-003` are defined in [`project/MODULE.md`](../../MODULE.md).

## Part P1 — Project lifecycle

This plan builds the lifecycle rules in this feature's SPEC. The shared module contract remains in `MODULE.md`; this plan does not restate it. The project contract migration is C-06 of the [Data model plan](../../../platform/features/data-model/PLAN.md), and ships with this plan's cancellation code as C.4 requires; the grant half of `TSK-023` ships with the Task management plan instead.

### What the current code offers

The status below is based on the implementation at the draft's starting revision. It is a rule-by-rule scope inventory, not a claim that existing code or tests fully satisfy each rule.

| Rule | Current implementation and evidence | Planned outcome |
|---|---|---|
| `PRJ-001` | Project creation builds and saves the Project with its initial membership and leadership term in one service transaction: `ProjectService.java:130-164`; route/form: `ProjectController.java:129-164`. | Preserve; retain concurrency and atomicity probes. |
| `PRJ-002` | `ProjectService.delete` checks ownership/status and then bulk-deletes child rows (`ProjectService.java:1557-1580`); `ProjectEntity.requireDeletable` only checks `PLANNED` (`ProjectEntity.java:402-407`). It neither checks emptiness nor deletes notifications. The controller exposes deletion without an emptiness predicate (`ProjectController.java:496-518`); `templates/projects/detail.html:75-89` shows the delete form for every manageable `PLANNED` Project. | Delete only an empty `PLANNED` Project with its original single membership and leadership term, no Task (including soft-deleted), invitation or exit request, and notifications linked by `notifications.project_id`; refuse and hide deletion in every other case. Keep all retained work/history. |
| `PRJ-012` | Activation service resolves and locks current membership/account state, validates eligible members and Task assignees, then delegates Project state transition (`ProjectService.java:1491-1554`). | Preserve and cover every prerequisite with focused refusal and successful activation tests. |
| `PRJ-013` | Task status changes require an `ACTIVE` Project (`TaskService.java:167-192`); work-log create/correct paths call `requireActiveProject` (`TaskService.java:622`, `705`, `971-975`). | This plan verifies lifecycle integration: a `PLANNED` Project cannot accept Task status changes or work logs; Task management owns implementation. |
| `PRJ-014` | Completion requires `ACTIVE` and every non-deleted Task `DONE`, resolves pending invitations and exits, closes intervals, and emits notifications (`ProjectService.java:1374-1432`). | Preserve and test atomicity, retained intervals, terminal read-only behavior and notification effects. |
| `PRJ-015` | The progress query excludes soft-deleted Tasks (`TaskRepository.java:83-95`), the `TaskProjectProgress` value computes DONE over all non-deleted status counts and represents zero Tasks without a percentage (`TaskProjectProgress.java:24-39`); the project detail consumes it through query services. | Preserve exact numerator/denominator and zero-task presentation. |
| `PRJ-016` | The same `TaskRepository.projectProgress` query groups all four statuses and sums retained work-log minutes (`TaskRepository.java:69-95`); `TaskProjectProgress.java:6-39` carries the values. | Preserve status counts and total logged minutes beside progress. |
| `PRJ-023` | No Project cancellation operation or cancellation controller route is present; `ProjectService` has `complete`, `activate`, and `delete`, but no `cancel` (`ProjectService.java:1374`, `1491`, `1557`; controller routes `ProjectController.java:457-518`). V3 already provides nullable cancellation columns and accepts the expanded state (`V3__platform_schema_expansion.sql`, `projects` alterations; C.3). | Add cancellation from `PLANNED` and `ACTIVE` with reason, actor and server time; close and retain intervals; revoke pending invitations as `PROJECT_CANCELLED`; supersede pending exits; preserve Tasks, comments, work logs, estimates and forecasts; notify every closed member; make the aggregate read-only. |
| `PRJ-024` | Creation accepts the submitted start date and validates `endDate >= startDate` in `ProjectEntity.plan` (`ProjectService.java:130-164`; `ProjectEntity.java:118-138`); form range validation is also present (`ProjectCreateForm.java:48-54`). Work-log date restrictions are owned by Task management (`TSK-014`). | Preserve past/today/future starts, reject end-before-start, and keep work-log eligibility tied to membership intervals. |
| `AC-PRJ-009` | Concurrent creation race commits exactly one Project, initial Leader, and term; no 0 or 2 Leader state: `ProjectService.java:130-164`, `ProjectEntity.java:118-138`. Candidate tests to confirm in the task: `ProjectServiceIntegrationTest#createsProjectMembershipAndLeadershipInOneTransaction`, `ProjectControllerTest#validCreateSubmissionUsesAuthenticatedMentorAndRedirectsToDetail`. | Confirm concurrency controls commit exactly one aggregate and term; cite `AC-PRJ-009` in test Javadoc. |
| `AUTH-007` | Admin Project access is read-only; no comment/membership/leadership/task/status authority: `ProjectAuthorizationRequests.java:89-112`, `TaskService.java:220-235`. Candidate tests to confirm in the task: `TaskStatusGrantIntegrationTest#refusesAdminChangingAnyTaskStatus`, `ProjectServiceIntegrationTest#listAndDetailQueriesEnforceRoleOwnershipAndMembershipWithoutIdDisclosure`. | Confirm Admin role is restricted to read-only project observation and denied mutations; cite `AUTH-007` in test Javadoc. |
| `AC-AUTH-003` | Admin views read-only Project and History tab; cannot comment, assign, change status, or manage membership: `ProjectController.java:984-1008`. Candidate tests to confirm in the task: `ProjectControllerTest#completedProjectPagesRenderForAuthorizedRolesWithoutCurrentLeaderOrMutationForms`, `ProjectServiceIntegrationTest#listAndDetailQueriesEnforceRoleOwnershipAndMembershipWithoutIdDisclosure`. | Confirm Admin UI renders read-only views and history without action forms; cite `AC-AUTH-003` in test Javadoc. |
| `DB-019` | V3 expanded Project status/activation checks and added cancellation actor/time/reason columns (`V3__platform_schema_expansion.sql`, Project alterations); the tightened contract is explicitly assigned to C-06 in Data model PLAN C.4. | Ship C-06 with P1-02's cancellation code; probes must cover cancellation from both source states and all status/activation/cancellation-field combinations in `AC-DB-012`. |

The current `PRJ-002` deletion is a known data-loss defect: it removes Tasks, comments, and work logs from a draft without proving that it is empty. `D42` explicitly says that after the emptiness guard is implemented, these five JPQL bulk deletes always affect zero rows and should be removed:

| JPQL method in `ProjectRepository` | Current query | Why redundant after the guard |
|---|---|---|
| `deleteTaskCommentsByProjectId` (`ProjectRepository.java:34-40`) | Delete comments whose Task belongs to the Project | Any Task, including soft-deleted Tasks, makes the Project non-empty and deletion is refused. |
| `deleteTaskWorkLogsByProjectId` (`ProjectRepository.java:42-45`) | Delete Task work logs for the Project | Any Task makes deletion refuse; work history is retained. |
| `deleteTasksByProjectId` (`ProjectRepository.java:47-50`) | Delete the Project's Tasks | Any Task, including soft-deleted, makes deletion refuse. |
| `deleteExitRequestsByProjectId` (`ProjectRepository.java:52-55`) | Delete Project exit requests | Any exit request makes deletion refuse. |
| `deleteInvitationsByProjectId` (`ProjectRepository.java:57-60`) | Delete Project invitations | Any invitation makes deletion refuse. |

The three remaining delete operations remove the one initial leadership term, the one initial membership, and the Project itself. Notification deletion remains in the lifecycle operation and uses V3's `notifications.project_id`, which references `projects(id)` with `ON DELETE SET NULL` and which V3 filled from `action_url` for the rows that existed when it ran. No code writes the column yet: `NotificationService#publish` persists no Project link and `NotificationEntity` does not map it, so every notification raised after V3 has none and `PRJ-002` would leave it behind. P1-01 therefore makes the notification module carry an optional Project identifier in `NotificationAction`, persist it, and offer a delete by Project identifier; every publication whose action targets a Project passes that identifier. Delete linked notifications before deleting the Project, as `PRJ-002` requires.

### Order of work

Tasks P1-01 through P1-05 are in [TASKS.md](TASKS.md). This feature implements the Project business rules that Authorization B.11 leaves to owning features; the Task transition grant for `TSK-023` remains in Task management as Authorization B.7 directs. Each behavior change starts with its named acceptance scenario, adds the smallest failing test, records the expected failure, then runs GitNexus impact on each symbol before editing it. `UNKNOWN` is unresolved and requires text-search confirmation; HIGH or CRITICAL impact is reported before that symbol is changed. Each task adds or updates Javadoc in the same step as changed public/protected members. Tests name their rule IDs in Javadoc.

1. P1-01 first establishes the deletion behavior: exact empty-draft predicate, no delete affordance for non-empty/terminal Projects, retention of Task history, and deletion of Project-linked notifications. It removes the five D42 bulk-delete methods only after the RED probes prove the emptiness guard refuses all five child-data cases. P1-01 needs no contract migration and runs first, so the data-loss defect is removed before cancellation is built.
2. P1-02 implements Project cancellation from `PLANNED` and `ACTIVE`, preserving the full `PRJ-023` transaction and notification contract, and carries the Project contract migration of C-06 in the same change: the next free Flyway version tightens `projects` so that a `CANCELLED` row carries the cancelling Mentor, the time and a nonblank reason (`DB-019`), with its `AC-DB-012` probes and the cancellation half of `AC-DB-007` seen failing first. `DB-020` is already enforced by V3 and is not changed. The grant half of `TSK-023` is not part of C-06: it writes `task_status_transitions`, whose contract V3 already enforces, and ships with the Task management plan after the authorization policy of part B (C.4).
3. P1-03 closes remaining lifecycle acceptance gaps for create, activation, completion, progress, counts and date behavior, coordinating Task-owned behavior with the Task management plan.
4. P1-04 closes the part: C-06's probes and the full verification gates.
5. P1-05 closes traceability for lifecycle rules `AC-PRJ-009`, `AUTH-007`, and `AC-AUTH-003`: candidate tests are verified, rule citations are added to test Javadocs, and any necessary targeted tests are added without weakening assertions.

### Tests

Tests use real Spring services and PostgreSQL through Testcontainers (`test` profile), never H2. Reuse existing Project integration-test fixture builders and service tests; do not create fake Project repositories at service boundaries. For each task, run its focused test RED before production changes and GREEN after. Required behavior includes:

- `AC-PRJ-014`: empty draft deletion succeeds only for its owner and deletes initial membership/leadership rows plus every Project-linked notification; each blocker (Task including soft-deleted, invitation, exit request, extra membership, active/terminal status) refuses and shows no delete action; foreign-owner and missing-ID responses do not expose the record.
- `AC-PRJ-015`: cancellation without a reason and by a non-owner changes nothing; valid cancellation from both `PLANNED` and `ACTIVE` records actor/server time/reason, closes retained intervals, resolves pending workflows, sends notifications, preserves all Task and effort history, and makes later mutations fail.
- `AC-DB-012`: C-06 PostgreSQL probes exercise `DB-019` for cancellation from both source statuses, timestamp-presence combinations, required cancellation actor/time/reason, and invalid status/field combinations.
- Existing creation race, activation-readiness, completion, progress/count, and date-boundary scenarios remain asserted (`AC-PRJ-006`–`009`, `AC-PRJ-016`). Task-owned `PRJ-013` integration is coordinated with Task management.

Part B's `unbuilt-operations.tsv`, on the Authorization branch and not yet on `main`, lists "Cancel a PLANNED Project" and "Cancel an ACTIVE Project". Whichever of part B and part C reaches `main` second deletes those two lines and exercises their matrix cells in the same change. Nothing in this plan edits that file on the part C branch, where it does not exist.

### When done

- All rules listed in the P1 state table have an implementation/test disposition; Task-owned behavior is linked to its owning plan rather than claimed as built here.
- The draft deletion cannot remove retained work, deletes linked notifications only for a valid empty draft, and no longer calls the five redundant D42 deletes.
- Cancellation behavior meets the reviewer-approved feature scope; C-06 and its `AC-DB-007` and `AC-DB-012` probes ship with P1-02.
- The focused PostgreSQL tests, full Maven suite, disposable E2E suite, `npm run test:ui`, and `git diff --check` pass. GitNexus analyze and detect-changes are recorded after final edits.

### Risks

| Risk | Handling |
|---|---|
| Draft deletion destroys retained work | Lock the Project and establish emptiness across live and soft-deleted Tasks, invitations, exit requests and the exact original membership/leadership rows before any deletion. Probes prove each blocker independently. |
| A concurrent child row appears during deletion | Use the Project lock and verify all child-creation paths share the same aggregate lock protocol; if the current protocol cannot establish this, stop for a concurrency decision rather than claim the predicate is safe. |
| Notification identity is guessed from URL text | Every Project-scoped publication passes the Project identifier, and the delete uses the column, never `action_url`. A test raises a notification after V3 and proves it is deleted with the draft, while one raised for another Project is kept. |
| Cancellation partially closes membership or pending workflows | Keep all cancellation effects and notification publication in one service transaction; assert rollback leaves all rows unchanged on a failure. |
| Contract migration arrives before code can satisfy it | P1-02 carries the `DB-019` migration with cancellation code; see the `AC-DB-012` probes and cancellation half of `AC-DB-007` fail first. V3 already enforces `DB-020`, which is unchanged. |

### Not in this part

Editing Project details. §5.2 grants the owning Mentor *Edit* on its Project, but no rule of this SPEC says which fields may change, in which statuses, or what a date change does to existing Tasks, and no code edits a Project today. It needs a rule and an acceptance scenario of its own before any plan builds it; `plan.md` holds it under *Waiting on a decision*, and part B's unbuilt-operation line "Edit Project details" stays until then.

Task lifecycle/status transition implementation, Task restoration after blocking, membership/leadership and invitation workflows except their required lifecycle resolution, attendance and internship rules, notification delivery internals, and any schema contract other than the Project portion of C-06. The Task management plan owns the grant half of `TSK-023` and the `task_status_transitions` contract. No rule outside the Project lifecycle SPEC is redefined here.
