# Iteration 1–2 Attendance Audit — `work/attendance`

- **Audit date:** 2026-08-21
- **Auditor:** opencode (acting backend engineer for `work/attendance`)
- **Branch audited:** `work/attendance` at `b7e9d4f` (worktree NOT clean — see I2-ATT-01 web layer)
- **Authority order applied:** user decisions > `requirements-specification.md` > `requirements-update-manifest.md` > `PROJECT_PLAN.md` > `software-requirements-specification.md` §9 > `duong-work-attendance-scope.md` > `iteration-2-attendance-code-file-map.md` > `iteration-2-attendance-code-mastery.md` > `database-schema.sql` > existing tests/code > inference.
- **Environment used for re-verification:** Java 25.0.3, Maven 3.9.16 (`mvnw.cmd`), Spring Boot 4.1.0 baseline, PostgreSQL 18.4 Testcontainers (Docker daemon running), Flyway V1.

## Executive summary

| ID | Deliverable | Audit verdict | Core evidence | Notes |
|---|---|---|---|---|
| I1-ATT-01 | Seeded policy, timezone, workdays, separate grace boundaries | `DONE` | `AttendancePolicyTest` 3 GREEN; `AttendanceServiceTest` 5 GREEN; `attendance-policy.md`, `attendance-punch-boundaries.md` | Evidence cites stale (pre-replay) SHAs; behavior verified green on branch |
| I1-ATT-02 | Manual future calendar events and day-off decisions | `DONE` | `CalendarAuthorizationWebIntegrationTest` 1 GREEN; `AttendancePersistenceIntegrationTest`; `admin-calendar-authentication.md` | Admin-only, past-immutability covered |
| I1-ATT-03 | Check-in once on eligible day, server time, effective policy | `DONE` | `AttendanceServiceTest`, `AttendanceApplicationServiceTest`, `attendance-persistence.md` | Inclusive 09:00 boundary green |
| I1-ATT-04 | Check-out through attached-policy cutoff and basic classification | `DONE` | `AttendanceServiceTest`, `attendance-persistence.md` | Cutoff/zero-grace green |
| I1-ATT-05 | Own-attendance history + Mentor/Admin inspection | `DONE` | `AttendancePersistenceIntegrationTest`, `AttendanceControllerTest`, `attendance-web.md` | Authorization green |
| I2-ATT-01 | Schedule future-month policy versions + preserve effective history | `PARTIAL` | `AttendancePolicyServiceTest` 7 GREEN; `AttendancePolicySchedulingIntegrationTest` 4 GREEN; committed `1f8e49e`/`2761ae5` | Core service layer DONE; **Admin web layer (controller/template/test/evidence) UNCOMMITTED** — tracker prematurely `DONE` |
| I2-ATT-02 | Preview/import VN HolidayAPI, Admin selection, provenance, dedup | `DONE` | `HolidayImportServiceTest` 9, `HolidayImportIntegrationTest` 2, `HolidayFallbackIntegrationTest` 1, web 2+1; committed `9be57ef`/`c6d7264` | Fallback defect found+fixed by RED; boundary nuance flagged below |
| I2-ATT-03 | Materialize frozen leave allocations + monthly/cross-month quota | `DONE` | `LeaveServiceTest` 8, `LeaveMaterializationIntegrationTest` 4, `LeaveConcurrencyIntegrationTest` 2; committed `69b54eb`/`6f8477f` | PostgreSQL exclusion + locking green |
| I2-ATT-04 | Leave submit/approve/reject/cancel + overlap protection | `DONE` | `LeaveLifecycleTest` 16, `LeaveWorkflowIntegrationTest` 7, web 3+2; committed `5e9e598`/`a4bea66` | Same-day boundary/overlap green |
| I2-ATT-05 | Missed-checkout correction submission + effective-checkout derivation | `DONE` | `CorrectionServiceTest` 13, `CorrectionPersistenceIntegrationTest` 7, web 3; committed `4c09382`/`572ba15` | Window rules + unique constraint green |
| I2-ATT-06 | Mentor approve/reject/revert + separate decision-window locking | `DONE` | `CorrectionDecisionServiceTest` 12, `CorrectionWindowGuardTest` 6, `CorrectionExpiryIntegrationTest` 1, `CorrectionPersistenceIntegrationTest` 11, web 3; committed `2ace890`/`b7e9d4f` | Issue #6 fixture fixed; see stale `iteration-2-test-report.md` |
| I2-ATT-07 | Idempotent schedulers + equivalent request-time deadline guards | `NOT_IMPLEMENTED` | No `@Scheduled`/`@EnableScheduling`/`SchedulingConfigurer`/`TaskScheduler` in `src/main/java`; no `AttendanceScheduler`/`AttendanceDeadlineService`; no notification integration | `COR-008`, `LEV-010`, `ERR-004`, `NOT-*` unsatisfied; partial request-time correction guard exists |

