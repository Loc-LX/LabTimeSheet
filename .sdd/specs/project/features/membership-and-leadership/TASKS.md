# Membership and Leadership Tasks

Tasks of [PLAN.md](PLAN.md), part ML. The shared Project contract remains in [`project/MODULE.md`](../../MODULE.md).

**State:** drafted on 30 September 2026, awaiting lead review.

Tasks run on the branch `work/fix/project/traceability`. Every task follows `TST-001`–`TST-010`: review candidate tests against the specified rule, verify observable break and hand-derived assertions, attach rule IDs to Javadoc, add targeted tests for any uncovered requirements, and confirm test suites pass without weakening assertions.

| Task | What | Done when |
|---|---|---|
| ML-01 | Establish traceability for membership and leadership rules `PRJ-004`, `AC-PRJ-001`, `AC-PRJ-002`, and `AUTH-004`. | every listed ID is named in the Javadoc of at least one passing test whose assertions prove it; focused tests, then full Maven, UI and end-to-end gates pass |

## Verification gates

Run focused integration tests first, then full `./mvnw test`, `npm run test:ui`, disposable E2E suite, and `git diff --check`. No production code or schema modifications permitted.
