# Punches and results Spec

**Version:** 1.1.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

**Module:** `attendance` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Record attendance and derive the daily classification from the policy snapshot and retained punches.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. The split changes document ownership only.

## 2. Actors & Roles

Active Intern records attendance; authorized viewers read the result.

Use cases defined here: UC-08.

The [platform permission matrix](../../../platform/features/authorization/SPEC.md#52-permission-matrix)
and the module's shared authorization rules apply to every operation.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Check in

ATT-007 and ATT-008 govern eligibility, uniqueness and the server-recorded punch;
ATT-009 supplies the late-arrival boundary. Apply the shared policy, internship and
calendar guards as well.

### Check out

ATT-010 defines accepted checkout and its inclusive cutoff; ATT-011 defines early
departure and missing checkout; ATT-012 protects the raw punches from later edits.

### Classify and calculate

ATT-012 through ATT-018 define classifications, counted time, rates, penalties and terminal-date behavior.

### Canonical feature rules

| ID | Requirement |
|---|---|
| ATT-007 | WHILE an Intern account is `ACTIVE`, THE system SHALL permit at most one check-in per eligible workday that is not covered by approved leave. |
| ATT-008 | WHEN check-in is accepted, THE system SHALL store the server timestamp, the derived local work date, and the applied policy version in one transaction. WHERE the attempt falls on an off-day, on approved leave, duplicates an existing row, or comes from a non-active, completed, or withdrawn account, THE system SHALL reject it. |
| ATT-009 | WHERE `check_in > scheduled_start + check_in_grace`, THE system SHALL classify the day as late, and otherwise SHALL NOT. Under the seeded defaults, exactly 09:00:00 is on time and 09:00:00.001 is late. |
| ATT-010 | WHEN checkout is requested, THE system SHALL require that day's open attendance row and SHALL accept it once, only WHILE `server_now <= scheduled_end + checkout_grace` under the policy version attached to that row. The cutoff is inclusive: under the seeded defaults 16:00:00 is accepted and the first later instant is rejected. WHEN a checkout is accepted, THE system SHALL preserve the raw server timestamp. |
| ATT-011 | WHERE effective checkout falls before scheduled end, THE system SHALL record an early-departure violation. WHERE `server_now > scheduled_end + checkout_grace` and the row still has no effective checkout, THE system SHALL classify it as `MISSING_CHECKOUT` only and SHALL NOT also record early departure. After that cutoff THE system SHALL refuse normal checkout and SHALL NOT populate or overwrite the raw checkout. |
| ATT-012 | THE system SHALL NOT edit a raw check-in or raw checkout through any account, correction, or report operation. |
| ATT-013 | THE system SHALL exclude non-eligible dates from the attendance-rate and compliance denominators, including global days off and approved leave. |
| ATT-014 | THE system SHALL compute the attendance rate as `present eligible workdays / (eligible workdays − approved-leave workdays)`. WHERE that denominator is zero, THE system SHALL render `N/A`. |
| ATT-015 | WHERE a day is present, THE system SHALL compute daily compliance as `max(0, 1 − policy penalty × applicable violation count)`. WHERE an expected day is absent, THE system SHALL score it 0. WHERE a day is an off-day or approved leave, THE system SHALL give it no daily score. |
| ATT-016 | THE system SHALL count as applicable violations the late violation plus exactly one of early departure or missing checkout, leaving out a late arrival or early departure excused under `EXC-005`. WHEN a correction is approved, THE system SHALL recompute the effective checkout outcome without changing the historical policy penalty. |
| ATT-017 | THE system SHALL compute period compliance as the average daily score over expected workdays. WHERE a period contains no expected workday, THE system SHALL report `N/A`. |
| ATT-018 | WHEN a terminal internship timestamp is set, THE system SHALL create no further attendance obligation after that instant, and SHALL preserve any attendance already recorded on that local date. |

#### UC-08 — Check in and check out

**Part A1.**

| Field | Specification |
|---|---|
| Primary actor(s) | Active Intern |
| Trigger | An eligible Intern starts or ends an attendance day. |
| Preconditions | The local date is an eligible workday, not a global day off, and not covered by approved leave. |
| Postconditions | One immutable raw attendance record represents the date; reports derive authorized metrics from it. |
| Traced requirements | ATT-007–ATT-018 |

**Main success flow**

1. Open My attendance.
2. Submit check-in; the server records its own instant and attached historical policy.
3. Submit checkout at most once through that policy’s inclusive scheduled-end-plus-checkout-grace cutoff; the server records its own instant.
4. View derived classification and metrics.

**Alternatives and exceptions**

- Duplicate, off-day, leave-covered, ineligible-lifecycle, or uninitialized requests are rejected.
- Exactly scheduled start plus check-in grace is on time; any later instant is late.
- Under defaults, checkout at 16:00:00 succeeds and the first later instant is rejected.
- After cutoff with no effective checkout, the record has only MISSING_CHECKOUT; normal checkout stays closed and cannot change raw checkout.

## 4. Non-functional Requirements

Inherit [module constraints](../../MODULE.md#4-non-functional-requirements) and
[platform constraints](../../../platform/MODULE.md#4-non-functional-requirements),
including authorization, history, concurrency, server time and test evidence.

## 5. Data

`attendance_records`; shared policy snapshots and decision effects are described in MODULE.md §5.

The [module data contract](../../MODULE.md#5-data) retains ownership, constraints and
checks across features. The full physical model stays in platform §19. A feature folder
does not grant repository/entity access across the module boundaries of ARC-006.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order. PLAN resolves delivery predecessors against what
already exists and preserves ARC-005/ARC-006.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [calendar](../../../calendar/MODULE.md) | Applicable schedule and local day-off facts | [ATT-001](../../../calendar/features/attendance-policy/SPEC.md), [ATT-002](../../../calendar/features/attendance-policy/SPEC.md), [ATT-003](../../../calendar/features/attendance-policy/SPEC.md), [CAL-006](../../../calendar/features/global-calendar/SPEC.md), [CAL-007](../../../calendar/features/global-calendar/SPEC.md), [CAL-008](../../../calendar/features/global-calendar/SPEC.md), [CAL-009](../../../calendar/features/global-calendar/SPEC.md) |
| [internship/lifecycle](../../../internship/features/lifecycle/SPEC.md) | Active interval and terminal timestamp | [ACC-020](../../../internship/features/lifecycle/SPEC.md), [ACC-021](../../../internship/features/lifecycle/SPEC.md), [ACC-022](../../../internship/features/lifecycle/SPEC.md), [ACC-023](../../../internship/features/lifecycle/SPEC.md), [ACC-024](../../../internship/features/lifecycle/SPEC.md), [ACC-025](../../../internship/features/lifecycle/SPEC.md) |
| [attendance/leave](../leave/SPEC.md) | Effective leave-day allocations | [LEV-003](../leave/SPEC.md), [LEV-004](../leave/SPEC.md) |
| [attendance/missed-checkout-correction](../missed-checkout-correction/SPEC.md) | Confirmed effective checkout | [COR-006](../missed-checkout-correction/SPEC.md) |
| [attendance/attendance-exception](../attendance-exception/SPEC.md) | Current excuse flags | [EXC-005](../attendance-exception/SPEC.md) |
| [attendance/period-finalization](../period-finalization/SPEC.md) | Finalized-period guard | [ATT-021](../period-finalization/SPEC.md) |

### Related workflows and joint checks

- [Leave](../leave/SPEC.md): Approved leave affects obligations.
- [Missed-checkout correction](../missed-checkout-correction/SPEC.md): A confirmed time affects results.
- [Attendance exception](../attendance-exception/SPEC.md): Excuse flags affect compliance.
- [Period finalization and reopening](../period-finalization/SPEC.md): Finalized periods guard all mutations.

## 6. Error Handling

Apply each refusal, deadline, conflict and delivery-failure clause in the numbered rules
above, together with [module error handling](../../MODULE.md#6-error-handling).
Platform §21 supplies common failure behavior; an operation summary does not override it.

## 7. Acceptance Criteria

### Operation and acceptance map

This map traces existing behavior; its gap column does not define a new rule or
claim full test coverage. Actors and outcomes are summaries of the canonical rules.

| Operation | Actor and observable outcome | Canonical rules | Existing acceptance scenarios | Acceptance boundary or open decision |
|---|---|---|---|---|
| [Check in](#check-in) | Active Intern records one eligible server-timed check-in | [ATT-007](SPEC.md), [ATT-008](SPEC.md), [ATT-009](SPEC.md) | [AC-ATT-002](SPEC.md), [AC-ATT-003](SPEC.md) | Include duplicate concurrency and exact late-arrival boundary; completed/withdrawn accounts remain ineligible. |
| [Check out](#check-out) | Intern checks out within the inclusive cutoff without rewriting raw punches | [ATT-010](SPEC.md), [ATT-011](SPEC.md), [ATT-012](SPEC.md) | [AC-ATT-004](SPEC.md), [AC-ATT-005](SPEC.md) | Test exact cutoff and the first later instant with both default and zero grace. |
| [Classify and calculate](#classify-and-calculate) | Authorized reader receives the correct daily and period attendance values | [ATT-009](SPEC.md), [ATT-011](SPEC.md), [ATT-013](SPEC.md), [ATT-014](SPEC.md), [ATT-015](SPEC.md), [ATT-016](SPEC.md), [ATT-017](SPEC.md), [ATT-018](SPEC.md) | [AC-ATT-002](SPEC.md), [AC-ATT-005](SPEC.md), [AC-ATT-006](SPEC.md), [AC-ATT-007](SPEC.md), [AC-ATT-008](SPEC.md) | Combine correction, excuse, leave and terminal-date effects in addition to the existing hand-calculated cases. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-ATT-002 | ATT-007–ATT-009 | Intern checks in at 09:00:00 and another at 09:00:00.001 under defaults | First is on time; second is late. |
| AC-ATT-003 | ATT-007–ATT-008 | Intern attempts duplicate, off-day, approved-leave, or inactive check-in | Each is rejected and no additional attendance row exists. |
| AC-ATT-004 | ATT-010–ATT-012 | Under defaults, Intern checks out at 16:00:00 and retries at the first later instant; under zero checkout grace, Intern tries at 15:30:00 and the first later instant | Each exact cutoff succeeds once; every later or repeated attempt is rejected and cannot overwrite the first raw checkout. |
| AC-ATT-005 | ATT-011, ATT-016 | The attached-policy checkout cutoff passes with no raw checkout, then Intern attempts normal checkout | The day has `MISSING_CHECKOUT` only, not early departure too; the late attempt is rejected and raw checkout remains null. |
| AC-ATT-006 | ATT-013–ATT-017 | Period has 20 eligible days, one approved-leave day, 17 present, and two absent | Attendance rate is `17/19 = 89.47%`; compliance uses historical daily policy and absent days score zero. |
| AC-ATT-007 | ATT-014, ATT-017 | Filtered period has no expected workdays | Attendance and compliance display `N/A` without division error. |
| AC-ATT-008 | ATT-018 | An Intern checks in, the Admin completes the internship later the same local date, and the Intern attempts a second check-in the next workday | The already-recorded attendance row for the terminal date is retained unchanged and still appears in reports; the next-day check-in is refused because the lifecycle is terminal. |

Shared and cross-feature scenarios in [MODULE.md](../../MODULE.md#7-acceptance-criteria)
also apply. Scenario ownership follows the behavior exercised, not every prerequisite
named by its Requirements cell. Relocation preserves every existing expected result.

## 8. Out of Scope

Inherit [module exclusions](../../MODULE.md#8-out-of-scope). Adjacent feature behavior
is a dependency, not a duplicated contract. This split creates no new product behavior,
schema, dependency, PLAN.md or TASKS.md.

## Notes / Open Questions

No additional decision is introduced by this split. No question affecting this feature is open; the cross-feature checks and shared contracts in MODULE.md still apply.

Read [shared open questions](../../MODULE.md#notes--open-questions) before approving
the technical plan. [plan.md](../../../../../plan.md) is the only progress tracker.