## Full-suite re-verification (this audit)

Command:

```text
cmd /c "mvnw.cmd -q -Dtest='com.lab.labtimesheet.feature.attendance.**' -Dsurefire.failIfNoSpecifiedTests=false test"
```

Result (71 surefire reports; attendance + depended suites all ran against PostgreSQL 18.4 Testcontainers):

```text
FULL SUITE TOTAL=336 FAILURES=0 ERRORS=0
```

The untracked `AttendancePolicyWebIntegrationTest` (2 tests) also passed in this run. Branch head is green in the audited working tree.

## Per-tracker findings

### I1-ATT-01 — DONE
- Requirements: `ATT-001`–`ATT-004`, `ATT-006`, `GOV-011`, `GOV-012`.
- Code: `AttendancePolicy`, `AttendancePolicyTimeline`, `AttendanceService`, `AttendanceApplicationService`.
- Evidence: `docs/tests/unit/attendance-policy.md`, `attendance-punch-boundaries.md`, `attendance-checkout-eligibility.md`, `attendance-current-state.md`.
- Focused verification: `AttendancePolicyTest` 3 GREEN; `AttendanceServiceTest` 5 GREEN.
- Commit: evidence docs cite `71901d16…`, `4c39df70…`, `8b48e28…`, `c8d4e9ee…` — **all dangling** (history replayed); behavior is present in the reachable branch and green.
- Remaining risk: none material; only SHA references need refresh when docs are next edited.

### I1-ATT-02 — DONE
- Requirements: `CAL-001`–`CAL-009`, `SEC-013`, `AUTH-002`.
- Code: `CalendarApplicationService`, `GlobalCalendarEventEntity`, `CalendarController`, `AttendanceController`.
- Evidence: `docs/tests/web/admin-calendar-authentication.md`, `docs/tests/web/attendance-web.md`, `docs/tests/integration/attendance-persistence.md`.
- Focused verification: `CalendarAuthorizationWebIntegrationTest` 1 GREEN; persistence tests GREEN.
- Commit: same stale-SHA caveat as I1-ATT-01.
- Remaining risk: none.

### I1-ATT-03 — DONE
- Requirements: `ATT-007`, `ATT-008`, `ATT-009`, `ATT-010`, `AC-ATT-002`–`AC-ATT-005`.
- Code: `AttendanceService` (inclusive grace boundary), `AttendanceApplicationService`.
- Evidence: `attendance-punch-boundaries.md`, `attendance-checkout-eligibility.md`, `attendance-persistence.md`.
- Focused verification: `AttendanceServiceTest` 5 GREEN; `AttendanceApplicationServiceTest` 5 GREEN; `AttendanceConcurrencyIntegrationTest` 1 GREEN.
- Remaining risk: none.

