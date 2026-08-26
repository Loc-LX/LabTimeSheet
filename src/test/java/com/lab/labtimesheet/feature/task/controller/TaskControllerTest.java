package com.lab.labtimesheet.feature.task.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.lab.labtimesheet.feature.task.exception.TaskNotFoundException;
import com.lab.labtimesheet.feature.task.exception.TaskConflictException;
import com.lab.labtimesheet.feature.task.exception.TaskValidationException;
import com.lab.labtimesheet.feature.task.model.TaskProgress;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.TaskVarianceState;
import com.lab.labtimesheet.feature.task.model.dto.CreateTaskCommand;
import com.lab.labtimesheet.feature.task.model.dto.TaskAssigneeChoice;
import com.lab.labtimesheet.feature.task.model.dto.TaskCommentView;
import com.lab.labtimesheet.feature.task.model.dto.TaskDetails;
import com.lab.labtimesheet.feature.task.model.dto.TaskEffortPlanningView;
import com.lab.labtimesheet.feature.task.model.dto.TaskListView;
import com.lab.labtimesheet.feature.task.model.dto.TaskRemainingEffortForecastView;
import com.lab.labtimesheet.feature.task.model.dto.TaskView;
import com.lab.labtimesheet.feature.task.model.dto.RemainingEffortForecastInput;
import com.lab.labtimesheet.feature.task.service.TaskService;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TaskController.class)
class TaskControllerTest {

