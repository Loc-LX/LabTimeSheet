package com.lab.labtimesheet.platform.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/** Validated identifier submitted by the SMTP test and activation forms. */
@Getter
@Setter
public class SmtpActionForm {
    @NotNull(message = "SMTP draft is required")
    @Positive(message = "SMTP draft is invalid")
    private Long draftId;

}
