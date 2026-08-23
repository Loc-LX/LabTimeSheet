package com.lab.labtimesheet.feature.attendance.repository;

import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEventEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data persistence boundary for append-only correction transition history. */
public interface AttendanceCorrectionEventRepository extends JpaRepository<AttendanceCorrectionEventEntity, Long> {

    /**
     * Loads events in deterministic occurrence order.
     *
     * @param correctionId correction identifier
     * @return immutable transition rows in occurrence order
     */
    List<AttendanceCorrectionEventEntity> findByCorrectionIdOrderByOccurredAtAscIdAsc(long correctionId);
}
