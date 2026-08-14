# Test Evidence

TDD is mandatory. Every feature test must have one companion Markdown record
in the directory matching its test type:

- `unit/` — isolated state, calculation, and domain behavior.
- `integration/` — PostgreSQL, Flyway, repository, transaction, and module integration.
- `web/` — MockMvc, Thymeleaf, validation, and security behavior.
- `e2e/` — cross-module, browser, or full user journeys.

Copy that directory's `_TEMPLATE.md` and keep every heading. One evidence file
may cover a cohesive parameterized scenario set, but it must name every
requirement and scenario ID it protects.

An evidence record is complete only when it contains the observed RED failure,
the observed GREEN result, the affected-suite result, and honest external-test
boundaries. Commands and relevant output are copied exactly; prose such as
"passed locally" is not evidence.
