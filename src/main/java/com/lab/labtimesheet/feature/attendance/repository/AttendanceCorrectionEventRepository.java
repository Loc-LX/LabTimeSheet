package com.lab.labtimesheet.feature.attendance.repository;

import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEventEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to the append-only correction decision history.
 */
public interface AttendanceCorrectionEventRepository extends JpaRepository<AttendanceCorrectionEventEntity, Long> {

    /**
     * Loads a correction's immutable transitions in committed order.
     *
     * @param correctionId owning correction identifier
     * @return ordered transition events
     */
    List<AttendanceCorrectionEventEntity> findByCorrectionIdOrderByOccurredAtAscIdAsc(long correctionId);
}