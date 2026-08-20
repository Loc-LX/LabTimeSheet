package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.account.service.BootstrapService;
import com.lab.labtimesheet.feature.attendance.exception.LeaveException;
import com.lab.labtimesheet.feature.attendance.exception.LeaveRejection;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveOverview;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveSubmission;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveSubmissionCommand;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendancePolicyEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestDayEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestEntity;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestDayRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestRepository;
import com.lab.labtimesheet.feature.integration.model.SecurityMode;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpDraft;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static com.lab.labtimesheet.feature.attendance.model.entity.LeaveEntityFixtures.allocatedDay;
import static com.lab.labtimesheet.feature.attendance.model.entity.LeaveEntityFixtures.approvedRequest;

@Import(AttendancePersistenceIntegrationTest.IntegrationConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LeaveMaterializationIntegrationTest {

    @Autowired
    private LeaveService leave;

    @Autowired
    private CalendarApplicationService calendar;

    @Autowired
    private LeaveRequestRepository requests;

    @Autowired
    private LeaveRequestDayRepository days;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AttendancePersistenceIntegrationTest.MutableClock clock;

    @Autowired
    private BootstrapService bootstrap;

    @Autowired
    private AccountService accounts;

    @Autowired
    private SmtpConfigurationService smtp;

    @Autowired
    private AttendancePersistenceIntegrationTest.RecordingSmtpProbe mail;

    private long internId;
    private long adminId;

    @BeforeEach
    void seedUsers() {
        clock.set(Instant.parse("2026-08-14T00:00:00Z"));
        bootstrap.bootstrap("leave-admin@example.test", "Admin", "correct horse battery staple");
        adminId = accounts.requireActiveAdminId("leave-admin@example.test");
        long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                "mailpit",
                1025,
                SecurityMode.NONE,
                null,
                null,
                "leave-admin@example.test",
                "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "leave-admin@example.test");
        smtp.activate(draftId, adminId);
        mail.clear();

        var creation = accounts.create(new CreateAccountCommand(
                "leave-intern@example.test",
                "Leave Intern",
                GlobalRole.INTERN,
                "INT-LEAVE",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31)), adminId);
        assertThat(creation.deliverySucceeded()).isTrue();
        assertThat(accounts.activate(mail.onlyActivationToken(), "new secure intern password")).isTrue();
        accounts.activateInternship(creation.userId(), adminId);
        internId = creation.userId();
    }

    @Test
    void submitMaterializesOnlyEligibleWorkdaysWithMonthAndQuotaSnapshot() {
        calendar.createManual(
                new AttendanceActor(adminId, AttendanceRole.ADMIN),
                LocalDate.of(2026, 9, 2),
                "Team holiday",
                true);

        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        LeaveSubmission submission = leave.submit(
                internId, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 8, 31),
                        LocalDate.of(2026, 9, 4),
                        "Family trip"));

        assertThat(submission.status()).isEqualTo("PENDING");
        assertThat(submission.countedDays())
                .extracting(
                        com.lab.labtimesheet.feature.attendance.model.dto.CountedLeaveDay::date,
                        com.lab.labtimesheet.feature.attendance.model.dto.CountedLeaveDay::quotaMonth,
                        com.lab.labtimesheet.feature.attendance.model.dto.CountedLeaveDay::monthlyQuotaSnapshot)
                .containsExactly(
                        tuple(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1), 3),
                        tuple(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), 3),
                        tuple(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 1), 3),
                        tuple(LocalDate.of(2026, 9, 4), LocalDate.of(2026, 9, 1), 3));

        LeaveRequestEntity request = requests.findById(submission.requestId()).orElseThrow();
        assertThat(request.internUserId()).isEqualTo(internId);
        assertThat(request.startDate()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(request.endDate()).isEqualTo(LocalDate.of(2026, 9, 4));
        assertThat(request.reason()).isEqualTo("Family trip");
        assertThat(request.status()).isEqualTo("PENDING");
        assertThat(request.submittedAt()).isEqualTo(Instant.parse("2026-08-14T02:00:00Z"));
        assertThat(request.firstCountedStartAt()).isEqualTo(Instant.parse("2026-08-31T01:30:00Z"));

        List<LeaveRequestDayEntity> frozen = days.findLeaveDatesByRequestId(submission.requestId()).stream()
                .map(date -> entityManager.find(
                        LeaveRequestDayEntity.class,
                        new com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestDayId(
                                submission.requestId(), date)))
                .toList();
        assertThat(frozen).hasSize(4);
        assertThat(frozen)
                .extracting(
                        LeaveRequestDayEntity::leaveDate,
                        LeaveRequestDayEntity::quotaMonth,
                        LeaveRequestDayEntity::monthlyQuotaSnapshot)
                .containsExactly(
                        tuple(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1), 3),
                        tuple(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), 3),
                        tuple(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 1), 3),
                        tuple(LocalDate.of(2026, 9, 4), LocalDate.of(2026, 9, 1), 3));
    }

    @Test
    void onlyPendingAndApprovedDaysReserveQuotaAndRejectionReleasesIt() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        LeaveRequestEntity approved = approvedRequest(
                internId,
                LocalDate.of(2026, 8, 17),
                LocalDate.of(2026, 8, 17),
                Instant.parse("2026-08-13T00:00:00Z"),
                Instant.parse("2026-08-17T01:30:00Z"),
                adminId,
                Instant.parse("2026-08-13T00:30:00Z"));
        entityManager.persist(approved);
        entityManager.flush();
        entityManager.persist(allocatedDay(
                approved,
                LocalDate.of(2026, 8, 17),
                entityManager.getReference(AttendancePolicyEntity.class, 1L),
                3));
        entityManager.flush();

        LeaveOverview afterApproved = leave.overview(internId, LocalDate.of(2026, 8, 1));
        assertThat(afterApproved.quota()).isEqualTo(3);
        assertThat(afterApproved.reserved()).isEqualTo(1);
        assertThat(afterApproved.available()).isEqualTo(2);

        LeaveSubmission pending = leave.submit(
                internId, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 8, 18),
                        LocalDate.of(2026, 8, 18),
                        "Clinic visit"));

        LeaveOverview withPending = leave.overview(internId, LocalDate.of(2026, 8, 1));
        assertThat(withPending.reserved()).isEqualTo(2);
        assertThat(withPending.available()).isEqualTo(1);

        entityManager.createQuery(
                        "update LeaveRequestEntity r set r.status = 'REJECTED', r.decidedAt = :now where r.id = :id")
                .setParameter("now", Instant.parse("2026-08-14T02:00:00Z"))
                .setParameter("id", pending.requestId())
                .executeUpdate();
        entityManager.flush();

        LeaveOverview afterRejection = leave.overview(internId, LocalDate.of(2026, 8, 1));
        assertThat(afterRejection.reserved()).isEqualTo(1);
        assertThat(afterRejection.available()).isEqualTo(2);
    }

    @Test
    void overQuotaSubmissionIsRejectedWithoutPersistingAnyRow() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        LeaveRequestEntity approved = approvedRequest(
                internId,
                LocalDate.of(2026, 8, 17),
                LocalDate.of(2026, 8, 19),
                Instant.parse("2026-08-13T00:00:00Z"),
                Instant.parse("2026-08-17T01:30:00Z"),
                adminId,
                Instant.parse("2026-08-13T00:30:00Z"));
        entityManager.persist(approved);
        entityManager.flush();
        for (LocalDate date : List.of(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 18), LocalDate.of(2026, 8, 19))) {
            entityManager.persist(allocatedDay(
                    approved,
                    date,
                    entityManager.getReference(AttendancePolicyEntity.class, 1L),
                    3));
        }
        entityManager.flush();

        assertThatThrownBy(() -> leave.submit(
                internId, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 8, 20),
                        LocalDate.of(2026, 8, 20),
                        "Exceeds the month")))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.QUOTA_EXCEEDED));

        assertThat(requests.count()).isEqualTo(1);
        assertThat(days.count()).isEqualTo(3);
    }

    @Test
    void rangeFullyOutsideInternshipOrWeekendHasNoCountedDay() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));

        assertThatThrownBy(() -> leave.submit(
                internId, new LeaveSubmissionCommand(
                        LocalDate.of(2027, 1, 3),
                        LocalDate.of(2027, 1, 4),
                        "After internship")))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.NO_COUNTED_DAYS));

        assertThatThrownBy(() -> leave.submit(
                internId, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 8, 15),
                        LocalDate.of(2026, 8, 16),
                        "Weekend only")))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.NO_COUNTED_DAYS));

        assertThat(requests.count()).isZero();
        assertThat(days.count()).isZero();
    }
}