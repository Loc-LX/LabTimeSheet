# Data model Plan

**Owner:** Loc-LX · the part carries its own state in the table below.

How the rules of [SPEC.md](SPEC.md) will be built. It is a technical design, not a tracker:
progress belongs in [`plan.md`](../../../../../plan.md). Where this plan and a spec disagree, the spec wins.
Its tasks are in [TASKS.md](TASKS.md). Part C was written as part of the platform plan and moved
here under `D40`, which also replaced its references to module plans with feature plans; its part letter and section numbers are kept so that existing citations resolve.

| Part | Subject | Rules | State |
|---|---|---|---|
| C | The schema change the decisions require, below | `DB-011`, `DB-014`–`DB-022`, the audit of `D39` | Draft of 22 September 2026, for approval on its own; replaces version 1.0 of 16 September |

## Part C — The schema change the decisions require

**Implements** the schema of `DB-011` and `DB-014`–`DB-022`, the predicate audit `D39` starts,
`DB-002` for overdue leave, `DB-010` for the physical diagram, the stored shape `NOT-012` gives
email delivery, the link `PRJ-002` needs to delete a draft's notifications, and the treatment of
months already worked that `D27` decided. Column names are settled when each migration is written
(`D32`). It starts after part A is done, because the code that obeys each tightened predicate
lives in the modules part A creates. This text replaces sections 3 and 4 of version 1.0.

### C.1 Expand first, then contract, module by module

Version 1.0 planned one migration. Tightening every predicate at once breaks the running code,
which the current classes show:

- `AppUser#deactivate` clears the lock timestamp, which the lock rule of `DB-022` refuses.
- The code cancels a pending leave request as `CANCELLED` with no decision, which `DB-018` refuses.
- `LeaveStatus` has no `WITHDRAWN`, so rows reclassified to it could not be read.

So the change comes in two kinds of migration. **V3 expands**: it adds tables, columns and status
values, and relaxes the predicates that refuse newly lawful rows, all of which the running code can
live with. **Each contract migration tightens** what one module's rules forbid, and ships in the
same change as that module's code that obeys it. Each migration stays one review and one rollback
point. Migrations are numbered in the order they ship, and each file name names its module.

### C.2 Before any migration: which database, and what it holds

Each database a migration will run on is identified first by its Flyway history. A database created
while `V2__account_admin_edit_events.sql` existed, from `4c1fa67` until the merge `787d143` removed
it, holds a different `V2`; this plan does not guess how to reconcile it, and work on that database
stops until the maintainer decides. The same step reads the stored rows that the contract steps
depend on (C.5) and records the counts in `plan.md`.

### C.3 V3, the expansion

New tables, each with its constraints and, where the rule asks, an append-only trigger. No running
code writes them yet:

| Table | Rules |
|---|---|
| `attendance_periods` | `DB-014`, `ATT-019`–`ATT-021` |
| `attendance_period_reopens` | `DB-015`, `ATT-022` |
| `attendance_exceptions` | `DB-016`, `EXC-001`–`EXC-004` |
| `attendance_exception_decisions`, `leave_request_decisions` | `DB-017`, `ATT-024` |
| `task_status_transitions` | `DB-020`, `TSK-023`, `TSK-025` |

Changed predicates, each a `D39` member that refuses a row the rules make lawful, or admits a status
no running code writes yet:

