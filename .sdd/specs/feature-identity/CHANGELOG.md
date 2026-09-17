# Changelog — Identity spec

Each entry records a change to [SPEC.md](SPEC.md): the version, the date, what changed,
and why. A change that alters a rule raises the version.

## 1.1.5 — 2026-09-17

Scenario split; no rule changed. `AC-ACC-012` exercised two modules: the account directory and email correction of `ACC-009`, `ACC-017` and `ACC-018`, and the Student Code and internship-date correction of `ACC-019`. Under the principle in §20 of the platform spec it keeps the first and now covers `ACC-009`, `ACC-017` and `ACC-018`; the second is `AC-ACC-015` in the [internship spec](../feature-internship/SPEC.md). Each expected result is the part of the old one that its own Given and When exercise, with no clause dropped: the old "deactivated/terminal data" is split into deactivated account data here and completed and withdrawn profiles there.

Section 1 names the module the spec describes, as `ARC-005` names it. The state of the code belongs in `plan.md`, not in a specification.

The opening paragraph no longer says the section numbers come from a single-file specification; it states the numbering convention they follow.

Section 6 now lists `SEC-006`, which throttles a key after five failed sign-ins and so refuses further attempts.

## 1.1.4 — 2026-09-17

Relocation only; no rule text changed. Decision `D28` splits the account spec by module, and this spec keeps what belongs to `identity`, under a new name. The entries below are the account spec's history.

- `ACC-019`–`ACC-026` moved to [the internship spec](../feature-internship/SPEC.md), with `AC-ACC-010`, `AC-ACC-013` and `AC-ACC-014`, which cover only those rules. `AC-ACC-011` and `AC-ACC-012` cover rules of both specs, so they stay here.
- `UC-03` was rewritten rather than moved, and is reviewed on its own. It keeps accounts: its trace is `ACC-008`–`ACC-018` and `AUTH-001`–`AUTH-002`, and its title is now *Administer accounts*. The internship lifecycle it also described is `UC-18` in the internship spec. The two traces together equal the old trace of `UC-03` exactly.
- Sections 1, 5, 6, 7 and 8 were rewritten for what the spec now holds. The two notes about `ACC-023`, `ACC-024` and `ACC-026` moved with those rules.

## 1.1.3 — 2026-09-16

Status only; no rule changed. `D14` is no longer held provisional, so the note under `ACC-026` says it is decided. Recorded as provisional on that date and confirmed for build by the maintainer on 16 September 2026; see the note above the decision table in [`.sdd/decisions.md`](../../decisions.md).

## 1.1.2 — 2026-09-14

The remaining points of decision `D14`, recorded in [`.sdd/decisions.md`](../../decisions.md). Marked provisional, pending instructor confirmation.

- `ACC-026`: reassignment moves every pending or overdue leave, correction, and exception request to the new Mentor; while the responsible Mentor is locked or deactivated the Intern is shown to Admins for reassignment, and no Admin or other Mentor decides. Added `AC-ACC-014`.

## 1.1.1 — 2026-09-14

Relocation only; no rule text changed. `SEC-002`, `SEC-003`, `SEC-004`, `SEC-005`, `SEC-006`, `SEC-007` moved here from the platform spec because they concern only this feature, with `AC-SEC-002`, `AC-SEC-003`.

## 1.1.0 — 2026-09-14

Decision `D14`, recorded in [`.sdd/decisions.md`](../../decisions.md). Marked provisional, pending instructor confirmation.

- **Added `ACC-026`**: an Admin assigns and replaces an Intern's responsible Mentor, and earlier decisions keep the Mentor who made them. `ACC-021` does not activate an internship without one. Added `AC-ACC-013`.

## 1.0.1 — 2026-09-14

Notes only. The instructor's confirmation that a completed Intern may change their password, and the responsible-Mentor field `D14` will need.

## 1.0.0 — 2026-09-14

Approved by Loc-LX. The rules moved here unchanged from the single-file
specification at `.sdd/requirements.md` (commit `625463b`). Sections 1, 2, 4, 5, 6 and 8 and the notes were written for this spec.

Changes made to these rules during the review before approval are recorded in
[`.sdd/decisions.md`](../../decisions.md).
