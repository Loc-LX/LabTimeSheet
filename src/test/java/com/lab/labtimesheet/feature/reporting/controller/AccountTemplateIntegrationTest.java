package com.lab.labtimesheet.feature.reporting.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.feature.account.model.dto.ActivationForm;
import com.lab.labtimesheet.feature.account.model.dto.BootstrapForm;
import com.lab.labtimesheet.feature.account.model.dto.CreateAccountForm;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@WebMvcTest(AccountTemplateIntegrationTest.TemplateController.class)
@Import(AccountTemplateIntegrationTest.TemplateController.class)
class AccountTemplateIntegrationTest {

    private final MockMvc mvc;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @Autowired
    AccountTemplateIntegrationTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void accountCreationUsesAuthenticatedShellAndRealAccountRoute() throws Exception {
        mvc.perform(get("/template-contract/accounts/new")
                        .with(user("admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"app-shell\"")))
                .andExpect(content().string(containsString("action=\"/admin/accounts\"")))
                .andExpect(content().string(containsString("href=\"/admin/accounts\"")))
                .andExpect(content().string(containsString("src=\"/assets/theme.js\"")));
    }

    @Test
    void activationUsesPublicAuthShellAndLocalAssets() throws Exception {
        mvc.perform(get("/template-contract/accounts/activate")
                        .with(user("pending@example.test")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"auth-shell\"")))
                .andExpect(content().string(containsString("action=\"/activate\"")))
                .andExpect(content().string(containsString("value=\"raw-token\"")))
                .andExpect(content().string(containsString("src=\"/assets/theme.js\"")))
                .andExpect(content().string(containsString("href=\"/assets/app.css\"")));
    }

    @Test
    void loginUsesPublicAuthShellAndPreservesAuthenticationContract() throws Exception {
        mvc.perform(get("/template-contract/accounts/login")
                        .param("error", "")
                        .with(user("anonymous-template-viewer")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"auth-shell\"")))
                .andExpect(content().string(containsString("action=\"/login\"")))
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(containsString("autocomplete=\"current-password\"")))
                .andExpect(content().string(containsString("role=\"alert\"")))
                .andExpect(content().string(containsString("src=\"/assets/theme.js\"")));
    }

    @Test
    void bootstrapUsesPublicAuthShellAndPreservesFirstAdminContract() throws Exception {
        mvc.perform(get("/template-contract/bootstrap")
                        .with(user("bootstrap-template-viewer")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"auth-shell\"")))
                .andExpect(content().string(containsString("action=\"/bootstrap\"")))
                .andExpect(content().string(containsString("name=\"email\"")))
                .andExpect(content().string(containsString("name=\"displayName\"")))
                .andExpect(content().string(containsString("autocomplete=\"new-password\"")))
                .andExpect(content().string(containsString("src=\"/assets/theme.js\"")));
    }

    @Controller
    public static class TemplateController {

        @GetMapping("/template-contract/accounts/new")
        String accountCreation(Model model) {
            model.addAttribute("accountForm", new CreateAccountForm());
            return "accounts/new";
        }

        @GetMapping("/template-contract/accounts/activate")
        String activation(Model model) {
            ActivationForm form = new ActivationForm();
            form.setToken("raw-token");
            model.addAttribute("activationForm", form);
            return "accounts/activate";
        }

        @GetMapping("/template-contract/accounts/login")
        String login() {
            return "accounts/login";
        }

        @GetMapping("/template-contract/bootstrap")
        String bootstrap(Model model) {
            model.addAttribute("bootstrapForm", new BootstrapForm());
            return "bootstrap/form";
        }
    }
}
