# Test Evidence: Admin HolidayAPI setup and preview page

- **Test type:** Web
- **Requirement IDs:** `I2-PLAT-04`, `I2-PLAT-05`
- **Test class:** `com.lab.labtimesheet.feature.integration.controller.HolidayApiWebIntegrationTest`
- **Implementation commit:** pending

## Protected behavior

The Admin-only HolidayAPI page supports draft, explicit provider test, activation, and explicit year preview actions.
The Vietnam country is fixed, the key input is write-only, and provider failures render short operator-safe messages.

## RED

The initial web slice had no HolidayAPI controller/page and therefore failed the required compile/route assertions.

## GREEN

```text
.\mvnw.cmd '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=HolidayApiWebIntegrationTest' test
```

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
PostgreSQL: 18.4 Testcontainer
```

The web test covers Admin GET access, CSRF protection, encrypted draft persistence, blank-after-response key input,
safe invalid-provider feedback, successful test/activation, explicit preview rendering, and non-Admin denial. The
full suite also passed the existing SMTP onboarding tests.

## UI boundary

The page reuses the existing SMTP-era shell, panels, badges, alerts, form grids, data-table, and button tokens. It
links to SMTP settings without changing shared layout ownership.
