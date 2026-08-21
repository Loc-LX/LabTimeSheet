package com.lab.labtimesheet.feature.task.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.task.exception.TaskValidationException;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.TaskAssigneeChoice;
import com.lab.labtimesheet.feature.task.model.dto.TaskDetails;
import com.lab.labtimesheet.feature.task.model.dto.TaskView;
import com.lab.labtimesheet.feature.task.model.dto.TaskWorkLogView;
import com.lab.labtimesheet.feature.task.service.TaskService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Production web contract for Iteration 2 Task definition, reassignment, deletion, and work-log actions. */
@WebMvcTest(TaskController.class)
class Iteration2TaskWorkflowWebTest {

    private static final String EMAIL = "leader@example.test";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TaskService tasks;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @Test
    void capabilityDrivenDetailBindsEveryIterationTwoTaskAction() throws Exception {
        TaskWorkLogView log = new TaskWorkLogView(
                77L, 10L, 25L, 7L, LocalDate.of(2026, 8, 20), 60, "Initial",
                Instant.parse("2026-08-20T10:00:00Z"), Instant.parse("2026-08-20T10:00:00Z"));
        when(tasks.details(EMAIL, 10L, 25L)).thenReturn(new TaskDetails(
                task(), List.of(), List.of(log), 7L, true, true, true, true, true, true));
        when(tasks.assignmentChoices(EMAIL, 10L)).thenReturn(List.of(
                new TaskAssigneeChoice(8L, "Recipient")));

        mvc.perform(get("/projects/10/tasks/25").with(user(EMAIL).roles("INTERN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Edit Task")))
                .andExpect(content().string(containsString("Reassign")))
                .andExpect(content().string(containsString("Delete Task")))
                .andExpect(content().string(containsString("Log work")))
                .andExpect(content().string(containsString("Correct work log")));

        mvc.perform(post("/projects/10/tasks/25/edit").with(user(EMAIL)).with(csrf())
                        .param("title", "Updated").param("description", "Safe")
                        .param("dueDate", "2026-08-30"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/10/tasks/25"));
        mvc.perform(post("/projects/10/tasks/25/reassign").with(user(EMAIL)).with(csrf())
                        .param("assigneeMembershipId", "8"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/projects/10/tasks/25/work-logs").with(user(EMAIL)).with(csrf())
                        .param("workDate", "2026-08-21").param("minutes", "90").param("note", "Work"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/projects/10/tasks/25/work-logs/77").with(user(EMAIL)).with(csrf())
                        .param("minutes", "75").param("note", "Corrected"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/projects/10/tasks/25/delete").with(user(EMAIL)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/10/tasks"));

        verify(tasks).edit(EMAIL, 10L, 25L, "Updated", "Safe", LocalDate.of(2026, 8, 30));
        verify(tasks).reassign(EMAIL, 10L, 25L, 8L);
        verify(tasks).addWorkLog(EMAIL, 10L, 25L, LocalDate.of(2026, 8, 21), 90, "Work");
        verify(tasks).correctWorkLog(EMAIL, 10L, 77L, 75, "Corrected");
        verify(tasks).softDelete(EMAIL, 10L, 25L);
    }

    @Test
    void rejectedTaskEditRetainsSafeInputWithAnInlineError() throws Exception {
        doThrow(new TaskValidationException("Due date is outside the Project"))
                .when(tasks).edit(EMAIL, 10L, 25L, "Retained title", "Retained description",
                        LocalDate.of(2026, 9, 1));
        Map<String, Object> input = Map.of(
                "title", "Retained title",
                "description", "Retained description",
                "dueDate", "2026-09-01");

        mvc.perform(post("/projects/10/tasks/25/edit").with(user(EMAIL)).with(csrf())
                        .param("title", "Retained title")
                        .param("description", "Retained description")
                        .param("dueDate", "2026-09-01"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("taskEditInput", input));

        when(tasks.details(EMAIL, 10L, 25L)).thenReturn(new TaskDetails(
                task(), List.of(), List.of(), 7L, false, false, true, false, false, false));
        mvc.perform(get("/projects/10/tasks/25").with(user(EMAIL))
                        .flashAttr("taskError", "Due date is outside the Project")
                        .flashAttr("taskEditInput", input))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"Retained title\"")))
                .andExpect(content().string(containsString("id=\"task-edit-error\"")));
    }

    @Test
    void malformedTaskWorkLogRetainsRawSafeInputInsteadOfReturningBadRequest() throws Exception {
        mvc.perform(post("/projects/10/tasks/25/work-logs").with(user(EMAIL)).with(csrf())
                        .param("workDate", "not-a-date")
                        .param("minutes", "many")
                        .param("note", "Retained note"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/10/tasks/25"))
                .andExpect(flash().attribute("taskError", "Enter a valid work date and minutes."))
                .andExpect(flash().attribute("taskWorkLogInput", Map.of(
                        "workDate", "not-a-date",
                        "minutes", "many",
                        "note", "Retained note")));
    }

    @Test
    void malformedTaskStatusRetainsRawSafeInputInsteadOfReturningBadRequest() throws Exception {
        mvc.perform(post("/projects/10/tasks/25/status").with(user(EMAIL)).with(csrf())
                        .param("status", "NOT_A_STATUS"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/10/tasks/25"))
                .andExpect(flash().attribute("taskError", "Choose a valid Task status."))
                .andExpect(flash().attribute("taskStatusInput", Map.of("status", "NOT_A_STATUS")));
    }

    private static TaskView task() {
        Instant now = Instant.parse("2026-08-20T10:00:00Z");
        return new TaskView(25L, 10L, 7L, "Member", "Draft", "Notes", TaskStatus.TODO,
                LocalDate.of(2026, 8, 30), 7L, 7L, now, now);
    }
}
