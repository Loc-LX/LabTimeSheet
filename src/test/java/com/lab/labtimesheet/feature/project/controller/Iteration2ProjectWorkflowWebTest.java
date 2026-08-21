package com.lab.labtimesheet.feature.project.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.feature.account.model.dto.EligibleInternOption;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.project.model.InvitationResponse;
import com.lab.labtimesheet.feature.project.model.InvitationStatus;
import com.lab.labtimesheet.feature.project.model.ProjectExitRequestStatus;
import com.lab.labtimesheet.feature.project.model.ProjectExitRequestType;
import com.lab.labtimesheet.feature.project.model.dto.PendingProjectInvitationView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectDetail;
import com.lab.labtimesheet.feature.project.model.dto.ProjectExitReadinessView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectExitRequestHistoryView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectHistoryView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectInvitationHistoryView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectLeadershipTermView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMemberView;
import com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import com.lab.labtimesheet.feature.task.model.dto.TaskCommentView;
import com.lab.labtimesheet.feature.task.model.dto.TaskHistoryView;
import com.lab.labtimesheet.feature.task.model.dto.TaskWorkLogView;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Production web contract for Project invitation, exit-transfer, and retained History workflows. */
@WebMvcTest(ProjectController.class)
class Iteration2ProjectWorkflowWebTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProjectQueryService pages;

    @MockitoBean
    private ProjectService projects;

    @MockitoBean
    private AccountService accounts;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @BeforeEach
    void setUpClock() {
        when(clock.instant()).thenReturn(Instant.parse("2026-08-21T00:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    @Test
    void invitedInternCanOpenInboxAndSubmitAnAuthenticatedResponse() throws Exception {
        when(pages.authenticatedActor("invitee@example.test")).thenReturn(new ProjectActorView(20L, "INTERN"));
        when(pages.authenticatedUserId("invitee@example.test")).thenReturn(20L);
        when(pages.pendingInvitations(20L)).thenReturn(List.of(new PendingProjectInvitationView(
                90L, 30L, "Research Portal", "Current Leader", Instant.parse("2026-08-20T00:00:00Z"))));

        mvc.perform(get("/projects/invitations").with(user("invitee@example.test").roles("INTERN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Research Portal")))
                .andExpect(content().string(containsString("Accept")))
                .andExpect(content().string(containsString("Decline")));

        mvc.perform(post("/projects/invitations/90/respond")
                        .with(user("invitee@example.test").roles("INTERN"))
                        .with(csrf())
                        .param("response", "ACCEPT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/invitations"));

        verify(projects).respondToInvitation(20L, 90L, InvitationResponse.ACCEPT);
    }

    @Test
    void currentLeaderSeesPersistentReadinessAndPostsOneAtomicTransferBatch() throws Exception {
        ProjectActorView actor = new ProjectActorView(20L, "INTERN");
        when(pages.authenticatedActor("leader@example.test")).thenReturn(actor);
        when(pages.authenticatedUserId("leader@example.test")).thenReturn(20L);
        when(pages.detail(20L, 30L)).thenReturn(detail(false));
        when(pages.members(20L, 30L)).thenReturn(List.of(
                member(40L, 20L, "Current Leader", true),
                member(41L, 21L, "Leaving Member", false),
                member(42L, 22L, "Recipient", false)));
        when(pages.exitReadiness(20L, 30L)).thenReturn(List.of(
                new ProjectExitReadinessView(70L, 41L, false, 2L, false)));
        Instant now = Instant.parse("2026-08-20T00:00:00Z");
        when(pages.history(20L, 30L)).thenReturn(new ProjectHistoryView(
                30L,
                List.of(member(40L, 20L, "Current Leader", true)),
                List.of(new ProjectLeadershipTermView(60L, "Current Leader", now, null, 10L, null)),
                List.of(new ProjectInvitationHistoryView(
                        90L, 23L, 60L, InvitationStatus.PENDING, null, null, null, null, now, now)),
                List.of(new ProjectExitRequestHistoryView(
                        70L, 41L, 40L, ProjectExitRequestType.LEADER_REMOVAL, "Capacity",
                        ProjectExitRequestStatus.PENDING, null, null, null, now, now)),
                List.of(new TaskHistoryView(
                        101L, 30L, 41L, "Completed Task", "Retained", TaskStatus.DONE,
                        LocalDate.of(2026, 8, 20), 40L, 40L, now, now, now, null, null,
                        List.of(new TaskCommentView(1L, 101L, 20L, "Retained comment", now)),
                        List.of(new TaskWorkLogView(
                                1L, 30L, 101L, 41L, LocalDate.of(2026, 8, 20), 60,
                                "Retained work", now, now))))));
        when(accounts.eligibleInternOptions(LocalDate.of(2026, 8, 21))).thenReturn(List.of(
                new EligibleInternOption(23L, "Invitee", "SV-023",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))));

        TimeZone previousZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            mvc.perform(get("/projects/30/workflows").with(user("leader@example.test").roles("INTERN")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Exit readiness")))
                    .andExpect(content().string(containsString("2 unfinished Tasks remain")))
                    .andExpect(content().string(containsString("Transfer unfinished Tasks")))
                    .andExpect(content().string(containsString("Invite an Intern")))
                    .andExpect(content().string(containsString("Project History")))
                    .andExpect(content().string(containsString("20/08/2026 07:00")))
                    .andExpect(content().string(containsString("Completed Task")))
                    .andExpect(content().string(containsString("Retained comment")))
                    .andExpect(content().string(containsString("Retained work")));
        } finally {
            TimeZone.setDefault(previousZone);
        }

        mvc.perform(post("/projects/30/exits/70/transfer")
                        .with(user("leader@example.test").roles("INTERN"))
                        .with(csrf())
                        .param("sourceMembershipId", "41")
                        .param("taskIds", "101", "102")
                        .param("recipientMembershipId", "42"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/30/workflows"));

        verify(projects).transferTasks(20L, 30L, 41L, Set.of(101L, 102L), 42L);

        when(projects.requestMemberRemoval(20L, 30L, 41L, "Retain this reason"))
                .thenThrow(new ProjectRuleViolationException("A pending request already exists"));
        Map<String, Object> retainedRemoval = Map.of(
                "kind", "removal",
                "targetMembershipId", 41L,
                "reason", "Retain this reason");
        mvc.perform(post("/projects/30/exits/removal")
                        .with(user("leader@example.test").roles("INTERN")).with(csrf())
                        .param("targetMembershipId", "41")
                        .param("reason", "Retain this reason"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("projectInput", retainedRemoval));

        mvc.perform(get("/projects/30/workflows")
                        .with(user("leader@example.test").roles("INTERN"))
                        .flashAttr("projectError", "A pending request already exists")
                        .flashAttr("projectInput", retainedRemoval))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"Retain this reason\"")))
                .andExpect(content().string(containsString("id=\"removal-form-error\"")));
    }

    @Test
    void owningMentorSeesReadinessDecisionsAndCompletedHistoryHasNoMutationControls() throws Exception {
        ProjectActorView mentor = new ProjectActorView(10L, "MENTOR");
        when(pages.authenticatedActor("mentor@example.test")).thenReturn(mentor);
        when(pages.detail(10L, 30L)).thenReturn(detail(true));
        when(pages.members(10L, 30L)).thenReturn(List.of(
                member(40L, 20L, "Current Leader", true),
                member(41L, 21L, "Leaving Member", false)));
        when(pages.exitReadiness(10L, 30L)).thenReturn(List.of(
                new ProjectExitReadinessView(70L, 41L, false, 0L, true)));
        when(pages.history(10L, 30L)).thenReturn(emptyHistory(30L));

        mvc.perform(get("/projects/30/workflows")
                        .with(user("mentor@example.test").roles("MENTOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Approve exit")))
                .andExpect(content().string(containsString("Direct remove")))
                .andExpect(content().string(containsString("Complete Project")));

        when(pages.detail(10L, 31L)).thenReturn(new ProjectDetail(
                31L, "Completed Project", null, "COMPLETED",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                "Mentor", null, false));
        when(pages.members(10L, 31L)).thenReturn(List.of());
        when(pages.exitReadiness(10L, 31L)).thenReturn(List.of());
        when(pages.history(10L, 31L)).thenReturn(emptyHistory(31L));

        mvc.perform(get("/projects/31/workflows")
                        .with(user("mentor@example.test").roles("MENTOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Direct remove"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Complete Project"))));
    }

    private static ProjectDetail detail(boolean canManage) {
        return new ProjectDetail(30L, "Research Portal", null, "ACTIVE",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31),
                "Mentor", "Current Leader", canManage);
    }

    private static ProjectMemberView member(long membershipId, long userId, String name, boolean leader) {
        return new ProjectMemberView(membershipId, userId, name,
                Instant.parse("2026-08-01T00:00:00Z"), null, leader, 10L, null);
    }

    private static ProjectHistoryView emptyHistory(long projectId) {
        return new ProjectHistoryView(
                projectId, List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
