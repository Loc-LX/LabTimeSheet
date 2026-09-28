package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionDecisionKind;
import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionKind;
import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionOutcome;
import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionSource;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceRecordRepository;
import com.lab.labtimesheet.feature.identity.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.identity.service.BootstrapService;
import com.lab.labtimesheet.feature.internship.service.InternshipService;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.platform.model.SecurityMode;
import com.lab.labtimesheet.platform.model.dto.SmtpDraft;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

@Import(AttendancePersistenceIntegrationTest.IntegrationConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AttendanceExceptionServiceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-14T10:15:30Z");

    @Autowired private AttendanceExceptionService exceptions;
    @Autowired private AttendanceApplicationService attendance;
    @Autowired private BootstrapService bootstrap;
    @Autowired private AccountService accounts;
    @Autowired private InternshipService internships;
    @Autowired private AttendancePersistenceIntegrationTest.MutableClock clock;
    @Autowired private AttendancePersistenceIntegrationTest.RecordingSmtpProbe mail;
    @Autowired private AttendanceRecordRepository records;
    @Autowired private SmtpConfigurationService smtp;

    private long internId;
    private long mentorId;
    private long attendanceRecordId;

    @BeforeEach
    void createAttendanceFixture() {
        clock.set(NOW);
        bootstrap.bootstrap("admin@example.test", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("admin@example.test");
        long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                "mailpit", 1025, SecurityMode.NONE, null, null, "admin@example.test", "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "admin@example.test");
        smtp.activate(draftId, adminId);
        String fixture = UUID.randomUUID().toString();
        mentorId = createActiveUser("mentor-" + fixture + "@example.test", "Mentor", GlobalRole.MENTOR, null);
        internId = createActiveUser("intern-" + fixture + "@example.test", "Intern", GlobalRole.INTERN,
                "INT-" + fixture.substring(0, 8));
        internships.activateInternship(internId, adminId);
        attendance.checkIn(internId);
        attendanceRecordId = records.findByInternUserIdAndWorkDate(internId, java.time.LocalDate.of(2026, 8, 14))
                .orElseThrow().id();
    }

    private long createActiveUser(String email, String name, GlobalRole role, String studentCode) {
        mail.clear();
        var creation = internships.create(new CreateAccountCommand(
                email, name, role, studentCode,
                role == GlobalRole.INTERN ? java.time.LocalDate.of(2026, 8, 1) : null,
                role == GlobalRole.INTERN ? java.time.LocalDate.of(2026, 12, 31) : null),
                accounts.requireActiveAdminId("admin@example.test"));
        assertThat(creation.deliverySucceeded()).isTrue();
        assertThat(accounts.activate(mail.onlyActivationToken(), "new secure fixture password")).isTrue();
        return creation.userId();
    }

    /**
     * DB-016 and EXC-007: opening must not expose a decision before one exists; otherwise callers see a
     * fabricated outcome. The hand-derived state is PENDING, three null decision fields, and empty history.
     */
    @Test
    void opensPendingExceptionWithoutDecisionFields() {
        long id = exceptions.open(attendanceRecordId, AttendanceExceptionKind.LATE_ARRIVAL,
                AttendanceExceptionSource.REQUEST, "Train delay", NOW.plusSeconds(3600), NOW.plusSeconds(7200));

        var view = exceptions.find(id).orElseThrow();
        assertThat(view.status()).hasToString("PENDING");
        assertThat(view.decidedByMentorUserId()).isNull();
        assertThat(view.decidedAt()).isNull();
        assertThat(view.decisionNote()).isNull();
        assertThat(view.decisions()).isEmpty();
    }

    /**
     * DB-017 and EXC-007: a decision must be persisted with the same actor, time, and note as current state;
     * drift would make history disagree with the result. Expected actor is mentorId and time is NOW.
     */
    @Test
    void decisionSynchronizesCurrentStateAndHistoryFromInjectedClock() {
        long id = open();
        exceptions.appendDecision(id, AttendanceExceptionDecisionKind.DECISION,
                AttendanceExceptionOutcome.EXCUSED, "Approved", mentorId, null);

        var view = exceptions.find(id).orElseThrow();
        assertThat(view.status()).hasToString("EXCUSED");
        assertThat(view.decidedByMentorUserId()).isEqualTo(mentorId);
        assertThat(view.decidedAt()).isEqualTo(NOW);
        assertThat(view.decisionNote()).isEqualTo("Approved");
        assertThat(view.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.actorUserId()).isEqualTo(mentorId);
            assertThat(decision.occurredAt()).isEqualTo(NOW);
            assertThat(decision.decisionNote()).isEqualTo("Approved");
        });
    }

    /**
     * DB-017 and EXC-007: an amendment must preserve the effective outcome and append history; otherwise the
     * prior decision is lost. Expected status remains EXCUSED and the two notes occur in order.
     */
    @Test
    void amendmentRetainsOutcomeAndAppendsHistoryInOrder() {
        long id = open();
        exceptions.appendDecision(id, AttendanceExceptionDecisionKind.DECISION,
                AttendanceExceptionOutcome.EXCUSED, "Approved", mentorId, null);
        clock.set(NOW.plusSeconds(60));
        exceptions.appendDecision(id, AttendanceExceptionDecisionKind.AMENDMENT,
                AttendanceExceptionOutcome.EXCUSED, "Clarified", mentorId, "Correct the note");

        var view = exceptions.find(id).orElseThrow();
        assertThat(view.status()).hasToString("EXCUSED");
        assertThat(view.decisionNote()).isEqualTo("Clarified");
        assertThat(view.decisions()).extracting("decisionNote").containsExactly("Approved", "Clarified");
        assertThat(view.decisions()).extracting("occurredAt").containsExactly(NOW, NOW.plusSeconds(60));
    }

    /**
     * DB-017 and EXC-007: reversal must append a third immutable event and update the effective outcome;
     * otherwise compliance reads stale state. Expected status is UNEXCUSED with three ordered entries.
     */
    @Test
    void reversalChangesCurrentOutcomeAndKeepsThreeEntries() {
        long id = open();
        exceptions.appendDecision(id, AttendanceExceptionDecisionKind.DECISION,
                AttendanceExceptionOutcome.EXCUSED, "Approved", mentorId, null);
        exceptions.appendDecision(id, AttendanceExceptionDecisionKind.AMENDMENT,
                AttendanceExceptionOutcome.EXCUSED, "Clarified", mentorId, "Correct the note");
        clock.set(NOW.plusSeconds(60));
        exceptions.appendDecision(id, AttendanceExceptionDecisionKind.REVERSAL,
                AttendanceExceptionOutcome.UNEXCUSED, "Reversed", mentorId, "Evidence changed");

        var view = exceptions.find(id).orElseThrow();
        assertThat(view.status()).hasToString("UNEXCUSED");
        assertThat(view.decisions()).hasSize(3);
        assertThat(view.decisions()).extracting("decisionKind")
                .containsExactly(AttendanceExceptionDecisionKind.DECISION,
                        AttendanceExceptionDecisionKind.AMENDMENT,
                        AttendanceExceptionDecisionKind.REVERSAL);
    }

    /**
     * DB-017 and EXC-007: PostgreSQL refuses an amendment without a reason. Expected: the call fails with a
     * data-integrity error and the state stays EXCUSED at NOW with one history row. Atomicity is proven by
     * {@link AttendanceExceptionAtomicityIntegrationTest}.
     */
    @Test
    void amendmentWithoutReasonIsRefusedAndLeavesStateUnchanged() {
        long id = open();
        exceptions.appendDecision(id, AttendanceExceptionDecisionKind.DECISION,
                AttendanceExceptionOutcome.EXCUSED, "Approved", mentorId, null);
        TestTransaction.flagForCommit();
        TestTransaction.end();

        assertThatThrownBy(() -> exceptions.appendDecision(id, AttendanceExceptionDecisionKind.AMENDMENT,
                AttendanceExceptionOutcome.UNEXCUSED, "Changed", mentorId, " "))
                .isInstanceOf(DataIntegrityViolationException.class);
        TestTransaction.start();
        var view = exceptions.find(id).orElseThrow();
        assertThat(view.status()).hasToString("EXCUSED");
        assertThat(view.decidedAt()).isEqualTo(NOW);
        assertThat(view.decisionNote()).isEqualTo("Approved");
        assertThat(view.decisions()).hasSize(1);
    }

    private long open() {
        return exceptions.open(attendanceRecordId, AttendanceExceptionKind.LATE_ARRIVAL,
                AttendanceExceptionSource.REQUEST, "Train delay", NOW.plusSeconds(3600), NOW.plusSeconds(7200));
    }
}
