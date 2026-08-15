# Test Evidence: Windows legacy Vietnam timezone startup

- **Test type:** Integration
- **Requirement IDs:** `ARC-001`, `ARC-003`, `GOV-011`, `ATT-002`, `TST-001`, `TST-005`
- **Scenario IDs:** `N/A — user-reported cross-platform startup defect`
- **Test class/method:** `com.lab.labtimesheet.ApplicationTimeZoneIntegrationTest.mainCanonicalizesLegacyAliasBeforeStartingSpring`, `com.lab.labtimesheet.ApplicationTimeZoneIntegrationTest.canonicalizesLegacyVietnamAliasBeforePostgresConnects`, `com.lab.labtimesheet.ApplicationTimeZoneIntegrationTest.leavesSupportedSystemTimeZoneUnchanged`
- **Implementation commit:** `pending`

## Protected behavior

The executable entry point replaces the legacy Windows JVM timezone ID `Asia/Saigon` with the canonical business timezone ID `Asia/Ho_Chi_Minh` before pgJDBC opens a PostgreSQL connection. Other supported operating-system timezone IDs remain unchanged.

## Test method

The entry-point test replaces Spring startup with Mockito's existing static test seam, invokes the real `main` method with a legacy JVM default, and checks that normalization happens before Spring starts. The PostgreSQL test starts a real PostgreSQL 18.4 Testcontainer, proves pgJDBC 42.7.11 is rejected while the JVM default is `Asia/Saigon`, invokes the same startup normalization, and then opens a valid JDBC connection. A negative test verifies that an unrelated supported timezone is not overwritten.

## Hand-derived expected result

PostgreSQL does not accept `Asia/Saigon` as a startup `TimeZone`, while the approved business timezone is `Asia/Ho_Chi_Minh`. Therefore only the legacy alias is replaced, the following connection succeeds, and a supported non-Vietnam timezone remains unchanged.

## RED

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest=ApplicationTimeZoneIntegrationTest test
```

**Observed result**

```text
BUILD FAILURE during test compilation.
ApplicationTimeZoneIntegrationTest.java: cannot find symbol normalizeDefaultTimeZone()
```

The failing test established that the application had no pre-Spring normalization boundary.

A second mutation check temporarily removed the new call from `main` and ran:

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/bin:/usr/bin:/bin ./mvnw '-Dtest=ApplicationTimeZoneIntegrationTest#mainCanonicalizesLegacyAliasBeforeStartingSpring' test
```

It failed `1/1` with `expected: "Asia/Ho_Chi_Minh" but was: "Asia/Saigon"`, proving the test protects the entry-point ordering rather than only the helper.

## GREEN

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest=ApplicationTimeZoneIntegrationTest test
```

**Observed result**

```text
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=ApplicationTimeZoneIntegrationTest,LabtimesheetApplicationTests,PlatformFoundationTest,TimeConfigurationTest,CalendarDevelopmentProfileWebIntegrationTest' test
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw test
Tests run: 217, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/bin:/usr/bin:/bin ./mvnw -DskipTests -Ddoclint=all javadoc:javadoc
BUILD SUCCESS
```

A local Java 25 process was also started with `-Duser.timezone=Asia/Saigon` against a disposable PostgreSQL 18.4 database on port `55439`. Hikari connected, Flyway migrated the fresh database, Tomcat started on port `18080`, and Spring reported `Started LabtimesheetApplication`. The process shut down cleanly and the disposable database container was removed.

## External-test boundaries

The regression executes the installed pgJDBC version against PostgreSQL 18.4 and reproduces the exact rejected timezone value from the Windows report. It does not run the Windows JVM itself; the supplied Windows log is the evidence that its OS/JDK mapping produced `Asia/Saigon`.
