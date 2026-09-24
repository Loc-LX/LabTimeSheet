package com.lab.labtimesheet.feature.calendar.model.entity;

import com.lab.labtimesheet.feature.calendar.model.dto.CalendarHistoryItem;
import com.lab.labtimesheet.feature.calendar.model.dto.GlobalCalendarEvent;
import com.lab.labtimesheet.feature.calendar.model.dto.HolidayApiCandidate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * JPA model for the locally authoritative global calendar decision.
 */
@Entity
@Table(name = "global_calendar_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GlobalCalendarEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "calendar_date", nullable = false)
    private LocalDate calendarDate;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String source;

    @Column(name = "source_uuid")
    private String sourceUuid;

    @Column(name = "actual_date")
    private LocalDate actualDate;

    @Column(name = "observed_date")
    private LocalDate observedDate;

    @Column(name = "public_holiday")
    private Boolean publicHoliday;

    @Column(name = "is_day_off", nullable = false)
    private boolean dayOff;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private long createdByUserId;

    @Column(name = "updated_by_user_id", nullable = false)
    private long updatedByUserId;

    @Column(name = "imported_at")
    private Instant importedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    /**
     * Creates a custom calendar event attributed to the Admin actor.
     *
     * @param date local business date
     * @param name display name validated by the application service
     * @param dayOff whether the event exempts attendance and date validation
     * @param actorUserId Admin who created the local decision
     */
    public GlobalCalendarEventEntity(LocalDate date, String name, boolean dayOff, long actorUserId) {
        this(date, name, dayOff, actorUserId, Instant.now());
    }

    /**
     * Creates a custom event with an injected server timestamp.
     *
     * @param date local business date
     * @param name display name
     * @param dayOff local day-off decision
     * @param actorUserId Admin actor
     * @param now server creation timestamp
     */
    public GlobalCalendarEventEntity(LocalDate date, String name, boolean dayOff, long actorUserId, Instant now) {
        this.calendarDate = date;
        this.name = name;
        this.source = "CUSTOM";
        this.dayOff = dayOff;
        this.createdByUserId = actorUserId;
        this.updatedByUserId = actorUserId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Creates a locally authoritative imported event while retaining all upstream provenance.
     *
     * @param candidate validated Platform-owned upstream candidate
     * @param dayOff explicit local Admin decision
     * @param actorUserId Admin performing import
     * @param importedAt Platform preview retrieval instant retained as local import provenance
     * @param now local persistence timestamp
     */
    public GlobalCalendarEventEntity(
            HolidayApiCandidate candidate,
            boolean dayOff,
            long actorUserId,
            Instant importedAt,
            Instant now) {
        this.calendarDate = candidate.observedDate();
        this.name = candidate.name();
        this.source = "HOLIDAY_API";
        this.sourceUuid = candidate.uuid();
        this.actualDate = candidate.actualDate();
        this.observedDate = candidate.observedDate();
        this.publicHoliday = candidate.publicHoliday();
        this.dayOff = dayOff;
        this.importedAt = importedAt;
        this.createdByUserId = actorUserId;
        this.updatedByUserId = actorUserId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Applies an authorized future-event edit while preserving creator attribution.
     *
     * @param date replacement local date
     * @param name replacement display name
     * @param dayOff replacement authoritative day-off choice
     * @param actorUserId Admin performing the update
     */
    public void update(LocalDate date, String name, boolean dayOff, long actorUserId) {
        update(date, name, dayOff, actorUserId, Instant.now());
    }

    /**
     * Applies an authorized future-event edit with a server-provided timestamp.
     *
     * @param date replacement local business date
     * @param name replacement display name
     * @param dayOff replacement day-off decision
     * @param actorUserId Admin actor
     * @param now server update timestamp
     */
    public void update(LocalDate date, String name, boolean dayOff, long actorUserId, Instant now) {
        this.calendarDate = date;
        this.name = name;
        this.dayOff = dayOff;
        this.updatedByUserId = actorUserId;
        this.updatedAt = now;
    }

    /**
     * Returns a persistence-free event including the optimistic version needed for edits.
     *
     * @return calendar event DTO
     */
    public GlobalCalendarEvent toDomain() {
        return new GlobalCalendarEvent(id, calendarDate, name, dayOff, version);
    }

    /**
     * Returns the date whose mutability is governed by the current policy-local business date.
     *
     * @return event calendar date
     */
    public LocalDate calendarDate() {
        return calendarDate;
    }

    /**
     * Returns the optimistic version expected by a subsequent update.
     *
     * @return current version
     */
    public long version() {
        return version;
    }

    /**
     * Converts retained provenance and local decision metadata for Admin Calendar History.
     *
     * @return non-secret history projection
     */
    public CalendarHistoryItem toHistory() {
        return new CalendarHistoryItem(
                id,
                calendarDate,
                name,
                source,
                sourceUuid,
                actualDate,
                observedDate,
                publicHoliday,
                dayOff,
                importedAt,
                createdByUserId,
                updatedByUserId,
                createdAt,
                updatedAt,
                version);
    }
}
