package com.lab.labtimesheet.feature.reporting.service;

import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceHistoryItem;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService;
import com.lab.labtimesheet.feature.reporting.model.dto.AttendanceReportRow;
import com.lab.labtimesheet.feature.reporting.model.dto.AttendanceReportView;
import com.lab.labtimesheet.feature.reporting.model.dto.ReportTrendPoint;
import java.security.Principal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Composes the authorized Attendance history boundary into one HTML report dataset.
 *
 * <p>Target selection is never trusted from the browser: the Attendance current-user service
 * resolves the actor, and the Attendance application service rechecks own/detail scope before
 * rows are returned. Reporting calculates only display totals and never reads Attendance tables.</p>
 */
@Service
@RequiredArgsConstructor
public class AttendanceReportService {

    private final AttendanceCurrentUserService currentUsers;
    private final AttendanceApplicationService attendance;
    private final AccountService accounts;

    /**
     * Builds an inclusive attendance report for the authenticated actor.
     *
     * @param principal authenticated application principal
     * @param requestedInternId optional detail target; required for Mentor/Admin scope
     * @param requestedFrom optional inclusive local start date
     * @param requestedTo optional inclusive local end date
     * @return authorized render-ready report dataset
     * @throws AccessDeniedException when target scope is not valid for the actor
     * @throws IllegalArgumentException when the date range is reversed
     */
    @Transactional(readOnly = true)
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
        AccountIdentity target = requireInternTarget(targetId);
        List<AttendanceHistoryItem> history = attendance.history(actor, targetId, from, to);
        List<AttendanceReportRow> rows = history.stream().map(AttendanceReportService::row).toList();
        long compliant = rows.stream().filter(AttendanceReportRow::compliant).count();
        long recorded = rows.size();
        List<ReportTrendPoint> trend = history.stream()
                .map(item -> new ReportTrendPoint(
                        item.workDateDisplay(),
                        item.violations().late()
                                || item.violations().earlyDeparture()
                                || item.violations().missingCheckout()
                                ? "0.0%"
                                : "100.0%"))
                .toList();
        return new AttendanceReportView(
                targetId,
                target.displayName(),
                from,
                to,
                ownScope,
                rows,
                recorded,
                compliant,
                recorded - compliant,
                percentage(compliant, recorded),
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

    private static AttendanceReportRow row(AttendanceHistoryItem item) {
        boolean compliant = !item.violations().late()
                && !item.violations().earlyDeparture()
                && !item.violations().missingCheckout();
        String worked = item.checkOutAt() == null
                ? "N/A"
                : Duration.between(item.checkInAt(), item.checkOutAt()).toMinutes() + " min";
        return new AttendanceReportRow(
                item.workDateDisplay(),
                item.checkInTimeDisplay(),
                item.checkOutTimeDisplay(),
                item.scheduledStartDisplay() + "–" + item.scheduledEndDisplay()
                        + " (" + item.policy().zoneId() + ")",
                item.resultDisplay(),
                worked,
                compliant);
    }

    private static String percentage(long numerator, long denominator) {
        return denominator == 0
                ? "N/A"
                : String.format(Locale.ROOT, "%.1f%%", numerator * 100.0 / denominator);
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
                options,
                List.of());
    }
}
