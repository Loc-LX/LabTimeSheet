package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.attendance.exception.CalendarException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.GlobalCalendarEvent;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendancePolicyEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.GlobalCalendarEventEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendancePolicyRepository;
import com.lab.labtimesheet.feature.attendance.repository.GlobalCalendarEventRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional boundary for the locally authoritative global calendar and its cross-feature day-off decision.
 */
@Service
public class CalendarApplicationService {

    private final Clock clock;
    private final AttendancePolicyRepository policies;
    private final GlobalCalendarEventRepository events;

    CalendarApplicationService(
            Clock clock,
            AttendancePolicyRepository policies,
            GlobalCalendarEventRepository events) {
        this.clock = clock;
        this.policies = policies;
        this.events = events;
    }

    /**
     * Creates an Admin-authored custom event on a non-past policy-local date.
     *
     * @param actor authenticated Attendance actor; must be Admin
     * @param date local event date
     * @param name non-blank display name
     * @param dayOff authoritative attendance/due-date exemption choice
     * @return persisted event DTO including optimistic version
     */
    @Transactional
    public GlobalCalendarEvent createManual(
            AttendanceActor actor, LocalDate date, String name, boolean dayOff) {
        requireAdmin(actor);
        requireMutableDate(date);
        return events.saveAndFlush(new GlobalCalendarEventEntity(date, requireName(name), dayOff, actor.userId()))
                .toDomain();
    }

    /**
     * Updates a future custom event when the submitted optimistic version still matches.
     * Past event dates and attempts to move an event into the past are rejected.
     *
     * @param actor authenticated Attendance actor; must be Admin
     * @param eventId event identifier
     * @param expectedVersion version rendered to the editor
     * @param date replacement local event date
     * @param name replacement non-blank display name
     * @param dayOff replacement authoritative day-off choice
     * @return updated event DTO and advanced version
     */
    @Transactional
    public GlobalCalendarEvent updateManual(
            AttendanceActor actor,
            long eventId,
            long expectedVersion,
            LocalDate date,
            String name,
            boolean dayOff) {
        requireAdmin(actor);
        GlobalCalendarEventEntity event = events.findById(eventId)
                .orElseThrow(() -> new CalendarException("Calendar event not found"));
        requireMutableDate(event.calendarDate());
        requireMutableDate(date);
        if (event.version() != expectedVersion) {
            throw new CalendarException("Calendar event was changed by another request");
        }
        event.update(date, requireName(name), dayOff, actor.userId());
        return events.saveAndFlush(event).toDomain();
    }

    /**
     * Lists locally stored events over an inclusive date range.
     *
     * @param from inclusive first local date
     * @param to inclusive last local date
     * @return events ordered by date and identifier
     */
    @Transactional(readOnly = true)
    public List<GlobalCalendarEvent> list(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        return events.findByCalendarDateBetweenOrderByCalendarDateAscIdAsc(from, to)
                .stream()
                .map(GlobalCalendarEventEntity::toDomain)
                .toList();
    }

    /**
     * Answers whether the exact local date has any authoritative stored day-off event.
     * This is the public cross-feature calendar API; it performs no live HolidayAPI call.
     *
     * @param date local business date to inspect
     * @return {@code true} when at least one local event is marked as a day off
     */
    @Transactional(readOnly = true)
    public boolean isGlobalDayOff(LocalDate date) {
        return events.existsByCalendarDateAndDayOffTrue(date);
    }

    private void requireMutableDate(LocalDate date) {
        AttendancePolicy policy = new AttendancePolicyTimeline(policies
                        .findAllByOrderByEffectiveFromAsc()
                        .stream()
                        .map(AttendancePolicyEntity::toDomain)
                        .toList())
                .resolve(clock.instant());
        LocalDate today = clock.instant().atZone(policy.zoneId()).toLocalDate();
        if (date.isBefore(today)) {
            throw new CalendarException("Past calendar events are immutable");
        }
    }

    private static void requireAdmin(AttendanceActor actor) {
        if (actor.role() != AttendanceRole.ADMIN) {
            throw new AccessDeniedException("Only Admin may manage the global calendar");
        }
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return name.strip();
    }
}
