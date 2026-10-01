package com.lab.labtimesheet.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import com.lab.labtimesheet.platform.authorization.AuthorizationPolicy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import jakarta.persistence.EntityManagerFactory;

/**
 * Verifies ARC-010 and AUTH-012: growing a rendered Task list/report from one row to fifty must
 * not grow authorization decisions or SQL statements. The hand-derived expectation is equality
 * of each page's two counts across both sizes, with exactly 1 and 50 rendered Task rows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class RequestWorkBoundWebIntegrationTest {

    private static final AtomicLong FIXTURES = new AtomicLong();

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private ProjectService projects;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @MockitoSpyBean private AuthorizationPolicy authorizationPolicy;

    @Test
    void taskListAndProjectTaskReportKeepPolicyAndStatementCountsConstant() throws Exception {
        Fixture fixture = createFixture();
        List<Long> taskIds = new ArrayList<>();
        taskIds.add(createTaskWithWorkLog(fixture, 1));

        Measurement listOne = measure(fixture, "/projects/%d/tasks".formatted(fixture.projectId()), taskIds, 1);
        Measurement reportOne = measure(fixture,
                "/reports/project-tasks?projectId=%d".formatted(fixture.projectId()), taskIds, 1);

        for (int taskNumber = 2; taskNumber <= 50; taskNumber++) {
            taskIds.add(createTaskWithWorkLog(fixture, taskNumber));
        }
        Measurement listFifty = measure(fixture, "/projects/%d/tasks".formatted(fixture.projectId()), taskIds, 50);
        Measurement reportFifty = measure(fixture,
                "/reports/project-tasks?projectId=%d".formatted(fixture.projectId()), taskIds, 50);

        assertThat(listOne.policyCalls()).isEqualTo(listFifty.policyCalls());
        assertThat(listOne.statements()).isEqualTo(listFifty.statements());
        assertThat(reportOne.policyCalls()).isEqualTo(reportFifty.policyCalls());
        assertThat(reportOne.statements()).isEqualTo(reportFifty.statements());
        assertThat(listOne.renderedRows()).isEqualTo(1);
        assertThat(listFifty.renderedRows()).isEqualTo(50);
        assertThat(reportOne.renderedRows()).isEqualTo(1);
        assertThat(reportFifty.renderedRows()).isEqualTo(50);

        System.out.printf("AC-ARC-002 / task list: 1 row=%d policy, %d statements; 50 rows=%d policy, %d statements%n",
                listOne.policyCalls(), listOne.statements(), listFifty.policyCalls(), listFifty.statements());
        System.out.printf("AC-ARC-002 / Project Task report: 1 row=%d policy, %d statements; 50 rows=%d policy, %d statements%n",
                reportOne.policyCalls(), reportOne.statements(), reportFifty.policyCalls(), reportFifty.statements());
    }

    private Measurement measure(Fixture fixture, String path, List<Long> taskIds, int expectedRows)
            throws Exception {
        perform(path, fixture.email());
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        clearInvocations(authorizationPolicy);
        String html = perform(path, fixture.email());
        long policyCalls = mockingDetails(authorizationPolicy).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("allows"))
                .count();
        long statements = statistics.getPrepareStatementCount();
        int renderedRows = renderedRows(html);
        assertThat(taskIds).hasSize(expectedRows);
        assertThat(renderedRows).isEqualTo(expectedRows);
        return new Measurement(policyCalls, statements, renderedRows);
    }

    private String perform(String path, String email) throws Exception {
        MvcResult result = mvc.perform(get(path).with(user(email).roles("MENTOR")))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private static int renderedRows(String html) {
        return html.split(java.util.regex.Pattern.quote("AC ARC Task "), -1).length - 1;
    }

    private Fixture createFixture() {
        String suffix = "bound" + FIXTURES.incrementAndGet();
        String mentorEmail = suffix + "-mentor@example.test";
        long adminId = insertUser(suffix + "-admin@example.test", "ADMIN");
        jdbc.sql("""
                update system_state
                set initialized = true, initialized_at = current_timestamp, bootstrap_admin_id = :admin
                where singleton_id = 1
                """).param("admin", adminId).update();
        String internEmail = suffix + "-intern@example.test";
        long mentorId = insertUser(mentorEmail, "MENTOR");
        long internId = insertUser(internEmail, "INTERN");
        jdbc.sql("""
                insert into intern_profiles
                    (user_id, student_code, internship_start_date, internship_end_date, internship_status, activated_at)
                values (:id, :code, date '2026-08-01', date '2026-12-31', 'ACTIVE', current_timestamp)
                """).param("id", internId).param("code", "SB" + internId).update();
        long projectId = jdbc.sql("""
                insert into projects (mentor_user_id, name, status, start_date, end_date, activated_at)
                values (:mentor, :name, 'PLANNED', date '2026-08-01', date '2026-12-31', null)
                returning id
                """).param("mentor", mentorId).param("name", suffix + " Project")
                .query(Long.class).single();
        long membershipId = jdbc.sql("""
                insert into project_memberships (project_id, intern_user_id, joined_at, added_by_user_id)
                values (:project, :intern, timestamptz '2026-08-01 00:00:00+00', :mentor) returning id
                """).param("project", projectId).param("intern", internId).param("mentor", mentorId)
                .query(Long.class).single();
        jdbc.sql("""
                insert into project_leadership_terms
                    (project_id, membership_id, started_at, appointed_by_mentor_user_id)
                values (:project, :membership, timestamptz '2026-08-01 00:00:00+00', :mentor)
                """).param("project", projectId).param("membership", membershipId).param("mentor", mentorId).update();
        projects.activate(mentorId, projectId);
        return new Fixture(mentorEmail, mentorId, projectId, membershipId);
    }

    private long createTaskWithWorkLog(Fixture fixture, int taskNumber) {
        long taskId = jdbc.sql("""
                insert into tasks
                    (project_id, assignee_membership_id, title, status, assigned_at,
                     created_by_membership_id, assigned_by_membership_id)
                values (:project, :membership, :title, 'TODO', current_timestamp, :membership, :membership)
                returning id
                """).param("project", fixture.projectId()).param("membership", fixture.membershipId())
                .param("title", "AC ARC Task " + taskNumber)
                .query(Long.class).single();
        jdbc.sql("""
                insert into task_work_logs (project_id, task_id, membership_id, work_date, minutes, note)
                values (:project, :task, :membership, :date, 30, 'AC-ARC-002 work')
                """).param("project", fixture.projectId()).param("task", taskId)
                .param("membership", fixture.membershipId()).param("date", LocalDate.of(2026, 9, 28)).update();
        return taskId;
    }

    private long insertUser(String email, String role) {
        return jdbc.sql("""
                insert into app_users (email, display_name, password_hash, global_role, account_status, activated_at)
                values (:email, :email, 'test-hash', :role, 'ACTIVE', current_timestamp) returning id
                """).param("email", email).param("role", role).query(Long.class).single();
    }

    private record Fixture(String email, long mentorId, long projectId, long membershipId) {}

    private record Measurement(long policyCalls, long statements, int renderedRows) {}
}
