# Changelog — Calendar spec

Each entry records a change to [SPEC.md](SPEC.md): the version, the date, what changed,
and why. A change that alters a rule raises the version.

## 1.0.0 — 2026-09-17

Relocation only; no rule text changed. Decision `D28` places the whole attendance policy, the global calendar and HolidayAPI in the module `calendar`, and this spec holds their rules.

- From the [attendance spec](../feature-attendance/SPEC.md): `ATT-001`–`ATT-003`, `CAL-001`–`CAL-009` and `DB-009`, with `AC-CAL-001`–`AC-CAL-005` and `AC-ATT-001`. The history of these rules before this date is in the attendance changelog.
- Scenarios are placed by one principle, which does not depend on where a scenario sat before and so also places a scenario not yet written. A scenario sits in the spec of the module that owns the rules its Given/When actually exercises. A rule named only for the expected result, or a shared platform rule, does not decide where it sits. `AC-ATT-001` configures the policy in its Given/When and names `ATT-004`–`ATT-006` only for the result, so it moved here with its identifier. `AC-CAL-004` names `UI-019`, a shared platform rule, so it moved as well.
- From the integration spec, which no longer exists: `INT-009`. The integration spec's history is in git, `.sdd/specs/feature-integration/CHANGELOG.md` at commit `9123150`.
- `UC-04` keeps its number and was rewritten rather than moved, and is reviewed on its own. SMTP left it for `UC-19` in the [platform spec](../feature-platform/SPEC.md); its title is now *Configure attendance policy, calendar, and HolidayAPI* and its trace is `ATT-001`–`ATT-006` and `CAL-001`–`CAL-009`. With `UC-19`, which traces `INT-001`–`INT-008`, it traces exactly what the old `UC-04` traced.
- Sections 1, 2, 5, 6, 7 and 8 and the notes were written for this spec. One note records that `INT-009` still has no use case, a gap that predates `D28`.
