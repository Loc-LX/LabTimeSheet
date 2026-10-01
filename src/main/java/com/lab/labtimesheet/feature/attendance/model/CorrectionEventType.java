package com.lab.labtimesheet.feature.attendance.model;

/** Append-only correction decision event kinds retained by the V1 schema. */
public enum CorrectionEventType {
    /** Initial Intern submission. */
    SUBMITTED,
    /** Mentor approval. */
    APPROVED,
    /** Mentor rejection. */
    REJECTED,
    /** Mentor amends the decision note. */
    AMENDED,
    /** Mentor reverses an approved or rejected decision. */
    REVERSED,
    /** Decision deadline elapsed before Mentor action. */
    OVERDUE,
    /** Scheduler/request-time automatic rejection. */
    AUTO_REJECTED,
    /** Decision window lock marker. */
    LOCKED,
    /** Legacy history only: before MC-04 a Mentor could reopen a decision to pending; kept so stored rows still load, never written. */
    REOPENED
}
