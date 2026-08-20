package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.account.service.BootstrapService;
import com.lab.labtimesheet.feature.attendance.exception.AttendanceException;
import com.lab.labtimesheet.feature.attendance.exception.AttendanceRejection;
import com.lab.labtimesheet.feature.attendance.exception.PolicyException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.SchedulePolicyCommand;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendancePolicyEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendancePolicyRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceRecordRepository;
import com.lab.labtimesheet.feature.integration.model.SecurityMode;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpDraft;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@Import(AttendancePersistenceIntegrationTest.IntegrationConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AttendancePolicySchedulingIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired
    private AttendancePolicyService policies;

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

    @Autowired
    private AttendanceRecordRepository records;

    @Autowired
    private AttendancePolicyRepository policyEntities;

    private long adminId;
    private long internId;
    private AttendanceActor admin;

    @BeforeEach
    void seedUsers() {
        clock.set(Instant.parse("2026-08-14T00:00:00Z"));
        bootstrap.bootstrap("policy-admin@example.test", "Admin", "correct horse battery staple");
        adminId = accounts.requireActiveAdminId("policy-admin@example.test");
        long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                "mailpit",
                1025,
                SecurityMode.NONE,
                null,
                null,
                "policy-admin@example.test",
                "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "policy-admin@example.test");
        smtp.activate(draftId, adminId);
        mail.clear();

        var creation = accounts.create(new CreateAccountCommand(
                "policy-intern@example.test",
                "Policy Intern",
                GlobalRole.INTERN,
                "INT-POLICY",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31)), adminId);
        assertThat(creation.deliverySucceeded()).isTrue();
        assertThat(accounts.activate(mail.onlyActivationToken(), "new secure intern password")).isTrue();
        accounts.activateInternship(creation.userId(), adminId);
        internId = creation.userId();
        admin = new AttendanceActor(adminId, AttendanceRole.ADMIN);
    }

    @Test
    void scheduledFuturePolicyResolvesForNewDatesWithoutChangingOldCutoffOrReports() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        attendance.checkIn(internId);

        AttendancePolicy scheduled = policies.scheduleVersion(
                admin, command(LocalDate.of(2026, 9, 1), 20, 0));

        assertThat(scheduled.effectiveFrom()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(scheduled.checkInGraceMinutes()).isEqualTo(20);
        assertThat(scheduled.checkoutGraceMinutes()).isZero();
        assertThat(scheduled.monthlyLeaveQuota()).isEqualTo(5);
        assertThat(scheduled.violationPenalty()).isEqualByComparingTo(new BigDecimal("0.5"));

        assertThat(policies.timeline()).hasSize(2);
        AttendancePolicy seed = new AttendancePolicyTimeline(policies.timeline()).resolve(LocalDate.of(2026, 8, 14));
        assertThat(seed.checkoutGraceMinutes()).isEqualTo(30);
        AttendancePolicy future = new AttendancePolicyTimeline(policies.timeline()).resolve(LocalDate.of(2026, 9, 1));
        assertThat(future.checkoutGraceMinutes()).isZero();

        var oldRow = records.findByInternUserIdAndWorkDate(internId, LocalDate.of(2026, 8, 14)).orElseThrow().toDomain();
        assertThat(oldRow.checkOutAt()).isNull();
        assertThat(oldRow.violations(Instant.parse("2026-08-14T08:31:00Z")).missingCheckout()).isFalse();
        assertThat(oldRow.violations(Instant.parse("2026-08-14T09:00:00Z")).missingCheckout()).isFalse();
        assertThat(oldRow.violations(Instant.parse("2026-08-14T09:00:00.001Z")).missingCheckout()).isTrue();

        clock.set(Instant.parse("2026-09-01T01:30:00Z"));
        attendance.checkIn(internId);
        clock.set(Instant.parse("2026-09-01T09:00:00Z"));
        assertThatThrownBy(() -> attendance.checkOut(internId))
                .isInstanceOfSatisfying(AttendanceException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(AttendanceRejection.CHECKOUT_CUTOFF_PASSED));

        clock.set(Instant.parse("2026-09-01T08:30:00Z"));
        attendance.checkOut(internId);
        assertThat(records.findByInternUserIdAndWorkDate(internId, LocalDate.of(2026, 9, 1))
                .orElseThrow()
                .toDomain()
                .checkOutAt())
                .isEqualTo(Instant.parse("2026-09-01T08:30:00Z"));
    }

    @Test
    void replaceIsAllowedBeforeEffectiveDateAndRejectedOnceEffective() {
        AttendancePolicy scheduled = policies.scheduleVersion(
                admin, command(LocalDate.of(2026, 9, 1), 20, 0));

        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        policies.replaceVersion(admin, scheduled.id(), scheduledVersion(scheduled.id()), command(LocalDate.of(2026, 9, 1), 10, 30));
        assertThat(new AttendancePolicyTimeline(policies.timeline())
                .resolve(LocalDate.of(2026, 9, 1)).checkInGraceMinutes()).isEqualTo(10);

        clock.set(Instant.parse("2026-09-02T02:00:00Z"));
        assertThatThrownBy(() -> policies.replaceVersion(
                        admin, scheduled.id(), 0L, command(LocalDate.of(2026, 9, 1), 10, 30)))
                .isInstanceOf(PolicyException.class);
    }

    @Test
    void workdaySetOfScheduledVersionGatesAttendanceAfterEffectiveDate() {
        policies.scheduleVersion(admin, commandWithWorkdays(
                LocalDate.of(2026, 9, 1), Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)));

        clock.set(Instant.parse("2026-09-01T02:00:00Z"));
        assertThatThrownBy(() -> attendance.checkIn(internId))
                .isInstanceOfSatisfying(AttendanceException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(AttendanceRejection.NON_WORKDAY));

        clock.set(Instant.parse("2026-09-02T02:00:00Z"));
        attendance.checkIn(internId);
        assertThat(records.findByInternUserIdAndWorkDate(internId, LocalDate.of(2026, 9, 2))).isPresent();
    }

    @Test
    void outOfRangeValuesAreRejectedByServiceAndDatabase() {
        assertThatThrownBy(() -> policies.scheduleVersion(
                        admin, command(LocalDate.of(2026, 9, 1), 30, 721)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policies.scheduleVersion(
                        admin, new SchedulePolicyCommand(
                                LocalDate.of(2026, 9, 1),
                                ZONE,
                                LocalTime.of(8, 30),
                                LocalTime.of(23, 30),
                                30,
                                30,
                                5,
                                new BigDecimal("0.5"),
                                Set.of(DayOfWeek.MONDAY))))
                .isInstanceOf(IllegalArgumentException.class);

        AttendancePolicyEntity invalidQuota = new AttendancePolicyEntity(new AttendancePolicy(
                0L,
                LocalDate.of(2026, 10, 1),
                ZONE,
                LocalTime.of(8, 30),
                LocalTime.of(15, 30),
                30,
                30,
                5,
                new BigDecimal("0.5"),
                Set.of(DayOfWeek.MONDAY)), adminId);
        ReflectionTestUtils.setField(invalidQuota, "monthlyLeaveQuota", 99);
        assertThatThrownBy(() -> policyEntities.saveAndFlush(
                        invalidQuota))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long scheduledVersion(long policyId) {
        return policyEntities.findById(policyId).orElseThrow().version();
    }

    private static SchedulePolicyCommand command(LocalDate effectiveFrom, int checkInGrace, int checkoutGrace) {
        return new SchedulePolicyCommand(
                effectiveFrom,
                ZONE,
                LocalTime.of(8, 30),
                LocalTime.of(15, 30),
                checkInGrace,
                checkoutGrace,
                5,
                new BigDecimal("0.5"),
                Set.of(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY));
    }

    private static SchedulePolicyCommand commandWithWorkdays(LocalDate effectiveFrom, Set<DayOfWeek> workdays) {
        return new SchedulePolicyCommand(
                effectiveFrom,
                ZONE,
                LocalTime.of(8, 30),
                LocalTime.of(15, 30),
                20,
                0,
                5,
                new BigDecimal("0.5"),
                workdays);
    }
}