package com.lab.labtimesheet.feature.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.account.model.TokenPurpose;
import com.lab.labtimesheet.feature.account.model.entity.AppUser;
import com.lab.labtimesheet.feature.account.model.entity.UserActionToken;
import com.lab.labtimesheet.feature.account.repository.AppUserRepository;
import com.lab.labtimesheet.feature.account.repository.InternProfileRepository;
import com.lab.labtimesheet.feature.account.repository.UserActionTokenRepository;
import com.lab.labtimesheet.feature.integration.service.MailDeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the account-first recovery order by driving both production recovery paths through test-only repository
 * barriers. The barrier is attached to a dynamic repository proxy, so production receives no test hook or extra
 * dependency while the two real service methods are coordinated. It observes whichever row the consumer locks
 * first; a token-first consumer is held until the issuer owns the account, forcing the PostgreSQL cycle that this
 * regression test is intended to reject.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AccountRecoveryLockOrderIntegrationTest {
    private static final Instant BASE_TIME = Instant.parse("2026-08-14T00:00:00Z");
    private static final String EMAIL = "lock-order@example.com";
    private static final String RAW_TOKEN = "lock-order-reset-token";

    @Autowired
    private AppUserRepository users;

    @Autowired
    private InternProfileRepository internProfiles;

    @Autowired
    private UserActionTokenRepository tokens;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private java.time.Clock clock;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private SessionRegistry sessions;

    @Test
    void concurrentAccountFirstConsumptionAndIssuanceComplete() throws Exception {
        seedRows();
        LockBarrier barrier = new LockBarrier();
        AppUserRepository gatedUsers = gated(users, AppUserRepository.class, barrier);
        UserActionTokenRepository gatedTokens = gated(tokens, UserActionTokenRepository.class, barrier);
        MailDeliveryService mail = mock(MailDeliveryService.class);
        when(mail.isAvailable()).thenReturn(true);
        doNothing().when(mail).send(anyString(), anyString(), anyString());
        AccountService recovery = new AccountService(
                gatedUsers, internProfiles, gatedTokens, mail, passwords, clock, transactions, sessions,
                "http://localhost");

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> consume = executor.submit(() -> {
                barrier.role(Role.CONSUMER);
                return transactions.execute(status -> recovery.resetPassword(RAW_TOKEN, "new secure password"));
            });
            Future<Boolean> issue = executor.submit(() -> {
                barrier.awaitConsumerFirstLock();
                barrier.role(Role.ISSUER);
                return recovery.requestPasswordReset(EMAIL);
            });

            assertThat(consume.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(issue.get(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(tokens.findAll().stream()
                .filter(token -> token.getPurpose() == TokenPurpose.PASSWORD_RESET)
                .filter(token -> token.getUsedAt() != null)
                .count()).isEqualTo(1);
        assertThat(tokens.findAll().stream()
                .filter(token -> token.getPurpose() == TokenPurpose.PASSWORD_RESET)
                .count()).isEqualTo(2);
    }

    private void seedRows() {
        transactions.executeWithoutResult(status -> {
            AppUser user = users.save(AppUser.bootstrapAdmin(
                    EMAIL, "Lock Order", "{noop}password", BASE_TIME));
            tokens.save(UserActionToken.passwordReset(
                    user.getId(), sha256(RAW_TOKEN), BASE_TIME.plus(Duration.ofHours(1)), BASE_TIME));
            tokens.flush();
        });
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("JDK SHA-256 is required", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T gated(T delegate, Class<T> contract, LockBarrier barrier) {
        InvocationHandler handler = (proxy, method, args) -> {
            boolean accountLock = method.getName().equals("findForUpdateById");
            boolean tokenLock = method.getName().equals("findForUpdateByHashAndPurpose");
            if (accountLock && barrier.role() == Role.ISSUER && barrier.firstLock() == FirstLock.ACCOUNT) {
                barrier.issuerAccountAttempted();
            }
            Object result;
            try {
                result = method.invoke(delegate, args);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
            if (barrier.role() == Role.CONSUMER) {
                if (accountLock && barrier.consumerAccountWasLocked()) {
                    barrier.awaitIssuerAccountAttempt();
                } else if (tokenLock && barrier.consumerTokenWasLocked()) {
                    barrier.awaitIssuerUserLock();
                }
            } else if (accountLock && barrier.firstLock() == FirstLock.TOKEN) {
                barrier.issuerUserLocked();
            }
            return result;
        };
        return (T) Proxy.newProxyInstance(
                contract.getClassLoader(), new Class<?>[] {contract}, handler);
    }

    private enum Role {
        CONSUMER,
        ISSUER
    }

    private enum FirstLock {
        ACCOUNT,
        TOKEN
    }

    private static final class LockBarrier {
        private final ThreadLocal<Role> role = new ThreadLocal<>();
        private final CountDownLatch consumerFirstLock = new CountDownLatch(1);
        private final CountDownLatch issuerAccountAttempted = new CountDownLatch(1);
        private final CountDownLatch issuerUserLocked = new CountDownLatch(1);
        private final AtomicBoolean firstLockObserved = new AtomicBoolean();
        private volatile FirstLock firstLock;

        void role(Role value) {
            role.set(value);
        }

        Role role() {
            return role.get();
        }

        void awaitConsumerFirstLock() {
            await(consumerFirstLock, "consumer first lock");
        }

        void issuerAccountAttempted() {
            issuerAccountAttempted.countDown();
        }

        FirstLock firstLock() {
            return firstLock;
        }

        boolean consumerAccountWasLocked() {
            if (firstLockObserved.compareAndSet(false, true)) {
                firstLock = FirstLock.ACCOUNT;
                consumerFirstLock.countDown();
                return true;
            }
            return false;
        }

        boolean consumerTokenWasLocked() {
            if (firstLockObserved.compareAndSet(false, true)) {
                firstLock = FirstLock.TOKEN;
                consumerFirstLock.countDown();
                return true;
            }
            return false;
        }

        void awaitIssuerAccountAttempt() {
            await(issuerAccountAttempted, "issuer account lock attempt");
        }

        void issuerUserLocked() {
            issuerUserLocked.countDown();
        }

        void awaitIssuerUserLock() {
            await(issuerUserLocked, "issuer account lock acquisition");
        }

        private static void await(CountDownLatch latch, String name) {
            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting for " + name);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for " + name, interrupted);
            }
        }
    }
}
