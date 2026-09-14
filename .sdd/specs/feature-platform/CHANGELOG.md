# Changelog — Platform spec

Each entry records a change to [SPEC.md](SPEC.md): the version, the date, what changed,
and why. A change that alters a rule raises the version.

## 1.0.0 — 2026-09-14

Approved by Loc-LX. The rules moved here unchanged from the single-file
specification at `.sdd/requirements.md` (commit `fdd5ee1`), with these exceptions:

- `GOV-016` now places a feature's rules in that feature's spec and its acceptance scenarios in section 7 of the same spec, instead of in §20 of one document. The maintainer chose on 14 September 2026 to split the specification into feature specs as the playbook describes.
- §22.2 records the approval, and the questions and the code disagreement `D13` that remained open when it was given.
- §22.1 and §22.3 no longer point to a header that has gone, and say what "no open question" means now that questions for the instructor are listed.
- The recovered-appendices note records that use cases now sit in feature specs and that `UC-06` covers Task estimates and forecasts.
- The single-file header was replaced by the document table in section 1. Its quality-gate statement, that no open question remained, was dropped because it is no longer true.

Changes made to these rules during the review before approval are recorded in
[`.sdd/reviews/open-decisions.md`](../../reviews/open-decisions.md).
