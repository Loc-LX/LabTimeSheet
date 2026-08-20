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
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveDecisionCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveDecisionRow;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveOverview;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveSubmission;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveSubmissionCommand;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestEntity;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestDayRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestRepository;
import com.lab.labtimesheet.feature.integration.model.SecurityMode;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpDraft;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@Import(AttendancePersistenceIntegrationTest.IntegrationConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LeaveWorkflowIntegrationTest {

    @Autowired
    private LeaveService leave;

    @Autowired
    private LeaveRequestRepository requests;

    @Autowired
    private LeaveRequestDayRepository days;

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
    private long mentorId;

    @BeforeEach
    void seedUsers() {
        clock.set(Instant.parse("2026-08-14T00:00:00Z"));
        bootstrap.bootstrap("workflow-admin@example.test", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("workflow-admin@example.test");
        long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                "mailpit",
                1025,
                SecurityMode.NONE,
                null,
                null,
                "workflow-admin@example.test",
                "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "workflow-admin@example.test");
        smtp.activate(draftId, adminId);
        mail.clear();

        mentorId = createAndActivate(adminId, new CreateAccountCommand(
                "workflow-mentor@example.test", "Workflow Mentor", GlobalRole.MENTOR, null, null, null));
        internId = createAndActivate(adminId, new CreateAccountCommand(
                "workflow-intern@example.test",
                "Workflow Intern",
                GlobalRole.INTERN,
                "INT-WORKFLOW",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31)));
    }

    private long createAndActivate(long adminId, CreateAccountCommand command) {
        mail.clear();
        var creation = accounts.create(command, adminId);
        assertThat(creation.deliverySucceeded()).isTrue();
        assertThat(accounts.activate(mail.onlyActivationToken(), "new secure intern password")).isTrue();
        if (command.role() == GlobalRole.INTERN) {
            accounts.activateInternship(creation.userId(), adminId);
        }
        return creation.userId();
    }

    @Test
    void sameDaySubmitJustBeforeBoundaryIsAcceptedWithEditableActions() {
        clock.set(Instant.parse("2026-08-31T01:29:59Z"));
        LeaveSubmission accepted = leave.submit(
                internId, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 8, 31),
                        LocalDate.of(2026, 8, 31),
                        "Same day before 08:30"));
        assertThat(accepted.status()).isEqualTo("PENDING");
        assertThat(accepted.countedDays()).hasSize(1);

        LeaveRequestEntity persisted = requests.findById(accepted.requestId()).orElseThrow();
        assertThat(persisted.firstCountedStartAt()).isEqualTo(Instant.parse("2026-08-31T01:30:00Z"));
        LeaveOverview overview = leave.overview(internId, LocalDate.of(2026, 8, 1));
        assertThat(overview.requests())
                .allSatisfy(prior -> assertThat(prior.editable()).isTrue())
                .allSatisfy(prior -> assertThat(prior.cancellable()).isTrue());
    }

    @Test
    void sameDaySubmitAtBoundaryIsRejectedAsBoundaryPassedWithoutPersisting() {
        clock.set(Instant.parse("2026-08-31T01:30:00Z"));

        assertThatThrownBy(() -> leave.submit(
                internId, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 8, 31),
                        LocalDate.of(2026, 8, 31),
                        "Same day at 08:30")))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.BOUNDARY_PASSED));

        assertThat(requests.count()).isZero();
        assertThat(days.count()).isZero();
    }

    @Test
    void sequentialOverlappingSubmitIsRejectedAndAdjacentRangeIsAccepted() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        LeaveSubmission first = leave.submit(
                internId, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 2),
                        "First block"));

        assertThatThrownBy(() -> leave.submit(
                internId, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 9, 2),
                        LocalDate.of(2026, 9, 4),
                        "Overlaps first")))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.OVERLAPS_PENDING));

        LeaveSubmission adjacent = leave.submit(
                internId, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 9, 3),
                        LocalDate.of(2026, 9, 3),
                        "Adjacent touching day"));
        assertThat(adjacent.status()).isEqualTo("PENDING");
        assertThat(requests.count()).isEqualTo(2);
        assertThat(days.findLeaveDatesByRequestId(first.requestId()))
                .containsExactly(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2));
    }

    @Test
    void mentorApprovalKeepsReservationAndRejectionReleasesIt() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        LeaveSubmission first = leave.submit(
                internId, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 1),
                        "Family matter"));

        LeaveSubmission second = leave.submit(
                internId, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 9, 2),
                        LocalDate.of(2026, 9, 3),
                        "Clinic"));

        LeaveSubmission approved = leave.decide(
                mentorId, first.requestId(), new LeaveDecisionCommand(true, "Approved, family first"));
        assertThat(approved.status()).isEqualTo("APPROVED");
        LeaveRequestEntity approvedEntity = requests.findById(first.requestId()).orElseThrow();
        assertThat(approvedEntity.decidedByMentorUserId()).isEqualTo(mentorId);
        assertThat(approvedEntity.decidedAt()).isEqualTo(clock.instant());
        assertThat(approvedEntity.decisionNote()).isEqualTo("Approved, family first");
        assertThat(leave.overview(internId, LocalDate.of(2026, 9, 1)).reserved()).isEqualTo(3);

        LeaveSubmission rejected = leave.decide(
                mentorId, second.requestId(), new LeaveDecisionCommand(false, "Not enough notice"));
        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(requests.findById(second.requestId()).orElseThrow().decisionNote())
                .isEqualTo("Not enough notice");

        LeaveOverview after = leave.overview(internId, LocalDate.of(2026, 9, 1));
        assertThat(after.reserved()).isEqualTo(1);
        assertThat(after.available()).isEqualTo(2);

        List<LeaveDecisionRow> rows = leave.decisions();
        assertThat(rows)
                .extracting(LeaveDecisionRow::id, LeaveDecisionRow::internDisplayName, LeaveDecisionRow::status)
                .containsExactly(
                        tuple(second.requestId(), "Workflow Intern", "REJECTED"),
                        tuple(first.requestId(), "Workflow Intern", "APPROVED"));
    }

    @Test
    void decisionAndCancellationAfterBoundaryAreRejected() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        LeaveSubmission request = leave.submit(
                internId, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 1),
                        "Pending block"));

        clock.set(Instant.parse("2026-09-01T01:30:00Z"));
        assertThatThrownBy(() -> leave.decide(
                mentorId, request.requestId(), new LeaveDecisionCommand(true, null)))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.BOUNDARY_PASSED));
        assertThatThrownBy(() -> leave.cancel(internId, request.requestId()))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.BOUNDARY_PASSED));

        assertThat(requests.findById(request.requestId()).orElseThrow().status()).isEqualTo("PENDING");
        assertThat(leave.overview(internId, LocalDate.of(2026, 9, 1)).reserved()).isEqualTo(1);
    }

    @Test
    void cancelBeforeBoundaryReleasesReservationAndCannotBeRepeated() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        LeaveSubmission request = leave.submit(
                internId, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 2),
                        "Cancel me"));

        LeaveSubmission cancelled = leave.cancel(internId, request.requestId());
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(requests.findById(request.requestId()).orElseThrow().cancelledAt())
                .isEqualTo(clock.instant());
        assertThat(leave.overview(internId, LocalDate.of(2026, 9, 1)).reserved()).isZero();

        assertThatThrownBy(() -> leave.cancel(internId, request.requestId()))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.INVALID_STATE));

        assertThatThrownBy(() -> leave.cancel(internId + 10_000, request.requestId()))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.NOT_OWNER));
    }

    @Test
    void editReplacesRangeAndFailsAtomicallyOnOverlapAndQuota() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        LeaveSubmission request = leave.submit(
                internId, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 1),
                        "Original"));

        LeaveSubmission other = leave.submit(
                internId, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 9, 10),
                        LocalDate.of(2026, 9, 11),
                        "Other block"));

        LeaveSubmission edited = leave.edit(
                internId, request.requestId(), new LeaveSubmissionCommand(
                        LocalDate.of(2026, 9, 7),
                        LocalDate.of(2026, 9, 7),
                        "Rescheduled"));
        assertThat(edited.status()).isEqualTo("PENDING");
        LeaveRequestEntity editedEntity = requests.findById(request.requestId()).orElseThrow();
        assertThat(editedEntity.startDate()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(editedEntity.endDate()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(editedEntity.reason()).isEqualTo("Rescheduled");
        assertThat(editedEntity.submittedAt()).isEqualTo(clock.instant());
        assertThat(editedEntity.firstCountedStartAt()).isEqualTo(Instant.parse("2026-09-07T01:30:00Z"));
        assertThat(days.findLeaveDatesByRequestId(request.requestId()))
                .containsExactly(LocalDate.of(2026, 9, 7));

        assertThatThrownBy(() -> leave.edit(
                internId, request.requestId(), new LeaveSubmissionCommand(
                        LocalDate.of(2026, 9, 9),
                        LocalDate.of(2026, 9, 10),
                        "Now overlaps other")))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.OVERLAPS_PENDING));
        assertThat(requests.findById(request.requestId()).orElseThrow().startDate())
                .isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(days.findLeaveDatesByRequestId(request.requestId()))
                .containsExactly(LocalDate.of(2026, 9, 7));

        assertThatThrownBy(() -> leave.edit(
                internId, request.requestId(), new LeaveSubmissionCommand(
                        LocalDate.of(2026, 9, 4),
                        LocalDate.of(2026, 9, 8),
                        "Exceeds shared month")))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.QUOTA_EXCEEDED));
        assertThat(requests.findById(request.requestId()).orElseThrow().startDate())
                .isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(days.findLeaveDatesByRequestId(request.requestId()))
                .containsExactly(LocalDate.of(2026, 9, 7));

        assertThat(days.findLeaveDatesByRequestId(other.requestId()))
                .containsExactly(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11));
    }
}
