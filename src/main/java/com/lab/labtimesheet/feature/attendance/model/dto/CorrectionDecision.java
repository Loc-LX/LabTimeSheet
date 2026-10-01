package com.lab.labtimesheet.feature.attendance.model.dto;

/** Mentor correction transition requested inside the separate decision window. */
public enum CorrectionDecision {
    /** Accept the proposed effective checkout. */
    APPROVE,
    /** Reject the proposed effective checkout. */
    REJECT,
    /** Amend the decision note of an already-decided correction. */
    AMEND,
    /** Reverse an approved or rejected decision to the opposite outcome. */
    REVERSE
}
