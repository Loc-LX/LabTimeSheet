package com.lab.labtimesheet.feature.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.task.exception.TaskNotFoundException;
import com.lab.labtimesheet.feature.task.exception.TaskValidationException;
import com.lab.labtimesheet.feature.task.model.dto.CreateTaskCommand;
import com.lab.labtimesheet.feature.task.model.dto.LogWorkCommand;
import com.lab.labtimesheet.feature.task.model.dto.LogWorkCorrection;
import com.lab.labtimesheet.feature.task.model.dto.TaskView;
import com.lab.labtimesheet.feature.task.model.dto.WorkLogView;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TaskWorkLogIntegrationTest {

    private static final LocalDate PROJECT_START = LocalDate.of(2026, 8, 1);
    private static final LocalDate PROJECT_END = LocalDate.of(2026, 8, 31);
    private static final LocalDate WORK_DATE = LocalDate.of(2026, 8, 14);

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TaskService taskService;

    private long projectId;
    private long leaderMembershipId;
    private long memberMembershipId;
    private long memberUserId;

    @BeforeEach
    void setUpProject() {
        long mentorId = insertUser("mentor@example.test", "MENTOR");
        long leaderId = insertIntern("leader@example.test");
        long memberId = insertIntern("member@example.test");
        memberUserId = memberId;
        projectId = insertProject(mentorId, "ACTIVE");
        leaderMembershipId = insertMembership(projectId, leaderId, mentorId);
        memberMembershipId = insertMembership(projectId, memberId, mentorId);
        jdbc.sql("""
                        insert into project_leadership_terms
                            (project_id, membership_id, appointed_by_mentor_user_id)
                        values (:projectId, :membershipId, :mentorId)
                        """)
                .param("projectId", projectId)
                .param("membershipId", leaderMembershipId)
                .param("mentorId", mentorId)
                .update();
    }

    @Test
    void currentAssigneeLogsWorkWithinProjectAndMembershipDates() {
        TaskView task = createMemberTask("Run experiment");

        WorkLogView log = taskService.logWork(
                "member@example.test",
                projectId,
                task.id(),
                new LogWorkCommand(WORK_DATE, 90, "  Prepared samples  "));

        assertThat(log.membershipId()).isEqualTo(memberMembershipId);
        assertThat(log.workDate()).isEqualTo(WORK_DATE);
        assertThat(log.minutes()).isEqualTo(90);
        assertThat(log.note()).isEqualTo("Prepared samples");
        assertThat(workLogCount()).isEqualTo(1);
    }

    @Test
    void onlyCurrentAssigneeLogsWorkAndFormerMemberIsRejected() {
        TaskView task = createMemberTask("Private effort");

        assertThatThrownBy(() -> taskService.logWork(
                        "leader@example.test", projectId, task.id(),
                        new LogWorkCommand(WORK_DATE, 30, null)))
                .isInstanceOf(TaskNotFoundException.class);

        closeMembership(memberMembershipId);
        assertThatThrownBy(() -> taskService.logWork(
                        "member@example.test", projectId, task.id(),
                        new LogWorkCommand(WORK_DATE, 30, null)))
                .isInstanceOf(TaskNotFoundException.class);
        assertThat(workLogCount()).isZero();
    }

    @Test
    void workDateRejectedOutsideProjectDatesBeforeMembershipOrInFuture() {
        TaskView task = createMemberTask("Boundary effort");

        assertThatThrownBy(() -> taskService.logWork(
                        "member@example.test", projectId, task.id(),
                        new LogWorkCommand(LocalDate.of(2026, 7, 31), 30, null)))
                .isInstanceOf(TaskValidationException.class);
        assertThatThrownBy(() -> taskService.logWork(
                        "member@example.test", projectId, task.id(),
                        new LogWorkCommand(LocalDate.of(2026, 9, 1), 30, null)))
                .isInstanceOf(TaskValidationException.class);
        assertThatThrownBy(() -> taskService.logWork(
                        "member@example.test", projectId, task.id(),
                        new LogWorkCommand(LocalDate.of(2026, 8, 15), 30, null)))
                .isInstanceOf(TaskValidationException.class);
        assertThatThrownBy(() -> taskService.logWork(
                        "member@example.test", projectId, task.id(),
                        new LogWorkCommand(null, 30, null)))
                .isInstanceOf(TaskValidationException.class);
        assertThat(workLogCount()).isZero();
    }

    @Test
    void rejectsInvalidMinutesAndBlankNoteWithoutWriting() {
        TaskView task = createMemberTask("Invalid effort");

        assertThatThrownBy(() -> taskService.logWork(
                        "member@example.test", projectId, task.id(),
                        new LogWorkCommand(WORK_DATE, 0, null)))
                .isInstanceOf(TaskValidationException.class);
        assertThatThrownBy(() -> taskService.logWork(
                        "member@example.test", projectId, task.id(),
                        new LogWorkCommand(WORK_DATE, 1441, null)))
                .isInstanceOf(TaskValidationException.class);
        assertThatThrownBy(() -> taskService.logWork(
                        "member@example.test", projectId, task.id(),
                        new LogWorkCommand(WORK_DATE, 30, "   ")))
                .isInstanceOf(TaskValidationException.class);
        assertThat(workLogCount()).isZero();
    }

    @Test
    void globalDayOffDoesNotBlockWorkLogsAndCreatesNoAttendance() {
        long mentorId = userId("mentor@example.test");
        jdbc.sql("""
                        insert into global_calendar_events
                            (calendar_date, name, source, is_day_off, created_by_user_id, updated_by_user_id)
                        values (:date, 'National day', 'CUSTOM', true, :userId, :userId)
                        """)
                .param("date", WORK_DATE)
                .param("userId", mentorId)
                .update();
        TaskView task = createMemberTask("Day off effort");

        WorkLogView log = taskService.logWork(
                "member@example.test", projectId, task.id(),
                new LogWorkCommand(WORK_DATE, 60, null));

        assertThat(log.minutes()).isEqualTo(60);
        assertThat(attendanceCount(memberUserId, WORK_DATE)).isZero();
    }

    @Test
    void authorCorrectsOwnLogAfterTaskReassignment() {
        TaskView task = createMemberTask("Historical effort");
        WorkLogView original = taskService.logWork(
                "member@example.test", projectId, task.id(),
                new LogWorkCommand(WORK_DATE, 45, null));
        long mentorId = userId("mentor@example.test");
        reassign(task, leaderMembershipId, mentorId);

        WorkLogView corrected = taskService.correctWorkLog(
                "member@example.test", projectId, task.id(), original.id(),
                new LogWorkCorrection(120, "Actual total"));

        assertThat(corrected.minutes()).isEqualTo(120);
        assertThat(corrected.note()).isEqualTo("Actual total");
        assertThat(corrected.membershipId()).isEqualTo(memberMembershipId);
    }

    @Test
    void nonAuthorCannotCorrectAnotherMembersLog() {
        TaskView task = createMemberTask("Shared effort");
        WorkLogView original = taskService.logWork(
                "member@example.test", projectId, task.id(),
                new LogWorkCommand(WORK_DATE, 45, null));
        TaskView leaderTask = taskService.create(
                "leader@example.test",
                new CreateTaskCommand(projectId, leaderMembershipId, "Leader effort", null, null));
        taskService.logWork(
                "leader@example.test", projectId, leaderTask.id(),
                new LogWorkCommand(WORK_DATE, 30, null));

        assertThatThrownBy(() -> taskService.correctWorkLog(
                        "leader@example.test", projectId, task.id(), original.id(),
                        new LogWorkCorrection(100, null)))
                .isInstanceOf(TaskNotFoundException.class);
        assertThat(workLogMinutes(original.id())).isEqualTo(45);
    }

    @Test
    void dailyTotalCountsAllProjectsAndRejectsOverAllocation() {
        long mentorId = userId("mentor@example.test");
        long leaderId = userId("leader@example.test");
        long secondProjectId = insertProject(mentorId, "ACTIVE");
        long secondMembershipId = insertMembership(secondProjectId, memberUserId, mentorId);
        long secondLeaderMembershipId = insertMembership(secondProjectId, leaderId, mentorId);
        jdbc.sql("""
                        insert into project_leadership_terms
                            (project_id, membership_id, appointed_by_mentor_user_id)
                        values (:projectId, :membershipId, :mentorId)
                        """)
                .param("projectId", secondProjectId)
                .param("membershipId", secondLeaderMembershipId)
                .param("mentorId", mentorId)
                .update();
        TaskView projectOneTask = createMemberTask("Project one effort");
        TaskView projectTwoTask = taskService.create(
                "member@example.test",
                new CreateTaskCommand(secondProjectId, secondMembershipId, "Project two effort", null, null));

        taskService.logWork(
                "member@example.test", projectId, projectOneTask.id(),
                new LogWorkCommand(WORK_DATE, 1400, null));

        assertThatThrownBy(() -> taskService.logWork(
                        "member@example.test", secondProjectId, projectTwoTask.id(),
                        new LogWorkCommand(WORK_DATE, 41, null)))
                .isInstanceOf(TaskValidationException.class)
                .hasMessageContaining("1440");

        WorkLogView remaining = taskService.logWork(
                "member@example.test", secondProjectId, projectTwoTask.id(),
                new LogWorkCommand(WORK_DATE, 40, null));

        assertThat(remaining.minutes()).isEqualTo(40);
        assertThat(totalMinutesFor(memberUserId, WORK_DATE)).isEqualTo(1440);
    }

    @Test
    void correctionCannotRaiseCombinedDailyTotalAboveFourteenForty() {
        TaskView task = createMemberTask("Correction effort");
        WorkLogView original = taskService.logWork(
                "member@example.test", projectId, task.id(),
                new LogWorkCommand(WORK_DATE, 700, null));
        taskService.logWork(
                "member@example.test", projectId, task.id(),
                new LogWorkCommand(WORK_DATE, 700, null));

        assertThatThrownBy(() -> taskService.correctWorkLog(
                        "member@example.test", projectId, task.id(), original.id(),
                        new LogWorkCorrection(750, null)))
                .isInstanceOf(TaskValidationException.class)
                .hasMessageContaining("1440");

        WorkLogView corrected = taskService.correctWorkLog(
                "member@example.test", projectId, task.id(), original.id(),
                new LogWorkCorrection(740, null));

        assertThat(corrected.minutes()).isEqualTo(740);
        assertThat(totalMinutesFor(memberUserId, WORK_DATE)).isEqualTo(1440);
    }

    private TaskView createMemberTask(String title) {
        return taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, title, null, null));
    }

    private void reassign(TaskView task, long newAssigneeMembershipId, long mentorId) {
        jdbc.sql("""
                        update tasks
                        set assignee_membership_id = :assignee,
                            assigned_by_membership_id = :assigner,
                            assigned_at = current_timestamp
                        where id = :id
                        """)
                .param("assignee", newAssigneeMembershipId)
                .param("assigner", leaderMembershipId)
                .param("id", task.id())
                .update();
    }

    private long insertUser(String email, String role) {
        return jdbc.sql("""
                        insert into app_users
                            (email, display_name, password_hash, global_role, account_status, activated_at)
                        values (:email, :email, 'hash', :role, 'ACTIVE', current_timestamp)
                        returning id
                        """)
                .param("email", email)
                .param("role", role)
                .query(Long.class)
                .single();
    }

    private long insertIntern(String email) {
        long userId = insertUser(email, "INTERN");
        jdbc.sql("""
                        insert into intern_profiles
                            (user_id, student_code, internship_start_date, internship_end_date,
                             internship_status, activated_at)
                        values (:userId, :studentCode, date '2026-01-01', date '2026-12-31',
                                'ACTIVE', current_timestamp)
                        """)
                .param("userId", userId)
                .param("studentCode", "S" + userId)
                .update();
        return userId;
    }

    private long insertProject(long mentorId, String status) {
        return jdbc.sql("""
                        insert into projects
                            (mentor_user_id, name, status, start_date, end_date, activated_at)
                        values (:mentorId, 'Project', :status, :startDate, :endDate,
                                case when :status = 'ACTIVE' then current_timestamp else null end)
                        returning id
                        """)
                .param("mentorId", mentorId)
                .param("status", status)
                .param("startDate", PROJECT_START)
                .param("endDate", PROJECT_END)
                .query(Long.class)
                .single();
    }

    private long insertMembership(long targetProjectId, long internId, long mentorId) {
        return jdbc.sql("""
                        insert into project_memberships (project_id, intern_user_id, added_by_user_id, joined_at)
                        values (:projectId, :internId, :mentorId, date '2026-08-01')
                        returning id
                        """)
                .param("projectId", targetProjectId)
                .param("internId", internId)
                .param("mentorId", mentorId)
                .query(Long.class)
                .single();
    }

    private void closeMembership(long membershipId) {
        jdbc.sql("""
                        update project_memberships
                        set left_at = joined_at + interval '1 day', removed_by_mentor_user_id = :mentorId
                        where id = :id
                        """)
                .param("mentorId", userId("mentor@example.test"))
                .param("id", membershipId)
                .update();
        entityManager.clear();
    }

    private long userId(String email) {
        return jdbc.sql("select id from app_users where email = :email")
                .param("email", email)
                .query(Long.class)
                .single();
    }

    private long workLogCount() {
        return jdbc.sql("select count(*) from task_work_logs").query(Long.class).single();
    }

    private long workLogMinutes(long logId) {
        return jdbc.sql("select minutes from task_work_logs where id = :id")
                .param("id", logId)
                .query(Long.class)
                .single();
    }

    private long attendanceCount(long internUserId, LocalDate workDate) {
        return jdbc.sql("""
                        select count(*)
                        from attendance_records
                        where intern_user_id = :internUserId
                          and work_date = :workDate
                        """)
                .param("internUserId", internUserId)
                .param("workDate", workDate)
                .query(Long.class)
                .single();
    }

    private long totalMinutesFor(long internUserId, LocalDate workDate) {
        return jdbc.sql("""
                        select coalesce(sum(log.minutes), 0)
                        from task_work_logs log
                        join project_memberships membership
                          on membership.id = log.membership_id
                        where membership.intern_user_id = :internUserId
                          and log.work_date = :workDate
                        """)
                .param("internUserId", internUserId)
                .param("workDate", workDate)
                .query(Long.class)
                .single();
    }
}
