# Changelog — Leave

## 1.3.0 — 2026-09-22

`D39`, corrected on second review. The notes add the two data steps for stored
`CANCELLED` rows that become `WITHDRAWN`, their order before the tightened checks, and the
need to identify a database's migration history before reading it.

## 1.2.0 — 2026-09-21

`D39`, corrected on review. `AC-DB-010` covers overlap against an `OVERDUE` range, which
V1's exclusion does not block and `AC-DB-005` does not test, and against a `WITHDRAWN`
range, which must not block. The notes record that gap and how stored `CANCELLED` rows that
were never decided become `WITHDRAWN`, to be confirmed against the data.

## 1.1.0 — 2026-09-21

Status only (`D38`). No question affecting this document is open, so its Status
moves from the inherited baseline to `APPROVED BUSINESS BASELINE`. The specification
map now defines what that field means and when it must change. No numbered rule,
acceptance row, schema or dependency changed.

## 1.0.1 — 2026-09-21

Review correction: add per-operation actor/outcome, canonical rule and acceptance
trace with explicit gaps; distinguish required contracts from related workflows
and joint checks. Follow the public Azure/Jira guidance mapped in the
[specification README](../../../README.md). No numbered rule or acceptance row changed.

## 1.0.0 — 2026-09-21

Extracted from the attendance 1.5.2 baseline under `D34`; existing rule and
acceptance rows are unchanged. [Earlier history](../../CHANGELOG.md#retained-history)
remains available. Scenarios here exercise this feature; cross-feature scenarios
remain in [MODULE.md](../../MODULE.md). No new business approval is implied.
