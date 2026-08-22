# Test Evidence: Account historical Intern reporting window

- **Test type:** Integration
- **Requirement IDs:** `ACC-025`, `ATT-018`
- **Scenario IDs:** Attendance round-3 review producer request; no separate numbered scenario assigned
- **Test class/method:** `com.lab.labtimesheet.feature.account.service.HistoricalInternReportingWindowIntegrationTest`
- **Implementation commit:** pending

## Protected behavior

Account exposes an immutable, persistence-free historical reporting window for an Intern. Activation and terminal
timestamps are converted to Asia/Ho_Chi_Minh business dates; activation and terminal dates are inclusive. Completed
and withdrawn profiles retain eligible pre-terminal empty workdays even after their current lifecycle status changes.
Pending, not-started, manually deactivated active profiles, missing profiles, and unknown accounts return an empty
Optional rather than leaking Account entities to Attendance.

## Test method

The PostgreSQL 18.4 Testcontainers test persists Account-owned users and profiles, exercises active-to-completed and
active-to-withdrawn terminal states, and calls only `AccountService.historicalInternReportingWindow`. Assertions cover
the date before activation, dates before/on/after terminal action, current deactivation after withdrawal, and
non-eligible lifecycle cases. The DTO rejects null dates at its public `eligibleOn` boundary; an empty Optional is the
service result for an account that cannot establish a historical window.

## Hand-derived expected result

An Intern activated on local August 14 is ineligible on August 13. A terminal action at UTC August 20 16:00 is local
August 20 23:00, so August 19 and August 20 are eligible and August 21 is not. The configured internship end date is
also an upper bound. The terminal date is retained for both COMPLETED and WITHDRAWN profiles; a deactivated active
profile has no terminal Intern window.

## RED

**Command**

```text
./mvnw -q -Dtest=HistoricalInternReportingWindowIntegrationTest test
```

**Observed result**

```text
2026-08-22T15:54:00+07:00 — BUILD FAILURE during testCompile: missing InternReportingWindow DTO and
AccountService.historicalInternReportingWindow boundary. This was the expected producer-contract RED.
```

## GREEN

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH \
DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock \
./mvnw -q -Dtest=HistoricalInternReportingWindowIntegrationTest test
```

**Observed result**

```text
2026-08-22T15:55:18+07:00 — PostgreSQL 18.4 Testcontainers; Tests run: 3, Failures: 0, Errors: 0, Skipped: 0;
BUILD SUCCESS.
```

## Affected suite

**Command and result**

```text
Account/security affected suite including HistoricalInternReportingWindowIntegrationTest: pending after this
producer milestone; the focused PostgreSQL producer test passed 3/3 above.
```

## External-test boundaries

This producer evidence does not change or prove Attendance report assembly, export formatting, browser behavior, or
cross-feature consumer integration. Attendance must consume only this public Optional/DTO boundary and must add its
own report regressions. It does not expose Account entities or terminal persistence fields.