| Predicate | Becomes | Rules |
|---|---|---|
| `ck_projects_status`; `ck_projects_activation` | `CANCELLED` accepted, with or without an activation timestamp | `DB-019` |
| `ck_project_invitations_resolution_code`; the `REVOKED` branch of `ck_project_invitations_resolution_state` | `PROJECT_CANCELLED` accepted, with no resolving actor | `DB-011`, `PRJ-023` |
| `ck_leave_requests_status`; `ck_leave_requests_decision` | `OVERDUE` and `WITHDRAWN` accepted as undecided rows without a decision time | `DB-018` |
| `ck_attendance_corrections_status`; `ck_attendance_corrections_decided_at` | `OVERDUE` accepted as undecided | `DB-018`, `COR-007` |
| `ck_attendance_correction_events_type`, `_from_status`, `_to_status` | The amendment, reversal and overdue entries accepted; `OVERDUE` as a status | `DB-017` |
| `ck_app_users_pending_password`, `ck_app_users_activated_state`, `ck_app_users_lock_timestamp` | A `DEACTIVATED` row with neither hash nor activation timestamp accepted, and a `DEACTIVATED` row keeping a lock beside its activation timestamp accepted; the non-blank hash kept | `DB-022` |
| `ex_leave_requests_no_overlap` | Its predicate covers `PENDING`, `OVERDUE` and `APPROVED`. This tightens, but no `OVERDUE` row exists until the attendance code writes one | `DB-002`, `AC-DB-010` |

New nullable columns: who cancelled a Project, when and why (`DB-019`); when a leave request was
withdrawn (`DB-018`); the withdrawn-approval mark of a leave request day (`DB-018`); the responsible
Mentor of an Intern profile, which may reference only a `MENTOR` account (`DB-021`, `AC-DB-008`);
and the Project a notification was raised for (`PRJ-002`). Existing notifications get that link
from their action route where it names a Project, which is how the Project services build it today
(`/projects/{id}` and routes below it); a notification whose route names no Project keeps no link.

Data: V3 creates a period for every Intern and month that has attendance, then applies `ATT-020`
to each, as `D27` decided.

### C.4 Contract migrations and the module that ships each

| Module, and the feature plans whose code ships with it | Predicates the contract migration tightens | Code in the same change |
|---|---|---|
| `identity`: [Account lifecycle](../../../identity/features/account-lifecycle/SPEC.md), [Authentication](../../../identity/features/authentication/SPEC.md) | The exact `DB-022` shapes: a non-blank hash and an activation timestamp together or not at all per status; an activation timestamp set only by `PENDING_ACTIVATION → ACTIVE` and never changed or cleared; a lock timestamp set only by `ACTIVE → LOCKED`, cleared only by `LOCKED → ACTIVE`, and otherwise unchanged. Both timestamp rules by trigger, comparing old and new values with `IS DISTINCT FROM` | Deactivation keeps the lock; `ACC-028`, `ACC-029`, `ACC-030` |
| `attendance`: [Leave](../../../attendance/features/leave/SPEC.md), [Missed-checkout correction](../../../attendance/features/missed-checkout-correction/SPEC.md), [Attendance exception](../../../attendance/features/attendance-exception/SPEC.md) | `DB-018`: an undecided leave request carries no deciding actor (new predicate); `ck_attendance_corrections_pending_decision` counts `OVERDUE` as undecided; a `CANCELLED` leave request carries its approval time and approving Mentor (`ck_leave_requests_decision`, `ck_leave_requests_approval_actor`); a `WITHDRAWN` one carries its withdrawal time. `DB-017`: correction entries carry a reason for an amendment or reversal and refuse update and delete. Before these checks, the reclassification of C.5 | Withdrawal and cancellation under `LEV-011` and `LEV-013`; overdue marking; decision history; corrections stop writing their lock |
| `project`: [Project lifecycle](../../../project/features/lifecycle/SPEC.md), [Task management](../../../project/features/task-management/SPEC.md) | `DB-019`: a `CANCELLED` Project carries the cancelling Mentor, the time and a non-blank reason | `PRJ-023`; `PRJ-002` deleting a draft's notifications by their Project link; the grant half of `TSK-023` |
| `notification`: [Email delivery](../../../notification/features/email-delivery/SPEC.md) | `ck_notifications_email_payload`: `NOT_REQUIRED` and `UNAVAILABLE` carry no payload (`NOT-012`) | None, if C.2 finds no stored row that breaks it; otherwise the rows found are reported before this step |

### C.5 Stored rows

