# Test Evidence: Eligible Intern picker query

- **Test type:** Integration
- **Requirement IDs:** ACC-014, ACC-019–ACC-021, AUTH-001, PRJ-017, TST-001–TST-010
- **Scenario IDs:** AC-ACC-009, AC-ACC-010, AC-PRJ-010 (selection-eligibility support)
- **Test class/method:** com.lab.labtimesheet.feature.account.service.EligibleInternOptionIntegrationTest#listsOnlyActiveInternsWithActiveInclusiveInternshipsInPickerOrder
- **Implementation commit:** Pending

## Protected behavior

Pending, locked, deactivated, non-Intern, not-started, completed, and date-expired records must not appear in the
Account-owned Intern picker. An option is selectable only when both account and internship are ACTIVE and the
explicit business date lies within the inclusive internship range. The returned numeric user ID is the internal
submission identity, and options sort by display name then student code.

## Test method

The PostgreSQL 18.4 integration test persists valid account/profile combinations through the account feature's JPA
entities and repositories. It uses SQL only as a test fixture for future lock, deactivation, and completion states
whose production transitions are outside this change. It calls the public Account service query and compares the
complete immutable DTO sequence, including both inclusive date boundaries and unique user IDs.

## Hand-derived expected result

For business date 2026-08-14, profiles starting on that date and ending on that date remain eligible. The only
expected options are Alpha / STU-100, Alpha / STU-200, and Zeta / STU-300, in that order. Every other seeded
row fails at least one account role/state, internship state, or inclusive date condition.

## RED

**Command**

~~~text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=EligibleInternOptionIntegrationTest test
~~~

**Observed result**

~~~text
[ERROR] EligibleInternOptionIntegrationTest.java:[11,54] cannot find symbol
  symbol:   class EligibleInternOption
  location: package com.lab.labtimesheet.feature.account.model.dto
BUILD FAILURE
~~~

## GREEN

**Command**

~~~text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=EligibleInternOptionIntegrationTest test
~~~

**Observed result**

~~~text
PostgreSQL 18.4 Testcontainers started and Flyway applied V1 baseline.
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
~~~

## Affected suite

**Command and result**

~~~text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=EligibleInternOptionIntegrationTest,AccountActivationIntegrationTest,BootstrapIntegrationTest,AccountWebIntegrationTest,AuthenticationWebIntegrationTest,BootstrapOnboardingWebIntegrationTest test

Selected account reports: 13 tests, 0 failures, 0 errors, 0 skipped.

./mvnw -Dtest=AccountActivationIntegrationTest test
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

./mvnw -Dtest=LayerStructureTest test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

./mvnw test
Tests run: 105, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
~~~

## External-test boundaries

This query does not authorize Project membership itself; the consuming Project transaction must still recheck
membership and ownership invariants. It does not test the later lifecycle mutation workflows that produce locked,
deactivated, or completed rows.
