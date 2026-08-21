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
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * JPA mapping of an immutable leave-day allocation whose exact date, policy, and quota snapshot remain historical.
 */
@Entity
@Table(name = "leave_request_days")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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
     * Creates an immutable frozen allocation after the parent request has been persisted.
     *
     * @param request persisted leave request
     * @param leaveDate exact requested local date
     * @param policy attached historical policy version
     * @param monthlyQuotaSnapshot quota captured for the request month
     */
    public LeaveRequestDayEntity(
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

    /**
     * Returns the exact frozen leave date.
     *
     * @return allocated local workday
     */
    public LocalDate leaveDate() {
        return id.leaveDate();
    }

    /**
     * Returns the frozen quota month.
     *
     * @return first local date of the quota month
     */
    public LocalDate quotaMonth() {
        return quotaMonth;
    }

    /**
     * Returns the frozen monthly quota snapshot.
     *
     * @return quota value captured at allocation time
     */
    public int monthlyQuotaSnapshot() {
        return monthlyQuotaSnapshot;
    }

    /**
     * Returns the attached historical policy version identifier.
     *
     * @return policy version identifier
     */
    public long policyVersionId() {
        return policy.toDomain().id();
    }
}
