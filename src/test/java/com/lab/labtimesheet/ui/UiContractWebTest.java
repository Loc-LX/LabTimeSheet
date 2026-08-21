package com.lab.labtimesheet.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;

@WebMvcTest(UiContractWebTest.ContractController.class)
@Import(UiContractWebTest.ContractController.class)
class UiContractWebTest {

    private final MockMvc mvc;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @Autowired
    UiContractWebTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    @WithMockUser(username = "mentor@example.test", roles = "MENTOR")
    void mentorShellRendersOnlyReachableAuthorizedNavigation() throws Exception {
        MvcResult result = mvc.perform(get("/ui-contract"))
                .andExpect(status().isOk())
                .andReturn();

        String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("Lab Timesheet"));
        assertTrue(html.contains("Owned Projects"));
        assertTrue(html.contains("Theme"));
        assertTrue(html.contains("Logout"));
        assertFalse(html.contains("Accounts"));
        assertFalse(html.contains("My attendance"));
        assertFalse(html.contains("Intern attendance"));
        assertFalse(html.contains("href=\"/attendance\""));
        assertFalse(html.contains("href=\"/profile\""));
        assertTrue(html.contains("href=\"/notifications\""));
        assertTrue(html.contains("aria-label=\"Notifications\""));
        assertTrue(html.indexOf("/assets/theme.js") < html.indexOf("/assets/app.css"));
        assertTrue(html.contains("rel=\"icon\" href=\"/assets/icons.svg\""));
        assertTrue(html.contains("href=\"/assets/icons.svg#panel-left\""));
    }

    @Test
    @WithMockUser(username = "intern@example.test", roles = "INTERN")
    void internShellLinksToTheReachableOwnAttendanceRoute() throws Exception {
        String html = mvc.perform(get("/ui-contract"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(html.contains("href=\"/attendance\""));
        assertFalse(html.contains("href=\"/attendance/me\""));
        assertFalse(html.contains("href=\"/profile\""));
        assertTrue(html.contains("href=\"/notifications\""));
        assertTrue(html.contains("aria-label=\"Notifications\""));
    }

    @Test
    void compiledAssetsAreLocalAndContainOnlyTheSelectedIconSprite() throws Exception {
        ClassPathResource css = new ClassPathResource("static/assets/app.css");
        ClassPathResource theme = new ClassPathResource("static/assets/theme.js");
        ClassPathResource script = new ClassPathResource("static/assets/app.js");
        ClassPathResource sprite = new ClassPathResource("static/assets/icons.svg");

        assertTrue(css.exists());
        assertTrue(theme.exists());
        assertTrue(script.exists());
        assertTrue(sprite.exists());

        String icons = sprite.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(icons.contains("id=\"panel-left\""));
        assertTrue(icons.contains("id=\"circle-user-round\""));
        assertFalse(icons.contains("<script"));
        assertFalse(icons.contains("href=\"http"));
        assertFalse(icons.contains("<image"));

        String themeBootstrap = theme.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(themeBootstrap.contains("localStorage.getItem('labtimesheet-theme')"));
        assertTrue(themeBootstrap.contains("matchMedia('(prefers-color-scheme: dark)')"));
    }

    @Test
    @WithMockUser(username = "admin@example.test", roles = "ADMIN")
    void collapsedSidebarExposesStateAndKeyboardVisibleControlNames() throws Exception {
        String html = mvc.perform(get("/ui-contract"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String script = new ClassPathResource("static/assets/app.js")
                .getContentAsString(StandardCharsets.UTF_8);
        String css = new ClassPathResource("static/assets/app.css")
                .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(html.contains("data-sidebar-toggle aria-expanded=\"true\""));
        assertTrue(html.contains("data-tooltip=\"Overview\""));
        assertTrue(html.contains("data-tooltip=\"Accounts\""));
        assertTrue(html.contains("data-tooltip=\"Global calendar\""));
        assertTrue(html.contains("data-tooltip=\"Logout\""));
        assertTrue(html.contains("title=\"Theme preference\""));
        assertTrue(script.contains("setAttribute('aria-expanded', String(!collapsed))"));
        assertTrue(css.contains("content:attr(data-tooltip)"));
    }

    @Test
    void themeTokensMeetTextFocusAndMeaningfulBoundaryContrast() throws Exception {
        String css = new ClassPathResource("static/assets/app.css")
                .getContentAsString(StandardCharsets.UTF_8);
        String light = section(css, ":root\\{color-scheme:light;([^}]*)}");
        String dark = section(css, ":root\\[data-theme=dark]\\{color-scheme:dark;([^}]*)}");

        assertContrast(light, "ink", "canvas", 4.5);
        assertContrast(light, "muted", "panel", 4.5);
        assertContrast(light, "subtle", "sidebar", 4.5);
        assertContrast(light, "border", "canvas", 3.0);
        assertContrast(light, "border-strong", "panel", 3.0);
        assertContrast(light, "focus", "canvas", 3.0);

        assertContrast(dark, "ink", "canvas", 4.5);
        assertContrast(dark, "muted", "panel", 4.5);
        assertContrast(dark, "subtle", "sidebar", 4.5);
        assertContrast(dark, "border", "panel", 3.0);
        assertContrast(dark, "border-strong", "panel", 3.0);
        assertContrast(dark, "focus", "panel", 3.0);
    }

    @Test
    @WithMockUser(username = "admin@example.test", roles = "ADMIN")
    void sharedComponentsExposeAccessibleFormsStatusAndEmptyState() throws Exception {
        String html = mvc.perform(get("/ui-components"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(html.contains("for=\"displayName\""));
        assertTrue(html.contains("id=\"displayName\""));
        assertTrue(html.contains("role=\"alert\""));
        assertTrue(html.contains("No records yet"));
        assertTrue(html.contains("Status: Active"));
        assertTrue(html.contains("aria-describedby=\"confirm-dialog-description\""));
    }

    @Controller
    public static class ContractController {

        @GetMapping("/ui-contract")
        String contract() {
            return "test/layout-consumer";
        }

        @GetMapping("/ui-components")
        String components() {
            return "test/components-consumer";
        }
    }

    private static String section(String css, String expression) {
        Matcher matcher = Pattern.compile(expression).matcher(css);
        assertTrue(matcher.find(), () -> "Missing CSS token section: " + expression);
        return matcher.group(1);
    }

    private static void assertContrast(String section, String foreground, String background, double minimum) {
        double ratio = contrast(color(section, foreground), color(section, background));
        assertTrue(ratio >= minimum,
                () -> foreground + " / " + background + " contrast " + ratio + " is below " + minimum);
    }

    private static String color(String section, String name) {
        Matcher matcher = Pattern.compile("--" + Pattern.quote(name) + ":(#[0-9a-fA-F]{3}(?:[0-9a-fA-F]{3})?)")
                .matcher(section);
        assertTrue(matcher.find(), () -> "Missing CSS color token: " + name);
        String value = matcher.group(1);
        if (value.length() == 4) {
            return "#" + value.charAt(1) + value.charAt(1)
                    + value.charAt(2) + value.charAt(2)
                    + value.charAt(3) + value.charAt(3);
        }
        return value;
    }

    private static double contrast(String first, String second) {
        double lighter = Math.max(luminance(first), luminance(second));
        double darker = Math.min(luminance(first), luminance(second));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(String hex) {
        double red = linear(Integer.parseInt(hex.substring(1, 3), 16) / 255.0);
        double green = linear(Integer.parseInt(hex.substring(3, 5), 16) / 255.0);
        double blue = linear(Integer.parseInt(hex.substring(5, 7), 16) / 255.0);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double linear(double component) {
        return component <= 0.04045
                ? component / 12.92
                : Math.pow((component + 0.055) / 1.055, 2.4);
    }
}
