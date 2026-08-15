package com.lab.labtimesheet.feature.project.controller;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException;
import com.lab.labtimesheet.feature.project.model.dto.ProjectCreateCommand;
import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectDetail;
import com.lab.labtimesheet.feature.project.model.dto.ProjectSummary;
import com.lab.labtimesheet.feature.project.model.dto.ProjectLeadershipTermView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMemberView;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
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
    private SmtpConfigurationService smtpConfiguration;

    @Test
    @WithMockUser(username = "mentor@example.test")
    void listsOnlyTheAuthenticatedUsersAuthorizedProjects() throws Exception {
        when(pages.authenticatedActor("mentor@example.test"))
                .thenReturn(new ProjectActorView(10L, "MENTOR"));
        when(pages.listVisible(10L)).thenReturn(List.of(new ProjectSummary(
                30L,
                "Intern Portal Refresh",
                "PLANNED",
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 9, 30))));

        mvc.perform(get("/projects"))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/list"))
                .andExpect(model().attributeExists("projects"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("Create Project")));

        verify(pages).listVisible(10L);
    }

    @Test
    @WithMockUser(username = "member@example.test")
    void nonMentorProjectListOmitsTheCreateLink() throws Exception {
        when(pages.authenticatedActor("member@example.test"))
                .thenReturn(new ProjectActorView(20L, "INTERN"));
        when(pages.listVisible(20L)).thenReturn(List.of());

        mvc.perform(get("/projects"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString("Create Project"))));
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
        when(pages.authenticatedUserId("mentor@example.test")).thenReturn(10L);
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

        mvc.perform(get("/projects/30"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString(">Activate<")));

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
    void invalidCreateSubmissionStaysOnSafeFormWithoutMutation() throws Exception {
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
        when(pages.authenticatedUserId("mentor@example.test")).thenReturn(10L);
        when(pages.detail(10L, 30L)).thenReturn(plannedOwnerDetail());
        when(pages.members(10L, 30L)).thenReturn(List.of(new ProjectMemberView(
                40L, 20L, "Current Leader", Instant.parse("2026-08-15T00:00:00Z"), null, true)));
        when(pages.leadership(10L, 30L)).thenReturn(List.of(new ProjectLeadershipTermView(
                50L, "Current Leader", Instant.parse("2026-08-15T00:00:00Z"), null)));
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
                .when(projects).addMember(10L, 30L, 20L);
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
                        .param("internUserId", "20"))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/members"))
                .andExpect(model().attributeHasFieldErrors("projectMemberForm", "internUserId"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("value=\"20\"")));

        mvc.perform(post("/projects/30/leadership")
                        .with(csrf())
                        .param("internUserId", "20"))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/leadership"))
                .andExpect(model().attributeHasFieldErrors("projectMemberForm", "internUserId"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("value=\"20\"")));
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
                Instant.parse("2026-09-30T00:00:00Z"))));

        mvc.perform(get("/projects/30").with(user(email)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("No current Leader")))
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
}
