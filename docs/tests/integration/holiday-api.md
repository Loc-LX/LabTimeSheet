# Test Evidence: encrypted HolidayAPI revision lifecycle and safe client mapping

- **Test type:** Integration and unit
- **Requirement IDs:** `I2-PLAT-04`, `I2-PLAT-05`
- **Test classes:** `SecretCipherTest`, `HolidayApiConfigurationTest`, `HolidayApiConfigurationServiceTest`, `HolidayApiHttpClientTest`, `HolidayApiIntegrationTest`
- **Implementation commit:** pending

## Protected behavior

HolidayAPI credentials are stored as AES-256-GCM ciphertext with a fresh 12-byte nonce and key-version metadata.
Draft/test/active/retired revisions are explicit, only a successfully tested draft can activate, and replacing an
active revision retains the old active configuration until the replacement is promoted. Provider failures cross the
integration boundary only as typed safe outcomes.

## RED

The initial HolidayAPI RED run failed during test compilation with 15 errors for the missing HolidayAPI DTOs,
configuration types, service boundary, and Jackson 3 imports. This was expected before the implementation slice.

## GREEN

```text
.\mvnw.cmd '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=SecretCipherTest,HolidayApiConfigurationTest,HolidayApiConfigurationServiceTest,HolidayApiHttpClientTest' test
.\mvnw.cmd '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=HolidayApiIntegrationTest' test
```

```text
Focused encryption/configuration/client tests: 8 passed.
HolidayApiIntegrationTest: 1 passed.
BUILD SUCCESS
PostgreSQL: 18.4 Testcontainer
```

## Covered scenarios

- AES-GCM round trip, fresh nonce, version metadata, and wrong-key/authentication failure.
- Invalid-key, rate-limit, unavailable, malformed-response, and unusable-provider-status mapping.
- Failed tests leave a draft untested; successful tests permit activation.
- A replacement draft clears prior test state while the old active revision continues serving explicit previews.
- No-active revision returns `NOT_CONFIGURED` without invoking the provider.
- The existing `holiday_api_configurations` baseline table is mapped without a new migration.
- Ciphertext/nonce/key values and raw provider diagnostics are not returned to DTOs, templates, or error messages.

## Feature boundary

Attendance local calendar reads remain backed by the existing stored calendar service. HolidayAPI calls are explicit
Admin test/preview operations through a DTO-only integration contract; this slice does not import holidays or alter
attendance day-off totals.
