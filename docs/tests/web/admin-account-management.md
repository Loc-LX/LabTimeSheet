# Test Evidence: Admin account management pages and lifecycle actions

- **Test type:** Web
- **Requirement IDs:** `ACC-008`–`ACC-025` (listing/detail carriers), `ACC-014`, `ACC-015`, `ACC-016`, `ACC-017`, `ACC-018`
- **Scenario IDs:** `AC-ACC-009`
- **Test class/method:** `com.lab.labtimesheet.feature.account.controller.AccountManagementWebIntegrationTest`
- **Implementation commit:** `pending`

## Protected behavior

An authenticated Active Admin can list every account (`GET /admin/accounts`), filter the list by immutable role, open an account detail page (`GET /admin/accounts/{id}`), and lock, unlock, or deactivate an account through POST actions. Mentors and Interns are denied every route. A guessed or missing account identifier returns 404 without disclosing why. Invalid lifecycle transitions redirect back to the detail page with an `error` flag instead of crashing.

## Test method

A real PostgreSQL Testcontainer and Spring Security filter chain host MockMvc. The setup bootstraps an Admin, activates SMTP, creates a Mentor and an Intern, and activates both accounts through real activation tokens. Each test then exercises the Admin routes as the Admin and asserts non-Admin denial with `user(...).roles(...)`. State transitions are confirmed both by the redirect target and by `AccountService.requireIdentityById`.

## Hand-derived expected result

- `GET /admin/accounts` renders the `accounts/list` view with both account names and the "Last sign-in" column; Mentor and Intern get 403.
- `GET /admin/accounts?role=INTERN` shows only Intern One.
- `GET /admin/accounts/{id}` renders the `accounts/detail` view showing "Mentor One", "MENTOR", and the "Lock account" action.
- `GET /admin/accounts/99999999` returns 404.
- `POST /admin/accounts/{id}/lock` redirects to `/admin/accounts/{id}?locked` and leaves the account `LOCKED`; `/unlock` → `?unlocked` back to `ACTIVE` with role `MENTOR`; `/deactivate` → `?deactivated` → `DEACTIVATED` with role unchanged.
- Non-Admin POST lock returns 403; locking an already-locked account redirects to `/admin/accounts/{id}?error...`.

## RED

**Command**

```text
env JAVA_HOME=C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11 PATH=<JDK bin>;... .\mvnw.cmd -Dtest=AccountManagementWebIntegrationTest "-DargLine=-Duser.timezone=Asia/Ho_Chi_Minh" test
```

**Observed result**

```text
[ERROR] Tests run: 5, Failures: 5, Errors: 0, Skipped: 0, Time elapsed: 27.34 s <<< FAILURE!
com.lab.labtimesheet.feature.account.controller.AccountManagementWebIntegrationTest.adminSeesAccountDetailAndOnlyAdminMayRequestIt -- <<< FAILURE!
java.lang.AssertionError: Status expected:<200> but was:<404>
com.lab.labtimesheet.feature.account.controller.AccountManagementWebIntegrationTest.adminLocksUnlocksAndDeactivatesAndOnlyAdminMayDoSo -- <<< FAILURE!
java.lang.AssertionError: Range for response status value 404 expected:<REDIRECTION> but was:<CLIENT_ERROR>
com.lab.labtimesheet.feature.account.controller.AccountManagementWebIntegrationTest.adminSeesAccountListWithStateColumnsAndNonAdminsAreDenied -- <<< FAILURE!
java.lang.AssertionError: Status expected:<200> but was:<405>
com.lab.labtimesheet.feature.account.controller.AccountManagementWebIntegrationTest.invalidLockTransitionRedirectsToDetailWithErrorInsteadOfCrashing -- <<< FAILURE!
java.lang.AssertionError: Range for response status value 404 expected:<REDIRECTION> but was:<CLIENT_ERROR>
com.lab.labtimesheet.feature.account.controller.AccountManagementWebIntegrationTest.adminFiltersAccountListByRoleAndGuessedIdsReturnNotFoundWithoutDisclosure -- <<< FAILURE!
java.lang.AssertionError: Status expected:<200> but was:<405>
```

The list route returned 405 (only `POST /admin/accounts` existed), the detail route returned 404 (no `GET /admin/accounts/{id}` mapping), and every lock/unlock/deactivate POST returned 404 — exactly the missing Iteration 2 routes.

## GREEN

**Command**

```text
env JAVA_HOME=C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11 PATH=<JDK bin>;... .\mvnw.cmd -Dtest=AccountManagementWebIntegrationTest "-DargLine=-Duser.timezone=Asia/Ho_Chi_Minh" test
```

**Observed result**

```text
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 28.02 s -- in com.lab.labtimesheet.feature.account.controller.AccountManagementWebIntegrationTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
env JAVA_HOME=C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11 PATH=<JDK bin>;... .\mvnw.cmd -Dtest="AccountManagementWebIntegrationTest,AccountLifecycleIntegrationTest,AccountWebIntegrationTest,AccountActivationIntegrationTest,AuthenticationWebIntegrationTest,BootstrapOnboardingWebIntegrationTest,EligibleInternOptionIntegrationTest,BootstrapIntegrationTest" "-DargLine=-Duser.timezone=Asia/Ho_Chi_Minh" test
[INFO] Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## External-test boundaries

These tests prove the HTTP routes, authorization, redirects, and rendered Thymeleaf views against a real database. They do not prove the session-invalidation side of ACC-018 (covered by `AccountLifecycleIntegrationTest`), the exact row timestamps written by the state graph, or browser behavior.