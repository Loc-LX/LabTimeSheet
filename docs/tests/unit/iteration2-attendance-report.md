# Test Evidence: Attendance report consumes the accepted producer dataset

- **Test type:** Unit
- **Requirement IDs:** `I2-UI-03`, `RPT-001`, `RPT-002`, `RPT-003`, `RPT-004`
- **Scenario IDs:** `AC-ATT-006`, `AC-ATT-007`, `AC-RPT-001`, `AC-RPT-002`
- **Test class/method:** `AttendanceReportServiceTest#usesAttendanceOwnedClassificationAndExactAggregateFormulas`, `#rejectsInternDetailTargetOutsideOwnAccountBeforeAttendanceRead`, `#rendersMentorTargetPickerBeforeReadingAttendanceRows`
- **Implementation commit:** `pending local independent review`

## Protected behavior

Reporting delegates classification, expected-day counts, attendance rate, compliance rate, historical-policy scoring,
and target authorization to `AttendanceReportQueryService`. It formats that immutable Attendance-owned result for
HTML without re-reading Attendance persistence or recalculating the aggregate formulas. Intern target expansion is
rejected before the producer query; Mentor/Admin target selection uses the Account-owned eligible-Intern DTO.

## Test method

The unit test supplies one present day with late and missing-checkout violations and one absent day through the
accepted producer DTO. It verifies producer aggregate values are preserved, raw missing checkout displays `N/A`,
classification/violation labels remain distinct, and trend points use the same daily scores. Separate tests assert
the Intern own-scope guard and the authorized target-picker empty state.

## Hand-derived expected result

One present day across two expected days produces `1` present, `1` absent, and `50.00%` attendance. Scores `0.6667`
and `0` average to `33.335%`, which the producer supplies as `33.34%`; the equivalent trend values are `66.67%`
and `0.00%`. The present row has no effective checkout, so worked time is `N/A`.

## RED

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw '-DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' '-Dtest=AttendanceReportControllerWebTest,AttendanceReportServiceTest' test
```

**Observed result**

```text
Test compilation failed after the accepted Attendance producer merge: the Reporting test still constructed the old
history-derived view and the production service had no AttendanceReportQueryService dependency or accepted report
DTO mapping. This was the expected handoff RED, not a fixture or environment failure.
```

## GREEN

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw '-DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' '-Dtest=AttendanceReportControllerWebTest,AttendanceReportServiceTest' test
```

**Observed result**

```text
Java 25.0.4; Tests run: 5, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
```

## Affected suite

**Command and result**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw '-DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' '-Dtest=AttendanceControllerTest,AttendanceRequestControllerWebTest,Iteration2ProjectWorkflowWebTest,ProjectControllerTest,AccountTemplateIntegrationTest,AdminSettingsControllerWebTest,AttendanceReportControllerWebTest,AttendanceTemplateIntegrationTest,DashboardControllerWebTest,DashboardTemplateWebTest,Iteration2ComponentsWebTest,Iteration2WorkflowFragmentsWebTest,NotificationControllerWebTest,ProjectTaskFormAccessibilityWebTest,ProjectTaskReportControllerWebTest,SharedErrorTemplateWebTest,Iteration2TaskWorkflowWebTest,TaskControllerTest,UiContractWebTest' test

Java 25.0.4; Tests run: 100, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
```

## External-test boundaries

This unit evidence does not prove PostgreSQL classification/authorization calculations or browser behavior. The
accepted Attendance producer evidence proves the PostgreSQL formulas; the web and Chart.js evidence protect HTML
and progressive enhancement. No export format is claimed in Iteration 2.
