package com.lab.labtimesheet.feature.integration.model.dto;

/** Non-secret HolidayAPI revision state used by the Admin setup page. */
public record HolidayApiSetupStatus(boolean active, Long draftId, boolean tested, String countryCode) {
}
