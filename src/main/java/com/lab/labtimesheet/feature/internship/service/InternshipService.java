package com.lab.labtimesheet.feature.internship.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.internship.model.InternshipStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountAdministrationView;
import com.lab.labtimesheet.feature.identity.model.dto.AccountCreation;
import com.lab.labtimesheet.feature.identity.model.dto.AccountDirectoryFilter;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentityCorrection;
import com.lab.labtimesheet.feature.identity.model.dto.AccountSummary;
import com.lab.labtimesheet.feature.identity.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.internship.model.dto.EligibleInternOption;
import com.lab.labtimesheet.feature.internship.model.dto.InternReportingWindow;
import com.lab.labtimesheet.feature.internship.model.dto.InternWorkWindow;
import com.lab.labtimesheet.feature.internship.model.dto.InternshipAccountAdministrationView;
import com.lab.labtimesheet.feature.internship.model.dto.InternshipLifecycleGuard;
import com.lab.labtimesheet.feature.internship.model.dto.LockedAccountMutationEligibility;
import com.lab.labtimesheet.feature.internship.model.entity.InternProfile;
import com.lab.labtimesheet.feature.internship.repository.InternProfileRepository;
import com.lab.labtimesheet.platform.model.GlobalRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns Intern profile creation, lifecycle and eligibility, composing identity facts through {@link AccountService}.
 * Repository queries never cross the module boundary and every composition uses a bounded number of batched reads.
 */
@Service
public class InternshipService {
    private final AccountService accounts;
    private final InternProfileRepository internProfiles;
    private final InternshipLifecycleReadiness lifecycleReadiness;
    private final Clock clock;

    InternshipService(
            AccountService accounts,
            InternProfileRepository internProfiles,
            InternshipLifecycleReadiness lifecycleReadiness,
            Clock clock) {
        this.accounts = accounts;
        this.internProfiles = internProfiles;
        this.lifecycleReadiness = lifecycleReadiness;
        this.clock = clock;
    }

    /** Creates an account and its required Intern profile atomically before activation delivery. */
    public AccountCreation create(CreateAccountCommand command, long adminId) {
        ValidatedAccount account = validate(command);
        Instant now = clock.instant();
        return accounts.createIdentity(
                account.email(), account.displayName(), account.role(), adminId,
                userId -> {
                    if (account.role() == GlobalRole.INTERN) {
                        internProfiles.save(InternProfile.notStarted(
                                userId, account.studentCode(), account.internshipStart(), account.internshipEnd(), now));
                    }
                });
    }

    /** Corrects identity and permitted Intern-profile fields in one account-first transaction. */
    @Transactional
    public void correctAccount(long targetUserId, long adminId, AccountIdentityCorrection correction) {
        if (correction == null) {
            throw new IllegalArgumentException("Account correction is required");
        }
        boolean profileChange = correction.studentCode() != null
                || correction.internshipStart() != null
                || correction.internshipEnd() != null;
        accounts.correctIdentity(
                targetUserId,
                adminId,
                correction.email(),
                profileChange,
                target -> correctProfile(target.id(), correction));
    }

    /** Returns the Admin dashboard counts composed from identity and internship state. */
    @Transactional(readOnly = true)
    public AccountSummary summary() {
        return new AccountSummary(
                accounts.countByAccountStatus(AccountStatus.ACTIVE),
                accounts.countByAccountStatus(AccountStatus.PENDING_ACTIVATION),
                internProfiles.countByInternshipStatus(InternshipStatus.ACTIVE));
    }

    /** Returns every Admin account view with its optional profile in account-ID order. */
    @Transactional(readOnly = true)
    public List<InternshipAccountAdministrationView> administrationViews(long adminId) {
        List<AccountAdministrationView> identities = accounts.administrationViews(adminId);
        return composeDirectory(identities, profilesByIds(identities.stream().map(AccountAdministrationView::id).toList()));
    }

    /** Returns the union of identity and Student Code matches once each in stable account-ID order. */
    @Transactional(readOnly = true)
    public List<InternshipAccountAdministrationView> administrationViews(
            long adminId, AccountDirectoryFilter filter) {
        if (filter == null) {
            throw new IllegalArgumentException("Account directory filter is required");
        }
        if (filter.search().isEmpty() && filter.role() == null) {
            return administrationViews(adminId);
        }
        List<AccountAdministrationView> identityMatches = accounts.administrationViews(adminId, filter);
        List<InternProfile> studentCodeMatches = internProfiles.findByStudentCodeFilter(filter.search());
        List<Long> studentMatchIds = filter.role() == null || filter.role() == GlobalRole.INTERN
                ? studentCodeMatches.stream().map(InternProfile::getUserId).toList()
                : List.of();
        List<AccountAdministrationView> studentIdentities = accounts.administrationViewsByIds(adminId, studentMatchIds);

        Map<Long, AccountAdministrationView> union = new TreeMap<>();
        identityMatches.forEach(identity -> union.put(identity.id(), identity));
        studentIdentities.forEach(identity -> union.put(identity.id(), identity));
        List<AccountAdministrationView> identities = List.copyOf(union.values());
        return composeDirectory(identities, profilesByIds(union.keySet()));
    }

