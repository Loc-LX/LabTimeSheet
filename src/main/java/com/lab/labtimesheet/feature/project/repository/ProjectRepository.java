package com.lab.labtimesheet.feature.project.repository;

import com.lab.labtimesheet.feature.project.model.dto.ProjectMembershipIntervalView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMutationRoute;
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
     * Projects retained membership intervals for one Intern without hydrating a filtered Project
     * aggregate. The scalar projection prevents a read in an ambient transaction from leaving a
     * partially initialized membership collection for a later authorization or Task-context read.
     *
     * <p>The caller must authorize the requested Intern before invoking this query.</p>
     *
     * @param internUserId authenticated Intern account identifier
     * @return immutable interval projections ordered by Project and membership identifier
     */
    @Query("""
            select new com.lab.labtimesheet.feature.project.model.dto.ProjectMembershipIntervalView(
                    project.id, membership.id, membership.joinedAt, membership.leftAt)
            from ProjectEntity project
            join project.memberships membership
            where membership.internUserId = :internUserId
            order by project.id asc, membership.id asc
            """)
    List<ProjectMembershipIntervalView> findMembershipIntervalsByInternUserId(
            @Param("internUserId") long internUserId);

    /**
     * Reads only scalar owner and current-Leader routing facts before a mutation obtains Account
     * lifecycle locks. The left join keeps completed history routable without hydrating an
     * incomplete Project aggregate.
     *
     * @param projectId Project identifier
     * @return scalar route, or empty when the Project does not exist
     */
    @Query("""
            select new com.lab.labtimesheet.feature.project.model.dto.ProjectMutationRoute(
                    project.id, project.mentorUserId, term.membership.internUserId)
            from ProjectEntity project
            left join project.leadershipTerms term on term.endedAt is null
            where project.id = :projectId
            """)
    Optional<ProjectMutationRoute> findMutationRouteById(@Param("projectId") long projectId);

    /**
     * Lists current-member Intern identifiers without hydrating the Project aggregate. The
     * mutation owner compares this immutable set after the Project write lock is acquired.
     *
     * @param projectId Project identifier
     * @return current Intern account identifiers in stable order
     */
    @Query("""
            select membership.internUserId
            from ProjectMembershipEntity membership
            where membership.project.id = :projectId
              and membership.leftAt is null
            order by membership.internUserId asc
            """)
    List<Long> findCurrentInternUserIdsByProjectId(@Param("projectId") long projectId);

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
}
