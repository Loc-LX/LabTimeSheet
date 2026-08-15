package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.account.service.BootstrapService;
import com.lab.labtimesheet.feature.attendance.exception.AttendanceException;
import com.lab.labtimesheet.feature.attendance.exception.AttendanceRejection;
import com.lab.labtimesheet.feature.integration.model.SecurityMode;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpDraft;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@Import(AttendancePersistenceIntegrationTest.IntegrationConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AttendanceConcurrencyIntegrationTest {

    @Autowired
    private AttendanceApplicationService attendance;

    @Autowired
    private BootstrapService bootstrap;

    @Autowired
    private AccountService accounts;

    @Autowired
    private SmtpConfigurationService smtp;

    @Autowired
    private AttendancePersistenceIntegrationTest.RecordingSmtpProbe mail;

    @Autowired
    private AttendancePersistenceIntegrationTest.MutableClock clock;

    @Test
    void concurrentDuplicatePunchesReturnStableDomainOutcomes() throws Exception {
        long internId = createActiveIntern();

        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        assertThat(runConcurrently(() -> punchOutcome(() -> attendance.checkIn(internId))))
                .containsExactlyInAnyOrder("SUCCESS", AttendanceRejection.ALREADY_CHECKED_IN.name());

        clock.set(Instant.parse("2026-08-14T09:00:00Z"));
        assertThat(runConcurrently(() -> punchOutcome(() -> attendance.checkOut(internId))))
                .containsExactlyInAnyOrder("SUCCESS", AttendanceRejection.ALREADY_CHECKED_OUT.name());
    }

    private long createActiveIntern() {
        bootstrap.bootstrap("concurrency-admin@example.test", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("concurrency-admin@example.test");
        long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                "mailpit",
                1025,
                SecurityMode.NONE,
                null,
                null,
                "concurrency-admin@example.test",
                "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "concurrency-admin@example.test");
        smtp.activate(draftId, adminId);
        mail.clear();

        var creation = accounts.create(new CreateAccountCommand(
                "concurrency-intern@example.test",
                "Concurrent Intern",
                GlobalRole.INTERN,
                "INT-CONCURRENT",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31)), adminId);
        assertThat(creation.deliverySucceeded()).isTrue();
        assertThat(accounts.activate(mail.onlyActivationToken(), "new secure intern password")).isTrue();
        accounts.activateInternship(creation.userId(), adminId);
        return creation.userId();
    }

    private static String punchOutcome(Runnable punch) {
        try {
            punch.run();
            return "SUCCESS";
        } catch (AttendanceException rejection) {
            return rejection.rejection().name();
        }
    }

    private static List<String> runConcurrently(Callable<String> action) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<String> synchronizedAction = () -> {
                ready.countDown();
                start.await();
                return action.call();
            };
            Future<String> first = executor.submit(synchronizedAction);
            Future<String> second = executor.submit(synchronizedAction);
            ready.await();
            start.countDown();
            return List.of(first.get(), second.get());
        }
    }
}
