package com.lab.labtimesheet.feature.attendance.repository;

import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to the full-day leave request lifecycle owned by Attendance.
 */
public interface LeaveRequestRepository extends JpaRepository<LeaveRequestEntity, Long> {

    /**
     * Loads an Intern's requests newest-first for the prior-requests table.
     *
     * @param internUserId owning Intern account identifier
     * @return requests ordered by newest submission
     */
    List<LeaveRequestEntity> findByInternUserIdOrderByIdDesc(long internUserId);
}