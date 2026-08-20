package com.lab.labtimesheet.feature.attendance.model;

/** Append-only correction decision event kinds retained by the V1 schema. */
public enum CorrectionEventType {
    /** Initial Intern submission. */
    SUBMITTED,
    /** Mentor approval. */
    APPROVED,
    /** Mentor rejection. */
    REJECTED,
    /** Mentor reopens a prior decision inside the window. */
    REOPENED,
    /** Scheduler/request-time automatic rejection. */
    AUTO_REJECTED,
    /** Decision window lock marker. */
    LOCKED
}
