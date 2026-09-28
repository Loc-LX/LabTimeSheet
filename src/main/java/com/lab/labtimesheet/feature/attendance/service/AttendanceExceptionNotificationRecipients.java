package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.internship.service.InternshipService;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationRecipient;
import com.lab.labtimesheet.platform.model.GlobalRole;
import java.util.List;
import org.springframework.stereotype.Component;

/** Resolves the {@code D47} recipient route for Attendance request notifications, used by attendance exception and Leave requests: the active responsible Mentor, or every active Admin when that Mentor is absent, {@code LOCKED} or {@code DEACTIVATED}. */
@Component
final class AttendanceExceptionNotificationRecipients {

    private final AccountService accounts;
    private final InternshipService internships;

    AttendanceExceptionNotificationRecipients(AccountService accounts, InternshipService internships) {
        this.accounts = accounts;
        this.internships = internships;
    }

    /**
     * Selects the active responsible Mentor, or every active Admin when that Mentor is absent or unavailable.
     *
     * @param internId owner of the exception request
     * @return notification recipients resolved under D47
     */
    List<NotificationRecipient> forIntern(long internId) {
        List<AccountIdentity> recipients = internships.responsibleMentorUserId(internId)
                .flatMap(accounts::identityById)
                .filter(identity -> identity.role() == GlobalRole.MENTOR
                        && identity.status() == AccountStatus.ACTIVE)
                .map(List::of)
                .orElseGet(accounts::activeAdminIdentities);
        return recipients.stream()
                .map(identity -> new NotificationRecipient(identity.id(), identity.email()))
                .toList();
    }
}
