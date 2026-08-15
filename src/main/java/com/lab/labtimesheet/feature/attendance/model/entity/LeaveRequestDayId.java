package com.lab.labtimesheet.feature.attendance.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Composite identifier of one frozen quota-consuming date within a leave request.
 */
@Embeddable
public class LeaveRequestDayId implements Serializable {

    /** Parent request identity used by the composite primary key. */
    @Column(name = "leave_request_id", nullable = false)
    private long leaveRequestId;

    /** Exact frozen allocation date used by the composite primary key. */
    @Column(name = "leave_date", nullable = false)
    private LocalDate leaveDate;

    /**
     * Required by JPA.
     */
    protected LeaveRequestDayId() {}

    /**
     * Creates the identity for an already-persisted request and its exact allocated date.
     *
     * @param leaveRequestId persisted leave request identifier
     * @param leaveDate frozen quota-consuming date
     */
    public LeaveRequestDayId(long leaveRequestId, LocalDate leaveDate) {
        this.leaveRequestId = leaveRequestId;
        this.leaveDate = Objects.requireNonNull(leaveDate, "leaveDate");
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof LeaveRequestDayId other
                && leaveRequestId == other.leaveRequestId
                && leaveDate.equals(other.leaveDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(leaveRequestId, leaveDate);
    }
}
