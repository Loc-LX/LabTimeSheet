# Test Evidence: Task work log PostgreSQL workflow

- **Test type:** Integration
- **Requirement IDs:** `TSK-013`, `TSK-014`, `TSK-016`, `TSK-017`
- **Scenario IDs:** `I2-TSK-01`, `AC-TSK-007`, `AC-TSK-009`
- **Test class/method:** `com.lab.labtimesheet.feature.task.service.TaskWorkLogIntegrationTest`
- **Implementation commit:** `pending`

## Protected behavior

On PostgreSQL 18.4, only the current assignee writes a work log for an ACTIVE Project; former members lose access immediately. Work dates are rejected outside the Project window, before the membership join date, or in the future. Invalid minutes or a blank note reject without writing. A global day off does not block work logging and no attendance record is created. The author may correct minutes and note after reassignment; any other member cannot.

## Test method

Seven `@SpringBootTest` tests run on Testcontainers PostgreSQL with JDBC fixtures (accounts, intern profiles, project, memberships, task, optional `global_calendar_events`) and a fixed `Asia/Ho_Chi_Minh` clock at 2026-08-14. `currentAssigneeLogsWork` writes and re-reads the row; `onlyCurrentAssigneeLogsWorkAndFormerMemberIsRejected` closes the membership mid-test and proves the leader and the now-former member are both rejected with no row written; `workDateRejectedOutsideProjectDatesBeforeMembershipOrInFuture` proves all three rejections; `rejectsInvalidMinutesAndBlankNoteWithoutWriting` proves validation before write; `globalDayOffDoesNotBlockWorkLogsAndCreatesNoAttendance` inserts a day-off event and counts `attendance_records`; `authorCorrectsOwnLogAfterTaskReassignment` reassigns the task and corrects as the original author; `nonAuthorCannotCorrectAnotherMembersLog` rejects a different active member.

## Hand-derived expected result

Each rejection leaves `task_work_logs` empty. Logging on a day-off still writes one row and leaves `attendance_records` at zero for that intern/date. After reassignment the author's correction updates minutes/note and preserves the work date; the non-author correction throws `TaskNotFoundException` and changes nothing. The day-off assertion independently counts via SQL against the real schema (`work_date` column).

## RED

**Command**

```text
.\mvnw.cmd "-Dtest=TaskWorkLogIntegrationTest" test
```

**Observed result**

```text
[ERROR] Failures: 1, Errors: 0
[ERROR] taskWorkLogIntegrationTest.globalDayOffDoesNotBlockWorkLogsAndCreatesNoAttendance:
  ERROR: column attendance_records.check_in_time does not exist
[ERROR] Failures: 1, Errors: 0
[ERROR] onlyCurrentAssigneeLogsWorkAndFormerMemberIsRejected:92 expected 0 but was 1
[ERROR] Failures: 1, Errors: 0
[ERROR] authorCorrectsOwnLogAfterTaskReassignment:
  ERROR: insert or update on table "tasks" violates foreign key constraint
  "fk_tasks_assigner_project"
[INFO] BUILD FAILURE
```

Three distinct REDs: the attendance fixture query used a wrong column name, the close-membership fixture missed an EntityManager clear so JPA served stale membership state, and the reassign fixture set a foreign `assigned_by_membership_id` that was not a project membership. All were fixture defects, not production-behavior gaps.

## GREEN

**Command**

```text
.\mvnw.cmd "-Dtest=TaskWorkLogIntegrationTest" test
```

**Observed result**

```text
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
.\mvnw.cmd "-Dtest=Task*,TaskCreationIntegrationTest,TaskWorkLogIntegrationTest,TaskWorkLogDailyTotalConcurrencyTest" test

[INFO] Tests run: 81, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## External-test boundaries

These tests prove PostgreSQL behavior for a single transaction each. They do not run two concurrent correction transactions; the lock annotation is statically verified by `TaskPersistenceStructureTest` and the combined daily-total serialization is the separate `I2-TSK-02` scope. They do not exercise the browser UI or real SMTP.
