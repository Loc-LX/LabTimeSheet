package com.lab.labtimesheet.platform;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.platform.authorization.AuthorizationCatalogue;
import com.lab.labtimesheet.platform.authorization.AuthorizationPolicy;
import com.lab.labtimesheet.platform.authorization.WithdrawnAuthorizationCatalogues;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;

/** Integration evidence for the single withdrawn Admin Attendance report cell. */
@AutoConfigureMockMvc
class AuthorizationWithdrawalIntegrationTest extends AuthorizationMatrixIntegrationTest {

    private static final String REPORT_CAPABILITY = "View and export the Attendance report (`RPT-004`)";

    @TestBean(methodName = "withdrawnAuthorizationPolicy")
    private AuthorizationPolicy authorizationPolicy;

    static AuthorizationPolicy withdrawnAuthorizationPolicy() {
        AuthorizationCatalogue catalogue = WithdrawnAuthorizationCatalogues.attendanceReportAdminWithdrawn();
        return new AuthorizationPolicy(catalogue);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Override
    String matrixResultLabel() {
        return "B-04";
    }

    @Override
    Map<Cell, Outcome> expectedMismatches() {
        return Map.of(new Cell(REPORT_CAPABILITY, "Admin"), new Outcome("DENIED", "B-02"));
    }

    /**
     * Protects {@code AUTH-012}, {@code AC-AUTH-011}, and {@code RPT-004}. Observable break: the withdrawn Admin
     * permission could still open or export an Attendance report, or a Mentor's granted no-target page could break.
     * The expected responses are 403 for both Admin endpoints and 200 for the active Mentor page.
     */
    @Test
    void withdrawnAttendanceReportCapabilityDeniesAdminAndRetainsMentorAccess() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String adminEmail = "withdrawn-admin-" + suffix + "@example.test";
        String mentorEmail = "withdrawn-mentor-" + suffix + "@example.test";
        long adminId = insertActiveUser(adminEmail, "ADMIN");
        long mentorId = insertActiveUser(mentorEmail, "MENTOR");
        jdbc.sql("""
                update system_state
                set initialized = true, initialized_at = current_timestamp, bootstrap_admin_id = :admin
                where singleton_id = 1
                """).param("admin", adminId).update();
        try {
            mvc.perform(get("/dashboard").with(user(adminEmail).roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                            .string(not(containsString("/reports/attendance"))));
            mvc.perform(get("/dashboard").with(user(mentorEmail).roles("MENTOR")))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                            .string(containsString("href=\"/reports/attendance\"")));
            mvc.perform(get("/reports/attendance").with(user(adminEmail).roles("ADMIN")))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/reports/attendance.xlsx").with(user(adminEmail).roles("ADMIN")))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/reports/attendance").with(user(mentorEmail).roles("MENTOR")))
                    .andExpect(status().isOk());
        } finally {
            jdbc.sql("""
                    update system_state
                    set initialized = false, initialized_at = null, bootstrap_admin_id = null
                    where singleton_id = 1
                    """).update();
            jdbc.sql("delete from app_users where id in (:admin, :mentor)")
                    .param("admin", adminId).param("mentor", mentorId).update();
        }
    }

    private long insertActiveUser(String email, String role) {
        return jdbc.sql("""
                insert into app_users (email, display_name, password_hash, global_role, account_status, activated_at)
                values (:email, :email, 'test-hash', :role, 'ACTIVE', current_timestamp) returning id
                """).param("email", email).param("role", role).query(Long.class).single();
    }
}
