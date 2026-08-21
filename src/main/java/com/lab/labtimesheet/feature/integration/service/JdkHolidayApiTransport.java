package com.lab.labtimesheet.feature.integration.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Default JDK HTTP transport for the fixed Vietnam HolidayAPI endpoint. */
@Component
public class JdkHolidayApiTransport implements HolidayApiTransport {

    private final HttpClient client;
    private final String baseUrl;

    JdkHolidayApiTransport(
            @Value("${lab.integrations.holiday-api-base-url:https://holidayapi.com}") String baseUrl) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public HolidayApiTransportResponse get(String apiKey, int year) {
        try {
            URI endpoint = URI.create(baseUrl + "/v1/holidays?country=VN&year=" + year
                    + "&key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new HolidayApiTransportResponse(response.statusCode(), response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new HolidayApiTransportResponse(0, null);
        } catch (IOException | RuntimeException unavailable) {
            return new HolidayApiTransportResponse(0, null);
        }
    }
}
