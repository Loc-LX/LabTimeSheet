package com.lab.labtimesheet.feature.attendance.model.dto;

import com.lab.labtimesheet.feature.attendance.model.CorrectionEventType;
import com.lab.labtimesheet.feature.attendance.model.CorrectionStatus;
import java.time.Instant;

/**
 * Persistence-free immutable correction transition event.
 *
 * @param id event identifier
 * @param type transition kind, including expiry and lock markers
 * @param fromStatus state before the transition, or {@code null} for submission
 * @param toStatus state after the transition
 * @param actorUserId Mentor actor, or {@code null} for scheduler transitions
 * @param note optional decision or expiry note
 * @param occurredAt server timestamp of the immutable event
 */
public record CorrectionEventView(
        long id,
        CorrectionEventType type,
        CorrectionStatus fromStatus,
        CorrectionStatus toStatus,
        Long actorUserId,
        String note,
        Instant occurredAt) {}
