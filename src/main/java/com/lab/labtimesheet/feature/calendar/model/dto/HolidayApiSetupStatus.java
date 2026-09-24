package com.lab.labtimesheet.feature.calendar.model.dto;

/** Non-secret setup snapshot for the Admin HolidayAPI configuration surface. */
public record HolidayApiSetupStatus(boolean active, Long draftId, boolean tested, String countryCode) {
}
