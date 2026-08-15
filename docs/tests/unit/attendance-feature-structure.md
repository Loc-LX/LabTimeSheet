# Test Evidence: Attendance feature package and JPA boundaries

- **Test type:** Unit
- **Requirement IDs:** `ARC-005`, `OPS-020`
- **Scenario IDs:** `I1-ATT-01` through `I1-ATT-05` structural gate
- **Test class/method:** `com.lab.labtimesheet.architecture.AttendanceLayerStructureTest`
- **Implementation commit:** `8b48e281f7e860af435ae35b16c4edeb139286dc`

## Protected behavior

Attendance/calendar code lives under one `feature.attendance` boundary with
controller, model, model.dto, model.entity, repository, service, and exception
layers. The superseded feature-first and global-layer classes are absent, and
application services do not depend on `JdbcTemplate`.
Attendance does not map or expose the account feature's `app_users` or
`intern_profiles` tables.

## Test method

Plain JUnit loads the required public classes by authoritative package name,
proves superseded class names are absent, verifies the query repository is a
Spring Data repository, reflects over application-service dependencies, and
proves that attendance-owned account entities/repositories cannot be loaded.

## Hand-derived expected result

Seven representative classes load from `feature.attendance` internal layers;
the old `attendance.AttendanceService` and global `controller.AttendanceController`
do not load; query access implements Spring Data `Repository`; no checked
application service has a `JdbcTemplate` field.
The four forbidden attendance-owned account entity/repository class names do
not load.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=AttendanceLayerStructureTest test
```

**Observed result**

```text
ClassNotFoundException: com.lab.labtimesheet.feature.attendance.controller.AttendanceController
ClassNotFoundException: com.lab.labtimesheet.feature.attendance.repository.AttendanceQueryRepository
Tests run: 2, Failures: 0, Errors: 2, Skipped: 0
BUILD FAILURE
Process exited 1 because the implementation still used the superseded package layout.
```

The account-boundary assertion was separately observed RED after the final
feature package move:

```text
./mvnw -Dtest=AttendanceLayerStructureTest test
AttendanceLayerStructureTest.attendanceDoesNotMapOrExposeAccountFeatureTables:
Expecting code to raise a throwable.
Tests run: 3, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE
Process exited 1 because attendance still owned shadow AppUser/InternProfile entity and repository types.
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=AttendanceLayerStructureTest test
```

**Observed result**

```text
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Process exited 0.
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest='*Attendance*Test' test
Tests run: 32, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Process exited 0.
```

## External-test boundaries

This test proves source/package and dependency shape, not Spring context startup,
PostgreSQL queries, MVC behavior, or the final platform account-service wiring.
