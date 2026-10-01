# Membership Exit and Transfer Tasks

Tasks of [PLAN.md](PLAN.md), part MX. The shared Project contract remains in [`project/MODULE.md`](../../MODULE.md).

**State:** drafted on 30 September 2026, awaiting lead review.

Tasks run on the branch `work/fix/project/traceability`. Every task follows `TST-001`–`TST-010`: review candidate tests against the specified rule, verify observable break and hand-derived assertions, attach rule IDs to Javadoc, add targeted tests for any uncovered requirements, and confirm test suites pass without weakening assertions.

| Task | What | Done when |
|---|---|---|
| MX-01 | Establish traceability for membership exit rules `AC-PRJ-005` and `AC-AUTH-007`. | every listed ID is named in the Javadoc of at least one passing test whose assertions prove it; focused tests, then full Maven, UI and end-to-end gates pass |

## Verification gates

Run focused integration tests first, then full `./mvnw test`, `npm run test:ui`, disposable E2E suite, and `git diff --check`. No production code or schema modifications permitted.
