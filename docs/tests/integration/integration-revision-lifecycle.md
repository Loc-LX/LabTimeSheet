# Test Evidence: Encrypted SMTP and HolidayAPI revision lifecycles

- **Test type:** Integration
- **Requirement IDs exercised:** `INT-001`–`INT-006`, `INT-008`–`INT-010` (service, persistence, and concrete-provider-boundary slices only)
- **Scenario IDs exercised:** `AC-INT-001` and `AC-INT-002` (service/persistence slices only)
- **Test classes:** `com.lab.labtimesheet.feature.integration.service.SmtpIntegrationTest`; `com.lab.labtimesheet.feature.integration.service.HolidayApiIntegrationTest`; `com.lab.labtimesheet.feature.integration.service.HolidayApiHttpClientTest`; `com.lab.labtimesheet.feature.integration.service.IntegrationExternalTransactionIntegrationTest`; `com.lab.labtimesheet.feature.integration.service.SecretCipherTest`
- **Implementation commit:** `pending (uncommitted milestone; base aff763d94371814611a2ad26487811270cbdd060)`

## Protected behavior

Admin-managed SMTP and HolidayAPI revisions persist only encrypted AES-256-GCM envelopes with fresh 96-bit nonces and key-version metadata. Each integration permits at most one draft and one active revision; a failed test leaves a draft untested and an active revision unchanged, while a successful tested replacement retires the previous active revision atomically. Service-level History DTOs and immutable DTOs contain non-secret metadata only.

SMTP test delivery is derived from the currently authenticated Admin identity at the service boundary (`testDraft(draftId, adminId)`); callers cannot supply an arbitrary recipient. New SMTP and HolidayAPI setup, test, activation, preview, and History service methods authenticate an active Admin before revision lookup, decryption, or provider I/O.

HolidayAPI is fixed to country `VN` and is preview-only in this milestone: local calendar reads and reports do not call the provider. The public boundary returns immutable safe candidates and a server `retrievedAt` instant for successful responses, while distinguishing absent key, invalid key, rate limit, and unavailable outcomes without returning provider bodies or secrets. The concrete HTTP adapter uses only the official HTTPS origin, finite JDK connect/read timeouts, and maps only HTTP 401 to `INVALID_KEY`; HTTP 400 and 403 are safe `UNAVAILABLE` configuration outcomes.

Both SMTP and HolidayAPI external adapters run outside the Spring transaction that saves a revision. A deterministic blocking probe allows a concurrent newer draft save, then releases the late provider success; the late operation fails optimistic locking and cannot overwrite the newer encrypted secret or its untested state.

## Test method

The PostgreSQL 18.4 Testcontainers tests bootstrap a real Admin and use the real JPA entities, repositories, AES-GCM cipher, and configuration services. SMTP and HolidayAPI service tests use deterministic in-process probes. `IntegrationExternalTransactionIntegrationTest` blocks each real service path, observes `TransactionSynchronizationManager` at the adapter boundary, concurrently edits the same draft, releases the late probe, and verifies the optimistic-lock failure plus newer ciphertext and `testedAt` state. `HolidayApiHttpClientTest` uses a deterministic JDK HTTP server and the concrete `HolidayApiHttpClient` to exercise VN request construction, JSON mapping, finite stalled-server timeout, official-origin construction, and provider status classification without a real external API or credential. The assertions inspect persisted ciphertext/nonces/version, lifecycle rows, Admin authorization failures, recipient derivation, immutable preview results, and secret-free service History text.

## Hand-derived expected result

Two successfully configured revisions leave the newest revision `ACTIVE` and the predecessor `RETIRED`; the database reports exactly one active revision. A failed replacement test remains `DRAFT` and cannot displace the active revision. Ciphertext does not contain the submitted password or API key and has a 12-byte nonce and key-version `1`. A successful HolidayAPI preview uses `VN`, has non-null `retrievedAt`, and returns immutable candidates; absent, invalid, rate-limited, unavailable, 400, and 403 outcomes contain no candidates and no secret/provider diagnostic. A late blocked provider result cannot replace a newer concurrently saved draft.

## RED

