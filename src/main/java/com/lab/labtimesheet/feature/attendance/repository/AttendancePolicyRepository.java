package com.lab.labtimesheet.feature.attendance.repository;

import com.lab.labtimesheet.feature.attendance.model.entity.AttendancePolicyEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to the effective-dated policy timeline owned by Attendance.
 */
public interface AttendancePolicyRepository extends JpaRepository<AttendancePolicyEntity, Long> {

    /**
     * Loads the complete timeline in effective-date order for deterministic local-date resolution.
     *
     * @return ascending policy versions, including the 1970 seed
     */
    List<AttendancePolicyEntity> findAllByOrderByEffectiveFromAsc();
}
