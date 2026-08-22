# Test Evidence: Admin account directory and identity correction

- **Test type:** Integration
- **Requirement IDs:** `ACC-009`, `ACC-017`–`ACC-019`, `ACC-018`
- **Scenario IDs:** `AC-ACC-012`, `AC-AUTH-001`
- **Test class/method:** `com.lab.labtimesheet.feature.account.service.AccountIdentityCorrectionIntegrationTest`
- **Implementation commit:** `pending`

## Protected behavior

The Account-owned Admin directory searches normalized display name, email, and Student Code and filters only on the
immutable global role. The correction boundary permits only email, Student Code, and lifecycle-allowed Intern dates;
email changes require tested SMTP and commit atomically with the required notice or replacement activation. Pending
activation tokens are replaced, active/locked sessions are expired, and deactivated/terminal profile boundaries remain
read-only without inventing session history.

## Test method

The PostgreSQL 18.4 Testcontainers test bootstraps an Admin, configures a recording tested SMTP adapter, creates
pending/active/locked accounts, and exercises the concrete AccountService boundary. Assertions inspect only
Account-owned projections, account/profile state, token terminal state, and SessionRegistry expiration. The delivery
failure case proves profile and email changes roll back together.

## Hand-derived expected result

Directory text is trimmed and case-folded before matching; role filtering returns only the selected immutable role.
The old pending activation token is unusable after a successful email correction and the newly delivered token activates
the corrected account. Active/locked corrections notify the new address and expire sessions registered under the old
identity. SMTP failure leaves all persisted identity/profile state unchanged. Student Code is editable only before
terminal internship state, dates only while `NOT_STARTED`, and a deactivated account is read-only.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=AccountIdentityCorrectionIntegrationTest test
```

**Observed result**

```text
2026-08-22T13:20:48+07:00 — BUILD FAILURE during testCompile: the production-shaped test could not find
AccountDirectoryFilter or AccountIdentityCorrection. No container or behavioral execution occurred; this was the
expected missing Account directory/correction boundary RED.
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=AccountIdentityCorrectionIntegrationTest test
```

**Observed result**

```text
2026-08-22T13:24:25+07:00 — PostgreSQL 18.4 Testcontainers; Tests run: 5, Failures: 0, Errors: 0, Skipped: 0;
BUILD SUCCESS.
```

## Affected suite

**Command and result**

```text
Pending for the branch-wide platform gate. The focused GREEN above is the producer milestone gate; the full
account/integration suite and branch-wide suite will be recorded after I3-PLAT-01 through I3-PLAT-06 land.
```

## External-test boundaries

This evidence does not prove the Account Directory Thymeleaf form or desktop accessibility; the reports-ui owner
consumes the reviewed AccountService DTO boundary for those screens. It does not expose or persist raw activation
tokens, exercise a real SMTP server, or authorize Project/Task/Attendance data.
