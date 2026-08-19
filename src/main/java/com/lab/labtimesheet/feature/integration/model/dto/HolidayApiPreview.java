package com.lab.labtimesheet.feature.integration.model.dto;

import java.util.List;

/** Explicit Vietnam HolidayAPI preview data; it is never used by local reads implicitly. */
public record HolidayApiPreview(int year, String countryCode, List<HolidayApiCandidate> holidays) {
    public HolidayApiPreview {
        if (!"VN".equals(countryCode)) {
            throw new IllegalArgumentException("HolidayAPI country is fixed to VN");
        }
        holidays = List.copyOf(holidays);
    }
}
