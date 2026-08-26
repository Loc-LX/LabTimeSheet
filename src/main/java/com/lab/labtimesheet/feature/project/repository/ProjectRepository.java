package com.lab.labtimesheet.feature.project.repository;

import com.lab.labtimesheet.feature.project.model.dto.ProjectMembershipIntervalView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMutationRoute;
import com.lab.labtimesheet.feature.project.model.entity.ProjectEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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
// Repository là cổng ProjectService/ProjectQueryService dùng để đọc và ghi bảng projects.
// Spring Data JPA tự triển khai interface này; các @Query bên dưới là JPQL query trên Entity, không phải SQL thô.
// JpaRepository đã cung cấp save/saveAndFlush/findById; vì vậy flow create không cần tự viết INSERT SQL. Khi
// Service gọi saveAndFlush(root), Spring Data chuyển root thành managed entity, Hibernate sinh SQL theo mapping
// @Table/@Column và transaction quyết định commit/rollback.
public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {

    /**
     * Loads one Project under a pessimistic write lock for mutation-time authorization and
     * invariant checks. The caller's transaction retains the lock through commit or rollback.
     *
     * @param id Project identifier
     * @return the locked aggregate, or empty when the identifier does not exist
     */
    // [Khóa Project trước khi thay đổi]
    // PESSIMISTIC_WRITE khóa dòng Project trong transaction để hai request không cùng sửa membership/Leader.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select project from ProjectEntity project where project.id = :id")
    Optional<ProjectEntity> findLockedById(@Param("id") long id);

    /**
     * Lists all Projects for Admin read-only inspection, most recently updated first.
     *
     * @param pageable page and maximum result size
     * @return ordered Project slice within the requested page
     */
    // [Danh sách Project cho Admin]
    // Spring Data tạo query từ tên method; chỉ lấy một Slice để UI không tải toàn bộ database. Query này được gọi
    // từ GET /projects khi actor là ADMIN, sau đó QueryService map Entity thành ProjectSummary trước khi render.
    Slice<ProjectEntity> findAllByOrderByUpdatedAtDescIdDesc(Pageable pageable);

    /**
     * Lists Projects owned by one Mentor, most recently updated first.
     *
     * @param mentorUserId owning Mentor user identifier
     * @param pageable page and maximum result size
     * @return ordered owned Project slice within the requested page
     */
    // [Danh sách Project của Mentor]
    // Query theo mentorUserId để Mentor chỉ nhận Project do chính họ sở hữu; filter nằm ngay ở database chứ không
    // tải toàn bảng rồi lọc bằng Java. Với actorUserId=10 và page size logic là 50, ý nghĩa SQL tương đương:
    // SELECT *
    // FROM projects
    // WHERE mentor_user_id = 10
    // ORDER BY updated_at DESC, id DESC
    // LIMIT 50;
    // Đây là dạng SQL dễ hình dung; Hibernate có thể chọn cột cụ thể và Slice có thể đọc thêm một row (ví dụ LIMIT
    // 51) để tính hasNext, nhưng điều kiện owner/order vẫn giữ nguyên.
    Slice<ProjectEntity> findByMentorUserIdOrderByUpdatedAtDescIdDesc(
            long mentorUserId, Pageable pageable);

    /**
     * Lists the complete Project catalogue for an Admin report scope.
     *
     * <p>This intentionally has no {@link Pageable}: the Daily Project Work Report contract is
     * an all-authorized scope, whereas the navigation list above remains bounded for interactive
     * catalogue rendering.</p>
     *
     * @return all Projects in the same deterministic order as the bounded catalogue
     */
    List<ProjectEntity> findAllByOrderByUpdatedAtDescIdDesc();

    /**
     * Lists every Project owned by one Mentor for an all-Projects report scope.
     *
     * @param mentorUserId owning Mentor account identifier
     * @return all owned Projects in deterministic update order
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
    // [Lịch sử membership của một Intern]
    // Chỉ trả DTO gồm ID và mốc thời gian, không tải cả Project aggregate; QueryService dùng để kiểm tra quyền lịch sử.
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
    // [Route cho mutation Project]
    // Lấy ID Mentor và Leader hiện tại trước khi lock để ProjectService khóa Account theo thứ tự an toàn.
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
    // [Danh sách thành viên hiện tại]
    // Chỉ lấy user ID có leftAt null; Service dùng danh sách này để kiểm tra eligibility trước mutation.
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
     * @param pageable page and maximum result size
     * @return ordered visible Project slice without duplicate rows within the requested page
     */
    // [Project Intern được xem]
    // Intern chỉ xem Project đang tham gia, hoặc Project đã hoàn thành mà họ từng là thành viên.
    @Query("""
            select distinct project from ProjectEntity project
            join project.memberships membership
            where membership.internUserId = :internUserId
            and (membership.leftAt is null or project.status = com.lab.labtimesheet.feature.project.model.ProjectStatus.COMPLETED)
            order by project.updatedAt desc, project.id desc
            """)
    Slice<ProjectEntity> findVisibleToIntern(
            @Param("internUserId") long internUserId, Pageable pageable);

    /**
     * Counts every active Project visible to an Admin without hydrating a list page.
     *
     * @return complete active-Project total
     */
    // [Đếm Project active toàn hệ thống]
    // Dùng cho dashboard Admin nên chỉ trả một số đếm, không tải danh sách Project.
    @Query("""
            select count(project)
            from ProjectEntity project
            where project.status = com.lab.labtimesheet.feature.project.model.ProjectStatus.ACTIVE
            """)
    long countActiveProjects();

    /**
     * Counts active Projects owned by one Mentor without hydrating a list page.
     *
     * @param mentorUserId owning Mentor account identifier
     * @return complete active-Project total for the Mentor
     */
    // [Đếm Project active của Mentor]
    // Dùng cho dashboard Mentor, giới hạn theo người sở hữu Project.
    @Query("""
            select count(project)
            from ProjectEntity project
            where project.mentorUserId = :mentorUserId
              and project.status = com.lab.labtimesheet.feature.project.model.ProjectStatus.ACTIVE
            """)
    long countActiveProjectsByMentor(@Param("mentorUserId") long mentorUserId);

    /**
     * Lists the complete distinct current-member ID set for active Projects owned by one Mentor.
     * Account and internship eligibility is evaluated by the Account service after this scalar
     * Project-owned projection; no Account persistence crosses the feature boundary.
     *
     * @param mentorUserId owning Mentor account identifier
     * @return distinct current-member account identifiers in stable order
     */
    // [Lấy Intern đang thuộc các Project active của Mentor]
    // distinct loại trùng khi một Intern cùng tham gia nhiều Project; AccountService kiểm tra trạng thái tiếp theo.
    @Query("""
            select distinct membership.internUserId
            from ProjectEntity project
            join project.memberships membership
            where project.mentorUserId = :mentorUserId
              and project.status = com.lab.labtimesheet.feature.project.model.ProjectStatus.ACTIVE
              and membership.leftAt is null
            order by membership.internUserId asc
            """)
    List<Long> findDistinctCurrentMemberUserIdsByMentor(@Param("mentorUserId") long mentorUserId);

    /**
     * Counts active Projects with a current membership for one Intern without hydrating the
     * display page. Distinct Project IDs prevent multiple retained intervals from inflating the
     * total.
     *
     * @param internUserId Intern account identifier
     * @return complete active-Project total for the Intern
     */
    // [Đếm Project active của Intern]
    // distinct project.id tránh đếm lặp nếu dữ liệu lịch sử có nhiều membership của cùng Intern.
    @Query("""
            select count(distinct project.id)
            from ProjectEntity project
            join project.memberships membership
            where membership.internUserId = :internUserId
              and membership.leftAt is null
              and project.status = com.lab.labtimesheet.feature.project.model.ProjectStatus.ACTIVE
            """)
    long countActiveProjectsByIntern(@Param("internUserId") long internUserId);
}
