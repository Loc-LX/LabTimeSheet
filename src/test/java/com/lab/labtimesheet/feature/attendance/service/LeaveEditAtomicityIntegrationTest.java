package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;

import com.lab.labtimesheet.feature.attendance.exception.LeaveException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.LeaveStatus;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveAllocation;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestCommand;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestDayEntity;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestDayRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestRepository;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL proof that allocation replacement rolls back with the request when the new allocation write fails. */
@Import(AttendancePersistenceIntegrationTest.IntegrationConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class LeaveEditAtomicityIntegrationTest {

    @Autowired
    private BootstrapService bootstrap;

    @Autowired
    private AccountService accounts;

    @Autowired
    private InternshipService internships;

    @Autowired
    private SmtpConfigurationService smtp;

    @Autowired
    private AttendancePersistenceIntegrationTest.RecordingSmtpProbe mail;

    @Autowired
    private AttendancePersistenceIntegrationTest.MutableClock clock;

    @Autowired
    private LeaveApplicationService leaves;

    @Autowired
    private LeaveRequestRepository requests;

    @MockitoSpyBean
    private LeaveRequestDayRepository days;

    private long internId;

    @BeforeEach
    void createEligibleIntern() {
        clock.set(Instant.parse("2026-08-14T00:00:00Z"));
        bootstrap.bootstrap("atomic-admin@example.test", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("atomic-admin@example.test");
        long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                "mailpit", 1025, SecurityMode.NONE, null, null, "atomic-admin@example.test", "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "atomic-admin@example.test");
        smtp.activate(draftId, adminId);
        mail.clear();

        var creation = internships.create(new CreateAccountCommand(
                "atomic-intern@example.test", "Atomic Intern", GlobalRole.INTERN,
                "INT-ATOMIC", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31)), adminId);
        assertThat(creation.deliverySucceeded()).isTrue();
        assertThat(accounts.activate(mail.onlyActivationToken(), "new secure intern password")).isTrue();
        internships.activateInternship(creation.userId(), adminId);
        internId = creation.userId();
    }

    /**
     * Protects {@code LEV-007} and {@code AC-LEV-003}: failure while saving the replacement allocation rolls back
     * the earlier deletion and parent-range edit. Observable break: the row remains edited or its original date
     * allocations disappear. The edit deletes before attempting to save; the hand-recorded original has Aug 17 and
     * 18, policy 1, quota month Aug 1, and quota snapshot 3 on both rows.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void failedReplacementAllocationRestoresOriginalRangeAndFrozenDayRows() {
        AttendanceActor intern = new AttendanceActor(internId, GlobalRole.INTERN);
        var original = leaves.submit(intern, new LeaveRequestCommand(
                LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 18), "original reason"));
        List<LeaveAllocation> originalAllocations = original.allocations();
        assertThat(originalAllocations).hasSize(2);

        clearInvocations(days);
        doThrow(new DataIntegrityViolationException("injected replacement allocation failure"))
                .when(days).saveAllAndFlush(any());
        assertThatThrownBy(() -> leaves.edit(intern, original.id(), new LeaveRequestCommand(
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 21), "replacement reason")))
                .isInstanceOf(DataIntegrityViolationException.class);

        var order = inOrder(days);
        order.verify(days).deleteByRequestId(original.id());
        order.verify(days).saveAllAndFlush(any());
        var restoredRequest = requests.findById(original.id()).orElseThrow();
        assertThat(restoredRequest.startDate()).isEqualTo(LocalDate.of(2026, 8, 17));
        assertThat(restoredRequest.endDate()).isEqualTo(LocalDate.of(2026, 8, 18));
        assertThat(restoredRequest.reason()).isEqualTo("original reason");
        assertThat(restoredRequest.status()).isEqualTo(LeaveStatus.PENDING);
        assertThat(days.findByRequestIdOrderByLeaveDate(original.id()))
                .extracting(LeaveRequestDayEntity::leaveDate, LeaveRequestDayEntity::policyVersionId,
                        LeaveRequestDayEntity::quotaMonth, LeaveRequestDayEntity::monthlyQuotaSnapshot)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 8, 17), 1L,
                                LocalDate.of(2026, 8, 1), 3),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 8, 18), 1L,
                                LocalDate.of(2026, 8, 1), 3));
        assertThat(originalAllocations).extracting(LeaveAllocation::leaveDate)
                .containsExactly(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 18));
    }
}
