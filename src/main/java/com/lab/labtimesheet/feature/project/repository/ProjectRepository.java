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
 * Lưu aggregate Project, bao gồm các khoảng thời gian thành viên và nhiệm kỳ Leader.
 *
 * <p>Các feature bên ngoài Project sử dụng service và DTO của Project thay vì truy cập repository
 * hoặc entity JPA này.
 */
public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {

    /**
     * Tải một Project với khóa ghi bi quan để phân quyền và kiểm tra bất biến ngay lúc thay
     * đổi. Transaction của bên gọi giữ lock đến khi commit hoặc rollback.
     *
     * @param id mã Project
     * @return aggregate đã khóa, hoặc rỗng khi mã không tồn tại
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select project from ProjectEntity project where project.id = :id")
    Optional<ProjectEntity> findLockedById(@Param("id") long id);

    /**
     * Liệt kê mọi Project để Admin kiểm tra chỉ đọc, theo thứ tự cập nhật mới nhất trước.
     *
     * @return danh sách Project đã sắp xếp
     */
    List<ProjectEntity> findAllByOrderByUpdatedAtDescIdDesc();

    /**
     * Liệt kê các Project do một Mentor sở hữu, theo thứ tự cập nhật mới nhất trước.
     *
     * @param mentorUserId mã người dùng Mentor sở hữu
     * @return các Project thuộc sở hữu đã sắp xếp
     */
    List<ProjectEntity> findByMentorUserIdOrderByUpdatedAtDescIdDesc(long mentorUserId);

    /**
     * Liệt kê Project mà Intern được xem: lượt tham gia hiện tại trong Project đang mở và lượt
     * tham gia lịch sử chỉ được xem sau khi Project hoàn tất.
     *
     * @param internUserId mã người dùng Intern
     * @return các Project hiển thị đã sắp xếp, không có dòng trùng
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
