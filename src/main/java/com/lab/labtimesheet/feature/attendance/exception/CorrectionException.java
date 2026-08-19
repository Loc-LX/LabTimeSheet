package com.lab.labtimesheet.feature.attendance.exception;

/**
 * Signals a rejected missed-checkout correction with its stable business outcome.
 */
public class CorrectionException extends RuntimeException {

    private final CorrectionRejection rejection;

    /**
     * Creates an exception carrying the stable rejection reason.
     *
     * @param rejection business outcome for presentation and tests
     */
    public CorrectionException(CorrectionRejection rejection) {
        super(rejection.name());
        this.rejection = rejection;
    }

    /**
     * Returns the stable business outcome that caused this rejection.
     *
     * @return rejection reason
     */
    public CorrectionRejection rejection() {
        return rejection;
    }
}