package com.lab.labtimesheet.feature.integration.model.dto;

/** Request-local cleartext HolidayAPI credential; it must be encrypted before persistence. */
public record HolidayApiDraft(String apiKey) {
}
