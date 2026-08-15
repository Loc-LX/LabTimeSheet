# Test Evidence: Checkout eligibility and stable conflict outcomes

- **Test type:** Unit
- **Requirement IDs:** `ATT-007`, `ATT-008`, `ATT-010`, `ATT-012`
- **Scenario IDs:** `AC-ATT-003`, `AC-ATT-004`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationServiceTest#rejectsCheckoutWhenInternIsNoLongerEligibleForPersistedWorkDate`, `#translatesConcurrentCheckInUniqueConflictToStableDuplicateRejection`, `#translatesConcurrentCheckoutVersionConflictToStableDuplicateRejection`
- **Implementation commit:** `4c39df70e1f901e232669e9090ff5d21393519f0`

## Protected behavior

Checkout revalidates active internship eligibility for the attendance row's
persisted work date. A terminal Intern cannot checkout after checking in.
Database uniqueness and optimistic-lock races are translated to stable duplicate
punch rejection codes instead of leaking persistence exceptions.

## Test method

Plain JUnit and Mockito drive the production transactional application service
with a fixed Clock, attached policy, persisted row, AccountService eligibility,
and repository exceptions. Account state remains behind its public service API.

## Hand-derived expected result

False date-aware eligibility returns `INACTIVE_INTERN` before raw checkout is
saved. A check-in uniqueness race returns `ALREADY_CHECKED_IN`; a checkout
version race returns `ALREADY_CHECKED_OUT`.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=AttendanceApplicationServiceTest#rejectsCheckoutWhenInternIsNoLongerEligibleForPersistedWorkDate test
```

**Observed result**

```text
Expected AttendanceException(INACTIVE_INTERN) but was NullPointerException after
the service continued to save checkout without calling AccountService eligibility.
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE
Process exited 1.
```

The conflict regressions were also observed RED in the combined focused run:

```text
DataIntegrityViolationException: concurrent unique conflict
ObjectOptimisticLockingFailureException: optimistic locking failed
Both escaped AttendanceApplicationService instead of stable AttendanceException values.
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
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
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

The account platform has no Iteration 1 terminal-state mutation API, so the
completed/withdrawn state is represented through its public date-aware eligibility
result. The companion PostgreSQL concurrency test proves the real unique conflict.
