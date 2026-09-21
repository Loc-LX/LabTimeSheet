# Changelog — Calendar module

## 1.3.0 — 2026-09-21

Status only (`D38`). No question affecting this document is open, so its Status
moves from the inherited baseline to `APPROVED BUSINESS BASELINE`. The specification
map now defines what that field means and when it must change. No numbered rule,
acceptance row, schema or dependency changed.

## 1.2.0 — 2026-09-21

Navigation cleanup (`D38`). The parallel `feature-calendar/` compatibility index is
removed: it defined no rule or scenario and had no reader inside the repository.
Its entries are retained verbatim below, so this module keeps one changelog and one
home. Successor links in this module's feature changelogs now point at that section.
No numbered rule, acceptance row, schema or dependency changed.

## 1.1.0 — 2026-09-21

Organization only (`D34`). Inherits the 1.0.2 baseline from
[the retained history](#history-before-the-d34-feature-split). Rules and acceptance rows
move verbatim into cohesive feature contracts; shared rules and cross-feature scenarios
remain in [MODULE.md](MODULE.md). Canonical use cases and state tables move only
when one feature owns the workflow; their former headings remain links.

This entry does not approve a new business rule, technical plan or implementation.

---

## History before the D34 feature split

These entries recorded `calendar` while its rules lived in a single `feature-calendar/SPEC.md`.
They are retained verbatim; only their heading depth changed when they moved here.

### 1.1.0 — 2026-09-21

Navigation migration (`D34`). Canonical rules now live in [MODULE.md](../calendar/MODULE.md)
and its feature SPECs. This old SPEC.md is a compatibility index. Existing entries below
record the pre-split baseline and remain historical.

### 1.0.2 — 2026-09-17

Trace only; no rule changed. `UC-04` now traces `INT-001`–`INT-005`, the rules its HolidayAPI flow depends on: administration through the application (`INT-001`), the master key its precondition names (`INT-002`), and the encrypted key that is never redisplayed or leaked (`INT-003`–`INT-005`). Until now only `UC-19` traced them.

### 1.0.1 — 2026-09-17

Trace only; no rule changed. `UC-04` now traces `INT-009`. Its flow already described creating, testing and activating a HolidayAPI draft, which is what `INT-009` governs, but no use case traced the rule, a gap that predated `D28`. The note recording the gap is removed.

Section 1 names the module the spec describes, as `ARC-005` names it. The state of the code belongs in `plan.md`, not in a specification.

The note on SMTP no longer tells how `UC-04` was split, which the 1.0.0 entry records; it says where SMTP configuration is.

The opening paragraph no longer says the section numbers come from a single-file specification; it states the numbering convention they follow.

Section 7 names both prefixes of the scenarios it holds, `AC-CAL` and `AC-ATT`.

### 1.0.0 — 2026-09-17

Relocation only; no rule text changed. Decision `D28` places the whole attendance policy, the global calendar and HolidayAPI in the module `calendar`, and this spec holds their rules.

- From the [attendance spec](../attendance/MODULE.md): `ATT-001`–`ATT-003`, `CAL-001`–`CAL-009` and `DB-009`, with `AC-CAL-001`–`AC-CAL-005` and `AC-ATT-001`. The history of these rules before this date is in the attendance changelog.
- Scenarios are placed by one principle, which does not depend on where a scenario sat before and so also places a scenario not yet written. A scenario sits in the spec of the module that owns the rules its Given/When actually exercises. A rule named only for the expected result, or a shared platform rule, does not decide where it sits. `AC-ATT-001` configures the policy in its Given/When and names `ATT-004`–`ATT-006` only for the result, so it moved here with its identifier. `AC-CAL-004` names `UI-019`, a shared platform rule, so it moved as well.
- From the integration spec, which no longer exists: `INT-009`. The integration spec's history is in git, `.sdd/specs/feature-integration/CHANGELOG.md` at commit `9123150`.
- `UC-04` keeps its number and was rewritten rather than moved, and is reviewed on its own. SMTP left it for `UC-19` in the [platform spec](../platform/MODULE.md); its title is now *Configure attendance policy, calendar, and HolidayAPI* and its trace is `ATT-001`–`ATT-006` and `CAL-001`–`CAL-009`. With `UC-19`, which traces `INT-001`–`INT-008`, it traces exactly what the old `UC-04` traced.
- Sections 1, 2, 5, 6, 7 and 8 and the notes were written for this spec. One note records that `INT-009` still has no use case, a gap that predates `D28`.
