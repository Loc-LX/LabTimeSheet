# Test Evidence: Current business-date attendance state

- **Test type:** Unit
- **Requirement IDs:** `ATT-005`, `I1-UI-03`
- **Scenario IDs:** `I1-ATT-03`, `I1-ATT-04`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationServiceTest`
- **Implementation commit:** `8b48e281f7e860af435ae35b16c4edeb139286dc`

## Protected behavior

The public attendance service reports an eligible Intern's current business-date
state as not checked in, checked in, or checked out without exposing attendance
repositories/entities to dashboard consumers. Ineligible Interns are rejected.

## Test method

A fixed Clock, seeded policy, and mocked Spring Data/account boundaries drive the
real application service through all three persisted-record shapes. A separate
case makes account eligibility false and asserts the attendance rejection.

## Hand-derived expected result

No record means `NOT_CHECKED_IN`; a record without checkout means `CHECKED_IN`;
a record with checkout means `CHECKED_OUT`. An ineligible user produces
`INACTIVE_INTERN` instead of a state.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=AttendancePersistenceIntegrationTest test
```

**Observed result**

```text
cannot find symbol: class AttendanceCurrentState
Tests did not run because the requested public DTO/service behavior did not exist.
BUILD FAILURE
Process exited 1.
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=AttendanceApplicationServiceTest test
```

**Observed result**

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
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

The unit test does not prove PostgreSQL persistence, account fixture creation,
Spring transaction behavior, MVC rendering, or dashboard composition.
