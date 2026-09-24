package com.lab.labtimesheet.feature.reporting.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.lab.labtimesheet.platform.service.SmtpConfigurationService;

/**
 * Production-shaped contract for the shared Iteration 2 error, drawer, and chart fragments.
 *
 * <p>The test renders the fragments through Thymeleaf rather than asserting source text, so
 * accessible names, field links, and the equivalent chart table are verified at the MVC boundary.
 */
@WebMvcTest(Iteration2ComponentsWebTest.TemplateController.class)
@Import(Iteration2ComponentsWebTest.TemplateController.class)
class Iteration2ComponentsWebTest {

    private final MockMvc mvc;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @Autowired
    Iteration2ComponentsWebTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    @WithMockUser(username = "mentor@example.test", roles = "MENTOR")
    void sharedIteration2FragmentsExposeKeyboardSafeErrorsDrawersAndChartAlternative() throws Exception {
        mvc.perform(get("/template-contract/iteration-2/components"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"form-errors\"")))
                .andExpect(content().string(containsString("href=\"#displayName\"")))
                .andExpect(content().string(containsString("id=\"transfer-drawer\"")))
                .andExpect(content().string(containsString("aria-labelledby=\"transfer-drawer-title\"")))
                .andExpect(content().string(containsString("data-drawer-close")))
                .andExpect(content().string(containsString("data-report-chart")))
                .andExpect(content().string(containsString("aria-labelledby=\"trend-chart-title\"")))
                .andExpect(content().string(containsString("Daily attendance rate")))
                .andExpect(content().string(containsString("08/08/2026")))
                .andExpect(content().string(containsString("92.0%")));
    }

    @Controller
    static class TemplateController {

        @GetMapping("/template-contract/iteration-2/components")
        String components(Model model) {
            model.addAttribute("issues", List.of(
                    new FieldIssue("displayName", "Display name is required"),
                    new FieldIssue(null, "Choose one recipient")));
            model.addAttribute("points", List.of(
                    new ChartPoint("08/08/2026", "92.0%"),
                    new ChartPoint("09/08/2026", "100.0%")));
            return "test/iteration2-components-consumer";
        }
    }

    record FieldIssue(String field, String message) {}

    record ChartPoint(String label, String value) {}
}
