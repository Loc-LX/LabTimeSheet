package com.lab.labtimesheet.feature.integration.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Validated identifier submitted by the SMTP test and activation forms. */
public class SmtpActionForm {
    @NotNull(message = "SMTP draft is required")
    @Positive(message = "SMTP draft is invalid")
    private Long draftId;

    public Long getDraftId() {
        return draftId;
    }

    public void setDraftId(Long draftId) {
        this.draftId = draftId;
    }
}
