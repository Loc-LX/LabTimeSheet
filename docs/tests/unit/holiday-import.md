# Test Evidence: HolidayAPI preview/selection rules for Admin import

- **Test type:** Unit
- **Requirement IDs:** `CAL-001`, `CAL-002`, `CAL-003`, `CAL-004`, `CAL-005`, `CAL-006`
- **Scenario IDs:** `AC-ATT-001`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.HolidayImportServiceTest`
- **Implementation commit:** `9be57ef`

## Protected behavior

`HolidayImportService.preview` interprets fetched Vietnamese holiday candidates
without invoking a live HTTP transport: it flags already-imported source UUIDs,
preselects only `publicHoliday` candidates, and builds an `HolidayPreviewRow`
per candidate. `importSelections` persists only the Admin's explicitly selected
rows with full provenance (`source = HOLIDAY_API`, `source_uuid`, actual and
observed dates, `public_holiday`, day-off decision, `imported_at`), skipping rows
whose `(source, source_uuid)` already exists, and rejects non-Admin callers.

## Test method

`HolidayImportService` is driven through a mocked `HolidayApiClient`, mocked
`GlobalCalendarEventRepository`, and the MutableClock used across the attendance
suite. Each test exercises one externally observable rule: full provenance of an
imported row, idempotent re-import of the same source UUID, `public` preselect
without forcing `is_day_off`, `alreadyImported` disclosure in preview,
non-Admin `AccessDeniedException`, unknown-UUID rejection, and imported
provenance rounded from the clock instant.

## Hand-derived expected result

With the fixed test clock at `2026-08-14T02:00:00Z`, importing a
`publicHoliday=true` National Day candidate stores `calendar_date =
observed_date`, `source = "HOLIDAY_API"`, `source_uuid = candidate uuid`,
`actual_date`/`observed_date`/`public_holiday` copied verbatim, `day_off` equal
to the submitted decision, and `imported_at = 2026-08-14T02:00:00Z`. A second
import of the same UUID reports `imported = 0`, `skipped = 1`. Preview marks
`publicHoliday=true` rows `preselected = true` but still lets the Admin store
`day_off = false`.

## RED

**Command**

```text
cmd /c "mvnw.cmd -Dtest=HolidayImportServiceTest test"
```

**Observed result**

```text
[ERROR] COMPILATION ERROR :
[ERROR] ... cannot find symbol ... class HolidayImportService ...
[ERROR] ... cannot find symbol ... class HolidayCandidate ...
[ERROR] ... cannot find symbol ... class HolidayPreviewRow ...
[ERROR] ... cannot find symbol ... class HolidayImportSummary ...
[ERROR] ... cannot find symbol ... class HolidaySelection ...
[ERROR] ... cannot find symbol ... class HolidayCalendarException ...
[ERROR] ... cannot find symbol ... constructor GlobalCalendarEvent ...
...
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
BUILD FAILURE
```

The new `HolidayImportServiceTest` could not compile because the import boundary,
its DTOs, the rejected-type `HolidayCalendarException`, and the provenance-aware
`GlobalCalendarEvent` constructor did not exist yet.

## GREEN

**Command**

```text
cmd /c "mvnw.cmd -Dtest=HolidayImportServiceTest test"
```

**Observed result**

```text
Running com.lab.labtimesheet.feature.attendance.service.HolidayImportServiceTest
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.731 s
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
cmd /c "mvnw.cmd test"
Tests run: 243, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

The unit tests mock the client and repository; they do not prove the PostgreSQL
unique index on `(source, source_uuid)`, real persistence, the
`@ConditionalOnMissingBean` wiring, or the rendered web page. Those are covered
by `docs/tests/integration/holiday-import.md` and
`docs/tests/web/holiday-import.md`.