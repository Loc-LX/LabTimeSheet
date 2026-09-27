package com.lab.labtimesheet.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.CorrectionStatus;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionRequestCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestCommand;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceRecordEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceRecordRepository;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCorrectionApplicationService;
import com.lab.labtimesheet.feature.attendance.service.LeaveApplicationService;
import com.lab.labtimesheet.feature.identity.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.identity.service.BootstrapService;
import com.lab.labtimesheet.feature.internship.service.InternshipService;
import com.lab.labtimesheet.feature.project.model.dto.ProjectCreateCommand;
import com.lab.labtimesheet.feature.project.model.TaskStatus;
import com.lab.labtimesheet.feature.project.model.dto.CreateTaskCommand;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.project.service.TaskService;
import com.lab.labtimesheet.feature.notification.model.NotificationEmailStatus;
import com.lab.labtimesheet.feature.notification.model.NotificationType;
import com.lab.labtimesheet.feature.notification.model.entity.NotificationEntity;
import com.lab.labtimesheet.feature.notification.repository.NotificationRepository;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.platform.model.SecurityMode;
import com.lab.labtimesheet.platform.model.dto.SmtpDraft;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import com.lab.labtimesheet.platform.service.SmtpProbe;
import java.io.IOException;
import java.io.StringReader;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.StringJoiner;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import com.lab.labtimesheet.feature.calendar.model.entity.AttendancePolicyEntity;

/**
 * Web-level disclosure contract checks for guessed protected identifiers.
 *
 * <p>Protects AUTH-002 and B-06: an existing record outside an Intern's scope and an absent
 * identifier must produce the same observable response. The pair uses one active actor, route,
 * and database fixture; only the identifier changes.</p>
 */
