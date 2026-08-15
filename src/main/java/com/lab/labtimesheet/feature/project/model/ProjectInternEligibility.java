package com.lab.labtimesheet.feature.project.model;

/**
 * Account-owned eligibility fact used by the Project aggregate without importing Account
 * persistence types.
 *
 * @param userId Intern account identifier
 * @param eligible true only when both account and internship are active for the relevant check
 */
public record ProjectInternEligibility(long userId, boolean eligible) {

    /**
     * Rejects invalid identifiers before they enter Project membership history.
     *
     * @param userId Intern account identifier
     * @param eligible Account-service eligibility decision
     */
    public ProjectInternEligibility {
        if (userId <= 0) {
            throw new IllegalArgumentException("Intern user ID must be positive");
        }
    }

    /**
     * Returns the Account-service eligibility decision.
     *
     * @return true when the Intern may participate in the requested Project operation
     */
    public boolean isEligible() {
        return eligible;
    }
}
