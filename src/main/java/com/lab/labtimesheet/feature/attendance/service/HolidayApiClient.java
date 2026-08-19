package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.attendance.model.dto.HolidayCandidate;
import java.util.List;

/**
 * Contract for fetching Vietnamese holiday candidates, owned by the attendance
 * feature. The platform feature supplies the tested HTTP transport and active
 * credential resolution; attendance performs interpretation, preview selection,
 * deduplication, import, and day-off effects. Implementations never run on the
 * live path of attendance, leave, dashboards, or reports.
 */
public interface HolidayApiClient {

    /**
     * Returns the Vietnamese holiday candidates for the given year.
     *
     * @param year calendar year to fetch
     * @return interpreted candidates
     * @throws com.lab.labtimesheet.feature.attendance.exception.HolidayCalendarException
     *         when the API is unconfigured, unauthorized, rate-limited, or unreachable
     */
    List<HolidayCandidate> fetchVnHolidays(int year);
}