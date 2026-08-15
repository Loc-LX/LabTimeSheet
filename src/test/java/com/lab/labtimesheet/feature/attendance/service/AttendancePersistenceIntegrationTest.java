package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.account.service.BootstrapService;
import com.lab.labtimesheet.feature.attendance.exception.AttendanceException;
import com.lab.labtimesheet.feature.attendance.exception.CalendarException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceCurrentState;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceHistoryItem;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendancePolicyEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceRecordRepository;
import com.lab.labtimesheet.feature.integration.model.SecurityMode;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpConnection;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpDraft;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.integration.service.SmtpProbe;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.transaction.annotation.Transactional;
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

    @Autowired
    private SmtpConfigurationService smtp;

    @Autowired
    private RecordingSmtpProbe mail;

    @Autowired
    private AttendanceRecordRepository records;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MutableClock clock;

    private long internId;
    private long adminId;
    private long mentorId;

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
        smtp.testDraft(draftId, adminId, "admin@example.test");
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
    void publicCalendarServiceReportsAuthoritativeDayOff() {
        AttendanceActor admin = new AttendanceActor(adminId, AttendanceRole.ADMIN);
        LocalDate date = LocalDate.of(2026, 8, 20);
        var event = calendar.createManual(admin, date, "Observance", false);

        assertThat(calendar.isGlobalDayOff(date)).isFalse();

        calendar.updateManual(admin, event.id(), event.version(), date, "Lab closure", true);
        assertThat(calendar.isGlobalDayOff(date)).isTrue();
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
