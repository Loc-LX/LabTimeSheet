package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.attendance.exception.CalendarException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.GlobalCalendarEvent;
import com.lab.labtimesheet.feature.attendance.model.dto.CalendarHistoryItem;
import com.lab.labtimesheet.feature.attendance.model.dto.CalendarImportSelection;
import com.lab.labtimesheet.feature.attendance.model.dto.CalendarPreviewItem;
import com.lab.labtimesheet.feature.attendance.model.dto.HolidayCandidate;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendancePolicyEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.GlobalCalendarEventEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendancePolicyRepository;
import com.lab.labtimesheet.feature.attendance.repository.GlobalCalendarEventRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional boundary for the locally authoritative global calendar and its cross-feature day-off decision.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class CalendarApplicationService {

    private final Clock clock;
    private final AttendancePolicyRepository policies;
    private final GlobalCalendarEventRepository events;

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
        return events.saveAndFlush(new GlobalCalendarEventEntity(
                        date, requireName(name), dayOff, actor.userId(), clock.instant()))
                .toDomain();
    }

    /**
     * Presents upstream holiday candidates for explicit Admin review without making them authoritative.
     *
     * @param actor authenticated Admin actor
     * @param candidates non-secret candidates returned by the Platform HolidayAPI client
     * @return preview rows with public holidays preselected only
     */
    @Transactional(readOnly = true)
    public List<CalendarPreviewItem> preview(AttendanceActor actor, List<HolidayCandidate> candidates) {
        requireAdmin(actor);
        if (candidates == null) {
            throw new IllegalArgumentException("Holiday candidates are required");
        }
        Set<String> seenSourceUuids = new HashSet<>();
        return candidates.stream()
                .map(CalendarApplicationService::requireCandidate)
                .filter(candidate -> seenSourceUuids.add(candidate.sourceUuid()))
                .map(candidate -> new CalendarPreviewItem(candidate, candidate.publicHoliday()))
                .toList();
    }

    /**
     * Copies explicitly selected candidates into the local calendar, skipping an existing source UUID idempotently.
     * Existing rows are never overwritten by a repeated import.
     *
     * @param actor authenticated Admin actor
     * @param selections explicit local day-off decisions
     * @return newly imported local history rows; an empty result means every selection was already imported
     */
    @Transactional
    public List<CalendarHistoryItem> importSelected(
            AttendanceActor actor, List<CalendarImportSelection> selections) {
        requireAdmin(actor);
        if (selections == null) {
            throw new IllegalArgumentException("Calendar selections are required");
        }
        return selections.stream()
                .map(selection -> importOne(actor, selection))
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    /**
     * Lists all retained past/current/future event metadata for the Admin History view.
     *
     * @param actor authenticated Admin actor
     * @return retained events ordered by local date and version
     */
    @Transactional(readOnly = true)
    public List<CalendarHistoryItem> history(AttendanceActor actor) {
        requireAdmin(actor);
        return events.findAll().stream()
                .map(GlobalCalendarEventEntity::toHistory)
                .sorted(Comparator.comparing(CalendarHistoryItem::calendarDate)
                        .thenComparingLong(CalendarHistoryItem::id))
                .toList();
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
        event.update(date, requireName(name), dayOff, actor.userId(), clock.instant());
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
        if (actor == null || actor.role() != AttendanceRole.ADMIN) {
            throw new AccessDeniedException("Only Admin may manage the global calendar");
        }
    }

    private java.util.Optional<CalendarHistoryItem> importOne(
            AttendanceActor actor, CalendarImportSelection selection) {
        if (selection == null) {
            throw new IllegalArgumentException("Calendar selection is required");
        }
        HolidayCandidate candidate = selection.candidate();
        if (events.findBySourceAndSourceUuid("HOLIDAY_API", candidate.sourceUuid()).isPresent()) {
            return java.util.Optional.empty();
        }
        requireMutableDate(candidate.observedDate());
        GlobalCalendarEventEntity entity = events.saveAndFlush(new GlobalCalendarEventEntity(
                candidate, selection.dayOff(), actor.userId(), clock.instant()));
        return java.util.Optional.of(entity.toHistory());
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return name.strip();
    }

    private static HolidayCandidate requireCandidate(HolidayCandidate candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("Holiday candidate is required");
        }
        return candidate;
    }
}
