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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.lab.labtimesheet.feature.task.exception.TaskNotFoundException;
import com.lab.labtimesheet.feature.task.exception.TaskValidationException;
import com.lab.labtimesheet.feature.task.model.TaskProgress;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.CreateTaskCommand;
import com.lab.labtimesheet.feature.task.model.dto.EditTaskCommand;
import com.lab.labtimesheet.feature.task.model.dto.LogWorkCommand;
import com.lab.labtimesheet.feature.task.model.dto.LogWorkCorrection;
import com.lab.labtimesheet.feature.task.model.dto.TaskAssigneeChoice;
import com.lab.labtimesheet.feature.task.model.dto.TaskCommentView;
import com.lab.labtimesheet.feature.task.model.dto.TaskDetails;
import com.lab.labtimesheet.feature.task.model.dto.TaskListView;
import com.lab.labtimesheet.feature.task.model.dto.TaskView;
import com.lab.labtimesheet.feature.task.model.dto.WorkLogView;
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
                .andExpect(status().isNotFound());
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
        given(taskService.changeStatus(ACTOR_EMAIL, 10L, 25L, TaskStatus.IN_PROGRESS))
                .willReturn(task(25L));
        given(taskService.addComment(ACTOR_EMAIL, 10L, 25L, "Update"))
                .willReturn(new TaskCommentView(3L, 25L, 5L, "Update", Instant.parse("2026-08-14T10:00:00Z")));

        mockMvc.perform(post("/projects/10/tasks/25/status")
                        .with(user(ACTOR_EMAIL))
                        .with(csrf())
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
    void reassignmentPostUsesAuthenticatedIdentityAndCsrf() throws Exception {
        given(taskService.reassign(ACTOR_EMAIL, 10L, 25L, 7L))
                .willReturn(task(25L));

        mockMvc.perform(post("/projects/10/tasks/25/reassign")
                        .with(user(ACTOR_EMAIL))
                        .with(csrf())
                        .param("assigneeMembershipId", "7"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/10/tasks/25"));

        verify(taskService).reassign(ACTOR_EMAIL, 10L, 25L, 7L);
    }

    @Test
    void reassignmentDetailsOfferAssigneeChoicesToLeaderOnly() throws Exception {
        TaskAssigneeChoice assignee = new TaskAssigneeChoice(7L, "Member Name");
        given(taskService.details(ACTOR_EMAIL, 10L, 25L))
                .willReturn(details(TaskStatus.IN_PROGRESS));
        given(taskService.assignmentChoices(ACTOR_EMAIL, 10L))
                .willReturn(List.of(assignee));

        mockMvc.perform(get("/projects/10/tasks/25").with(user(ACTOR_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Reassign")));
        verify(taskService).assignmentChoices(ACTOR_EMAIL, 10L);
    }

    @Test
    void editFormShowsExistingFieldsAndNoAssigneeSelector() throws Exception {
        given(taskService.details(ACTOR_EMAIL, 10L, 25L))
                .willReturn(details(true, true));

        mockMvc.perform(get("/projects/10/tasks/25/edit").with(user(ACTOR_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Save changes")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Draft")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Select an assignee"))));
    }

    @Test
    void editPostUsesAuthenticatedIdentityAndCsrf() throws Exception {
        given(taskService.edit(org.mockito.ArgumentMatchers.eq(ACTOR_EMAIL), any(EditTaskCommand.class)))
                .willReturn(task(25L));

        mockMvc.perform(post("/projects/10/tasks/25/edit")
                        .with(user(ACTOR_EMAIL))
                        .with(csrf())
                        .param("title", "Retitled")
                        .param("dueDate", "2026-08-21"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/10/tasks/25"));

        ArgumentCaptor<EditTaskCommand> command = ArgumentCaptor.forClass(EditTaskCommand.class);
        verify(taskService).edit(org.mockito.ArgumentMatchers.eq(ACTOR_EMAIL), command.capture());
        assertThat(command.getValue())
                .isEqualTo(new EditTaskCommand(10L, 25L, "Retitled", null, LocalDate.of(2026, 8, 21)));
    }

    @Test
    void deletePostUsesAuthenticatedIdentityAndCsrfAndRedirectsToHistory() throws Exception {
        given(taskService.softDelete(ACTOR_EMAIL, 10L, 25L))
                .willReturn(task(25L));

        mockMvc.perform(post("/projects/10/tasks/25/delete")
                        .with(user(ACTOR_EMAIL))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/10/tasks/25/history"));

        verify(taskService).softDelete(ACTOR_EMAIL, 10L, 25L);
    }

    @Test
    void historicalDetailsRenderDeletedBannerForAuthorizedViewer() throws Exception {
        given(taskService.historicalDetails(ACTOR_EMAIL, 10L, 25L))
                .willReturn(new TaskDetails(task(25L), List.of(), List.of(), false, false, false, false, false, false, true, 7L));

        mockMvc.perform(get("/projects/10/tasks/25/history").with(user(ACTOR_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Historical Task")));
    }

    @Test
    void taskDetailsHideUnavailableActionsAndShowAssignee() throws Exception {
        given(taskService.details(ACTOR_EMAIL, 10L, 25L))
                .willReturn(details(false, false));

        mockMvc.perform(get("/projects/10/tasks/25").with(user(ACTOR_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Member Name")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Change status"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Add comment"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Log work"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Reassign"))));
    }

    @Test
    void taskDetailsRenderAvailableActions() throws Exception {
        given(taskService.details(ACTOR_EMAIL, 10L, 25L))
                .willReturn(details(true, true));

        mockMvc.perform(get("/projects/10/tasks/25").with(user(ACTOR_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Change status")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Add comment")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Log work")));
    }

    @Test
    void taskDetailsRenderEditAndDeleteBesideTheTaskTitle() throws Exception {
        given(taskService.details(ACTOR_EMAIL, 10L, 25L))
                .willReturn(details(TaskStatus.TODO));

        mockMvc.perform(get("/projects/10/tasks/25").with(user(ACTOR_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("primary-action"),
                                org.hamcrest.Matchers.containsString(">Edit<"),
                                org.hamcrest.Matchers.containsString(">Delete<"))));
    }

    @Test
    void workLogPostsUseAuthenticatedIdentityAndCsrf() throws Exception {
        given(taskService.logWork(
                        org.mockito.ArgumentMatchers.eq(ACTOR_EMAIL),
                        org.mockito.ArgumentMatchers.eq(10L),
                        org.mockito.ArgumentMatchers.eq(25L),
                        any(LogWorkCommand.class)))
                .willReturn(new WorkLogView(
                        9L, 10L, 25L, 7L, "Member Name", LocalDate.of(2026, 8, 14), 60, null,
                        Instant.parse("2026-08-14T10:00:00Z"), Instant.parse("2026-08-14T10:00:00Z")));

        mockMvc.perform(post("/projects/10/tasks/25/work-logs")
                        .with(user(ACTOR_EMAIL))
                        .with(csrf())
                        .param("workDate", "2026-08-14")
                        .param("minutes", "60"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/10/tasks/25"));

        ArgumentCaptor<LogWorkCommand> command = ArgumentCaptor.forClass(LogWorkCommand.class);
        verify(taskService).logWork(
                org.mockito.ArgumentMatchers.eq(ACTOR_EMAIL), org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(25L), command.capture());
        assertThat(command.getValue()).isEqualTo(new LogWorkCommand(LocalDate.of(2026, 8, 14), 60, null));
    }

    @Test
    void workLogValidationFailureRerendersDetailsWithFriendlyError() throws Exception {
        given(taskService.logWork(
                        org.mockito.ArgumentMatchers.eq(ACTOR_EMAIL),
                        org.mockito.ArgumentMatchers.eq(10L),
                        org.mockito.ArgumentMatchers.eq(25L),
                        any(LogWorkCommand.class)))
                .willThrow(new TaskValidationException("Work date must be within Project dates"));
        given(taskService.details(ACTOR_EMAIL, 10L, 25L))
                .willReturn(details(true, true));

        mockMvc.perform(post("/projects/10/tasks/25/work-logs")
                        .with(user(ACTOR_EMAIL))
                        .with(csrf())
                        .param("workDate", "2026-01-01")
                        .param("minutes", "60"))
                .andExpect(status().isOk())
                .andExpect(view().name("tasks/detail"))
                .andExpect(model().attribute("workLogError", "Work date must be within Project dates"))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Work date must be within Project dates")));
    }

    @Test
    void workLogCorrectionPostsUseAuthenticatedIdentityAndCsrf() throws Exception {
        given(taskService.correctWorkLog(
                        org.mockito.ArgumentMatchers.eq(ACTOR_EMAIL),
                        org.mockito.ArgumentMatchers.eq(10L),
                        org.mockito.ArgumentMatchers.eq(25L),
                        org.mockito.ArgumentMatchers.eq(9L),
                        any(LogWorkCorrection.class)))
                .willReturn(new WorkLogView(
                        9L, 10L, 25L, 7L, "Member Name", LocalDate.of(2026, 8, 14), 90, "Rework",
                        Instant.parse("2026-08-14T10:00:00Z"), Instant.parse("2026-08-15T09:00:00Z")));

        mockMvc.perform(post("/projects/10/tasks/25/work-logs/9")
                        .with(user(ACTOR_EMAIL))
                        .with(csrf())
                        .param("minutes", "90")
                        .param("note", "Rework"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/10/tasks/25"));

        ArgumentCaptor<LogWorkCorrection> correction = ArgumentCaptor.forClass(LogWorkCorrection.class);
        verify(taskService).correctWorkLog(
                org.mockito.ArgumentMatchers.eq(ACTOR_EMAIL), org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(25L), org.mockito.ArgumentMatchers.eq(9L), correction.capture());
        assertThat(correction.getValue()).isEqualTo(new LogWorkCorrection(90, "Rework"));
    }

    @Test
    void detailRendersWorkLogsAndCorrectionFormOnlyForAuthoringMember() throws Exception {
        WorkLogView ownLog = new WorkLogView(
                9L, 10L, 25L, 7L, "Member Name", LocalDate.of(2026, 8, 14), 60, null,
                Instant.parse("2026-08-14T10:00:00Z"), Instant.parse("2026-08-14T10:00:00Z"));
        WorkLogView otherLog = new WorkLogView(
                8L, 10L, 25L, 12L, "Other Member", LocalDate.of(2026, 8, 13), 30, null,
                Instant.parse("2026-08-13T10:00:00Z"), Instant.parse("2026-08-13T10:00:00Z"));
        given(taskService.details(ACTOR_EMAIL, 10L, 25L))
                .willReturn(new TaskDetails(task(25L), List.of(), List.of(otherLog, ownLog), true, true, true, false, true, true, false, 7L));

        mockMvc.perform(get("/projects/10/tasks/25").with(user(ACTOR_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Correct")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Log by")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Other Member")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("by another member"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("14/08/2026")));
    }

    @ParameterizedTest(name = "{0} exposes only {1}")
    @MethodSource("allowedStatusChoices")
    void taskDetailsExposeOnlyAllowedStatusTransitions(TaskStatus current, List<TaskStatus> expected) throws Exception {
        given(taskService.details(ACTOR_EMAIL, 10L, 25L))
                .willReturn(details(current));

        mockMvc.perform(get("/projects/10/tasks/25").with(user(ACTOR_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("statuses", expected));
    }

    private static TaskDetails details(boolean canChangeStatus, boolean canComment) {
        return new TaskDetails(task(25L), List.of(), List.of(), canChangeStatus, canComment, canChangeStatus, false, false, false, false, null);
    }

    private static TaskDetails details(TaskStatus status) {
        return new TaskDetails(task(25L, status), List.of(), List.of(), true, true, true, true, true, true, false, 7L);
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
                LocalDate.of(2026, 8, 20), 7L, 7L, instant, instant, null);
    }
}
