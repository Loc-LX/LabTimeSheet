package com.lab.labtimesheet.feature.attendance.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * JPA mapping of an immutable leave-day allocation whose exact date, policy, and quota snapshot remain historical.
 */
@Entity
@Table(name = "leave_request_days")
public class LeaveRequestDayEntity {

    @EmbeddedId
    private LeaveRequestDayId id;

    @MapsId("leaveRequestId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_request_id", nullable = false)
    private LeaveRequestEntity request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_version_id", nullable = false)
    private AttendancePolicyEntity policy;

    @Column(name = "quota_month", nullable = false)
    private LocalDate quotaMonth;

    @Column(name = "monthly_quota_snapshot", nullable = false)
    private int monthlyQuotaSnapshot;

    /**
     * Required by JPA.
     */
    protected LeaveRequestDayEntity() {}

    LeaveRequestDayEntity(
            LeaveRequestEntity request,
            LocalDate leaveDate,
            AttendancePolicyEntity policy,
            int monthlyQuotaSnapshot) {
        this.request = request;
        this.id = new LeaveRequestDayId(request.id(), leaveDate);
        this.policy = policy;
        this.quotaMonth = leaveDate.withDayOfMonth(1);
        this.monthlyQuotaSnapshot = monthlyQuotaSnapshot;
    }
}
