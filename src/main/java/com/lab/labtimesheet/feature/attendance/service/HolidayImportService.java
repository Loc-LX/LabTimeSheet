package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.attendance.exception.HolidayCalendarException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.HolidayCandidate;
import com.lab.labtimesheet.feature.attendance.model.dto.HolidayImportSummary;
import com.lab.labtimesheet.feature.attendance.model.dto.HolidayPreviewRow;
import com.lab.labtimesheet.feature.attendance.model.dto.HolidaySelection;
import com.lab.labtimesheet.feature.attendance.model.entity.GlobalCalendarEventEntity;
import com.lab.labtimesheet.feature.attendance.repository.GlobalCalendarEventRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional Admin boundary that previews and imports Vietnamese HolidayAPI
 * candidates into the locally authoritative global calendar. The source is a
 * suggestion, never authority: {@code public=true} only preselects the preview
 * row and the Admin's explicit day-off choice is stored.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class HolidayImportService {

    private static final String HOLIDAY_API_SOURCE = "HOLIDAY_API";

    private final Clock clock;
    private final HolidayApiClient client;
    private final GlobalCalendarEventRepository events;

    /**
     * Interprets one year of Vietnamese candidates for Admin review.
     *
     * @param actor authenticated Attendance actor; must be Admin
     * @param year calendar year between 2000 and 2100
     * @return preview rows where public holidays are preselected and previously
     *         imported source UUIDs are disclosed
     */
    @Transactional(readOnly = true)
    public List<HolidayPreviewRow> preview(AttendanceActor actor, int year) {
        requireAdmin(actor);
        requireYear(year);
        List<HolidayCandidate> candidates = client.fetchVnHolidays(year);
        return candidates.stream()
                .map(candidate -> new HolidayPreviewRow(
                        candidate,
                        candidate.publicHoliday(),
                        events.existsBySourceAndSourceUuid(HOLIDAY_API_SOURCE, candidate.uuid())))
                .toList();
    }

    /**
     * Imports the Admin's explicitly selected candidates with full provenance.
     * A source UUID already stored locally is skipped idempotently rather than
     * overwritten; a repeated import of the same selection is safe.
     *
     * @param actor authenticated Attendance actor; must be Admin
     * @param year calendar year the selections were previewed from
     * @param selections explicit import choices
     * @return count of newly imported events and of skipped existing UUIDs
     */
    @Transactional
    public HolidayImportSummary importSelections(
            AttendanceActor actor, int year, List<HolidaySelection> selections) {
        requireAdmin(actor);
        requireYear(year);
        Map<String, HolidayCandidate> candidates = client.fetchVnHolidays(year).stream()
                .collect(Collectors.toMap(HolidayCandidate::uuid, Function.identity()));
        int imported = 0;
        int skipped = 0;
        for (HolidaySelection selection : selections) {
            HolidayCandidate candidate = candidates.get(selection.uuid());
            if (candidate == null) {
                throw new HolidayCalendarException("Holiday candidate not found: " + selection.uuid());
            }
            if (events.existsBySourceAndSourceUuid(HOLIDAY_API_SOURCE, candidate.uuid())) {
                skipped++;
                continue;
            }
            try {
                events.saveAndFlush(new GlobalCalendarEventEntity(
                        candidate.observedDate(),
                        candidate.name(),
                        candidate,
                        selection.dayOff(),
                        clock.instant(),
                        actor.userId()));
                imported++;
            } catch (DataIntegrityViolationException duplicate) {
                skipped++;
            }
        }
        return new HolidayImportSummary(imported, skipped);
    }

    private static void requireYear(int year) {
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("year must be between 2000 and 2100");
        }
    }

    private static void requireAdmin(AttendanceActor actor) {
        if (actor.role() != AttendanceRole.ADMIN) {
            throw new AccessDeniedException("Only Admin may preview or import holidays");
        }
    }
}