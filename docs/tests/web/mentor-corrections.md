# Test Evidence: Mentor correction decision, revert, and lock feedback through the web forms

- **Test type:** Web
- **Requirement IDs:** `COR-004`, `COR-005`, `COR-007`, `COR-009`
- **Scenario IDs:** `AC-COR-003`, `AC-COR-005`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.controller.MentorCorrectionWebIntegrationTest`
- **Implementation commit:** `2ace890`

## Protected behavior

A Mentor opens `/mentor/corrections` and sees every correction newest-first with
the Intern display name, policy-local proposed checkout, status, and decision
forms. Approve and reject POST routes decide a pending correction before its
deadline and redirect with stable feedback; revert POST reopens a decided
correction inside its window. Only an active Mentor may open the page or submit
any decision route; Admin and Intern requests are forbidden.

## Test method

`@SpringBootTest` + `@AutoConfigureMockMvc` + a real PostgreSQL 18.4
Testcontainer, a mutable clock, and a recording SMTP probe. An Admin bootstraps
SMTP, an Intern is created and activated, the Intern checks in and submits a
correction through the public web routes, and the Mentor then drives the
approve/reject/revert forms over MockMvc with CSRF, asserting redirects, flash
messages, rendered state badges, and persisted entity fields.

## Hand-derived expected result

For a correction submitted at `2026-08-14T09:00:01Z` (decision deadline
`2026-08-15T09:00:01Z`) with the clock inside the window:

- `GET /mentor/corrections` renders "Correction decisions", the Intern display
  name, proposed checkout `15:45`, and a `PENDING` badge.
- `POST /mentor/corrections/{id}/approve` with note `"Verified on camera"`
  redirects to `/mentor/corrections`; the next GET shows
  `"Correction {id} approved"` and an `APPROVED` badge; the persisted row carries
  the note and deciding Mentor id.
- `POST /mentor/corrections/{id}/revert` redirects and the next GET shows
  `"Correction {id} reverted to pending"` and a `PENDING` badge; the persisted
  status returns to `PENDING`.
- `POST /mentor/corrections/{id}/reject` redirects and the next GET shows
  `"Correction {id} rejected"` and a `REJECTED` badge with the note persisted.
- `GET /mentor/corrections` by Admin and Intern returns HTTP 403.

## RED

**Command**

```text
cmd /c "mvnw.cmd -Dtest=MentorCorrectionWebIntegrationTest test"
```

**Observed result**

```text
[ERROR] COMPILATION ERROR :
[ERROR] ... cannot find symbol ... class MentorCorrectionController ...
BUILD FAILURE
```

The web test could not compile because `MentorCorrectionController` and the
`attendance/mentor-corrections` template did not exist yet.

## GREEN

**Command**

```text
cmd /c "mvnw.cmd -Dtest=MentorCorrectionWebIntegrationTest test"
```

**Observed result**

```text
Running com.lab.labtimesheet.feature.attendance.controller.MentorCorrectionWebIntegrationTest
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
cmd /c "mvnw.cmd test"
Tests run: 334, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

This slice does not prove committed-row expiry locking, PostgreSQL event
constraints, the scheduled worker (I2-ATT-07), or live-browser accessibility.
The navigational entry is covered by the shared shell contract
(`docs/tests/web/attendance-shell-integration.md`), and the decision semantics
by `docs/tests/unit/correction-decision.md` and
`docs/tests/integration/correction-expiry.md`.