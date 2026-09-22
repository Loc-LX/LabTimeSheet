package com.lab.labtimesheet.feature.project.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.lab.labtimesheet.feature.account.model.dto.EligibleInternOption;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException;
import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectCreateCommand;
import com.lab.labtimesheet.feature.project.model.dto.ProjectDetail;
import com.lab.labtimesheet.feature.project.model.dto.ProjectExitReadinessView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectLeadershipTermView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectListPage;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMemberView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectSummary;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import com.lab.labtimesheet.feature.task.exception.TaskConflictException;
import com.lab.labtimesheet.feature.task.exception.TaskValidationException;
import com.lab.labtimesheet.feature.task.model.dto.RemainingEffortForecastInput;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProjectController.class)
class ProjectControllerTest {

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
    void serverBusinessDate() {
        when(clock.instant()).thenReturn(Instant.parse("2026-08-15T01:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    @Test
    @WithMockUser(username = "mentor@example.test")
    void projectCreationRendersSearchableEligibleLeaderOptionsWithoutVisibleNumericIds() throws Exception {
        when(pages.authenticatedActor("mentor@example.test"))
                .thenReturn(new ProjectActorView(10L, "MENTOR"));
        when(accounts.eligibleInternOptions(LocalDate.of(2026, 8, 15))).thenReturn(List.of(
                option(20L, "Nguyen An", "STU-020"),
                option(21L, "Tran Binh", "STU-021")));

        String html = mvc.perform(get("/projects/new"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("eligibleInternOptions"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("data-intern-picker")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("type=\"radio\"")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("Nguyen An")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("STU-020")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("01/08/2026 – 31/12/2026")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString("Initial Leader user ID"))))
                .andReturn().getResponse().getContentAsString();
        assertFalse(containsRequiredRadio(html));
    }

    @Test
    @WithMockUser(username = "mentor@example.test")
    void projectCreationExplainsWhenNoEligibleLeaderIsAvailable() throws Exception {
        when(pages.authenticatedActor("mentor@example.test"))
                .thenReturn(new ProjectActorView(10L, "MENTOR"));
        when(accounts.eligibleInternOptions(LocalDate.of(2026, 8, 15))).thenReturn(List.of());

        mvc.perform(get("/projects/new"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("No eligible Interns are available.")));
    }

    @Test
    @WithMockUser(username = "mentor@example.test")
    void memberAndLeadershipPickersExposeOnlyValidServerFilteredOptions() throws Exception {
        when(pages.authenticatedUserId("mentor@example.test")).thenReturn(10L);
        when(pages.detail(10L, 30L)).thenReturn(plannedOwnerDetail());
        when(pages.members(10L, 30L)).thenReturn(List.of(
                new ProjectMemberView(40L, 20L, "Current Leader", Instant.parse("2026-08-15T00:00:00Z"), null, true, 10L, null),
                new ProjectMemberView(41L, 21L, "Current Member", Instant.parse("2026-08-15T00:00:00Z"), null, false, 10L, null)));
        when(pages.leadership(10L, 30L)).thenReturn(List.of());
        when(accounts.eligibleInternOptions(LocalDate.of(2026, 8, 15))).thenReturn(List.of(
                option(20L, "Current Leader", "STU-020"),
                option(21L, "Current Member", "STU-021"),
                option(22L, "Eligible Nonmember", "STU-022")));

        String membersHtml = mvc.perform(get("/projects/30/members"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(membersHtml.contains("name=\"internUserIds\""));
        assertTrue(membersHtml.contains("Eligible Nonmember"));
        assertFalse(membersHtml.contains("data-picker-label>Current Member"));

        String leadershipHtml = mvc.perform(get("/projects/30/leadership"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(leadershipHtml.contains("type=\"radio\""));
        assertTrue(leadershipHtml.contains("Current Member"));
        assertFalse(leadershipHtml.contains("Eligible Nonmember"));
        assertFalse(leadershipHtml.contains("data-picker-label>Current Leader"));
        assertFalse(containsRequiredRadio(leadershipHtml));
    }

    @Test
    @WithMockUser(username = "mentor@example.test")
    void rejectedMemberBatchRetainsEligibleSelectionsAndExplainsUnavailableCountWithoutIds() throws Exception {
        when(pages.authenticatedUserId("mentor@example.test")).thenReturn(10L);
        when(pages.detail(10L, 30L)).thenReturn(plannedOwnerDetail());
        when(pages.members(10L, 30L)).thenReturn(List.of());
        when(accounts.eligibleInternOptions(LocalDate.of(2026, 8, 15))).thenReturn(List.of(
                option(21L, "First Intern", "STU-021")));
        doThrow(new ProjectRuleViolationException("One or more selected Interns are no longer eligible"))
                .when(projects).addMembers(10L, 30L, List.of(21L, 22L));

        mvc.perform(post("/projects/30/members")
                        .with(csrf())
                        .param("internUserIds", "21", "22"))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/members"))
                .andExpect(model().attributeHasFieldErrors("projectMembersForm", "internUserIds"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("One or more selected Interns are no longer eligible")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("1 previously selected Intern is no longer eligible; choose a replacement.")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("value=\"21\" id=\"internUserIds1\" name=\"internUserIds\" checked=\"checked\"")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString("value=\"22\""))));
    }

    @Test
    @WithMockUser(username = "mentor@example.test")
    void listsOnlyTheAuthenticatedUsersAuthorizedProjects() throws Exception {
        when(pages.authenticatedActor("mentor@example.test"))
                .thenReturn(new ProjectActorView(10L, "MENTOR"));
        when(pages.listPage(10L, PageRequest.of(0, 50))).thenReturn(new ProjectListPage(
                List.of(new ProjectSummary(
                        30L,
                        "Intern Portal Refresh",
                        "PLANNED",
                        LocalDate.of(2026, 8, 15),
                        LocalDate.of(2026, 9, 30))),
                1,
                50,
                false,
                false));

        mvc.perform(get("/projects"))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/list"))
                .andExpect(model().attributeExists("projects"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("Create Project")));

        verify(pages).listPage(10L, PageRequest.of(0, 50));
    }

    @Test
    @WithMockUser(username = "mentor@example.test")
    void projectListExposesPageTwoContinuationForMoreThanOneBoundedPage() throws Exception {
        when(pages.authenticatedActor("mentor@example.test"))
                .thenReturn(new ProjectActorView(10L, "MENTOR"));
        when(pages.listPage(10L, PageRequest.of(1, 50))).thenReturn(new ProjectListPage(
                List.of(new ProjectSummary(
                        1L,
                        "Project 51",
                        "Second page",
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 12, 31))),
                2,
                50,
                true,
                false));

        mvc.perform(get("/projects").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/list"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("Project 51")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("/projects?page=1")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString("/projects?page=3"))));

        verify(pages).listPage(10L, PageRequest.of(1, 50));
    }

    @Test
    @WithMockUser(username = "member@example.test")
    void nonMentorProjectListOmitsTheCreateLink() throws Exception {
        when(pages.authenticatedActor("member@example.test"))
                .thenReturn(new ProjectActorView(20L, "INTERN"));
        when(pages.listPage(20L, PageRequest.of(0, 50))).thenReturn(new ProjectListPage(
                List.of(), 1, 50, false, false));

        mvc.perform(get("/projects"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString("Create Project"))));
    }

    @Test
    @WithMockUser(username = "mentor@example.test")
    void nestedWorkflowPostsPassTheirRouteProjectToEveryMutationBoundary() throws Exception {
        when(pages.authenticatedUserId("mentor@example.test")).thenReturn(10L);

        mvc.perform(post("/projects/30/invitations/40/revoke").with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/projects/30/exits/50/cancel").with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/projects/30/exits/60/approve").with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/projects/30/exits/70/reject").with(csrf()).param("note", "No"))
                .andExpect(status().is3xxRedirection());

        verify(projects).revokeInvitation(10L, 30L, 40L);
        verify(projects).cancelExit(10L, 30L, 50L);
        verify(projects).approveExit(10L, 30L, 60L, null);
        verify(projects).rejectExit(10L, 30L, 70L, "No");
    }

    @Test
    @WithMockUser(username = "leader@example.test")
    void exitTransferBindsCompleteTaskVersionPairsAndPassesThemToProjectService() throws Exception {
        when(pages.authenticatedUserId("leader@example.test")).thenReturn(20L);

        mvc.perform(post("/projects/30/exits/70/transfer")
                        .with(csrf())
                        .param("sourceMembershipId", "41")
                        .param("taskIds", "101", "102")
                        .param("taskVersions", "101:4", "102:9")
                        .param("recipientMembershipId", "42"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/30/workflows"));

        verify(projects).transferTasks(
                20L, 30L, 70L, 41L, Set.of(101L, 102L), Map.of(101L, 4L, 102L, 9L), 42L);
    }

    @Test
    @WithMockUser(username = "leader@example.test")
    void exitTransferBindsWorkedTaskForecastInputsAtThePublicBoundary() throws Exception {
        when(pages.authenticatedUserId("leader@example.test")).thenReturn(20L);

        mvc.perform(post("/projects/30/exits/70/transfer")
                        .with(csrf())
                        .param("sourceMembershipId", "41")
                        .param("taskIds", "101", "102")
                        .param("taskVersions", "101:4", "102:9")
                        .param("forecastInputs", "101:90:  next phase  ")
                        .param("recipientMembershipId", "42"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/30/workflows"));

        verify(projects).transferTasks(
                20L,
                30L,
                70L,
                41L,
                Set.of(101L, 102L),
                Map.of(101L, 4L, 102L, 9L),
                Map.of(101L, new RemainingEffortForecastInput(90, "next phase")),
                42L);
    }

    @Test
    @WithMockUser(username = "leader@example.test")
    void exitTransferRendersTaskForecastValidationThroughTheWorkflowFlashBoundary() throws Exception {
        when(pages.authenticatedUserId("leader@example.test")).thenReturn(20L);
        when(projects.transferTasks(
                        20L,
                        30L,
                        70L,
                        41L,
                        Set.of(101L),
                        Map.of(101L, 4L),
                        Map.of(101L, new RemainingEffortForecastInput(90, "next phase")),
                        42L))
                .thenThrow(new TaskValidationException(
                        "A remaining-effort forecast is required for every worked Task"));

        mvc.perform(post("/projects/30/exits/70/transfer")
                        .with(csrf())
                        .param("sourceMembershipId", "41")
                        .param("taskIds", "101")
                        .param("taskVersions", "101:4")
                        .param("forecastInputs", "101:90:next phase")
                        .param("recipientMembershipId", "42"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/30/workflows"))
                .andExpect(flash().attribute("projectError",
                        "A remaining-effort forecast is required for every worked Task"));
    }

    @Test
    @WithMockUser(username = "leader@example.test")
    void exitTransferRejectsAnIncompleteTaskVersionMapBeforeCallingProjectService() throws Exception {
        when(pages.authenticatedUserId("leader@example.test")).thenReturn(20L);

        mvc.perform(post("/projects/30/exits/70/transfer")
                        .with(csrf())
                        .param("sourceMembershipId", "41")
                        .param("taskIds", "101", "102")
                        .param("taskVersions", "101:4")
                        .param("recipientMembershipId", "42"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/30/workflows"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash()
                        .attribute("projectError", "Submit one version for every selected Task."));

        org.mockito.Mockito.verifyNoInteractions(projects);
    }

    @Test
    @WithMockUser(username = "leader@example.test")
    void staleExitTransferReturnsSafeConflictWithoutProjectRedirect() throws Exception {
        when(pages.authenticatedUserId("leader@example.test")).thenReturn(20L);
        when(projects.transferTasks(
                        20L, 30L, 70L, 41L, Set.of(101L), Map.of(101L, 4L), 42L))
                .thenThrow(new TaskConflictException(
                        "Task changed concurrently; reload before trying again", null));

        mvc.perform(post("/projects/30/exits/70/transfer")
                        .with(csrf())
                        .param("sourceMembershipId", "41")
                        .param("taskIds", "101")
                        .param("taskVersions", "101:4")
                        .param("recipientMembershipId", "42"))
                .andExpect(status().isConflict())
                .andExpect(view().name("error/generic"))
                .andExpect(model().attribute("errorStatus", 409))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("Reload")));
    }

    @Test
    @WithMockUser(username = "member@example.test")
    void guessedProjectIdReturnsTheSameNotFoundResponseAsAMissingProject() throws Exception {
        when(pages.authenticatedUserId("member@example.test")).thenReturn(20L);
        when(pages.detail(20L, 999L)).thenThrow(new ProjectAccessDeniedException());

        mvc.perform(get("/projects/999"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/generic"))
                .andExpect(model().attribute("errorStatus", 404))
                .andExpect(model().attribute("errorTitle", "Project unavailable"))
                .andExpect(model().attributeExists("errorMessage"));
    }

    @Test
    @WithMockUser(username = "member@example.test")
    void memberAndLeadershipPagesUseTheSameProjectScopedAuthorization() throws Exception {
        when(pages.authenticatedUserId("member@example.test")).thenReturn(20L);
        when(pages.detail(20L, 30L)).thenReturn(new ProjectDetail(
                30L,
                "Intern Portal Refresh",
                null,
                "PLANNED",
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 9, 30),
                "Mentor",
                "Leader",
                false));
        when(pages.members(20L, 30L)).thenReturn(List.of());
        when(pages.leadership(20L, 30L)).thenReturn(List.of());

        mvc.perform(get("/projects/30/members"))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/members"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString("Add member"))));
        mvc.perform(get("/projects/30/leadership"))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/leadership"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString("Change Leader"))));
    }

    @Test
    @WithMockUser(username = "leader@example.test", roles = "INTERN")
    void currentLeaderGetsTheProjectDetailDailyReportAction() throws Exception {
        when(pages.authenticatedUserId("leader@example.test")).thenReturn(20L);
        when(pages.detail(20L, 30L)).thenReturn(new ProjectDetail(
                30L,
                "Intern Portal Refresh",
                "Portal work",
                "ACTIVE",
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 9, 30),
                "Mentor",
                "Current Leader",
                false,
                true));
        when(pages.exitReadiness(20L, 30L)).thenReturn(List.of());

        mvc.perform(get("/projects/30"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("Generate Daily Report")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("href=\"/reports/daily?projectId=30\"")));
    }

    @Test
    @WithMockUser(username = "member@example.test", roles = "INTERN")
    void ordinaryMemberDoesNotGetTheProjectDetailDailyReportAction() throws Exception {
        when(pages.authenticatedUserId("member@example.test")).thenReturn(20L);
        when(pages.detail(20L, 30L)).thenReturn(new ProjectDetail(
                30L,
                "Intern Portal Refresh",
                null,
                "ACTIVE",
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 9, 30),
                "Mentor",
                "Current Leader",
                false,
                false));
        when(pages.exitReadiness(20L, 30L)).thenReturn(List.of());

        mvc.perform(get("/projects/30"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString("Generate Daily Report"))));
    }

    @Test
    @WithMockUser(username = "member@example.test")
    void nonMentorCannotOpenProjectCreationForm() throws Exception {
        when(pages.authenticatedActor("member@example.test"))
                .thenReturn(new ProjectActorView(20L, "INTERN"));

        mvc.perform(get("/projects/new"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "mentor@example.test")
    void validCreateSubmissionUsesAuthenticatedMentorAndRedirectsToDetail() throws Exception {
        when(pages.authenticatedActor("mentor@example.test"))
                .thenReturn(new ProjectActorView(10L, "MENTOR"));
        when(projects.create(
                        10L,
                        new ProjectCreateCommand(
                                "Intern Portal Refresh",
                                "Refresh portal",
                                LocalDate.of(2026, 8, 15),
                                LocalDate.of(2026, 9, 30),
                                20L)))
                .thenReturn(30L);

        mvc.perform(post("/projects")
                        .with(csrf())
                        .param("name", "Intern Portal Refresh")
                        .param("description", "Refresh portal")
                        .param("startDate", "2026-08-15")
                        .param("endDate", "2026-09-30")
                        .param("initialLeaderUserId", "20"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/30"));
    }

    /**
     * Protects {@code PRJ-024} in the browser form. Observable break: the start date input carries
     * a {@code min} of today, so the browser blocks a Mentor from entering a Project that has
     * already started before the request ever reaches the server. Expected: with the server date
     * at 15 August, the start date input has no {@code min} attribute.
     *
     * @throws Exception if the form cannot be rendered
     */
    @Test
    @WithMockUser(username = "mentor@example.test")
    void projectCreationFormLetsTheStartDateLieInThePast() throws Exception {
        when(pages.authenticatedActor("mentor@example.test"))
                .thenReturn(new ProjectActorView(10L, "MENTOR"));
        when(accounts.eligibleInternOptions(LocalDate.of(2026, 8, 15)))
                .thenReturn(List.of(option(20L, "Nguyen An", "STU-020")));

        String html = mvc.perform(get("/projects/new"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        java.util.regex.Matcher startDateInput = java.util.regex.Pattern
                .compile("<input[^>]*id=\"startDate\"[^>]*>").matcher(html);
        assertTrue(startDateInput.find(), "the form renders a start date input");
        assertFalse(startDateInput.group().contains("min="), startDateInput.group());
    }

    /**
     * Protects the form's handling of a date rule the domain enforces. Observable break: a
     * {@code ProjectRuleViolationException} about dates escapes as an error page instead of
     * re-rendering the form with its message. Expected: the form is shown again with the
     * message bound to the start date field. Until 15 September 2026 this used the past start
     * date refusal, which {@code D21} removed; the end-date rule of {@code PRJ-024} remains.
     *
     * @throws Exception if the submission cannot be performed
     */
    @Test
    @WithMockUser(username = "mentor@example.test")
    void domainDateRuleViolationStaysOnStartDateField() throws Exception {
        when(pages.authenticatedActor("mentor@example.test"))
                .thenReturn(new ProjectActorView(10L, "MENTOR"));
        when(projects.create(
                        10L,
                        new ProjectCreateCommand(
                                "Dated Project", null, LocalDate.of(2026, 8, 14),
                                LocalDate.of(2026, 9, 30), 20L)))
                .thenThrow(new ProjectRuleViolationException("Project end date must not precede its start date"));

        mvc.perform(post("/projects")
                        .with(csrf())
                        .param("name", "Dated Project")
                        .param("startDate", "2026-08-14")
                        .param("endDate", "2026-09-30")
                        .param("initialLeaderUserId", "20"))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/form"))
                .andExpect(model().attributeHasFieldErrors("projectForm", "startDate"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("Project end date must not precede its start date")));
    }

    @Test
    @WithMockUser(username = "mentor@example.test")
    void missingInitialLeaderReRendersServerFieldError() throws Exception {
        when(pages.authenticatedActor("mentor@example.test"))
                .thenReturn(new ProjectActorView(10L, "MENTOR"));

        mvc.perform(post("/projects")
                        .with(csrf())
                        .param("name", "Intern Portal Refresh")
                        .param("startDate", "2026-08-15")
                        .param("endDate", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/form"))
                .andExpect(model().attributeHasFieldErrors("projectForm", "initialLeaderUserId"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("id=\"initialLeaderUserId-error\"")));

        verify(projects, never()).create(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @WithMockUser(username = "mentor@example.test")
    void missingReplacementLeaderReRendersServerFieldError() throws Exception {
        when(pages.authenticatedUserId("mentor@example.test")).thenReturn(10L);
        when(pages.detail(10L, 30L)).thenReturn(plannedOwnerDetail());
        when(pages.leadership(10L, 30L)).thenReturn(List.of());
        when(pages.members(10L, 30L)).thenReturn(List.of(new ProjectMemberView(
                41L, 21L, "Current Member", Instant.parse("2026-08-15T00:00:00Z"), null, false, 10L, null)));
        when(accounts.eligibleInternOptions(LocalDate.of(2026, 8, 15))).thenReturn(List.of(
                option(21L, "Current Member", "STU-021")));

        mvc.perform(post("/projects/30/leadership").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/leadership"))
                .andExpect(model().attributeHasFieldErrors("projectMemberForm", "internUserId"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("id=\"leadership-intern-user-error\"")));

        verify(projects, never()).changeLeader(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @WithMockUser(username = "mentor@example.test")
    void owningMentorCanActivateAPlannedProject() throws Exception {
        when(pages.authenticatedUserId("mentor@example.test")).thenReturn(10L);

        mvc.perform(post("/projects/30/activate").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/30"));

        verify(projects).activate(10L, 30L);
    }

    @Test
    @WithMockUser(username = "mentor@example.test")
    void plannedProjectDetailShowsActivationOnlyToTheOwningMentor() throws Exception {
        when(pages.authenticatedUserId("mentor@example.test")).thenReturn(10L);
        when(pages.detail(10L, 30L)).thenReturn(new ProjectDetail(
                30L,
                "Intern Portal Refresh",
                null,
                "PLANNED",
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 9, 30),
                "Mentor",
                "Leader",
                true));
        when(pages.exitReadiness(10L, 30L)).thenReturn(List.of(
                new ProjectExitReadinessView(70L, 40L, false, 2L, false)));

        mvc.perform(get("/projects/30"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString(">Activate<")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString(">Delete Project<")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("2 unfinished Tasks remain")));

        when(pages.detail(10L, 30L)).thenReturn(new ProjectDetail(
                30L,
                "Intern Portal Refresh",
                null,
                "PLANNED",
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 9, 30),
                "Mentor",
                "Leader",
                false));
        mvc.perform(get("/projects/30"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString(">Activate<"))));
    }

    @Test
    @WithMockUser(username = "mentor@example.test")
    void owningMentorCanDeleteAPlannedProject() throws Exception {
        when(pages.authenticatedUserId("mentor@example.test")).thenReturn(10L);

        mvc.perform(post("/projects/30/delete").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects"));

        verify(projects).delete(10L, 30L);
    }

    @Test
    @WithMockUser(username = "mentor@example.test")
    void activeProjectDoesNotExposeDeleteAndRejectedDeleteReturnsSafeDetail() throws Exception {
        when(pages.authenticatedUserId("mentor@example.test")).thenReturn(10L);
        when(pages.detail(10L, 30L)).thenReturn(new ProjectDetail(
                30L,
                "Active Project",
                null,
                "ACTIVE",
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 9, 30),
                "Mentor",
                "Leader",
                true));
        when(pages.exitReadiness(10L, 30L)).thenReturn(List.of());
        doThrow(new ProjectRuleViolationException("Only a planned Project can be deleted"))
                .when(projects).delete(10L, 30L);

        mvc.perform(get("/projects/30"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString(">Delete Project<"))));

        mvc.perform(post("/projects/30/delete").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/detail"))
                .andExpect(model().attribute("projectError", "Only a planned Project can be deleted"));

        verify(projects).delete(10L, 30L);
    }

    @Test
    @WithMockUser(username = "mentor@example.test")
    void invalidCreateSubmissionStaysOnSafeFormWithoutMutation() throws Exception {
        when(pages.authenticatedActor("mentor@example.test"))
                .thenReturn(new ProjectActorView(10L, "MENTOR"));

        mvc.perform(post("/projects")
                        .with(csrf())
                        .param("name", " ")
                        .param("startDate", "2026-09-30")
                        .param("endDate", "2026-08-15")
                        .param("initialLeaderUserId", "0"))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/form"))
                .andExpect(model().attributeHasFieldErrors(
                        "projectForm", "name", "initialLeaderUserId"))
                .andExpect(model().attributeHasErrors("projectForm"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("2026-09-30")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("2026-08-15")));

        verify(projects, never()).create(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @WithMockUser(username = "mentor@example.test")
    void domainValidationErrorsStayOnTheirSafeFormsWithRetainedInput() throws Exception {
        when(pages.authenticatedActor("mentor@example.test"))
                .thenReturn(new ProjectActorView(10L, "MENTOR"));
        when(pages.authenticatedUserId("mentor@example.test")).thenReturn(10L);
        when(pages.detail(10L, 30L)).thenReturn(plannedOwnerDetail());
        when(pages.members(10L, 30L)).thenReturn(List.of(new ProjectMemberView(
                40L, 20L, "Current Leader", Instant.parse("2026-08-15T00:00:00Z"), null, true, 10L, null)));
        when(pages.leadership(10L, 30L)).thenReturn(List.of(new ProjectLeadershipTermView(
                50L, "Current Leader", Instant.parse("2026-08-15T00:00:00Z"), null, 10L, null)));
        when(projects.create(
                        10L,
                        new ProjectCreateCommand(
                                "Retained name",
                                "Retained description",
                                LocalDate.of(2026, 8, 15),
                                LocalDate.of(2026, 9, 30),
                                99L)))
                .thenThrow(new ProjectRuleViolationException("Intern must have an active account and internship"));
        doThrow(new ProjectRuleViolationException("Intern is already a current Project member"))
                .when(projects).addMembers(10L, 30L, List.of(20L));
        doThrow(new ProjectRuleViolationException("Selected Intern is already the current Leader"))
                .when(projects).changeLeader(10L, 30L, 20L);

        mvc.perform(post("/projects")
                        .with(csrf())
                        .param("name", "Retained name")
                        .param("description", "Retained description")
                        .param("startDate", "2026-08-15")
                        .param("endDate", "2026-09-30")
                        .param("initialLeaderUserId", "99"))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/form"))
                .andExpect(model().attributeHasFieldErrors("projectForm", "initialLeaderUserId"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("Retained name")));

        mvc.perform(post("/projects/30/members")
                        .with(csrf())
                        .param("internUserIds", "20"))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/members"))
                .andExpect(model().attributeHasFieldErrors("projectMembersForm", "internUserIds"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("Intern is already a current Project member")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString("value=\"20\""))));

        mvc.perform(post("/projects/30/leadership")
                        .with(csrf())
                        .param("internUserId", "20"))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/leadership"))
                .andExpect(model().attributeHasFieldErrors("projectMemberForm", "internUserId"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("Selected Intern is already the current Leader")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString("value=\"20\""))));
    }

    @Test
    @WithMockUser(username = "mentor@example.test")
    void activationRuleErrorReturnsToDetailWithoutLosingSafeContext() throws Exception {
        when(pages.authenticatedUserId("mentor@example.test")).thenReturn(10L);
        when(pages.detail(10L, 30L)).thenReturn(plannedOwnerDetail());
        doThrow(new ProjectRuleViolationException("Every current Task assignee must be an active Project member"))
                .when(projects).activate(10L, 30L);

        mvc.perform(post("/projects/30/activate").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/detail"))
                .andExpect(model().attribute("projectError",
                        "Every current Task assignee must be an active Project member"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("Every current Task assignee must be an active Project member")));
    }

    @Test
    @WithMockUser(username = "member@example.test")
    void uncaughtRuleConflictUsesGenericNonDisclosingErrorContract() throws Exception {
        when(pages.authenticatedUserId("member@example.test")).thenReturn(20L);
        when(pages.detail(20L, 30L))
                .thenThrow(new ProjectRuleViolationException("sensitive aggregate detail"));

        mvc.perform(get("/projects/30"))
                .andExpect(status().isConflict())
                .andExpect(view().name("error/generic"))
                .andExpect(model().attribute("errorStatus", 409))
                .andExpect(model().attribute("errorTitle", "Project request could not be completed"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString("sensitive aggregate detail"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"owner@example.test", "admin@example.test", "former@example.test"})
    void completedProjectPagesRenderForAuthorizedRolesWithoutCurrentLeaderOrMutationForms(String email)
            throws Exception {
        long actorId = switch (email) {
            case "owner@example.test" -> 10L;
            case "admin@example.test" -> 11L;
            default -> 20L;
        };
        when(pages.authenticatedUserId(email)).thenReturn(actorId);
        when(pages.detail(actorId, 30L)).thenReturn(new ProjectDetail(
                30L,
                "Completed Project",
                null,
                "COMPLETED",
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 9, 30),
                "Mentor",
                null,
                false));
        when(pages.members(actorId, 30L)).thenReturn(List.of());
        when(pages.leadership(actorId, 30L)).thenReturn(List.of(new ProjectLeadershipTermView(
                50L,
                "Former Leader",
                Instant.parse("2026-08-15T00:00:00Z"),
                Instant.parse("2026-09-30T00:00:00Z"),
                10L,
                10L)));

        mvc.perform(get("/projects/30").with(user(email)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("No current Leader")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString("Generate Daily Report"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString(">Activate<"))));
        mvc.perform(get("/projects/30/members").with(user(email)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString("Add member"))));
        mvc.perform(get("/projects/30/leadership").with(user(email)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("Former Leader")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString("Change Leader"))));
    }

    @Test
    @WithMockUser(username = "mentor@example.test")
    void stateChangingRoutesRequireCsrf() throws Exception {
        mvc.perform(post("/projects"))
                .andExpect(status().isForbidden());
    }

    private static ProjectDetail plannedOwnerDetail() {
        return new ProjectDetail(
                30L,
                "Intern Portal Refresh",
                null,
                "PLANNED",
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 9, 30),
                "Mentor",
                "Current Leader",
                true);
    }

    private static EligibleInternOption option(long userId, String name, String studentCode) {
        return new EligibleInternOption(
                userId,
                name,
                studentCode,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31));
    }

    private static boolean containsRequiredRadio(String html) {
        return Pattern.compile("<input(?=[^>]*type=\\\"radio\\\")(?=[^>]*required(?:=|\\s|>))[^>]*>")
                .matcher(html)
                .find();
    }
}
