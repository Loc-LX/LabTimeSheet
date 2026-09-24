package com.lab.labtimesheet.feature.identity.service;

import java.time.Clock;

import com.lab.labtimesheet.feature.identity.repository.AppUserRepository;
import com.lab.labtimesheet.feature.identity.repository.UserActionTokenRepository;
import com.lab.labtimesheet.platform.service.MailDeliveryService;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/** Test fixture for constructing the package-scoped identity service with guarded collaborators. */
public final class AccountServiceTestFactory {
    private AccountServiceTestFactory() {
    }

    /** Creates an identity service from explicit test doubles. */
    public static AccountService create(
            AppUserRepository users,
            UserActionTokenRepository tokens,
            MailDeliveryService mailDelivery,
            PasswordEncoder passwords,
            Clock clock,
            TransactionTemplate transactions,
            SessionRegistry sessions,
            String publicOrigin) {
        return new AccountService(
                users, tokens, mailDelivery, passwords, clock, transactions, sessions, publicOrigin);
    }
}
