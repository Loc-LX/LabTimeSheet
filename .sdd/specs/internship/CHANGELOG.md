# Changelog — Internship module

## 1.4.0 — 2026-09-21

Status only (`D38`). No question affecting this document is open, so its Status
moves from the inherited baseline to `APPROVED BUSINESS BASELINE`. The specification
map now defines what that field means and when it must change. No numbered rule,
acceptance row, schema or dependency changed.

## 1.3.0 — 2026-09-21

Navigation cleanup (`D38`). The parallel `feature-internship/` compatibility index is
removed: it defined no rule or scenario and had no reader inside the repository.
Its entries are retained verbatim below, so this module keeps one changelog and one
home. Successor links in this module's feature changelogs now point at that section.
No numbered rule, acceptance row, schema or dependency changed.

## 1.2.0 — 2026-09-21

Organization only (`D34`). Inherits the 1.1.1 baseline from
[the retained history](#history-before-the-d34-feature-split). Rules and acceptance rows
move verbatim into cohesive feature contracts; shared rules and cross-feature scenarios
remain in [MODULE.md](MODULE.md). Canonical use cases and state tables move only
when one feature owns the workflow; their former headings remain links.

This entry does not approve a new business rule, technical plan or implementation.

---

## History before the D34 feature split

These entries recorded `internship` while its rules lived in a single `feature-internship/SPEC.md`.
They are retained verbatim; only their heading depth changed when they moved here.

### 1.2.0 — 2026-09-21

Navigation migration (`D34`). Canonical rules now live in [MODULE.md](../internship/MODULE.md)
and its feature SPECs. This old SPEC.md is a compatibility index. Existing entries below
record the pre-split baseline and remain historical.

### 1.1.1 — 2026-09-17

No rule changed. The internship gains a state transition table after its rules, which yields to them where they differ.

### 1.1.0 — 2026-09-17

Rule added; decision `D32`. **Added `DB-021`**: an Intern profile references at most one responsible Mentor, whose role is `MENTOR`. **Added `AC-DB-008`.** §5 says the profile carries that reference.

### 1.0.1 — 2026-09-17

Scenario added by a split; no rule changed. `AC-ACC-015` takes from `AC-ACC-012` in the [identity spec](../identity/MODULE.md) the correction of the Student Code and the internship dates under `ACC-019`, in every internship state. It keeps the `AC-ACC` prefix so that one numbering covers every scenario that came from the account spec.

Section 1 names the module the spec describes, as `ARC-005` names it. The state of the code belongs in `plan.md`, not in a specification.

The notes no longer record when the instructor confirmed `ACC-023` and `ACC-024`, which the identity changelog keeps, nor when `D14` was decided. The note on `ACC-023` and `ACC-024` only restated the two rules, which `GOV-016` forbids, so it is removed.

The opening paragraph no longer says the section numbers come from a single-file specification; it states the numbering convention they follow. Section 7 explains the `AC-ACC` prefix by the rules the scenarios cover rather than by where they once were.

### 1.0.0 — 2026-09-17

Relocation only; no rule text changed. Decision `D28` gives the internship lifecycle a module of its own, built on `identity`, and this spec holds its rules.

- `ACC-019`–`ACC-026` moved here from the account spec, now the [identity spec](../identity/MODULE.md), with `AC-ACC-010`, `AC-ACC-013` and `AC-ACC-014`. Their history before this date is in the account spec's changelog, whose entries the identity changelog keeps.
- `UC-18` is new prose, reviewed on its own. It takes the internship lifecycle out of `UC-03` and traces `ACC-019`–`ACC-025` and `AUTH-001`–`AUTH-002`; together with the new `UC-03` it traces exactly what the old `UC-03` traced. `ACC-026` is not added to it, because `UC-15` and `UC-17` already trace that rule and the split adds nothing.
- Sections 1, 2, 5, 6, 7 and 8 were written for this spec. The notes on `ACC-023`, `ACC-024` and `ACC-026` came with the rules unchanged, and one note records where `ACC-026` is traced.
