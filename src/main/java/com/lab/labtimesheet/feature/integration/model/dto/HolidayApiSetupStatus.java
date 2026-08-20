package com.lab.labtimesheet.feature.integration.model.dto;

/** Non-secret setup snapshot for the Admin HolidayAPI configuration surface. */
public record HolidayApiSetupStatus(boolean active, Long draftId, boolean tested, String countryCode) {
}
