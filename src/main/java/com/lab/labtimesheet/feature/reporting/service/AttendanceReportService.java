package com.lab.labtimesheet.feature.reporting.service;

import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceReport;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceReportDay;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceReportQueryService;
import com.lab.labtimesheet.feature.reporting.model.dto.AttendanceReportRow;
import com.lab.labtimesheet.feature.reporting.model.dto.AttendanceReportView;
import com.lab.labtimesheet.feature.reporting.model.dto.ReportTrendPoint;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Composes the authorized Attendance history boundary into one HTML report dataset.
 *
 * <p>Target selection is never trusted from the browser: the Attendance current-user service
 * resolves the actor, and the Attendance report query rechecks scope before returning historical
 * classifications and formulas. Reporting only formats that immutable producer result.</p>
 */
@Service
@RequiredArgsConstructor
public class AttendanceReportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final AttendanceCurrentUserService currentUsers;
    private final AttendanceApplicationService attendance;
    private final AttendanceReportQueryService reportQueries;
    private final AccountService accounts;

    /**
     * Builds an inclusive attendance report for the authenticated actor.
     *
     * @param principal authenticated application principal
     * @param requestedInternId optional detail target; required for Mentor/Admin detail scope
     * @param requestedFrom optional inclusive local start date
     * @param requestedTo optional inclusive local end date
     * @return authorized render-ready report dataset
     * @throws AccessDeniedException when target scope is not valid for the actor
     * @throws IllegalArgumentException when the date range is reversed
     */
    public AttendanceReportView build(
            Principal principal,
            Long requestedInternId,
            LocalDate requestedFrom,
            LocalDate requestedTo) {
        AttendanceActor actor = currentUsers.actor(principal);
        LocalDate to = requestedTo == null ? attendance.currentBusinessDate() : requestedTo;
        LocalDate from = requestedFrom == null ? to.withDayOfMonth(1) : requestedFrom;
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }

        boolean ownScope = actor.role() == AttendanceRole.INTERN;
        long targetId = resolveTarget(actor, requestedInternId, ownScope);
        if (!ownScope && requestedInternId == null) {
            return emptyDetailSelection(from, to, accounts.eligibleInternOptions(to));
        }
        AttendanceReport report = reportQueries.query(actor, targetId, from, to);
        AccountIdentity target = requireInternTarget(report.internId());
        List<AttendanceReportRow> rows = report.days().stream().map(AttendanceReportService::row).toList();
        List<ReportTrendPoint> trend = report.days().stream()
                .filter(day -> day.dailyComplianceScore().isPresent())
                .map(day -> new ReportTrendPoint(DATE.format(day.workDate()),
                        percentage(day.dailyComplianceScore().orElseThrow())))
                .toList();
        return new AttendanceReportView(
                report.internId(),
                target.displayName(),
                report.from(),
                report.to(),
                ownScope,
                rows,
                report.expectedWorkdays(),
                report.presentWorkdays(),
                report.expectedWorkdays() - report.presentWorkdays(),
                report.attendanceRateDisplay(),
                report.complianceDisplay(),
                ownScope ? List.of() : accounts.eligibleInternOptions(to),
                trend);
    }

    private static long resolveTarget(AttendanceActor actor, Long requestedInternId, boolean ownScope) {
        if (ownScope) {
            if (requestedInternId != null && requestedInternId != actor.userId()) {
                throw new AccessDeniedException("Interns may view only their own attendance");
            }
            return actor.userId();
        }
        return requestedInternId == null ? 0L : requestedInternId;
    }

    private AccountIdentity requireInternTarget(long targetId) {
        AccountIdentity identity = accounts.requireIdentityById(targetId);
        if (identity.role() != GlobalRole.INTERN) {
            throw new AccessDeniedException("Attendance reports require an Intern target");
        }
        return identity;
    }

    private static AttendanceReportRow row(AttendanceReportDay day) {
        String checkIn = day.checkInAt() == null
                ? "N/A"
                : TIME.format(day.checkInAt().atZone(day.policyZoneId()));
        String rawCheckout = day.rawCheckoutAt() == null
                ? "N/A"
                : TIME.format(day.rawCheckoutAt().atZone(day.policyZoneId()));
        String effectiveCheckout = day.effectiveCheckoutAt() == null
                ? "N/A"
                : TIME.format(day.effectiveCheckoutAt().atZone(day.policyZoneId()));
        String worked = day.checkInAt() == null || day.effectiveCheckoutAt() == null
                ? "N/A"
                : Duration.between(day.checkInAt(), day.effectiveCheckoutAt()).toMinutes() + " min";
        boolean compliant = day.dailyComplianceScore()
                .map(score -> score.compareTo(BigDecimal.ONE) == 0)
                .orElse(true);
        return new AttendanceReportRow(
                DATE.format(day.workDate()),
                checkIn,
                rawCheckout,
                effectiveCheckout,
                TIME.format(day.scheduledStart()) + "–" + TIME.format(day.scheduledEnd())
                        + " (" + day.policyZoneId() + ")",
                result(day),
                worked,
                compliant);
    }

    private static String result(AttendanceReportDay day) {
        List<String> violations = new ArrayList<>(3);
        if (day.late()) {
            violations.add("Late");
        }
        if (day.earlyDeparture()) {
            violations.add("Early departure");
        }
        if (day.missingCheckout()) {
            violations.add("Missing checkout");
        }
        String classification = switch (day.classification()) {
            case HOLIDAY -> "Holiday";
            case OFF_DAY -> "Off day";
            case APPROVED_LEAVE -> "Approved leave";
            case PRESENT -> "Present";
            case ABSENT -> "Absent";
        };
        return violations.isEmpty() ? classification : classification + " · " + String.join(", ", violations);
    }

    private static String percentage(BigDecimal score) {
        return score.multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString() + "%";
    }

    private static AttendanceReportView emptyDetailSelection(
            LocalDate from, LocalDate to,
            List<com.lab.labtimesheet.feature.account.model.dto.EligibleInternOption> options) {
        return new AttendanceReportView(
                0L,
                "Select an Intern",
                from,
                to,
                false,
                List.of(),
                0L,
                0L,
                0L,
                "N/A",
                "N/A",
                options,
                List.of());
    }
}