| Case | Treatment |
|---|---|
| Months already worked | `D27`: a period per Intern and month with attendance, then `ATT-020` applied as the running system would |
| Interns already `ACTIVE` without a responsible Mentor | The reference stays empty; `ACC-026` shows them to Admins as needing one, and a decision waits for the assignment |
| The lock timestamp on corrections | Kept as a record of what the old rule did and no longer written. `ck_attendance_corrections_locked_state` stays: it is a history-bound member (`D39`) |
| Correction deadlines | Rows keep the deadlines they were given; only new rows use 48 hours (`D23`) |
| `CANCELLED` leave requests without a decision time | Reclassified to `WITHDRAWN`: the cancellation time becomes the withdrawal time and is then cleared, and the owning Intern is the withdrawer (`D39`). Rows with a decision time stay `CANCELLED`. The counts C.2 records must match this reading before the attendance contract runs |
| Accounts deactivated before the identity contract | Their lock timestamp was already cleared and cannot be restored; reinstatement returns them to `ACTIVE` (`D38`) |
| Monthly leave quota (`ATT-003`) | `ck_attendance_policy_versions_quota` and `ck_leave_request_days_quota` are history-bound members. If C.2 finds no stored policy version above 4 on any database, the calendar module narrows both to 0 through 4 in a contract migration of its own; otherwise both stay at 0 through 31 as lawful history, and `AttendancePolicyCommand` keeps enforcing 0 through 4 for new versions |
| The demo seed of `scripts/` | Regenerated after each migration it is affected by, not patched |

### C.6 The predicate audit

**Scope.** The 148 check, exclusion and unique predicates, constraints and unique indexes, on the
24 tables of `V1__baseline.sql` and `V2__add_task_effort_planning.sql`, counted on 22 September
2026. Primary keys, foreign keys and triggers are not counted. **Criterion**, as `D39` states it: a
predicate is a member when, once the rules are applied, it refuses a lawful row or admits an
unlawful one; a history-bound member is one whose tightening would refuse stored rows that were
lawful when written. **No completeness is claimed**: each verdict below is proved by a probe test
(C.7), and no test or reading can show that no member remains.

The eight tables `D39` examined hold 54 predicates, with the verdicts of its table: 19 members,
2 of them history-bound. The other 16 tables hold 94:

