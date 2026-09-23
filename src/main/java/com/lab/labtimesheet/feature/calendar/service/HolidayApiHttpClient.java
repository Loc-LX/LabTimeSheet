package com.lab.labtimesheet.feature.calendar.service;

import java.time.LocalDate;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;
import com.lab.labtimesheet.feature.calendar.model.dto.HolidayApiCandidate;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Concrete HolidayAPI v1 HTTP adapter. It sends the fixed VN country and maps provider failures to safe categories;
 * provider bodies, keys, and transport diagnostics never cross this boundary.
 */
@Component
public class HolidayApiHttpClient {
    static final String PRODUCTION_BASE_URL = "https://holidayapi.com";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(2);

    private final RestClient client;

    /**
     * Creates the production adapter against the official HTTPS HolidayAPI origin.
     */
    public HolidayApiHttpClient() {
        this(RestClient.builder(), PRODUCTION_BASE_URL);
    }

    /**
     * Creates a deterministic adapter seam for package-local transport tests.
     *
     * @param builder Spring HTTP client builder
     * @param baseUrl loopback/test endpoint; production construction uses the fixed official origin
     */
    HolidayApiHttpClient(RestClient.Builder builder, String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("HolidayAPI base URL must not be blank");
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        client = builder.requestFactory(requestFactory).baseUrl(baseUrl).build();
    }

    /**
     * Fetches the official HolidayAPI v1 JSON shape for one VN year.
     *
     * @param apiKey request-local decrypted key
     * @param countryCode required fixed country, which must be VN
     * @param year four-digit calendar year
     * @return parsed immutable candidates
     */
    public List<HolidayApiCandidate> fetch(String apiKey, String countryCode, int year) {
        if (apiKey == null || apiKey.isBlank() || !"VN".equals(countryCode)
                || year < 1 || year > 9999) {
            throw HolidayApiClientException.unavailable();
        }
        try {
            JsonNode response = client.get()
                    .uri(uriBuilder -> uriBuilder.path("/v1/holidays")
                            .queryParam("country", countryCode)
                            .queryParam("year", year)
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            return parse(response);
        } catch (RestClientResponseException failure) {
            throw classify(failure.getStatusCode());
        } catch (RestClientException | DateTimeParseException | IllegalArgumentException failure) {
            throw HolidayApiClientException.unavailable();
        }
    }

    private List<HolidayApiCandidate> parse(JsonNode response) {
        if (response == null || !response.isObject()) {
            throw HolidayApiClientException.unavailable();
        }
        int providerStatus = response.path("status").asInt(200);
        if (providerStatus != 200) {
            throw classify(HttpStatusCode.valueOf(providerStatus));
        }
        JsonNode holidays = response.get("holidays");
        if (holidays == null || !holidays.isArray()) {
            throw HolidayApiClientException.unavailable();
        }
        List<HolidayApiCandidate> candidates = new ArrayList<>();
        for (JsonNode holiday : holidays) {
            if (holiday == null || !holiday.isObject()
                    || !holiday.path("country").asText().equals("VN")
                    || holiday.path("uuid").asText().isBlank()
                    || holiday.path("name").asText().isBlank()
                    || holiday.path("date").asText().isBlank()
                    || holiday.path("observed").asText().isBlank()
                    || !holiday.path("public").isBoolean()) {
                throw HolidayApiClientException.unavailable();
            }
            candidates.add(new HolidayApiCandidate(
                    holiday.path("uuid").asText(),
                    holiday.path("name").asText(),
                    LocalDate.parse(holiday.path("date").asText()),
                    LocalDate.parse(holiday.path("observed").asText()),
                    holiday.path("public").asBoolean()));
        }
        return List.copyOf(candidates);
    }

    private HolidayApiClientException classify(HttpStatusCode status) {
        if (status.value() == 401) {
            return HolidayApiClientException.invalidKey();
        }
        if (status.value() == 429) {
            return HolidayApiClientException.rateLimited();
        }
        return HolidayApiClientException.unavailable();
    }
}
