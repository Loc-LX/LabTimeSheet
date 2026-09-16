# Attendance Spec

**Version:** 1.3.4 · **Owner:** Loc-LX · **Status:** APPROVED · **Date:** 2026-09-16

Part of the Lab Timesheet specification. Rules every feature shares, including the
glossary, the authorization model, the domain model, and failure handling, are in
[the platform spec](../feature-platform/SPEC.md). Section numbers marked `§` are the
numbers the rules carried in the single-file specification and are kept so existing
references still resolve.

## 1. Context & Goal

Global attendance, leave, and missed-checkout correction, the second area named by the product objective (§1.2 of the platform spec).
In code this is `feature/attendance`: punches, effective-dated policy versions, the global calendar, leave, and corrections. `GOV-004` keeps it separate from Task work and `GOV-005` keeps its past results fixed; both are in the platform spec.

## 2. Actors & Roles

Primary actors named by this feature's use cases:

- **UC-04 — Configure policy, calendar, and integrations:** Admin
- **UC-08 — Check in and check out:** Active Intern
- **UC-09 — Correct a missed checkout:** Intern (submitter); the Intern's responsible Mentor (decision maker)
- **UC-10 — Request and decide leave:** Active Intern; the Intern's responsible Mentor

Every capability by role is in the permission matrix, [platform spec](../feature-platform/SPEC.md) §5.2. Authorization is resolved from stored context, never from the global role alone (§5.1).

## 3. Functional Requirements

### §8. Global attendance policy and calendar

#### §8.1 Effective-dated policy

| ID | Requirement |
|---|---|
| ATT-001 | THE system SHALL maintain one effective-dated global attendance-policy timeline managed by an Admin. THE system SHALL store, in each version, the timezone, scheduled start and end, check-in grace minutes, checkout grace minutes, monthly leave quota, violation penalty, and configured ISO weekdays. THE system SHALL expose a read-only policy History built from those retained versions and their non-secret creator and effective metadata. |
| ATT-002 | THE system SHALL seed one policy version effective `1970-01-01` with timezone `Asia/Ho_Chi_Minh`, Monday through Friday, 08:30 to 15:30, 30-minute check-in grace, 30-minute checkout grace, 3 leave workdays per month, and a 0.25 penalty per applicable violation. |
| ATT-003 | THE system SHALL require each grace value to be an integer from 0 through 720 minutes, and SHALL require scheduled end plus checkout grace to fall strictly before the next local midnight, so that an overnight schedule cannot be configured. THE system SHALL require the monthly leave quota to be an integer from 0 through 4, because four days is the largest whole number that stays within one fifth of the shortest twenty-workday month. THE system SHALL require a new policy version to begin on the first day of a future calendar month, SHALL collect that month in the Admin form and derive its first day on the server before applying the same validation, SHALL permit a not-yet-effective version to be replaced, and SHALL treat an effective version as immutable. |
| ATT-004 | WHERE a report covers a date with no attendance row, THE system SHALL resolve the immutable policy version effective on that date, so that historical absence and denominator calculations are preserved. |
| ATT-005 | WHEN check-in is accepted, THE system SHALL attach the applied policy version to that attendance row, and that version SHALL govern the row's check-in and checkout boundaries permanently. WHEN a leave request is submitted, THE system SHALL reference and snapshot the applicable policy and quota on each allocated day. |
| ATT-006 | WHEN a later policy or calendar change is scheduled, THE system SHALL leave existing leave-day allocations frozen, and SHALL apply the revised eligibility only to newly submitted requests. |

#### §8.2 Global calendar and HolidayAPI

| ID | Requirement |
|---|---|
| CAL-001 | THE system SHALL permit only an Admin to create or edit a global calendar event, whether custom or imported. THE system SHALL NOT provide project-level calendar overrides. |
| CAL-002 | THE system SHALL treat the HolidayAPI integration as optional and fixed to country `VN`, and SHALL call it only from an explicit Admin preview or import action. THE system SHALL NOT call it from attendance, leave, a dashboard, or a report. |
| CAL-003 | WHEN an import preview is produced, THE system SHALL preserve the source UUID, name, actual date, observed date, public-holiday marker, import timestamp, and the non-secret provenance the Admin Calendar History needs. WHERE a candidate carries `public=true`, THE system SHALL preselect the local `is_day_off` choice without forcing it. |
| CAL-004 | THE system SHALL require an Admin to review and explicitly select the rows to import, SHALL copy the selected data locally, and SHALL NOT silently overwrite an existing source UUID. |
| CAL-005 | WHERE the HolidayAPI key is absent, invalid, rate-limited, or the service is unavailable, THE system SHALL keep manual custom calendar entry available. |
| CAL-006 | THE system SHALL treat a calendar date as globally exempt WHERE at least one local event on that date carries `is_day_off=true`. THE system SHALL display a non-day-off observance without letting it affect attendance or quota. |
| CAL-007 | WHILE a global event's calendar date has passed, THE system SHALL treat that event as immutable. THE system SHALL permit a future event to change under optimistic locking with an impact preview. THE system SHALL expose retained past and current event metadata through a Calendar History view without exposing any integration secret. |
| CAL-008 | WHEN a leave request is submitted, THE system SHALL treat a global day off as waiving attendance obligation, absence classification, compliance penalty, and quota consumption. THE system SHALL leave already-materialized leave-day allocations frozen under `ATT-006`, and SHALL disclose any such reservation in the future-calendar impact preview rather than rewriting it silently. |
| CAL-009 | WHILE a date is a global day off, THE system SHALL refuse attendance check-in on it and SHALL refuse creating or moving a Task due date onto it. THE system SHALL continue to permit voluntary Task comments, status changes, and work logs on that date. |

