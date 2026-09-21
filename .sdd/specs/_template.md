# [Feature Name] Spec

**Version:** 0.1.0 · **Owner:** @name · **Status:** DRAFT · **Date:** YYYY-MM-DD

<!-- Status is one of DRAFT, INHERITED BASELINE; SEE OPEN QUESTIONS, or
     APPROVED BUSINESS BASELINE. What each one means, and when it must change,
     is in the specification map: README.md, "What the Status field means". -->

Copy this file to `.sdd/specs/<module>/features/<feature>/SPEC.md` and add a
`CHANGELOG.md` beside it. Link to `../../MODULE.md`, the shared module contract.
A module's MODULE.md uses these same eight sections and its own CHANGELOG.md.
A rule lives in exactly one contract (`GOV-016`); another contract that needs it
points to it instead of restating it. See [the specification map](README.md).

Group complete workflows into features. Login and Logout can be headings inside
Authentication; each operation does not need its own folder or branch. Shared rules
and scenarios that exercise several features belong in MODULE.md. Feature PLAN.md
and TASKS.md are written in their later phases; do not create empty placeholders.

## 1. Context & Goal
<!-- Why the feature exists and the problem it solves. -->

## 2. Actors & Roles
<!-- Who interacts and with what authority. Point to the permission matrix in the platform spec, §5.2. -->

## 3. Functional Requirements
<!-- Numbered rules under the feature's prefix, in EARS: WHEN / WHILE / WHERE … THE system SHALL … -->
<!-- Give individual operations clear headings, followed by the canonical rules and use cases. -->

| ID | Requirement |
|---|---|

## 4. Non-functional Requirements
<!-- Measurable: performance, security, accessibility. Shared rules stay in the platform spec. -->

## 5. Data
<!-- Tables and constraints this feature owns. The full model is §19 of the platform spec. -->
<!-- Required contracts: provider, fact/behavior consumed, canonical rule trace.
     Related workflows and joint checks: coordination or combined evidence, without
     claiming implementation order. PLAN chooses actual delivery predecessors.
     A feature folder is not a new module boundary. -->

## 6. Error Handling
<!-- WHERE <failure>, THE system SHALL <response>. -->

## 7. Acceptance Criteria
<!-- One scenario per testable behavior. Which spec a scenario lives in is decided by the platform spec, §20, "Where a scenario lives". -->

### Operation and acceptance map

<!-- One row per operation heading. Link canonical rule/scenario owners; name uncovered
     behavior or an open decision explicitly. No scenario reference means no evidence,
     not implicit approval. Actor/outcome describes the user's result, not implementation.
     See README.md for the public Azure/Jira guidance and the local mapping. -->

| Operation | Actor and observable outcome | Canonical rules | Existing acceptance scenarios | Acceptance boundary or open decision |
|---|---|---|---|---|

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|

## 8. Out of Scope
<!-- What this feature explicitly does not do. -->

## Notes / Open Questions
<!-- Questions not yet answered. A spec is not approved while one blocks implementation. -->