@Import({TestcontainersConfiguration.class, RecordDisclosureWebIntegrationTest.MailProbeConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RecordDisclosureWebIntegrationTest {
    private static final String PASSWORD = "correct horse battery staple";

    @Autowired private MockMvc mvc;
    @Autowired private BootstrapService bootstrap;
    @Autowired private AccountService accounts;
    @Autowired private InternshipService internships;
    @Autowired private SmtpConfigurationService smtp;
    @Autowired private RecordingSmtpProbe mail;
    @Autowired private LeaveApplicationService leaves;
    @Autowired private AttendanceCorrectionApplicationService corrections;
    @Autowired private AttendanceRecordRepository attendanceRecords;
    @Autowired private EntityManager entityManager;
    @Autowired private ProjectService projects;
    @Autowired private ProjectQueryService projectQueries;
    @Autowired private TaskService tasks;
    @Autowired private NotificationRepository notificationRows;

    /** Protects AUTH-002 and B-06 across Leave GET, edit and cancel routes, plus server-side denial of a hidden decision action. */
    @Test
    @Transactional
    void foreignLeaveAndMissingLeaveHaveTheSameNotFoundResponse() throws Exception {
        long adminId = initializeAdminAndSmtp();
        long ownerId = createActiveIntern(adminId, "owner@example.test", "INT-OWNER");
        createActiveIntern(adminId, "reader@example.test", "INT-READER");
        long leaveId = leaves.submit(
                new AttendanceActor(ownerId, GlobalRole.INTERN),
                new LeaveRequestCommand(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6), "Leave"))
                .id();

        assertSameNotFound(
                mvc.perform(get("/attendance/leave/{requestId}", leaveId)
                        .with(user("reader@example.test").roles("INTERN")))
                        .andReturn(),
                mvc.perform(get("/attendance/leave/{requestId}", Long.MAX_VALUE)
                        .with(user("reader@example.test").roles("INTERN")))
                        .andReturn());
        assertSameNotFound(
                mvc.perform(post("/attendance/leave/{requestId}/edit", leaveId)
                        .with(user("reader@example.test").roles("INTERN"))
                        .with(csrf())
                        .param("startDate", "2026-10-05").param("endDate", "2026-10-06").param("reason", "Updated"))
                        .andReturn(),
                mvc.perform(post("/attendance/leave/{requestId}/edit", Long.MAX_VALUE)
                        .with(user("reader@example.test").roles("INTERN"))
                        .with(csrf())
                        .param("startDate", "2026-10-05").param("endDate", "2026-10-06").param("reason", "Updated"))
                        .andReturn());
        assertSameNotFound(
                mvc.perform(post("/attendance/leave/{requestId}/cancel", leaveId)
                        .with(user("reader@example.test").roles("INTERN"))
                        .with(csrf()))
                        .andReturn(),
                mvc.perform(post("/attendance/leave/{requestId}/cancel", Long.MAX_VALUE)
                        .with(user("reader@example.test").roles("INTERN"))
                        .with(csrf()))
                        .andReturn());

        String ownerPage = mvc.perform(get("/attendance/leave/{requestId}", leaveId)
                        .with(user("owner@example.test").roles("INTERN")))
                .andReturn().getResponse().getContentAsString();
        assertThat(ownerPage).doesNotContain("/attendance/leave/" + leaveId + "/approve");
        var hiddenApprove = mvc.perform(post("/attendance/leave/{requestId}/approve", leaveId)
                        .with(user("owner@example.test").roles("INTERN"))
                        .with(csrf()))
                .andReturn();
        assertThat(hiddenApprove.getResponse().getStatus()).isEqualTo(403);
        assertThat(leaves.view(new AttendanceActor(ownerId, GlobalRole.INTERN), leaveId).status().name())
                .isEqualTo("PENDING");
    }

    private static void assertSameNotFound(MvcResult foreign, MvcResult missing) throws Exception {
        assertThat(foreign.getResponse().getStatus()).isEqualTo(404);
        assertThat(foreign.getResponse().getStatus()).isEqualTo(missing.getResponse().getStatus());
        assertThat(foreign.getModelAndView().getViewName()).isEqualTo("error/generic");
        assertThat(missing.getModelAndView().getViewName()).isEqualTo("error/generic");
        assertThat(normalizedBody(foreign.getResponse().getContentAsString()))
                .isEqualTo(normalizedBody(missing.getResponse().getContentAsString()));
    }

    /** Protects AUTH-002 for selected Project IDs in the HTML and XLSX Project/Task report routes. */
    @Test
    @Transactional
    void inaccessibleAndMissingProjectUseTheSameReportNotFoundResponse() throws Exception {
        long adminId = initializeAdminAndSmtp();
        long mentorId = createActiveAccount(adminId, new CreateAccountCommand(
                "mentor@example.test", "Mentor", GlobalRole.MENTOR, null, null, null));
        createActiveAccount(adminId, new CreateAccountCommand(
                "reader-mentor@example.test", "Reader Mentor", GlobalRole.MENTOR, null, null, null));
        long leaderId = createActiveIntern(adminId, "leader@example.test", "INT-LEADER");
        createActiveIntern(adminId, "outsider@example.test", "INT-OUTSIDER");
        internships.activateInternship(leaderId, adminId);
        long projectId = projects.create(mentorId, new ProjectCreateCommand(
                "Report Project", "Disclosure fixture", LocalDate.of(2026, 8, 14),
                LocalDate.of(2026, 12, 31), leaderId));

        String htmlPath = "/reports/project-tasks";
        MvcResult foreignHtml = mvc.perform(get(htmlPath).param("projectId", Long.toString(projectId))
                        .with(user("outsider@example.test").roles("INTERN")))
                .andReturn();
        MvcResult missingHtml = mvc.perform(get(htmlPath).param("projectId", Long.toString(Long.MAX_VALUE))
                        .with(user("outsider@example.test").roles("INTERN")))
                .andReturn();
        assertSameNotFound(foreignHtml, missingHtml);

        MvcResult foreignExport = mvc.perform(get("/reports/project-tasks.xlsx")
                        .param("projectId", Long.toString(projectId))
                        .param("dueFrom", "2026-08-14").param("dueTo", "2026-08-14")
                        .param("workFrom", "2026-08-14").param("workTo", "2026-08-14")
                        .with(user("outsider@example.test").roles("INTERN")))
                .andReturn();
        MvcResult missingExport = mvc.perform(get("/reports/project-tasks.xlsx")
                        .param("projectId", Long.toString(Long.MAX_VALUE))
                        .param("dueFrom", "2026-08-14").param("dueTo", "2026-08-14")
                        .param("workFrom", "2026-08-14").param("workTo", "2026-08-14")
                        .with(user("outsider@example.test").roles("INTERN")))
                .andReturn();
        assertThat(foreignExport.getResponse().getStatus()).isEqualTo(404);
        assertThat(foreignExport.getResponse().getStatus()).isEqualTo(missingExport.getResponse().getStatus());
        assertThat(foreignExport.getResponse().getContentAsString())
                .isEqualTo(missingExport.getResponse().getContentAsString());
    }

    /** Protects AUTH-002 separately for Daily report HTML and XLSX export routes. */
    @Test
    @Transactional
    void dailyReportHtmlAndExportHideForeignAndMissingProjects() throws Exception {
        long adminId = initializeAdminAndSmtp();
        long ownerId = createActiveAccount(adminId, new CreateAccountCommand(
                "owner-mentor@example.test", "Owner Mentor", GlobalRole.MENTOR, null, null, null));
        long readerId = createActiveAccount(adminId, new CreateAccountCommand(
                "reader-mentor@example.test", "Reader Mentor", GlobalRole.MENTOR, null, null, null));
        long leaderId = createActiveIntern(adminId, "leader@example.test", "INT-LEADER");
        internships.activateInternship(leaderId, adminId);
        long projectId = projects.create(ownerId, new ProjectCreateCommand(
                "Daily Project", "Daily report disclosure", LocalDate.of(2026, 8, 14),
                LocalDate.of(2026, 12, 31), leaderId));

        assertSameNotFound(
                mvc.perform(get("/reports/daily").param("projectId", Long.toString(projectId))
                        .with(user("reader-mentor@example.test").roles("MENTOR"))).andReturn(),
                mvc.perform(get("/reports/daily").param("projectId", Long.toString(Long.MAX_VALUE))
                        .with(user("reader-mentor@example.test").roles("MENTOR"))).andReturn());

        var foreignExport = mvc.perform(get("/reports/daily.xlsx")
                        .param("projectId", Long.toString(projectId)).param("date", "2026-08-14")
                        .with(user("reader-mentor@example.test").roles("MENTOR"))).andReturn();
        var missingExport = mvc.perform(get("/reports/daily.xlsx")
                        .param("projectId", Long.toString(Long.MAX_VALUE)).param("date", "2026-08-14")
                        .with(user("reader-mentor@example.test").roles("MENTOR"))).andReturn();
        assertThat(foreignExport.getResponse().getStatus()).isEqualTo(404);
        assertThat(foreignExport.getResponse().getStatus()).isEqualTo(missingExport.getResponse().getStatus());
        assertThat(foreignExport.getResponse().getContentAsString())
                .isEqualTo(missingExport.getResponse().getContentAsString());
    }

    /** Protects AUTH-002 and the B-01 ledger rule: every observed Project/identity mismatch has one exact ledger row. */
    @Test
    void projectAndIdentityDisclosureLedgerMatchesObservedFailures() throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream("authorization/open-disclosure-cases.tsv")) {
            assertThat(stream).isNotNull();
            try (var reader = new BufferedReader(new InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))) {
                assertThat(reader.lines().toList())
                        .containsExactly("record type\troute\tactor\tdifference\tfix-task");
            }
        }
    }

    private long createActiveAccount(long adminId, CreateAccountCommand command) {
        var created = internships.create(command, adminId);
        assertThat(created.deliverySucceeded()).isTrue();
        assertThat(accounts.activate(mail.activationTokenFor(command.email()), PASSWORD)).isTrue();
        return created.userId();
    }

    /** Protects AUTH-002 for target Intern history and the role check for Admin account IDs. */
    @Test
    @Transactional
    void nonInternAndMissingTargetHaveTheSameAttendanceHistoryNotFoundResponse() throws Exception {
        long adminId = initializeAdminAndSmtp();
        createActiveAccount(adminId, new CreateAccountCommand(
                "mentor@example.test", "Mentor", GlobalRole.MENTOR, null, null, null));
        assertSameNotFound(
                mvc.perform(get("/attendance/interns/{internId}", adminId)
                        .with(user("mentor@example.test").roles("MENTOR"))).andReturn(),
                mvc.perform(get("/attendance/interns/{internId}", Long.MAX_VALUE)
                        .with(user("mentor@example.test").roles("MENTOR"))).andReturn());
    }

    /** Protects AUTH-002 on correction detail and submission routes, plus the hidden Mentor decision action. */
    @Test
    @Transactional
    void foreignAndMissingCorrectionTargetsHaveTheSameNotFoundResponse() throws Exception {
        long adminId = initializeAdminAndSmtp();
        long ownerId = createActiveIntern(adminId, "owner@example.test", "INT-OWNER");
        long otherId = createActiveIntern(adminId, "other@example.test", "INT-OTHER");
        internships.activateInternship(ownerId, adminId);
        internships.activateInternship(otherId, adminId);
        long recordId = createMissingCheckout(ownerId);
        var correction = corrections.submit(new AttendanceActor(ownerId, GlobalRole.INTERN), recordId,
                new CorrectionRequestCommand(LocalDateTime.of(2026, 8, 13, 15, 0), "Missed checkout"));

        assertSameNotFound(
                mvc.perform(get("/attendance/corrections/{correctionId}", correction.id())
                        .with(user("other@example.test").roles("INTERN"))).andReturn(),
                mvc.perform(get("/attendance/corrections/{correctionId}", Long.MAX_VALUE)
                        .with(user("other@example.test").roles("INTERN"))).andReturn());

        assertSameNotFound(
                mvc.perform(post("/attendance/corrections")
                        .with(user("other@example.test").roles("INTERN")).with(csrf())
                        .param("attendanceRecordId", Long.toString(recordId))
                        .param("proposedCheckout", "2026-08-13T15:00")
                        .param("reason", "Forged attendance ID")).andReturn(),
                mvc.perform(post("/attendance/corrections")
                        .with(user("other@example.test").roles("INTERN")).with(csrf())
                        .param("attendanceRecordId", Long.toString(Long.MAX_VALUE))
                        .param("proposedCheckout", "2026-08-13T15:00")
                        .param("reason", "Forged attendance ID")).andReturn());

        String ownerPage = mvc.perform(get("/attendance/corrections/{correctionId}", correction.id())
                        .with(user("owner@example.test").roles("INTERN")))
                .andReturn().getResponse().getContentAsString();
        assertThat(ownerPage).doesNotContain("/attendance/corrections/" + correction.id() + "/decide");
        var hiddenDecision = mvc.perform(post("/attendance/corrections/{correctionId}/decide", correction.id())
                        .with(user("owner@example.test").roles("INTERN")).with(csrf()).param("decision", "APPROVE"))
                .andReturn();
        assertThat(hiddenDecision.getResponse().getStatus()).isEqualTo(403);
        assertThat(corrections.view(new AttendanceActor(ownerId, GlobalRole.INTERN), correction.id()).status())
                .isEqualTo(CorrectionStatus.PENDING);
    }

    private long createMissingCheckout(long internId) {
        AttendancePolicyEntity policy = entityManager.getReference(AttendancePolicyEntity.class, 1L);
        AttendanceRecordEntity row = new AttendanceRecordEntity(
                internId, LocalDate.of(2026, 8, 13), policy.toDomain().id(),
                Instant.parse("2026-08-13T02:00:00Z"), null);
        entityManager.persist(row);
        entityManager.flush();
        return row.id();
    }

    /** Protects AUTH-002 and B-06 against hidden Project, Task and membership actions sent directly. */
    @Test
    @Transactional
    void hiddenProjectAndTaskActionsAreRejectedWithoutMutation() throws Exception {
        long adminId = initializeAdminAndSmtp();
        long mentorId = createActiveAccount(adminId, new CreateAccountCommand(
                "mentor@example.test", "Mentor", GlobalRole.MENTOR, null, null, null));
        createActiveAccount(adminId, new CreateAccountCommand(
                "reader-mentor@example.test", "Reader Mentor", GlobalRole.MENTOR, null, null, null));
        long leaderId = createActiveIntern(adminId, "leader@example.test", "INT-LEADER");
        long memberId = createActiveIntern(adminId, "member@example.test", "INT-MEMBER");
        internships.activateInternship(leaderId, adminId);
        internships.activateInternship(memberId, adminId);
        long projectId = projects.create(mentorId, new ProjectCreateCommand(
                "Protected Project", "Hidden controls", LocalDate.of(2026, 8, 14),
                LocalDate.of(2026, 12, 31), leaderId));
        projects.addMember(mentorId, projectId, memberId);

        assertSameNotFound(
                mvc.perform(get("/projects/{projectId}", projectId)
                        .with(user("reader-mentor@example.test").roles("MENTOR"))).andReturn(),
                mvc.perform(get("/projects/{projectId}", Long.MAX_VALUE)
                        .with(user("reader-mentor@example.test").roles("MENTOR"))).andReturn());

        String leaderPage = mvc.perform(get("/projects/{projectId}", projectId)
                        .with(user("leader@example.test").roles("INTERN")))
                .andReturn().getResponse().getContentAsString();
        assertThat(leaderPage).doesNotContain("/projects/" + projectId + "/activate");
        var directActivation = mvc.perform(post("/projects/{projectId}/activate", projectId)
                        .with(user("leader@example.test").roles("INTERN")).with(csrf()))
                .andReturn();
        assertThat(directActivation.getResponse().getStatus()).isEqualTo(404);
        assertThat(projectQueries.detail(leaderId, projectId).status()).isEqualTo("PLANNED");

        projects.activate(mentorId, projectId);
        long leaderMembershipId = projectQueries.taskContext(leaderId, projectId).currentLeaderMembershipId();
        var task = tasks.create("leader@example.test", new CreateTaskCommand(
                projectId, leaderMembershipId, "Leader task", null, LocalDate.of(2026, 8, 20)));
        var leaderWorkLog = tasks.addWorkLog(
                "leader@example.test", projectId, task.id(), task.version(),
                LocalDate.of(2026, 8, 14), 30, "Leader's real work");
        assertSameNotFound(
                mvc.perform(get("/projects/{projectId}/tasks/{taskId}", projectId, task.id())
                        .with(user("reader-mentor@example.test").roles("MENTOR"))).andReturn(),
                mvc.perform(get("/projects/{projectId}/tasks/{taskId}", projectId, Long.MAX_VALUE)
                        .with(user("reader-mentor@example.test").roles("MENTOR"))).andReturn());
        String memberTaskPage = mvc.perform(get("/projects/{projectId}/tasks/{taskId}", projectId, task.id())
                        .with(user("member@example.test").roles("INTERN")))
                .andReturn().getResponse().getContentAsString();
        assertThat(memberTaskPage).doesNotContain("/projects/" + projectId + "/tasks/" + task.id() + "/delete");
        var directDelete = mvc.perform(post(
                        "/projects/{projectId}/tasks/{taskId}/delete", projectId, task.id())
                        .with(user("member@example.test").roles("INTERN")).with(csrf())
                        .param("expectedVersion", Long.toString(task.version())))
                .andReturn();
        assertThat(directDelete.getResponse().getStatus()).isEqualTo(404);
        assertThat(tasks.list("member@example.test", projectId).tasks())
                .anyMatch(visible -> visible.id() == task.id());

        assertThat(memberTaskPage).doesNotContain("/projects/" + projectId + "/tasks/" + task.id() + "/work-logs");
        assertSameNotFound(
                mvc.perform(post("/projects/{projectId}/tasks/{taskId}/work-logs/{workLogId}",
                                projectId, task.id(), leaderWorkLog.id())
                        .with(user("member@example.test").roles("INTERN")).with(csrf())
                        .param("expectedTaskVersion", Long.toString(task.version()))
                        .param("expectedWorkLogVersion", Long.toString(leaderWorkLog.version()))
                        .param("minutes", "45").param("note", "Forged correction"))
                        .andReturn(),
                mvc.perform(post("/projects/{projectId}/tasks/{taskId}/work-logs/{workLogId}",
                                projectId, task.id(), Long.MAX_VALUE)
                        .with(user("member@example.test").roles("INTERN")).with(csrf())
                        .param("expectedTaskVersion", Long.toString(task.version()))
                        .param("expectedWorkLogVersion", "0")
                        .param("minutes", "45").param("note", "Forged correction"))
                        .andReturn());
        var directWorkLog = mvc.perform(post(
                        "/projects/{projectId}/tasks/{taskId}/work-logs", projectId, task.id())
                        .with(user("member@example.test").roles("INTERN")).with(csrf())
                        .param("expectedTaskVersion", Long.toString(task.version()))
                        .param("workDate", "2026-08-14").param("minutes", "30").param("note", "Forged"))
                .andReturn();
        assertThat(directWorkLog.getResponse().getStatus()).isEqualTo(404);
        assertThat(tasks.details("member@example.test", projectId, task.id()).workLogs())
                .extracting(workLog -> workLog.id())
                .containsExactly(leaderWorkLog.id());

        long memberMembershipId = projectQueries.taskContext(leaderId, projectId).activeMembers().stream()
                .filter(member -> member.userId() == memberId).findFirst().orElseThrow().membershipId();
        String leaderWorkflow = mvc.perform(get("/projects/{projectId}/workflows", projectId)
                        .with(user("leader@example.test").roles("INTERN")))
                .andReturn().getResponse().getContentAsString();
        assertThat(leaderWorkflow).doesNotContain(
                "/projects/" + projectId + "/members/" + memberMembershipId + "/remove");
        assertSameNotFound(
                mvc.perform(post("/projects/{projectId}/members/{membershipId}/remove", projectId, memberMembershipId)
                        .with(user("leader@example.test").roles("INTERN")).with(csrf())).andReturn(),
                mvc.perform(post("/projects/{projectId}/members/{membershipId}/remove", projectId, Long.MAX_VALUE)
                        .with(user("leader@example.test").roles("INTERN")).with(csrf())).andReturn());
        assertThat(projectQueries.taskContext(leaderId, projectId).activeMembers())
                .anyMatch(member -> member.userId() == memberId);
    }

    /** Protects AUTH-002 for invitation response IDs and verifies the recipient-only hidden action boundary. */
    @Test
    @Transactional
    void nonRecipientCannotRespondToInvitationByGuessingItsId() throws Exception {
        long adminId = initializeAdminAndSmtp();
        long mentorId = createActiveAccount(adminId, new CreateAccountCommand(
                "mentor@example.test", "Mentor", GlobalRole.MENTOR, null, null, null));
        long leaderId = createActiveIntern(adminId, "leader@example.test", "INT-LEADER");
        long memberId = createActiveIntern(adminId, "member@example.test", "INT-MEMBER");
        long inviteeId = createActiveIntern(adminId, "invitee@example.test", "INT-INVITEE");
        internships.activateInternship(leaderId, adminId);
        internships.activateInternship(memberId, adminId);
        internships.activateInternship(inviteeId, adminId);
        long projectId = projects.create(mentorId, new ProjectCreateCommand(
                "Invitation Project", "Invitation disclosure", LocalDate.of(2026, 8, 14),
                LocalDate.of(2026, 12, 31), leaderId));
        projects.addMember(mentorId, projectId, memberId);
        projects.activate(mentorId, projectId);
        long invitationId = projects.issueInvitation(leaderId, projectId, inviteeId);

        String inbox = mvc.perform(get("/projects/invitations")
                        .with(user("member@example.test").roles("INTERN")))
                .andReturn().getResponse().getContentAsString();
        assertThat(inbox).doesNotContain("Invitation Project");
        assertSameNotFound(
                mvc.perform(post("/projects/invitations/{invitationId}/respond", invitationId)
                        .with(user("member@example.test").roles("INTERN")).with(csrf()).param("response", "ACCEPT"))
                        .andReturn(),
                mvc.perform(post("/projects/invitations/{invitationId}/respond", Long.MAX_VALUE)
                        .with(user("member@example.test").roles("INTERN")).with(csrf()).param("response", "ACCEPT"))
                        .andReturn());
        assertThat(projectQueries.pendingInvitations(inviteeId))
                .anyMatch(pending -> pending.invitationId() == invitationId);
    }

    /** Protects AUTH-002 for an exit-request ID and prevents another Mentor's direct approval. */
    @Test
    @Transactional
    void nonOwningMentorCannotApproveExitRequestByGuessingItsId() throws Exception {
        long adminId = initializeAdminAndSmtp();
        long ownerId = createActiveAccount(adminId, new CreateAccountCommand(
                "owner-mentor@example.test", "Owner Mentor", GlobalRole.MENTOR, null, null, null));
        createActiveAccount(adminId, new CreateAccountCommand(
                "other-mentor@example.test", "Other Mentor", GlobalRole.MENTOR, null, null, null));
        long leaderId = createActiveIntern(adminId, "leader@example.test", "INT-LEADER");
        long memberId = createActiveIntern(adminId, "member@example.test", "INT-MEMBER");
        internships.activateInternship(leaderId, adminId);
        internships.activateInternship(memberId, adminId);
        long projectId = projects.create(ownerId, new ProjectCreateCommand(
                "Exit Project", "Exit disclosure", LocalDate.of(2026, 8, 14),
                LocalDate.of(2026, 12, 31), leaderId));
        projects.addMember(ownerId, projectId, memberId);
        long memberMembershipId = projectQueries.taskContext(leaderId, projectId).activeMembers().stream()
                .filter(member -> member.userId() == memberId).findFirst().orElseThrow().membershipId();
        long exitRequestId = projects.requestMemberRemoval(leaderId, projectId, memberMembershipId, "Exit request");

        assertSameNotFound(
                mvc.perform(post("/projects/{projectId}/exits/{requestId}/approve", projectId, exitRequestId)
                        .with(user("other-mentor@example.test").roles("MENTOR")).with(csrf()))
                        .andReturn(),
                mvc.perform(post("/projects/{projectId}/exits/{requestId}/approve", projectId, Long.MAX_VALUE)
                        .with(user("other-mentor@example.test").roles("MENTOR")).with(csrf()))
                        .andReturn());
        assertThat(projectQueries.exitReadiness(ownerId, projectId))
                .anyMatch(readiness -> readiness.requestId() == exitRequestId);
    }

    /** Protects AUTH-002's coarse Admin route gate for account records. */
    @Test
    @Transactional
    void nonAdminIsDeniedFromAdminAccountRoutes() throws Exception {
        long adminId = initializeAdminAndSmtp();
        createActiveAccount(adminId, new CreateAccountCommand(
                "mentor@example.test", "Mentor", GlobalRole.MENTOR, null, null, null));
        mvc.perform(get("/admin/accounts/{targetUserId}", 1L)
                        .with(user("mentor@example.test").roles("MENTOR")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }

    /** Protects AUTH-002 recipient scoping and confirms a forged mark-read request is a no-op. */
    @Test
    @Transactional
    void foreignAndMissingNotificationIdsRedirectIdenticallyWithoutMutation() throws Exception {
        long adminId = initializeAdminAndSmtp();
        long recipientId = createActiveIntern(adminId, "recipient@example.test", "INT-RECIPIENT");
        long otherId = createActiveIntern(adminId, "other@example.test", "INT-OTHER");
        NotificationEntity notification = notificationRows.saveAndFlush(NotificationEntity.create(
                recipientId, NotificationType.SYSTEM, "Test", "Body", null,
                NotificationEmailStatus.NOT_REQUIRED, null, null, null, null, Instant.parse("2026-08-14T00:00:00Z")));

        var foreign = mvc.perform(post("/notifications/{notificationId}/read", notification.getId())
                        .with(user("other@example.test").roles("INTERN")).with(csrf()))
                .andReturn();
        var missing = mvc.perform(post("/notifications/{notificationId}/read", Long.MAX_VALUE)
                        .with(user("other@example.test").roles("INTERN")).with(csrf()))
                .andReturn();
        assertThat(foreign.getResponse().getStatus()).isEqualTo(302);
        assertThat(foreign.getResponse().getRedirectedUrl()).isEqualTo("/notifications");
        assertThat(foreign.getResponse().getStatus()).isEqualTo(missing.getResponse().getStatus());
        assertThat(foreign.getResponse().getRedirectedUrl()).isEqualTo(missing.getResponse().getRedirectedUrl());
        assertThat(notificationRows.findById(notification.getId()).orElseThrow().getReadAt()).isNull();
    }

    private static String normalizedBody(String html) throws IOException {
        StringBuilder canonical = new StringBuilder();
        new ParserDelegator().parse(new StringReader(html), new HTMLEditorKit.ParserCallback() {
            @Override public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                appendTag(canonical, "<", tag, attributes);
            }
            @Override public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                Object name = attributes.getAttribute(HTML.Attribute.NAME);
                if (tag == HTML.Tag.INPUT && "_csrf".equals(name)) return;
                appendTag(canonical, "<", tag, attributes);
            }
            @Override public void handleEndTag(HTML.Tag tag, int position) {
                canonical.append("</").append(tag).append('>');
            }
            @Override public void handleText(char[] data, int position) {
                canonical.append(new String(data).replaceAll("\\s+", " ").trim());
            }
            private void appendTag(StringBuilder target, String prefix, HTML.Tag tag, MutableAttributeSet attributes) {
                target.append(prefix).append(tag);
                Enumeration<?> names = attributes.getAttributeNames();
                List<String> rendered = new ArrayList<>();
                while (names.hasMoreElements()) {
                    Object key = names.nextElement();
                    if (HTML.Attribute.ENDTAG.equals(key)) continue;
                    if (tag == HTML.Tag.INPUT && "_csrf".equals(attributes.getAttribute(HTML.Attribute.NAME))
                            && HTML.Attribute.VALUE.equals(key)) continue;
                    rendered.add(key + "=" + attributes.getAttribute(key));
                }
                Collections.sort(rendered);
                StringJoiner joiner = new StringJoiner(" ", " ", "");
                rendered.forEach(joiner::add);
                if (!rendered.isEmpty()) target.append(joiner);
                target.append('>');
            }
        }, true);
        return canonical.toString();
    }

    private long initializeAdminAndSmtp() {
        bootstrap.bootstrap("admin@example.test", "Admin", PASSWORD);
        long adminId = accounts.requireActiveAdminId("admin@example.test");
        long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                "mailpit", 1025, SecurityMode.NONE, null, null, "admin@example.test", "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "admin@example.test");
        smtp.activate(draftId, adminId);
        mail.clear();
        return adminId;
    }

    private long createActiveIntern(long adminId, String email, String studentCode) {
        return createActiveAccount(adminId, new CreateAccountCommand(
                email,
                studentCode,
                GlobalRole.INTERN,
                studentCode,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31)));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MailProbeConfiguration {
        @Bean @Primary RecordingSmtpProbe recordingSmtpProbe() { return new RecordingSmtpProbe(); }
    }

    static final class RecordingSmtpProbe implements SmtpProbe {
        private final List<Message> messages = new ArrayList<>();
        @Override public void send(
                com.lab.labtimesheet.platform.model.dto.SmtpConnection connection,
                String recipient, String subject, String body) {
            messages.add(new Message(recipient, body));
        }
        void clear() { messages.clear(); }
        String activationTokenFor(String recipient) {
            String body = messages.stream().filter(message -> message.recipient().equals(recipient))
                    .findFirst().orElseThrow().body();
            int tokenStart = body.indexOf("token=");
            assertThat(tokenStart).isGreaterThanOrEqualTo(0);
            return body.substring(tokenStart + "token=".length()).trim();
        }
    }
    record Message(String recipient, String body) { }
}