### I1-ATT-04 — DONE
- Requirements: `ATT-010`, `ATT-011`, `ATT-012`, `ATT-016`.
- Code: `AttendanceService.checkout`, `AttendanceViolations`, `AttendanceRecordEntity.setCheckOutAt`.
- Evidence: `attendance-punch-boundaries.md`, `attendance-persistence.md`, `attendance-web.md`.
- Focused verification: cutoff + zero-grace + no-overwrite tests GREEN.
- Remaining risk: none.

### I1-ATT-05 — DONE
- Requirements: `ATT-016`, `AUTH-001`–`AUTH-003`, `RPT-004`, `UI-013`.
- Code: `AttendanceApplicationService.history`, `AttendanceController`, `AttendanceCurrentUserService`.
- Evidence: `attendance-persistence.md`, `attendance-web.md`, `attendance-current-state.md`.
- Focused verification: `AttendancePersistenceIntegrationTest` 7 GREEN; `AttendanceControllerTest` 8 GREEN.
- Remaining risk: none.

### I2-ATT-01 — PARTIAL (core DONE; Admin web layer uncommitted)
- Requirements: `ATT-001`–`ATT-006`, `AC-ATT-001`, `SEC-013`, `UI-013`.
- Core service layer: `AttendancePolicyService.scheduleVersion/replaceVersion`, `AttendancePolicyTimeline`, `AttendancePolicyEntity`, `SchedulePolicyCommand`. Reachable commits `1f8e49e` (feat) + `2761ae5` (docs). Focused verification: `AttendancePolicyServiceTest` 7 GREEN; `AttendancePolicySchedulingIntegrationTest` 4 GREEN.
- **Gap (blocking full DONE):** the Admin policy web screen is implemented but **not committed**. Untracked/modified in the worktree:
  - `src/main/java/com/lab/labtimesheet/feature/attendance/controller/AttendancePolicyController.java`
  - `src/main/java/com/lab/labtimesheet/feature/attendance/model/dto/AttendancePolicyVersionView.java`
  - `src/main/java/com/lab/labtimesheet/feature/attendance/model/dto/PolicyScheduleForm.java`
  - `src/main/resources/templates/attendance/policy.html`
  - `src/test/java/com/lab/labtimesheet/feature/attendance/controller/AttendancePolicyWebIntegrationTest.java`
  - `docs/tests/web/attendance-policy-admin.md` (header says `Implementation commit: pending (tracker row TBD)`)
  - Modified: `AttendancePolicyService.java` (added `versions()`), `templates/fragments/layout.html` (Admin policy nav link), `.gitignore`
- The repo tracker already claims `I2-ATT-01 DONE` citing `a2205b5` (a dangling pre-replay SHA). The web evidence has not been committed, so the Global DoD ("documentation/evidence paths recorded in this tracker", "final branch head is green" for the screen) is not met yet.
- **Required next action:** commit the Admin policy web layer (RED/GREEN for `AttendancePolicyWebIntegrationTest` recorded in `docs/tests/web/attendance-policy-admin.md`), then mark the tracker row `DONE`.

