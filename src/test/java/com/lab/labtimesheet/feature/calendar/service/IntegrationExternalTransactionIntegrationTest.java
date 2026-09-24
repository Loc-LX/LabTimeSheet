package com.lab.labtimesheet.feature.calendar.service;

import com.lab.labtimesheet.feature.calendar.service.HolidayApiConfigurationService;
import com.lab.labtimesheet.feature.calendar.service.HolidayApiHttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.identity.service.BootstrapService;
import com.lab.labtimesheet.feature.calendar.model.dto.HolidayApiCandidate;
import com.lab.labtimesheet.feature.calendar.model.dto.HolidayApiDraft;
import com.lab.labtimesheet.feature.calendar.model.entity.HolidayApiConfiguration;
import com.lab.labtimesheet.feature.calendar.repository.HolidayApiConfigurationRepository;
import com.lab.labtimesheet.platform.model.SecurityMode;
import com.lab.labtimesheet.platform.model.dto.SmtpConnection;
import com.lab.labtimesheet.platform.model.dto.SmtpDraft;
import com.lab.labtimesheet.platform.model.entity.SmtpConfiguration;
import com.lab.labtimesheet.platform.repository.SmtpConfigurationRepository;
import com.lab.labtimesheet.platform.service.SecretCipher;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import com.lab.labtimesheet.platform.service.SmtpProbe;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * PostgreSQL proof that integration provider I/O is outside Spring transactions and cannot overwrite a newer draft.
 */
@Import({TestcontainersConfiguration.class, IntegrationExternalTransactionIntegrationTest.ExternalClientConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class IntegrationExternalTransactionIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired
    private BootstrapService bootstrap;

    @org.springframework.beans.factory.annotation.Autowired
    private AccountService accounts;

    @org.springframework.beans.factory.annotation.Autowired
    private SmtpConfigurationService smtp;

    @org.springframework.beans.factory.annotation.Autowired
    private BlockingSmtpProbe smtpProbe;

    @org.springframework.beans.factory.annotation.Autowired
    private SmtpConfigurationRepository smtpConfigurations;

    @org.springframework.beans.factory.annotation.Autowired
    private HolidayApiConfigurationService holidayApi;

    @org.springframework.beans.factory.annotation.Autowired
    private BlockingHolidayApiClient holidayClient;

    @org.springframework.beans.factory.annotation.Autowired
    private HolidayApiConfigurationRepository holidayConfigurations;

    @org.springframework.beans.factory.annotation.Autowired
    private SecretCipher secrets;

    @Test
    void smtpLateProbeCannotOverwriteNewerEncryptedDraftAndSeesNoTransaction() throws Exception {
        bootstrap.bootstrap("smtp-concurrency-admin@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("smtp-concurrency-admin@example.com");
        long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                "mailpit", 1025, SecurityMode.NONE, "smtp-user", "old-smtp-password",
                "smtp-concurrency-admin@example.com", "Lab"));
        smtpProbe.arm();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var lateTest = executor.submit(
                    () -> smtp.testDraft(draftId, adminId, "smtp-concurrency-admin@example.com"));
            smtpProbe.awaitStarted();

            smtp.saveDraft(adminId, new SmtpDraft(
                    "mailpit", 1025, SecurityMode.NONE, "smtp-user", "new-smtp-password",
                    "smtp-concurrency-admin@example.com", "Lab"));
            boolean transactionActive = smtpProbe.transactionActive();
            smtpProbe.release();

            assertOptimisticFailure(lateTest);
            assertThat(transactionActive).isFalse();
            SmtpConfiguration current = smtpConfigurations.findById(draftId).orElseThrow();
            assertThat(current.getTestedAt()).isNull();
            assertThat(secrets.decrypt(current.getPasswordCiphertext(), current.getPasswordNonce()))
                    .isEqualTo("new-smtp-password");
        } finally {
            smtpProbe.release();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void holidayApiLateProbeCannotOverwriteNewerEncryptedDraftAndSeesNoTransaction() throws Exception {
        bootstrap.bootstrap("holiday-concurrency-admin@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("holiday-concurrency-admin@example.com");
        long draftId = holidayApi.saveDraft(adminId, new HolidayApiDraft("old-holiday-api-key"));
        holidayClient.arm();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var lateTest = executor.submit(() -> holidayApi.testDraft(draftId, adminId, 2026));
            holidayClient.awaitStarted();

            holidayApi.saveDraft(adminId, new HolidayApiDraft("new-holiday-api-key"));
            boolean transactionActive = holidayClient.transactionActive();
            holidayClient.release();

            assertOptimisticFailure(lateTest);
            assertThat(transactionActive).isFalse();
            HolidayApiConfiguration current = holidayConfigurations.findById(draftId).orElseThrow();
            assertThat(current.getTestedAt()).isNull();
            assertThat(secrets.decrypt(current.getApiKeyCiphertext(), current.getApiKeyNonce()))
                    .isEqualTo("new-holiday-api-key");
        } finally {
            holidayClient.release();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void assertOptimisticFailure(java.util.concurrent.Future<?> lateTest) throws Exception {
        assertThatThrownBy(() -> lateTest.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ExternalClientConfiguration {
        @Bean
        @Primary
        BlockingSmtpProbe blockingSmtpProbe() {
            return new BlockingSmtpProbe();
        }

        @Bean
        @Primary
        BlockingHolidayApiClient blockingHolidayApiClient() {
            return new BlockingHolidayApiClient();
        }
    }

    static final class BlockingSmtpProbe implements SmtpProbe {
        private volatile CountDownLatch started = new CountDownLatch(0);
        private volatile CountDownLatch release = new CountDownLatch(0);
        private final AtomicBoolean transactionActive = new AtomicBoolean(true);

        void arm() {
            started = new CountDownLatch(1);
            release = new CountDownLatch(1);
            transactionActive.set(true);
        }

        @Override
        public void send(SmtpConnection connection, String recipient, String subject, String body) {
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            started.countDown();
            await(release, "SMTP probe release");
        }

        void awaitStarted() {
            await(started, "SMTP probe start");
        }

        boolean transactionActive() {
            return transactionActive.get();
        }

        void release() {
            release.countDown();
        }
    }

    static final class BlockingHolidayApiClient extends HolidayApiHttpClient {
        private volatile CountDownLatch started = new CountDownLatch(0);
        private volatile CountDownLatch release = new CountDownLatch(0);
        private final AtomicBoolean transactionActive = new AtomicBoolean(true);

        BlockingHolidayApiClient() {
            super(org.springframework.web.client.RestClient.builder(), "http://localhost");
        }

        void arm() {
            started = new CountDownLatch(1);
            release = new CountDownLatch(1);
            transactionActive.set(true);
        }

        @Override
        public List<HolidayApiCandidate> fetch(String apiKey, String countryCode, int year) {
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            started.countDown();
            await(release, "HolidayAPI probe release");
            return List.of(new HolidayApiCandidate(
                    "vn-concurrency", "Concurrency Holiday", LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 1, 1), true));
        }

        void awaitStarted() {
            await(started, "HolidayAPI probe start");
        }

        boolean transactionActive() {
            return transactionActive.get();
        }

        void release() {
            release.countDown();
        }
    }

    private static void await(CountDownLatch latch, String operation) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for " + operation);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for " + operation, exception);
        }
    }
}
