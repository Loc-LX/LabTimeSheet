package com.lab.labtimesheet.feature.reporting.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@WebMvcTest(SharedErrorTemplateWebTest.ErrorTemplateController.class)
@Import(SharedErrorTemplateWebTest.ErrorTemplateController.class)
class SharedErrorTemplateWebTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @Test
    @WithMockUser(username = "intern@example.test", roles = "INTERN")
    void notFoundPageUsesSharedShellWithoutDisclosingRecordDetails() throws Exception {
        mvc.perform(get("/template-contract/error/404"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("class=\"app-shell\"")))
                .andExpect(content().string(containsString("Page not found")))
                .andExpect(content().string(not(containsString("secret Project"))));
    }

    @Test
    @WithMockUser(username = "mentor@example.test", roles = "MENTOR")
    void conflictPageUsesSharedShellWithoutRenderingExceptionDetails() throws Exception {
        mvc.perform(get("/template-contract/error/409"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("class=\"app-shell\"")))
                .andExpect(content().string(containsString("Request could not be completed")))
                .andExpect(content().string(not(containsString("internal lifecycle detail"))));
    }

    @Controller
    static class ErrorTemplateController {

        @GetMapping("/template-contract/error/404")
        @ResponseStatus(HttpStatus.NOT_FOUND)
        String notFound(Model model) {
            model.addAttribute("errorStatus", 404);
            model.addAttribute("errorTitle", "Page not found");
            model.addAttribute("errorMessage", "The requested resource is unavailable or you may not have access.");
            return "error/generic";
        }

        @GetMapping("/template-contract/error/409")
        @ResponseStatus(HttpStatus.CONFLICT)
        String conflict(Model model) {
            model.addAttribute("errorStatus", 409);
            model.addAttribute("errorTitle", "Request could not be completed");
            model.addAttribute("errorMessage", "The request conflicts with its current state. Review and try again.");
            return "error/generic";
        }
    }
}
