# Calendar Module

<a id="calendar-spec"></a>

**Version:** 1.3.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

Part of the Lab Timesheet specification. Rules every feature shares, including the
glossary, the authorization model, the domain model, and failure handling, are in
[the platform spec](../platform/MODULE.md). Section numbers marked `§` are one
numbering shared by all the specs, so a reference such as §5.2 names the same section
wherever it appears.

## 1. Context & Goal

The effective-dated attendance policy, the global calendar, and HolidayAPI import, part of the second area named by the product objective (§1.2 of the platform spec). Attendance, leave, and corrections, which apply them, are in [the attendance spec](../attendance/MODULE.md).
Its module is `calendar` (`ARC-005`), which holds the whole attendance policy although its name says calendar: calendar needs the policy timezone and attendance needs the calendar, so the policy sits in the lower of the two.


### Feature index

Read this shared contract together with the relevant feature SPEC. Rules are canonical
in exactly one of these documents; references do not create copies. Cross-feature use
cases, shared data constraints and unresolved decisions remain here (`D34`).

| Feature | Operations |
|---|---|
| [Attendance policy](features/attendance-policy/SPEC.md) | Seed policy; Schedule and validate a version; Read history |
| [Global calendar](features/global-calendar/SPEC.md) | Create and edit events; Apply day-off effects |
| [Holiday import](features/holiday-import/SPEC.md) | Configure and test HolidayAPI; Preview and select; Import and handle failure |


## 2. Actors & Roles

Primary actors named by this feature's use cases:

- **UC-04 — Configure attendance policy, calendar, and HolidayAPI:** Admin

Every capability by role is in the permission matrix, [platform spec](../platform/MODULE.md) §5.2. Authorization is resolved from stored context, never from the global role alone (§5.1).

## 3. Functional Requirements

### §8. Global attendance policy and calendar

#### §8.1 Effective-dated policy

`ATT-004`–`ATT-006`, which apply the policy to attendance and leave, are in [the attendance spec](../attendance/MODULE.md).

Feature contracts: [Attendance policy](features/attendance-policy/SPEC.md).

#### §8.2 Global calendar and HolidayAPI

Feature contracts: [Global calendar](features/global-calendar/SPEC.md), [Holiday import](features/holiday-import/SPEC.md).

HolidayAPI field behavior is based on its [official API documentation](https://holidayapi.com/docs).

### §12. Integrations and notifications

#### §12.1 Secret-bearing integration configuration

`INT-001`–`INT-008` and `INT-010`, the draft, test, activation, and encryption rules this one applies to HolidayAPI, are in [the platform spec](../platform/MODULE.md).

Feature contracts: [Holiday import](features/holiday-import/SPEC.md).

### Integrity (from platform §19.3)

Feature contracts: [Attendance policy](features/attendance-policy/SPEC.md).

### Use cases

#### UC-04 — Configure attendance policy, calendar, and HolidayAPI

| Field | Specification |
|---|---|
| Primary actor(s) | Admin |
| Trigger | An Admin changes future attendance policy, global calendar, or HolidayAPI configuration. |
| Preconditions | The Admin is active and has the deployment-provided encryption master key available to the application. |
| Postconditions | New decisions use the new effective configuration; historical calculations remain stable. |
| Traced requirements | ATT-001–ATT-006, CAL-001–CAL-009, INT-001–INT-005, INT-009 |

**Main success flow**

1. Create a draft HolidayAPI revision or future policy version.
2. Preview the effect of the change.
3. Test HolidayAPI drafts before activation.
4. For HolidayAPI, preview VN holidays and explicitly select/import local rows.
5. Create or edit future global calendar events and decide which are days off.
6. Activate or schedule the reviewed revision.

**Alternatives and exceptions**

- Effective policy versions and past calendar events are immutable.
- A failed HolidayAPI test cannot replace the working active revision.
- Manual calendar entry remains available without HolidayAPI.
- Existing frozen leave allocations are disclosed but not rewritten.

## 4. Non-functional Requirements

System-wide non-functional rules apply unchanged: architecture §3 (`ARC`), authentication and security §13 (`SEC`), interface and accessibility §15 (`UI`), delivery §16 (`OPS`), and test evidence §17 (`TST`), all in the [platform spec](../platform/MODULE.md).

## 5. Data

Tables this feature's entities map to: `attendance_policy_versions`, `attendance_policy_workdays`, `global_calendar_events`, `holiday_api_configurations`.

The conceptual model, the table inventory, the integrity rules (`DB`), and both diagrams are §19 of the [platform spec](../platform/MODULE.md).

## 6. Error Handling

Rules in section 3 whose text names a refusal, rejection, denial, or failure: `CAL-009`.

System-wide failure behavior is §21 (`ERR-001`–`ERR-007`), and the interface message families are Appendix F, both in the [platform spec](../platform/MODULE.md).

## 7. Acceptance Criteria

Scenarios from the §20 acceptance catalogue whose identifiers start with `AC-CAL` or `AC-ATT`. The catalogue's introduction, including the rules deliberately written without a scenario, is in the [platform spec](../platform/MODULE.md).

Feature contracts: [Attendance policy](features/attendance-policy/SPEC.md), [Holiday import](features/holiday-import/SPEC.md), [Global calendar](features/global-calendar/SPEC.md).

## 8. Out of Scope

System-wide exclusions are §1.3 of the [platform spec](../platform/MODULE.md): `GOV-007` through `GOV-010` and `GOV-015`.

Exclusions stated inside this spec's own rules: `CAL-001`.

## Notes / Open Questions

The feature split is organizational; read the feature-specific notes as well as the shared
notes below. No question affecting this module is open (`D38`). Should one be recorded here
later, this module and every feature it affects return to the inherited-baseline status.


- SMTP configuration is `UC-19` in [the platform spec](../platform/MODULE.md), where the SMTP rules live.
- `UC-04` traces `ATT-004`–`ATT-006`, which live in the attendance spec; a trace across specs is allowed.
