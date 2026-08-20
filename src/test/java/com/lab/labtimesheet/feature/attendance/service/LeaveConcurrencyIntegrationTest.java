package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.account.service.BootstrapService;
import com.lab.labtimesheet.feature.attendance.exception.LeaveException;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveDecisionCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveSubmission;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveSubmissionCommand;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestRepository;
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
class LeaveConcurrencyIntegrationTest {

    @Autowired
    private LeaveService leave;

    @Autowired
    private LeaveRequestRepository requests;

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

    private static Long adminId;

    @Test
    void concurrentOverlappingSubmitsProduceExactlyOneSuccessAndOneOverlap() throws Exception {
        long internId = createActiveIntern("a");
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));

        List<String> outcomes = runConcurrently(() -> submitOutcome(internId));
        assertThat(outcomes).containsExactlyInAnyOrder("SUCCESS", "OVERLAPS_PENDING");
        assertThat(requests.count()).isEqualTo(1);
    }

    @Test
    void concurrentDecisionsProduceExactlyOneSuccessAndOneInvalidState() throws Exception {
        long internId = createActiveIntern("b");
        long mentorId = createActiveMentor("b");
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        LeaveSubmission request = leave.submit(
                internId, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 2),
                        "Concurrent decision target"));

        List<String> outcomes = runConcurrently(
                () -> decideOutcome(mentorId, request.requestId()));
        assertThat(outcomes).containsExactlyInAnyOrder("SUCCESS", "INVALID_STATE");
        assertThat(requests.findById(request.requestId()).orElseThrow().status()).isEqualTo("APPROVED");
    }

    private String submitOutcome(long internId) {
        try {
            leave.submit(internId, new LeaveSubmissionCommand(
                    LocalDate.of(2026, 9, 1),
                    LocalDate.of(2026, 9, 2),
                    "Concurrent block"));
            return "SUCCESS";
        } catch (LeaveException rejection) {
            return rejection.rejection().name();
        }
    }

    private String decideOutcome(long mentorId, long requestId) {
        try {
            leave.decide(mentorId, requestId, new LeaveDecisionCommand(true, null));
            return "SUCCESS";
        } catch (LeaveException rejection) {
            return rejection.rejection().name();
        }
    }

    private long createActiveIntern(String suffix) {
        long adminId = adminId();
        long userId = createAndActivate(adminId, new CreateAccountCommand(
                "leave-concurrency-intern-" + suffix + "@example.test",
                "Concurrent Leave Intern",
                GlobalRole.INTERN,
                "INT-LEAVE-CONCURRENT-" + suffix.toUpperCase(),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31)));
        accounts.activateInternship(userId, adminId);
        return userId;
    }

    private long createActiveMentor(String suffix) {
        return createAndActivate(adminId(), new CreateAccountCommand(
                "leave-concurrency-mentor-" + suffix + "@example.test",
                "Concurrent Leave Mentor",
                GlobalRole.MENTOR,
                null,
                null,
                null));
    }

    private long adminId() {
        if (adminId == null) {
            adminId = bootstrapAdmin();
        }
        return adminId;
    }

    private long bootstrapAdmin() {
        bootstrap.bootstrap("leave-concurrency-admin@example.test", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("leave-concurrency-admin@example.test");
        long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                "mailpit",
                1025,
                SecurityMode.NONE,
                null,
                null,
                "leave-concurrency-admin@example.test",
                "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "leave-concurrency-admin@example.test");
        smtp.activate(draftId, adminId);
        return adminId;
    }

    private long createAndActivate(long adminId, CreateAccountCommand command) {
        mail.clear();
        var creation = accounts.create(command, adminId);
        assertThat(creation.deliverySucceeded()).isTrue();
        assertThat(accounts.activate(mail.onlyActivationToken(), "new secure intern password")).isTrue();
        return creation.userId();
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