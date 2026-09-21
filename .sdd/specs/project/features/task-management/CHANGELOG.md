# Changelog — Task management

## 1.2.0 — 2026-09-21

D37 aligns TSK-007 to explicitly require a non-deleted Task for assignee transitions, matching TSK-023 and the Task state transition table, and adds AC-TSK-021 to cover the rejection of status transitions on soft-deleted Tasks.

## 1.1.0 — 2026-09-21

D35 sets TSK-003 creation to TODO, constrains every TSK-007 unblock to the latest pre-block state under TSK-025, and adds the Task transition table. Updates AC-TSK-003/016; adds AC-TSK-019/020 for creation, invalid targets and repeated blocks. Existing actor permissions and history remain binding.

Approved business defaults are recorded in `D35`; this revision does not implement
Java behavior or approve a technical plan.

## 1.0.1 — 2026-09-21

Review correction: add per-operation actor/outcome, canonical rule and acceptance
trace with explicit gaps; distinguish required contracts from related workflows
and joint checks. Follow the public Azure/Jira guidance mapped in the
[specification README](../../../README.md). No numbered rule or acceptance row changed.

## 1.0.0 — 2026-09-21

Extracted from the project 1.4.2 baseline under `D34`; existing rule and
acceptance rows are unchanged. [Earlier history](../../CHANGELOG.md#retained-history)
remains available. Scenarios here exercise this feature; cross-feature scenarios
remain in [MODULE.md](../../MODULE.md). No new business approval is implied.
