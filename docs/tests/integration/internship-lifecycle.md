# Test Evidence: internship lifecycle activation and terminal guards

- **Test type:** Integration
- **Requirement IDs:** `I2-PLAT-03`
- **Test classes:** `com.lab.labtimesheet.feature.account.service.InternshipLifecycleIntegrationTest`, `com.lab.labtimesheet.feature.account.service.AccountActivationIntegrationTest`
- **Implementation commit:** pending

## Protected behavior

Due active Internships start through the same guarded transition whether reached by an eligibility request or the
Vietnam-zone scheduler. Completion and withdrawal re-check current Project leadership and unfinished Tasks using
service/DTO boundaries, preserve completed history, and expire registered sessions on withdrawal.

## RED

The lifecycle and cross-feature guard tests initially failed to compile because the completion/withdrawal domain
methods, Project guard contract, Task count boundary, and lifecycle services did not yet exist. The separate
HolidayAPI RED run is recorded in `holiday-api.md`.

## GREEN

**Focused commands**

```text
.\mvnw.cmd '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=InternProfileTest,ProjectInternshipGuardServiceTest,TaskQueryServiceTest,InternshipTerminalGuardTest,InternshipLifecycleServiceTest' test
.\mvnw.cmd '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=InternshipLifecycleIntegrationTest,AccountActivationIntegrationTest' test
```

**Observed result**

```text
Lifecycle unit coverage: 14 tests passed.
InternshipLifecycleIntegrationTest: 5 tests passed.
AccountActivationIntegrationTest: 2 tests passed.
BUILD SUCCESS
```

The test clock and Maven JVM use `Asia/Ho_Chi_Minh`; this avoids the repository's Windows/JVM legacy
`Asia/Saigon` mismatch and matches the application's business-timezone contract.

## Covered scenarios

- Inclusive start-date activation and rejection before the start date.
- Request-time activation before eligibility evaluation and hourly Vietnam-zone scheduler activation.
- `ACTIVE -> COMPLETED` with authentication retained and mutation eligibility removed.
- `NOT_STARTED/ACTIVE -> WITHDRAWN` with authentication disabled and registered sessions expired.
- Completion blocked by current Project leadership.
- Withdrawal blocked by an unfinished, non-deleted Task across retained membership history.
- Account code depends on Project/Task services only through DTO/service boundaries; no Project/Task repository or
  entity imports are present in the Account feature.

## External-test boundaries

The scheduler is a timeliness mechanism; request-time activation is the correctness mechanism. The tests do not claim
that a scheduler tick is instantaneous, and they do not change attendance calendar interpretation or Task state rules.
