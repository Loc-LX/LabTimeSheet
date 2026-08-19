package com.lab.labtimesheet.feature.account.service;

import com.lab.labtimesheet.feature.account.model.InternshipStatus;
import com.lab.labtimesheet.feature.account.repository.InternProfileRepository;
import java.time.Clock;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Starts due active Internships periodically; request-time activation remains the correctness guard. */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class InternshipStartScheduler {

    private static final String BUSINESS_TIME_ZONE = "Asia/Ho_Chi_Minh";

    private final InternProfileRepository internProfiles;
    private final InternshipLifecycleService lifecycle;
    private final Clock clock;

    /**
     * Scans due profiles hourly in the Vietnam business timezone.
     */
    @Scheduled(cron = "0 0 * * * *", zone = BUSINESS_TIME_ZONE)
    void activateDueInternships() {
        internProfiles.findUserIdsDueForStart(InternshipStatus.NOT_STARTED, LocalDate.now(clock))
                .forEach(lifecycle::ensureStartedIfDue);
    }
}
