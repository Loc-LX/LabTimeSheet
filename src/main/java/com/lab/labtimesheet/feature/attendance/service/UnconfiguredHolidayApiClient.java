package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.attendance.exception.HolidayCalendarException;
import com.lab.labtimesheet.feature.attendance.model.dto.HolidayCandidate;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Default HolidayAPI client used when the platform feature has not supplied the
 * tested HTTP client. It reports an actionable failure so Admin still reaches the
 * manual custom-event fallback (CAL-005, AC-CAL-001). Registered by
 * {@link HolidayApiClientConfiguration}; the platform implementation replaces this
 * bean once available.
 */
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class UnconfiguredHolidayApiClient implements HolidayApiClient {

    @Override
    public List<HolidayCandidate> fetchVnHolidays(int year) {
        throw new HolidayCalendarException(
                "HolidayAPI is not configured; add credentials or use manual calendar events instead");
    }
}