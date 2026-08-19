package com.lab.labtimesheet.feature.integration.service;

import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiResult;

/** Provider adapter boundary receiving a transient decrypted key for one explicit call. */
public interface HolidayApiProbe {
    /**
     * Calls HolidayAPI for the fixed Vietnam country.
     *
     * @param apiKey transient decrypted API key
     * @param year requested calendar year
     * @return safe provider result
     */
    HolidayApiResult preview(String apiKey, int year);
}
