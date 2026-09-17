# Calendar Spec

**Version:** 1.0.2 · **Owner:** Loc-LX · **Status:** APPROVED · **Date:** 2026-09-17

Part of the Lab Timesheet specification. Rules every feature shares, including the
glossary, the authorization model, the domain model, and failure handling, are in
[the platform spec](../feature-platform/SPEC.md). Section numbers marked `§` are one
numbering shared by all the specs, so a reference such as §5.2 names the same section
wherever it appears.

## 1. Context & Goal

The effective-dated attendance policy, the global calendar, and HolidayAPI import, part of the second area named by the product objective (§1.2 of the platform spec). Attendance, leave, and corrections, which apply them, are in [the attendance spec](../feature-attendance/SPEC.md).
Its module is `calendar` (`ARC-005`), which holds the whole attendance policy although its name says calendar: calendar needs the policy timezone and attendance needs the calendar, so the policy sits in the lower of the two.

## 2. Actors & Roles

Primary actors named by this feature's use cases:

- **UC-04 — Configure attendance policy, calendar, and HolidayAPI:** Admin

Every capability by role is in the permission matrix, [platform spec](../feature-platform/SPEC.md) §5.2. Authorization is resolved from stored context, never from the global role alone (§5.1).

## 3. Functional Requirements

### §8. Global attendance policy and calendar

#### §8.1 Effective-dated policy

`ATT-004`–`ATT-006`, which apply the policy to attendance and leave, are in [the attendance spec](../feature-attendance/SPEC.md).

| ID | Requirement |
|---|---|
| ATT-001 | THE system SHALL maintain one effective-dated global attendance-policy timeline managed by an Admin. THE system SHALL store, in each version, the timezone, scheduled start and end, check-in grace minutes, checkout grace minutes, monthly leave quota, violation penalty, and configured ISO weekdays. THE system SHALL expose a read-only policy History built from those retained versions and their non-secret creator and effective metadata. |
| ATT-002 | THE system SHALL seed one policy version effective `1970-01-01` with timezone `Asia/Ho_Chi_Minh`, Monday through Friday, 08:30 to 15:30, 30-minute check-in grace, 30-minute checkout grace, 3 leave workdays per month, and a 0.25 penalty per applicable violation. |
| ATT-003 | THE system SHALL require each grace value to be an integer from 0 through 720 minutes, and SHALL require scheduled end plus checkout grace to fall strictly before the next local midnight, so that an overnight schedule cannot be configured. THE system SHALL require the monthly leave quota to be an integer from 0 through 4, because four days is the largest whole number that stays within one fifth of the shortest twenty-workday month. THE system SHALL require a new policy version to begin on the first day of a future calendar month, SHALL collect that month in the Admin form and derive its first day on the server before applying the same validation, SHALL permit a not-yet-effective version to be replaced, and SHALL treat an effective version as immutable. |

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

### §12. Integrations and notifications

#### §12.1 Secret-bearing integration configuration

`INT-001`–`INT-008` and `INT-010`, the draft, test, activation, and encryption rules this one applies to HolidayAPI, are in [the platform spec](../feature-platform/SPEC.md).

| ID | Requirement |
|---|---|
| INT-009 | THE system SHALL apply the same draft, test, activate and retire behavior, the same encrypted key handling, and the fixed country code `VN` to the HolidayAPI configuration, and SHALL expose a read-only HolidayAPI History carrying non-secret revision metadata and outcomes. |

### Integrity (from platform §19.3)

| ID | Requirement |
|---|---|
| DB-009 | THE schema seed SHALL create the `1970-01-01` policy version and ISO workdays 1 through 5. |

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

System-wide non-functional rules apply unchanged: architecture §3 (`ARC`), authentication and security §13 (`SEC`), interface and accessibility §15 (`UI`), delivery §16 (`OPS`), and test evidence §17 (`TST`), all in the [platform spec](../feature-platform/SPEC.md).

## 5. Data

Tables this feature's entities map to: `attendance_policy_versions`, `attendance_policy_workdays`, `global_calendar_events`, `holiday_api_configurations`.

The conceptual model, the table inventory, the integrity rules (`DB`), and both diagrams are §19 of the [platform spec](../feature-platform/SPEC.md).

## 6. Error Handling

Rules in section 3 whose text names a refusal, rejection, denial, or failure: `CAL-009`.

System-wide failure behavior is §21 (`ERR-001`–`ERR-007`), and the interface message families are Appendix F, both in the [platform spec](../feature-platform/SPEC.md).

## 7. Acceptance Criteria

Scenarios from the §20 acceptance catalogue whose identifiers start with `AC-CAL` or `AC-ATT`. The catalogue's introduction, including the rules deliberately written without a scenario, is in the [platform spec](../feature-platform/SPEC.md).

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-ATT-001 | ATT-001–ATT-006, UI-014, UI-019 | Admin selects a future month, schedules different workdays/penalty and zero checkout grace, then opens Policy History and submits crafted arbitrary-day, out-of-range-grace, or midnight-crossing requests | The server derives day 1 from the selected month; every invalid direct request is rejected; History shows non-secret version/effective metadata; past/current policy, an older attendance row's cutoff, reports, and existing leave allocations do not drift. |
| AC-CAL-001 | CAL-002–CAL-005 | HolidayAPI unavailable or unconfigured | Preview reports actionable failure; Admin can add custom event; attendance/reporting continue from local data. |
| AC-CAL-002 | CAL-003–CAL-004 | Preview returns public and non-public Vietnam events | Public is preselected only; Admin can toggle either; selected rows preserve provenance and duplicate UUID import is rejected/idempotent. |
| AC-CAL-003 | CAL-006–CAL-009 | Admin marks observed holiday as display-only, then as day off before date | Display-only date remains eligible; day-off version suppresses new attendance/quota and new due dates. |
| AC-CAL-004 | CAL-003, CAL-007, UI-019 | Admin attempts to edit an event after its date and opens Calendar History | Mutation is rejected; retained event/provenance metadata remains read-only and historical daily classification remains unchanged. |
| AC-CAL-005 | CAL-001 | A Mentor and an Intern each attempt to create and to edit a global calendar event, and a client attempts to attach a calendar override to one Project | All non-Admin attempts are refused before any write; no schema path or endpoint accepts a project-scoped calendar override; Admin succeeds for both a custom and an imported event. |

## 8. Out of Scope

System-wide exclusions are §1.3 of the [platform spec](../feature-platform/SPEC.md): `GOV-007` through `GOV-010` and `GOV-015`.

Exclusions stated inside this spec's own rules: `CAL-001`.

## Notes / Open Questions

- SMTP configuration is `UC-19` in [the platform spec](../feature-platform/SPEC.md), where the SMTP rules live.
- `UC-04` traces `ATT-004`–`ATT-006`, which live in the attendance spec; a trace across specs is allowed.
