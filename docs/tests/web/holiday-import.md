# Test Evidence: Admin holiday preview/import page and role enforcement

- **Test type:** Web
- **Requirement IDs:** `CAL-001`, `CAL-002`, `CAL-003`, `CAL-004`, `CAL-005`, `CAL-006`
- **Scenario IDs:** `AC-ATT-001`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.controller.HolidayImportWebIntegrationTest`, `com.lab.labtimesheet.feature.attendance.controller.HolidayFallbackWebIntegrationTest`
- **Implementation commit:** `9be57ef`

## Protected behavior

The Admin preview page renders the HolidayAPI candidates with public rows
preselected and previously imported rows disclosed; submitting the selection
imports the chosen rows through the form and persists them. Without a configured
client the page renders an actionable "not configured" message while the manual
custom-event form still creates a day off. Non-Admin roles receive 403 on both the
preview GET and the import POST.

## Test method

`@SpringBootTest` + `@AutoConfigureMockMvc` + `TestcontainersConfiguration`
(fixed clock `2026-08-14T00:00:00Z`) + form-login via
`SecurityMockMvcRequestPostProcessors`. `HolidayImportWebIntegrationTest` supplies
a `@TestConfiguration @Primary HolidayApiClient` (3 candidates) and asserts the
rendered page names the candidates, that exactly the two public rows render
`checked="checked"`, that POSTing the National Day selection redirects to
`/attendance/calendar/holidays?year=2026`, and that the re-rendered preview shows
"already imported"; it also asserts 403 for an intern on GET and POST.
`HolidayFallbackWebIntegrationTest` uses the default context, asserting the page
shows "not configured" and that creating "Lab closure" through the calendar form
still redirects and renders.

## Hand-derived expected result

National Day and Christmas Day (public) are preselected; Lunar New Year Eve
(observed 2026-02-17) is not. The import POST binds `selections[i].uuid`,
`.selected`, `.dayOff`; a single selected row imports one event and re-preview
discloses it. An intern session is denied with 403. The fallback page contains the
"not configured" guidance and manual events remain usable.

## RED

**Command**

```text
cmd /c "mvnw.cmd -Dtest=HolidayImportWebIntegrationTest,HolidayFallbackWebIntegrationTest test"
```

**Observed result**

```text
Tests run: 2, Failures: 1, Errors: 0, Skipped: 0 (HolidayImportWebIntegrationTest)
com...adminPreviewPreselectsPublicRowsAndImportedSelectionPersists
expected: 2
 but was: 4   (StringUtils.countOccurrencesOf(previewHtml, "checked"))
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 (HolidayFallbackWebIntegrationTest)
BUILD FAILURE
```

The web test caught that the layout/fragment contains the substring "checked"
outside the two preselected checkboxes; the assertion was tightened to count the
exact rendered attribute `checked="checked"`, which appears once per preselected
row.

## GREEN

**Command**

```text
cmd /c "mvnw.cmd -Dtest=HolidayImportWebIntegrationTest,HolidayFallbackWebIntegrationTest test"
```

**Observed result**

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 (HolidayImportWebIntegrationTest)
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 (HolidayFallbackWebIntegrationTest)
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
cmd /c "mvnw.cmd test"
Tests run: 243, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The full suite additionally required `AttendanceControllerTest` (a `@WebMvcTest`
slice) to supply a `@MockitoBean HolidayImportService`, because `CalendarController`
gained the new dependency.

## External-test boundaries

MockMvc exercises the servlet + form binding + CSRF but not a real browser, a live
HTTP transport, or the platform-side credential storage. The fallback message is
verified for the unconfigured client only; the platform's real client is still
owned by `work/platform` (I2-PLAT-05).