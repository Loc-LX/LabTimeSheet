package com.lab.labtimesheet.feature.account.service;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.InternshipStatus;
import com.lab.labtimesheet.feature.account.repository.AppUserRepository;
import com.lab.labtimesheet.feature.account.repository.InternProfileRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Adapts persisted account credentials and lifecycle state to Spring Security authentication. */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class DatabaseUserDetailsService implements UserDetailsService {
    private final AppUserRepository users;
    private final InternProfileRepository internProfiles;

    /**
     * Loads the normalized account and disables authentication unless its lifecycle state is active.
     *
     * @param username submitted email address
     * @return Spring Security user details with the immutable global role
     * @throws UsernameNotFoundException when no account has that normalized email
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var account = users.findByNormalizedEmail(BootstrapService.normalizeEmail(username))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        String hash = account.getPasswordHash();
        boolean withdrawnIntern = account.getGlobalRole() == GlobalRole.INTERN
                && internProfiles.existsByUserIdAndInternshipStatus(account.getId(), InternshipStatus.WITHDRAWN);
        return User.withUsername(account.getEmail())
                .password(hash == null ? "{noop}unavailable" : hash)
                .roles(account.getGlobalRole().name())
                .disabled(account.getAccountStatus() != AccountStatus.ACTIVE || withdrawnIntern)
                .build();
    }
}
