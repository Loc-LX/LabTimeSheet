# Changelog — Internship lifecycle

## 1.3.0 — 2026-09-21

Status only (`D38`). No question affecting this document is open, so its Status
moves from the inherited baseline to `APPROVED BUSINESS BASELINE`. The specification
map now defines what that field means and when it must change. No numbered rule,
acceptance row, schema or dependency changed.

## 1.2.0 — 2026-09-21

D36 amends ACC-022 to exclude soft-deleted Tasks from internship completion/withdrawal readiness, including retained unfinished Tasks on COMPLETED Projects. Adds AC-ACC-019 for the reproduced dead end, open-Project cases, other live work and independent leadership blockers. AC-ACC-018 clarifies that its negative Task fixture is non-deleted. UC-18, operation/dependency summaries and notes follow the same predicate; no Task status/history is rewritten.

The maintainer approved this amendment in `D36`; runtime implementation and
technical-plan approval remain separate.

## 1.1.0 — 2026-09-21

D35 changes ACC-022 to exclude unfinished Tasks in CANCELLED Projects from completion/withdrawal readiness. Adds AC-ACC-018 for both terminal actions, other blocking work/leadership, session effects and retained cancellation history; aligns UC-18, state-table guards and dependency notes.

Approved business defaults are recorded in `D35`; this revision does not implement
Java behavior or approve a technical plan.

## 1.0.1 — 2026-09-21

Review correction: add per-operation actor/outcome, canonical rule and acceptance
trace with explicit gaps; distinguish required contracts from related workflows
and joint checks. Follow the public Azure/Jira guidance mapped in the
[specification README](../../../README.md). No numbered rule or acceptance row changed.

## 1.0.0 — 2026-09-21

Extracted from the internship 1.1.1 baseline under `D34`; existing rule and
acceptance rows are unchanged. [Earlier history](../../CHANGELOG.md#history-before-the-d34-feature-split)
remains available. Scenarios here exercise this feature; cross-feature scenarios
remain in [MODULE.md](../../MODULE.md). No new business approval is implied.
