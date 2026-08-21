# Test Evidence: Account lifecycle state graph, authentication denial, retained attribution, and session invalidation

- **Test type:** Integration
- **Requirement IDs:** `ACC-014`, `ACC-015`, `ACC-016`, `ACC-017`, `ACC-018`
- **Scenario IDs:** `AC-ACC-009`
- **Test class/method:** `com.lab.labtimesheet.feature.account.service.AccountLifecycleIntegrationTest`
- **Implementation commit:** `476b6d7` (GREEN); RED `6c1491c`; LayerStructureTest Windows fix `0c52691`

## Protected behavior

`AccountService.lockAccount`, `unlockAccount`, and `deactivateAccount` implement the ACC-014 state graph against PostgreSQL: lock moves `ACTIVE → LOCKED` and writes `locked_at`; unlock moves `LOCKED → ACTIVE` without changing role or recreating credentials; deactivate moves `ACTIVE → DEACTIVATED` and writes `deactivated_at`. Locked and deactivated accounts cannot authenticate (`DatabaseUserDetailsService` disables them) yet their attribution stays resolvable through `requireIdentityById`. Lock and deactivation also expire the affected user's existing authenticated sessions per ACC-018.

## Test method

A real PostgreSQL Testcontainer hosts the JPA entities and Flyway schema. The setup bootstraps an Admin, activates SMTP, creates a Mentor and an Intern, and activates both through real single-use tokens. Each test drives the service methods and reads the entity rows and the Spring Security `SessionRegistry`.

## Hand-derived expected result

- After lock: status `LOCKED`, `lockedAt` non-null, `deactivatedAt` null, `UserDetailsService.loadUserByUsername("mentor@example.com").isEnabled()` is `false`.
- After unlock: status `ACTIVE`, `lockedAt` null, role still `MENTOR`, password hash byte-identical to the pre-lock value, authentication enabled again.
- After deactivate: status `DEACTIVATED`, `deactivatedAt` non-null, role `MENTOR`, password hash present, authentication disabled, and `requireIdentityById` still returns the Mentor identity with `DEACTIVATED` status.
- Invalid transitions throw `IllegalArgumentException`: lock while already `LOCKED`, unlock a non-`LOCKED` account, deactivate a `LOCKED` account, and any operation on a missing account id.
- Locking Mentor expires `mentor-session-1` and `mentor-session-2` while leaving `intern-session-1` live; deactivating the Intern then expires `intern-session-1`.

## RED

**Command**

```text
env JAVA_HOME=C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11 PATH=<JDK bin>;... .\mvnw.cmd -Dtest=AccountLifecycleIntegrationTest "-DargLine=-Duser.timezone=Asia/Ho_Chi_Minh" test
```

**Observed result**

```text
[ERROR] COMPILATION ERROR :
.../AccountLifecycleIntegrationTest.java:[87,17] cannot find symbol
  symbol:   method lockAccount(long,long)
  location: variable accounts of type com.lab.labtimesheet.feature.account.service.AccountService
.../AccountLifecycleIntegrationTest.java:[90,26] cannot find symbol
  symbol:   method getLockedAt()
  location: variable locked of type com.lab.labtimesheet.feature.account.model.entity.AppUser
.../AccountLifecycleIntegrationTest.java:[94,17] cannot find symbol
  symbol:   method unlockAccount(long,long)
.../AccountLifecycleIntegrationTest.java:[105,17] cannot find symbol
  symbol:   method deactivateAccount(long,long)
.../AccountLifecycleIntegrationTest.java:[108,31] cannot find symbol
  symbol:   method getDeactivatedAt()
```

The test could not compile because `AccountService` exposes no `lockAccount`/`unlockAccount`/`deactivateAccount` and `AppUser` exposes no `getLockedAt`/`getDeactivatedAt` — exactly the missing Iteration 2 API.

## GREEN

**Command**

```text
env JAVA_HOME=C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11 PATH=<JDK bin>;... .\mvnw.cmd -Dtest=AccountLifecycleIntegrationTest "-DargLine=-Duser.timezone=Asia/Ho_Chi_Minh" test
```

**Observed result**

```text
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 23.24 s -- in com.lab.labtimesheet.feature.account.service.AccountLifecycleIntegrationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
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

These tests prove the JPA state graph, the persisted timestamps, authentication denial via `UserDetailsService`, retained attribution, and session expiry through the Spring Security `SessionRegistry`. They do not prove the browser flows, the rendered Admin pages, or that a real servlet session is torn down (the registry `expireNow` is the ACC-018 contract exercised here).