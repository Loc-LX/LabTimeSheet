package com.lab.labtimesheet.feature.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicyFixtures;
import com.lab.labtimesheet.feature.attendance.model.AttendanceViolations;
import com.lab.labtimesheet.feature.reporting.model.dto.AttendanceReportDay;
import com.lab.labtimesheet.feature.reporting.model.dto.AttendanceReportSummary;
import com.lab.labtimesheet.feature.reporting.model.dto.DayKind;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AttendanceReportFormulasTest {

    private static final AttendancePolicy SEEDED = AttendancePolicyFixtures.seeded(1L);
    private static final AttendanceReportService formulas = new AttendanceReportService();

    @Test
    void countsExactlyOneOfEarlyOrMissingTowardApplicableViolations() {
        assertThat(formulas.applicableViolationCount(new AttendanceViolations(false, false, false))).isZero();
        assertThat(formulas.applicableViolationCount(new AttendanceViolations(true, false, false))).isEqualTo(1);
        assertThat(formulas.applicableViolationCount(new AttendanceViolations(false, true, false))).isEqualTo(1);
        assertThat(formulas.applicableViolationCount(new AttendanceViolations(false, false, true))).isEqualTo(1);
        assertThat(formulas.applicableViolationCount(new AttendanceViolations(true, true, false))).isEqualTo(2);
        assertThat(formulas.applicableViolationCount(new AttendanceViolations(true, false, true))).isEqualTo(2);
    }

    @Test
    void dailyScoreIsOneMinusPenaltyPerViolationFlooredAtZero() {
        assertThat(formulas.dailyScore(SEEDED, new AttendanceViolations(false, false, false)))
                .isEqualByComparingTo("1.0000");
        assertThat(formulas.dailyScore(SEEDED, new AttendanceViolations(true, false, false)))
                .isEqualByComparingTo("0.7500");
        assertThat(formulas.dailyScore(SEEDED, new AttendanceViolations(true, true, false)))
                .isEqualByComparingTo("0.5000");

        AttendancePolicy fullPenalty = policy(new BigDecimal("1.00"));
        assertThat(formulas.dailyScore(fullPenalty, new AttendanceViolations(true, true, false)))
                .isEqualByComparingTo("0.0000");
    }

    @Test
    void computesRateAndComplianceForMixedPeriod() {
        List<AttendanceReportDay> days = java.util.stream.Stream.of(
                        presentDays(1, 17),
                        absentDays(18, 2),
                        leaveDays(20))
                .flatMap(List::stream)
                .toList();

        AttendanceReportSummary summary = formulas.summarize(days);

        assertThat(summary.expectedWorkdays()).isEqualTo(19);
        assertThat(summary.leaveDays()).isEqualTo(1);
        assertThat(summary.presentDays()).isEqualTo(17);
        assertThat(summary.absentDays()).isEqualTo(2);
        assertThat(summary.attendanceRate()).contains(new BigDecimal("0.8947"));
        assertThat(summary.periodCompliance()).contains(new BigDecimal("0.8947"));
    }

    @Test
    void reportsNAWhenNoExpectedWorkdays() {
        AttendanceReportSummary empty = formulas.summarize(List.of());
        assertThat(empty.expectedWorkdays()).isZero();
        assertThat(empty.attendanceRate()).isEmpty();
        assertThat(empty.periodCompliance()).isEmpty();

        AttendanceReportSummary noWorkdays = formulas.summarize(List.of(
                day(1, DayKind.OFF_DAY),
                day(2, DayKind.LEAVE)));
        assertThat(noWorkdays.expectedWorkdays()).isZero();
        assertThat(noWorkdays.attendanceRate()).isEmpty();
        assertThat(noWorkdays.periodCompliance()).isEmpty();
    }

    @Test
    void complianceUsesEachDaysHistoricalPolicyPenalty() {
        List<AttendanceReportDay> days = List.of(
                new AttendanceReportDay(
                        LocalDate.of(2026, 8, 3),
                        DayKind.WORKDAY_PRESENT,
                        SEEDED,
                        new AttendanceViolations(true, false, false)),
                new AttendanceReportDay(
                        LocalDate.of(2026, 8, 4),
                        DayKind.WORKDAY_PRESENT,
                        policy(new BigDecimal("1.00")),
                        new AttendanceViolations(true, false, false)));

        AttendanceReportSummary summary = formulas.summarize(days);

        assertThat(summary.presentDays()).isEqualTo(2);
        assertThat(summary.attendanceRate()).contains(new BigDecimal("1.0000"));
        assertThat(summary.periodCompliance()).contains(new BigDecimal("0.3750"));
    }

    private static List<AttendanceReportDay> presentDays(int first, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> day(first + index, DayKind.WORKDAY_PRESENT))
                .toList();
    }

    private static List<AttendanceReportDay> absentDays(int first, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> day(first + index, DayKind.WORKDAY_ABSENT))
                .toList();
    }

    private static List<AttendanceReportDay> leaveDays(int first) {
        return List.of(day(first, DayKind.LEAVE));
    }

    private static AttendanceReportDay day(int dayOfMonth, DayKind kind) {
        return new AttendanceReportDay(
                LocalDate.of(2026, 8, dayOfMonth),
                kind,
                SEEDED,
                new AttendanceViolations(false, false, false));
    }

    private static AttendancePolicy policy(BigDecimal penalty) {
        return new AttendancePolicy(
                9L,
                LocalDate.of(2026, 1, 1),
                SEEDED.zoneId(),
                SEEDED.scheduledStart(),
                SEEDED.scheduledEnd(),
                30,
                30,
                3,
                penalty,
                SEEDED.workdays());
    }
}