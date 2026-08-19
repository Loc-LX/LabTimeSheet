package com.lab.labtimesheet.feature.integration.model.dto;

import com.lab.labtimesheet.feature.integration.model.HolidayApiFailureKind;

/** Typed HolidayAPI success/failure result without raw provider data or diagnostics. */
public record HolidayApiResult(HolidayApiPreview preview, HolidayApiFailureKind failure) {
    public HolidayApiResult {
        if ((preview == null) == (failure == null)) {
            throw new IllegalArgumentException("HolidayAPI result must contain exactly one outcome");
        }
    }

    /** @return a successful result containing preview candidates */
    public static HolidayApiResult success(HolidayApiPreview preview) {
        return new HolidayApiResult(preview, null);
    }

    /** @return a safe typed failure result */
    public static HolidayApiResult failure(HolidayApiFailureKind failure) {
        return new HolidayApiResult(null, failure);
    }

    /** @return whether the provider returned usable holiday data */
    public boolean successful() {
        return preview != null;
    }
}
