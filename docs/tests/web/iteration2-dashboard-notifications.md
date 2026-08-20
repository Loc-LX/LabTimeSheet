# Test Evidence: Iteration 2 dashboard notification menu

- **Test type:** Web
- **Requirement IDs:** `I2-UI-01`, `NOT-009`, `UI-001`, `UI-013`
- **Scenario IDs:** `AC-UI-001`, `AC-UI-005`
- **Test class/method:** `com.lab.labtimesheet.feature.reporting.controller.DashboardTemplateWebTest#internTemplateRendersNotificationMenuWhenDashboardSuppliesNotifications`
- **Implementation commit:** pending local commit

## Protected behavior

Role dashboards render a supplied notification list in the shared shell without fabricating notifications or adding the panel when no producer model attribute is present. The notification region has an accessible name and preserves each producer-provided action URL.

## Test method

The test-only controller renders the production Intern dashboard with one representative notification DTO-shaped record. MockMvc and Thymeleaf assertions verify the title, action URL, and accessible region name. Existing Admin, Mentor, and Intern dashboard tests continue to verify that the panel is absent when `notifications` is not supplied.

## Hand-derived expected result

The supplied item `Review pending exit` links to `/projects/7/exit`; the rendered notification region is named `Notifications`. No notification content is expected in the baseline dashboard tests without a supplied list.

## RED

**Command**

```text
./mvnw '-Dtest=DashboardTemplateWebTest#internTemplateRendersNotificationMenuWhenDashboardSuppliesNotifications' test
```

**Observed result**

```text
The focused test reached the production dashboard template but failed because the response did not contain `Review pending exit`; the controller supplied the notification item, but the baseline template had no notification consumer.
```

## GREEN

**Command**

```text
./mvnw '-Dtest=DashboardTemplateWebTest' test
```

**Observed result**

```text
`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS` on Java 25.
```

## Affected suite

**Command and result**

```text
./mvnw '-Dtest=*Reporting*Test,*Dashboard*Test,*Template*Test,*Shell*Test,*Accessibility*Test,*UiContractWebTest,*AttendanceReport*Test,*ProjectTaskReport*Test,*Iteration2WorkflowFragments*Test' test

`Tests run: 63, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS` on Java 25 with PostgreSQL 18.4 Testcontainers where required.
```

## External-test boundaries

This contract deliberately does not invent or prove a notification query, unread-state policy, authorization, delivery, or producer service API. Platform-owned notification DTO/service integration and browser keyboard/focus evidence remain external boundaries until a reviewed public contract is available.
