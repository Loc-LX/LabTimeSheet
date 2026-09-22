package com.lab.labtimesheet.feature.account.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicBoolean;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.account.repository.AppUserRepository;
import com.lab.labtimesheet.feature.account.repository.InternProfileRepository;
import com.lab.labtimesheet.feature.account.repository.UserActionTokenRepository;
import com.lab.labtimesheet.platform.service.MailDeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL proof that authenticated email routing uses a scalar Account query without hydrating AppUser. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AccountScalarLookupIntegrationTest {
    @Autowired
    private BootstrapService bootstrap;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private InternProfileRepository internProfiles;

    @Autowired
    private UserActionTokenRepository tokens;

    @Autowired
    private MailDeliveryService mailDelivery;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private Clock clock;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private SessionRegistry sessions;

    @Test
    void resolvesNormalizedEmailThroughScalarQueryWithoutHydratingAccountEntity() {
        bootstrap.bootstrap("scalar-routing@example.com", "Scalar Routing", "correct horse battery staple");
        LookupGuard guard = new LookupGuard();
        AccountService routing = new AccountService(
                guarded(users, guard), internProfiles, tokens, mailDelivery, passwords, clock, transactions,
                sessions, "http://localhost");

        long accountId = routing.requireAccountIdByEmail("  SCALAR-ROUTING@EXAMPLE.COM ");

        assertThat(accountId).isPositive();
        assertThat(guard.scalarQueryCalled()).isTrue();
        assertThat(guard.entityQueryCalled()).isFalse();
    }

    private static AppUserRepository guarded(AppUserRepository delegate, LookupGuard guard) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getName().equals("findAccountIdByNormalizedEmail")) {
                guard.scalarQueryCalled.set(true);
            }
            if (method.getName().equals("findByNormalizedEmail")
                    || method.getName().equals("findById")
                    || method.getName().equals("findForUpdateById")) {
                guard.entityQueryCalled.set(true);
                throw new AssertionError("Authenticated email routing hydrated an AppUser entity");
            }
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        };
        return (AppUserRepository) Proxy.newProxyInstance(
                AppUserRepository.class.getClassLoader(), new Class<?>[] {AppUserRepository.class}, handler);
    }

    private static final class LookupGuard {
        private final AtomicBoolean scalarQueryCalled = new AtomicBoolean();
        private final AtomicBoolean entityQueryCalled = new AtomicBoolean();

        boolean scalarQueryCalled() {
            return scalarQueryCalled.get();
        }

        boolean entityQueryCalled() {
            return entityQueryCalled.get();
        }
    }
}
