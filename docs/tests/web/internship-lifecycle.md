# Test Evidence: Admin internship completion and withdrawal actions

- **Test type:** Web
- **Requirement IDs:** `I2-PLAT-03`
- **Test class:** `com.lab.labtimesheet.feature.account.controller.InternshipLifecycleWebIntegrationTest`
- **Implementation commit:** pending

## Protected behavior

Only an authenticated Admin can complete or withdraw an internship. Both state-changing forms require CSRF, use the
existing account-detail UI shell, and show explicit confirmation text describing the history/authentication effects.

## RED

The web tests initially failed with the lifecycle action API and controller/template behavior missing. The failing
compile/route assertions established the required routes before implementation.

## GREEN

```text
.\mvnw.cmd '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=InternshipLifecycleWebIntegrationTest' test
```

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
PostgreSQL: 18.4 Testcontainer
```

The test verifies CSRF denial, Admin completion redirect/state change, non-Admin withdrawal denial, and the rendered
confirmation consequences. Existing account-management web coverage remains green in the full suite.

## UI boundary

The account detail page reuses the established panel, badge, alert, form-action, and button classes. No shared reports
shell redesign or unrelated navigation change was introduced.
