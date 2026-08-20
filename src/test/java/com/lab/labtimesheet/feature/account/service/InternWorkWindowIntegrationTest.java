package com.lab.labtimesheet.feature.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.InternshipStatus;
import com.lab.labtimesheet.feature.account.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.account.repository.AppUserRepository;
import com.lab.labtimesheet.feature.account.repository.InternProfileRepository;
import com.lab.labtimesheet.feature.account.repository.UserActionTokenRepository;
import com.lab.labtimesheet.feature.integration.service.MailDeliveryService;
import com.lab.labtimesheet.feature.integration.model.SecurityMode;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpConnection;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpDraft;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.integration.service.SmtpProbe;
import org.springframework.beans.factory.annotation.Value;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Import({InternWorkWindowIntegrationTest.IntegrationConfiguration.class,
        InternWorkWindowIntegrationTest.MailProbeConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InternWorkWindowIntegrationTest {
    @Autowired
    private BootstrapService bootstrap;

    @Autowired
    private AccountService accounts;

    @Autowired
    private SmtpConfigurationService smtp;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private InternProfileRepository internProfiles;

    @Autowired
    private UserActionTokenRepository tokens;

    @Autowired
    private MailDeliveryService mailDelivery;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private SessionRegistry sessions;

    @Autowired
    private TransactionTemplate transactions;

    @Value("${lab.public-origin}")
    private String publicOrigin;

    @Autowired
    private RecordingSmtpProbe mail;

    @Test
    void requestedFutureDateDoesNotActivateBeforeTheRealStartDate() {
        long internId = createIntern("window-before-start@example.com", LocalDate.of(2026, 12, 31));
        clock.set(Instant.parse("2026-08-13T12:00:00Z"));

        var window = accounts.lockedInternWorkWindow(internId, LocalDate.of(2026, 8, 20));

        assertThat(window.businessDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(window.internshipStatus()).isEqualTo(InternshipStatus.NOT_STARTED);
        assertThat(window.eligible()).isFalse();
    }

    @Test
    void historicalRequestedDateAfterRealStartActivatesAndKeepsRequestedDateForEvaluation() {
        long internId = createIntern("window-after-start@example.com", LocalDate.of(2026, 12, 31));
        clock.set(Instant.parse("2026-08-14T12:00:00Z"));

        var window = accounts.lockedInternWorkWindow(internId, LocalDate.of(2026, 8, 13));

        assertThat(window.businessDate()).isEqualTo(LocalDate.of(2026, 8, 13));
        assertThat(window.internshipStatus()).isEqualTo(InternshipStatus.ACTIVE);
        assertThat(window.eligible()).isFalse();
    }

    @Test
    void firstAccessAfterTheInternshipEndDoesNotActivateNotStartedProfile() {
        long internId = createIntern("window-after-end@example.com", LocalDate.of(2026, 8, 16));
        clock.set(Instant.parse("2026-08-17T12:00:00Z"));

        assertThat(accounts.activateDueInternships()).isZero();
        var window = accounts.lockedInternWorkWindow(internId, LocalDate.of(2026, 8, 17));

        assertThat(window.businessDate()).isEqualTo(LocalDate.of(2026, 8, 17));
        assertThat(window.internshipStatus()).isEqualTo(InternshipStatus.NOT_STARTED);
        assertThat(window.eligible()).isFalse();
    }

    @Test
    void schedulerRechecksClockAfterWaitingForAccountAndProfileLocks() throws Exception {
        long internId = createIntern("scheduler-lock-wait@example.com", LocalDate.of(2026, 8, 16));
        SchedulerLockBarrier barrier = new SchedulerLockBarrier();
        AccountService scheduler = new AccountService(
                gated(users, AppUserRepository.class, barrier),
                gated(internProfiles, InternProfileRepository.class, barrier),
                tokens, mailDelivery, passwords, clock, transactions, sessions, publicOrigin);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch allowOuterCommit = new CountDownLatch(1);
        try {
            Future<?> outer = executor.submit(() -> transactions.executeWithoutResult(status -> {
                users.findForUpdateById(internId).orElseThrow();
                internProfiles.findForUpdateByUserId(internId).orElseThrow();
                barrier.outerLocksHeld.countDown();
                awaitLatch(allowOuterCommit);
            }));
            assertThat(barrier.outerLocksHeld.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Integer> scheduled = executor.submit(
                    () -> transactions.execute(status -> scheduler.activateDueInternships()));
            assertThat(barrier.candidatesSelected.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(barrier.accountLockAttempted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> scheduled.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            clock.set(Instant.parse("2026-08-17T12:00:00Z"));
            allowOuterCommit.countDown();
            outer.get(10, TimeUnit.SECONDS);
            assertThat(scheduled.get(10, TimeUnit.SECONDS)).isZero();
            InternshipStatus finalStatus = transactions.execute(status -> internProfiles.findForUpdateByUserId(internId)
                    .orElseThrow()
                    .getInternshipStatus());
            assertThat(finalStatus).isEqualTo(InternshipStatus.NOT_STARTED);
        } finally {
            allowOuterCommit.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private long createIntern(String email, LocalDate endDate) {
        bootstrap.bootstrap("admin@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("admin@example.com");
        long draftId = smtp.saveDraft(adminId,
                new SmtpDraft("mailpit", 1025, SecurityMode.NONE, null, null, "admin@example.com", "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "admin@example.com");
        smtp.activate(draftId, adminId);

        var creation = accounts.create(new CreateAccountCommand(
                email, "Intern", GlobalRole.INTERN, "STU-WINDOW",
                LocalDate.of(2026, 8, 14), endDate), adminId);
        assertThat(accounts.activate(mail.token(), "a secure intern password")).isTrue();
        return creation.userId();
    }

    @Autowired
    private MutableClock clock;

    @TestConfiguration(proxyBeanMethods = false)
    static class IntegrationConfiguration {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:18.4"));
        }

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-08-14T00:00:00Z"));
        }
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
        private String token;

        @Override
        public void send(SmtpConnection connection, String recipient, String subject, String body) {
            int marker = body.indexOf("token=");
            if (marker >= 0) {
                token = body.substring(marker + "token=".length()).trim();
            }
        }

        String token() {
            return token;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T gated(T delegate, Class<T> contract, SchedulerLockBarrier barrier) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getName().equals("findForUpdateById")) {
                barrier.accountLockAttempted.countDown();
            }
            Object result;
            try {
                result = method.invoke(delegate, args);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
            if (method.getName().equals("findDueUserIds")) {
                barrier.candidatesSelected.countDown();
            }
            return result;
        };
        return (T) Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[] {contract}, handler);
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for the scheduler contention transaction");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for the scheduler contention transaction", exception);
        }
    }

    private static final class SchedulerLockBarrier {
        private final CountDownLatch outerLocksHeld = new CountDownLatch(1);
        private final CountDownLatch candidatesSelected = new CountDownLatch(1);
        private final CountDownLatch accountLockAttempted = new CountDownLatch(1);
    }

    static final class MutableClock extends Clock {
        private volatile Instant current;

        MutableClock(Instant initial) {
            current = initial;
        }

        void set(Instant instant) {
            current = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
