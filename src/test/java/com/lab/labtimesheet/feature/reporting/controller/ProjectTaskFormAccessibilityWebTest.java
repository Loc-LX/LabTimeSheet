package com.lab.labtimesheet.feature.reporting.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.project.controller.ProjectController;
import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import com.lab.labtimesheet.feature.task.controller.TaskController;
import com.lab.labtimesheet.feature.task.model.dto.TaskAssigneeChoice;
import com.lab.labtimesheet.feature.task.service.TaskService;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({ProjectController.class, TaskController.class})
class ProjectTaskFormAccessibilityWebTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProjectQueryService projectQueries;

    @MockitoBean
    private ProjectService projects;

    @MockitoBean
    private AccountService accounts;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private TaskService tasks;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @BeforeEach
    void mentorActor() {
        given(projectQueries.authenticatedActor("mentor@example.test"))
                .willReturn(new ProjectActorView(10L, "MENTOR"));
        given(clock.instant()).willReturn(Instant.parse("2026-08-15T01:00:00Z"));
        given(clock.getZone()).willReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    @Test
    void projectFieldErrorsHaveStableIdsAndInputAssociations() throws Exception {
        mvc.perform(post("/projects")
                        .with(user("mentor@example.test").roles("MENTOR"))
                        .with(csrf())
                        .param("name", " ")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "")
                        .param("initialLeaderUserId", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("aria-describedby=\"name-error\"")))
                .andExpect(content().string(containsString("id=\"name-error\"")))
                .andExpect(content().string(containsString("aria-describedby=\"endDate-error\"")))
                .andExpect(content().string(containsString("id=\"endDate-error\"")))
                .andExpect(content().string(containsString("End date is required")))
                .andExpect(content().string(containsString("aria-describedby=\"initialLeaderUserId-error\"")))
                .andExpect(content().string(containsString("id=\"initialLeaderUserId-error\"")));

        mvc.perform(post("/projects")
                        .with(user("mentor@example.test").roles("MENTOR"))
                        .with(csrf())
                        .param("name", "Project")
                        .param("startDate", "")
                        .param("endDate", "2026-08-31")
                        .param("initialLeaderUserId", "7"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("aria-describedby=\"startDate-error\"")))
                .andExpect(content().string(containsString("id=\"startDate-error\"")));
    }

    @Test
    void projectDateRangeErrorIsAssociatedWithEndDate() throws Exception {
        mvc.perform(post("/projects")
                        .with(user("mentor@example.test").roles("MENTOR"))
                        .with(csrf())
                        .param("name", "Project")
                        .param("startDate", "2026-08-31")
                        .param("endDate", "2026-08-01")
                        .param("initialLeaderUserId", "7"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("aria-describedby=\"dateRangeValid-error\"")))
                .andExpect(content().string(containsString("id=\"dateRangeValid-error\"")))
                .andExpect(content().string(containsString("End date must be on or after the Start date")));
    }

    @Test
    void taskFieldErrorsHaveStableIdsAndControlAssociations() throws Exception {
        given(tasks.assignmentChoices("leader@example.test", 10L))
                .willReturn(List.of(new TaskAssigneeChoice(7L, "Member")));

        mvc.perform(post("/projects/10/tasks")
                        .with(user("leader@example.test").roles("INTERN"))
                        .with(csrf())
                        .param("title", " ")
                        .param("dueDate", "2026-08-20"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("aria-describedby=\"title-error\"")))
                .andExpect(content().string(containsString("id=\"title-error\"")))
                .andExpect(content().string(containsString("aria-describedby=\"assigneeMembershipId-error\"")))
                .andExpect(content().string(containsString("id=\"assigneeMembershipId-error\"")));

        mvc.perform(post("/projects/10/tasks")
                        .with(user("leader@example.test").roles("INTERN"))
                        .with(csrf())
                        .param("title", "Task")
                        .param("assigneeMembershipId", "7")
                        .param("dueDate", "invalid"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("aria-describedby=\"dueDate-error\"")))
                .andExpect(content().string(containsString("id=\"dueDate-error\"")));
    }
}
