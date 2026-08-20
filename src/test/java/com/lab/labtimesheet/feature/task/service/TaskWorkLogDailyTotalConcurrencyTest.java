package com.lab.labtimesheet.feature.task.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.task.exception.TaskValidationException;
import com.lab.labtimesheet.feature.task.model.dto.CreateTaskCommand;
import com.lab.labtimesheet.feature.task.model.dto.LogWorkCommand;
import com.lab.labtimesheet.feature.task.model.dto.TaskView;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class TaskWorkLogDailyTotalConcurrencyTest {

    private static final LocalDate PROJECT_START = LocalDate.of(2026, 8, 1);
    private static final LocalDate PROJECT_END = LocalDate.of(2026, 8, 31);
    private static final LocalDate WORK_DATE = LocalDate.of(2026, 8, 14);

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TaskService taskService;

    private long memberUserId;
    private long firstProjectId;
    private long secondProjectId;

    @BeforeEach
    void setUp() {
        long mentorId = insertUser("mentor@example.test", "MENTOR");
        memberUserId = insertIntern("member@example.test");
        long leaderId = insertIntern("leader@example.test");
        firstProjectId = insertProject(mentorId, "First project", "ACTIVE");
        secondProjectId = insertProject(mentorId, "Second project", "ACTIVE");
        long firstMembershipId = insertMembership(firstProjectId, memberUserId, mentorId);
        long secondMembershipId = insertMembership(secondProjectId, memberUserId, mentorId);
        long firstLeaderMembershipId = insertMembership(firstProjectId, leaderId, mentorId);
        long secondLeaderMembershipId = insertMembership(secondProjectId, leaderId, mentorId);
        insertLeadership(firstProjectId, firstLeaderMembershipId, mentorId);
        insertLeadership(secondProjectId, secondLeaderMembershipId, mentorId);
        taskService.create(
                "member@example.test",
                new CreateTaskCommand(firstProjectId, firstMembershipId, "First effort", null, null));
        taskService.create(
                "member@example.test",
                new CreateTaskCommand(secondProjectId, secondMembershipId, "Second effort", null, null));
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("delete from task_work_logs").update();
        jdbc.sql("delete from tasks").update();
        jdbc.sql("delete from project_leadership_terms").update();
        jdbc.sql("delete from project_memberships").update();
        jdbc.sql("delete from projects").update();
        jdbc.sql("delete from intern_profiles").update();
        jdbc.sql("delete from app_users").update();
    }

    @Test
    void concurrentCrossProjectLogsAreSerializedOnInternProfileAndRejectOverAllocation() throws Exception {
        long firstTaskId = taskId(firstProjectId);
        long secondTaskId = taskId(secondProjectId);
        CyclicBarrier start = new CyclicBarrier(2);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        Callable<Void> first = () -> {
            start.await();
            try {
                taskService.logWork(
                        "member@example.test", firstProjectId, firstTaskId,
                        new LogWorkCommand(WORK_DATE, 800, null));
                accepted.incrementAndGet();
            } catch (TaskValidationException exception) {
                rejected.incrementAndGet();
            }
            return null;
        };
        Callable<Void> second = () -> {
            start.await();
            try {
                taskService.logWork(
                        "member@example.test", secondProjectId, secondTaskId,
                        new LogWorkCommand(WORK_DATE, 800, null));
                accepted.incrementAndGet();
            } catch (TaskValidationException exception) {
                rejected.incrementAndGet();
            }
            return null;
        };

        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> firstFuture = executor.submit(first);
            Future<Void> secondFuture = executor.submit(second);
            firstFuture.get(60, TimeUnit.SECONDS);
            secondFuture.get(60, TimeUnit.SECONDS);

            assertThat(accepted.get()).isEqualTo(1);
            assertThat(rejected.get()).isEqualTo(1);
            assertThat(totalMinutesFor(memberUserId, WORK_DATE)).isEqualTo(800);
        } finally {
            executor.shutdownNow();
        }
    }

    private long taskId(long projectId) {
        return jdbc.sql("select id from tasks where project_id = :projectId")
                .param("projectId", projectId)
                .query(Long.class)
                .single();
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

    private long insertProject(long mentorId, String name, String status) {
        return jdbc.sql("""
                        insert into projects
                            (mentor_user_id, name, status, start_date, end_date, activated_at)
                        values (:mentorId, :name, :status, :startDate, :endDate,
                                case when :status = 'ACTIVE' then current_timestamp else null end)
                        returning id
                        """)
                .param("mentorId", mentorId)
                .param("name", name)
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

    private void insertLeadership(long targetProjectId, long leaderMembershipId, long mentorId) {
        jdbc.sql("""
                        insert into project_leadership_terms
                            (project_id, membership_id, appointed_by_mentor_user_id)
                        values (:projectId, :membershipId, :mentorId)
                        """)
                .param("projectId", targetProjectId)
                .param("membershipId", leaderMembershipId)
                .param("mentorId", mentorId)
                .update();
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
