# Test Evidence: form-authenticated global calendar access

- **Test type:** Web
- **Requirement IDs:** `AUTH-002`, `CAL-001`, `SEC-001`, `SEC-013`
- **Scenario IDs:** `AC-SEC-005`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.controller.CalendarAuthorizationWebIntegrationTest#formAuthenticatedAdminCanOpenCalendarWhileMentorAndInternAreDenied`
- **Implementation commit:** `c8d4e9eecc59c78941769487af30953fb31a83c5`

## Incident scope

This record covers only the reported HTTP 403 for a fresh Admin session on
`GET /attendance/calendar`. The separately supplied 500 about policy
materialization is not a calendar-session or identity-mapping claim. It is
cross-referenced to
`.superpowers/sdd/access-navigation-icon-intern-picker/task-4-intern-dashboard-report.md`,
which independently records valid current PostgreSQL policy/constraint state
and no reproduction of that 500.

## Protected behavior

The persisted first Admin can open global calendar management after a real CSRF-protected form login. Persisted Mentor and Intern accounts, each authenticated by the same form-login path, receive HTTP 403 for that route.

## Test method

The test posts the actual bootstrap form, logs in through Spring Security, and follows the resulting session to `/attendance/calendar`. It configures a test-only SMTP probe solely to activate Mentor and Intern accounts through the public AccountService, then logs in those accounts before asserting denial. Spring Boot applies Flyway to PostgreSQL 18.4 through the shared Testcontainers configuration.

## Hand-derived expected result

The bootstrap entity always has immutable `ADMIN` role, so its fresh authenticated session must receive HTTP 200 from the Admin-only calendar route. Immutable `MENTOR` and `INTERN` roles are not permitted by `CAL-001`, so their matching fresh authenticated sessions must receive HTTP 403. No calendar mutation is attempted.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw '-Dtest=CalendarAuthorizationWebIntegrationTest#formAuthenticatedAdminCanOpenCalendarWhileMentorAndInternAreDenied' test
```

**Observed result**

```text
No valid RED occurred. On exact base 8be1b754e188367b260981718a5d33fc2d4d8a3b,
the new incident reproducer passed immediately: Tests run: 1, Failures: 0,
Errors: 0, Skipped: 0; BUILD SUCCESS. The production authorization guard was
not temporarily weakened merely to manufacture a failing result.
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw '-Dtest=CalendarAuthorizationWebIntegrationTest#formAuthenticatedAdminCanOpenCalendarWhileMentorAndInternAreDenied' test
```

**Observed result**

```text
No production correction was warranted. The strengthened regression, including
form-login authority assertions, passed: Tests run: 1, Failures: 0, Errors: 0,
Skipped: 0; BUILD SUCCESS. It observed Admin HTTP 200 and Mentor/Intern HTTP
403 after distinct persisted-account logins.
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=CalendarAuthorizationWebIntegrationTest,AttendanceControllerTest,AttendanceTemplateIntegrationTest,AuthenticationWebIntegrationTest,SecurityResponseIntegrationTest,RoleDashboardWebIntegrationTest test

Tests run: 16, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.

Full backend suite:
./mvnw -q test

Result: exit code 0 with Java 25.0.4 and PostgreSQL 18.4 Testcontainers.
```

## External-test boundaries

This web test uses real Spring MVC, form authentication, account identity mapping, Flyway, and PostgreSQL 18.4. It substitutes only SMTP transport with an in-memory probe, does not exercise calendar mutations or a real browser, and does not establish production deployment configuration.