    private static final String ACTOR_EMAIL = "member@example.test";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @Test
    void taskListRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/projects/10/tasks"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(taskService);
    }

    @Test
    void emptyTaskListRendersNotApplicableProgress() throws Exception {
        given(taskService.list(ACTOR_EMAIL, 10L))
                .willReturn(new TaskListView(List.of(), TaskProgress.from(List.of()), false));

        mockMvc.perform(get("/projects/10/tasks").with(user(ACTOR_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(view().name("tasks/list"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("N/A")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Create Task"))));
    }

    @Test
    void taskListShowsAssigneeAndCreateActionOnlyWhenAllowed() throws Exception {
        given(taskService.list(ACTOR_EMAIL, 10L)).willReturn(new TaskListView(
                List.of(task(25L)), TaskProgress.from(List.of(TaskStatus.TODO)), true));

        mockMvc.perform(get("/projects/10/tasks").with(user(ACTOR_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Member Name")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Create Task")));
    }

    @Test
    void guessedTaskIdentifierReturnsNotFoundWithoutRenderingDetails() throws Exception {
        given(taskService.details(ACTOR_EMAIL, 10L, 999L)).willThrow(new TaskNotFoundException());

        mockMvc.perform(get("/projects/10/tasks/999").with(user(ACTOR_EMAIL)))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/generic"))
                .andExpect(model().attribute("errorTitle", "Task or Project unavailable"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("TaskNotFoundException"))));
    }

    @Test
    void inaccessibleProjectTaskListReturnsGenericNotFoundPage() throws Exception {
        given(taskService.list(ACTOR_EMAIL, 10L)).willThrow(new TaskNotFoundException());

        mockMvc.perform(get("/projects/10/tasks").with(user(ACTOR_EMAIL)))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/generic"))
                .andExpect(model().attribute("errorTitle", "Task or Project unavailable"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "not available to you")));
    }

    @Test
    void validCreateFormUsesAuthenticatedIdentityAndRedirectsToCreatedTask() throws Exception {
        given(taskService.create(org.mockito.ArgumentMatchers.eq(ACTOR_EMAIL), any(CreateTaskCommand.class)))
                .willReturn(task(25L));

        mockMvc.perform(post("/projects/10/tasks")
                        .with(user(ACTOR_EMAIL))
                        .with(csrf())
                        .param("title", "Draft")
                        .param("description", "Notes")
                        .param("assigneeMembershipId", "7")
                        .param("dueDate", "2026-08-20"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/10/tasks/25"));

        ArgumentCaptor<CreateTaskCommand> command = ArgumentCaptor.forClass(CreateTaskCommand.class);
        verify(taskService).create(org.mockito.ArgumentMatchers.eq(ACTOR_EMAIL), command.capture());
        assertThat(command.getValue()).isEqualTo(new CreateTaskCommand(
                10L, 7L, "Draft", "Notes", LocalDate.of(2026, 8, 20)));
    }

    @Test
    void leaderCreateFormShowsEstimateButMemberCreateFormDoesNot() throws Exception {
        given(taskService.assignmentChoices("leader@example.test", 10L)).willReturn(List.of(new TaskAssigneeChoice(7L, "Member")));
        given(taskService.canSetEstimateOnCreate("leader@example.test", 10L)).willReturn(true);
        mockMvc.perform(get("/projects/10/tasks/new").with(user("leader@example.test")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("estimatedMinutes")));
        given(taskService.assignmentChoices(ACTOR_EMAIL, 10L)).willReturn(List.of(new TaskAssigneeChoice(7L, "Member")));
        given(taskService.canSetEstimateOnCreate(ACTOR_EMAIL, 10L)).willReturn(false);
        mockMvc.perform(get("/projects/10/tasks/new").with(user(ACTOR_EMAIL)))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("estimatedMinutes"))));
    }

    @Test
    void estimatePostUsesPublicServiceAndMapsValidationToSafeFlash() throws Exception {
        given(taskService.estimate(ACTOR_EMAIL, 10L, 25L, 4L, 120))
                .willThrow(new TaskValidationException("A Task estimate cannot change after work is logged."));
        mockMvc.perform(post("/projects/10/tasks/25/estimate").with(user(ACTOR_EMAIL)).with(csrf())
                        .param("expectedVersion", "4").param("estimatedMinutes", "120"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/projects/10/tasks/25"))
                .andExpect(flash().attribute("taskError", "A Task estimate cannot change after work is logged."));
        verify(taskService).estimate(ACTOR_EMAIL, 10L, 25L, 4L, 120);
    }

    @Test
    void estimatePostConflictUsesExistingConflictContract() throws Exception {
        given(taskService.estimate(ACTOR_EMAIL, 10L, 25L, 4L, 120))
                .willThrow(new TaskConflictException("stale", null));
        mockMvc.perform(post("/projects/10/tasks/25/estimate").with(user(ACTOR_EMAIL)).with(csrf())
                        .param("expectedVersion", "4").param("estimatedMinutes", "120"))
                .andExpect(status().isConflict()).andExpect(view().name("error/generic"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Reload")));
    }

    @Test
    void workedReassignmentBindsForecastInputThroughPublicService() throws Exception {
        mockMvc.perform(post("/projects/10/tasks/25/reassign").with(user("leader@example.test")).with(csrf())
                        .param("expectedVersion", "2").param("assigneeMembershipId", "8")
                        .param("remainingMinutes", "90").param("forecastNote", "  next phase  "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/10/tasks/25"));
        verify(taskService).reassign("leader@example.test", 10L, 25L, 2L, 8L,
                new RemainingEffortForecastInput(90, "  next phase  "));
    }

    @Test
    void forecastCorrectionBindsPredecessorAndReasonThroughPublicService() throws Exception {
        mockMvc.perform(post("/projects/10/tasks/25/forecasts/44/correct")
                        .with(user("leader@example.test"))
                        .with(csrf())
                        .param("remainingMinutes", "90")
                        .param("reason", "  scope changed  "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/10/tasks/25"));

        verify(taskService).correctForecast(
                "leader@example.test", 10L, 25L, 44L, 90, "  scope changed  ");
    }

    @Test
    void malformedForecastMinutesRedirectsWithSafeFlashInput() throws Exception {
        mockMvc.perform(post("/projects/10/tasks/25/reassign").with(user(ACTOR_EMAIL)).with(csrf())
                        .param("expectedVersion", "2").param("assigneeMembershipId", "8")
                        .param("remainingMinutes", "oops").param("forecastNote", "note"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("taskError", "Enter valid remaining effort minutes."))
                .andExpect(flash().attribute("taskReassignInput", org.hamcrest.Matchers.hasEntry("remainingMinutes", "oops")));
    }

    @Test
    void missingForecastMinutesWithNoteRedirectsThroughValidation() throws Exception {
        given(taskService.reassign(org.mockito.ArgumentMatchers.eq(ACTOR_EMAIL), org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(25L), org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.eq(8L), any(RemainingEffortForecastInput.class)))
                .willThrow(new TaskValidationException("Remaining effort must be between 1 and 527040 minutes"));
        mockMvc.perform(post("/projects/10/tasks/25/reassign").with(user(ACTOR_EMAIL)).with(csrf())
                        .param("expectedVersion", "2").param("assigneeMembershipId", "8")
                        .param("forecastNote", "note"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("taskError", "Remaining effort must be between 1 and 527040 minutes"));
    }

    @Test
    void forgedMemberEstimateCreateIsSafeNotFoundWithoutRerender() throws Exception {
        given(taskService.create(org.mockito.ArgumentMatchers.eq(ACTOR_EMAIL), any(CreateTaskCommand.class)))
                .willThrow(new TaskNotFoundException());
        mockMvc.perform(post("/projects/10/tasks").with(user(ACTOR_EMAIL)).with(csrf())
                        .param("title", "Forged").param("assigneeMembershipId", "7")
                        .param("estimatedMinutes", "120"))
                .andExpect(status().isNotFound()).andExpect(view().name("error/generic"));
        verify(taskService, org.mockito.Mockito.never()).assignmentChoices(ACTOR_EMAIL, 10L);
    }

    @Test
    void detailRendersPlanningFactsAndOnlyLeaderControl() throws Exception {
        TaskDetails details = new TaskDetails(task(25L, TaskStatus.TODO), List.of(), List.of(), 7L,
                true, true, true, true, true, true,
                new TaskEffortPlanningView(120, 0, TaskVarianceState.PENDING, null, true),
                List.of(new TaskRemainingEffortForecastView(0L, 10L, 25L, 8L, "Incoming",
                        7L, "Leader", Instant.parse("2026-08-20T02:00:00Z"), 90, 135,
                        "next phase", Instant.parse("2026-08-20T02:00:00Z"), null, null,
                        false, true, true)));
        given(taskService.details(ACTOR_EMAIL, 10L, 25L)).willReturn(details);
        mockMvc.perform(get("/projects/10/tasks/25").with(user(ACTOR_EMAIL)))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Pending")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Update estimate")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Remaining effort forecast")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("next phase")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Incoming")));
        given(taskService.details(ACTOR_EMAIL, 10L, 25L)).willReturn(new TaskDetails(
                task(25L, TaskStatus.DONE), List.of(), List.of(), 7L, false, true, false, false, false, false,
                new TaskEffortPlanningView(120, 150, TaskVarianceState.VALUE, 30L, false)));
        mockMvc.perform(get("/projects/10/tasks/25").with(user(ACTOR_EMAIL)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("120m")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("150m")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("+30m")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Update estimate"))));
        given(taskService.details(ACTOR_EMAIL, 10L, 25L)).willReturn(new TaskDetails(
                task(25L), List.of(), List.of(), 7L, false, true, false, false, false, false,
                new TaskEffortPlanningView(null, 0, TaskVarianceState.NOT_ESTIMATED, null, false)));
        mockMvc.perform(get("/projects/10/tasks/25").with(user(ACTOR_EMAIL)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("N/A")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No Remaining effort forecast")));
    }

    @Test
    void historicalAssignmentForecastIsNotRenderedAsCurrentOrCorrectable() throws Exception {
        Instant historicalAssignment = Instant.parse("2026-08-13T10:00:00Z");
        Instant currentAssignment = Instant.parse("2026-08-14T10:00:00Z");
        TaskView currentTask = new TaskView(
                25L, 10L, 9L, "Current member", "Draft", "Notes", TaskStatus.IN_PROGRESS,
                LocalDate.of(2026, 8, 20), 7L, 7L, currentAssignment, historicalAssignment, 3L);
        TaskRemainingEffortForecastView historical = new TaskRemainingEffortForecastView(
                41L, 10L, 25L, 8L, "Former member", 7L, "Leader",
                historicalAssignment, 90, 60, "handover", historicalAssignment,
                null, null, false, false, false);
        TaskRemainingEffortForecastView current = new TaskRemainingEffortForecastView(
                42L, 10L, 25L, 9L, "Current member", 7L, "Leader",
                currentAssignment, 80, 60, "latest", currentAssignment,
                null, null, false, true, true);
        given(taskService.details(ACTOR_EMAIL, 10L, 25L)).willReturn(new TaskDetails(
                currentTask, List.of(), List.of(), 7L,
                true, true, true, true, true, true,
                new TaskEffortPlanningView(null, 60, TaskVarianceState.NOT_ESTIMATED, null, false),
                List.of(historical, current)));

        mockMvc.perform(get("/projects/10/tasks/25").with(user(ACTOR_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(">Historical<")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("/forecasts/41/correct"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/forecasts/42/correct")));
    }

    @Test
    void closedCurrentForecastRemainsCurrentHistoryWithoutCorrectionForm() throws Exception {
        Instant assignment = Instant.parse("2026-08-14T10:00:00Z");
        TaskRemainingEffortForecastView closed = new TaskRemainingEffortForecastView(
                42L, 10L, 25L, 7L, "Current member", 7L, "Leader",
                assignment, 80, 60, "latest", assignment,
                null, null, false, true, false);
        given(taskService.details(ACTOR_EMAIL, 10L, 25L)).willReturn(new TaskDetails(
                task(25L), List.of(), List.of(), 7L,
                true, true, true, true, true, true,
                new TaskEffortPlanningView(null, 60, TaskVarianceState.NOT_ESTIMATED, null, false),
                List.of(closed)));

        mockMvc.perform(get("/projects/10/tasks/25").with(user(ACTOR_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Current (correction closed)")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("/forecasts/42/correct"))));
    }

    @Test
    void longForecastNoteRedirectsWithSafeRawInput() throws Exception {
        given(taskService.reassign(org.mockito.ArgumentMatchers.eq(ACTOR_EMAIL), org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(25L), org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.eq(8L), any(RemainingEffortForecastInput.class)))
                .willThrow(new TaskValidationException("Forecast note must not exceed 500 characters"));
        String note = "x".repeat(501);
        mockMvc.perform(post("/projects/10/tasks/25/reassign").with(user(ACTOR_EMAIL)).with(csrf())
                        .param("expectedVersion", "2").param("assigneeMembershipId", "8")
                        .param("remainingMinutes", "90").param("forecastNote", note))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("taskError", "Forecast note must not exceed 500 characters"))
                .andExpect(flash().attribute("taskReassignInput", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.hasEntry("assigneeMembershipId", "8"),
                        org.hamcrest.Matchers.hasEntry("remainingMinutes", "90"),
                        org.hamcrest.Matchers.hasEntry("forecastNote", note))));
    }

    @Test
    void blankCreateFormRendersValidationErrorWithoutWriting() throws Exception {
        given(taskService.assignmentChoices(ACTOR_EMAIL, 10L))
                .willReturn(List.of(new TaskAssigneeChoice(7L, "Member")));

        mockMvc.perform(post("/projects/10/tasks")
                        .with(user(ACTOR_EMAIL))
                        .with(csrf())
                        .param("title", "  ")
                        .param("assigneeMembershipId", "7"))
                .andExpect(status().isOk())
                .andExpect(view().name("tasks/form"))
                .andExpect(model().attributeHasFieldErrors("taskForm", "title"));

        verify(taskService, org.mockito.Mockito.never())
                .create(org.mockito.ArgumentMatchers.eq(ACTOR_EMAIL), any(CreateTaskCommand.class));
    }

    @Test
    void invalidDueDateRendersFieldErrorAndRetainsSafeInput() throws Exception {
        TaskAssigneeChoice assignee = new TaskAssigneeChoice(7L, "Member Name");
        given(taskService.create(org.mockito.ArgumentMatchers.eq(ACTOR_EMAIL), any(CreateTaskCommand.class)))
                .willThrow(new TaskValidationException("Due date must fall within the Project dates"));
        given(taskService.assignmentChoices(ACTOR_EMAIL, 10L)).willReturn(List.of(assignee));

        mockMvc.perform(post("/projects/10/tasks")
                        .with(user(ACTOR_EMAIL))
                        .with(csrf())
                        .param("title", "Draft")
                        .param("description", "Safe notes")
                        .param("assigneeMembershipId", "7")
                        .param("dueDate", "2026-09-01"))
                .andExpect(status().isOk())
                .andExpect(view().name("tasks/form"))
                .andExpect(model().attributeHasFieldErrors("taskForm", "dueDate"))
                .andExpect(model().attribute("assignees", List.of(assignee)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Draft")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Safe notes")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2026-09-01")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Due date must fall within the Project dates")));
    }

    @Test
    void guessedProjectDuringCreateRemainsNotFound() throws Exception {
        given(taskService.create(org.mockito.ArgumentMatchers.eq(ACTOR_EMAIL), any(CreateTaskCommand.class)))
                .willThrow(new TaskNotFoundException());

        mockMvc.perform(post("/projects/999/tasks")
                        .with(user(ACTOR_EMAIL))
                        .with(csrf())
                        .param("title", "Draft")
                        .param("assigneeMembershipId", "7"))
                .andExpect(status().isNotFound());

        verify(taskService, org.mockito.Mockito.never()).assignmentChoices(ACTOR_EMAIL, 999L);
    }

    @Test
    void statusAndCommentPostsUseAuthenticatedIdentityAndCsrf() throws Exception {
        given(taskService.changeStatus(ACTOR_EMAIL, 10L, 25L, 0L, TaskStatus.IN_PROGRESS))
                .willReturn(task(25L));
        given(taskService.addComment(ACTOR_EMAIL, 10L, 25L, "Update"))
                .willReturn(new TaskCommentView(3L, 25L, 5L, "Update", Instant.parse("2026-08-14T10:00:00Z")));

        mockMvc.perform(post("/projects/10/tasks/25/status")
                        .with(user(ACTOR_EMAIL))
                        .with(csrf())
                        .param("expectedVersion", "0")
                        .param("status", "IN_PROGRESS"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/10/tasks/25"));
        mockMvc.perform(post("/projects/10/tasks/25/comments")
                        .with(user(ACTOR_EMAIL))
                        .with(csrf())
                        .param("body", "Update"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/10/tasks/25"));
    }

    @Test
    void taskConflictReturnsExplicitReloadResponse() throws Exception {
        given(taskService.changeStatus(ACTOR_EMAIL, 10L, 25L, 3L, TaskStatus.IN_PROGRESS))
                .willThrow(new TaskConflictException(
                        "Task changed concurrently; reload before trying again", null));

        mockMvc.perform(post("/projects/10/tasks/25/status")
                        .with(user(ACTOR_EMAIL))
                        .with(csrf())
                        .param("expectedVersion", "3")
                        .param("status", "IN_PROGRESS"))
                .andExpect(status().isConflict())
                .andExpect(view().name("error/generic"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Reload")));
    }

    @Test
    void taskDetailsHideUnavailableActionsAndShowAssignee() throws Exception {
        given(taskService.details(ACTOR_EMAIL, 10L, 25L))
                .willReturn(new TaskDetails(task(25L), List.of(), false, false));

        mockMvc.perform(get("/projects/10/tasks/25").with(user(ACTOR_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Member Name")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Change status"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Add comment"))));
    }

    @Test
    void taskDetailsRenderAvailableActions() throws Exception {
        given(taskService.details(ACTOR_EMAIL, 10L, 25L))
                .willReturn(new TaskDetails(task(25L), List.of(), true, true));

        mockMvc.perform(get("/projects/10/tasks/25").with(user(ACTOR_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Change status")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Add comment")));
    }

    @ParameterizedTest(name = "{0} exposes only {1}")
    @MethodSource("allowedStatusChoices")
    void taskDetailsExposeOnlyAllowedStatusTransitions(TaskStatus current, List<TaskStatus> expected) throws Exception {
        given(taskService.details(ACTOR_EMAIL, 10L, 25L))
                .willReturn(new TaskDetails(task(25L, current), List.of(), true, true));

        mockMvc.perform(get("/projects/10/tasks/25").with(user(ACTOR_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("statuses", expected));
    }

    private static Stream<Arguments> allowedStatusChoices() {
        return Stream.of(
                Arguments.of(TaskStatus.TODO, List.of(TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED)),
                Arguments.of(TaskStatus.IN_PROGRESS, List.of(TaskStatus.BLOCKED, TaskStatus.DONE)),
                Arguments.of(TaskStatus.BLOCKED, List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS)),
                Arguments.of(TaskStatus.DONE, List.of(TaskStatus.IN_PROGRESS)));
    }

    private static TaskView task(long id) {
        return task(id, TaskStatus.TODO);
    }

    private static TaskView task(long id, TaskStatus status) {
        Instant instant = Instant.parse("2026-08-14T10:00:00Z");
        return new TaskView(
                id, 10L, 7L, "Member Name", "Draft", "Notes", status,
                LocalDate.of(2026, 8, 20), 7L, 7L, instant, instant);
    }
}
