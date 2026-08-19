package com.lab.labtimesheet.feature.account.service;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.InternshipStatus;
import com.lab.labtimesheet.feature.account.model.entity.AppUser;
import com.lab.labtimesheet.feature.account.model.entity.InternProfile;
import com.lab.labtimesheet.feature.account.repository.AppUserRepository;
import com.lab.labtimesheet.feature.account.repository.InternProfileRepository;
import com.lab.labtimesheet.feature.project.service.ProjectInternshipGuardService;
import com.lab.labtimesheet.feature.task.service.TaskQueryService;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns automatic and administrator-authorized Intern internship lifecycle transitions.
 * Request-time start activation is deliberately a separate transaction so it can safely be
 * called by read-only eligibility queries.
 */
@Service
public class InternshipLifecycleService {

    private final AppUserRepository users;
    private final InternProfileRepository internProfiles;
    private final ProjectInternshipGuardService projectGuards;
    private final TaskQueryService taskQueries;
    private final SessionRegistry sessionRegistry;
    private final Clock clock;

    InternshipLifecycleService(
            AppUserRepository users,
            InternProfileRepository internProfiles,
            ProjectInternshipGuardService projectGuards,
            TaskQueryService taskQueries,
            SessionRegistry sessionRegistry,
            Clock clock) {
        this.users = users;
        this.internProfiles = internProfiles;
        this.projectGuards = projectGuards;
        this.taskQueries = taskQueries;
        this.sessionRegistry = sessionRegistry;
        this.clock = clock;
    }

    /**
     * Performs the existing Admin-authorized start transition once its inclusive start date has arrived.
     *
     * @param internUserId Intern account identifier
     * @param adminId active Admin authorizing the transition
     */
    @Transactional
    public void activateInternship(long internUserId, long adminId) {
        requireActiveAdmin(adminId);
        AppUser intern = lockedUser(internUserId);
        requireIntern(intern);
        if (intern.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("An active Intern account is required");
        }
        InternProfile profile = lockedProfile(internUserId);
        if (LocalDate.now(clock).isBefore(profile.getInternshipStartDate())) {
            throw new IllegalStateException("Internship cannot activate before its start date");
        }
        profile.activate(clock.instant());
    }

    /**
     * Starts an active, due internship if present. Missing/non-Intern/inactive accounts are ignored so this
     * method can guard generic account eligibility reads and scheduler candidates safely.
     *
     * @param userId account identifier to evaluate
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureStartedIfDue(long userId) {
        AppUser intern = users.findForUpdateById(userId).orElse(null);
        if (intern == null || intern.getGlobalRole() != GlobalRole.INTERN
                || intern.getAccountStatus() != AccountStatus.ACTIVE) {
            return;
        }
        InternProfile profile = internProfiles.findForUpdateByUserId(userId).orElse(null);
        if (profile == null
                || profile.getInternshipStatus() != InternshipStatus.NOT_STARTED
                || LocalDate.now(clock).isBefore(profile.getInternshipStartDate())) {
            return;
        }
        profile.activate(clock.instant());
    }

    /**
     * Completes an active internship after rechecking terminal Project and Task guards.
     *
     * @param internUserId Intern account identifier
     * @param adminId active Admin authorizing the transition
     */
    @Transactional
    public void completeInternship(long internUserId, long adminId) {
        requireActiveAdmin(adminId);
        AppUser intern = lockedUser(internUserId);
        requireIntern(intern);
        InternProfile profile = lockedProfile(internUserId);
        if (profile.getInternshipStatus() != InternshipStatus.ACTIVE) {
            throw new IllegalStateException("Only an active internship can complete");
        }
        terminalGuard().assertAllowed(internUserId);
        profile.complete(clock.instant());
    }

    /**
     * Withdraws a not-started or active internship after rechecking terminal Project and Task guards and
     * expires every registered session for the account.
     *
     * @param internUserId Intern account identifier
     * @param adminId active Admin authorizing the transition
     */
    @Transactional
    public void withdrawInternship(long internUserId, long adminId) {
        requireActiveAdmin(adminId);
        AppUser intern = lockedUser(internUserId);
        requireIntern(intern);
        InternProfile profile = lockedProfile(internUserId);
        if (profile.getInternshipStatus() != InternshipStatus.NOT_STARTED
                && profile.getInternshipStatus() != InternshipStatus.ACTIVE) {
            throw new IllegalStateException("Only a not-started or active internship can withdraw");
        }
        terminalGuard().assertAllowed(internUserId);
        profile.withdraw(clock.instant());
        expireSessions(intern.getEmail());
    }

    private InternshipTerminalGuard terminalGuard() {
        return new InternshipTerminalGuard(projectGuards, taskQueries);
    }

    private AppUser lockedUser(long userId) {
        return users.findForUpdateById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Intern not found"));
    }

    private InternProfile lockedProfile(long userId) {
        return internProfiles.findForUpdateByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Intern profile not found"));
    }

    private void requireActiveAdmin(long adminId) {
        AppUser admin = users.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));
        if (admin.getGlobalRole() != GlobalRole.ADMIN || admin.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("An active Admin is required");
        }
    }

    private static void requireIntern(AppUser user) {
        if (user.getGlobalRole() != GlobalRole.INTERN) {
            throw new IllegalArgumentException("An Intern account is required");
        }
    }

    private void expireSessions(String email) {
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            String principalName = principal instanceof org.springframework.security.core.userdetails.UserDetails details
                    ? details.getUsername()
                    : String.valueOf(principal);
            if (email.equalsIgnoreCase(principalName)) {
                for (SessionInformation information : sessionRegistry.getAllSessions(principal, false)) {
                    information.expireNow();
                }
            }
        }
    }
}
