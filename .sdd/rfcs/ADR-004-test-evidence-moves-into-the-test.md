# ADR-004 — Test evidence moves from a Markdown file into the test source

- **Status:** Accepted
- **Date:** 2026-09-12
- **Amends:** `TST-005`, `TST-006`, `TST-007`, `TST-008`, `AC-TST-001`
- **Removes:** `docs/tests/`, 155 files

## Context

`TST-005` required a tracked Markdown file created alongside the first failing
test, and `TST-007` listed what it had to record. The practice produced 150
evidence records across four directories.

The records were measured before this decision, not after.

| | |
|---|---:|
| Records | 150 |
| Embedding an absolute path from one contributor's machine | 104 |
| Embedding that machine's package manager prefix | 124 |
| Distinct commit identifiers cited | 117 |
| Those identifiers that resolve in this repository | 0 |
| Test classes named | 90 |
| Those classes that still exist | 88 |

Two of the named classes, `AttendanceReportFormulasTest` and
`ProjectTaskReportFormulasTest`, were renamed and the records were never updated.

The last row is the argument. A record proves that someone ran a command on a
machine that no longer exists, against a commit this repository cannot resolve.
It does not prove that the behaviour holds now. `TST-008` already says as much:
a Markdown claim shall not replace executable CI evidence.

What the records did hold, and the source did not, is the mapping from a numbered
rule to the test that protects it: 224 distinct rules traced across 88 classes.
Deleting them without moving that mapping would have lost it.

## Decision

The trace moves into the test source. A test names the numbered rules it protects
in its own Javadoc, and the run records its own execution.

| Rule | Was | Becomes |
|---|---|---|
| `TST-005` | a Markdown file is created with the first failing test | the test names the rules it protects, in the test source |
| `TST-006` | evidence directories are `unit`, `integration`, `web`, `e2e` | one test class covers one cohesive behaviour, not one production class |
| `TST-007` | the file records ten listed fields | the trace records the rule identifiers, the observable break, and the hand-derived expected value |
| `TST-008` | evidence updated on the same branch; Markdown never replaces CI | the verification run records its own commands, results, and resolved tool versions; a written claim never replaces it |

`TST-001` through `TST-004`, `TST-009` and `TST-010` are unchanged. The RED then
GREEN discipline, the ban on tests that mirror the implementation, and the rule
that a milestone is not green until affected suites pass all stand. Only the
place the evidence lives has changed.

`docs/tests/` is deleted. The rule-to-test mapping it carried was extracted first
and lives in [`../reviews/traceability.md`](../reviews/traceability.md).

## Rationale

1. **A trace in the source survives a rename.** Two records already pointed at classes that no longer exist, and nothing detected it, because a Markdown file has no compiler.
2. **A trace in the source can be read back mechanically.** The report of which rules have no test becomes generated rather than maintained, which removes the class of error where the report and the tests disagree.
3. **The records could not be reproduced.** 104 of 150 embed a path from a machine this project no longer has. A contributor on another platform cannot follow them, which A1 in [`../product.md`](../product.md) already records as a live risk.
4. **The rule that forbids this practice was already written.** `TST-008` says a Markdown claim does not replace executable evidence. The practice it forbids is the one `TST-005` mandated.

## Consequences

- 155 files leave `docs/`, which then holds one file.
- Eighty-eight existing test classes carry no identifiers yet. Until they are back-filled the mapping for them lives in the traceability record, which is stated there rather than implied.
- `AC-TST-001` changes from checking that an evidence file precedes production code to checking that the trace and the failing test do.
- The historical records are not destroyed. They remain reachable in git history, and this decision names the commit that removes them.
- Anyone reading an older evidence path in a dated record, such as those cited in [`ADR-003-verification-authority.md`](ADR-003-verification-authority.md), will find it gone. Those records are dated and stay as written; this consequence is the reason they cannot be followed.
- **One approved statement is now partly false and is left alone.** The branch-workflow sentence quoted verbatim in `AGENTS.md`, `README.md`, `TESTING.md` and `DEVELOPMENT.md` says a repair "records companion evidence". It is reproduced in all four because `scripts/verify-fix-branch-workflow.cjs` asserts it word for word, and because it is a quotation of an approved decision rather than prose this project may edit. Amending it is a separate decision with its own approval, and until then a reader should take "companion evidence" to mean the rule trace in the test.

## What this does not decide

It does not decide when the eighty-eight classes get their identifiers. That is
ordinary work, scheduled with the test suite that will be written from the
approved specification, not a condition of this amendment.