**Original producer boundary RED**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest=SmtpIntegrationTest,HolidayApiIntegrationTest,HolidayApiHttpClientTest test
```

```text
2026-08-20T21:44:33+07:00 — BUILD FAILURE during testCompile; 13 errors for the expected missing producer boundary, including HolidayApiConfigurationService, HolidayApiClient, HolidayApiCandidate, HolidayApiDraft, HolidayApiPreviewStatus, HolidayApiStatus, HolidayApiRevisionHistory, HolidayApiConfigurationRepository, and SmtpRevisionHistory.
```

The failure occurred before container or behavioral execution and was the expected RED for the absent revision/history and HolidayAPI producer APIs.

**Reviewed SMTP API-shape RED**

```text
2026-08-20T22:16:57+07:00 — ./mvnw -Dtest=SmtpIntegrationTest test
```

Temporarily removing the unsupported caller-controlled SMTP recipient overload produced a genuine testCompile RED with 17 stale three-argument call-site errors. All affected call sites were then mechanically migrated to the reviewed two-argument service contract; the overload was not retained.

**Official-origin contract RED**

```text
2026-08-20T22:50:48+07:00 — JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw -Dtest=HolidayApiHttpClientTest test
```

The newly added production-origin test failed during testCompile because `HolidayApiHttpClient.PRODUCTION_BASE_URL` and the corresponding fixed production construction contract did not exist. This was a missing production boundary, not an environment/setup failure.

**External transaction-boundary RED**

```text
2026-08-20T22:54:56+07:00 — JAVA_HOME=/opt/homebrew/opt/openjdk@25 DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest=IntegrationExternalTransactionIntegrationTest test
```

With only a temporary `@Transactional` replay added to each `testDraft` method, the unchanged blocking tests failed 2/2 because both external probes observed an active Spring transaction (`expected false, was true`). The tests exercised the real service paths and were then restored to the production-shaped no-transaction boundary.

**HTTP timeout/status RED**

```text
2026-08-20T22:56:00+07:00 — JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw -Dtest=HolidayApiHttpClientTest test
```

With only the pre-fix HTTP client behavior replayed (no finite request timeout and 400/401/403 all classified as invalid-key), the unchanged tests ran 6 with 2 failures: the stalled server produced a test-side `TimeoutException` rather than the client’s safe unavailable result, and the 400 response returned the invalid-key message. The 403 path used the same pre-fix invalid-key classification; the loop stopped at the first 400 assertion.

## GREEN

**Focused command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest=SmtpIntegrationTest,HolidayApiIntegrationTest,HolidayApiHttpClientTest,SecretCipherTest,IntegrationExternalTransactionIntegrationTest test
```

```text
2026-08-20T22:57:41+07:00 — PostgreSQL 18.4 Testcontainers plus deterministic JDK HTTP server; Tests run: 12, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
```

The two transaction-boundary tests prove both adapters observe no active Spring transaction while the late optimistic-lock path preserves the newer encrypted draft/test state; the HTTP tests prove fixed official-origin construction, finite timeout, and exact 400/401/403 classification.

## Affected suite

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest='**/feature/account/**/*Test,**/feature/integration/**/*Test' test
```

```text
2026-08-20T22:58:22+07:00 — PostgreSQL 18.4 Testcontainers; Tests run: 62, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
```

**Branch-wide and structural verification**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw test
```

```text
2026-08-20T23:02:17+07:00 — PostgreSQL 18.4 Testcontainers; aggregate Surefire reports: Tests run: 255, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
```

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest=LayerStructureTest,PlatformFoundationTest,AttendanceLayerStructureTest,ReportingArchitectureTest,ProjectPersistenceStructureTest,TaskPersistenceStructureTest test
```

```text
2026-08-20T23:05:07+07:00 — PostgreSQL 18.4/Flyway replay; Tests run: 12, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
```

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH ./mvnw -DskipTests compile
```

```text
2026-08-20T23:05:15+07:00 — BUILD SUCCESS on the final current tree.
```

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH ./mvnw -DskipTests -Ddoclint=all javadoc:javadoc
```

```text
2026-08-20T23:05:26+07:00 — BUILD SUCCESS; forced-current doclint completed without errors.
```

```text
git diff --check
```

```text
2026-08-20T23:05:37+07:00 — no output; clean.
```

## External-test boundaries

No real HolidayAPI or SMTP credential, provider account, or external transport is used. The deterministic JDK HTTP server, concrete-client tests, and in-process SMTP/HolidayAPI probes model the external boundary and safe classification only; they do not prove live provider availability, quota, DNS/TLS behavior, or a real SMTP server. The exercised scope is limited to service/persistence and concrete-adapter scenarios: browser authorization/rendering of integration History pages is explicitly unproved, even though service authorization and secret-free History DTO assertions execute. The tests do not implement notification delivery (`I2-PLAT-06`), Attendance-owned calendar import/persistence, or local calendar/report browser journeys. No migration or dependency was added: the existing V1 schema and installed Spring/JDK dependencies were sufficient. The local worktree `.superpowers/` duplicate is ignored, unmodified, and excluded from this milestone.