    /** Resolves one composed Admin account view. */
    @Transactional(readOnly = true)
    public InternshipAccountAdministrationView administrationView(long targetUserId, long adminId) {
        return administrationViews(adminId).stream()
                .filter(view -> view.id() == targetUserId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    /** Moves an active Intern's profile from NOT_STARTED to ACTIVE after its configured start date. */
    @Transactional
    public void activateInternship(long internUserId, long adminId) {
        accounts.requireActiveAdminId(adminId);
        AccountIdentity intern = lockedIdentity(internUserId);
        if (intern.role() != GlobalRole.INTERN || intern.status() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("An active Intern account is required");
        }
        InternProfile profile = lockInternProfile(internUserId);
        if (businessDate().isBefore(profile.getInternshipStartDate())) {
            throw new IllegalStateException("Internship cannot activate before its start date");
        }
        profile.activate(clock.instant());
    }

    /** Activates every due active Intern after rechecking the locked account and profile. */
    @Transactional
    public int activateDueInternships() {
        LocalDate selectionDate = businessDate();
        HashSet<Long> activeInternIds = new HashSet<>(accounts.accountIdsByRoleAndStatusOrderedById(
                GlobalRole.INTERN, AccountStatus.ACTIVE));
        List<Long> dueIds = internProfiles.findDueUserIds(InternshipStatus.NOT_STARTED, selectionDate).stream()
                .filter(activeInternIds::contains)
                .toList();
        int activated = 0;
        for (Long userId : dueIds) {
            AccountIdentity intern;
            try {
                intern = lockedIdentity(userId);
            } catch (IllegalArgumentException missing) {
                continue;
            }
            if (intern.role() != GlobalRole.INTERN || intern.status() != AccountStatus.ACTIVE) {
                continue;
            }
            InternProfile profile = internProfiles.findForUpdateByUserId(userId).orElse(null);
            if (profile == null) {
                continue;
            }
            Instant now = clock.instant();
            LocalDate currentDate = now.atZone(clock.getZone()).toLocalDate();
            if (profile.getInternshipStatus() == InternshipStatus.NOT_STARTED
                    && !currentDate.isBefore(profile.getInternshipStartDate())
                    && !currentDate.isAfter(profile.getInternshipEndDate())) {
                profile.activate(now);
                activated++;
            }
        }
        return activated;
    }

    /** Returns producer-owned readiness facts for an Admin-managed Intern account page. */
    @Transactional(readOnly = true)
    public InternshipLifecycleGuard internshipLifecycleReadiness(long internUserId, long adminId) {
        accounts.requireActiveAdminId(adminId);
        AccountIdentity intern = accounts.requireIdentityById(internUserId);
        if (intern.role() != GlobalRole.INTERN) {
            throw new IllegalArgumentException("An Intern account is required");
        }
        return lifecycleReadiness.preview(internUserId);
    }

    /** Completes an Intern after recomputing producer-owned readiness under the shared lock order. */
    @Transactional
    public void completeInternship(long internUserId, long adminId) {
        List<LockedAccountMutationEligibility> lockedAccounts = lockTerminalAccounts(adminId, internUserId);
        LockedAccountMutationEligibility intern = terminalIntern(lockedAccounts, adminId, internUserId);
        requireTerminalGuard(lifecycleReadiness.lockForTerminalAction(internUserId));
        if (intern.accountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Only an active Intern account can complete");
        }
        lockInternProfile(internUserId).complete(clock.instant());
    }

    /** Withdraws an Intern after recomputing readiness, then deactivates its identity atomically. */
    @Transactional
    public void withdrawInternship(long internUserId, long adminId) {
        List<LockedAccountMutationEligibility> lockedAccounts = lockTerminalAccounts(adminId, internUserId);
        LockedAccountMutationEligibility intern = terminalIntern(lockedAccounts, adminId, internUserId);
        requireTerminalGuard(lifecycleReadiness.lockForTerminalAction(internUserId));
        if (intern.accountStatus() == AccountStatus.PENDING_ACTIVATION
                || intern.accountStatus() == AccountStatus.DEACTIVATED) {
            throw new IllegalStateException("Only an activated Intern account can withdraw");
        }
        lockInternProfile(internUserId).withdraw(clock.instant());
        accounts.deactivateAccount(internUserId, adminId);
    }

    /** Returns whether an account and its internship are currently active. */
    @Transactional(readOnly = true)
    public boolean isEligibleIntern(long userId) {
        AccountIdentity identity = identityOrNull(userId);
        return identity != null
                && identity.role() == GlobalRole.INTERN
                && identity.status() == AccountStatus.ACTIVE
                && internProfiles.existsByUserIdAndInternshipStatus(userId, InternshipStatus.ACTIVE);
    }

    /** Returns whether an active Intern is eligible on an inclusive work date. */
    @Transactional(readOnly = true)
    public boolean isEligibleIntern(long userId, LocalDate workDate) {
        if (workDate == null) {
            throw new IllegalArgumentException("Work date is required");
        }
        AccountIdentity identity = identityOrNull(userId);
        return identity != null
                && identity.role() == GlobalRole.INTERN
                && identity.status() == AccountStatus.ACTIVE
                && internProfiles
                        .existsByUserIdAndInternshipStatusAndInternshipStartDateLessThanEqualAndInternshipEndDateGreaterThanEqual(
                                userId, InternshipStatus.ACTIVE, workDate, workDate);
    }

    /** Resolves the retained historical reporting window of an Intern. */
    @Transactional(readOnly = true)
    public Optional<InternReportingWindow> historicalInternReportingWindow(long userId) {
        if (userId <= 0) {
            return Optional.empty();
        }
        AccountIdentity user = identityOrNull(userId);
        if (user == null || user.role() != GlobalRole.INTERN) {
            return Optional.empty();
        }
        InternProfile profile = internProfiles.findById(userId).orElse(null);
        if (profile == null || profile.getActivatedAt() == null) {
            return Optional.empty();
        }
        Instant terminalAt = switch (profile.getInternshipStatus()) {
            case ACTIVE -> null;
            case COMPLETED -> profile.getCompletedAt();
            case WITHDRAWN -> profile.getWithdrawnAt();
            case NOT_STARTED -> null;
        };
        if (profile.getInternshipStatus() == InternshipStatus.ACTIVE && user.status() != AccountStatus.ACTIVE) {
            return Optional.empty();
        }
        if (profile.getInternshipStatus() == InternshipStatus.NOT_STARTED
                || (terminalAt == null && profile.getInternshipStatus() != InternshipStatus.ACTIVE)) {
            return Optional.empty();
        }
        LocalDate activationDate = profile.getActivatedAt().atZone(clock.getZone()).toLocalDate();
        LocalDate terminalDate = terminalAt == null ? null : terminalAt.atZone(clock.getZone()).toLocalDate();
        LocalDate startDate = activationDate.isAfter(profile.getInternshipStartDate())
                ? activationDate : profile.getInternshipStartDate();
        LocalDate endDate = profile.getInternshipEndDate();
        if (terminalDate != null && terminalDate.isBefore(endDate)) {
            endDate = terminalDate;
        }
        if (endDate.isBefore(startDate)) {
            return Optional.empty();
        }
        return Optional.of(new InternReportingWindow(userId, activationDate, terminalDate, startDate, endDate));
    }

    /** Locks an account and its Intern profile and returns the inclusive work-window snapshot. */
    @Transactional
    public InternWorkWindow lockedInternWorkWindow(long userId, LocalDate businessDate) {
        if (userId <= 0) {
            throw new IllegalArgumentException("Intern user ID must be positive");
        }
        if (businessDate == null) {
            throw new IllegalArgumentException("Business date is required");
        }
        AccountIdentity user = lockedInternIdentity(userId);
        InternProfile profile = lockInternProfile(userId);
        Instant now = clock.instant();
        LocalDate currentDate = now.atZone(clock.getZone()).toLocalDate();
        activateIfDue(user, profile, currentDate, now);
        return new InternWorkWindow(
                userId,
                businessDate,
                profile.getInternshipStartDate(),
                profile.getInternshipEndDate(),
                user.status(),
                profile.getInternshipStatus());
    }

    /** Locks all requested accounts first, then every requested Intern profile, both in ascending account-ID order. */
    @Transactional
    public List<LockedAccountMutationEligibility> lockedAccountMutationEligibility(Collection<Long> userIds) {
        List<AccountIdentity> lockedUsers = accounts.lockedIdentities(userIds);
        Map<Long, InternProfile> lockedProfiles = new LinkedHashMap<>();
        for (AccountIdentity user : lockedUsers) {
            if (user.role() == GlobalRole.INTERN) {
                lockedProfiles.put(user.id(), lockInternProfile(user.id()));
            }
        }
        Instant now = clock.instant();
        LocalDate currentDate = now.atZone(clock.getZone()).toLocalDate();
        for (AccountIdentity user : lockedUsers) {
            InternProfile profile = lockedProfiles.get(user.id());
            if (profile != null) {
                activateIfDue(user, profile, currentDate, now);
            }
        }
        return lockedUsers.stream()
                .map(user -> new LockedAccountMutationEligibility(
                        user.id(),
                        user.role(),
                        user.status(),
                        user.role() == GlobalRole.INTERN
                                ? Optional.of(lockedProfiles.get(user.id()).getInternshipStatus())
                                : Optional.empty()))
                .toList();
    }

    /**
     * Composes eligible account and profile intersections using database positions; Java never compares names.
     */
    @Transactional(readOnly = true)
    public List<EligibleInternOption> eligibleInternOptions(LocalDate businessDate) {
        if (businessDate == null) {
            throw new IllegalArgumentException("Business date is required");
        }
        List<AccountIdentity> identities = accounts.identitiesByRoleAndStatusOrderedByDisplayName(
                GlobalRole.INTERN, AccountStatus.ACTIVE);
        List<InternProfile> profiles = internProfiles.findEligibleProfiles(InternshipStatus.ACTIVE, businessDate);

        Map<Long, AccountIdentity> identitiesById = new HashMap<>();
        Map<Long, Integer> nameRanks = new HashMap<>();
        String previousName = null;
        int nameRank = -1;
        for (AccountIdentity identity : identities) {
            if (previousName == null || !previousName.equals(identity.displayName())) {
                nameRank++;
                previousName = identity.displayName();
            }
            identitiesById.put(identity.id(), identity);
            nameRanks.put(identity.id(), nameRank);
        }
        Map<Long, Integer> profileRanks = new HashMap<>();
        for (int index = 0; index < profiles.size(); index++) {
            profileRanks.put(profiles.get(index).getUserId(), index);
        }
        List<EligibleInternOption> options = new ArrayList<>();
        for (InternProfile profile : profiles) {
            AccountIdentity identity = identitiesById.get(profile.getUserId());
            if (identity != null) {
                options.add(new EligibleInternOption(
                        identity.id(),
                        identity.displayName(),
                        profile.getStudentCode(),
                        profile.getInternshipStartDate(),
                        profile.getInternshipEndDate()));
            }
        }
        options.sort(java.util.Comparator
                .comparingInt((EligibleInternOption option) -> nameRanks.get(option.userId()))
                .thenComparingInt(option -> profileRanks.get(option.userId()))
                .thenComparingLong(EligibleInternOption::userId));
        return List.copyOf(options);
    }

    /** Resolves the identity of a currently eligible Intern. */
    @Transactional(readOnly = true)
    public AccountIdentity requireEligibleIntern(long userId) {
        if (!isEligibleIntern(userId)) {
            throw new IllegalArgumentException("An active Intern account and internship are required");
        }
        return accounts.requireIdentityById(userId);
    }

    /** Returns the Student Code for an account that has an Intern profile. */
    @Transactional(readOnly = true)
    public Optional<String> studentCodeByUserId(long userId) {
        return internProfiles.findById(userId).map(InternProfile::getStudentCode);
    }

    private void correctProfile(long userId, AccountIdentityCorrection correction) {
        InternProfile profile = lockInternProfile(userId);
        if (correction.studentCode() != null) {
            profile.correctStudentCode(correction.studentCode(), clock.instant());
        }
        if (correction.internshipStart() != null || correction.internshipEnd() != null) {
            LocalDate correctedStart = correction.internshipStart() == null
                    ? profile.getInternshipStartDate()
                    : correction.internshipStart();
            LocalDate correctedEnd = correction.internshipEnd() == null
                    ? profile.getInternshipEndDate()
                    : correction.internshipEnd();
            profile.correctDates(correctedStart, correctedEnd, clock.instant());
        }
    }

    private Map<Long, InternProfile> profilesByIds(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, InternProfile> result = new HashMap<>();
        internProfiles.findByUserIdsOrderByUserId(userIds).forEach(profile -> result.put(profile.getUserId(), profile));
        return result;
    }

    private static List<InternshipAccountAdministrationView> composeDirectory(
            List<AccountAdministrationView> identities,
            Map<Long, InternProfile> profiles) {
        return identities.stream().map(identity -> {
            InternProfile profile = profiles.get(identity.id());
            return new InternshipAccountAdministrationView(
                    identity.id(),
                    identity.email(),
                    identity.displayName(),
                    identity.role(),
                    identity.accountStatus(),
                    profile == null ? null : profile.getStudentCode(),
                    profile == null ? null : profile.getInternshipStartDate(),
                    profile == null ? null : profile.getInternshipEndDate(),
                    profile == null ? null : profile.getInternshipStatus());
        }).toList();
    }

    private AccountIdentity lockedIdentity(long userId) {
        return accounts.lockedIdentities(List.of(userId)).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    private AccountIdentity lockedInternIdentity(long userId) {
        AccountIdentity identity = lockedIdentity(userId);
        if (identity.role() != GlobalRole.INTERN) {
            throw new IllegalArgumentException("An Intern account is required");
        }
        return identity;
    }

    private InternProfile lockInternProfile(long userId) {
        return internProfiles.findForUpdateByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Intern profile not found"));
    }

    private AccountIdentity identityOrNull(long userId) {
        return accounts.identityById(userId).orElse(null);
    }

    private List<LockedAccountMutationEligibility> lockTerminalAccounts(long adminId, long internUserId) {
        try {
            return lockedAccountMutationEligibility(List.of(adminId, internUserId));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Account action could not be completed", failure);
        }
    }

    private static LockedAccountMutationEligibility terminalIntern(
            List<LockedAccountMutationEligibility> lockedAccounts,
            long adminId,
            long internUserId) {
        LockedAccountMutationEligibility admin = lockedEligibility(lockedAccounts, adminId);
        if (admin.role() != GlobalRole.ADMIN || admin.accountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("An active Admin is required");
        }
        LockedAccountMutationEligibility intern = lockedEligibility(lockedAccounts, internUserId);
        if (intern.role() != GlobalRole.INTERN) {
            throw new IllegalArgumentException("An Intern account is required");
        }
        return intern;
    }

    private static LockedAccountMutationEligibility lockedEligibility(
            List<LockedAccountMutationEligibility> lockedAccounts, long userId) {
        return lockedAccounts.stream()
                .filter(snapshot -> snapshot.userId() == userId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Locked Account snapshot is missing"));
    }

    private static void activateIfDue(
            AccountIdentity user, InternProfile profile, LocalDate currentDate, Instant now) {
        if (user.status() == AccountStatus.ACTIVE
                && profile.getInternshipStatus() == InternshipStatus.NOT_STARTED
                && !currentDate.isBefore(profile.getInternshipStartDate())
                && !currentDate.isAfter(profile.getInternshipEndDate())) {
            profile.activate(now);
        }
    }

    private static void requireTerminalGuard(InternshipLifecycleGuard guard) {
        if (guard == null) {
            throw new IllegalArgumentException("Internship lifecycle guard is required");
        }
        if (guard.currentLeader()) {
            throw new IllegalStateException("Intern is still a current Leader");
        }
        if (guard.unfinishedTaskCount() > 0) {
            throw new IllegalStateException("Intern still owns unfinished Tasks");
        }
    }

    private LocalDate businessDate() {
        return clock.instant().atZone(clock.getZone()).toLocalDate();
    }

    private static ValidatedAccount validate(CreateAccountCommand command) {
        if (command == null || command.role() == null) {
            throw new IllegalArgumentException("Account role is required");
        }
        String email = requireText(command.email(), "Email");
        String displayName = requireText(command.displayName(), "Display name");
        if (command.role() != GlobalRole.INTERN) {
            if (command.studentCode() != null || command.internshipStart() != null || command.internshipEnd() != null) {
                throw new IllegalArgumentException("Internship fields are allowed only for Intern accounts");
            }
            return new ValidatedAccount(email, displayName, command.role(), null, null, null);
        }
        String studentCode = requireText(command.studentCode(), "Student code");
        if (command.internshipStart() == null || command.internshipEnd() == null
                || command.internshipEnd().isBefore(command.internshipStart())) {
            throw new IllegalArgumentException("A valid internship date range is required");
        }
        return new ValidatedAccount(
                email, displayName, command.role(), studentCode, command.internshipStart(), command.internshipEnd());
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private record ValidatedAccount(
            String email,
            String displayName,
            GlobalRole role,
            String studentCode,
            LocalDate internshipStart,
            LocalDate internshipEnd) {
    }
}
