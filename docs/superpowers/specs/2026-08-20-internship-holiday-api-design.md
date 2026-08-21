# I2-PLAT-03/04/05: Internship Lifecycle and HolidayAPI Design

**Date:** 2026-08-20  
**Status:** Approved for implementation  
**Scope:** `work/platform`

## Goal

Implement the platform-owned parts of:

- **I2-PLAT-03:** automatic/request-time internship start, completion and withdrawal, including terminal-state guards;
- **I2-PLAT-04:** encrypted SMTP/HolidayAPI configuration revision lifecycle;
- **I2-PLAT-05:** a tested Vietnam HolidayAPI client that is safe when the provider is unavailable.

The implementation must preserve the existing package-by-feature structure, follow the platform ownership rules, and keep the current calm ledger-style administration UI.

## Constraints and existing evidence

The project requirements define the internship state graph as `NOT_STARTED -> ACTIVE -> COMPLETED`, with withdrawal allowed from `NOT_STARTED` or `ACTIVE`. Completed accounts remain authenticatable but become read-only for internship-gated mutations. Withdrawn accounts lose normal authentication while their history remains reportable.

The integration requirements define AES-256-GCM with a fresh 96-bit nonce per encryption, key-version metadata, no secret redisplay, and `DRAFT -> ACTIVE -> RETIRED` revisions with at most one draft and one active revision. The HolidayAPI country is fixed to `VN`; local calendar reads must not make live provider calls.

The repository already contains:

- the `InternProfile` entity and existing administrator activation flow;
- SMTP revision storage, encryption, probing, activation, and administration;
- a baseline `holiday_api_configurations` table and its one-draft/one-active indexes;
- local attendance calendar reads;
- shared shell styles and account administration templates.

No new migration is expected for the HolidayAPI table unless implementation verification proves that the checked-in schema is insufficient.

## Approved architecture

### Internship lifecycle

Introduce a focused lifecycle service that owns profile transitions and start activation. It will:

1. activate due profiles when the Vietnam-local current date reaches the configured start date;
2. expose a request-time due-start guard so delayed schedulers cannot leave a user incorrectly blocked;
3. complete or withdraw profiles only after checking the current account/profile state and terminal-action guards;
4. re-check those guards inside the transition transaction;
5. expire an exiting Intern's registered sessions when withdrawal succeeds.

The existing administrator activation operation remains available and delegates to the same transition rules. Scheduler timing is an availability optimization; correctness comes from the request-time guard.

### Cross-feature terminal guards

Account management must not import Project or Task repositories/entities. The boundary is DTO/service-only:

- the Project feature owns a small read-only guard service backed by Project persistence, with no dependency on `AccountService`;
- the Task feature exposes a query method that counts unfinished, non-deleted tasks assigned to a set of Intern membership IDs;
- the account/lifecycle feature consumes only the guard result and the Task query contract.

The guard considers an Intern blocked when:

- they are the current leader of a `PLANNED` or `ACTIVE` Project; or
- any non-deleted task assigned through one of their memberships is not `DONE`.

Membership IDs include historical memberships so a transfer cannot make unfinished work disappear from the terminal-action check. The check is read-only and is repeated in the same transition transaction. Stronger locking across future Project/Task transfer workflows is outside these I2 platform changes and remains a later hardening concern.

### Authentication and read-only behavior

`DatabaseUserDetailsService` will consult the Intern profile status in addition to account status. `WITHDRAWN` profiles are disabled; `COMPLETED` profiles remain enabled. Existing mutation paths continue to use the active-intern eligibility service, so a completed Intern can authenticate and read history but cannot create or mutate internship-owned work.

### Integration revisions and encryption

HolidayAPI configuration mirrors the established SMTP lifecycle:

- save a draft using an encrypted API key;
- test only the draft with the current active administrator;
- activate only after a successful test;
- retire the previous active revision atomically during activation;
- retain the active revision while a replacement draft is being prepared.

`SecretCipher` remains the encryption boundary. It uses AES-256-GCM, a fresh 12-byte nonce, and the configured master-key version. API keys and SMTP passwords are never returned in DTOs, rendered in HTML, included in error messages, or logged. Replacing a secret requires submitting a new value; a blank value does not redisplay or recover the old one.

### HolidayAPI client

Platform provides a DTO-only client contract for attendance. Its default HTTP adapter calls HolidayAPI's official `/v1/holidays` endpoint with fixed `country=VN`, a requested year, and the active key. The adapter maps provider states to safe typed results:

- valid response;
- invalid key (`401`/`403` or provider invalid-key status);
- rate limited (`429`);
- unavailable (network, timeout, `5xx`, malformed or otherwise unusable provider response);
- not configured (no active revision).

Only an explicit administrator preview/test operation may call the provider. The existing local calendar read path remains local and must not invoke the client. Preview DTOs may contain provider holiday data (`uuid`, name, actual date, observed date, public flag), but never the API key or raw provider diagnostics.

## User-facing design

### Account details

The existing administrator account-detail page gains completion and withdrawal actions for eligible Intern profiles. Each action displays the consequence explicitly and requires confirmation. A blocked action returns a safe explanation (current Project leadership or unfinished Tasks) without exposing persistence details.

### HolidayAPI administration

Add a platform administration page using the same layout, panels, badges, alerts, buttons, and spacing as the SMTP setup page. It shows safe setup status, draft/test/activation state, and provider result messages. The API-key field is write-only and blank on every response, including validation and failure responses.

Shared navigation and shell ownership remain unchanged unless a minimal link is required; module templates consume the existing layout instead of introducing a second visual system.

## Verification strategy

Use RED-first tests, then the smallest implementation that makes each group GREEN:

1. profile transition rules and scheduler/request-time activation;
2. terminal guards, completed read-only behavior, withdrawal authentication/session invalidation;
3. AES round-trip, wrong-key failure, nonce/key-version behavior, and secret non-disclosure;
4. HolidayAPI revision lifecycle and SMTP regression coverage;
5. HolidayAPI HTTP/result mapping for success, invalid key, rate limit, unavailable, and not-configured states;
6. administrator authorization, CSRF, safe messages, and HolidayAPI secret non-disclosure;
7. focused module tests followed by the relevant existing regression suite.

Evidence belongs under `docs/tests/` following the repository's existing test-evidence convention. The external project plan at `C:\Users\dookubt\Desktop\comaysech\labtimesheet\.agents\PROJECT_PLAN.md` will be updated with implementation evidence and final test results.

## Non-goals

- No generic integration lifecycle framework.
- No direct cross-feature repository/entity access from Account.
- No HolidayAPI import, attendance day-off mutation, or preview interpretation owned by this platform task; those remain attendance responsibilities.
- No live HolidayAPI call during local calendar reads.
- No redesign of the shared reports UI shell.
- No unrelated cleanup or migration rewrite.

## Open hardening note

The terminal guard is re-evaluated immediately before the profile transition. Cross-transaction coordination with future membership-transfer/task-reassignment workflows is intentionally not expanded here; those workflows must consume the same service contracts when implemented.
