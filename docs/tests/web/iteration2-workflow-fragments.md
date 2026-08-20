# Test Evidence: Iteration 2 exit, transfer, and history UI fragments

- **Test type:** Web
- **Requirement IDs:** `I2-UI-02`, `AUTH-006`, `UI-001`, `UI-005`, `UI-013`, `NOT-009`
- **Scenario IDs:** `AC-UI-001`, `AC-UI-004`, `AC-UI-009`
- **Test class/method:** `com.lab.labtimesheet.feature.reporting.controller.Iteration2WorkflowFragmentsWebTest#rendersReadinessLeaderTransferDrawerProjectHistoryAndRedactedAdminHistoryShape`, `#suppressesLeaderTransferControlsWhenProducerReadinessDeniesTransfer`
- **Implementation commit:** `9172f1b10005a0d13f8b7de1c00b9f964981473a`

## Protected behavior

Shared server-rendered fragments expose a readiness summary, a Leader-gated right-side transfer drawer with multiple Task checkboxes and one recipient radio group, Project History retained-record columns, and an Admin-setting History table that accepts only a producer-supplied non-secret display value. The UI does not create transfer/history records or infer authorization.

## Test method

The test-only controller supplies producer-shaped records to the production fragments through Thymeleaf. The positive case asserts readiness text, drawer semantics, one-recipient controls, retained attribution, and a non-secret SMTP host display while asserting a secret sentinel is absent. The denied case supplies `canOpenTransfer=false` and verifies that no transfer trigger or controls render.

## Hand-derived expected result

Two unfinished Tasks (`IN_PROGRESS` and `BLOCKED`) are listed for one batch; exactly one recipient option is available. A completed Task History event retains actor and attribution text. Admin History displays `mailpit` but never the `super-secret` sentinel.

## RED

**Command**

```text
./mvnw '-Dtest=Iteration2WorkflowFragmentsWebTest' test
```

**Observed result**

```text
Both tests reached the test consumer and failed with Thymeleaf `TemplateInputException: Error resolving fragment ... exitReadiness`; the new shared workflow fragments were absent from the baseline.
```

## GREEN

**Command**

```text
./mvnw '-Dtest=Iteration2WorkflowFragmentsWebTest' test
```

**Observed result**

```text
`Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS` on Java 25.
```

## Affected suite

**Command and result**

```text
./mvnw '-Dtest=*Reporting*Test,*Dashboard*Test,*Template*Test,*Shell*Test,*Accessibility*Test,*UiContractWebTest,*AttendanceReport*Test,*ProjectTaskReport*Test,*Iteration2WorkflowFragments*Test' test

`Tests run: 63, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS` on Java 25 with PostgreSQL 18.4 Testcontainers where required. The first affected run caught and was corrected for parameter-heavy fragments leaking into unrelated `components.html` consumers; the final isolated-fragment run is the result recorded here.
```

## External-test boundaries

The fragments do not prove producer authorization, readiness calculations, atomic transfer transactions, CSRF/POST outcomes, Project History query composition, Admin-only route guards, secret storage/redaction in producer DTOs, or browser keyboard/focus behavior. Those require the reviewed Projects, Tasks, Attendance, and Platform public services plus browser/E2E evidence under their owning boundaries.
