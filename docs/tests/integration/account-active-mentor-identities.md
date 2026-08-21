# Test Evidence: Active global Mentor identity projection

- **Test type:** Integration
- **Requirement IDs:** `I2-ATT-PLAT-04` (root-authorized producer slice); `AUTH-003`; `NOT-002`
- **Scenario IDs:** `AC-AUTH-002` global Mentor decision queue; active-account filtering; deterministic recipient ordering
- **Test class/method:** `com.lab.labtimesheet.feature.account.service.ActiveMentorIdentityIntegrationTest#returnsOnlyActiveMentorsInAscendingIdOrderThroughImmutableProjection`
- **Implementation commit:** `a6338fec71f60e45462145d56f0d97de3083f7e8`

## Protected behavior

Account exposes one concrete cross-feature query that returns every active global Mentor as immutable, non-secret
`AccountIdentity` DTOs ordered by ascending account ID. Pending, locked, and deactivated Mentors and all non-Mentor
roles are excluded. The repository uses a constructor projection, so this recipient-boundary read does not hydrate
`AppUser` entities or acquire lifecycle locks.

## Test method

The PostgreSQL 18.4 Testcontainers test persists an active Admin, two active Mentors inserted with deliberately
different display-name order, a pending Mentor, a locked Mentor, a deactivated Mentor, and an active Intern. It
invokes the real `AccountService` with a test-only dynamic proxy around the real `AppUserRepository`. The proxy
records the constructor-projection method and fails if entity-producing or pessimistic-lock lookup methods are used.
Assertions independently verify the exact active-Mentor DTO values, ascending IDs, record/final-field immutability,
and the absence of entity or lock lookups.

## Hand-derived expected result

Exactly the two active Mentor rows are returned, in database ID order regardless of display name. Each result is an
`AccountIdentity` record containing only the expected ID, normalized email, display name, `MENTOR` role, and `ACTIVE`
status. No pending, locked, deactivated, Admin, or Intern account appears.

## RED

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest=ActiveMentorIdentityIntegrationTest test
```

**Observed result**

```text
2026-08-21T01:20:02+07:00 — BUILD FAILURE during testCompile;
ActiveMentorIdentityIntegrationTest could not find symbol AccountService.activeGlobalMentorIdentities().
This was the expected missing public Account producer boundary; no container or behavioral execution occurred.
```

## GREEN

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest=ActiveMentorIdentityIntegrationTest test
```

**Observed result**

```text
PostgreSQL 18.4 Testcontainers; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
```

## Affected suite

**Command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -Dtest='**/feature/account/**/*Test' test
```

```text
Exit 0; PostgreSQL 18.4 Testcontainers; 45 tests, 0 failures, 0 errors, 0 skipped.
```

The full branch suite was run on the same tree:

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q test
```

```text
Exit 0; PostgreSQL 18.4 Testcontainers; 267 tests, 0 failures, 0 errors, 0 skipped.
```

## Additional verification

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -Dtest=LayerStructureTest,PlatformFoundationTest,AttendanceLayerStructureTest,ReportingArchitectureTest,ProjectPersistenceStructureTest,TaskPersistenceStructureTest test
Exit 0; PostgreSQL 18.4/Flyway replay and architecture checks; 12 tests, 0 failures, 0 errors, 0 skipped.

JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -q -DskipTests compile
Exit 0; BUILD SUCCESS.

JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -q -DskipTests -Ddoclint=all javadoc:javadoc
Exit 0; BUILD SUCCESS.

git diff --check
Exit 0; no whitespace errors.
```

## External-test boundaries

This evidence proves only the Account service/repository identity-projection boundary. It does not prove Attendance
leave/correction authorization or mutation, notification persistence/deduplication/delivery, SMTP behavior, browser
flows, or any consumer transaction/lock sequence. The dynamic proxy is test-only and adds no production hook or
dependency. No migration, direct SQL, foreign repository/entity import, notification dependency, or external provider
is introduced.
