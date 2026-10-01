package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionDecisionKind;
import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionKind;
import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionOutcome;
import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionSource;
import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionStatus;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceExceptionEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceExceptionRepository;
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
import java.time.LocalDate;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Import(AttendancePersistenceIntegrationTest.IntegrationConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class AttendanceExceptionAtomicityIntegrationTest {

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
    @Autowired private TransactionTemplate transactions;
    @Autowired private EntityManager entityManager;

    @MockitoSpyBean
    private AttendanceExceptionRepository exceptionRows;

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
        attendanceRecordId = records.findByInternUserIdAndWorkDate(internId, LocalDate.of(2026, 8, 14))
                .orElseThrow().id();
    }

    private long createActiveUser(String email, String name, GlobalRole role, String studentCode) {
        mail.clear();
        var creation = internships.create(new CreateAccountCommand(
                email, name, role, studentCode,
                role == GlobalRole.INTERN ? LocalDate.of(2026, 8, 1) : null,
                role == GlobalRole.INTERN ? LocalDate.of(2026, 12, 31) : null),
                accounts.requireActiveAdminId("admin@example.test"));
        assertThat(creation.deliverySucceeded()).isTrue();
        assertThat(accounts.activate(mail.activationTokenFor(email), "new secure fixture password")).isTrue();
        return creation.userId();
    }

    /**
     * Protects {@code DB-017} and {@code EXC-007}: V3 does not tie {@code attendance_exceptions.status} to the
     * latest decision entry, so the entry and the current state must commit together. Observable break: a failure
     * after the entry is inserted leaves an entry the current state does not reflect. Expected: the entry is rolled
     * back, the history keeps one row, and the state stays EXCUSED with note Approved.
     */
    @Test
    void decisionEntryIsRolledBackWhenCurrentStateCannotBeSaved() {
        long exceptionId = exceptions.open(attendanceRecordId, AttendanceExceptionKind.LATE_ARRIVAL,
                AttendanceExceptionSource.REQUEST, "Train delay", NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        // Keep the production row lock for the green path; the untransactional RED probe must reach the later flush.
        doAnswer(invocation -> {
            long id = invocation.getArgument(0);
            LockModeType lock = TransactionSynchronizationManager.isActualTransactionActive()
                    ? LockModeType.PESSIMISTIC_WRITE
                    : LockModeType.NONE;
            return Optional.ofNullable(entityManager.find(AttendanceExceptionEntity.class, id, lock));
        }).when(exceptionRows).findForUpdateById(anyLong());
        transactions.executeWithoutResult(status -> exceptions.appendDecision(exceptionId,
                AttendanceExceptionDecisionKind.DECISION, AttendanceExceptionOutcome.EXCUSED,
                "Approved", mentorId, null));
        Instant originalDecisionAt = exceptions.find(exceptionId).orElseThrow().decidedAt();

        IllegalStateException forcedFailure = new IllegalStateException("forced after the decision insert");
        doThrow(forcedFailure).when(exceptionRows).flush();
        try {
            assertThatThrownBy(() -> exceptions.appendDecision(exceptionId,
                    AttendanceExceptionDecisionKind.AMENDMENT, AttendanceExceptionOutcome.EXCUSED,
                    "Clarified", mentorId, "Clarify note"))
                    .isSameAs(forcedFailure);
        } finally {
            reset(exceptionRows);
        }

        var view = exceptions.find(exceptionId).orElseThrow();
        assertThat(view.status()).isEqualTo(AttendanceExceptionStatus.EXCUSED);
        assertThat(view.decisionNote()).isEqualTo("Approved");
        assertThat(view.decidedAt()).isEqualTo(originalDecisionAt);
        assertThat(view.decisions()).hasSize(1);
        assertThat(view.decisions().getFirst().decisionKind()).isEqualTo(AttendanceExceptionDecisionKind.DECISION);
    }
}