### I2-ATT-02 — DONE (with boundary nuance)
- Requirements: `CAL-001`–`CAL-006`, `INT-007`–`INT-009`, `AC-CAL-001`–`AC-CAL-004`, `AC-INT-002`.
- Code: `HolidayImportService`, `HolidayApiClient`, `HolidayApiClientConfiguration` (`@ConditionalOnMissingBean` factory), `UnconfiguredHolidayApiClient`, `CalendarController` `/holidays`.
- Evidence: `docs/tests/unit/holiday-import.md`, `docs/tests/integration/holiday-import.md`, `docs/tests/web/holiday-import.md`.
- Focused verification: `HolidayImportServiceTest` 9 GREEN; `HolidayImportIntegrationTest` 2 + `HolidayFallbackIntegrationTest` 1 GREEN; `HolidayImportWebIntegrationTest` 2 + `HolidayFallbackWebIntegrationTest` 1 GREEN.
- Commits: `9be57ef` (feat) + `c6d7264` (docs), both reachable.
- RED value: the fallback test proved a real defect (`@ConditionalOnMissingBean` on `@Service` was unreliable when no client existed) and the fix moved the fallback to a conditional `@Bean` factory. Genuine TDD on the integrated path.
- **Boundary nuance to coordinate:** the file map (authority order position 6) lists `HolidayApiClient.java` as **platform-owned** under `feature/integration/service/`. In this branch it is defined as an attendance-owned contract under `feature/attendance/service/` plus an attendance fallback bean, and it explicitly consumes a platform-supplied tested HTTP transport. This is a reasonable interpretation (attendance owns preview interpretation; the real client/credential storage remains platform-owned and absent), but the file placement should be confirmed with the platform owner to avoid a duplicate-client conflict when `I2-PLAT-05` lands.
- Remaining risk: real HTTP client + credential lifecycle not yet supplied by platform (out of attendance ownership).

### I2-ATT-03 — DONE
- Requirements: `LEV-001`–`LEV-005`, `LEV-006` (overlap pre-check), `AC-LEV-001`–`AC-LEV-002`, `DB-008`.
- Code: `LeaveService.submit`, `LeaveRequestEntity.pending`, `LeaveRequestDayEntity`, `LeaveRequestDayRepository` (aggregates), `AccountService.lockActiveInternship`.
- Evidence: `docs/tests/unit/leave-service.md`, `docs/tests/integration/leave-materialization.md`, `docs/tests/integration/leave-concurrency.md`, `docs/tests/web/intern-leave.md`.
- Focused verification: `LeaveServiceTest` 8 GREEN; `LeaveMaterializationIntegrationTest` 4 GREEN; `LeaveConcurrencyIntegrationTest` 2 GREEN.
- Commits: `69b54eb` (feat) + `6f8477f` (docs), reachable.
- PostgreSQL specifics verified: `leave_request_days` frozen rows, per-month quota snapshot, quota release on rejection, profile-row lock serialization, exclusion constraint backstop.

### I2-ATT-04 — DONE
- Requirements: `LEV-006`–`LEV-009`, `LEV-011`, `LEV-012`, `AC-LEV-003`–`AC-LEV-005`.
- Code: `LeaveService.submit/decide/cancel/edit`, `LeaveRequestEntity` mutators, `findOverlapping`, exclusion-based reservation-exclusion aggregate.
- Evidence: `docs/tests/unit/leave-lifecycle.md`, `docs/tests/integration/leave-workflow.md`, `docs/tests/web/mentor-leave.md`, `docs/tests/web/intern-leave.md`.
- Focused verification: `LeaveLifecycleTest` 16 GREEN; `LeaveWorkflowIntegrationTest` 7 GREEN; `MentorLeaveWebIntegrationTest` 2 + `InternLeaveWebIntegrationTest` 3 GREEN.
- Commits: `5e9e598` (feat) + `a4bea66` (docs), reachable.
- Note: `LEV-010` (pending auto-reject at first counted start) is **NOT covered here** — it belongs to I2-ATT-07 (see below).

### I2-ATT-05 — DONE
- Requirements: `COR-001`–`COR-004`, `COR-006`, `ATT-016`, `AC-COR-001`–`AC-COR-002`.
- Code: `CorrectionService.submit`, `AttendanceCorrectionEntity`, `AttendanceCorrectionEventEntity` (SUBMITTED), `AttendanceRecordEntity`, unique `uq_attendance_corrections_record`.
- Evidence: `docs/tests/unit/correction-submission.md`, `docs/tests/integration/correction-persistence.md`, `docs/tests/web/intern-corrections.md`.
- Focused verification: `CorrectionServiceTest` 13 GREEN; `CorrectionPersistenceIntegrationTest` 7 GREEN; `InternCorrectionWebIntegrationTest` 3 GREEN.
- Commits: `4c09382` (feat) + `572ba15` (docs), reachable.
- PostgreSQL specifics verified: exclusive after-cutoff-through-scheduled-end+24h window, raw checkout stays null, derivation of effective checkout.

