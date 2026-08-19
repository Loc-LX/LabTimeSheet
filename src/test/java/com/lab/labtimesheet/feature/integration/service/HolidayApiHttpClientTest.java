package com.lab.labtimesheet.feature.integration.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.labtimesheet.feature.integration.model.HolidayApiFailureKind;
import java.util.concurrent.atomic.AtomicReference;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class HolidayApiHttpClientTest {

    @Test
    void validResponseMapsVietnamHolidayFieldsAndUsesOnlyTheRequestedYear() {
        var requestedKey = new AtomicReference<String>();
        var requestedYear = new AtomicReference<Integer>();
        HolidayApiTransport transport = (apiKey, year) -> {
            requestedKey.set(apiKey);
            requestedYear.set(year);
            return new HolidayApiTransportResponse(200, """
                    {"status":200,"holidays":[
                      {"uuid":"uuid-1","name":"National Day","date":"2026-09-02",
                       "observed":null,"public":true}
                    ]}
                    """);
        };
        var client = new HolidayApiHttpClient(transport, new ObjectMapper());

        var result = client.preview("secret-api-key", 2026);

        assertThat(result.successful()).isTrue();
        assertThat(result.preview().countryCode()).isEqualTo("VN");
        assertThat(result.preview().holidays()).singleElement().satisfies(holiday -> {
            assertThat(holiday.uuid()).isEqualTo("uuid-1");
            assertThat(holiday.actualDate()).hasToString("2026-09-02");
            assertThat(holiday.publicHoliday()).isTrue();
        });
        assertThat(requestedKey).hasValue("secret-api-key");
        assertThat(requestedYear).hasValue(2026);
    }

    @Test
    void invalidKeyRateLimitAndProviderOutageMapToSafeTypedResults() {
        assertThat(clientWith(401, "{}").preview("secret-api-key", 2026).failure())
                .isEqualTo(HolidayApiFailureKind.INVALID_KEY);
        assertThat(clientWith(429, "{}").preview("secret-api-key", 2026).failure())
                .isEqualTo(HolidayApiFailureKind.RATE_LIMITED);
        assertThat(clientWith(503, "{}").preview("secret-api-key", 2026).failure())
                .isEqualTo(HolidayApiFailureKind.UNAVAILABLE);
        assertThat(clientWith(200, "not-json").preview("secret-api-key", 2026).failure())
                .isEqualTo(HolidayApiFailureKind.UNAVAILABLE);
    }

    private static HolidayApiHttpClient clientWith(int status, String body) {
        return new HolidayApiHttpClient((apiKey, year) -> new HolidayApiTransportResponse(status, body),
                new ObjectMapper());
    }
}
