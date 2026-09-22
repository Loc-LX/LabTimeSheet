package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import com.lab.labtimesheet.feature.identity.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.identity.service.BootstrapService;
import com.lab.labtimesheet.feature.attendance.exception.AttendanceException;
import com.lab.labtimesheet.feature.attendance.exception.AttendanceRejection;
import com.lab.labtimesheet.feature.attendance.exception.CalendarException;
import com.lab.labtimesheet.feature.attendance.exception.CorrectionException;
import com.lab.labtimesheet.feature.attendance.exception.LeaveException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.CorrectionEventType;
import com.lab.labtimesheet.feature.attendance.model.CorrectionStatus;
import com.lab.labtimesheet.feature.attendance.model.dto.CalendarHistoryItem;
import com.lab.labtimesheet.feature.attendance.model.dto.CalendarImportSelection;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionDecision;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionRequestCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestCommand;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendancePolicyEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceRecordEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionEventRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendancePolicyRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceRecordRepository;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiCandidate;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiPreview;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiPreviewStatus;
import com.lab.labtimesheet.feature.integration.service.HolidayApiConfigurationService;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.platform.model.SecurityMode;
import com.lab.labtimesheet.platform.model.dto.SmtpDraft;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

@Import(AttendancePersistenceIntegrationTest.IntegrationConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.connection-timeout=2000"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AttendanceConcurrencyIntegrationTest {

    @Autowired
    private AttendanceApplicationService attendance;

    @Autowired
    private CalendarApplicationService calendar;

    @Autowired
    private LeaveApplicationService leaves;

    @Autowired
    private AttendanceCorrectionApplicationService corrections;

    @Autowired
    private AttendanceCorrectionRepository correctionRequests;

    @Autowired
    private AttendanceCorrectionEventRepository correctionEvents;

    @Autowired
    private AttendancePolicyRepository policies;

    @Autowired
    private AttendanceRecordRepository records;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private BootstrapService bootstrap;

    @Autowired
    private AccountService accounts;

    @MockitoSpyBean
    private HolidayApiConfigurationService holidayApi;

    @Autowired
    private SmtpConfigurationService smtp;

    @Autowired
    private AttendancePersistenceIntegrationTest.RecordingSmtpProbe mail;

    @Autowired
    private AttendancePersistenceIntegrationTest.MutableClock clock;

    private static volatile long concurrencyInternId;
    private static volatile long concurrencyMentorId;

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

    @Test
    void concurrentLeaveReservationsSerializeOnThePolicyQuotaRow() throws Exception {
        long internId = createActiveIntern();
        clock.set(Instant.parse("2026-08-14T00:00:00Z"));
        AttendanceActor actor = new AttendanceActor(internId, AttendanceRole.INTERN);
        AtomicInteger requestNumber = new AtomicInteger();

        List<String> outcomes = runConcurrently(() -> {
            try {
                LeaveRequestCommand command = requestNumber.getAndIncrement() == 0
                        ? new LeaveRequestCommand(
                                LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 18), "first")
                        : new LeaveRequestCommand(
                                LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 20), "second");
                leaves.submit(actor, command);
                return "SUCCESS";
            } catch (LeaveException rejection) {
                return "QUOTA_OR_OVERLAP_REJECTED";
            }
        });

        assertThat(outcomes).containsExactlyInAnyOrder("SUCCESS", "QUOTA_OR_OVERLAP_REJECTED");
    }

    @Test
    void overlappingHistoryAndExpiryUseAscendingCorrectionLocksWhenAttendanceOrderDiffers() throws Exception {
        long internId = createActiveIntern();
        AttendancePolicyEntity policy = policies.findById(1L).orElseThrow();

        clock.set(Instant.parse("2026-10-05T02:00:00Z"));
        long firstRecordId = records.saveAndFlush(new AttendanceRecordEntity(
                internId,
                LocalDate.of(2026, 10, 5),
                policy,
                clock.instant(),
                null)).id();
        long secondRecordId = records.saveAndFlush(new AttendanceRecordEntity(
                internId,
                LocalDate.of(2026, 10, 6),
                policy,
                clock.instant().plusSeconds(24 * 60 * 60L),
                null)).id();

        Instant submittedAt = Instant.parse("2026-10-06T09:01:00Z");
        AttendanceCorrectionEntity secondAttendanceCorrection = correctionRequests.saveAndFlush(
                new AttendanceCorrectionEntity(
                        secondRecordId,
                        Instant.parse("2026-10-06T06:00:00Z"),
                        "second attendance row submitted first",
                        submittedAt,
                        submittedAt,
                        Instant.parse("2026-10-08T00:00:00Z")));
        AttendanceCorrectionEntity firstAttendanceCorrection = correctionRequests.saveAndFlush(
                new AttendanceCorrectionEntity(
                        firstRecordId,
                        Instant.parse("2026-10-05T06:00:00Z"),
                        "first attendance row submitted second",
                        submittedAt,
                        submittedAt,
                        Instant.parse("2026-10-07T09:01:00Z")));

        assertThat(firstAttendanceCorrection.id()).isGreaterThan(secondAttendanceCorrection.id());
        List<Long> ordered = transactions.execute(status -> correctionRequests
                .findByAttendanceRecordIdInForUpdate(List.of(secondRecordId, firstRecordId)).stream()
                .map(AttendanceCorrectionEntity::id)
                .toList());
        assertThat(ordered)
                .containsExactly(secondAttendanceCorrection.id(), firstAttendanceCorrection.id());

        clock.set(Instant.parse("2026-10-08T00:00:00Z"));
        AtomicInteger operation = new AtomicInteger();
        assertThat(runConcurrently(() -> {
            if (operation.getAndIncrement() == 0) {
                attendance.history(
                        new AttendanceActor(internId, AttendanceRole.INTERN),
                        internId,
                        LocalDate.of(2026, 10, 5),
                        LocalDate.of(2026, 10, 6));
                return "HISTORY";
            }
            corrections.expire(2);
            return "EXPIRY";
        })).containsExactlyInAnyOrder("HISTORY", "EXPIRY");

        assertThat(correctionRequests.findAll())
                .filteredOn(row -> row.attendanceRecordId() == firstRecordId
                        || row.attendanceRecordId() == secondRecordId)
                .allSatisfy(row -> assertThat(row.status()).isEqualTo(CorrectionStatus.REJECTED));
    }

    @Test
    void poolSizedMentorDecisionBurstCompletesWithAuthorizationAndExpiryOnInnerConnection() throws Exception {
        long internId = createActiveIntern();
        long mentorId = createActiveMentor();

        clock.set(Instant.parse("2026-11-09T02:00:00Z"));
        attendance.checkIn(internId);
        long recordId = records.findByInternUserIdAndWorkDate(internId, LocalDate.of(2026, 11, 9))
                .orElseThrow()
                .id();
        clock.set(Instant.parse("2026-11-09T09:01:00Z"));
        var submittedCorrection = corrections.submit(
                new AttendanceActor(internId, AttendanceRole.INTERN),
                recordId,
                new CorrectionRequestCommand(
                        LocalDateTime.of(2026, 11, 9, 14, 0),
                        "pool-sized correction"));
        var submittedLeave = leaves.submit(
                new AttendanceActor(internId, AttendanceRole.INTERN),
                new LeaveRequestCommand(
                        LocalDate.of(2026, 11, 12),
                        LocalDate.of(2026, 11, 12),
                        "pool-sized leave"));

        clock.set(Instant.parse("2026-11-09T10:00:00Z"));
        AtomicInteger operation = new AtomicInteger();
        assertThat(runConcurrently(() -> {
            try {
                if (operation.getAndIncrement() == 0) {
                    leaves.approve(
                            new AttendanceActor(mentorId, AttendanceRole.MENTOR), submittedLeave.id());
                    return "LEAVE_APPROVED";
                }
                corrections.decide(
                        new AttendanceActor(mentorId, AttendanceRole.MENTOR),
                        submittedCorrection.id(),
                        CorrectionDecision.APPROVE,
                        "pool-sized approval");
                return "CORRECTION_APPROVED";
            } catch (RuntimeException failure) {
                return "FAILURE:" + failure.getClass().getSimpleName();
            }
        })).containsExactlyInAnyOrder("LEAVE_APPROVED", "CORRECTION_APPROVED");
    }

    @Test
    void concurrentDecisionsOnOneCorrectionCommitOneTransitionAndOneFailure() throws Exception {
        long internId = createActiveIntern();
        long mentorId = createActiveMentor();
        clock.set(Instant.parse("2026-11-10T02:00:00Z"));
        attendance.checkIn(internId);
        long recordId = records.findByInternUserIdAndWorkDate(internId, LocalDate.of(2026, 11, 10))
                .orElseThrow().id();
        clock.set(Instant.parse("2026-11-10T09:01:00Z"));
        var submitted = corrections.submit(
                new AttendanceActor(internId, AttendanceRole.INTERN), recordId,
                new CorrectionRequestCommand(LocalDateTime.of(2026, 11, 10, 14, 0), "same correction"));
        clock.set(Instant.parse("2026-11-10T10:00:00Z"));

        List<String> outcomes = runConcurrently(() -> {
            try {
                corrections.decide(new AttendanceActor(mentorId, AttendanceRole.MENTOR), submitted.id(),
                        CorrectionDecision.APPROVE, "race");
                return "SUCCESS";
            } catch (CorrectionException failure) {
                return "CONFLICT";
            }
        });

        assertThat(outcomes).containsExactlyInAnyOrder("SUCCESS", "CONFLICT");
        assertThat(correctionRequests.findById(submitted.id()).orElseThrow().status())
                .isEqualTo(CorrectionStatus.APPROVED);
        assertThat(correctionEvents.findByCorrectionIdOrderByOccurredAtAscIdAsc(submitted.id()))
                .extracting(event -> event.toView().type())
                .containsExactly(CorrectionEventType.SUBMITTED, CorrectionEventType.APPROVED);
    }

    @Test
    void poolSizedInternLeaveMutationBurstCompletesWithoutNestedExpiryConnection() throws Exception {
        long internId = createActiveIntern();
        AttendanceActor actor = new AttendanceActor(internId, AttendanceRole.INTERN);
        clock.set(Instant.parse("2026-11-16T00:00:00Z"));
        var first = leaves.submit(actor, new LeaveRequestCommand(
                LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 18), "first cancellation"));
        var second = leaves.submit(actor, new LeaveRequestCommand(
                LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 19), "second cancellation"));

        clock.set(Instant.parse("2026-11-16T10:00:00Z"));
        AtomicInteger request = new AtomicInteger();
        assertThat(runConcurrently(() -> {
            try {
                if (request.getAndIncrement() == 0) {
                    leaves.edit(actor, first.id(), new LeaveRequestCommand(
                            LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 18), "edited"));
                    return "EDITED";
                }
                leaves.cancel(actor, second.id());
                return "CANCELLED";
            } catch (RuntimeException failure) {
                return "FAILURE:" + failure.getClass().getSimpleName();
            }
        })).containsExactlyInAnyOrder("EDITED", "CANCELLED");
    }

    @Test
    void concurrentSameHolidayUuidImportsAreIdempotent() throws Exception {
        createActiveIntern();
        long adminId = accounts.requireActiveAdminId("concurrency-admin@example.test");
        AttendanceActor admin = new AttendanceActor(adminId, AttendanceRole.ADMIN);
        clock.set(Instant.parse("2026-11-09T00:00:00Z"));
        HolidayApiCandidate candidate = new HolidayApiCandidate(
                "vn-concurrent-2026-11-10",
                "Concurrent holiday",
                LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 10),
                true);
        HolidayApiPreview trustedPreview = new HolidayApiPreview(
                HolidayApiPreviewStatus.SUCCESS,
                List.of(candidate),
                "loaded",
                Instant.parse("2026-11-08T12:00:00Z"));
        doReturn(trustedPreview).when(holidayApi).preview(adminId, 2026);
        CalendarImportSelection selection = new CalendarImportSelection(
                "vn-concurrent-2026-11-10", true);

        List<String> outcomes = runConcurrently(() -> {
            try {
                List<CalendarHistoryItem> imported = calendar.importSelected(
                        admin, 2026, List.of(selection));
                return imported.isEmpty() ? "DUPLICATE" : "IMPORTED";
            } catch (RuntimeException failure) {
                return "FAILURE:" + failure.getClass().getSimpleName();
            }
        });

        assertThat(outcomes).containsExactlyInAnyOrder("IMPORTED", "DUPLICATE");
        assertThat(calendar.history(admin)).filteredOn(item ->
                        "vn-concurrent-2026-11-10".equals(item.sourceUuid()))
                .hasSize(1);
    }

    @Test
    void publicImportRejectsClientIdentityOutsideTrustedPreview() {
        createActiveIntern();
        long adminId = accounts.requireActiveAdminId("concurrency-admin@example.test");
        AttendanceActor admin = new AttendanceActor(adminId, AttendanceRole.ADMIN);
        HolidayApiPreview trustedPreview = new HolidayApiPreview(
                HolidayApiPreviewStatus.SUCCESS,
                List.of(new HolidayApiCandidate(
                        "trusted-source", "Trusted holiday", LocalDate.of(2026, 11, 10),
                        LocalDate.of(2026, 11, 10), true)),
                "loaded",
                Instant.parse("2026-11-08T12:00:00Z"));
        doReturn(trustedPreview).when(holidayApi).preview(adminId, 2026);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> calendar.importSelected(
                        admin, 2026, List.of(new CalendarImportSelection("client-altered-source", true)))))
                .isInstanceOf(CalendarException.class)
                .hasMessage("Selected HolidayAPI candidate was not present in the trusted preview");
        assertThat(calendar.history(admin)).filteredOn(item ->
                        "client-altered-source".equals(item.sourceUuid()))
                .isEmpty();
    }

    private long createActiveIntern() {
        if (concurrencyInternId != 0L) {
            return concurrencyInternId;
        }
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
        concurrencyInternId = creation.userId();
        return concurrencyInternId;
    }

    private long createActiveMentor() {
        if (concurrencyMentorId != 0L) {
            return concurrencyMentorId;
        }
        long adminId = accounts.requireActiveAdminId("concurrency-admin@example.test");
        mail.clear();
        var creation = accounts.create(new CreateAccountCommand(
                "concurrency-mentor@example.test",
                "Concurrent Mentor",
                GlobalRole.MENTOR,
                null,
                null,
                null), adminId);
        assertThat(creation.deliverySucceeded()).isTrue();
        assertThat(accounts.activate(mail.onlyActivationToken(), "new secure mentor password")).isTrue();
        concurrencyMentorId = creation.userId();
        return concurrencyMentorId;
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
            return List.of(first.get(8, TimeUnit.SECONDS), second.get(8, TimeUnit.SECONDS));
        }
    }
}
