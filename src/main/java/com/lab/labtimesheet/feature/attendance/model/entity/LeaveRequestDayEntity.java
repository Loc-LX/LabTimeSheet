package com.lab.labtimesheet.feature.attendance.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;
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

    @Column(name = "policy_version_id", nullable = false)
    private long policyVersionId;

    @Column(name = "quota_month", nullable = false)
    private LocalDate quotaMonth;

    @Column(name = "monthly_quota_snapshot", nullable = false)
    private int monthlyQuotaSnapshot;

    @Column(name = "approval_withdrawn_at")
    private Instant approvalWithdrawnAt;

    /**
     * Creates an immutable frozen allocation after the parent request has been persisted.
     *
     * @param request persisted leave request
     * @param leaveDate exact requested local date
     * @param policyVersionId attached historical policy version identifier
     * @param monthlyQuotaSnapshot quota captured for the request month
     */
    public LeaveRequestDayEntity(
            LeaveRequestEntity request,
            LocalDate leaveDate,
            long policyVersionId,
            int monthlyQuotaSnapshot) {
        this.request = request;
        this.id = new LeaveRequestDayId(request.id(), leaveDate);
        this.policyVersionId = policyVersionId;
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
        return policyVersionId;
    }

    /**
     * Returns the optional approval-withdrawal timestamp.
     *
     * @return withdrawal instant, or {@code null} when approval has not been withdrawn
     */
    public Instant approvalWithdrawnAt() {
        return approvalWithdrawnAt;
    }

    /**
     * Marks this day's approval as withdrawn. The day must not have been withdrawn already.
     *
     * @param now server timestamp of the amendment
     * @throws IllegalStateException if approval has already been withdrawn
     */
    public void withdrawApproval(Instant now) {
        if (approvalWithdrawnAt != null) {
            throw new IllegalStateException("Approval has already been withdrawn for this leave day");
        }
        this.approvalWithdrawnAt = now;
    }
}
