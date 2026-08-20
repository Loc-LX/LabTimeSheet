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
                .andExpect(content().string(containsString("name=\"taskIds\"")))
                .andExpect(content().string(containsString("type=\"radio\" name=\"recipientId\"")))
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
                .andExpect(content().string(not(containsString("name=\"recipientId\""))));
    }

    @Controller
    static class TemplateController {

        @GetMapping("/template-contract/iteration-2/workflows")
        String workflows(Model model) {
            model.addAttribute("readiness", new Readiness(false, 2, false, true));
            model.addAttribute("unfinishedTasks", List.of(
                    new TransferTask(41L, "Review report", "IN_PROGRESS"),
                    new TransferTask(42L, "Fix chart", "BLOCKED")));
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
                    new TransferTask(41L, "Review report", "IN_PROGRESS")));
            model.addAttribute("recipients", List.of(new TransferRecipient(8L, "Lan Intern")));
            model.addAttribute("projectHistory", List.of());
            model.addAttribute("adminHistory", List.of());
            return "test/iteration2-workflow-consumer";
        }
    }

    record Readiness(boolean replacementRequired, int unfinishedTaskCount,
                     boolean readyForMentorDecision, boolean canOpenTransfer) {}

    record TransferTask(long id, String title, String status) {}

    record TransferRecipient(long id, String displayName) {}

    record HistoryEvent(String type, String occurredAt, String actorName,
                        String summary, String attribution) {}

    record AdminHistoryEntry(String label, String value, String changedAt, String changedBy) {}
}
