package com.lab.labtimesheet.feature.attendance.model.dto;

import java.time.LocalDate;

/**
 * Persistence-free global calendar event returned to controllers and feature consumers.
 *
 * @param id stable event identifier
 * @param date local business date of the event
 * @param name operator-provided display name
 * @param dayOff whether this event makes the date globally exempt
 * @param version optimistic version required by update requests
 */
public record GlobalCalendarEvent(long id, LocalDate date, String name, boolean dayOff, long version) {}
