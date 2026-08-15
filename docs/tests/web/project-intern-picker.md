# Web Test Evidence

## Requirement and scenario IDs

- AUTH-001, AUTH-002, AUTH-011; PRJ-001, PRJ-004, PRJ-005, PRJ-006, PRJ-017; UI-001, UI-005, UI-014, UI-018; TST-001 through TST-010.
- AC-AUTH-001, AC-AUTH-010, AC-PRJ-001, AC-PRJ-003, AC-PRJ-009, AC-UI-005, AC-TST-001.

## Behavior under test

Project creation, direct member addition, and leadership reassignment render only server-provided eligible Intern choices. The native dialog picker exposes name, student code, and internship dates while numeric identifiers remain form values rather than visible labels. Local search, selection summaries, focus, apply, cancel, empty results, and retained server errors remain usable without adding a client API.

## Expected result derivation

The expected options are literal fixtures from the Account public DTO. Project membership history independently determines which eligible users are valid nonmembers or current-member leadership candidates. Native dialog controls keep server forms and CSRF as the mutation boundary.

## RED

`env PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run test:ui` executed the dependency-free interaction contract first: 1 test, 1 failure. Opening the picker left `dialog.open` undefined because no picker behavior existed.

The combined Java RED command was `env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw '-Dtest=ProjectControllerTest,ProjectServiceIntegrationTest' test`. After correcting test-only assertion imports, test compilation failed only because the requested `ProjectService.addMembers(long,long,List<Long>)` API did not exist. Controller rendering RED will be rerun after that producer API compiles.

After the producer API compiled, `env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw '-Dtest=ProjectControllerTest' test` ran 19 tests with 4 expected assertion failures for the missing eligible-option model, filtered multi-select markup, and retained selection rendering. A separate no-roster regression ran 1 test with 1 assertion failure because the disabled picker trigger had no reachable explanatory copy.

Independent review added rendered regressions before the correction. The same focused controller command ran 22 tests with exactly 3 failures and no errors: both closed-dialog radio contracts detected browser `required`, and stale batch recovery lacked the count-only replacement message. The new missing-selection POST contracts already passed through server Bean Validation.

## GREEN

`env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw '-Dtest=ProjectControllerTest' test` passed the initial rendered picker suite at 19/19. After adding the no-roster regression, the affected Project command below passed the expanded controller suite at 20/20.

`env PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run test:ui` passed 1/1 executable tests with no failures, proving local name/student-code filtering, summary updates, initial search focus, apply retention, cancel rollback, and opener focus restoration.

`env PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run build` succeeded with Tailwind CSS 4.3.3 and the existing local icon builder. No dependency was added.

After the review correction, the focused controller command passed 22/22. Creation and leadership radios no longer use closed-dialog browser constraint validation; missing selections re-render their server field errors. A failed member batch retains submitted option 21 when refreshed eligibility contains only 21, omits all rendered value/ID markup for stale option 22, and reports one unavailable selection without exposing its identifier.

## Affected suite

`env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=ProjectControllerTest,ProjectEntityTest,ProjectPersistenceStructureTest,ProjectServiceIntegrationTest,ProjectTaskMutationContextTest,LayerStructureTest' test` passed 38/38 tests with no failures, errors, or skips.

`env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -DskipTests compile` succeeded. Project-scoped `javadoc:javadoc` with `-Ddoclint=all` succeeded; it retained four non-fatal default-constructor warnings, including pre-existing advice/query types. `git diff --check` passed.

## External boundaries

No browser loop or Impeccable detector is run on this branch; the root owner performs one integrated pass. MockMvc proves rendered semantics and a dependency-free Node test executes the dialog/search/selection behavior with controlled DOM boundaries.

After merging exact reviewed `main` `32c8a2d315d2175760c5d4792988cd0aa5ab6dd0`, `npm ci`, the 1/1 UI test, frontend build, compile, Project-scoped Javadoc/doclint, and diff check all succeeded. The first affected Java command added the updated shared `UiContractWebTest` and passed 45/45 tests with no failures, errors, or skips.

The bounded post-review affected command reran `ProjectControllerTest,ProjectEntityTest,ProjectPersistenceStructureTest,ProjectServiceIntegrationTest,ProjectTaskMutationContextTest,LayerStructureTest,UiContractWebTest` and passed 47/47 with no failures, errors, or skips, including 9/9 Project service tests against PostgreSQL 18.4. The UI test remained 1/1; frontend build, compile, Project-scoped Javadoc/doclint, and `git diff --check` also succeeded.

The root-owned final full suite then exposed a branch-induced MVC-slice fixture RED: 213 tests ran with 0 failures and 3 errors, all `ProjectTaskFormAccessibilityWebTest` context errors because the slice did not provide the new ProjectController AccountService dependency. A focused reproduction ran the class at 3 tests, 0 failures, 3 errors and reported the same missing AccountService constructor dependency.

The smallest test-only correction supplies the controller's AccountService and Clock dependencies and the existing ProjectQueryService mock's authenticated Mentor response. The intermediate focused runs exposed each dependency in order; no production code changed. Final focused GREEN: `env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw '-Dtest=ProjectTaskFormAccessibilityWebTest' test` passed 3/3 with no failures, errors, or skips. The root owner retains the broader rerun.
