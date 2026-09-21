# Attendance Module

<a id="attendance-spec"></a>

**Version:** 1.8.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

Part of the Lab Timesheet specification. Rules every feature shares, including the
glossary, the authorization model, the domain model, and failure handling, are in
[the platform spec](../platform/MODULE.md). Section numbers marked `§` are one
numbering shared by all the specs, so a reference such as §5.2 names the same section
wherever it appears.

## 1. Context & Goal

Global attendance, leave, and missed-checkout correction, the second area named by the product objective (§1.2 of the platform spec).
Its module is `attendance` (`ARC-005`): punches, attendance periods, corrections, attendance exceptions, and leave. The effective-dated policy and the global calendar they apply are in [the calendar spec](../calendar/MODULE.md). `GOV-004` keeps it separate from Task work and `GOV-005` keeps its past results fixed; both are in the platform spec.

### Parts within attendance

These are business scopes for sections of one attendance `PLAN.md`, not new modules or
separate copies of requirements (`D33`). Each part includes its form, authorization,
transaction, history and acceptance evidence; it is not a controller/service/database layer.
Part labels organize work; rule and scenario identifiers remain the trace identifiers.
Progress is tracked only in [plan.md](../../../plan.md).

| Part and outcome | Rules in this spec | Use cases and acceptance scenarios |
|---|---|---|
| [A1 — Punches and results](MODULE.md#attendance-a1): record presence and derive daily/period metrics | `ATT-007`–`ATT-018` | `UC-08`; `AC-ATT-002`–`AC-ATT-008` |
| [A2 — Leave](MODULE.md#attendance-a2): allocate, decide and withdraw full-day leave | `LEV-001`–`LEV-013`, `DB-002` | `UC-10`; `AC-LEV-001`–`AC-LEV-008`, `AC-DB-005` |
| [A3 — Missed checkout](MODULE.md#attendance-a3): decide an effective checkout while retaining the raw record | `COR-001`–`COR-009` | `UC-09`; `AC-COR-001`–`AC-COR-006` |
| [A4 — Exceptions](MODULE.md#attendance-a4): decide whether a recorded violation is excused | `EXC-001`–`EXC-007`, `DB-016` | `UC-15`; `AC-EXC-001`–`AC-EXC-004` |
| [A5 — Finalization and reopening](MODULE.md#attendance-a5): close a month and control later changes to a named range | `ATT-019`–`ATT-023`, `DB-014`–`DB-015` | `UC-17`; `AC-ATT-009`–`AC-ATT-010` |

**Shared contracts, retained once:** applied policy and frozen allocations (`ATT-004`–`ATT-006`),
append-only decisions (`ATT-024`, `DB-017`), leave/correction states (`DB-018`), and
recipients (`NOT-011`). `AC-DB-006` checks integrity across A2–A5. These contracts belong
in every affected part's plan; A5 is not permission to postpone finalization guards in A2–A4.
The shared decision mechanism does not give leave the reversal actions of corrections or exceptions.


### Feature index

Read this shared contract together with the relevant feature SPEC. Rules are canonical
in exactly one of these documents; references do not create copies. Cross-feature use
cases, shared data constraints and unresolved decisions remain here (`D34`).

| Feature | Operations |
|---|---|
| [Punches and results](features/punches-and-results/SPEC.md) | Check in; Check out; Classify and calculate |
| [Leave](features/leave/SPEC.md) | Submit, edit or cancel; Decide and retain allocation |
| [Missed-checkout correction](features/missed-checkout-correction/SPEC.md) | Submit correction; Decide correction |
| [Attendance exception](features/attendance-exception/SPEC.md) | Request or mark an exception; Decide and amend |
| [Period finalization and reopening](features/period-finalization/SPEC.md) | Finalize period; Request and authorize reopening |

The earlier A/P/F labels remain navigation aliases for the same scopes, not extra features.

## 2. Actors & Roles

Primary actors named by this feature's use cases:

- **UC-08 — Check in and check out:** Active Intern
- **UC-09 — Correct a missed checkout:** Intern (submitter); the Intern's responsible Mentor (decision maker)
- **UC-10 — Request and decide leave:** Active Intern; the Intern's responsible Mentor
- **UC-15 — Excuse a late arrival or early departure:** Intern (requester); the Intern's responsible Mentor (decision maker, and the only actor who may mark an excuse without a request)
- **UC-17 — Reopen a finalized attendance period:** The Intern or their responsible Mentor (requester); Admin (approves or rejects the reopen, and decides nothing inside it); the responsible Mentor (acts in the reopened range and finalizes it again)

Every capability by role is in the permission matrix, [platform spec](../platform/MODULE.md) §5.2. Authorization is resolved from stored context, never from the global role alone (§5.1).

## 3. Functional Requirements

### §8. Global attendance policy and calendar

#### §8.1 Effective-dated policy

`ATT-001`–`ATT-003`, which define the policy, and the global calendar of §8.2 are in [the calendar spec](../calendar/MODULE.md).

| ID | Requirement |
|---|---|
| ATT-004 | WHERE a report covers a date with no attendance row, THE system SHALL resolve the immutable policy version effective on that date, so that historical absence and denominator calculations are preserved. |
| ATT-005 | WHEN check-in is accepted, THE system SHALL attach the applied policy version to that attendance row, and that version SHALL govern the row's check-in and checkout boundaries permanently. WHEN a leave request is submitted, THE system SHALL reference and snapshot the applicable policy and quota on each allocated day. |
| ATT-006 | WHEN a later policy or calendar change is scheduled, THE system SHALL leave existing leave-day allocations frozen, and SHALL apply the revised eligibility only to newly submitted requests. |

<a id="attendance-a1"></a>

### §9. Attendance records and metrics

**Part A1 — Punches and results.**

#### §9.1 Check-in and checkout

Feature contracts: [Punches and results](features/punches-and-results/SPEC.md).

#### §9.2 Daily classification and formulas

For an applicable Intern/date, classification precedence is:

1. `HOLIDAY` when an imported day-off event applies, otherwise `OFF_DAY` for another global/configured off-day;
2. `APPROVED_LEAVE`;
3. `PRESENT` when an attendance record exists, including a missing-checkout record;
4. `ABSENT` for the remaining eligible workday.

Feature contracts: [Punches and results](features/punches-and-results/SPEC.md).

<a id="attendance-a2"></a>

### §11. Leave

**Part A2 — Leave.**

Feature contracts: [Leave](features/leave/SPEC.md).

#### State transitions: leave request

Canonical workflow: [feature contract](features/leave/SPEC.md).

<a id="attendance-a3"></a>

### §10. Missed-checkout corrections

**Part A3 — Missed checkout.**

Feature contracts: [Missed-checkout correction](features/missed-checkout-correction/SPEC.md).

#### State transitions: missed-checkout correction

Canonical workflow: [feature contract](features/missed-checkout-correction/SPEC.md).

<a id="attendance-a4"></a>

### Attendance exceptions

**Part A4 — Exceptions.**

Feature contracts: [Attendance exception](features/attendance-exception/SPEC.md).

#### State transitions: attendance exception

Canonical workflow: [feature contract](features/attendance-exception/SPEC.md).

<a id="attendance-a5"></a>

### Attendance periods and finalization

**Part A5 — Finalization and reopening; shared decision history follows in `ATT-024`.**

| ID | Requirement |
|---|---|
| ATT-024 | WHERE a leave, correction, or attendance exception rule permits a decision to be amended or reversed, THE system SHALL record the amendment or reversal as a new decision entry carrying its kind, the actor, the server time, and a nonblank reason, SHALL treat the latest effective entry as the current decision, SHALL derive every result from it, and SHALL keep the request and every earlier entry unchanged. THE system SHALL NOT return a decided request to `PENDING`, and SHALL require a new request under the rules of its own kind for any change that needs a new approval. WHILE a period the decision touches is finalized, THE system SHALL refuse every amendment or reversal except inside a range reopened under `ATT-022`. This rule shares the history and finalization mechanism only; which amendments and reversals each kind permits is set by `LEV-011`, `COR-005`, and `EXC-007`. |

Feature contracts: [Period finalization and reopening](features/period-finalization/SPEC.md).

#### State transitions: attendance period

Canonical workflow: [feature contract](features/period-finalization/SPEC.md).

#### State transitions: request to reopen a finalized period

Canonical workflow: [feature contract](features/period-finalization/SPEC.md).

### Notifications (from notification §12.2)

| ID | Requirement |
|---|---|
| NOT-011 | WHEN leave, a correction, or an attendance exception request is submitted, THE system SHALL notify the Intern's responsible Mentor under `ACC-026`, and WHEN any such request becomes overdue, SHALL notify that Mentor again. WHEN such a request is decided, or its decision is amended or reversed under `ATT-024`, or a late arrival or early departure is marked excused without a request, THE system SHALL notify the Intern. WHEN a request to reopen a finalized attendance period is made under `ATT-022`, THE system SHALL notify every active user whom the authorization policy of `AUTH-012` permits to approve or reject it, and SHALL NOT choose those recipients by role; under the §5.2 matrix they are the active Admins today. WHEN it is approved or rejected, THE system SHALL notify the requester and, where different, the Intern's responsible Mentor. |

### Integrity (from platform §19.3)

| ID | Requirement |
|---|---|
| DB-017 | THE schema SHALL store every decision, amendment and reversal on a correction, an attendance exception, and a leave request as an append-only entry carrying its kind, the outcome and note it sets, for a leave amendment the dates it withdraws from approval, the actor, the server time and, for an amendment or reversal, a nonblank reason, and SHALL refuse an update or deletion of such an entry. WHILE a correction, an attendance exception or a leave request has a decision entry and has not been cancelled, its status SHALL equal the outcome of its latest effective decision entry. THE system SHALL NOT record a new correction event of kind `AUTO_REJECTED` or `LOCKED`. |
| DB-018 | THE schema SHALL constrain a leave request's status to `PENDING`, `OVERDUE`, `APPROVED`, `REJECTED`, `WITHDRAWN` or `CANCELLED`, and a correction's status to `PENDING`, `OVERDUE`, `APPROVED` or `REJECTED`. THE schema SHALL mark a leave request day whose approval an amendment withdrew, without changing the policy version or monthly quota snapshot that day carries. |

Feature contracts: [Leave](features/leave/SPEC.md), [Period finalization and reopening](features/period-finalization/SPEC.md), [Attendance exception](features/attendance-exception/SPEC.md).

### Use cases

#### UC-08 — Check in and check out

Canonical workflow: [feature contract](features/punches-and-results/SPEC.md).

#### UC-09 — Correct a missed checkout

Canonical workflow: [feature contract](features/missed-checkout-correction/SPEC.md).

#### UC-10 — Request and decide leave

Canonical workflow: [feature contract](features/leave/SPEC.md).

#### UC-15 — Excuse a late arrival or early departure

Canonical workflow: [feature contract](features/attendance-exception/SPEC.md).

#### UC-17 — Reopen a finalized attendance period

Canonical workflow: [feature contract](features/period-finalization/SPEC.md).

## 4. Non-functional Requirements

System-wide non-functional rules apply unchanged: architecture §3 (`ARC`), authentication and security §13 (`SEC`), interface and accessibility §15 (`UI`), delivery §16 (`OPS`), and test evidence §17 (`TST`), all in the [platform spec](../platform/MODULE.md).

## 5. Data

All rows below are owned by the attendance module. A part is not a new data owner.

| Part | Owned records used by the part | Inputs and dependencies |
|---|---|---|
| A1 | `attendance_records` | Calendar-owned immutable policy versions and eligible dates; internship interval; approved leave from A2; effective checkout from A3; current exception decision from A4 |
| A2 | `leave_requests`, `leave_request_days`, `leave_request_decisions` | Calendar policy/quota snapshots; internship interval and responsible Mentor; A5 period guards and shared decision history |
| A3 | `attendance_corrections`, `attendance_correction_events` | A1 record and its attached policy; responsible Mentor; A5 period guards and shared decision history |
| A4 | `attendance_exceptions`, `attendance_exception_decisions` | A1 classifications, including the effective checkout from A3; responsible Mentor; A5 guards and shared decision history |
| A5 | `attendance_periods`, `attendance_period_reopens` | Requests and dated results from A1–A4; platform authorization for reopen; notification delivery |

Dependencies above describe required facts, not permission for cross-module repository access
or reciprocal service imports (`ARC-005`, `ARC-006`). The plan resolves collaboration inside
attendance and uses the published interfaces of calendar, internship and notification.

### Checks across parts

| Boundary | Existing evidence | What the plan must carry through |
|---|---|---|
| Calendar → A1/A2 | `AC-ATT-001` in the [calendar spec](../calendar/MODULE.md) | Old attendance cutoffs, results and leave allocations remain tied to their applied policy after an Admin change. |
| A3 → A1 → A4 | `AC-COR-004`, `AC-EXC-001`–`AC-EXC-003` cover the individual behaviors | Compose approval of an early effective checkout with its exception decision; keep raw checkout null and the early classification visible while computing compliance from the current decision. The combined sequence is not yet one acceptance scenario. |
| A2 → A5 | `AC-LEV-001`, `AC-ATT-009`–`AC-ATT-010` cover allocation and period controls separately | Exercise a leave request spanning two periods when one is finalized; enforce the guards of `LEV-008`, `LEV-011`, `LEV-013` and `ATT-021` across every affected date. The combined case still needs explicit test evidence. |
| A2/A3/A4 → A5 | `AC-ATT-009`, `AC-DB-006`; each request's overdue scenarios | Verify finalization against each pending/overdue request kind and race its last decision with the worker; retain one current result, append-only history and the recipients of `NOT-011`. Existing scenario counts alone do not prove that race is covered. |

The conceptual model, the table inventory, the integrity rules (`DB`), and both diagrams are §19 of the [platform spec](../platform/MODULE.md).

## 6. Error Handling

Rules in section 3 whose text names a refusal, rejection, denial, or failure: `ATT-008`, `ATT-010`, `ATT-011`, `ATT-021`, `ATT-022`, `ATT-023`, `ATT-024`, `COR-005`, `COR-007`, `COR-008`, `COR-009`, `LEV-004`, `LEV-006`, `LEV-008`, `LEV-010`, `LEV-012`, `EXC-006`, `DB-017`.

System-wide failure behavior is §21 (`ERR-001`–`ERR-007`), and the interface message families are Appendix F, both in the [platform spec](../platform/MODULE.md).

## 7. Acceptance Criteria

Scenarios from the §20 acceptance catalogue whose identifiers start with `AC-ATT`, `AC-COR`, `AC-LEV`, `AC-EXC` or `AC-DB`. The catalogue's introduction, including the rules deliberately written without a scenario, is in the [platform spec](../platform/MODULE.md).

### A1 — Punches and results

Feature contracts: [Punches and results](features/punches-and-results/SPEC.md).

### A2 — Leave

Feature contracts: [Leave](features/leave/SPEC.md).

### A3 — Missed checkout

Feature contracts: [Missed-checkout correction](features/missed-checkout-correction/SPEC.md).

### A4 — Exceptions

Feature contracts: [Attendance exception](features/attendance-exception/SPEC.md).

### A5 — Finalization and reopening

Feature contracts: [Period finalization and reopening](features/period-finalization/SPEC.md).

### Integrity shared by A2–A5

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-DB-006 | DB-014–DB-018 | SQL probes insert a second period for the same Intern and month, a reopen request with a blank reason, a second exception for the same attendance record and violation kind, an update and a deletion of a decision entry, a correction amendment or reversal without a reason, a status value no rule names, leave requests `OVERDUE` and `WITHDRAWN`, a correction `OVERDUE`, and a change to the policy snapshot of a withdrawn leave day | The duplicate period, blank reason, duplicate exception, update, deletion, reasonless correction amendment or reversal, unknown status and snapshot change are refused by the database; both new leave statuses and the overdue correction commit. |

## 8. Out of Scope

System-wide exclusions are §1.3 of the [platform spec](../platform/MODULE.md): `GOV-007` through `GOV-010` and `GOV-015`.

Exclusions stated inside this spec's own rules: `LEV-001`.

## Notes / Open Questions

The feature split is organizational; read the feature-specific notes as well as the shared
notes below. No question affecting this module is open (`D38`). Should one be recorded here
later, this module and every feature it affects return to the inherited-baseline status.


- `COR-006`: approving a correction confirms the effective checkout only; a checkout before scheduled end remains an early departure under `ATT-011`.
- `D14` covers `EXC-001`–`EXC-007`, leave withdrawal under `LEV-013`, approved leave under `LEV-011`, and the monthly periods and shared decision-history mechanism of `ATT-019`–`ATT-024`. Enterprise-backed: the fact kept apart from the approval; approval by a responsible Mentor; overdue never meaning rejected; one mechanism for decision history and finalization reused by leave, corrections, and exceptions, each keeping its own actions and states; withdrawal by the requester before a decision; no silent reversal of approved leave; reopening decided on data-governance grounds only; reassignment when the approver is unavailable. Laboratory policy, not a standard: the 48-hour submission and decision limits shared by corrections and exceptions (`D23`), the five-day finalization grace, leaving excused violations out of compliance, and recalculating attendance and compliance when an amendment leaves a past date without leave.
- `NOT-011` follows `D14`. It notifies every active user the authorization policy lets decide a reopen request, not every Admin by role.
