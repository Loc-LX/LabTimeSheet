package com.lab.labtimesheet.feature.internship.service;

import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.identity.service.BootstrapService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.internship.model.InternshipStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountCreation;
import com.lab.labtimesheet.feature.identity.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.internship.model.dto.InternshipLifecycleGuard;
import com.lab.labtimesheet.feature.internship.model.dto.LockedAccountMutationEligibility;
import com.lab.labtimesheet.feature.identity.repository.AppUserRepository;
import com.lab.labtimesheet.feature.internship.repository.InternProfileRepository;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.platform.model.SecurityMode;
import com.lab.labtimesheet.platform.model.dto.SmtpConnection;
import com.lab.labtimesheet.platform.model.dto.SmtpDraft;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import com.lab.labtimesheet.platform.service.SmtpProbe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL proof for the Account-owned multi-user mutation eligibility boundary. */
@Import({TestcontainersConfiguration.class, InternMutationEligibilityIntegrationTest.MailProbeConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InternMutationEligibilityIntegrationTest {
    @Autowired
    private BootstrapService bootstrap;

    @Autowired
    private AccountService accounts;

    @Autowired
    private InternshipService internships;

    @Autowired
    private SmtpConfigurationService smtp;

    @Autowired
    private RecordingSmtpProbe mail;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private InternProfileRepository internProfiles;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    void locksRequestedAccountsInAscendingOrderAndMarksCompletedInternsIneligible() {
        bootstrap.bootstrap("admin@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("admin@example.com");
        long draftId = smtp.saveDraft(adminId,
                new SmtpDraft("mailpit", 1025, SecurityMode.NONE, null, null, "admin@example.com", "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "admin@example.com");
        smtp.activate(draftId, adminId);

        var mentor = internships.create(new CreateAccountCommand(
                "mentor@example.com", "Mentor", GlobalRole.MENTOR, null, null, null), adminId);
        assertThat(accounts.activate(mail.tokenFor("mentor@example.com"), "a secure mentor password")).isTrue();
        var first = createIntern(adminId, "first@example.com", "STU-FIRST");
        var second = createIntern(adminId, "second@example.com", "STU-SECOND");
        assertThat(accounts.activate(mail.tokenFor("second@example.com"), "a secure second password")).isTrue();
        assertThat(accounts.activate(mail.tokenFor("first@example.com"), "a secure first password")).isTrue();
        assertThat(internships.activateDueInternships()).isEqualTo(2);
        internships.completeInternship(second.userId(), adminId, new InternshipLifecycleGuard(false, 0));

        List<LockedAccountMutationEligibility> result = internships.lockedAccountMutationEligibility(
                List.of(second.userId(), mentor.userId(), first.userId(), second.userId()));

        assertThat(result).extracting(LockedAccountMutationEligibility::userId)
                .containsExactly(mentor.userId(), first.userId(), second.userId());
        assertThat(result.getFirst().role()).isEqualTo(GlobalRole.MENTOR);
        assertThat(result.getFirst().internshipStatus()).isEmpty();
        assertThat(result.get(1).eligibleForProjectMutation()).isTrue();
        assertThat(result.getLast().internshipStatus()).contains(InternshipStatus.COMPLETED);
        assertThat(result.getLast().eligibleForProjectMutation()).isFalse();
    }

    @Test
    void dueNotStartedInternIsActivatedBeforeMutationEligibilitySnapshot() {
        bootstrap.bootstrap("due-admin@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("due-admin@example.com");
        configureSmtp(adminId, "due-admin@example.com");

        var intern = createIntern(adminId, "due-intern@example.com", "STU-DUE");
        assertThat(accounts.activate(mail.tokenFor("due-intern@example.com"), "a secure due password")).isTrue();

        List<LockedAccountMutationEligibility> result = internships.lockedAccountMutationEligibility(
                List.of(intern.userId()));

        assertThat(result).singleElement()
                .satisfies(snapshot -> {
                    assertThat(snapshot.internshipStatus()).contains(InternshipStatus.ACTIVE);
                    assertThat(snapshot.eligibleForProjectMutation()).isTrue();
                });
    }

    @Test
    void outerMutationEligibilityRetainsAccountAndInternProfileLocksUntilCommit() throws Exception {
        bootstrap.bootstrap("retention-admin@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("retention-admin@example.com");
        configureSmtp(adminId, "retention-admin@example.com");

        var intern = createIntern(adminId, "retention-intern@example.com", "STU-RETENTION");
        assertThat(accounts.activate(mail.tokenFor("retention-intern@example.com"), "a secure retention password"))
                .isTrue();
        assertThat(internships.activateDueInternships()).isEqualTo(1);

        CountDownLatch methodReturned = new CountDownLatch(1);
        CountDownLatch allowOuterCommit = new CountDownLatch(1);
        CountDownLatch accountAttemptStarted = new CountDownLatch(1);
        CountDownLatch profileAttemptStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<?> outer = executor.submit(() -> transactions.executeWithoutResult(status -> {
                internships.lockedAccountMutationEligibility(List.of(intern.userId()));
                methodReturned.countDown();
                awaitLatch(allowOuterCommit);
            }));
            assertThat(methodReturned.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Long> accountProbe = executor.submit(() -> transactions.execute(status -> {
                accountAttemptStarted.countDown();
                return users.findForUpdateById(intern.userId()).orElseThrow().getId();
            }));
            Future<Long> profileProbe = executor.submit(() -> transactions.execute(status -> {
                profileAttemptStarted.countDown();
                return internProfiles.findForUpdateByUserId(intern.userId()).map(profile -> intern.userId()).orElseThrow();
            }));

            assertThat(accountAttemptStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(profileAttemptStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> accountProbe.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            assertThatThrownBy(() -> profileProbe.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            allowOuterCommit.countDown();
            outer.get(10, TimeUnit.SECONDS);
            assertThat(accountProbe.get(10, TimeUnit.SECONDS)).isEqualTo(intern.userId());
            assertThat(profileProbe.get(10, TimeUnit.SECONDS)).isEqualTo(intern.userId());
        } finally {
            allowOuterCommit.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void configureSmtp(long adminId, String adminEmail) {
        long draftId = smtp.saveDraft(adminId,
                new SmtpDraft("mailpit", 1025, SecurityMode.NONE, null, null, adminEmail, "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, adminEmail);
        smtp.activate(draftId, adminId);
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for the outer mutation transaction");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for the outer mutation transaction", exception);
        }
    }

    private AccountCreation createIntern(long adminId, String email, String studentCode) {
        return internships.create(new CreateAccountCommand(
                email, email.substring(0, email.indexOf('@')), GlobalRole.INTERN, studentCode,
                LocalDate.of(2026, 8, 14), LocalDate.of(2026, 12, 31)), adminId);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MailProbeConfiguration {
        @Bean
        @Primary
        RecordingSmtpProbe recordingSmtpProbe() {
            return new RecordingSmtpProbe();
        }
    }

    static final class RecordingSmtpProbe implements SmtpProbe {
        private final java.util.Map<String, String> tokens = new java.util.HashMap<>();

        @Override
        public void send(SmtpConnection connection, String recipient, String subject, String body) {
            int marker = body.indexOf("token=");
            if (marker >= 0) {
                tokens.put(recipient, body.substring(marker + "token=".length()).trim());
            }
        }

        String tokenFor(String recipient) {
            return tokens.get(recipient);
        }
    }
}
