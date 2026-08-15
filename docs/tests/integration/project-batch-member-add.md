# Integration Test Evidence

## Requirement and scenario IDs

- AUTH-001, AUTH-002, AUTH-011; PRJ-003, PRJ-004, PRJ-017; ERR-001, ERR-003; TST-001 through TST-010.
- AC-AUTH-001, AC-AUTH-010, AC-PRJ-001, AC-TST-001.

## Behavior under test

The owning Mentor adds several eligible nonmembers under one Project lock and transaction. Null, empty, duplicate, current-member, invalid, or stale/noneligible selections reject the whole batch; no valid prefix becomes a membership.

## Expected result derivation

The fixture begins with one Leader. A successful two-Intern batch must yield three current memberships. Every rejected batch leaves the eligible and stale candidate membership count at zero.

## RED

`env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw '-Dtest=ProjectControllerTest,ProjectServiceIntegrationTest' test` failed during test compilation with eight `cannot find symbol` errors for the requested `ProjectService.addMembers(long,long,List<Long>)` API. Production compiled first; the failure was the missing behavior boundary rather than the environment or fixture.

## GREEN

The focused PostgreSQL command was:

`env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=ProjectServiceIntegrationTest#ownerAddsSeveralEligibleMembersInOneLockedTransaction+memberBatchRejectsMissingDuplicateCurrentAndStaleSelectionsWithoutPartialMutation' test`

Result: 2 tests, 0 failures, 0 errors, 0 skipped against PostgreSQL 18.4. The
successful case added two memberships; the rejection case covered null, empty, duplicate,
invalid, current-member, and one-valid-plus-one-stale selections without partial persistence.

## Affected suite

`env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=ProjectServiceIntegrationTest' test`
passed 9/9 tests with no failures, errors, or skips.

The complete Project plus layer-architecture command was:

`env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=ProjectControllerTest,ProjectEntityTest,ProjectPersistenceStructureTest,ProjectServiceIntegrationTest,ProjectTaskMutationContextTest,LayerStructureTest' test`

Result: 38 tests, 0 failures, 0 errors, 0 skipped.

## External boundaries

PostgreSQL 18.4 Testcontainers provides the real schema, constraints, JPA transaction, and Project pessimistic lock path. The test does not exercise concurrent requests; existing Project locking coverage remains unchanged.

After merging exact reviewed `main` `32c8a2d315d2175760c5d4792988cd0aa5ab6dd0`, the affected command was rerun with `UiContractWebTest` included. It passed 45/45 tests with no failures, errors, or skips; the Project service portion remained 9/9 against PostgreSQL 18.4.
