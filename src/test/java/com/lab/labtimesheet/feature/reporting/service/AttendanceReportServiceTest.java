package com.lab.labtimesheet.feature.reporting.service;

import com.lab.labtimesheet.feature.internship.service.InternshipService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.internship.model.dto.EligibleInternOption;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceReport;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceReportClassification;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceReportDay;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.calendar.service.CalendarApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceReportQueryService;
import com.lab.labtimesheet.platform.model.GlobalRole;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit contract for attendance report aggregation and own/detail scope handling. */
class AttendanceReportServiceTest {

    private final AttendanceCurrentUserService currentUsers = mock(AttendanceCurrentUserService.class);
    private final AttendanceApplicationService attendance = mock(AttendanceApplicationService.class);
    private final CalendarApplicationService calendar = mock(CalendarApplicationService.class);
    private final AttendanceReportQueryService reportQueries = mock(AttendanceReportQueryService.class);
    private final AccountService accounts = mock(AccountService.class);
    private final InternshipService internships = mock(InternshipService.class);
    private final Principal principal = () -> "intern@example.test";
    private AttendanceReportService reports;

    @BeforeEach
    void setUp() {
        reports = new AttendanceReportService(currentUsers, attendance, calendar, reportQueries, accounts, internships);
    }

    @Test
    void usesAttendanceOwnedClassificationAndExactAggregateFormulas() {
        AttendanceActor actor = new AttendanceActor(7L, GlobalRole.INTERN);
        given(currentUsers.actor(principal)).willReturn(actor);
        given(calendar.currentBusinessDate()).willReturn(LocalDate.of(2026, 8, 31));
        given(accounts.requireIdentityById(7L))
                .willReturn(identity(7L, "Mai Intern", GlobalRole.INTERN));
        given(reportQueries.query(
                actor,
                7L,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)))
                .willReturn(new AttendanceReport(
                        7L,
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 8, 31),
                        List.of(
                                reportDay(
                                        LocalDate.of(2026, 8, 13),
                                        AttendanceReportClassification.PRESENT,
                                        Instant.parse("2026-08-13T01:30:00Z"),
                                        null,
                                        true,
                                        false,
                                        true,
                                        Optional.of(new BigDecimal("0.6667"))),
                                reportDay(
                                        LocalDate.of(2026, 8, 14),
                                        AttendanceReportClassification.ABSENT,
                                        null,
                                        null,
                                        false,
                                        false,
                                        false,
                                        Optional.of(BigDecimal.ZERO))),
                        1,
                        2,
                        Optional.of(new BigDecimal("50.00")),
                        Optional.of(new BigDecimal("33.34"))));

        var view = reports.build(principal, null, null, null);

        assertThat(view.expectedWorkdays()).isEqualTo(2);
        assertThat(view.presentWorkdays()).isEqualTo(1);
        assertThat(view.absentWorkdays()).isEqualTo(1);
        assertThat(view.attendanceRate()).isEqualTo("50.00%");
        assertThat(view.complianceRate()).isEqualTo("33.34%");
        assertThat(view.rows().getFirst().result()).isEqualTo("Present · Late, Missing checkout");
        assertThat(view.rows().getFirst().workedMinutes()).isEqualTo("N/A");
        assertThat(view.trend()).extracting(point -> point.value())
                .containsExactly("66.67%", "0.00%");
    }

    @Test
    void preservesDistinctRawAndEffectiveCheckoutDisplays() {
        AttendanceActor actor = new AttendanceActor(7L, GlobalRole.INTERN);
        given(currentUsers.actor(principal)).willReturn(actor);
        given(accounts.requireIdentityById(7L))
                .willReturn(identity(7L, "Mai Intern", GlobalRole.INTERN));
        given(reportQueries.query(
                actor,
                7L,
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 20)))
                .willReturn(new AttendanceReport(
                        7L,
                        LocalDate.of(2026, 8, 20),
                        LocalDate.of(2026, 8, 20),
                        List.of(new AttendanceReportDay(
                                LocalDate.of(2026, 8, 20),
                                AttendanceReportClassification.PRESENT,
                                1L,
                                LocalDate.of(2026, 1, 1),
                                ZoneId.of("Asia/Ho_Chi_Minh"),
                                LocalTime.of(8, 30),
                                LocalTime.of(15, 30),
                                5,
                                5,
                                new BigDecimal("0.3333"),
                                Instant.parse("2026-08-20T01:30:00Z"),
                                Instant.parse("2026-08-20T08:00:00Z"),
                                Instant.parse("2026-08-20T09:30:00Z"),
                                false,
                                false,
                                false,
                                Optional.of(BigDecimal.ONE))),
                        1,
                        1,
                        Optional.of(BigDecimal.ONE),
                        Optional.of(BigDecimal.ONE)));

        var row = reports.build(
                principal,
                null,
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 20)).rows().getFirst();

        assertThat(row.rawCheckout()).isEqualTo("15:00");
        assertThat(row.effectiveCheckout()).isEqualTo("16:30");
    }

    @Test
    void rejectsInternDetailTargetOutsideOwnAccountBeforeAttendanceRead() {
        given(currentUsers.actor(principal)).willReturn(new AttendanceActor(7L, GlobalRole.INTERN));

        assertThatThrownBy(() -> reports.build(
                principal,
                8L,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void buildsSelectedInternReportForAdmin() {
        Principal admin = () -> "admin@example.test";
        AttendanceActor actor = new AttendanceActor(1L, GlobalRole.ADMIN);
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 31);
        given(currentUsers.actor(admin)).willReturn(actor);
        given(accounts.requireIdentityById(7L))
                .willReturn(identity(7L, "Mai Intern", GlobalRole.INTERN));
        given(reportQueries.query(actor, 7L, from, to))
                .willReturn(new AttendanceReport(
                        7L,
                        from,
                        to,
                        List.of(),
                        0,
                        0,
                        Optional.empty(),
                        Optional.empty()));

        var view = reports.build(admin, 7L, from, to);

        assertThat(view.targetInternId()).isEqualTo(7L);
        assertThat(view.targetName()).isEqualTo("Mai Intern");
        assertThat(view.ownScope()).isFalse();
    }

    @Test
    void rendersMentorTargetPickerBeforeReadingAttendanceRows() {
        given(currentUsers.actor(principal)).willReturn(new AttendanceActor(2L, GlobalRole.MENTOR));
        given(calendar.currentBusinessDate()).willReturn(LocalDate.of(2026, 8, 31));
        given(internships.eligibleInternOptions(LocalDate.of(2026, 8, 31)))
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

    private static AttendanceReportDay reportDay(
            LocalDate date,
            AttendanceReportClassification classification,
            Instant checkIn,
            Instant checkout,
            boolean late,
            boolean early,
            boolean missing,
            Optional<BigDecimal> score) {
        return new AttendanceReportDay(
                date,
                classification,
                1L,
                LocalDate.of(2026, 1, 1),
                ZoneId.of("Asia/Ho_Chi_Minh"),
                LocalTime.of(8, 30),
                LocalTime.of(15, 30),
                5,
                5,
                new BigDecimal("0.3333"),
                checkIn,
                null,
                checkout,
                late,
                early,
                missing,
                score);
    }
}
