# ADR 0002 — Admin retains detailed Intern Attendance report scope

- **Status:** Accepted
- **Date:** 2026-08-30 (recorded 2026-09-10)
- **Supersedes:** the Attendance portion of the 27 August 2026 reporting-role revision in
  `docs/requirements/requirements-specification.md`
- **Requirements touched:** `RPT-004`, `RPT-011`, `AUTH-010` context, §5.2 permission matrix,
  §15.2 role-oriented page map, `AC-RPT-002`

## Context

The Admin reporting scope changed direction three times, and the requirements document was
deleted before the last change could be written down. The full sequence, recovered from git:

| Date | Source | Position on Admin Attendance reports |
|---|---|---|
| 17 Aug 2026 | `docs/software-requirements-specification.md`, commit `0430718` | Permitted. `RPT-004` read "Admin and active Mentors may view detailed Intern attendance." |
| 20 Aug 2026 | commit `66f0da4` | That document was deleted by an unexplained revert of a merge commit. |
| 27 Aug 2026 | `requirements-specification.md`, commit `6fa4b1a` | Withdrawn. Admin was limited to account lifecycle and system configuration. |
| 29 Aug 2026 | commit `73df96b` | That document was deleted as "redundant docs files" while `PRODUCT.md` still referenced it. |
| 30 Aug 2026 | commit `684e13c` | Restored. Code, `README.md`, `PRODUCT.md`, and the affected test evidence were all updated together. |

Because the requirements document had already been removed on 29 August, the 30 August decision
never reached it. That left the recovered document asserting a denial the running system does not
implement.

## Decision

Admin retains detailed Intern Attendance report scope: navigation, the HTML report page, the XLSX
and PDF exports, and the report dataset, on the same footing as an active Mentor. The acting Admin
account must be active and the target account's immutable role must be `INTERN`.

Admin gains no other reporting scope. Project/Task requests remain denied before Project option
resolution or downstream reads, Daily Project Work Report requests continue to return the
non-disclosing `Project unavailable` response, and the Admin dashboard remains account-lifecycle
and configuration guidance only.

## Rationale

1. `GOV-001` sets the authority order, and its highest rank is the current decision of the primary
   implementor. The 30 August change is that decision, and it is the most recent statement in the
   sequence above.
2. Three independent artefacts already agree: `OperationalReportAuthorization.requireAttendanceReportAccess`
   admits `ROLE_ADMIN`, the shared layout exposes the Attendance report link to
   `hasAnyRole('ADMIN','MENTOR','INTERN')`, and `AttendanceReportQueryService` documents Admin and
   Mentor as holding the same target scope. `README.md` and `PRODUCT.md` describe the same
   behaviour. Only the deleted requirements document dissented.
3. The position is not novel. It restores what the 17 August SRS already specified, so this is the
   team reverting its own 27 August change rather than code diverging from an approved rule.
4. Correcting the document is the smaller and safer change. The opposite choice would require
   reverting `684e13c`, editing `README.md` and `PRODUCT.md`, and rewriting roughly six test
   evidence records, to reach a position the team had already abandoned once.

## Consequences

- `GOV-005` is respected: the 27 August revision block stays in place and is superseded by a dated
  30 August block rather than being rewritten. The document keeps its own audit trail.
- `AC-RPT-002` now expects an authorized detailed Attendance result for an active Admin and an
  access denial only for Admin Project/Task requests.
- No production code, migration, or test changes follow from this record. The implementation
  already matches it.
- If the instructor or product owner later withdraws Admin Attendance scope, that is a new
  decision and needs its own ADR plus the code and evidence changes listed in rationale item 4.
