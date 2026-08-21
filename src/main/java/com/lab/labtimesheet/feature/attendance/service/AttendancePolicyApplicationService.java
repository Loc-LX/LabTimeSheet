package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.attendance.exception.PolicyException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendancePolicyCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendancePolicyHistoryItem;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendancePolicyEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendancePolicyRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin transaction boundary for the effective-dated Attendance policy timeline.
 * Effective versions are immutable; only a future version may be replaced before its first local date.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class AttendancePolicyApplicationService {

    private final Clock clock;
    private final AttendancePolicyRepository policies;

    /**
     * Schedules or replaces a future first-of-month policy and returns non-secret history metadata.
     *
     * @param actor authenticated Admin actor
     * @param command policy values to persist
     * @return retained Policy History projection
     */
    @Transactional
    public AttendancePolicyHistoryItem schedule(AttendanceActor actor, AttendancePolicyCommand command) {
        requireAdmin(actor);
        validateCommand(command);
        Instant now = clock.instant();
        AttendancePolicy todayPolicy = timeline().resolve(now);
        LocalDate today = now.atZone(todayPolicy.zoneId()).toLocalDate();
        LocalDate currentMonth = today.withDayOfMonth(1);
        if (command.effectiveFrom().getDayOfMonth() != 1
                || !command.effectiveFrom().isAfter(currentMonth)) {
            throw new PolicyException("Policy must begin on the first day of a future month");
        }

        AttendancePolicyEntity entity = policies.findForUpdateByEffectiveFrom(command.effectiveFrom())
                .map(existing -> {
                    if (!existing.effectiveFrom().isAfter(today)) {
                        throw new PolicyException("Effective policy versions are immutable");
                    }
                    existing.replace(command, actor.userId(), now);
                    return existing;
                })
                .orElseGet(() -> new AttendancePolicyEntity(command, actor.userId(), now));
        return policies.saveAndFlush(entity).toHistory();
    }

    /**
     * Lists all retained versions for the Admin-only Policy History view.
     *
     * @param actor authenticated Admin actor
     * @return versions ordered by effective date
     */
    @Transactional(readOnly = true)
    public List<AttendancePolicyHistoryItem> history(AttendanceActor actor) {
        requireAdmin(actor);
        return policies.findAllByOrderByEffectiveFromAsc().stream()
                .map(AttendancePolicyEntity::toHistory)
                .toList();
    }

    private AttendancePolicyTimeline timeline() {
        return new AttendancePolicyTimeline(policies.findAllByOrderByEffectiveFromAsc().stream()
                .map(AttendancePolicyEntity::toDomain)
                .toList());
    }

    private static void validateCommand(AttendancePolicyCommand command) {
        if (command == null) {
            throw new PolicyException("Policy values are required");
        }
        new AttendancePolicy(
                0L,
                command.effectiveFrom(),
                command.zoneId(),
                command.scheduledStart(),
                command.scheduledEnd(),
                command.checkInGraceMinutes(),
                command.checkoutGraceMinutes(),
                command.monthlyLeaveQuota(),
                command.violationPenalty(),
                command.workdays());
    }

    private static void requireAdmin(AttendanceActor actor) {
        if (actor == null || actor.role() != AttendanceRole.ADMIN) {
            throw new AccessDeniedException("Only Admin may manage attendance policy");
        }
    }
}
