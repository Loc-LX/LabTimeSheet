package com.lab.labtimesheet.feature.integration.service;

/** Minimal transport seam used to keep HolidayAPI HTTP mapping deterministic in tests. */
@FunctionalInterface
public interface HolidayApiTransport {
    /** @return provider HTTP result, or a zero-status response when the provider is unavailable */
    HolidayApiTransportResponse get(String apiKey, int year);
}