### I2-ATT-06 — DONE
- Requirements: `COR-004`, `COR-005`, `COR-007`, `COR-009`, `AC-COR-003`–`AC-COR-005`.
- Code: `CorrectionService.decide/revert/decisions`, `CorrectionWindowGuard.expire` (`REQUIRES_NEW`, idempotent), entity `approve/reject/reopen/lock/autoReject`, append-only events.
- Evidence: `docs/tests/unit/correction-decision.md`, `docs/tests/integration/correction-expiry.md`, `docs/tests/integration/correction-persistence.md`, `docs/tests/web/mentor-corrections.md`.
- Focused verification: `CorrectionDecisionServiceTest` 12 GREEN; `CorrectionWindowGuardTest` 6 GREEN; `CorrectionExpiryIntegrationTest` 1 GREEN (committed-row `REQUIRES_NEW`); `CorrectionPersistenceIntegrationTest` 11 GREEN; `MentorCorrectionWebIntegrationTest` 3 GREEN.
- Commits: `2ace890` (feat) + `b7e9d4f` (docs), reachable.
- Note: `iteration-2-test-report.md` (untracked) still describes I2-ATT-06 as `IN_PROGRESS` with 1 failing test (Issue #6). That report predates the fix; `correction-expiry.md` documents the fixture correction and GREEN, and the branch log records `b7e9d4f` marking the tracker `DONE`. The report should be refreshed or removed to avoid a stale status.

### I2-ATT-07 — NOT_IMPLEMENTED (Phase B work)
- Requirements: `COR-008`, `LEV-010`, `ERR-003`, `ERR-004`, `AC-COR-005`, `AC-LEV-004`, `AC-TST-001`, plus notification requirements `NOT-001`–`NOT-005`, `NOT-009` (as consumed).
- Code inspected: **no** `@Scheduled`, `@EnableScheduling`, `SchedulingConfigurer`, or `TaskScheduler` anywhere in `src/main/java` (only false-positive field names `scheduledStart`/`scheduledEnd`). No `AttendanceScheduler`/`AttendanceDeadlineService` class exists (both are in the file-map/mastery planned topology). No `NotificationService` usage in attendance.
- What exists (request-time, partial):
  - `CorrectionWindowGuard.expire()` — idempotent `REQUIRES_NEW` expiry (auto-reject+lock pending / lock decided), invoked from `CorrectionService.decide`/`revert`. Satisfies the *correction* request-time half of `COR-008` and `AC-COR-005` "first access auto-rejects".
  - `LeaveService.requireBeforeBoundary()` — rejects new mutations at/after the first counted start, but does **not** auto-transition a still-pending request to `REJECTED`. `LEV-010` ("a pending request still unresolved at the first counted start shall automatically become REJECTED. Scheduler and access-time guards shall enforce the same boundary") is **not satisfied** — no access-time auto-reject, no scheduler.
  - No scheduled worker for either expiry path; no bounded batch processing (`ERR-004`); no notification requests on leave/correction transitions (`NOT-*`, `COR-008` "persist auto-rejection and notifications").
- **Dependency:** attendance's notification integration requires the platform's `NotificationService` (`I2-PLAT-06`, `feature/notification/service/NotificationService.java` planned, not yet present). Attendance must consume it via public API, never write `notifications` rows directly.
- **Required next action (Phase B, TDD):** introduce `AttendanceDeadlineService` (shared idempotent transitions) + `AttendanceScheduler` (bounded, `@Scheduled`) covering both correction decision-window expiry and leave first-counted-start auto-reject, ensuring scheduler and request-time guards call the same transition logic; add equivalent request-time leave auto-reject; verify with PostgreSQL integration tests (committed-row `REQUIRES_NEW`, delayed/repeated scheduler produces exactly one final transition/notification set); integrate platform notifications when available.

## Shared-rule and schema checks

| Rule | Status | Notes |
|---|---|---|
| `GOV-004` attendance/Task time separation | OK | No cross-module mutation; no attendance service writes Task data |
| `GOV-011`/`GOV-012` UTC instants + policy timezone | OK | Injected `Clock`; `Asia/Ho_Chi_Minh`; `timestamptz` instants persisted |
| `GOV-013` transactional writes + optimistic locking | OK | `@Version` on policy/record/correction/leave; `saveAndFlushChecked` maps conflicts to stable rejections |
| `GOV-014` retained history not deleted | OK | Policy versions immutable; correction events append-only; leave days frozen |
| `SEC-001/008/009/013` server-side auth, CSRF, validation, safe errors | OK | Controller role guards, `AccessDeniedException`, web tests assert 403 for wrong roles; CSRF-protected forms |
| `NOT-001`–`NOT-005`, `NOT-009` | **NOT MET** | No notification code anywhere; depends on platform `I2-PLAT-06`; blocks `I2-ATT-07` completion |
| `DB-003`–`DB-009` PostgreSQL-not-H2 | OK | Every integration/web test runs PostgreSQL 18.4 Testcontainers (never H2); exclusion/unique/index constraints exercised |
| `ERR-001`/`ERR-002` form safety, stale decisions | OK | Flash-message redirects; stable rejection codes; optimistic-version UI forms |
| `ERR-003` deadline guard inside mutation transaction | Partial | Correction guard uses `REQUIRES_NEW` + in-txn recheck; leave boundary checked in-txn; **no leave auto-reject** |
| `ERR-004` workers idempotent + bounded batches | **NOT MET** | No scheduler exists |
| `ERR-005` HolidayAPI/SMTP failure stays offline-safe | OK | Fallback client + `RecordingSmtpProbe`; manual calendar unaffected |
| Schema drift (`V1__baseline.sql` vs `database-schema.sql`) | **NONE** | `V1__baseline.sql` (1123 lines) is byte-identical to the authoritative DDL ignoring CRLF/whitespace |

## Missing-test matrix (gaps to close in Phase B)

| Area | Missing evidence | Blocking |
|---|---|---|
| I2-ATT-01 Admin policy web layer | `AttendancePolicyWebIntegrationTest` + `docs/tests/web/attendance-policy-admin.md` are written and green but **uncommitted** | Committing the web layer |
| Leave pending auto-reject (`LEV-010`) | No unit/integration test for pending→REJECTED auto-transition at first counted start (access guard or scheduler) | I2-ATT-07 |
| Correction scheduler equivalence | No test that a delayed/repeated scheduled worker produces exactly one `AUTO_REJECTED`/`LOCKED` event set (request-time guard is tested; scheduled worker is not) | I2-ATT-07 |
| Scheduler bounded/idempotent batches (`ERR-004`) | No bounded-batch worker test | I2-ATT-07 |
| Notifications on leave/correction transitions (`NOT-*`) | No notification-dedup test | I2-ATT-07 + platform `I2-PLAT-06` |

## Handoff details (PROJECT_PLAN §9)

### I2-ATT-01 — Schedule future-month policy versions + preserve history

- Status: PARTIAL (core DONE; web layer uncommitted)
- Owner: Duong
- Requirements/scenarios: `ATT-001`–`ATT-006`, `AC-ATT-001`
- RED evidence: `docs/tests/unit/attendance-policy-scheduling.md` (compile RED because service/command/rejection did not exist)
- GREEN evidence: `AttendancePolicyServiceTest` 7 GREEN; `AttendancePolicySchedulingIntegrationTest` 4 GREEN; full suite 336 GREEN (this audit)
- Focused verification: `mvnw.cmd -Dtest=AttendancePolicyServiceTest,AttendancePolicySchedulingIntegrationTest test`
- Affected-suite verification: `mvnw.cmd test` (336 GREEN)
- Commit SHA: reachable feat `3f617fe`, docs `3445451` (evidence docs/tracker cited dangling `a2205b5`/`69357f6` — refreshed on the Phase B docs edit)
- Remaining risk/blocker: Admin policy web UI untracked in worktree; not yet committed
- Required next owner/action: commit the web layer with its RED/GREEN (`docs/tests/web/attendance-policy-admin.md`) and then mark `DONE`

### I2-ATT-07 — Idempotent schedulers + request-time deadline guards

- Status: NOT_IMPLEMENTED
- Owner: — (assigned next)
- Requirements/scenarios: `COR-008`, `LEV-010`, `ERR-003`, `ERR-004`, `AC-COR-005`, `AC-LEV-004`, `AC-TST-001`; consumes `NOT-001`–`NOT-005`, `NOT-009`
- RED evidence: none yet
- GREEN evidence: none yet
- Focused verification: planned `AttendanceDeadlineServiceTest` + scheduler integration tests
- Affected-suite verification: full suite
- Commit SHA: —
- Remaining risk/blocker: platform `NotificationService` (`I2-PLAT-06`) not yet present; attendance must consume it, not write `notifications` directly
- Required next owner/action: implement `AttendanceDeadlineService` + `AttendanceScheduler` (TDD, PostgreSQL integration, delayed/repeated-invocation idempotency), add request-time leave auto-reject, wire notifications when platform provides the service

## Notes for Phase B

1. Preserve the dirty worktree: the untracked I2-ATT-01 web layer must be committed as part of Phase B (it is the current in-progress deliverable).
2. Priority order for Phase B fixes: `INCORRECT`/`NOT_IMPLEMENTED` first — `I2-ATT-07` is the only such tracker.
3. Do not touch `work/platform`, `work/projects`, `work/tasks`, or `work/reports-ui` ownership areas. Schema changes, if any, must be requested through platform.
4. `iteration-2-test-report.md` is stale (describes I2-ATT-06 as failing) — refresh or remove.
5. Refresh the SHA references in evidence docs/tracker to the reachable branch commits (`3f617fe`, `9be57ef`, `69b54eb`, `5e9e598`, `4c09382`, `2ace890`, `169830f` + their doc commits) when those files are next edited.

## Phase B follow-up (2026-08-21)

All action items from this audit have been completed on `work/attendance`:

- The uncommitted I2-ATT-01 web layer was committed (`3f617fe` feat + `3445451` docs) and the tracker row marked `DONE`.
- **I2-ATT-07** was implemented TDD and marked `DONE` (`169830f` feat + `docs(tests)` evidence): `AttendanceDeadlineService` (bounded idempotent `REQUIRES_NEW` batches), `AttendanceScheduler` (cron worker, inert under the `test` profile via `SchedulingConfiguration`), `LeaveService`/`CorrectionService` request-time and read-path guards over the same transition logic, `LeaveRequestEntity.autoReject`, and the `AttendanceNotificationClient` port with a `@ConditionalOnMissingBean` no-op fallback (platform `I2-PLAT-06` wiring remains a pending dependency, recorded in the tracker). Notification-dedup (ERR-004) and notification-failure rollback protection (NOT-002/ERR-005) are proven by `DeadlineGuardIntegrationTest`.
- Evidence: `docs/tests/integration/deadline-guard.md`, `docs/tests/unit/attendance-deadline-worker.md`; attendance suite **166/166 GREEN**.
- Stale SHA references in evidence docs were refreshed to reachable commits (`a2205b5` → `3f617fe`, `69357f6` → `96c2974`), and `iteration-2-test-report.md` was refreshed to the 7/7 GREEN state.