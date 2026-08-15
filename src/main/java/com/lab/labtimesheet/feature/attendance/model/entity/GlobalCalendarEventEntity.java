package com.lab.labtimesheet.feature.attendance.model.entity;

import com.lab.labtimesheet.feature.attendance.model.dto.GlobalCalendarEvent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;

/**
 * JPA model for the locally authoritative global calendar decision.
 */
@Entity
@Table(name = "global_calendar_events")
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

    @Column(name = "is_day_off", nullable = false)
    private boolean dayOff;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private long createdByUserId;

    @Column(name = "updated_by_user_id", nullable = false)
    private long updatedByUserId;

    @Version
    private long version;

    /**
     * Required by JPA.
     */
    protected GlobalCalendarEventEntity() {}

    /**
     * Creates a custom calendar event attributed to the Admin actor.
     *
     * @param date local business date
     * @param name display name validated by the application service
     * @param dayOff whether the event exempts attendance and date validation
     * @param actorUserId Admin who created the local decision
     */
    public GlobalCalendarEventEntity(LocalDate date, String name, boolean dayOff, long actorUserId) {
        this.calendarDate = date;
        this.name = name;
        this.source = "CUSTOM";
        this.dayOff = dayOff;
        this.createdByUserId = actorUserId;
        this.updatedByUserId = actorUserId;
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
        this.calendarDate = date;
        this.name = name;
        this.dayOff = dayOff;
        this.updatedByUserId = actorUserId;
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
}
