package com.lab.labtimesheet.feature.integration.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.lab.labtimesheet.feature.integration.model.HolidayApiFailureKind;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiCandidate;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiPreview;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiResult;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Maps HolidayAPI HTTP/JSON responses into safe typed results. Raw provider responses and
 * diagnostics never cross this boundary.
 */
@Component
public class HolidayApiHttpClient implements HolidayApiProbe {

    private final HolidayApiTransport transport;
    private final ObjectMapper objectMapper;

    public HolidayApiHttpClient(HolidayApiTransport transport, ObjectMapper objectMapper) {
        this.transport = transport;
        this.objectMapper = objectMapper;
    }

    @Override
    public HolidayApiResult preview(String apiKey, int year) {
        if (apiKey == null || apiKey.isBlank()) {
            return HolidayApiResult.failure(HolidayApiFailureKind.INVALID_KEY);
        }
        HolidayApiTransportResponse response = transport.get(apiKey, year);
        if (response == null || response.statusCode() == 0) {
            return HolidayApiResult.failure(HolidayApiFailureKind.UNAVAILABLE);
        }
        if (response.statusCode() == 401 || response.statusCode() == 403 || response.statusCode() == 400) {
            return HolidayApiResult.failure(HolidayApiFailureKind.INVALID_KEY);
        }
        if (response.statusCode() == 429) {
            return HolidayApiResult.failure(HolidayApiFailureKind.RATE_LIMITED);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return HolidayApiResult.failure(HolidayApiFailureKind.UNAVAILABLE);
        }

        try {
            JsonNode root = objectMapper.readTree(response.body());
            int providerStatus = root.path("status").asInt(response.statusCode());
            if (providerStatus == 401 || providerStatus == 403) {
                return HolidayApiResult.failure(HolidayApiFailureKind.INVALID_KEY);
            }
            if (providerStatus == 429) {
                return HolidayApiResult.failure(HolidayApiFailureKind.RATE_LIMITED);
            }
            if (providerStatus != 200 || !root.path("holidays").isArray()) {
                return HolidayApiResult.failure(HolidayApiFailureKind.UNAVAILABLE);
            }

            List<HolidayApiCandidate> candidates = new ArrayList<>();
            for (JsonNode holiday : root.path("holidays")) {
                String uuid = requiredText(holiday, "uuid");
                String name = requiredText(holiday, "name");
                String date = requiredText(holiday, "date");
                if (uuid == null || name == null || date == null) {
                    return HolidayApiResult.failure(HolidayApiFailureKind.UNAVAILABLE);
                }
                String observed = optionalText(holiday, "observed");
                candidates.add(new HolidayApiCandidate(
                        uuid,
                        name,
                        LocalDate.parse(date),
                        observed == null ? null : LocalDate.parse(observed),
                        holiday.path("public").asBoolean(false)));
            }
            return HolidayApiResult.success(new HolidayApiPreview(year, "VN", candidates));
        } catch (RuntimeException malformed) {
            return HolidayApiResult.failure(HolidayApiFailureKind.UNAVAILABLE);
        }
    }

    private static String requiredText(JsonNode node, String field) {
        String value = optionalText(node, field);
        return value == null ? null : value;
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }
}
