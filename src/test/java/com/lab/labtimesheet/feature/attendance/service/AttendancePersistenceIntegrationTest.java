package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.account.model.dto.InternWorkWindow;
import com.lab.labtimesheet.feature.account.model.dto.InternshipLifecycleGuard;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.account.service.BootstrapService;
import com.lab.labtimesheet.feature.attendance.exception.AttendanceException;
import com.lab.labtimesheet.feature.attendance.exception.CalendarException;
import com.lab.labtimesheet.feature.attendance.exception.CorrectionException;
import com.lab.labtimesheet.feature.attendance.exception.LeaveException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.CorrectionStatus;
import com.lab.labtimesheet.feature.attendance.model.CorrectionEventType;
import com.lab.labtimesheet.feature.attendance.model.LeaveStatus;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceCurrentState;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceHistoryItem;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendancePolicyCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.CalendarHistoryItem;
import com.lab.labtimesheet.feature.attendance.model.dto.CalendarImportSelection;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionDecision;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionRequestCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveAllocation;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestView;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendancePolicyEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceRecordEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionEventRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendancePolicyRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceRecordRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestDayRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestRepository;
import com.lab.labtimesheet.feature.integration.model.SecurityMode;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiCandidate;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiPreview;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiPreviewStatus;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpConnection;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpDraft;
import com.lab.labtimesheet.feature.integration.service.HolidayApiConfigurationService;
import com.lab.labtimesheet.feature.integration.service.MailDeliveryService;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.integration.service.SmtpProbe;
import com.lab.labtimesheet.feature.notification.model.NotificationEmailStatus;
import com.lab.labtimesheet.feature.notification.model.NotificationType;
import com.lab.labtimesheet.feature.notification.model.entity.NotificationEntity;
import com.lab.labtimesheet.feature.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import static com.lab.labtimesheet.feature.attendance.model.entity.LeaveEntityFixtures.allocatedDay;
import static com.lab.labtimesheet.feature.attendance.model.entity.LeaveEntityFixtures.approvedRequest;

