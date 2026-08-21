# Test Evidence: future attendance-policy scheduling boundary

- **Test type:** Unit
- **Requirement IDs:** `ATT-001`–`ATT-003`
- **Scenario IDs:** `AC-ATT-001`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.AttendancePolicyApplicationServiceTest#rejectsEffectivePolicyOutsideTheNextOrLaterFutureMonth`
- **Implementation commit:** pending local green milestone

## Protected behavior

Only an Admin may schedule an effective-dated attendance policy whose effective date is the first day of a future
calendar month. A date inside the current month is rejected before persistence.

## Test method

The test supplies the seeded policy timeline, fixes server time to 20 August 2026 in the policy timezone, and submits
31 August 2026. The application-shaped service call must reject the request with the stable policy-operation error.

## Hand-derived expected result

The current policy month is August 2026; 31 August is not a first day in a future month, so no repository save may
occur and `PolicyException` is expected.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
./mvnw -Dtest=AttendancePolicyApplicationServiceTest test
```

**Observed result**

```text
BUILD FAILURE during test compilation: cannot find symbol AttendancePolicyCommand.
The production command DTO and policy application boundary did not exist yet.
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=AttendancePolicyApplicationServiceTest test
```

**Observed result**

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=AttendanceLombokBoilerplateTest,AttendancePolicyApplicationServiceTest,CalendarImportServiceTest,LeaveApplicationServiceTest,AttendanceCorrectionApplicationServiceTest,AttendanceDeadlineSchedulerTest,AttendanceRecordCorrectionTest,AttendanceApplicationServiceTest test

Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

This unit test does not prove PostgreSQL persistence, optimistic replacement, Spring authorization wiring, Policy
History rendering, or historical attendance/leave calculations. Those boundaries require the affected integration and
web suites.
