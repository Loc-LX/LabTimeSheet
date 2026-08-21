package com.lab.labtimesheet.feature.integration.service;

/** Internal HTTP response without logging or exposing the request credential. */
public record HolidayApiTransportResponse(int statusCode, String body) {
}
