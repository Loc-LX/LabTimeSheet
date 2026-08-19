package com.lab.labtimesheet.feature.account.model.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Append-only record of one changed account field made through the Admin account-edit flow. Rows are
 * never updated or removed; the migration table has no delete boundary and the repository exposes no
 * removal operation, satisfying the GOV-009 audit requirement with a field-specific trail.
 */
@Entity
@Table(name = "account_admin_edit_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountAdminEditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_user_id", nullable = false)
    @Getter
    private Long targetUserId;

    @Column(name = "actor_user_id", nullable = false)
    @Getter
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_name", nullable = false, length = 32)
    @Getter
    private AccountAdminEditField field;

    @Column(name = "old_value", length = 320)
    @Getter
    private String oldValue;

    @Column(name = "new_value", length = 320)
    @Getter
    private String newValue;

    @Column(name = "occurred_at", nullable = false)
    @Getter
    private Instant occurredAt;

    private AccountAdminEditEvent(
            long targetUserId, long actorUserId, AccountAdminEditField field,
            String oldValue, String newValue, Instant occurredAt) {
        this.targetUserId = targetUserId;
        this.actorUserId = actorUserId;
        this.field = field;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.occurredAt = occurredAt;
    }

    /**
     * Creates an immutable audit row for a single changed account field.
     *
     * @param targetUserId account whose field changed
     * @param actorUserId active Admin who authorized the change
     * @param field the changed field
     * @param oldValue value before the change, {@code null} when the previous value was absent
     * @param newValue value after the change, {@code null} when the new value is absent
     * @param occurredAt server timestamp of the change
     * @return append-only audit row
     */
    public static AccountAdminEditEvent of(
            long targetUserId, long actorUserId, AccountAdminEditField field,
            String oldValue, String newValue, Instant occurredAt) {
        return new AccountAdminEditEvent(targetUserId, actorUserId, field, oldValue, newValue, occurredAt);
    }
}