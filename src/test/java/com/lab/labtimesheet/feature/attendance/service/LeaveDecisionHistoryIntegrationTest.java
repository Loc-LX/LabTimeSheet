package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.labtimesheet.feature.attendance.exception.AttendanceRecordNotFoundException;
import com.lab.labtimesheet.feature.attendance.exception.LeaveException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.LeaveStatus;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceReport;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceReportClassification;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceReportDay;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveBalance;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestView;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestDayEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestDecisionEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceRecordRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestDayRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestDecisionRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestRepository;
import com.lab.labtimesheet.feature.identity.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.identity.service.BootstrapService;
import com.lab.labtimesheet.feature.internship.service.InternshipService;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.platform.model.SecurityMode;
import com.lab.labtimesheet.platform.model.dto.SmtpDraft;
import com.lab.labtimesheet.platform.service.MailDeliveryService;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test suite for responsible-Mentor Leave decisions, reasoned amendment,
 * and append-only decision history (LV-04).
 *
 * <p>Protects {@code LEV-004}, {@code LEV-008}, {@code LEV-011}, {@code AC-LEV-004},
 * {@code AC-LEV-008}, {@code ATT-024}, {@code DB-017}, {@code DB-018}, {@code AUTH-012}.</p>
 */
@Import(AttendancePersistenceIntegrationTest.IntegrationConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LeaveDecisionHistoryIntegrationTest {

    @Autowired private BootstrapService bootstrap;
    @Autowired private AccountService accounts;
    @Autowired private InternshipService internships;
    @Autowired private SmtpConfigurationService smtp;
    @Autowired private AttendancePersistenceIntegrationTest.RecordingSmtpProbe mail;
    @Autowired private AttendancePersistenceIntegrationTest.MutableClock clock;
    @Autowired private MailDeliveryService mailDelivery;
    @Autowired private LeaveApplicationService leaveService;
    @Autowired private LeaveRequestRepository requests;
    @Autowired private LeaveRequestDayRepository days;
    @Autowired private LeaveRequestDecisionRepository decisions;
    @Autowired private AttendanceRecordRepository records;
    @Autowired private AttendanceReportQueryService attendanceReports;
    @Autowired private JdbcTemplate jdbc;

    private long adminId;
    private long mentorId;
    private long internId;
    private AttendanceActor internActor;
    private AttendanceActor mentorActor;

    @BeforeEach
    void prepareResponsibleMentorAndIntern() {
        clock.set(Instant.parse("2026-08-01T00:00:00Z"));
        bootstrap.bootstrap("admin@example.test", "Admin", "correct horse battery staple");
        adminId = accounts.requireActiveAdminId("admin@example.test");
        if (!mailDelivery.isAvailable()) {
            long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                    "mailpit", 1025, SecurityMode.NONE, null, null, "admin@example.test", "Lab Timesheet"));
            smtp.testDraft(draftId, adminId, "admin@example.test");
            smtp.activate(draftId, adminId);
        }
        String fixture = UUID.randomUUID().toString().substring(0, 8);
        mentorId = createActiveUser("mentor-lv04-" + fixture + "@example.test", GlobalRole.MENTOR, null);
        internId = createActiveUser("intern-lv04-" + fixture + "@example.test", GlobalRole.INTERN,
                "INT-LV04-" + fixture);
        internships.activateInternship(internId, adminId);
        internActor = new AttendanceActor(internId, GlobalRole.INTERN);
        mentorActor = new AttendanceActor(mentorId, GlobalRole.MENTOR);
        // Set responsible Mentor for this Intern
        jdbc.update("update intern_profiles set responsible_mentor_user_id = ? where user_id = ?",
                mentorId, internId);
    }

    /**
     * Protects {@code LEV-004}, {@code LEV-011}, {@code ATT-024}, {@code AC-LEV-008}:
     * On the third day of an approved three-day leave, the responsible Mentor fails to reject,
     * fails to withdraw non-belonging date, fails to amend without reason, and successfully
     * amends to withdraw the first day.
     *
     * <p>Observable break: rejection accepted on approved request, date addition permitted,
     * reasonless amendment accepted, decision history rewritten, or withdrawn date retaining
     * quota or approved classification. Hand-derived expectation: initial approval creates
     * DECISION row (APPROVED, mentor, occurred_at); illegal actions fail; valid amendment
     * appends AMENDMENT row (APPROVED, mentor, occurred_at, reason), prior DECISION row
     * unchanged; only first day marked with approval_withdrawn_at; request stays APPROVED;
     * reserved quota decreases by exactly 1 day (from 3 to 2); no attendance record created;
     * first day classified as ABSENT by {@link AttendanceReportDay#classification()}.</p>
     */
    @Test
    void responsibleMentorApprovesAmendsWithReasonAndFailsUnlawfulActions() {
        LocalDate d1 = LocalDate.of(2026, 8, 17);
        LocalDate d2 = LocalDate.of(2026, 8, 18);
        LocalDate d3 = LocalDate.of(2026, 8, 19);

        // Submit 3-day leave on Sunday 2026-08-16
        Instant submitTime = Instant.parse("2026-08-16T10:00:00Z");
        clock.set(submitTime);
        LeaveRequestView submitted = leaveService.submit(internActor,
                new LeaveRequestCommand(d1, d3, "Three-day internship leave"));
        long requestId = submitted.id();

        // T0: Responsible mentor approves
        Instant approveTime = Instant.parse("2026-08-16T12:00:00Z");
        clock.set(approveTime);
        LeaveRequestView afterApprove = leaveService.approve(mentorActor, requestId);
        assertThat(afterApprove.status()).isEqualTo(LeaveStatus.APPROVED);

        // Check DECISION row in leave_request_decisions
        List<LeaveRequestDecisionEntity> decisionsAfterApprove = decisions
                .findByLeaveRequestIdOrderByIdAsc(requestId);
        assertThat(decisionsAfterApprove).hasSize(1);
        LeaveRequestDecisionEntity initialDecision = decisionsAfterApprove.get(0);
        assertThat(initialDecision.decisionKind()).isEqualTo("DECISION");
        assertThat(initialDecision.outcome()).isEqualTo("APPROVED");
        assertThat(initialDecision.actorUserId()).isEqualTo(mentorId);
        assertThat(initialDecision.occurredAt()).isEqualTo(approveTime);
        assertThat(initialDecision.reason()).isNull();

        // Check 3 days reserved in quota
        LeaveBalance balanceBefore = leaveService.balance(internActor, YearMonth.of(2026, 8));
        assertThat(balanceBefore.reservedDays()).isEqualTo(3);

        // Advance to day 3: 2026-08-19 morning (leave has begun)
        Instant day3Time = Instant.parse("2026-08-19T02:00:00Z");
        clock.set(day3Time);

        // 1. Responsible Mentor tries to reject approved request -> refused, status stays APPROVED
        assertThatThrownBy(() -> leaveService.reject(mentorActor, requestId))
                .isInstanceOf(LeaveException.class);
        assertThat(leaveService.view(mentorActor, requestId).status()).isEqualTo(LeaveStatus.APPROVED);

        // 2. Responsible Mentor tries to amend with date not in request -> refused
        LocalDate foreignDate = LocalDate.of(2026, 8, 20);
        assertThatThrownBy(() -> leaveService.amend(mentorActor, requestId, List.of(foreignDate), "Withdrawing foreign date"))
                .isInstanceOf(LeaveException.class)
                .hasMessage("An amendment can only withdraw dates of this leave");

        // 3. Responsible Mentor tries to amend without a reason -> refused
        assertThatThrownBy(() -> leaveService.amend(mentorActor, requestId, List.of(d1), ""))
                .isInstanceOf(LeaveException.class)
                .hasMessage("A reason is required to amend a leave decision");
        assertThatThrownBy(() -> leaveService.amend(mentorActor, requestId, List.of(d1), "   "))
                .isInstanceOf(LeaveException.class)
                .hasMessage("A reason is required to amend a leave decision");

        // Snapshot of leave_request_decisions before amendment
        List<Map<String, Object>> snapshotBefore = queryDecisionSnapshot(requestId);

        // 4. Responsible Mentor amends with reason to withdraw first day (d1)
        Instant amendTime = Instant.parse("2026-08-19T02:30:00Z");
        clock.set(amendTime);
        LeaveRequestView afterAmend = leaveService.amend(mentorActor, requestId, List.of(d1), "Intern was needed on site");

        // Verify: exactly one new AMENDMENT row appended
        List<LeaveRequestDecisionEntity> decisionsAfterAmend = decisions
                .findByLeaveRequestIdOrderByIdAsc(requestId);
        assertThat(decisionsAfterAmend).hasSize(2);
        LeaveRequestDecisionEntity amendmentDecision = decisionsAfterAmend.get(1);
        assertThat(amendmentDecision.decisionKind()).isEqualTo("AMENDMENT");
        assertThat(amendmentDecision.outcome()).isEqualTo("APPROVED");
        assertThat(amendmentDecision.actorUserId()).isEqualTo(mentorId);
        assertThat(amendmentDecision.reason()).isEqualTo("Intern was needed on site");
        assertThat(amendmentDecision.occurredAt()).isEqualTo(amendTime);

        // Verify: prior decisions unchanged (snapshot check)
        List<Map<String, Object>> snapshotAfter = queryDecisionSnapshot(requestId);
        assertThat(snapshotAfter).hasSize(snapshotBefore.size() + 1);
        assertThat(snapshotAfter.subList(0, snapshotBefore.size())).isEqualTo(snapshotBefore);

        // Verify: 3 leave_request_days retain snapshots, only d1 has approval_withdrawn_at
        List<LeaveRequestDayEntity> dayRows = days.findByRequestIdOrderByLeaveDate(requestId);
        assertThat(dayRows).hasSize(3);
        LeaveRequestDayEntity day1Row = dayRows.stream().filter(d -> d.leaveDate().equals(d1)).findFirst().orElseThrow();
        LeaveRequestDayEntity day2Row = dayRows.stream().filter(d -> d.leaveDate().equals(d2)).findFirst().orElseThrow();
        LeaveRequestDayEntity day3Row = dayRows.stream().filter(d -> d.leaveDate().equals(d3)).findFirst().orElseThrow();

        assertThat(day1Row.approvalWithdrawnAt()).isEqualTo(amendTime);
        assertThat(day2Row.approvalWithdrawnAt()).isNull();
        assertThat(day3Row.approvalWithdrawnAt()).isNull();

        // Verify: request status is still APPROVED
        assertThat(afterAmend.status()).isEqualTo(LeaveStatus.APPROVED);

        // Verify: Intern's reserved quota decreased by exactly 1 day (from 3 to 2)
        LeaveBalance balanceAfter = leaveService.balance(internActor, YearMonth.of(2026, 8));
        assertThat(balanceAfter.reservedDays()).isEqualTo(2);

        // Verify: no attendance records created for the withdrawn day d1
        assertThat(records.findByInternUserIdAndWorkDate(internId, d1)).isEmpty();

        // Verify: day 1 is classified as ABSENT via AttendanceReportDay#classification()
        AttendanceReport report = attendanceReports.query(mentorActor, internId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        AttendanceReportDay reportDay1 = report.days().stream()
                .filter(d -> d.workDate().equals(d1))
                .findFirst()
                .orElseThrow();
        assertThat(reportDay1.classification()).isEqualTo(AttendanceReportClassification.ABSENT);
    }

    /**
     * Protects {@code AUTH-012}, {@code LEV-008}, {@code LEV-011}, {@code ATT-024}:
     * Only the designated responsible Mentor may approve or amend a leave request.
     * Non-responsible active Mentor, Admin, owning Intern, and locked responsible Mentor
     * all receive AccessDeniedException, and decisions/allocations remain unchanged.
     * Non-existent ID preserves AttendanceRecordNotFoundException.
     *
     * <p>Observable break: non-responsible mentor or admin deciding leave; hand-derived
     * expectation: AccessDeniedException for each unlawful actor, zero changes to DB rows.</p>
     */
    @Test
    void onlyResponsibleMentorIsAllowedToDecideAndAmendOtherActorsAreDenied() {
        LocalDate d1 = LocalDate.of(2026, 8, 24);
        LocalDate d2 = LocalDate.of(2026, 8, 25);
        clock.set(Instant.parse("2026-08-20T10:00:00Z"));
        LeaveRequestView submitted = leaveService.submit(internActor,
                new LeaveRequestCommand(d1, d2, "Auth probe leave"));
        long requestId = submitted.id();

        // Create a second active Mentor who is NOT the responsible Mentor
        String fixture = UUID.randomUUID().toString().substring(0, 8);
        long otherMentorId = createActiveUser("mentor-other-" + fixture + "@example.test", GlobalRole.MENTOR, null);
        AttendanceActor otherMentorActor = new AttendanceActor(otherMentorId, GlobalRole.MENTOR);
        AttendanceActor adminActor = new AttendanceActor(adminId, GlobalRole.ADMIN);

        long decisionsBefore = countDecisions(requestId);

        // 1. Non-responsible Mentor -> AccessDeniedException for approve
        assertThatThrownBy(() -> leaveService.approve(otherMentorActor, requestId))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(countDecisions(requestId)).isEqualTo(decisionsBefore);

        // 2. Admin -> AccessDeniedException for approve
        assertThatThrownBy(() -> leaveService.approve(adminActor, requestId))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(countDecisions(requestId)).isEqualTo(decisionsBefore);

        // 3. Owning Intern -> AccessDeniedException for approve
        assertThatThrownBy(() -> leaveService.approve(internActor, requestId))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(countDecisions(requestId)).isEqualTo(decisionsBefore);

        // 4. Locked responsible Mentor -> AccessDeniedException for approve
        accounts.lockAccount(mentorId, adminId);
        assertThatThrownBy(() -> leaveService.approve(mentorActor, requestId))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(countDecisions(requestId)).isEqualTo(decisionsBefore);
        accounts.unlockAccount(mentorId, adminId);

        // 5. Non-existent request ID -> AttendanceRecordNotFoundException
        assertThatThrownBy(() -> leaveService.approve(mentorActor, 999_999_999L))
                .isInstanceOf(AttendanceRecordNotFoundException.class);

        // Now responsible mentor approves
        leaveService.approve(mentorActor, requestId);
        assertThat(countDecisions(requestId)).isEqualTo(1);

        // Advance past first counted start
        clock.set(Instant.parse("2026-08-24T02:00:00Z"));

        long decisionsAfterApprove = countDecisions(requestId);

        // 6. Non-responsible Mentor -> AccessDeniedException for amend
        assertThatThrownBy(() -> leaveService.amend(otherMentorActor, requestId, List.of(d1), "Reason"))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(countDecisions(requestId)).isEqualTo(decisionsAfterApprove);

        // 7. Admin -> AccessDeniedException for amend
        assertThatThrownBy(() -> leaveService.amend(adminActor, requestId, List.of(d1), "Reason"))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(countDecisions(requestId)).isEqualTo(decisionsAfterApprove);

        // 8. Owning Intern -> AccessDeniedException for amend
        assertThatThrownBy(() -> leaveService.amend(internActor, requestId, List.of(d1), "Reason"))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(countDecisions(requestId)).isEqualTo(decisionsAfterApprove);

        // 9. Locked responsible Mentor -> AccessDeniedException for amend
        accounts.lockAccount(mentorId, adminId);
        assertThatThrownBy(() -> leaveService.amend(mentorActor, requestId, List.of(d1), "Reason"))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(countDecisions(requestId)).isEqualTo(decisionsAfterApprove);
        accounts.unlockAccount(mentorId, adminId);

        // 10. Non-existent request ID for amend -> AttendanceRecordNotFoundException
        assertThatThrownBy(() -> leaveService.amend(mentorActor, 999_999_999L, List.of(d1), "Reason"))
                .isInstanceOf(AttendanceRecordNotFoundException.class);
    }

    /**
     * Protects {@code LEV-008}, {@code LEV-010}, {@code AC-LEV-004}:
     * An undecided leave request whose first counted start has passed transitions to OVERDUE
     * on access or decision time, retains its quota reservation, and can still be approved
     * by the responsible Mentor.
     *
     * <p>Observable break: overdue request rejected or refused approval; hand-derived
     * expectation: status OVERDUE verified, then approved to APPROVED with decision row.</p>
     */
    @Test
    void overdueLeaveTransitionsOnAccessAndCanStillBeApprovedByResponsibleMentor() {
        LocalDate workDate = LocalDate.of(2026, 8, 26);
        clock.set(Instant.parse("2026-08-25T10:00:00Z"));
        LeaveRequestView submitted = leaveService.submit(internActor,
                new LeaveRequestCommand(workDate, workDate, "Overdue probe"));
        long requestId = submitted.id();
        assertThat(submitted.status()).isEqualTo(LeaveStatus.PENDING);

        // Advance clock past first counted start (2026-08-26 08:30 Asia/Ho_Chi_Minh = 01:30 UTC)
        clock.set(Instant.parse("2026-08-26T02:00:00Z"));

        // Access-time guard transitions request to OVERDUE
        LeaveRequestView viewed = leaveService.view(mentorActor, requestId);
        assertThat(viewed.status()).isEqualTo(LeaveStatus.OVERDUE);

        // Responsible mentor approves overdue request
        Instant approveTime = Instant.parse("2026-08-26T02:15:00Z");
        clock.set(approveTime);
        LeaveRequestView approved = leaveService.approve(mentorActor, requestId);
        assertThat(approved.status()).isEqualTo(LeaveStatus.APPROVED);

        // Check DECISION row recorded
        List<LeaveRequestDecisionEntity> decisionsList = decisions.findByLeaveRequestIdOrderByIdAsc(requestId);
        assertThat(decisionsList).hasSize(1);
        assertThat(decisionsList.get(0).decisionKind()).isEqualTo("DECISION");
        assertThat(decisionsList.get(0).outcome()).isEqualTo("APPROVED");
        assertThat(decisionsList.get(0).actorUserId()).isEqualTo(mentorId);
        assertThat(decisionsList.get(0).occurredAt()).isEqualTo(approveTime);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private long createActiveUser(String email, GlobalRole role, String studentCode) {
        mail.clear();
        var creation = internships.create(new CreateAccountCommand(
                email, role == GlobalRole.INTERN ? "Intern" : "Mentor", role, studentCode,
                role == GlobalRole.INTERN ? LocalDate.of(2026, 8, 1) : null,
                role == GlobalRole.INTERN ? LocalDate.of(2026, 12, 31) : null), adminId);
        assertThat(creation.deliverySucceeded()).isTrue();
        assertThat(accounts.activate(mail.activationTokenFor(email), "new secure fixture password")).isTrue();
        return creation.userId();
    }

    private long countDecisions(long leaveRequestId) {
        return jdbc.queryForObject(
                "select count(*) from leave_request_decisions where leave_request_id = ?",
                Long.class, leaveRequestId);
    }

    private List<Map<String, Object>> queryDecisionSnapshot(long leaveRequestId) {
        return jdbc.queryForList(
                "select id, leave_request_id, decision_kind, outcome, decision_note, actor_user_id, occurred_at, reason "
                        + "from leave_request_decisions where leave_request_id = ? order by id asc",
                leaveRequestId);
    }
}
