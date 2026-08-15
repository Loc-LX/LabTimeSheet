package com.lab.labtimesheet.feature.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import com.lab.labtimesheet.LabtimesheetApplication;
import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.repository.AppUserRepository;
import com.lab.labtimesheet.feature.account.repository.SystemStateRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BootstrapIntegrationTest {

    @Autowired
    private BootstrapService bootstrapService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private SystemStateRepository systemStates;

    @Autowired
    private DataSource dataSource;

    @Test
    void rootGuidesFreshInstallToBootstrapWhileOtherRoutesRemainHidden() throws Exception {
        mockMvc.perform(get("/bootstrap")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/bootstrap"));
        mockMvc.perform(get("/dashboard")).andExpect(status().isNotFound());

        bootstrapService.bootstrap("admin@example.com", "Admin", "correct horse battery staple");
        mockMvc.perform(get("/bootstrap")).andExpect(status().isNotFound());
    }

    @Test
    void concurrentBootstrapCreatesExactlyOneAdminAndPermanentlyCloses() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<BootstrapService.BootstrapOutcome>> futures = new ArrayList<>();

        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                int suffix = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return bootstrapService.bootstrap(
                            "admin" + suffix + "@example.com", "First Admin", "correct horse battery staple");
                }));
            }
            ready.await();
            start.countDown();
        }

        assertThat(futures).extracting(future -> future.get()).containsExactlyInAnyOrder(
                BootstrapService.BootstrapOutcome.CREATED, BootstrapService.BootstrapOutcome.ALREADY_INITIALIZED);
        assertThat(users.count()).isEqualTo(1);
        assertThat(users.countByGlobalRoleAndAccountStatus(GlobalRole.ADMIN, AccountStatus.ACTIVE)).isEqualTo(1);
        assertThat(bootstrapService.bootstrap(
                "another@example.com", "Another", "correct horse battery staple"))
                .isEqualTo(BootstrapService.BootstrapOutcome.ALREADY_INITIALIZED);
        assertThat(systemStates.findById((short) 1).orElseThrow().isInitialized()).isTrue();
    }

    @Test
    void bootstrapRemainsClosedInAnIndependentApplicationContext() {
        bootstrapService.bootstrap("admin@example.com", "First Admin", "correct horse battery staple");
        HikariDataSource currentDataSource = (HikariDataSource) dataSource;

        try (var restarted = new SpringApplicationBuilder(LabtimesheetApplication.class)
                .profiles("test")
                .web(WebApplicationType.SERVLET)
                .properties(
                        "server.port=0",
                        "spring.main.register-shutdown-hook=false",
                        "spring.datasource.url=" + currentDataSource.getJdbcUrl(),
                        "spring.datasource.username=" + currentDataSource.getUsername(),
                        "spring.datasource.password=" + currentDataSource.getPassword())
                .run()) {
            BootstrapService restartedBootstrap = restarted.getBean(BootstrapService.class);
            assertThat(restartedBootstrap.isInitialized()).isTrue();
            assertThat(restartedBootstrap.bootstrap(
                    "another@example.com", "Another", "correct horse battery staple"))
                    .isEqualTo(BootstrapService.BootstrapOutcome.ALREADY_INITIALIZED);
        }
    }

    @Test
    void exposesIdentityAndDateAwareInternEligibilityWithoutPersistenceTypes() {
        bootstrapService.bootstrap("admin@example.com", "First Admin", "correct horse battery staple");

        var identityByEmail = accountService.requireIdentityByEmail(" ADMIN@EXAMPLE.COM ");
        assertThat(identityByEmail.email()).isEqualTo("admin@example.com");
        assertThat(identityByEmail.displayName()).isEqualTo("First Admin");
        assertThat(identityByEmail.role()).isEqualTo(GlobalRole.ADMIN);
        assertThat(identityByEmail.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(accountService.requireIdentityById(identityByEmail.id())).isEqualTo(identityByEmail);
        assertThat(accountService.isEligibleIntern(identityByEmail.id())).isFalse();
        assertThat(accountService.isEligibleIntern(identityByEmail.id(), LocalDate.of(2026, 8, 14))).isFalse();
    }
}
