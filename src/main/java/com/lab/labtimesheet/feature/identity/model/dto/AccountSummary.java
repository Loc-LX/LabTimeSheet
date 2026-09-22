package com.lab.labtimesheet.feature.identity.model.dto;

/**
 * Current account metrics exposed to reporting without persistence coupling.
 *
 * @param activeAccounts accounts able to authenticate
 * @param pendingActivations accounts awaiting first-password activation
 * @param activeInternships Intern profiles in the active lifecycle state
 */
public record AccountSummary(long activeAccounts, long pendingActivations, long activeInternships) {
}
