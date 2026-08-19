# Test Evidence: Admin account-edit form and submit flow

- **Test type:** Web (MockMvc over real PostgreSQL Testcontainer)
- **Requirement IDs:** `ACC-017`, `ACC-005`, `GOV-009`
- **Scenario IDs:** (none; no prior plan item allocated Admin account editing)
- **Test class/method:** `com.lab.labtimesheet.feature.account.controller.AccountAdminEditWebIntegrationTest`
- **Implementation commit:** `4c1fa67` (GREEN); RED `68f96a4`

## Protected behavior

The browser layer renders and drives the Admin account-edit flow: an Admin-only `GET /admin/accounts/{id}/edit` that pre-fills the current values and hides the immutable role, and an Admin-only `POST /admin/accounts/{id}/edit` that saves the changed identity and Intern profile fields and redirects to the detail page. Duplicate emails and stale optimistic-lock versions re-render the edit form with an error instead of crashing or silently overwriting a concurrent change, and non-Admin callers are denied at the security layer.

## Test method

A real PostgreSQL Testcontainer hosts the JPA entities and Flyway schema. Setup bootstraps an Admin, activates SMTP, creates a Mentor and an Intern through the real `/admin/accounts` web flow, and activates both through the real `/activate` web flow. Tests then perform GET/POST requests through MockMvc with CSRF and role-scoped principals, reading the rendered HTML, the redirect targets, and the persisted account rows.

## Hand-derived expected result

- `GET /admin/accounts/{id}/edit` by an Admin renders `accounts/edit` containing "Edit account", the current display name, and the current email; the same GET by an Intern user returns `403`.
- A valid Mentor edit posts and redirects to `/admin/accounts/{id}?updated`; the persisted email and display name are updated while role and status stay `MENTOR` / `ACTIVE`, and the detail page shows the new values.
- Posting the Mentor's email as the Intern's email re-renders `accounts/edit` with "already exists" (mapped from `uq_app_users_email_ci` to the email field).
- Posting a stale `userVersion` (current minus one) re-renders `accounts/edit` with "changed by another request".
- A valid Intern edit with changed internship dates redirects to `?updated` while role and status stay `INTERN` / `ACTIVE`.
- `POST /admin/accounts/{id}/edit` by a Mentor principal returns `403`.

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
...
[ERROR] 4 errors
[INFO] BUILD FAILURE
```

Compilation of the whole test module was blocked by the missing `AccountService` edit API and audit entities, so the web tests could not run at all; the `AccountAdminEditWebIntegrationTest` exercises `/admin/accounts/{id}/edit` routes that did not exist (which would otherwise return 404/403) and the `AccountAdminDetail` version accessors that did not exist.

## GREEN

**Command**

```text
env JAVA_HOME=C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11 PATH=<JDK bin>;... .\mvnw.cmd -Dtest="AccountAdminEditIntegrationTest,AccountAdminEditWebIntegrationTest" "-DargLine=-Duser.timezone=Asia/Ho_Chi_Minh" test
```

**Observed result**

```text
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- in com.lab.labtimesheet.feature.account.controller.AccountAdminEditWebIntegrationTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Notes: the edit form carries the account and Intern profile optimistic-lock versions as hidden fields (`userVersion`, `profileVersion`) so a stale submit re-renders the form with a conflict error; the controller reuses the create-flow `rejectUniquenessViolation` mapping so database duplicates become field-specific "already exists" errors. The role is displayed read-only and never round-trips through the form — the service treats the persisted role as authoritative.

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

These tests prove the routes, Admin-only authorization, CSRF handling, form rendering, redirect targets, field-specific duplicate errors, and the optimistic-lock conflict re-render through a real servlet stack and PostgreSQL. They do not prove the exact audit-row contents and values for every field, the stale-version no-side-effects property, or the database-level uniqueness rollback, which are proven at the integration layer (`AccountAdminEditIntegrationTest`).