package com.lab.labtimesheet.feature.attendance.repository;

import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestDecisionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data persistence boundary for append-only leave decision history. */
public interface LeaveRequestDecisionRepository extends JpaRepository<LeaveRequestDecisionEntity, Long> {

    /**
     * Loads all decision rows for a leave request in insertion order.
     *
     * @param leaveRequestId leave request identifier
     * @return decision history rows
     */
    List<LeaveRequestDecisionEntity> findByLeaveRequestIdOrderByIdAsc(long leaveRequestId);
}
