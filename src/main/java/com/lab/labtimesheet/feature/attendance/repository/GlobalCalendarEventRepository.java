package com.lab.labtimesheet.feature.attendance.repository;

import com.lab.labtimesheet.feature.attendance.model.entity.GlobalCalendarEventEntity;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to locally authoritative global calendar events.
 */
public interface GlobalCalendarEventRepository extends JpaRepository<GlobalCalendarEventEntity, Long> {

    /**
     * Reports whether any stored event makes the exact local date a global day off.
     *
     * @param date local business date
     * @return {@code true} when at least one authoritative day-off decision exists
     */
    boolean existsByCalendarDateAndDayOffTrue(LocalDate date);

    /**
     * Reports whether a HolidayAPI source UUID is already stored locally.
     *
     * @param source event origin ({@code CUSTOM} or {@code HOLIDAY_API})
     * @param sourceUuid source-side identifier
     * @return {@code true} when an identical import already exists
     */
    boolean existsBySourceAndSourceUuid(String source, String sourceUuid);

    /**
     * Lists events across an inclusive local-date range in deterministic order.
     *
     * @param from inclusive first date
     * @param to inclusive last date
     * @return events ordered by date then identifier
     */
    List<GlobalCalendarEventEntity> findByCalendarDateBetweenOrderByCalendarDateAscIdAsc(
            LocalDate from, LocalDate to);
}
