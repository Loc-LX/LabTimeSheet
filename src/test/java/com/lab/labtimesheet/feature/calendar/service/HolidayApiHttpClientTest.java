package com.lab.labtimesheet.feature.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Modifier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class HolidayApiHttpClientTest {
    private HttpServer server;

    @Test
    void productionClientUsesOnlyTheOfficialHttpsOrigin() throws NoSuchMethodException {
        assertThat(HolidayApiHttpClient.PRODUCTION_BASE_URL).isEqualTo("https://holidayapi.com");
        assertThat(Modifier.isPublic(HolidayApiHttpClient.class.getDeclaredConstructor().getModifiers())).isTrue();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void parsesSafeVietnameseHolidayCandidatesAndSendsFixedCountry() throws IOException {
        AtomicReference<String> request = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/holidays", exchange -> {
            request.set(exchange.getRequestURI().toString());
            respond(exchange, 200, """
                    {"status":200,"holidays":[{"name":"Tet Holiday","date":"2026-02-17",
                    "observed":"2026-02-17","public":true,"country":"VN","uuid":"vn-tet-2026"}]}
                    """);
        });
        server.start();

        var client = new HolidayApiHttpClient(RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort());
        var result = client.fetch("stub-api-key", "VN", 2026);

        assertThat(request).hasValueSatisfying(uri -> assertThat(uri)
                .contains("country=VN", "year=2026", "key=stub-api-key"));
        assertThat(result).singleElement().satisfies(candidate -> {
            assertThat(candidate.uuid()).isEqualTo("vn-tet-2026");
            assertThat(candidate.name()).isEqualTo("Tet Holiday");
            assertThat(candidate.publicHoliday()).isTrue();
        });
    }

    @Test
    void mapsProviderFailureStatusesWithoutReturningProviderBody() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/holidays", exchange -> respond(exchange, 401, "secret provider detail"));
        server.start();

        var client = new HolidayApiHttpClient(RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort());

        assertThatThrownBy(() -> client.fetch("stub-api-key", "VN", 2026))
                .isInstanceOf(HolidayApiClientException.class)
                .hasMessage("HolidayAPI rejected the configured key.")
                .hasMessageNotContaining("secret provider detail")
                .hasMessageNotContaining("stub-api-key");
    }

    @Test
    void mapsBadParametersAndHttpsRequiredToUnavailableWithoutLeakingProviderDetails() throws IOException {
        AtomicReference<Integer> status = new AtomicReference<>(400);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/holidays", exchange -> respond(exchange, status.get(), "provider detail"));
        server.start();
        var client = new HolidayApiHttpClient(RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort());

        for (int providerStatus : new int[] {400, 403}) {
            status.set(providerStatus);
            assertThatThrownBy(() -> client.fetch("stub-api-key", "VN", 2026))
                    .isInstanceOf(HolidayApiClientException.class)
                    .hasMessage("HolidayAPI is unavailable; local calendar data remains usable.")
                    .hasMessageNotContaining("provider detail")
                    .hasMessageNotContaining("stub-api-key");
        }
    }

    @Test
    void mapsRateLimitAndUnavailableResponsesToActionableSafeFailures() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/holidays", exchange -> respond(exchange, 429, "quota detail"));
        server.start();
        var rateClient = new HolidayApiHttpClient(RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort());

        assertThatThrownBy(() -> rateClient.fetch("stub-api-key", "VN", 2026))
                .isInstanceOf(HolidayApiClientException.class)
                .hasMessage("HolidayAPI rate limit reached; try again later.")
                .hasMessageNotContaining("quota detail");

        server.stop(0);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/holidays", exchange -> respond(exchange, 503, "outage detail"));
        server.start();
        var unavailableClient = new HolidayApiHttpClient(RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort());

        assertThatThrownBy(() -> unavailableClient.fetch("stub-api-key", "VN", 2026))
                .isInstanceOf(HolidayApiClientException.class)
                .hasMessage("HolidayAPI is unavailable; local calendar data remains usable.")
                .hasMessageNotContaining("outage detail");
    }

    @Test
    void timesOutWhenProviderStallsBeforeReturningAResponse() throws Exception {
        CountDownLatch requestReceived = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/holidays", exchange -> {
            requestReceived.countDown();
            await(releaseResponse);
            respond(exchange, 200, "{\"status\":200,\"holidays\":[]}");
        });
        server.start();
        var client = new HolidayApiHttpClient(RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var request = executor.submit(() -> client.fetch("stub-api-key", "VN", 2026));
            assertThat(requestReceived.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> request.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasRootCauseInstanceOf(HolidayApiClientException.class)
                    .hasRootCauseMessage("HolidayAPI is unavailable; local calendar data remains usable.");
        } finally {
            releaseResponse.countDown();
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for stalled-server release");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for stalled-server release", exception);
        }
    }
}
