package com.lab.labtimesheet.feature.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.account.model.dto.EligibleInternOption;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicyFixtures;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.AttendanceViolations;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceHistoryItem;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit contract for attendance report aggregation and own/detail scope handling. */
class AttendanceReportServiceTest {

    private final AttendanceCurrentUserService currentUsers = mock(AttendanceCurrentUserService.class);
    private final AttendanceApplicationService attendance = mock(AttendanceApplicationService.class);
    private final AccountService accounts = mock(AccountService.class);
    private final Principal principal = () -> "intern@example.test";
    private AttendanceReportService reports;

    @BeforeEach
    void setUp() {
        reports = new AttendanceReportService(currentUsers, attendance, accounts);
    }

    @Test
    void computesComplianceAndExplicitNaForMissingCheckoutAndEmptyRows() {
        given(currentUsers.actor(principal)).willReturn(new AttendanceActor(7L, AttendanceRole.INTERN));
        given(attendance.currentBusinessDate()).willReturn(LocalDate.of(2026, 8, 31));
        given(accounts.requireIdentityById(7L))
                .willReturn(identity(7L, "Mai Intern", GlobalRole.INTERN));
        given(attendance.history(
                new AttendanceActor(7L, AttendanceRole.INTERN),
                7L,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)))
                .willReturn(List.of(
                        new AttendanceHistoryItem(
                                LocalDate.of(2026, 8, 14),
                                Instant.parse("2026-08-14T02:05:00Z"),
                                Instant.parse("2026-08-14T09:00:00Z"),
                                AttendancePolicyFixtures.seeded(1L),
                                new AttendanceViolations(false, false, false)),
                        new AttendanceHistoryItem(
                                LocalDate.of(2026, 8, 13),
                                Instant.parse("2026-08-13T01:30:00Z"),
                                null,
                                AttendancePolicyFixtures.seeded(1L),
                                new AttendanceViolations(true, false, true))));

        var view = reports.build(principal, null, null, null);

        assertThat(view.recordedDays()).isEqualTo(2);
        assertThat(view.compliantDays()).isEqualTo(1);
        assertThat(view.violationDays()).isEqualTo(1);
        assertThat(view.complianceRate()).isEqualTo("50.0%");
        assertThat(view.rows().get(1).workedMinutes()).isEqualTo("N/A");
        assertThat(view.rows().get(1).result()).isEqualTo("Late, Missing checkout");
    }

    @Test
    void rejectsInternDetailTargetOutsideOwnAccountBeforeAttendanceRead() {
        given(currentUsers.actor(principal)).willReturn(new AttendanceActor(7L, AttendanceRole.INTERN));

        assertThatThrownBy(() -> reports.build(
                principal,
                8L,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void rendersMentorTargetPickerBeforeReadingAttendanceRows() {
        given(currentUsers.actor(principal)).willReturn(new AttendanceActor(2L, AttendanceRole.MENTOR));
        given(attendance.currentBusinessDate()).willReturn(LocalDate.of(2026, 8, 31));
        given(accounts.eligibleInternOptions(LocalDate.of(2026, 8, 31)))
                .willReturn(List.of(new EligibleInternOption(
                        7L,
                        "Mai Intern",
                        "SV-007",
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 12, 31))));

        var view = reports.build(principal, null, null, null);

        assertThat(view.ownScope()).isFalse();
        assertThat(view.targetName()).isEqualTo("Select an Intern");
        assertThat(view.rows()).isEmpty();
        assertThat(view.targetOptions()).hasSize(1);
    }

    private static AccountIdentity identity(long id, String name, GlobalRole role) {
        return new AccountIdentity(id, name.toLowerCase().replace(' ', '.') + "@example.test", name, role,
                AccountStatus.ACTIVE);
    }
}
