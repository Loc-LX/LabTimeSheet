package com.lab.labtimesheet.feature.account.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.account.model.entity.AppUser;
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

/** PostgreSQL proof that Account exposes only active global Mentors as immutable scalar identities. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ActiveMentorIdentityIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-21T00:00:00Z");

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
    void returnsOnlyActiveMentorsInAscendingIdOrderThroughImmutableProjection() {
        AppUser admin = users.saveAndFlush(AppUser.bootstrapAdmin(
                "mentor-query-admin@example.com", "Query Admin", "encoded-password", NOW));
        AppUser firstActiveMentor = activeMentor(admin, "zulu-mentor@example.com", "Zulu Mentor");
        AppUser secondActiveMentor = activeMentor(admin, "alpha-mentor@example.com", "Alpha Mentor");
        pendingMentor(admin, "pending-mentor@example.com", "Pending Mentor");
        AppUser lockedMentor = activeMentor(admin, "locked-mentor@example.com", "Locked Mentor");
        lockedMentor.lock(NOW);
        users.saveAndFlush(lockedMentor);
        AppUser deactivatedMentor = activeMentor(admin, "deactivated-mentor@example.com", "Deactivated Mentor");
        deactivatedMentor.deactivate(NOW);
        users.saveAndFlush(deactivatedMentor);
        AppUser activeIntern = activeUser(admin, "active-intern@example.com", "Active Intern", GlobalRole.INTERN);

        LookupGuard guard = new LookupGuard();
        AccountService query = new AccountService(
                guarded(users, guard), internProfiles, tokens, mailDelivery, passwords, clock, transactions,
                sessions, "http://localhost");

        List<AccountIdentity> identities = query.activeGlobalMentorIdentities();

        assertThat(identities).containsExactly(
                new AccountIdentity(
                        firstActiveMentor.getId(), firstActiveMentor.getEmail(), firstActiveMentor.getDisplayName(),
                        GlobalRole.MENTOR, AccountStatus.ACTIVE),
                new AccountIdentity(
                        secondActiveMentor.getId(), secondActiveMentor.getEmail(), secondActiveMentor.getDisplayName(),
                        GlobalRole.MENTOR, AccountStatus.ACTIVE));
        assertThat(identities).extracting(AccountIdentity::id)
                .isSortedAccordingTo(Long::compareTo)
                .doesNotContain(admin.getId(), activeIntern.getId());
        assertThat(identities).allMatch(identity -> identity.getClass() == AccountIdentity.class);
        assertThat(AccountIdentity.class.isRecord()).isTrue();
        assertThat(AccountIdentity.class.getDeclaredFields())
                .allMatch(field -> Modifier.isPrivate(field.getModifiers()) && Modifier.isFinal(field.getModifiers()));
        assertThat(guard.projectionQueryCalled()).isTrue();
        assertThat(guard.entityQueryCalled()).isFalse();
    }

    private AppUser activeMentor(AppUser admin, String email, String displayName) {
        return activeUser(admin, email, displayName, GlobalRole.MENTOR);
    }

    private AppUser activeUser(AppUser admin, String email, String displayName, GlobalRole role) {
        AppUser user = AppUser.pending(email, displayName, role, admin, NOW);
        user.activate("encoded-password", NOW);
        return users.saveAndFlush(user);
    }

    private void pendingMentor(AppUser admin, String email, String displayName) {
        users.saveAndFlush(AppUser.pending(email, displayName, GlobalRole.MENTOR, admin, NOW));
    }

    private static AppUserRepository guarded(AppUserRepository delegate, LookupGuard guard) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getName().equals("findActiveMentorIdentities")) {
                guard.projectionQueryCalled.set(true);
            }
            if (method.getName().equals("findByNormalizedEmail")
                    || method.getName().equals("findById")
                    || method.getName().equals("findForUpdateById")) {
                guard.entityQueryCalled.set(true);
                throw new AssertionError("Mentor identity query hydrated or locked an AppUser entity");
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
        private final AtomicBoolean projectionQueryCalled = new AtomicBoolean();
        private final AtomicBoolean entityQueryCalled = new AtomicBoolean();

        boolean projectionQueryCalled() {
            return projectionQueryCalled.get();
        }

        boolean entityQueryCalled() {
            return entityQueryCalled.get();
        }
    }
}