| Table | Verdict |
|---|---|
| `notifications` | Member: `ck_notifications_email_payload` (`NOT-012`), tightened by the notification contract. Unaffected: `ck_notifications_type`, which keeps `SYSTEM` for the notices of `NOT-011` unless a feature plan adds codes in its own contract; `ck_notifications_email_attempts`, since a manual retry resets the attempt count, so "attempts exhausted" in `NOT-012` is not a stored shape; `ck_notifications_title`, `_body`, `_email_status`, `_email_sent`, `_email_retry`, `_version` |
| `attendance_policy_versions` | History-bound member: `ck_attendance_policy_versions_quota` (C.5). Unaffected: `_timezone`, `_month_boundary`, `_schedule`, `_check_in_grace`, `_checkout_grace`, `_checkout_cutoff`, `_penalty`, `_version`, `uq_attendance_policy_versions_effective_from` |
| `attendance_policy_workdays`, `global_calendar_events`, `attendance_records` | Unaffected: `D14`, `D24`, `CAL-007` and `CAL-008` add tables and interfaces, not rows here. `ck_attendance_policy_workdays_iso_day`; `ck_global_calendar_events_name`, `_source`, `_api_provenance`, `_version`, `uq_global_calendar_events_source_uuid`; `uq_attendance_records_intern_date`, `ck_attendance_records_checkout`, `_version` |
| `project_memberships`, `project_leadership_terms`, `project_membership_exit_requests` | Unaffected: `PRJ-023` closes intervals with the cancelling Mentor as actor and marks pending exits `SUPERSEDED`, which the current predicates admit. `uq_project_memberships_id_project`, `ck_project_memberships_interval`, `_removal_actor`, `_version`, `uq_project_memberships_one_active`; `uq_project_leadership_terms_id_project`, `ck_project_leadership_terms_interval`, `_end_actor`, `ex_project_leadership_terms_no_overlap`, `uq_project_leadership_terms_one_current`; `ck_project_membership_exit_requests_type`, `_reason`, `_resolution_note`, `_participants`, `_status`, `_resolution`, `_version`, `uq_project_membership_exit_requests_one_pending_target` |
| `tasks`, `task_comments`, `task_work_logs`, `task_remaining_effort_forecasts` | Unaffected: `D13`, `D15` and `D35`–`D37` change transitions, which the application enforces, and add `task_status_transitions`. `uq_tasks_id_project`, `ck_tasks_title`, `_status`, `_soft_delete_actor`, `_version`, `_estimated_minutes`; `ck_task_comments_body`; `ck_task_work_logs_minutes`, `_note`, `_version`; `ck_task_forecasts_remaining_minutes`, `_actual_snapshot`, `_initial_or_correction`, `_initial_note`, `uq_task_forecasts_one_successor` |
| `user_action_tokens` | Unaffected: `ACC-028` invalidates tokens through the invalidation time, which the current predicates admit. `uq_user_action_tokens_hash`, `ck_user_action_tokens_purpose`, `_hash_length`, `_expiry`, `_terminal_state`, `uq_user_action_tokens_one_live` |
| `system_state`, `smtp_configurations`, `holiday_api_configurations` | Unaffected: no pending decision changes their rows. `ck_system_state_singleton`, `_initialization`, `_version`; `ck_smtp_configurations_status`, `_port`, `_security`, `_host`, `_from`, `_from_name`, `_secret_pair`, `_test_actor`, `_activation`, `_retirement`, `_version`, `uq_smtp_configurations_one_active`, `_one_draft`; `ck_holiday_api_configurations_status`, `_country`, `_ciphertext`, `_nonce`, `_key_version`, `_test_actor`, `_activation`, `_retirement`, `_version`, `uq_holiday_api_configurations_one_active`, `_one_draft` |

Two non-unique indexes filter on `PENDING` alone, `ix_leave_requests_pending_cutoff` and
`ix_attendance_corrections_pending_deadline`; the Leave plan for the first and the Missed-checkout
correction plan for the second say whether it must also serve `OVERDUE` rows.

### C.7 Tests, written first

Every migration step starts with its probe tests, run on PostgreSQL through Testcontainers and seen
failing against the schema before the step: the `AC-DB-*` scenarios the step concerns, and one
probe per member and history-bound member it touches, showing the predicate accepts and refuses what
the rules say. A step is done when its probes pass and the full Maven suite passes; the part is done
when every `AC-DB-*` scenario passes, which is the gate `plan.md` holds for accepting the migration.
Each migration updates, in the same change, the physical diagram of §19.4 for the tables and
columns it adds or changes (`DB-010`).

### C.8 Order of work

The tasks are C-01 to C-08 in [TASKS.md](TASKS.md). C-01 reads before anything writes; C-02 and C-03 are the expansion; C-04 to C-07 are planned here and ship with the code of the feature plans C.4 names, in the order those plans run; C-08 closes the part.

### C.9 Risks

| Risk | Handling |
|---|---|
| A tightened predicate refuses a stored row | C-01 reads the rows first; the contract step's migration runs its data change before its checks |
| A database with the other `V2` receives V3 | C-01 identifies the history first and stops on it |
| An expansion admits a row a rule forbids, until its contract | Only statuses and columns no running code writes are admitted, and each contract ships with the code that writes them |
| The diagram drifts from the SQL | Each migration updates §19.4 in the same change (`DB-010`) |

### C.10 Not in this part

The code of each module that uses the new tables and statuses: the feature plans C.4 names own it,
and each contract migration planned here ships with that code (`D40`). The decision whether to narrow the quota predicates waits on C-01.
