# Open decisions blocking approval of the specification

[The specification](requirements-specification.md) cannot be approved until the
questions below are answered by a person, not by reading the code. Each one is a
place where the document currently states something that no recorded human
decision supports.

Section 1.1 of the specification names who may answer. Until an answer is
recorded here and applied to the numbered rules, the specification stays
unapproved and no test written against the affected rule is authoritative.

## How this document is used

1. Whoever holds authority answers each question below.
2. The answer is written into the Decision column with a date and a name.
3. The affected numbered rules are edited to match.
4. Anything that contradicts the answer, in code or in tests, is then a defect.

A decision recorded here outranks the current behaviour of the application. Where
they disagree, the application is wrong.

## D1. May an Admin see one Intern's detailed attendance?

**Affects** `RPT-004`, `RPT-011`, and the permission matrix in section 5.2.

This is the only question on this page where the running application was used to
settle a business rule, and it should not have been.

| | |
|---|---|
| What the specification says today | An active Admin may view detailed Intern attendance, with the same target scope, page, export, and dataset as an active Mentor. |
| Why it says that | The shipped code implements it, and the change was made to stop the document contradicting the product. |
| What the earlier text said | Admins have no Attendance-report scope or report dataset. |

The rule has moved three times, and no written reason survives for any of the
moves. [ADR 0002](../adr/0002-admin-attendance-report-scope.md) reconstructs the
sequence from commits: granted on 17 August 2026, the specification carrying it
deleted on 20 August, withdrawn on 27 August, the specification deleted again on
29 August, restored on 30 August.

Three facts worth weighing before answering.

- An Admin already administers accounts, the internship lifecycle, and attendance policy. Attendance detail is adjacent to work they already do.
- An Admin has no Project or Task report scope at all. Granting attendance detail does not open Project delivery data.
- Attendance detail identifies a named Intern's presence day by day. Whether a system administrator should hold that is a privacy question, not a technical one.

**Options**

| Option | Consequence |
|---|---|
| A. Admin may see it. | The specification stays as written. No code changes. |
| B. Admin may not see it. | `RPT-004` and `RPT-011` revert, the permission matrix changes, and the reporting code and its tests must be changed to deny Admin. |

**Decision:**
**Decided by:**
**Date:**

## D2. Is the external issue tracker permanently out of scope?

**Affects** `GOV-015`.

The specification now states that version 1 will not integrate with Jira or
Tempo, will not mirror issues, sprints, or story points, will not add a
`SUBMITTED` Task state with Leader acceptance and rejection, will not offer
Weekly or Monthly report presets, and will not perform continuous replanning.

That rule was written from a decision record which has since been removed from
the repository, so nothing now supports it except this sentence. If the exclusion
is real it belongs in the specification. If it was one person's working note it
should not be a numbered rule.

**Options**

| Option | Consequence |
|---|---|
| A. Confirm the exclusion. | `GOV-015` stays. Any future integration needs a new rule and a recorded decision. |
| B. Withdraw it. | `GOV-015` is deleted and the question of external integration is simply unanswered rather than answered no. |

**Decision:**
**Decided by:**
**Date:**

## D3. Does a future feature get its own specification document?

**Affects** `GOV-016`.

The specification now states that it is the system-wide layer, that a later
feature must not be appended to it, and that a feature needing its own
specification gets a separate document naming this one as parent.

This is a working method, not a business rule. It was added to stop the pattern
that already damaged this project, where a second document claimed precedence
over the requirements and the two then disagreed. It is stated here because a
numbered rule that describes how people work should be one the team agreed to.

**Options**

| Option | Consequence |
|---|---|
| A. Keep it as a numbered rule. | The layering is enforceable through review, and a feature document that claims precedence is a defect. |
| B. Move it out of the numbered rules. | It becomes guidance in the contributing guide, and nothing in the specification prevents the pattern recurring. |

**Decision:**
**Decided by:**
**Date:**

## D4. Must test tooling versions be pinned exactly?

**Affects** `ARC-004`, and section 22.3 where this is recorded as OQ-1.

`ARC-004` pins Node and Tailwind and says nothing about the test toolchain. A
committed contract test asserts an exact Playwright version that `package.json`
no longer matches. One of the two encodes a decision nobody wrote down, and the
mismatch is why the pipeline on `main` fails.

| Where | Value |
|---|---|
| `src/test/js/playwright-contract.test.mjs`, line 8 | `1.55.0`, asserted for equality |
| `package.json` | `^1.62.1` |

**Options**

| Option | Consequence |
|---|---|
| A. Pin test tooling exactly. | A new numbered rule states the pinning policy, and `package.json` is corrected to the asserted version. |
| B. A compatible range is enough. | The assertion has no requirement behind it and is retired. |

**Decision:**
**Decided by:**
**Date:**

## D5. Do the recovered appendices stay in the baseline?

**Affects** Appendices C through F, roughly 1 000 lines.

They were recovered from a detailed specification deleted on 20 August 2026, and
they hold the only written record of the use-case flows, the screen inventory
with access rules, the desktop layout intent, and the system message catalogue.

They also predate the last two iterations and are stale in four known ways.

- No use case covers the Daily Project Work Report.
- No use case or screen covers Task estimates and remaining effort forecasts.
- The screen inventory lists 48 screens against 43 templates that exist today.
- The Admin reporting scope in them predates D1 above.

They are marked explanatory rather than normative, and the numbered rules win
where they disagree. The question is whether a baseline meant for someone
inheriting the project should contain a thousand lines that are knowingly out of
date.

**Options**

| Option | Consequence |
|---|---|
| A. Keep and re-derive them. | The baseline is complete, and someone must bring four areas up to date. |
| B. Keep them as they are. | The reader gets the flows and the screen list, and must respect the staleness warning. |
| C. Remove them. | The document shrinks to the numbered rules and the acceptance catalogue, and the use-case flows and message catalogue exist nowhere. |

**Decision:**
**Decided by:**
**Date:**

## Decided without escalation

These were settled by editing rather than by a business judgement. They are
listed so that a reviewer can object rather than discover them later.

| Rule | What changed | Basis |
|---|---|---|
| `SEC-011` | The word "appropriate" was removed. The rule now states the properties the security headers must have, and the exact directive values sit in the acceptance scenario. | A requirement states a property; a scenario states a value. |
| `OPS-006` | Readiness now names the datasource and the completed Flyway migration set instead of "dependencies appropriate to the active profile". | The earlier wording could not be checked by any reviewer. |
| `DB-010` | The fixed count of 23 tables was removed. The rule now says the diagram covers the 23 baseline tables and that the table added by migration `V2` is not yet drawn. | The schema has 24 tables. The rule asserted a number that was false. |
| `AUTH-010` | Replaced by a pointer to `RPT-005`, which already carried the same rule. | Two rules stating one thing drift apart. |
| `TSK-017` | Replaced by a pointer to `CAL-009` and `GOV-004`, which already carried the same rule. | Same reason. |

## What acceptance of the specification still requires

Answering the five questions above closes the gaps that are visible from inside
the document. It does not make the document correct.

Two hundred and sixty of the 269 numbered rules were written by an earlier author
and have not been changed. They are internally consistent and they match the
code. Neither of those facts establishes that they describe what the laboratory
actually wants. Only a reading by the authority named in section 1.1 can
establish that, and section 22.2 already requires it.
