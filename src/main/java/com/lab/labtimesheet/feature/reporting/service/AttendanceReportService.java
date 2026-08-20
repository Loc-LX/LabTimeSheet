package com.lab.labtimesheet.feature.reporting.service;

import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendanceViolations;
import com.lab.labtimesheet.feature.reporting.model.dto.AttendanceDailyScore;
import com.lab.labtimesheet.feature.reporting.model.dto.AttendanceReportDay;
import com.lab.labtimesheet.feature.reporting.model.dto.AttendanceReportSummary;
import com.lab.labtimesheet.feature.reporting.model.dto.DayKind;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Pure attendance/compliance report formulas shared by every output format.
 *
 * <p>This service owns no persistence or authorization. It classifies only from supplied day rows,
 * so HTML, Excel, and PDF cannot drift in totals, rounding, or {@code N/A} handling (RPT-001, RPT-009).
 */
@Service
public final class AttendanceReportService {

    private static final int SCALE = 4;

    /**
     * Counts violations applicable to the daily compliance penalty.
     *
     * <p>Late counts once and exactly one of early departure or missing checkout counts once
     * (ATT-016). The two departure outcomes are mutually exclusive in the domain; an XOR keeps the
     * formula bounded even if both flags were ever observed.
     *
     * @param violations independent attendance violation flags
     * @return applicable violation count from zero through two
     */
    public int applicableViolationCount(AttendanceViolations violations) {
        int late = violations.late() ? 1 : 0;
        int departure = violations.earlyDeparture() ^ violations.missingCheckout() ? 1 : 0;
        return late + departure;
    }

    /**
     * Computes one present workday's compliance score.
     *
     * <p>The score is {@code max(0, 1 - policy penalty x applicable violation count)} using the day's
     * historical policy penalty (ATT-015, ATT-016). The floor prevents a negative score.
     *
     * @param policy policy version effective on the attendance date
     * @param violations applicable violation flags for the day
     * @return daily score from zero through one, rounded to four decimal places
     */
    public BigDecimal dailyScore(AttendancePolicy policy, AttendanceViolations violations) {
        BigDecimal penalties = policy.violationPenalty()
                .multiply(BigDecimal.valueOf(applicableViolationCount(violations)));
        return BigDecimal.ONE.subtract(penalties)
                .max(BigDecimal.ZERO)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Summarizes a classified period into the shared report totals.
     *
     * <p>Attendance rate is present divided by present plus absent, since approved leave is excluded
     * from the denominator; compliance is the average daily score over expected workdays (ATT-014,
     * ATT-017). Either value is empty ({@code N/A}) when its denominator is zero.
     *
     * @param days every classified date in the requested range
     * @return hand-checkable period totals and optional rate and compliance values
     */
    public AttendanceReportSummary summarize(List<AttendanceReportDay> days) {
        long present = 0;
        long absent = 0;
        long leave = 0;
        BigDecimal complianceSum = BigDecimal.ZERO;
        for (AttendanceReportDay day : days) {
            switch (day.kind()) {
                case WORKDAY_PRESENT -> {
                    present++;
                    complianceSum = complianceSum.add(dailyScore(day.policy(), day.violations()));
                }
                case WORKDAY_ABSENT -> absent++;
                case LEAVE -> leave++;
                case OFF_DAY -> { /* excluded from every denominator and average */ }
            }
        }
        long expectedWorkdays = present + absent;
        BigDecimal denominator = BigDecimal.valueOf(expectedWorkdays);
        Optional<BigDecimal> rate = denominator.signum() == 0
                ? Optional.empty()
                : Optional.of(BigDecimal.valueOf(present)
                        .divide(denominator, SCALE, RoundingMode.HALF_UP));
        Optional<BigDecimal> compliance = expectedWorkdays == 0
                ? Optional.empty()
                : Optional.of(complianceSum.divide(denominator, SCALE, RoundingMode.HALF_UP));
        return new AttendanceReportSummary(
                expectedWorkdays, leave, present, absent, rate, compliance);
    }

    /**
     * Builds the daily compliance series for the trend chart, present workdays only.
     *
     * <p>Off-days, leave, and absent days have no daily score, so only present days enter the
     * series; an empty series means the page omits the chart rather than drawing a meaningless one
     * (UI-011).
     *
     * @param days every classified date in the requested range
     * @return present-day scores in ascending date order
     */
    public List<AttendanceDailyScore> dailyScoreSeries(List<AttendanceReportDay> days) {
        return days.stream()
                .filter(day -> day.kind() == DayKind.WORKDAY_PRESENT)
                .sorted(Comparator.comparing(AttendanceReportDay::date))
                .map(day -> new AttendanceDailyScore(
                        day.date(), dailyScore(day.policy(), day.violations())))
                .toList();
    }
}