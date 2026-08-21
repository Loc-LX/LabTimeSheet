package com.lab.labtimesheet.feature.integration.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Write-only HolidayAPI credential form. The cleartext key is cleared before every re-render. */
@Getter
@Setter
public class HolidayApiForm {
    @NotBlank(message = "HolidayAPI key is required")
    @Size(max = 1024, message = "HolidayAPI key is too long")
    private String apiKey;

    /** @return request-local draft command; the caller encrypts the key immediately */
    public HolidayApiDraft toDraft() {
        return new HolidayApiDraft(apiKey == null || apiKey.isBlank() ? null : apiKey.trim());
    }

    /** Clears the request-local API key before rendering a response. */
    public void clearApiKey() {
        apiKey = null;
    }
}
