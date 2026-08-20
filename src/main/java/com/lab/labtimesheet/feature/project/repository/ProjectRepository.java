package com.lab.labtimesheet.feature.project.repository;

import com.lab.labtimesheet.feature.project.model.entity.ProjectEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persists the Project aggregate, including its membership and leadership intervals.
 *
 * <p>Consumers outside the Project feature use Project services and DTOs rather than this
 * repository or its JPA entities.
 */
public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {

    /**
     * Loads one Project under a pessimistic write lock for mutation-time authorization and
     * invariant checks. The caller's transaction retains the lock through commit or rollback.
     *
     * @param id Project identifier
     * @return the locked aggregate, or empty when the identifier does not exist
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select project from ProjectEntity project where project.id = :id")
    Optional<ProjectEntity> findLockedById(@Param("id") long id);

    /**
     * Lists all Projects for Admin read-only inspection, most recently updated first.
     *
     * @return ordered Projects
     */
    List<ProjectEntity> findAllByOrderByUpdatedAtDescIdDesc();

    /**
     * Lists Projects owned by one Mentor, most recently updated first.
     *
     * @param mentorUserId owning Mentor user identifier
     * @return ordered owned Projects
     */
    List<ProjectEntity> findByMentorUserIdOrderByUpdatedAtDescIdDesc(long mentorUserId);

    /**
     * Lists Projects visible to an Intern: current memberships in open Projects and historical
     * memberships only after completion.
     *
     * @param internUserId Intern user identifier
     * @return ordered visible Projects without duplicate rows
     */
    @Query("""
            select distinct project from ProjectEntity project
            join project.memberships membership
            where membership.internUserId = :internUserId
            and (membership.leftAt is null or project.status = com.lab.labtimesheet.feature.project.model.ProjectStatus.COMPLETED)
            order by project.updatedAt desc, project.id desc
            """)
    List<ProjectEntity> findVisibleToIntern(@Param("internUserId") long internUserId);

    /**
     * Lists every membership interval identifier of one Intern across all Projects, current and
     * historical. Consumers use this to aggregate the Intern's own cross-Project daily totals
     * without mapping the membership table again.
     *
     * @param internUserId Intern user identifier
     * @return the Intern's membership interval identifiers
     */
    @Query("""
            select membership.id from ProjectEntity project
            join project.memberships membership
            where membership.internUserId = :internUserId
            """)
    List<Long> findMembershipIdsByInternUserId(@Param("internUserId") long internUserId);
}
