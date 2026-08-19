# Test Evidence: Admin account editing with append-only audit trail

- **Test type:** Integration
- **Requirement IDs:** `ACC-017`, `ACC-005`, `GOV-009`
- **Scenario IDs:** (none; no prior plan item allocated Admin account editing)
- **Test class/method:** `com.lab.labtimesheet.feature.account.service.AccountAdminEditIntegrationTest`
- **Implementation commit:** `4c1fa67` (GREEN); RED `68f96a4`

## Protected behavior

`AccountService` must let an active Admin change an account's identity and, for Intern accounts, its profile fields, while keeping the role immutable and recording every change:

- **Editable fields:** normalized email, trimmed display name, and (Intern only) student code plus inclusive internship start/end dates.
- **Audit trail (GOV-009):** each changed field writes exactly one append-only row in `account_admin_edit_events` naming the authorizing Admin, the target account, the old and new values, and the change moment. Rows are never updated or removed (no delete boundary on the repository).
- **Optimistic locking:** the edit is rejected with `IllegalStateException` before any field or audit-row change when the account or Intern profile version rendered to the edit form is stale, so a concurrent Admin edit cannot be silently overwritten.
- **Uniqueness:** duplicate emails and duplicate student codes are rejected by the database unique indexes `uq_app_users_email_ci` and `uq_intern_profiles_student_code_ci` (`DataIntegrityViolationException`), with the whole transaction rolled back so no partial field change and no audit rows survive.
- **Authorization:** only an active Admin may author an edit; any other caller is rejected.

## Test method

A real PostgreSQL Testcontainer hosts the JPA entities and Flyway schema (V1 baseline plus the new V2 audit table). Setup bootstraps an Admin, activates SMTP, and creates and activates a Mentor, an Intern, and a second Intern through the real service. Tests drive the missing `AccountService.updateAccountAdminFields` method, inspect the `app_users`, `intern_profiles`, and `account_admin_edit_events` rows directly, and assert the returned detail carries the advanced optimistic-lock versions.

## Hand-derived expected result

- Editing a Mentor's email and display name returns an `AccountAdminDetail` with the new values, role `MENTOR`, status `ACTIVE`, and exactly two audit rows — `EMAIL` then `DISPLAY_NAME` — with the stored old values and the acting Admin as actor.
- Editing an Intern's email, display name, student code, and both internship dates returns the new profile values and exactly five audit rows in the field order `EMAIL`, `DISPLAY_NAME`, `STUDENT_CODE`, `INTERNSHIP_START`, `INTERNSHIP_END`.
- Re-submitting with the stale pre-edit `userVersion` throws `IllegalStateException`, leaves the first edit's values and its two audit rows untouched, and appends no new rows.
- Changing the Mentor's email to the Intern's email, or the Intern's student code to the second Intern's code, throws `DataIntegrityViolationException` with no field changes and no audit rows for either account.
- Passing the Mentor account itself as the authorizer throws `IllegalArgumentException` (not an active Admin).

## RED

**Command**

```text
env JAVA_HOME=C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11 PATH=<JDK bin>;... .\mvnw.cmd -Dtest="AccountAdminEditIntegrationTest,AccountAdminEditWebIntegrationTest" "-DargLine=-Duser.timezone=Asia/Ho_Chi_Minh" test
```

**Observed result**

```text
[ERROR] COMPILATION ERROR :
.../AccountAdminEditIntegrationTest.java:[14,57] cannot find symbol
  symbol:   class AccountAdminEditEvent
  location: package com.lab.labtimesheet.feature.account.model.entity
.../AccountAdminEditIntegrationTest.java:[15,57] cannot find symbol
  symbol:   class AccountAdminEditField
.../AccountAdminEditIntegrationTest.java:[16,55] cannot find symbol
  symbol:   class AccountAdminEditEventRepository
  location: package com.lab.labtimesheet.feature.account.repository
.../AccountAdminEditIntegrationTest.java:[59,13] cannot find symbol
  symbol:   class AccountAdminEditEventRepository
[ERROR] 4 errors
[INFO] BUILD FAILURE
```

The test could not compile because the account feature exposes no `AccountService.updateAccountAdminFields` API and no audit entity or repository — exactly the missing I2-PLAT-07 behavior. The RED state also proves the `AccountAdminDetail` web projection is missing its optimistic-lock version accessors (`userVersion`, `profileVersion`) referenced by the web test.

## GREEN

**Command**

```text
env JAVA_HOME=C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11 PATH=<JDK bin>;... .\mvnw.cmd -Dtest="AccountAdminEditIntegrationTest,AccountAdminEditWebIntegrationTest" "-DargLine=-Duser.timezone=Asia/Ho_Chi_Minh" test
```

**Observed result**

```text
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in com.lab.labtimesheet.feature.account.service.AccountAdminEditIntegrationTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Notes: the duplicates are guarded by the pre-existing database unique indexes, matching the account-creation flow, so the service intentionally lets the constraint violation surface (`DataIntegrityViolationException`) and the whole transaction rolls back — no pre-check query needed, no audit rows leak. The `V2__account_admin_edit_events.sql` migration is modeled on the existing `attendance_correction_events` append-only pattern, with a CHECK constraint restricting `field_name` to the five enum names.

## Affected suite

**Command and result**

```text
env JAVA_HOME=C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11 PATH=<JDK bin>;... .\mvnw.cmd -Dtest="com.lab.labtimesheet.feature.account.**" "-DargLine=-Duser.timezone=Asia/Ho_Chi_Minh" test
[INFO] Tests run: 45, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Full suite (after extending the approved catalog contract to 24 tables / 58 foreign keys for the new audit table):

```text
env JAVA_HOME=C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11 PATH=<JDK bin>;... .\mvnw.cmd "-DargLine=-Duser.timezone=Asia/Ho_Chi_Minh" test
[INFO] Tests run: 246, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## External-test boundaries

These tests prove the field-level edit semantics, optimistic-lock rejection, database uniqueness rollback, and append-only audit rows against PostgreSQL. They do not prove the rendered edit form, the controller routes and authorization, the CSRF-protected submit flow, or the redirect and error re-rendering states — proven at the web layer (`AccountAdminEditWebIntegrationTest`).