HolidayAPI field behavior is based on its [official API documentation](https://holidayapi.com/docs).

### §9. Attendance records and metrics

#### §9.1 Check-in and checkout

| ID | Requirement |
|---|---|
| ATT-007 | WHILE an Intern account is `ACTIVE`, THE system SHALL permit at most one check-in per eligible workday that is not covered by approved leave. |
| ATT-008 | WHEN check-in is accepted, THE system SHALL store the server timestamp, the derived local work date, and the applied policy version in one transaction. WHERE the attempt falls on an off-day, on approved leave, duplicates an existing row, or comes from a non-active, completed, or withdrawn account, THE system SHALL reject it. |
| ATT-009 | WHERE `check_in > scheduled_start + check_in_grace`, THE system SHALL classify the day as late, and otherwise SHALL NOT. Under the seeded defaults, exactly 09:00:00 is on time and 09:00:00.001 is late. |
| ATT-010 | WHEN checkout is requested, THE system SHALL require that day's open attendance row and SHALL accept it once, only WHILE `server_now <= scheduled_end + checkout_grace` under the policy version attached to that row. The cutoff is inclusive: under the seeded defaults 16:00:00 is accepted and the first later instant is rejected. WHEN a checkout is accepted, THE system SHALL preserve the raw server timestamp. |
| ATT-011 | WHERE effective checkout falls before scheduled end, THE system SHALL record an early-departure violation. WHERE `server_now > scheduled_end + checkout_grace` and the row still has no effective checkout, THE system SHALL classify it as `MISSING_CHECKOUT` only and SHALL NOT also record early departure. After that cutoff THE system SHALL refuse normal checkout and SHALL NOT populate or overwrite the raw checkout. |
| ATT-012 | THE system SHALL NOT edit a raw check-in or raw checkout through any account, correction, or report operation. |

#### §9.2 Daily classification and formulas

For an applicable Intern/date, classification precedence is:

1. `HOLIDAY` when an imported day-off event applies, otherwise `OFF_DAY` for another global/configured off-day;
2. `APPROVED_LEAVE`;
3. `PRESENT` when an attendance record exists, including a missing-checkout record;
4. `ABSENT` for the remaining eligible workday.

| ID | Requirement |
|---|---|
| ATT-013 | THE system SHALL exclude non-eligible dates from the attendance-rate and compliance denominators, including global days off and approved leave. |
| ATT-014 | THE system SHALL compute the attendance rate as `present eligible workdays / (eligible workdays − approved-leave workdays)`. WHERE that denominator is zero, THE system SHALL render `N/A`. |
| ATT-015 | WHERE a day is present, THE system SHALL compute daily compliance as `max(0, 1 − policy penalty × applicable violation count)`. WHERE an expected day is absent, THE system SHALL score it 0. WHERE a day is an off-day or approved leave, THE system SHALL give it no daily score. |
| ATT-016 | THE system SHALL count as applicable violations the late violation plus exactly one of early departure or missing checkout, leaving out a late arrival or early departure excused under `EXC-005`. WHEN a correction is approved, THE system SHALL recompute the effective checkout outcome without changing the historical policy penalty. |
| ATT-017 | THE system SHALL compute period compliance as the average daily score over expected workdays. WHERE a period contains no expected workday, THE system SHALL report `N/A`. |
| ATT-018 | WHEN a terminal internship timestamp is set, THE system SHALL create no further attendance obligation after that instant, and SHALL preserve any attendance already recorded on that local date. |

### §10. Missed-checkout corrections

| ID | Requirement |
|---|---|
| COR-001 | THE system SHALL permit only the owning Intern to request a correction, only WHERE the attendance row has no raw checkout, and only after the checkout cutoff of the policy version attached to that row has passed. THE system SHALL permit at most one correction request per attendance row. |
| COR-002 | THE system SHALL require the owning Intern to supply a proposed checkout and a nonblank reason, and SHALL require that proposed checkout to be after check-in, on the original local work date, and not in the future at submission. |
| COR-003 | THE system SHALL accept a submission through the inclusive deadline `scheduled end on the attendance date + 48 hours`, resolved from the attached historical policy version, and only while the attendance period of that date is not finalized. THE system SHALL anchor that deadline to scheduled end rather than to the checkout cutoff; under the seeded defaults it falls at 15:30 two days later. The 48-hour limit is a provisional laboratory policy shared with `EXC-002`. |
| COR-004 | WHEN a submission is accepted, THE system SHALL open a separate 48-hour decision window measured from `submitted_at`, the same window `EXC-003` gives an exception request. |
| COR-005 | WHILE a request is pending or overdue and its period is not finalized, THE system SHALL permit the Intern's responsible Mentor under `ACC-026` to approve or reject it. WHILE a decided request's period is not finalized, THE system SHALL permit that Mentor to amend the decision or reverse it between approved and rejected under `ATT-024`; an amendment SHALL NOT change the proposed checkout. THE system SHALL write an immutable correction event for every transition, amendment, and reversal. |
| COR-006 | WHEN a correction is approved, THE system SHALL leave the raw checkout null and use the proposed checkout only as the effective checkout. THE system SHALL then clear the missing-checkout classification, and WHERE the effective checkout falls before scheduled end, THE system SHALL record an early-departure violation under `ATT-011`. |
| COR-007 | WHEN the decision window expires and a request is still pending, THE system SHALL mark it `OVERDUE` without rejecting it, because the approver rather than the Intern missed the deadline. THE system SHALL NOT lock a decided correction when the window expires. WHERE the current decision changes under `ATT-024`, THE system SHALL derive the effective checkout and the classifications of `COR-006` from the new current decision. |
| COR-008 | THE system SHALL persist the overdue marking and its reminder through a scheduled worker, and SHALL apply the same deadline guard on every correction read and write path before acting. |
| COR-009 | WHILE the attendance period of a correction is finalized, THE system SHALL refuse any Mentor state change except inside a range reopened under `ATT-022`. THE system SHALL NOT permit an Admin, or any Mentor other than the responsible Mentor, to decide, amend, or reverse a correction. |

### §11. Leave

| ID | Requirement |
|---|---|
| LEV-001 | THE system SHALL represent leave as a full-day inclusive date range with a nonblank reason. THE system SHALL NOT provide a leave type or a seven-day advance-notice rule in v1. |
| LEV-002 | THE system SHALL require a leave request to fall within the Intern's applicable internship interval and to contain at least one eligible workday after excluding configured non-workdays and global days off. |
| LEV-003 | WHEN a leave request is submitted, THE system SHALL materialize each quota-consuming date with its policy version, calendar month, and monthly quota snapshot. WHERE a request spans months, THE system SHALL allocate each date to its own month, and SHALL show the Intern those frozen allocations grouped by quota month. |
| LEV-004 | THE system SHALL reserve quota for `PENDING`, `OVERDUE`, and `APPROVED` leave days, and SHALL release it WHEN a request becomes `REJECTED`, `WITHDRAWN`, or `CANCELLED`, and for each date an amendment under `LEV-011` leaves without approved leave. For a selected quota month THE system SHALL show the Intern `reserved / applicable quota / remaining`, where remaining is `max(0, quota − reserved)`. THE system SHALL default the dashboard to the current business month and SHALL permit month selection in My Leave. |
| LEV-005 | WHEN quota is validated, THE system SHALL include existing pending, overdue, and approved allocations together with the candidate request, and SHALL serialize on the Intern profile so that concurrent submissions cannot overbook. |
| LEV-006 | WHERE a `PENDING`, `OVERDUE`, or `APPROVED` inclusive date range would overlap another for the same Intern, THE system SHALL reject it in both the application and the database. |
| LEV-007 | WHILE a request is pending and the scheduled start of its first counted workday has not passed, THE system SHALL permit the owning Intern to edit it. WHEN an edit is submitted, THE system SHALL revalidate overlap, frozen day allocations, and quota in one transaction. |
| LEV-008 | WHILE a request is pending before that same boundary, or overdue after it, and no period it touches is finalized, THE system SHALL permit the Intern's responsible Mentor under `ACC-026` to approve or reject it. THE system SHALL NOT permit an Admin to decide leave. |
| LEV-009 | THE system SHALL accept a same-day submission before the scheduled start of the first counted workday. Under the seeded defaults, a request whose first counted date is today is valid before 08:30 and invalid at or after 08:30. |
| LEV-010 | WHEN the first counted start is reached and a request submitted in time is still pending, THE system SHALL mark it `OVERDUE`, SHALL keep its quota reservation, and SHALL NOT reject it, because the approver rather than the Intern missed the deadline. THE system SHALL enforce that boundary from both the scheduler and the access-time guard. |
| LEV-011 | WHILE the first counted start has not passed, THE system SHALL permit the owning Intern to cancel an approved request, which becomes `CANCELLED`. WHEN leave begins, THE system SHALL freeze the request's date range and its materialized day allocation. THE system SHALL NOT reverse a leave decision. WHILE an approved request has begun and no period it touches is finalized, THE system SHALL permit the Intern's responsible Mentor under `ACC-026` to amend it under `ATT-024` only by withdrawing approval from one or more of its dates, never by adding a date or changing the frozen allocation. WHERE an amendment leaves a date without approved leave, THE system SHALL release that date's quota, SHALL NOT create or change an attendance record, and SHALL classify the date as a date without leave. Recalculating attendance and compliance for such a date from the current decision is a provisional laboratory policy. |
| LEV-012 | THE system SHALL NOT create leave retroactively. WHILE the first counted start of a request has passed, THE system SHALL refuse every change to it except a decision on an `OVERDUE` request under `LEV-008`, a withdrawal under `LEV-013`, and an amendment under `LEV-011`. |
| LEV-013 | WHILE a request is `PENDING` or `OVERDUE` and no attendance period it touches is finalized, THE system SHALL permit the owning Intern to withdraw it. WHEN a request is withdrawn, THE system SHALL mark it `WITHDRAWN`, SHALL release its quota reservation and its overlap blocking, SHALL keep the request, its day allocations, and its history unchanged, and SHALL NOT delete or change any attendance record; each of its dates SHALL be classified as a date without leave. |

### Attendance exceptions

| ID | Requirement |
|---|---|
| EXC-001 | THE system SHALL keep a late arrival or an early departure recorded exactly as `ATT-009` and `ATT-011` classify it, and SHALL hold whether it is excused as a separate decision that never clears the classification and never changes the raw or effective times. |
| EXC-002 | WHEN an Intern requests that their own recorded late arrival or early departure be excused, THE system SHALL require a nonblank reason, SHALL accept the request only through 48 hours after the scheduled end of that work date and only while the attendance period of that work date is not finalized, and SHALL keep at most one request per attendance row and violation kind. The 48-hour limit is a provisional laboratory policy shared with `COR-003`. |
| EXC-003 | WHILE a request is pending or overdue and the attendance period of its work date is not finalized, THE system SHALL permit only the Intern's responsible Mentor under `ACC-026` to decide it as excused or unexcused, and SHALL retain the decider, the server time, and any decision note. WHEN 48 hours pass after submission without a decision, THE system SHALL mark the request overdue and SHALL NOT treat it as excused or unexcused; only a decision by the responsible Mentor makes it either. The 48-hour limit is a provisional laboratory policy shared with `COR-004`. |
| EXC-004 | WHEN the responsible Mentor marks an Intern's recorded late arrival or early departure excused without a request, THE system SHALL require a nonblank reason, SHALL accept the mark only while the attendance period of that work date is not finalized under `ATT-020`, and SHALL retain the Mentor and the server time. THE system SHALL NOT set a separate deadline of its own; the period is the boundary. |
| EXC-005 | WHERE the current decision on a late arrival or early departure is excused, THE system SHALL NOT count it among the applicable violations of `ATT-016`, and SHALL still show it, marked excused, in attendance history, reports, and statistics. Leaving an excused violation out of compliance is a provisional laboratory policy. |
| EXC-006 | THE system SHALL refuse an exception decision or mark by an Intern Leader, an Admin, or any Mentor other than the Intern's responsible Mentor. |
| EXC-007 | WHILE the attendance period of the work date is not finalized, THE system SHALL permit the responsible Mentor to amend an exception decision or mark, or reverse it between excused and unexcused, under `ATT-024`. WHERE the current decision changes, THE system SHALL count the violation under `EXC-005` from the new current decision and SHALL leave the classification of `EXC-001` unchanged. |

### Attendance periods and finalization

| ID | Requirement |
|---|---|
| ATT-019 | THE system SHALL hold each Intern's attendance in monthly attendance periods, one per calendar month in the business timezone. A period covers the attendance rows, leave days, corrections, and attendance exceptions dated in that month; a leave request spanning months belongs to every period it touches. |
| ATT-020 | WHEN 23:59 on the fifth day of the following month is reached, THE system SHALL finalize an Intern's period unless a leave, correction, or attendance exception request affecting that period is pending or overdue. WHERE such a request remains, THE system SHALL keep the period open and SHALL finalize it as soon as the last such request is decided or withdrawn. The five-day grace is a provisional laboratory policy (`D24`). |
| ATT-021 | WHILE a period or a reopened range within it is finalized, THE system SHALL refuse every change to the attendance results it covers, including every decision or decision change on leave, corrections, and attendance exceptions and every leave withdrawal or cancellation, and SHALL refuse new requests for its dates. |
| ATT-022 | WHEN the Intern's responsible Mentor or the Intern asks to reopen a finalized period, THE system SHALL require a nonblank reason and the attendance records or date range concerned, and SHALL retain the requester and the server time. WHILE the request is undecided, THE system SHALL permit an Admin to approve or reject it, deciding only whether to reopen, from the reason, the records or range requested, and the data-governance and finalization rules. WHEN an Admin approves it, THE system SHALL reopen only those records or that range and SHALL retain the Admin and the server time. WHEN an Admin rejects it, THE system SHALL require a nonblank reason, SHALL retain the Admin, the server time, and that reason, and SHALL leave the period finalized. THE system SHALL NOT let the Admin approve, reject, correct, or decide any leave, correction, or attendance exception. |
| ATT-023 | WHILE a range is reopened, THE system SHALL permit only the Intern's responsible Mentor under `ACC-026` to approve, reject, correct, decide, or reverse within it under the rules that applied before finalization, and SHALL let that Mentor finalize the range again. WHEN the range is finalized again, THE system SHALL retain the Mentor and the server time. |
| ATT-024 | WHERE a leave, correction, or attendance exception rule permits a decision to be amended or reversed, THE system SHALL record the amendment or reversal as a new decision entry carrying its kind, the actor, the server time, and a nonblank reason, SHALL treat the latest effective entry as the current decision, SHALL derive every result from it, and SHALL keep the request and every earlier entry unchanged. THE system SHALL NOT return a decided request to `PENDING`, and SHALL require a new request under the rules of its own kind for any change that needs a new approval. WHILE a period the decision touches is finalized, THE system SHALL refuse every amendment or reversal except inside a range reopened under `ATT-022`. This rule shares the history and finalization mechanism only; which amendments and reversals each kind permits is set by `LEV-011`, `COR-005`, and `EXC-007`. |

### Integrity (from platform §19.3)

| ID | Requirement |
|---|---|
| DB-002 | THE schema SHALL enable `btree_gist` and SHALL use an exclusion constraint so that a pending, overdue, or approved leave range cannot overlap another for the same Intern. |
| DB-009 | THE schema seed SHALL create the `1970-01-01` policy version and ISO workdays 1 through 5. |

### Use cases

#### UC-04 — Configure policy, calendar, and integrations

| Field | Specification |
|---|---|
| Primary actor(s) | Admin |
| Trigger | An Admin changes future attendance policy, global calendar, SMTP, or HolidayAPI configuration. |
| Preconditions | The Admin is active and has the deployment-provided encryption master key available to the application. |
| Postconditions | New decisions use the new effective configuration; historical calculations remain stable. |
| Traced requirements | ATT-001–ATT-006, CAL-001–CAL-009, INT-001–INT-008 |

**Main success flow**

1. Create a draft integration revision or future policy version.
2. Preview the effect of the change.
3. Test integration drafts before activation.
4. For HolidayAPI, preview VN holidays and explicitly select/import local rows.
5. Create or edit future global calendar events and decide which are days off.
6. Activate or schedule the reviewed revision.

**Alternatives and exceptions**

- Effective policy versions and past calendar events are immutable.
- A failed integration test cannot replace the working active revision.
- Manual calendar entry remains available without HolidayAPI.
- Existing frozen leave allocations are disclosed but not rewritten.

#### UC-08 — Check in and check out

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

#### UC-09 — Correct a missed checkout

| Field | Specification |
|---|---|
| Primary actor(s) | Intern (submitter); the Intern's responsible Mentor (decision maker) |
| Trigger | An Intern with MISSING_CHECKOUT proposes a checkout, or a Mentor reviews the request. |
| Preconditions | The attendance row has a check-in and no raw checkout; its attached-policy checkout cutoff has passed; the submission deadline remains open. |
| Postconditions | The request/event history explains the effective attendance result while preserving the raw record. |
| Traced requirements | COR-001–COR-009, ATT-024 |

**Main success flow**

1. After the checkout cutoff, submit one proposed checkout through the inclusive scheduled-end-plus-48-hours deadline.
2. Start a separate 48-hour Mentor decision window.
3. The responsible Mentor approves or rejects the request.
4. Until the attendance period is finalized, the responsible Mentor may amend the decision or reverse it with a reason; each change is a new entry.
5. Derive the effective checkout from the current decision without overwriting raw data.

**Alternatives and exceptions**

- A correction before or at the checkout cutoff is rejected because normal checkout remains available.
- The submission deadline stays anchored to scheduled end, not checkout grace; under defaults it is 15:30 two days later.
- The scheduler and the request-time guard both mark a request undecided at the end of its decision window overdue; it is never rejected for the Mentor's delay.
- A decided request does not lock when its decision window ends; after its period is finalized it changes only inside a range reopened under `ATT-022`.
- Concurrent decisions serialize so only a valid current transition wins.

#### UC-10 — Request and decide leave

| Field | Specification |
|---|---|
| Primary actor(s) | Active Intern; the Intern's responsible Mentor |
| Trigger | An Intern requests, withdraws, or cancels leave, or the responsible Mentor decides a request or amends an approved one. |
| Preconditions | The date range is valid, non-overlapping, within internship dates, and before the first counted workday’s scheduled start. |
| Postconditions | The request and frozen day allocations preserve historical quota and attendance meaning. |
| Traced requirements | LEV-001–LEV-013, ATT-024 |

**Main success flow**

1. Enter an inclusive full-day range and reason.
2. Freeze eligible workdays, policies, quota months, and counted-day snapshots.
3. Reserve monthly quota for pending, overdue, and approved days.
4. The responsible Mentor approves or rejects before the boundary.
5. Allow approved cancellation only before the same boundary.

**Alternatives and exceptions**

- Global days off and non-workdays do not consume quota.
- Cross-month requests reserve each month independently.
- A request still pending at the boundary becomes overdue, keeps its quota, and can still be approved or rejected by the responsible Mentor.
- Overlapping pending, overdue, or approved ranges and exhausted quota are rejected transactionally.
- Until its period is finalized, the Intern may withdraw a pending or overdue request; its quota and overlap blocking are released and its dates are classified as without leave.
- A leave decision is never reversed. Before leave begins the Intern cancels an approved request; after it begins, until the period is finalized, the responsible Mentor may only amend it with a reason by withdrawing approval from dates, which releases their quota and leaves attendance as it happened.

#### UC-15 — Excuse a late arrival or early departure

| Field | Specification |
|---|---|
| Primary actor(s) | Intern (requester); the Intern's responsible Mentor (decision maker) |
| Trigger | An Intern asks for a recorded late arrival or early departure to be excused, or the responsible Mentor marks one excused. |
| Preconditions | The attendance row records the late arrival or early departure, and the Intern has a responsible Mentor. |
| Postconditions | The violation stays recorded; whether it is excused is retained with actor, time, and reason. |
| Traced requirements | EXC-001–EXC-007, ATT-024, ACC-026, ATT-016, NOT-011 |

**Main success flow**

1. The Intern submits a request with a reason within 48 hours after scheduled end, while the attendance period is open.
2. The responsible Mentor sees it in their queue and decides it excused or unexcused; a request still undecided after 48 hours is marked overdue and the Mentor is reminded.
3. The Intern is notified of the decision.
4. An excused violation stops lowering compliance and still appears, marked excused, in history and reports.

**Alternatives and exceptions**

- The responsible Mentor marks a violation excused without a request, giving a reason, while the attendance period of that work date is open; there is no separate limit.
- Until the period is finalized, the Mentor amends or reverses a decision with a reason; each change is a new entry and earlier decisions stay in history.
- A late request, or a decision by anyone other than the responsible Mentor, is refused.

#### UC-17 — Reopen a finalized attendance period

| Field | Specification |
|---|---|
| Primary actor(s) | The Intern or their responsible Mentor (requester); Admin (approves or rejects the reopen); the responsible Mentor (acts and finalizes again) |
| Trigger | A result in a finalized attendance period needs to change. |
| Preconditions | The period is finalized under `ATT-020`. |
| Postconditions | Only the named records or dates changed, or nothing changed if the request was rejected; the request, its decision, and any renewed finalization are retained with actor, time, and reason. |
| Traced requirements | ATT-019–ATT-024, EXC-007, ACC-026, NOT-011 |

**Main success flow**

1. The Intern or the responsible Mentor asks to reopen, naming the records or date range and a reason.
2. An Admin approves the request, which reopens exactly that range; the approval is recorded.
3. The responsible Mentor makes the correction, approval, or decision the change needs.
4. The responsible Mentor finalizes the range again.

**Alternatives and exceptions**

- An Admin rejects the request with a reason; the rejection is recorded and the period stays finalized.
- An Admin attempting to approve, correct, or decide inside the reopened range is refused.
- Dates outside the reopened range stay finalized and refuse changes.

## 4. Non-functional Requirements

System-wide non-functional rules apply unchanged: architecture §3 (`ARC`), authentication and security §13 (`SEC`), interface and accessibility §15 (`UI`), delivery §16 (`OPS`), and test evidence §17 (`TST`), all in the [platform spec](../feature-platform/SPEC.md).

## 5. Data

Attendance exceptions (`EXC-001`–`EXC-006`) will need a table of their own. Tables this feature's entities map to today: `attendance_policy_versions`, `attendance_policy_workdays`, `global_calendar_events`, `attendance_records`, `attendance_corrections`, `attendance_correction_events`, `leave_requests`, `leave_request_days`.

The conceptual model, the table inventory, the integrity rules (`DB`), and both diagrams are §19 of the [platform spec](../feature-platform/SPEC.md).

## 6. Error Handling

Rules in section 3 whose text names a refusal, rejection, denial, or failure: `CAL-009`, `ATT-008`, `ATT-010`, `ATT-011`, `COR-005`, `COR-007`, `COR-008`, `COR-009`, `LEV-004`, `LEV-006`, `LEV-008`, `LEV-010`.

System-wide failure behavior is §21 (`ERR-001`–`ERR-007`), and the interface message families are Appendix F, both in the [platform spec](../feature-platform/SPEC.md).

## 7. Acceptance Criteria

Scenarios from the §20 acceptance catalogue whose identifiers start with `AC-ATT` or `AC-CAL` or `AC-COR` or `AC-LEV`. The catalogue's introduction, including the rules deliberately written without a scenario, is in the [platform spec](../feature-platform/SPEC.md).

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-ATT-001 | ATT-001–ATT-006, UI-014, UI-019 | Admin selects a future month, schedules different workdays/penalty and zero checkout grace, then opens Policy History and submits crafted arbitrary-day, out-of-range-grace, or midnight-crossing requests | The server derives day 1 from the selected month; every invalid direct request is rejected; History shows non-secret version/effective metadata; past/current policy, an older attendance row's cutoff, reports, and existing leave allocations do not drift. |
| AC-ATT-002 | ATT-007–ATT-009 | Intern checks in at 09:00:00 and another at 09:00:00.001 under defaults | First is on time; second is late. |
| AC-ATT-003 | ATT-007–ATT-008 | Intern attempts duplicate, off-day, approved-leave, or inactive check-in | Each is rejected and no additional attendance row exists. |
| AC-ATT-004 | ATT-010–ATT-012 | Under defaults, Intern checks out at 16:00:00 and retries at the first later instant; under zero checkout grace, Intern tries at 15:30:00 and the first later instant | Each exact cutoff succeeds once; every later or repeated attempt is rejected and cannot overwrite the first raw checkout. |
| AC-ATT-005 | ATT-011, ATT-016 | The attached-policy checkout cutoff passes with no raw checkout, then Intern attempts normal checkout | The day has `MISSING_CHECKOUT` only, not early departure too; the late attempt is rejected and raw checkout remains null. |
| AC-ATT-006 | ATT-013–ATT-017 | Period has 20 eligible days, one approved-leave day, 17 present, and two absent | Attendance rate is `17/19 = 89.47%`; compliance uses historical daily policy and absent days score zero. |
| AC-ATT-007 | ATT-014, ATT-017 | Filtered period has no expected workdays | Attendance and compliance display `N/A` without division error. |
| AC-ATT-008 | ATT-018 | An Intern checks in, the Admin completes the internship later the same local date, and the Intern attempts a second check-in the next workday | The already-recorded attendance row for the terminal date is retained unchanged and still appears in reports; the next-day check-in is refused because the lifecycle is terminal. |
| AC-ATT-009 | ATT-019–ATT-021, LEV-010, COR-007 | On 5 October at 23:59 one Intern's September period has no open request, while another's has a correction left undecided past its window; that correction is decided on 7 October | The correction became `OVERDUE`, not rejected; the first period finalizes at the deadline and every later change to its September results is refused; the second stays open, finalizes when the correction is decided, and then refuses changes too. |
| AC-ATT-010 | ATT-022–ATT-023, NOT-011 | After September is finalized, the Intern asks to reopen one date with a reason and an Admin rejects it, first without a reason and then with one; the responsible Mentor asks to reopen another date, an Admin approves it and tries to decide an exception in it, and the Mentor reverses the exception decision and finalizes the date again | Admins are notified of each request and the requester of each outcome; the rejection without a reason is refused, and the rejection with one keeps the Admin, time, and reason and leaves the date finalized; the approval records the Admin and time and reopens only the second date; the Admin's exception decision is refused; the Mentor's reversal commits and the date is finalized again with the Mentor and time; every other September date stays finalized throughout. |
| AC-CAL-001 | CAL-002–CAL-005 | HolidayAPI unavailable or unconfigured | Preview reports actionable failure; Admin can add custom event; attendance/reporting continue from local data. |
| AC-CAL-002 | CAL-003–CAL-004 | Preview returns public and non-public Vietnam events | Public is preselected only; Admin can toggle either; selected rows preserve provenance and duplicate UUID import is rejected/idempotent. |
| AC-CAL-003 | CAL-006–CAL-009 | Admin marks observed holiday as display-only, then as day off before date | Display-only date remains eligible; day-off version suppresses new attendance/quota and new due dates. |
| AC-CAL-004 | CAL-003, CAL-007, UI-019 | Admin attempts to edit an event after its date and opens Calendar History | Mutation is rejected; retained event/provenance metadata remains read-only and historical daily classification remains unchanged. |
| AC-CAL-005 | CAL-001 | A Mentor and an Intern each attempt to create and to edit a global calendar event, and a client attempts to attach a calendar override to one Project | All non-Admin attempts are refused before any write; no schema path or endpoint accepts a project-scoped calendar override; Admin succeeds for both a custom and an imported event. |
| AC-COR-001 | COR-001–COR-003 | Under defaults, Intern attempts correction before/at the 16:00 checkout cutoff, just after it, at 15:30 two days later, and at the first later instant; an Intern with a normal checkout also tries | Before/at cutoff and normal-checkout attempts are rejected; a missing-checkout request after cutoff through the inclusive 15:30 deadline two days later is accepted once; the first later instant is rejected. |
| AC-COR-002 | COR-002 | Proposed checkout precedes check-in, crosses local date, or is future | Validation rejects each value without creating correction. |
| AC-COR-003 | COR-004–COR-005, COR-007, ATT-024 | The responsible Mentor approves a correction; two days later, before the period is finalized, the Mentor amends its note with a reason, tries to amend the proposed checkout, reverses the approval to rejected with a reason, and tries a further change without a reason | The approval, the amendment, and the reversal each append an ordered immutable event with its kind, actor, and time, and earlier entries stay unchanged; no lock applied when the decision window ended and nothing returned to `PENDING`; changing the proposed checkout and the change without a reason are refused; the effective checkout and the missing-checkout classification follow the current rejection. |
| AC-COR-004 | COR-006 | Mentor approves a proposed checkout before scheduled end | Raw checkout remains null; effective checkout becomes proposal; missing flag clears and early flag appears. |
| AC-COR-005 | COR-007–COR-009 | Pending correction reaches decision deadline while scheduler is late | First access marks it `OVERDUE` atomically without rejecting it; the scheduler later behaves idempotently; the responsible Mentor can still decide it until its period is finalized. |
| AC-COR-006 | AUTH-003, COR-001–COR-009, UI-019 | Intern and Mentor open their correction workflows with pending and terminal requests | Intern sees only owned corrections; the responsible Mentor sees the actionable queue of the Interns they are responsible for before retained correction/event history; unauthorized users and guessed IDs disclose nothing; no Leave form is mixed into either Correction workflow. |
| AC-EXC-001 | EXC-001–EXC-003, EXC-005, NOT-011 | An Intern ten minutes late requests an excuse with a reason 47 hours after scheduled end, and on another day 49 hours after; the responsible Mentor excuses the first request within 48 hours | The first request is accepted and the second refused; the responsible Mentor is notified of the request and the Intern of the decision; the day still shows late, its compliance counts no late violation, and history and reports mark it excused with the Mentor and time. |
| AC-EXC-002 | EXC-004, EXC-006, ATT-021, AUTH-003 | The responsible Mentor marks one early departure excused without a reason, another with a reason while its period is open, and a third after that period has finalized; another Mentor, the Intern's Leader, and an Admin each try to decide a pending request | Only the reasoned mark inside the open period commits; the mark after finalization is refused and changes only through a range reopened under `ATT-022`; every other attempt is refused and changes nothing. |
| AC-EXC-003 | EXC-003, EXC-005, EXC-007, ATT-024, NOT-011 | A request stays undecided for 48 hours; the responsible Mentor then excuses it before the period is finalized, later reverses it to unexcused with a reason, and tries a further change after the period is finalized | At 48 hours the request becomes overdue, the Mentor is notified, and compliance still counts the violation; the later decision commits; the reversal appends a new decision while the earlier one stays unchanged in history; compliance follows the current decision; the change after finalization is refused. |
| AC-LEV-001 | LEV-001–LEV-003 | Request spans weekend, global day off, and two months | Only eligible dates materialize; each date uses its correct quota month/policy snapshot. |
| AC-LEV-002 | LEV-004–LEV-006 | Concurrent requests would exceed quota or overlap | Locking and exclusion constraint allow at most one valid outcome; no overbooking/overlap commits. |
| AC-LEV-003 | LEV-007 | Intern edits pending range | Original allocation is replaced only after new overlap/quota validation succeeds atomically. |
| AC-LEV-004 | LEV-008–LEV-010 | Same-day request submitted at 08:29:59 and at 08:30:00 | First may submit; second rejects. Pending at 08:30 becomes `OVERDUE` through the access guard even if the scheduler has not run, keeps its quota reservation, and can still be approved by the responsible Mentor. |
| AC-LEV-005 | LEV-011–LEV-012 | Intern cancels approved leave before and after first counted start | Before succeeds and releases quota; at/after boundary rejects and allocation remains frozen. |
| AC-LEV-006 | LEV-003–LEV-004, UI-019 | Intern opens the dashboard and My Leave across pending, overdue, approved, rejected, withdrawn, cancelled, and cross-month requests | Dashboard shows current-month reserved/quota/remaining; month selection recomputes from frozen allocations; pending, overdue, and approved requests reserve, rejected, withdrawn, and cancelled requests release, and each cross-month allocation remains separately labelled. |
| AC-LEV-007 | LEV-013, LEV-004, LEV-006 | An Intern withdraws one pending request before it starts and one overdue request whose two workdays have passed without a check-in, then submits a new request over the first range and tries to withdraw an approved request | Both withdrawals mark the requests `WITHDRAWN`, release their quota and overlap blocking, and keep each request, its day allocations, and its history; the two past workdays are classified `ABSENT`; the new request over the released range is accepted; withdrawing the approved request is refused. |
| AC-LEV-008 | LEV-004, LEV-011, ATT-024 | On the third day of an approved three-day leave, before the period is finalized, the responsible Mentor tries to reverse the approval, tries to add a fourth day, amends without a reason, and then amends with a reason to withdraw approval from the first day, on which the Intern did not check in | The reversal, the added day, and the amendment without a reason are refused; the amendment appends an entry with the Mentor, time, and reason while the approval and the frozen allocation stay unchanged in history; the first day's quota is released, no attendance record is created, and the day is classified `ABSENT`. |
| AC-DB-005 | DB-002 | `btree_gist` is queried after replay, then one Intern is given a pending leave range and a second overlapping range is inserted directly by SQL | The extension is present; the second insert is refused by the exclusion constraint; a non-overlapping range for the same Intern and an overlapping range for a different Intern both succeed. |

## 8. Out of Scope

System-wide exclusions are §1.3 of the [platform spec](../feature-platform/SPEC.md): `GOV-007` through `GOV-010` and `GOV-015`.

Exclusions stated inside this spec's own rules: `CAL-001`, `LEV-001`.

## Notes / Open Questions

- `UC-04` also configures SMTP and HolidayAPI, whose rules are in [the integration spec](../feature-integration/SPEC.md).
- `COR-006`: approving a correction confirms the effective checkout only; a checkout before scheduled end remains an early departure under `ATT-011`.
- `D14`, closed on 14 September 2026 and **provisional pending instructor confirmation**, covers `EXC-001`–`EXC-007`, leave withdrawal under `LEV-013`, approved leave under `LEV-011`, and the monthly periods and shared decision-history mechanism of `ATT-019`–`ATT-024`. Enterprise-backed: the fact kept apart from the approval; approval by a responsible Mentor; overdue never meaning rejected; one mechanism for decision history and finalization reused by leave, corrections, and exceptions, each keeping its own actions and states; withdrawal by the requester before a decision; no silent reversal of approved leave; reopening decided on data-governance grounds only; reassignment when the approver is unavailable. Laboratory policy, not a standard: the 48-hour submission and decision limits shared by corrections and exceptions (`D23`), the five-day finalization grace, leaving excused violations out of compliance, and recalculating attendance and compliance when an amendment leaves a past date without leave.
- **Interpreted, to confirm.** A period belongs to one Intern, so one Intern's open request never holds back anyone else's month. A reopened range is finalized again by the responsible Mentor. An overdue leave request keeps its quota reservation and still blocks overlapping requests.
- **Derived, to confirm.** A Mentor sets only an outcome and a note on a correction or an exception today, so an amendment there changes the note; any other amendable value would need a rule of its own. An amendment to approved leave can only withdraw approval from dates, because adding a date needs a new approval. A leave decision is never reversed, so a rejected request stays rejected and those dates need a new request; since `LEV-012` forbids retroactive leave, a rejected request whose dates have passed can no longer become leave. Under `COR-001` a row keeps one correction request, so after a decision only the responsible Mentor's amendment or reversal changes it.
