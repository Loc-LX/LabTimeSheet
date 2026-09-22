package com.lab.labtimesheet.feature.reporting.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Production-shaped MVC contract for Iteration 2 exit and retained-history fragments. */
@WebMvcTest(Iteration2WorkflowFragmentsWebTest.TemplateController.class)
@Import(Iteration2WorkflowFragmentsWebTest.TemplateController.class)
class Iteration2WorkflowFragmentsWebTest {

    private final MockMvc mvc;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @Autowired
    Iteration2WorkflowFragmentsWebTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    @WithMockUser(username = "admin@example.test", roles = "ADMIN")
    void rendersReadinessLeaderTransferDrawerProjectHistoryAndRedactedAdminHistoryShape() throws Exception {
        mvc.perform(get("/template-contract/iteration-2/workflows"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Exit readiness")))
                .andExpect(content().string(containsString("2 unfinished Tasks remain")))
                .andExpect(content().string(containsString("data-drawer-open=\"transfer-drawer\"")))
                .andExpect(content().string(containsString("data-transfer-confirm")))
                .andExpect(content().string(containsString("name=\"taskIds\"")))
                .andExpect(content().string(containsString("data-task-version-for=\"41\"")))
                .andExpect(content().string(containsString("data-task-version-for=\"42\"")))
                .andExpect(content().string(containsString("name=\"taskVersions\" value=\"41:3\" disabled")))
                .andExpect(content().string(containsString("name=\"taskVersions\" value=\"42:5\" disabled")))
                .andExpect(content().string(containsString("name=\"sourceMembershipId\" value=\"12\"")))
                .andExpect(content().string(containsString("type=\"radio\" name=\"recipientMembershipId\"")))
                .andExpect(content().string(containsString("Project History")))
                .andExpect(content().string(containsString("Completed Task")))
                .andExpect(content().string(containsString("Admin setting History")))
                .andExpect(content().string(containsString("Secret values are never displayed.")))
                .andExpect(content().string(containsString("mailpit")))
                .andExpect(content().string(not(containsString("super-secret"))));
    }

    @Test
    @WithMockUser(username = "member@example.test", roles = "INTERN")
    void suppressesLeaderTransferControlsWhenProducerReadinessDeniesTransfer() throws Exception {
        mvc.perform(get("/template-contract/iteration-2/workflows/transfer-denied"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Exit readiness")))
                .andExpect(content().string(not(containsString("data-drawer-open=\"transfer-drawer\""))))
                .andExpect(content().string(not(containsString("name=\"taskIds\""))))
                .andExpect(content().string(not(containsString("name=\"recipientMembershipId\""))));
    }

    @Controller
    static class TemplateController {

        @GetMapping("/template-contract/iteration-2/workflows")
        String workflows(Model model) {
            model.addAttribute("readiness", new Readiness(false, 2, false, true));
            model.addAttribute("unfinishedTasks", List.of(
                    new TransferTask(41L, "Review report", "IN_PROGRESS", 3L, List.of("one retained log")),
                    new TransferTask(42L, "Fix chart", "BLOCKED", 5L, List.of())));
            model.addAttribute("recipients", List.of(new TransferRecipient(8L, "Lan Intern")));
            model.addAttribute("projectHistory", List.of(new HistoryEvent(
                    "Task completed", "20/08/2026 09:00", "Mai Intern", "Completed Task: Review report", "Mai Intern")));
            model.addAttribute("adminHistory", List.of(new AdminHistoryEntry(
                    "SMTP host", "mailpit", "20/08/2026 08:00", "Admin User")));
            return "test/iteration2-workflow-consumer";
        }

        @GetMapping("/template-contract/iteration-2/workflows/transfer-denied")
        String transferDenied(Model model) {
            model.addAttribute("readiness", new Readiness(false, 2, false, false));
            model.addAttribute("unfinishedTasks", List.of(
                    new TransferTask(41L, "Review report", "IN_PROGRESS", 3L, List.of("one retained log"))));
            model.addAttribute("recipients", List.of(new TransferRecipient(8L, "Lan Intern")));
            model.addAttribute("projectHistory", List.of());
            model.addAttribute("adminHistory", List.of());
            return "test/iteration2-workflow-consumer";
        }
    }

    record Readiness(boolean replacementRequired, int unfinishedTaskCount,
                     boolean readyForMentorDecision, boolean canOpenTransfer) {}

    /**
     * Stand-in for the retained unfinished Task the transfer drawer renders.
     *
     * <p>{@code workLogs} is read only through {@code #lists.isEmpty} by
     * {@code fragments/workflows}, which shows the Remaining effort forecast controls for a worked
     * Task and hides them for an unworked one, so the element type does not matter and the
     * emptiness does. The property was absent until 13 September 2026 and the fragment could not
     * render, which is what {@code TSK-022} makes the drawer depend on.
     *
     * @param id Task identifier
     * @param title Task title
     * @param status Task status name
     * @param version optimistic version the drawer echoes back
     * @param workLogs retained work logs; non-empty means the Task has been worked
     */
    record TransferTask(long id, String title, String status, long version, List<Object> workLogs) {}

    record TransferRecipient(long membershipId, String displayName) {}

    record HistoryEvent(String type, String occurredAt, String actorName,
                        String summary, String attribution) {}

    record AdminHistoryEntry(String label, String value, String changedAt, String changedBy) {}
}