@Import(AttendancePersistenceIntegrationTest.IntegrationConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AttendancePersistenceIntegrationTest {

    @Autowired
    private AttendanceApplicationService attendance;

    @Autowired
    private CalendarApplicationService calendar;

    @Autowired
    private AttendanceCurrentUserService currentUsers;

    @Autowired
    private BootstrapService bootstrap;

    @Autowired
    private AccountService accounts;

    @MockitoSpyBean
    private AccountService accountSpy;

    @MockitoSpyBean
    private HolidayApiConfigurationService holidayApi;

    @Autowired
    private SmtpConfigurationService smtp;

    @Autowired
    private RecordingSmtpProbe mail;

    @MockitoSpyBean
    private MailDeliveryService mailDelivery;

    @Autowired
    private AttendanceRecordRepository records;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private LeaveApplicationService leaves;

    @Autowired
    private AttendanceCorrectionApplicationService corrections;

    @Autowired
    private LeaveRequestRepository leaveRequests;

    @Autowired
    private LeaveRequestDayRepository leaveDays;

    @Autowired
    private AttendanceCorrectionRepository correctionRequests;

    @Autowired
    private AttendanceCorrectionEventRepository correctionEvents;

    @Autowired
    private NotificationRepository notificationRows;

    @Autowired
    private AttendancePolicyApplicationService policyApplication;

    @Autowired
    private MutableClock clock;

    @Autowired
    private AmbientMutationInvoker ambientMutations;

    @Autowired
    private LifecycleMutationInvoker lifecycleMutations;

    @Autowired
    private PolicyLockInvoker policyLocks;

    private long internId;
    private long adminId;
    private long mentorId;
    private int mentorSequence;

    private long createActiveMentor() {
        mail.clear();
        var creation = accounts.create(new CreateAccountCommand(
                "mentor-" + internId + "-" + (++mentorSequence) + "@example.test",
                "Mentor",
                GlobalRole.MENTOR,
                null,
                null,
                null),
                adminId);
        assertThat(creation.deliverySucceeded()).isTrue();
        assertThat(accounts.activate(mail.onlyActivationToken(), "new secure mentor password")).isTrue();
        return creation.userId();
    }

    @BeforeEach
    void seedUsers() {
        clock.set(Instant.parse("2026-08-14T00:00:00Z"));
        bootstrap.bootstrap("admin@example.test", "Admin", "correct horse battery staple");
        adminId = accounts.requireActiveAdminId("admin@example.test");
        long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                "mailpit",
                1025,
                SecurityMode.NONE,
                null,
                null,
                "admin@example.test",
                "Lab Timesheet"));
        smtp.testDraft(draftId, adminId);
        smtp.activate(draftId, adminId);
        mail.clear();

        var creation = accounts.create(new CreateAccountCommand(
                "intern@example.test",
                "Intern",
                GlobalRole.INTERN,
                "INT-001",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31)), adminId);
        assertThat(creation.deliverySucceeded()).isTrue();
        assertThat(accounts.activate(mail.onlyActivationToken(), "new secure intern password")).isTrue();
        accounts.activateInternship(creation.userId(), adminId);

        mentorId = adminId + 1;
        internId = creation.userId();
    }

    @Test
    void storesServerPunchesWithSeededPolicyAndHistoricalPolicyDetails() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));

        assertThat(attendance.currentState(internId)).isEqualTo(AttendanceCurrentState.NOT_CHECKED_IN);
        attendance.checkIn(internId);
        assertThat(attendance.currentState(internId)).isEqualTo(AttendanceCurrentState.CHECKED_IN);

        var persisted = records.findByInternUserIdAndWorkDate(internId, LocalDate.of(2026, 8, 14))
                .orElseThrow()
                .toDomain();
        assertThat(persisted.checkInAt()).isEqualTo(clock.instant());
        assertThat(persisted.policy().id()).isEqualTo(1L);
        assertThatThrownBy(() -> attendance.checkIn(internId)).isInstanceOf(AttendanceException.class);

        clock.set(Instant.parse("2026-08-14T09:00:00Z"));
        attendance.checkOut(internId);
        assertThat(attendance.currentState(internId)).isEqualTo(AttendanceCurrentState.CHECKED_OUT);

        AttendanceHistoryItem item = attendance.history(
                        new AttendanceActor(internId, AttendanceRole.INTERN),
                        internId,
                        LocalDate.of(2026, 8, 14),
                        LocalDate.of(2026, 8, 14))
                .getFirst();
        assertThat(item.policy().id()).isEqualTo(1L);
        assertThat(item.policy().checkoutGraceMinutes()).isEqualTo(30);
        assertThat(item.checkOutAt()).isEqualTo(clock.instant());
        assertThat(item.violations().missingCheckout()).isFalse();
    }

    @Test
    void approvedLeaveBlocksOnlyItsFrozenAllocatedDates() {
        LocalDate unallocatedDate = LocalDate.of(2026, 8, 14);
        LocalDate allocatedDate = LocalDate.of(2026, 8, 17);
        LeaveRequestEntity request = approvedRequest(
                internId,
                unallocatedDate,
                allocatedDate,
                Instant.parse("2026-08-13T00:00:00Z"),
                Instant.parse("2026-08-14T01:30:00Z"),
                adminId,
                Instant.parse("2026-08-13T00:30:00Z"));
        entityManager.persist(request);
        entityManager.flush();
        entityManager.persist(allocatedDay(
                request,
                allocatedDate,
                entityManager.getReference(AttendancePolicyEntity.class, 1L),
                3));
        entityManager.flush();

        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        attendance.checkIn(internId);
        assertThat(records.findByInternUserIdAndWorkDate(internId, unallocatedDate)).isPresent();

        clock.set(Instant.parse("2026-08-17T02:00:00Z"));
        assertThatThrownBy(() -> attendance.checkIn(internId))
                .isInstanceOfSatisfying(AttendanceException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(com.lab.labtimesheet.feature.attendance.exception.AttendanceRejection.APPROVED_LEAVE));
    }

    @Test
    void calendarDayOffBlocksCheckInAndPastEventsAreImmutable() {
        AttendanceActor admin = new AttendanceActor(adminId, AttendanceRole.ADMIN);
        AttendanceActor intern = new AttendanceActor(internId, AttendanceRole.INTERN);
        LocalDate workDate = LocalDate.of(2026, 8, 14);
        clock.set(Instant.parse("2026-08-13T02:00:00Z"));

        assertThatThrownBy(() -> calendar.createManual(intern, workDate, "Blocked", true))
                .isInstanceOf(AccessDeniedException.class);
        var event = calendar.createManual(admin, workDate, "Team holiday", true);

        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        assertThatThrownBy(() -> attendance.checkIn(internId)).isInstanceOf(AttendanceException.class);

        clock.set(Instant.parse("2026-08-15T02:00:00Z"));
        assertThatThrownBy(() -> calendar.updateManual(
                        admin, event.id(), event.version(), workDate, "Changed", false))
                .isInstanceOf(CalendarException.class);
    }

    @Test
    void calendarRejectsStaleOptimisticVersion() {
        AttendanceActor admin = new AttendanceActor(adminId, AttendanceRole.ADMIN);
        LocalDate date = LocalDate.of(2026, 8, 20);
        var event = calendar.createManual(admin, date, "Lab closure", true);

        calendar.updateManual(admin, event.id(), event.version(), date, "Lab open", false);

        assertThatThrownBy(() -> calendar.updateManual(
                        admin, event.id(), event.version(), date, "Stale edit", true))
                .isInstanceOf(CalendarException.class);
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void importsPlatformCandidateWithCanonicalProvenanceAndIdempotentHistory() {
        AttendanceActor admin = new AttendanceActor(adminId, AttendanceRole.ADMIN);
        HolidayApiCandidate candidate = new HolidayApiCandidate(
                "  vn-september-2  ",
                " National Day ",
                LocalDate.of(2026, 9, 2),
                LocalDate.of(2026, 9, 3),
                true);
        HolidayApiPreview upstream = new HolidayApiPreview(
                HolidayApiPreviewStatus.SUCCESS,
                List.of(candidate, candidate),
                "loaded",
                Instant.parse("2026-08-13T12:34:56Z"));
        doReturn(upstream).when(holidayApi).preview(adminId, 2026);

        assertThat(calendar.preview(admin, 2026, upstream))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.candidate().uuid()).isEqualTo("vn-september-2");
                    assertThat(item.selectedByDefault()).isTrue();
                });

        TestTransaction.flagForCommit();
        TestTransaction.end();

        CalendarImportSelection selection = new CalendarImportSelection("vn-september-2", false);
        assertThat(calendar.importSelected(admin, 2026, List.of(selection))).hasSize(1);
        CalendarHistoryItem imported = calendar.history(admin).stream()
                .filter(item -> "vn-september-2".equals(item.sourceUuid()))
                .findFirst()
                .orElseThrow();
        assertThat(imported.source()).isEqualTo("HOLIDAY_API");
        assertThat(imported.name()).isEqualTo("National Day");
        assertThat(imported.actualDate()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(imported.observedDate()).isEqualTo(LocalDate.of(2026, 9, 3));
        assertThat(imported.publicHoliday()).isTrue();
        assertThat(imported.dayOff()).isFalse();
        assertThat(imported.importedAt()).isEqualTo(upstream.retrievedAt());
        assertThat(calendar.importSelected(admin, 2026, List.of(selection))).isEmpty();
        assertThat(calendar.history(admin)).filteredOn(item -> "vn-september-2".equals(item.sourceUuid()))
                .hasSize(1);
    }

    @Test
    void rejectsPlatformCandidateOutsideRequestedYearBeforePersistence() {
        AttendanceActor admin = new AttendanceActor(adminId, AttendanceRole.ADMIN);
        HolidayApiPreview trustedPreview = new HolidayApiPreview(
                HolidayApiPreviewStatus.SUCCESS,
                List.of(new HolidayApiCandidate(
                        "vn-prior-year",
                        "Prior year",
                        LocalDate.of(2025, 12, 31),
                        LocalDate.of(2025, 12, 31),
                        true)),
                "loaded",
                Instant.parse("2026-08-14T00:00:00Z"));
        doReturn(trustedPreview).when(holidayApi).preview(adminId, 2026);
        CalendarImportSelection selection = new CalendarImportSelection("vn-prior-year", true);

        assertThatThrownBy(() -> calendar.importSelected(admin, 2026, List.of(selection)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Holiday candidate dates must belong to the requested year");
        assertThat(calendar.history(admin)).isEmpty();
    }

    @Test
    void futurePolicyReplacementKeepsEffectiveHistoryAndCreatorAttribution() {
        AttendanceActor admin = new AttendanceActor(adminId, AttendanceRole.ADMIN);
        AttendancePolicyCommand command = new AttendancePolicyCommand(
                LocalDate.of(2026, 9, 1),
                ZoneId.of("Asia/Ho_Chi_Minh"),
                LocalTime.of(8, 0),
                LocalTime.of(16, 0),
                15,
                45,
                4,
                new BigDecimal("0.20"),
                Set.of(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY));

        var first = policyApplication.schedule(admin, command);
        assertThat(first.effectiveFrom()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(first.createdByUserId()).isEqualTo(adminId);

        var replacement = policyApplication.schedule(admin, new AttendancePolicyCommand(
                command.effectiveFrom(),
                command.zoneId(),
                command.scheduledStart(),
                command.scheduledEnd(),
                20,
                command.checkoutGraceMinutes(),
                command.monthlyLeaveQuota(),
                command.violationPenalty(),
                command.workdays()));
        assertThat(replacement.policy().checkInGraceMinutes()).isEqualTo(20);
        assertThat(replacement.createdByUserId()).isEqualTo(adminId);
        assertThat(policyApplication.history(admin)).extracting(item -> item.effectiveFrom())
                .containsExactly(LocalDate.of(1970, 1, 1), LocalDate.of(2026, 9, 1));
    }

    @Test
    void publicCalendarServiceReportsAuthoritativeDayOff() {
        AttendanceActor admin = new AttendanceActor(adminId, AttendanceRole.ADMIN);
        LocalDate date = LocalDate.of(2026, 8, 20);
        var event = calendar.createManual(admin, date, "Observance", false);

        assertThat(calendar.isGlobalDayOff(date)).isFalse();

        calendar.updateManual(admin, event.id(), event.version(), date, "Lab closure", true);
        assertThat(calendar.isGlobalDayOff(date)).isTrue();
    }

    @Test
    void leaveFreezesEligibleDatesAndRequestTimeExpiryIsDatabaseSafe() {
        LeaveRequestCommand command = new LeaveRequestCommand(
                LocalDate.of(2026, 8, 17),
                LocalDate.of(2026, 8, 18),
                "family leave");

        var submitted = leaves.submit(new AttendanceActor(internId, AttendanceRole.INTERN), command);
        assertThat(submitted.status()).isEqualTo(LeaveStatus.PENDING);
        assertThat(submitted.allocations()).extracting(allocation -> allocation.leaveDate())
                .containsExactly(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 18));
        assertThat(submitted.allocations()).allSatisfy(allocation -> {
            assertThat(allocation.policyVersionId()).isEqualTo(1L);
            assertThat(allocation.monthlyQuotaSnapshot()).isEqualTo(3);
        });

        clock.set(submitted.firstCountedStartAt());
        assertThat(leaves.expirePending(100)).isEqualTo(1);
        assertThat(leaves.view(new AttendanceActor(internId, AttendanceRole.INTERN), submitted.id()).status())
                .isEqualTo(LeaveStatus.REJECTED);
        assertThat(leaveRequests.findById(submitted.id()).orElseThrow().decidedByMentorUserId()).isNull();
    }

    @Test
    void leaveSubmitRejectsDatesOutsideInclusiveInternshipWindow() {
        assertThatThrownBy(() -> leaves.submit(
                        new AttendanceActor(internId, AttendanceRole.INTERN),
                        new LeaveRequestCommand(
                                LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 3), "before internship")))
                .isInstanceOf(LeaveException.class)
                .hasMessageContaining("internship interval");
        assertThat(leaveRequests.findAll()).isEmpty();
        assertThat(leaveDays.findAll()).isEmpty();
    }

    @Test
    void leaveSubmitRejectsDeactivatedInternBeforeAllocation() {
        accounts.deactivateAccount(internId, adminId);

        assertThatThrownBy(() -> leaves.submit(
                        new AttendanceActor(internId, AttendanceRole.INTERN),
                        new LeaveRequestCommand(
                                LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 17), "deactivated")))
                .isInstanceOf(LeaveException.class)
                .hasMessageContaining("active Intern");
        assertThat(leaveRequests.findAll()).isEmpty();
        assertThat(leaveDays.findAll()).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void leaveEditRejectsCompletedInternBeforeReplacingAllocation() {
        AttendanceActor intern = new AttendanceActor(internId, AttendanceRole.INTERN);
        var submitted = leaves.submit(intern, new LeaveRequestCommand(
                LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 17), "terminal edit"));
        accounts.completeInternship(internId, adminId, new InternshipLifecycleGuard(false, 0));

        assertThatThrownBy(() -> leaves.edit(intern, submitted.id(), new LeaveRequestCommand(
                        LocalDate.of(2026, 8, 18), LocalDate.of(2026, 8, 18), "must reject")))
                .isInstanceOf(LeaveException.class)
                .hasMessageContaining("active Intern");
        assertThat(leaveRequests.findById(submitted.id()).orElseThrow().status())
                .isEqualTo(LeaveStatus.PENDING);
        assertThat(leaveDays.findByRequestIdOrderByLeaveDate(submitted.id()))
                .extracting(day -> day.leaveDate())
                .containsExactly(LocalDate.of(2026, 8, 17));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void lateEditByDeactivatedOwnerPersistsExpiryBeforeLifecycleRejection() {
        AttendanceActor intern = new AttendanceActor(internId, AttendanceRole.INTERN);
        var submitted = leaves.submit(intern, new LeaveRequestCommand(
                LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 17), "late lifecycle edit"));
        accounts.deactivateAccount(internId, adminId);
        clock.set(submitted.firstCountedStartAt());

        assertThatThrownBy(() -> leaves.edit(intern, submitted.id(), new LeaveRequestCommand(
                        LocalDate.of(2026, 8, 18), LocalDate.of(2026, 8, 18), "must reject")))
                .isInstanceOf(LeaveException.class)
                .hasMessageContaining("Only pending leave");
        assertThat(leaveRequests.findById(submitted.id()).orElseThrow().status())
                .isEqualTo(LeaveStatus.REJECTED);
        assertThat(leaveDays.findByRequestIdOrderByLeaveDate(submitted.id()))
                .extracting(day -> day.leaveDate())
                .containsExactly(LocalDate.of(2026, 8, 17));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void pendingLeaveCanBeEditedAndCancelledBeforeItsFirstCountedStart() {
        AttendanceActor intern = new AttendanceActor(internId, AttendanceRole.INTERN);
        var submitted = leaves.submit(intern, new LeaveRequestCommand(
                LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 18), "initial"));

        var edited = leaves.edit(intern, submitted.id(), new LeaveRequestCommand(
                LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 17), "updated"));
        assertThat(edited.allocations()).hasSize(1);
        assertThat(edited.allocations().getFirst().leaveDate()).isEqualTo(LocalDate.of(2026, 8, 17));

        var cancelled = leaves.cancel(intern, edited.id());
        assertThat(cancelled.status()).isEqualTo(LeaveStatus.CANCELLED);
        assertThat(cancelled.cancelledAt()).isEqualTo(clock.instant());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void leaveSubmitRetainsInternProfileLockThroughQuotaPersistenceAgainstLifecycleMutation() throws Exception {
        LocalDate firstLeaveDate = LocalDate.of(2026, 8, 17);
        LocalDate lastLeaveDate = LocalDate.of(2026, 8, 18);
        CountDownLatch policyLocked = new CountDownLatch(1);
        CountDownLatch releasePolicy = new CountDownLatch(1);
        CountDownLatch workWindowLocked = new CountDownLatch(1);
        CountDownLatch releaseWorkWindow = new CountDownLatch(1);
        CountDownLatch lifecycleAttempted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        doAnswer(invocation -> {
            InternWorkWindow window = (InternWorkWindow) invocation.callRealMethod();
            workWindowLocked.countDown();
            awaitLatch(releaseWorkWindow);
            return window;
        }).when(accountSpy).lockedInternWorkWindow(internId, firstLeaveDate);

        Future<?> policy = executor.submit(() -> policyLocks.hold(1L, policyLocked, releasePolicy));
        Future<LeaveRequestView> submission = null;
        Future<?> lifecycle = null;
        try {
            assertThat(policyLocked.await(10, TimeUnit.SECONDS)).isTrue();
            submission = executor.submit(() -> leaves.submit(
                    new AttendanceActor(internId, AttendanceRole.INTERN),
                    new LeaveRequestCommand(firstLeaveDate, lastLeaveDate, "profile lock contention")));
            boolean observedWorkWindowLock = workWindowLocked.await(10, TimeUnit.SECONDS);
            if (!observedWorkWindowLock) {
                releaseWorkWindow.countDown();
                releasePolicy.countDown();
            }
            assertThat(observedWorkWindowLock)
                    .as("leave submission must acquire the AccountService account/profile lock before quota work")
                    .isTrue();

            Future<?> lifecycleFuture = executor.submit(
                    () -> lifecycleMutations.deactivate(internId, adminId, lifecycleAttempted));
            lifecycle = lifecycleFuture;
            assertThat(lifecycleAttempted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> lifecycleFuture.get(2, TimeUnit.SECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseWorkWindow.countDown();
            assertThatThrownBy(() -> lifecycleFuture.get(2, TimeUnit.SECONDS))
                    .isInstanceOf(TimeoutException.class);

            releasePolicy.countDown();
            LeaveRequestView submitted = submission.get(10, TimeUnit.SECONDS);
            assertThat(submitted.status()).isEqualTo(LeaveStatus.PENDING);
            assertThat(submitted.allocations()).extracting(LeaveAllocation::leaveDate)
                    .containsExactly(firstLeaveDate, lastLeaveDate);
            lifecycle.get(10, TimeUnit.SECONDS);
            assertThat(accounts.requireIdentityById(internId).status()).isEqualTo(AccountStatus.DEACTIVATED);
            assertThat(leaveRequests.findById(submitted.id())).isPresent();
            assertThat(leaveDays.findByRequestIdOrderByLeaveDate(submitted.id())).hasSize(2);
        } finally {
            releaseWorkWindow.countDown();
            releasePolicy.countDown();
            awaitFuture(policy);
            if (submission != null) {
                awaitFuture(submission);
            }
            if (lifecycle != null) {
                awaitFuture(lifecycle);
            }
            executor.shutdownNow();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void rehydratedPendingLeaveCanBeCancelledBeforeItsFirstCountedStart() {
        AttendanceActor intern = new AttendanceActor(internId, AttendanceRole.INTERN);
        var submitted = leaves.submit(intern, new LeaveRequestCommand(
                LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 17), "rehydrate"));
        entityManager.clear();

        assertThat(leaves.cancel(intern, submitted.id()).status()).isEqualTo(LeaveStatus.CANCELLED);
        assertThat(leaveRequests.findById(submitted.id()).orElseThrow().status())
                .isEqualTo(LeaveStatus.CANCELLED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void approvedLeaveCancellationHonoursLev011BeforeAndAfterFirstCountedStart() {
        AttendanceActor intern = new AttendanceActor(internId, AttendanceRole.INTERN);
        long mentor = createActiveMentor();

        var beforeStart = leaves.submit(intern, new LeaveRequestCommand(
                LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 17), "approved then cancelled"));
        var approvedBeforeStart = leaves.approve(
                new AttendanceActor(mentor, AttendanceRole.MENTOR), beforeStart.id());
        assertThat(approvedBeforeStart.status()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(approvedBeforeStart.allocations()).hasSize(1);
        assertThat(leaveDays.countReservedExcluding(
                        internId,
                        LocalDate.of(2026, 8, 1),
                        List.of(LeaveStatus.PENDING.name(), LeaveStatus.APPROVED.name()),
                        null))
                .isEqualTo(1);

        var cancelled = leaves.cancel(intern, beforeStart.id());
        assertThat(cancelled.status()).isEqualTo(LeaveStatus.CANCELLED);
        assertThat(cancelled.allocations()).hasSize(1);
        assertThat(leaveDays.findByRequestIdOrderByLeaveDate(beforeStart.id())).hasSize(1);
        assertThat(leaveDays.countReservedExcluding(
                        internId,
                        LocalDate.of(2026, 8, 1),
                        List.of(LeaveStatus.PENDING.name(), LeaveStatus.APPROVED.name()),
                        null))
                .isZero();

        var afterStart = leaves.submit(intern, new LeaveRequestCommand(
                LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 19), "approved after boundary"));
        var approvedAfterStart = leaves.approve(
                new AttendanceActor(mentor, AttendanceRole.MENTOR), afterStart.id());
        clock.set(approvedAfterStart.firstCountedStartAt());

        assertThatThrownBy(() -> leaves.cancel(intern, afterStart.id()))
                .isInstanceOf(LeaveException.class);
        assertThat(leaveRequests.findById(afterStart.id()).orElseThrow().status())
                .isEqualTo(LeaveStatus.APPROVED);
        assertThat(leaveDays.findByRequestIdOrderByLeaveDate(afterStart.id())).hasSize(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void leaveNotificationsUseGlobalMentorsForSubmissionAndInternForDecisionWithoutCancellation() {
        AttendanceActor intern = new AttendanceActor(internId, AttendanceRole.INTERN);
        long mentor = createActiveMentor();
        createActiveMentor();
        List<Long> activeMentorIds = accounts.activeGlobalMentorIdentities().stream()
                .map(identity -> identity.id())
                .toList();
        assertThat(activeMentorIds).hasSize(2).doesNotHaveDuplicates();
        doReturn(false).when(mailDelivery).isAvailable();

        var submitted = leaves.submit(intern, new LeaveRequestCommand(
                LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 17), "notification leave"));
        assertThat(leaveRequests.findById(submitted.id()).orElseThrow().status())
                .isEqualTo(LeaveStatus.PENDING);

        List<NotificationEntity> submissions = notificationRows.findAll().stream()
                .filter(row -> row.getNotificationType() == NotificationType.LEAVE_SUBMITTED)
                .toList();
        assertThat(submissions)
                .extracting(NotificationEntity::getRecipientUserId)
                .containsExactlyInAnyOrderElementsOf(activeMentorIds)
                .doesNotHaveDuplicates();
        assertThat(submissions).allSatisfy(row ->
                assertThat(row.getEmailStatus()).isEqualTo(NotificationEmailStatus.UNAVAILABLE));

        leaves.approve(new AttendanceActor(mentor, AttendanceRole.MENTOR), submitted.id());
        assertThat(notificationRows.findAll()).filteredOn(row -> row.getNotificationType() == NotificationType.LEAVE_DECIDED)
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getRecipientUserId()).isEqualTo(internId);
                    assertThat(row.getBody()).contains("APPROVED");
                    assertThat(row.getActionUrl()).isEqualTo("/attendance");
                    assertThat(row.getEmailStatus()).isEqualTo(NotificationEmailStatus.UNAVAILABLE);
                });

        int notificationsBeforeCancellation = notificationRows.findAll().size();
        leaves.cancel(intern, submitted.id());
        assertThat(notificationRows.findAll()).hasSize(notificationsBeforeCancellation);

        var rejected = leaves.submit(intern, new LeaveRequestCommand(
                LocalDate.of(2026, 8, 18), LocalDate.of(2026, 8, 18), "manual rejection notification"));
        leaves.reject(new AttendanceActor(mentor, AttendanceRole.MENTOR), rejected.id());
        assertThat(notificationRows.findAll())
                .filteredOn(row -> row.getNotificationType() == NotificationType.LEAVE_DECIDED)
                .filteredOn(row -> row.getBody().contains("REJECTED"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getRecipientUserId()).isEqualTo(internId);
                    assertThat(row.getActionUrl()).isEqualTo("/attendance");
                    assertThat(row.getEmailStatus()).isEqualTo(NotificationEmailStatus.UNAVAILABLE);
                });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void leaveRequestTimeAutoRejectionPublishesOnceThenSchedulerIsIdempotent() {
        AttendanceActor intern = new AttendanceActor(internId, AttendanceRole.INTERN);
        createActiveMentor();
        doReturn(false).when(mailDelivery).isAvailable();
        var submitted = leaves.submit(intern, new LeaveRequestCommand(
                LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 17), "auto reject notification"));

        clock.set(submitted.firstCountedStartAt());
        assertThat(leaves.view(intern, submitted.id()).status()).isEqualTo(LeaveStatus.REJECTED);
        assertThat(leaves.expirePending(100)).isZero();
        assertThat(leaves.expirePending(100)).isZero();

        List<NotificationEntity> decisions = notificationRows.findAll().stream()
                .filter(row -> row.getNotificationType() == NotificationType.LEAVE_DECIDED)
                .toList();
        assertThat(decisions).singleElement().satisfies(row -> {
            assertThat(row.getRecipientUserId()).isEqualTo(internId);
            assertThat(row.getBody()).contains("AUTO_REJECTED");
            assertThat(row.getEmailStatus()).isEqualTo(NotificationEmailStatus.UNAVAILABLE);
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void correctionNotificationsCoverSubmissionDecisionRevertAndRequestTimeAutoRejectionOnce() {
        long mentor = createActiveMentor();
        createActiveMentor();
        List<Long> activeMentorIds = accounts.activeGlobalMentorIdentities().stream()
                .map(identity -> identity.id())
                .toList();
        assertThat(activeMentorIds).hasSize(2).doesNotHaveDuplicates();
        doReturn(false).when(mailDelivery).isAvailable();
        AttendanceActor intern = new AttendanceActor(internId, AttendanceRole.INTERN);
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        attendance.checkIn(internId);
        long recordId = records.findByInternUserIdAndWorkDate(internId, LocalDate.of(2026, 8, 14))
                .orElseThrow()
                .id();

        clock.set(Instant.parse("2026-08-14T09:01:00Z"));
        var submitted = corrections.submit(
                intern,
                recordId,
                new CorrectionRequestCommand(
                        java.time.LocalDateTime.of(2026, 8, 14, 14, 0), "notification correction"));
        assertThat(correctionRequests.findById(submitted.id()).orElseThrow().status())
                .isEqualTo(CorrectionStatus.PENDING);
        List<NotificationEntity> submissions = notificationRows.findAll().stream()
                .filter(row -> row.getNotificationType() == NotificationType.CORRECTION_SUBMITTED)
                .toList();
        assertThat(submissions)
                .extracting(NotificationEntity::getRecipientUserId)
                .containsExactlyInAnyOrderElementsOf(activeMentorIds)
                .doesNotHaveDuplicates();
        assertThat(submissions).allSatisfy(row ->
                assertThat(row.getEmailStatus()).isEqualTo(NotificationEmailStatus.UNAVAILABLE));

        corrections.decide(
                new AttendanceActor(mentor, AttendanceRole.MENTOR),
                submitted.id(),
                CorrectionDecision.APPROVE,
                null);
        corrections.decide(
                new AttendanceActor(mentor, AttendanceRole.MENTOR),
                submitted.id(),
                CorrectionDecision.REOPEN,
                "reopen for review");
        clock.set(submitted.decisionDeadline());
        assertThat(corrections.view(intern, submitted.id()).status()).isEqualTo(CorrectionStatus.REJECTED);
        assertThat(corrections.expire(100)).isZero();
        assertThat(corrections.expire(100)).isZero();

        clock.set(Instant.parse("2026-08-17T02:00:00Z"));
        attendance.checkIn(internId);
        long rejectedRecordId = records.findByInternUserIdAndWorkDate(internId, LocalDate.of(2026, 8, 17))
                .orElseThrow()
                .id();
        clock.set(Instant.parse("2026-08-17T09:01:00Z"));
        var rejected = corrections.submit(
                intern,
                rejectedRecordId,
                new CorrectionRequestCommand(
                        java.time.LocalDateTime.of(2026, 8, 17, 14, 0), "manual rejection notification"));
        corrections.decide(
                new AttendanceActor(mentor, AttendanceRole.MENTOR),
                rejected.id(),
                CorrectionDecision.REJECT,
                "manual rejection");

        List<NotificationEntity> decisions = notificationRows.findAll().stream()
                .filter(row -> row.getNotificationType() == NotificationType.CORRECTION_DECIDED)
                .toList();
        assertThat(decisions).hasSize(4);
        assertThat(decisions).allSatisfy(row -> {
            assertThat(row.getRecipientUserId()).isEqualTo(internId);
            assertThat(row.getActionUrl()).isEqualTo("/attendance");
            assertThat(row.getEmailStatus()).isEqualTo(NotificationEmailStatus.UNAVAILABLE);
        });
        assertThat(decisions).extracting(NotificationEntity::getBody)
                .anySatisfy(body -> assertThat(body).contains("APPROVED"))
                .anySatisfy(body -> assertThat(body).contains("REVERTED"))
                .anySatisfy(body -> {
                    assertThat(body).contains("REJECTED");
                    assertThat(body).doesNotContain("AUTO_REJECTED");
                })
                .anySatisfy(body -> assertThat(body).contains("AUTO_REJECTED"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void recipientAccountLocksPrecedeLeaveAndCorrectionRows() throws Exception {
        long mentor = createActiveMentor();
        doReturn(false).when(mailDelivery).isAvailable();
        AttendanceActor intern = new AttendanceActor(internId, AttendanceRole.INTERN);
        var leave = leaves.submit(intern, new LeaveRequestCommand(
                LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 17), "lock order leave"));

        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            AtomicInteger calls = new AtomicInteger();
            CountDownLatch leaveAccountHeld = new CountDownLatch(1);
            CountDownLatch leaveServiceWaiting = new CountDownLatch(1);
            CountDownLatch releaseLeaveAccount = new CountDownLatch(1);
            doAnswer(invocation -> {
                if (calls.getAndIncrement() == 0) {
                    Object result = invocation.callRealMethod();
                    leaveAccountHeld.countDown();
                    awaitLatch(releaseLeaveAccount);
                    return result;
                }
                leaveServiceWaiting.countDown();
                return invocation.callRealMethod();
            }).when(accountSpy).lockedAccountMutationEligibility(any());

            Future<?> holder = executor.submit(() -> transactions.execute(status -> {
                accountSpy.lockedAccountMutationEligibility(List.of(internId, mentor));
                return null;
            }));
            assertThat(leaveAccountHeld.await(10, TimeUnit.SECONDS)).isTrue();
            Future<LeaveRequestView> decision = executor.submit(() -> leaves.approve(
                    new AttendanceActor(mentor, AttendanceRole.MENTOR), leave.id()));
            assertThat(leaveServiceWaiting.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Optional<LeaveRequestEntity>> rowProbe = executor.submit(
                    () -> transactions.execute(status -> leaveRequests.findForUpdateById(leave.id())));
            Optional<LeaveRequestEntity> probedLeave = rowProbe.get(2, TimeUnit.SECONDS);
            assertThat(probedLeave).isPresent();
            assertThat(probedLeave.orElseThrow().status()).isEqualTo(LeaveStatus.PENDING);
            releaseLeaveAccount.countDown();
            assertThat(decision.get(10, TimeUnit.SECONDS).status()).isEqualTo(LeaveStatus.APPROVED);
            holder.get(10, TimeUnit.SECONDS);

            clock.set(Instant.parse("2026-08-14T02:00:00Z"));
            attendance.checkIn(internId);
            long recordId = records.findByInternUserIdAndWorkDate(internId, LocalDate.of(2026, 8, 14))
                    .orElseThrow()
                    .id();
            clock.set(Instant.parse("2026-08-14T09:01:00Z"));
            var correction = corrections.submit(
                    intern,
                    recordId,
                    new CorrectionRequestCommand(
                            java.time.LocalDateTime.of(2026, 8, 14, 14, 0), "lock order correction"));
            clock.set(correction.decisionDeadline());

            reset(accountSpy);
            calls.set(0);
            CountDownLatch secondAccountHeld = new CountDownLatch(1);
            CountDownLatch secondServiceWaiting = new CountDownLatch(1);
            CountDownLatch secondReleaseAccount = new CountDownLatch(1);
            doAnswer(invocation -> {
                if (calls.getAndIncrement() == 0) {
                    Object result = invocation.callRealMethod();
                    secondAccountHeld.countDown();
                    awaitLatch(secondReleaseAccount);
                    return result;
                }
                secondServiceWaiting.countDown();
                return invocation.callRealMethod();
            }).when(accountSpy).lockedAccountMutationEligibility(any());

            Future<?> secondHolder = executor.submit(() -> transactions.execute(status -> {
                accountSpy.lockedAccountMutationEligibility(List.of(internId));
                return null;
            }));
            assertThat(secondAccountHeld.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Integer> expiry = executor.submit(() -> corrections.expire(1));
            assertThat(secondServiceWaiting.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Optional<AttendanceCorrectionEntity>> correctionProbe = executor.submit(
                    () -> transactions.execute(status -> correctionRequests.findForUpdateById(correction.id())));
            Optional<AttendanceCorrectionEntity> probedCorrection = correctionProbe.get(2, TimeUnit.SECONDS);
            assertThat(probedCorrection).isPresent();
            assertThat(probedCorrection.orElseThrow().status()).isEqualTo(CorrectionStatus.PENDING);
            secondReleaseAccount.countDown();
            assertThat(expiry.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            secondHolder.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void lateLeaveCancellationCommitsExpiryBeforeRejectingMutation() {
        AttendanceActor intern = new AttendanceActor(internId, AttendanceRole.INTERN);
        var submitted = leaves.submit(intern, new LeaveRequestCommand(
                LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 17), "late cancel"));

        clock.set(submitted.firstCountedStartAt());
        assertThatThrownBy(() -> leaves.cancel(intern, submitted.id()))
                .isInstanceOf(LeaveException.class);
        assertThat(leaveRequests.findById(submitted.id()).orElseThrow().status())
                .isEqualTo(LeaveStatus.REJECTED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void ambientTransactionLateMutationCommitsExpiryBeforePublicException() {
        AttendanceActor intern = new AttendanceActor(internId, AttendanceRole.INTERN);
        long mentor = createActiveMentor();
        var submittedLeave = leaves.submit(intern, new LeaveRequestCommand(
                LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 17), "ambient late cancel"));

        clock.set(submittedLeave.firstCountedStartAt());
        assertThatThrownBy(() -> ambientMutations.cancelLeave(intern, submittedLeave.id()))
                .isInstanceOf(LeaveException.class);
        assertThat(leaveRequests.findById(submittedLeave.id()).orElseThrow().status())
                .isEqualTo(LeaveStatus.REJECTED);

        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        attendance.checkIn(internId);
        long recordId = records.findByInternUserIdAndWorkDate(internId, LocalDate.of(2026, 8, 14))
                .orElseThrow()
                .id();
        clock.set(Instant.parse("2026-08-14T09:01:00Z"));
        var submittedCorrection = corrections.submit(
                intern,
                recordId,
                new CorrectionRequestCommand(
                        java.time.LocalDateTime.of(2026, 8, 14, 14, 0), "ambient late decision"));

        clock.set(submittedCorrection.decisionDeadline());
        assertThatThrownBy(() -> ambientMutations.decideCorrection(
                        new AttendanceActor(mentor, AttendanceRole.MENTOR),
                        submittedCorrection.id(),
                        CorrectionDecision.APPROVE))
                .isInstanceOf(CorrectionException.class);
        assertThat(correctionRequests.findById(submittedCorrection.id()).orElseThrow().status())
                .isEqualTo(CorrectionStatus.REJECTED);
        assertThat(correctionRequests.findById(submittedCorrection.id()).orElseThrow().lockedAt())
                .isEqualTo(submittedCorrection.decisionDeadline());
        assertThat(correctionEvents.findByCorrectionIdOrderByOccurredAtAscIdAsc(submittedCorrection.id()))
                .extracting(event -> event.toView().type())
                .containsExactly(
                        CorrectionEventType.SUBMITTED,
                        CorrectionEventType.AUTO_REJECTED,
                        CorrectionEventType.LOCKED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void unauthorizedLateLeaveCancellationDoesNotExpireRequest() {
        AttendanceActor owner = new AttendanceActor(internId, AttendanceRole.INTERN);
        var submitted = leaves.submit(owner, new LeaveRequestCommand(
                LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 17), "owner only"));

        clock.set(submitted.firstCountedStartAt());
        assertThatThrownBy(() -> leaves.edit(
                        new AttendanceActor(internId + 100, AttendanceRole.INTERN),
                        submitted.id(),
                        new LeaveRequestCommand(
                                LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 17), "guessed edit")))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(leaveRequests.findById(submitted.id()).orElseThrow().status())
                .isEqualTo(LeaveStatus.PENDING);
        assertThatThrownBy(() -> leaves.cancel(
                        new AttendanceActor(internId + 100, AttendanceRole.INTERN), submitted.id()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(leaveRequests.findById(submitted.id()).orElseThrow().status())
                .isEqualTo(LeaveStatus.PENDING);
    }

    @Test
    void leaveExpiryHonoursDatabaseBatchLimit() {
        Instant submittedAt = Instant.parse("2026-08-13T00:00:00Z");
        Instant firstCountedStart = Instant.parse("2026-08-14T01:00:00Z");
        LeaveRequestEntity first = new LeaveRequestEntity(
                internId,
                LocalDate.of(2026, 8, 17),
                LocalDate.of(2026, 8, 17),
                "first backlog",
                submittedAt,
                firstCountedStart);
        LeaveRequestEntity second = new LeaveRequestEntity(
                internId,
                LocalDate.of(2026, 8, 18),
                LocalDate.of(2026, 8, 18),
                "second backlog",
                submittedAt,
                firstCountedStart);
        entityManager.persist(first);
        entityManager.persist(second);
        entityManager.flush();

        clock.set(firstCountedStart);
        assertThat(leaves.expirePending(1)).isEqualTo(1);
        assertThat(leaveRequests.findAll()).extracting(LeaveRequestEntity::status)
                .containsExactlyInAnyOrder(LeaveStatus.REJECTED, LeaveStatus.PENDING);
        assertThat(leaves.expirePending(1)).isEqualTo(1);
        assertThat(leaveRequests.findAll()).extracting(LeaveRequestEntity::status)
                .containsOnly(LeaveStatus.REJECTED);
    }

    @Test
    void correctionKeepsRawCheckoutNullAndLocksOnceAtSeparateDecisionDeadline() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        attendance.checkIn(internId);
        long recordId = records.findByInternUserIdAndWorkDate(internId, LocalDate.of(2026, 8, 14))
                .orElseThrow()
                .id();
        clock.set(Instant.parse("2026-08-14T09:00:00Z"));
        assertThatThrownBy(() -> corrections.submit(
                        new AttendanceActor(internId, AttendanceRole.INTERN),
                        recordId,
                        new CorrectionRequestCommand(
                                java.time.LocalDateTime.of(2026, 8, 14, 14, 0),
                                "too early")))
                .isInstanceOf(CorrectionException.class);
        clock.set(Instant.parse("2026-08-14T09:01:00Z"));

        var submitted = corrections.submit(
                new AttendanceActor(internId, AttendanceRole.INTERN),
                recordId,
                new CorrectionRequestCommand(
                        java.time.LocalDateTime.of(2026, 8, 14, 14, 0),
                        "forgot to check out"));
        assertThat(submitted.status()).isEqualTo(CorrectionStatus.PENDING);
        assertThat(submitted.rawCheckoutAt()).isNull();
        assertThat(submitted.effectiveCheckoutAt()).isNull();

        clock.set(submitted.decisionDeadline());
        assertThat(corrections.expire(100)).isEqualTo(1);
        var expired = corrections.view(new AttendanceActor(internId, AttendanceRole.INTERN), submitted.id());
        assertThat(expired.status()).isEqualTo(CorrectionStatus.REJECTED);
        assertThat(expired.lockedAt()).isEqualTo(submitted.decisionDeadline());
        assertThat(correctionRequests.findById(submitted.id()).orElseThrow().requestedCheckoutAt())
                .isEqualTo(expired.proposedCheckout().atZone(expired.policy().zoneId()).toInstant());
        assertThat(correctionEvents.findByCorrectionIdOrderByOccurredAtAscIdAsc(submitted.id()))
                .extracting(event -> event.toView().type().name())
                .containsExactly("SUBMITTED", "AUTO_REJECTED", "LOCKED");
        assertThat(corrections.expire(100)).isEqualTo(0);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void lateCorrectionDecisionCommitsExpiryBeforeRejectingDecision() {
        long mentor = createActiveMentor();
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        attendance.checkIn(internId);
        long recordId = records.findByInternUserIdAndWorkDate(internId, LocalDate.of(2026, 8, 14))
                .orElseThrow()
                .id();
        clock.set(Instant.parse("2026-08-14T09:01:00Z"));
        var submitted = corrections.submit(
                new AttendanceActor(internId, AttendanceRole.INTERN),
                recordId,
                new CorrectionRequestCommand(
                        java.time.LocalDateTime.of(2026, 8, 14, 14, 0),
                        "late decision"));

        clock.set(submitted.decisionDeadline());
        assertThatThrownBy(() -> corrections.decide(
                        new AttendanceActor(mentor, AttendanceRole.MENTOR),
                        submitted.id(),
                        CorrectionDecision.APPROVE,
                        "too late"))
                .isInstanceOf(CorrectionException.class);
        assertThat(correctionRequests.findById(submitted.id()).orElseThrow().status())
                .isEqualTo(CorrectionStatus.REJECTED);
        assertThat(correctionRequests.findById(submitted.id()).orElseThrow().lockedAt())
                .isEqualTo(submitted.decisionDeadline());
    }

    @Test
    void correctionExpiryHonoursDatabaseBatchLimit() {
        AttendancePolicyEntity policy = entityManager.getReference(AttendancePolicyEntity.class, 1L);
        AttendanceRecordEntity firstRecord = new AttendanceRecordEntity(
                internId,
                LocalDate.of(2026, 8, 14),
                policy,
                Instant.parse("2026-08-14T02:00:00Z"),
                null);
        AttendanceRecordEntity secondRecord = new AttendanceRecordEntity(
                internId,
                LocalDate.of(2026, 8, 15),
                policy,
                Instant.parse("2026-08-15T02:00:00Z"),
                null);
        entityManager.persist(firstRecord);
        entityManager.persist(secondRecord);
        entityManager.flush();
        Instant submittedAt = Instant.parse("2026-08-14T09:01:00Z");
        Instant decisionDeadline = Instant.parse("2026-08-15T09:01:00Z");
        AttendanceCorrectionEntity first = new AttendanceCorrectionEntity(
                firstRecord.id(),
                Instant.parse("2026-08-14T06:00:00Z"),
                "first correction",
                submittedAt,
                Instant.parse("2026-08-15T09:00:00Z"),
                decisionDeadline);
        AttendanceCorrectionEntity second = new AttendanceCorrectionEntity(
                secondRecord.id(),
                Instant.parse("2026-08-15T06:00:00Z"),
                "second correction",
                submittedAt,
                Instant.parse("2026-08-15T09:00:00Z"),
                decisionDeadline);
        entityManager.persist(first);
        entityManager.persist(second);
        entityManager.flush();

        clock.set(Instant.parse("2026-08-16T00:00:00Z"));
        assertThat(corrections.expire(1)).isEqualTo(1);
        assertThat(correctionRequests.findAll()).extracting(AttendanceCorrectionEntity::lockedAt)
                .satisfiesExactlyInAnyOrder(
                        locked -> assertThat(locked).isNotNull(),
                        unlocked -> assertThat(unlocked).isNull());
        assertThat(corrections.expire(1)).isEqualTo(1);
        assertThat(correctionRequests.findAll()).allSatisfy(row -> assertThat(row.lockedAt()).isNotNull());
    }

    @Test
    void attendanceHistoryUsesApprovedEffectiveCheckoutWithoutChangingRawPunch() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        attendance.checkIn(internId);
        AttendanceRecordEntity raw = records.findByInternUserIdAndWorkDate(
                        internId, LocalDate.of(2026, 8, 14))
                .orElseThrow();
        clock.set(Instant.parse("2026-08-14T09:01:00Z"));
        Instant effectiveCheckout = Instant.parse("2026-08-14T06:00:00Z");
        AttendanceCorrectionEntity correction = new AttendanceCorrectionEntity(
                raw.id(),
                effectiveCheckout,
                "approved history",
                clock.instant(),
                clock.instant().plusSeconds(24 * 60 * 60L),
                clock.instant().plusSeconds(24 * 60 * 60L));
        correction.approve(adminId, clock.instant(), null);
        entityManager.persist(correction);
        entityManager.flush();

        AttendanceHistoryItem item = attendance.history(
                        new AttendanceActor(internId, AttendanceRole.INTERN),
                        internId,
                        LocalDate.of(2026, 8, 14),
                        LocalDate.of(2026, 8, 14))
                .getFirst();
        assertThat(item.checkOutAt()).isEqualTo(effectiveCheckout);
        assertThat(item.violations().missingCheckout()).isFalse();
        assertThat(item.violations().earlyDeparture()).isTrue();
        assertThat(records.findById(raw.id()).orElseThrow().toDomain().checkOutAt()).isNull();
    }

    @Test
    void historyAccessExpiresPendingCorrectionBeforeRendering() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        attendance.checkIn(internId);
        long recordId = records.findByInternUserIdAndWorkDate(internId, LocalDate.of(2026, 8, 14))
                .orElseThrow()
                .id();
        clock.set(Instant.parse("2026-08-14T09:01:00Z"));
        var submitted = corrections.submit(
                new AttendanceActor(internId, AttendanceRole.INTERN),
                recordId,
                new CorrectionRequestCommand(
                        java.time.LocalDateTime.of(2026, 8, 14, 14, 0),
                        "history expiry"));

        clock.set(submitted.decisionDeadline());
        AttendanceHistoryItem item = attendance.history(
                        new AttendanceActor(internId, AttendanceRole.INTERN),
                        internId,
                        LocalDate.of(2026, 8, 14),
                        LocalDate.of(2026, 8, 14))
                .getFirst();

        assertThat(item.checkOutAt()).isNull();
        assertThat(item.violations().missingCheckout()).isTrue();
        assertThat(correctionRequests.findById(submitted.id()).orElseThrow().status())
                .isEqualTo(CorrectionStatus.REJECTED);
        assertThat(correctionRequests.findById(submitted.id()).orElseThrow().lockedAt())
                .isEqualTo(submitted.decisionDeadline());
    }

    @Test
    void ownHistoryAndMentorAdminInspectionAreAuthorized() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        attendance.checkIn(internId);
        LocalDate date = LocalDate.of(2026, 8, 14);

        assertThat(attendance.history(
                        new AttendanceActor(internId, AttendanceRole.INTERN), internId, date, date))
                .hasSize(1);
        assertThat(attendance.history(
                        new AttendanceActor(mentorId, AttendanceRole.MENTOR), internId, date, date))
                .hasSize(1);
        assertThat(attendance.history(
                        new AttendanceActor(adminId, AttendanceRole.ADMIN), internId, date, date))
                .hasSize(1);
        assertThatThrownBy(() -> attendance.history(
                        new AttendanceActor(internId + 100, AttendanceRole.INTERN), internId, date, date))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void currentActorComesFromActiveNormalizedAccountServiceIdentity() {
        assertThat(currentUsers.actor(() -> " INTERN@EXAMPLE.TEST "))
                .isEqualTo(new AttendanceActor(internId, AttendanceRole.INTERN));

        assertThatThrownBy(() -> currentUsers.actor(() -> "missing@example.test"))
                .isInstanceOf(AccessDeniedException.class);
    }

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

        @Bean
        @Primary
        RecordingSmtpProbe recordingSmtpProbe() {
            return new RecordingSmtpProbe();
        }

        @Bean
        AmbientMutationInvoker ambientMutationInvoker(
                LeaveApplicationService leaves, AttendanceCorrectionApplicationService corrections) {
            return new AmbientMutationInvoker(leaves, corrections);
        }

        @Bean
        LifecycleMutationInvoker lifecycleMutationInvoker(AccountService accounts) {
            return new LifecycleMutationInvoker(accounts);
        }

        @Bean
        PolicyLockInvoker policyLockInvoker(AttendancePolicyRepository policies) {
            return new PolicyLockInvoker(policies);
        }
    }

    static class AmbientMutationInvoker {

        private final LeaveApplicationService leaves;
        private final AttendanceCorrectionApplicationService corrections;

        AmbientMutationInvoker(
                LeaveApplicationService leaves, AttendanceCorrectionApplicationService corrections) {
            this.leaves = leaves;
            this.corrections = corrections;
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void cancelLeave(AttendanceActor actor, long requestId) {
            leaves.cancel(actor, requestId);
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void decideCorrection(AttendanceActor actor, long correctionId, CorrectionDecision decision) {
            corrections.decide(actor, correctionId, decision, "ambient");
        }
    }

    static class LifecycleMutationInvoker {

        private final AccountService accounts;

        LifecycleMutationInvoker(AccountService accounts) {
            this.accounts = accounts;
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void deactivate(long internId, long adminId, CountDownLatch attempted) {
            attempted.countDown();
            accounts.deactivateAccount(internId, adminId);
        }
    }

    static class PolicyLockInvoker {

        private final AttendancePolicyRepository policies;

        PolicyLockInvoker(AttendancePolicyRepository policies) {
            this.policies = policies;
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void hold(long policyId, CountDownLatch locked, CountDownLatch release) {
            policies.findForUpdateById(policyId).orElseThrow();
            locked.countDown();
            awaitLatch(release);
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(60, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for the concurrency barrier");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for the concurrency barrier", exception);
        }
    }

    private static void awaitFuture(Future<?> future) {
        try {
            future.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Concurrency worker did not finish cleanly", exception);
        }
    }

    static final class RecordingSmtpProbe implements SmtpProbe {

        private final List<String> messages = new ArrayList<>();

        @Override
        public void send(SmtpConnection connection, String recipient, String subject, String body) {
            messages.add(body);
        }

        void clear() {
            messages.clear();
        }

        String onlyActivationToken() {
            assertThat(messages).hasSize(1);
            String body = messages.getFirst();
            int tokenStart = body.indexOf("token=");
            assertThat(tokenStart).isGreaterThanOrEqualTo(0);
            return body.substring(tokenStart + "token=".length()).trim();
        }
    }

    static final class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
