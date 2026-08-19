package com.lab.labtimesheet.feature.integration.service;

import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiResult;

/**
 * DTO-only HolidayAPI boundary for attendance and explicit Admin previews.
 * Implementations must never make calls during local calendar reads.
 */
public interface HolidayApiClient {
    /**
     * Previews holidays for one year using the active encrypted revision.
     *
     * @param year requested calendar year
     * @return safe success or typed provider/configuration failure
     */
    HolidayApiResult preview(int year);
}
