package com.lab.labtimesheet.feature.attendance.model.dto;

/** Mentor correction transition requested inside the separate decision window. */
public enum CorrectionDecision {
    /** Accept the proposed effective checkout. */
    APPROVE,
    /** Reject the proposed effective checkout. */
    REJECT,
    /** Reopen an approved/rejected decision to pending. */
    REOPEN
}
