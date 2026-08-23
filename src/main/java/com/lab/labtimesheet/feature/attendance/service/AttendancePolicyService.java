package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.attendance.exception.PolicyException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendancePolicyVersionView;
import com.lab.labtimesheet.feature.attendance.model.dto.SchedulePolicyCommand;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendancePolicyEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendancePolicyRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional Admin boundary that schedules and replaces effective-dated attendance-policy versions.
 * A new version must begin on the first day of a future calendar month; once that date arrives the version
 * becomes immutable so historical attendance rows, checkout cutoffs, and reports never drift.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class AttendancePolicyService {

    private final Clock clock;
    private final AttendancePolicyRepository policyEntities;

    /**
     * Schedules a new future-month policy version attributed to the authenticated Admin.
     *
     * @param actor authenticated Attendance actor; must be Admin
     * @param command validated schedule values
     * @return persisted policy version including its database identifier
     */
    @Transactional
    public AttendancePolicy scheduleVersion(AttendanceActor actor, SchedulePolicyCommand command) {
        requireAdmin(actor);
        AttendancePolicy policy = resolve(command);
        requireFirstOfFutureMonth(policy.effectiveFrom());
        return policyEntities.saveAndFlush(new AttendancePolicyEntity(policy, actor.userId()))
                .toDomain();
    }

    /**
     * Replaces a scheduled-but-not-yet-effective version when the submitted optimistic version still matches.
     * Effective versions are immutable and reject replacement.
     *
     * @param actor authenticated Attendance actor; must be Admin
     * @param policyId policy version identifier
     * @param expectedVersion version rendered to the editor
     * @param command replacement schedule values
     * @return replaced policy version with its advanced optimistic version
     */
    @Transactional
    public AttendancePolicy replaceVersion(
            AttendanceActor actor,
            long policyId,
            long expectedVersion,
            SchedulePolicyCommand command) {
        requireAdmin(actor);
        AttendancePolicyEntity entity = policyEntities.findById(policyId)
                .orElseThrow(() -> new PolicyException("Attendance policy version not found"));
        AttendancePolicy current = entity.toDomain();
        LocalDate today = currentBusinessDate();
        if (!current.effectiveFrom().isAfter(today)) {
            throw new PolicyException("Effective attendance policy versions are immutable");
        }
        if (entity.version() != expectedVersion) {
            throw new PolicyException("Attendance policy version was changed by another request");
        }
        AttendancePolicy replacement = resolve(command);
        requireFirstOfFutureMonth(replacement.effectiveFrom());
        entity.update(replacement);
        return policyEntities.saveAndFlush(entity).toDomain();
    }

    /**
     * Returns every policy version in effective-date order for deterministic resolution.
     *
     * @return ascending versions including the historical seed
     */
    @Transactional(readOnly = true)
    public List<AttendancePolicy> timeline() {
        return policyEntities.findAllByOrderByEffectiveFromAsc()
                .stream()
                .map(AttendancePolicyEntity::toDomain)
                .toList();
    }

    /**
     * Returns every policy version in effective-date order with its current optimistic
     * version, so the Admin editor can replace scheduled-but-not-yet-effective versions.
     *
     * @return ascending versions including the historical seed and their optimistic versions
     */
    @Transactional(readOnly = true)
    public List<AttendancePolicyVersionView> versions() {
        return policyEntities.findAllByOrderByEffectiveFromAsc()
                .stream()
                .map(entity -> new AttendancePolicyVersionView(entity.toDomain(), entity.version()))
                .toList();
    }

    private AttendancePolicy resolve(SchedulePolicyCommand command) {
        return new AttendancePolicy(
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

    private void requireFirstOfFutureMonth(LocalDate effectiveFrom) {
        if (effectiveFrom.getDayOfMonth() != 1) {
            throw new IllegalArgumentException("effectiveFrom must be the first day of a calendar month");
        }
        if (!effectiveFrom.isAfter(currentBusinessDate())) {
            throw new IllegalArgumentException("effectiveFrom must be after the current business date");
        }
    }

    private LocalDate currentBusinessDate() {
        AttendancePolicy current = new AttendancePolicyTimeline(policyEntities
                        .findAllByOrderByEffectiveFromAsc()
                        .stream()
                        .map(AttendancePolicyEntity::toDomain)
                        .toList())
                .resolve(clock.instant());
        return clock.instant().atZone(current.zoneId()).toLocalDate();
    }

    private static void requireAdmin(AttendanceActor actor) {
        if (actor.role() != AttendanceRole.ADMIN) {
            throw new AccessDeniedException("Only Admin may schedule attendance policy versions");
        }
    }
}