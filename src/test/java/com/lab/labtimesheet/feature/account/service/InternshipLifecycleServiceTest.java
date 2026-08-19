package com.lab.labtimesheet.feature.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.lab.labtimesheet.feature.account.model.InternshipStatus;
import com.lab.labtimesheet.feature.account.model.entity.AppUser;
import com.lab.labtimesheet.feature.account.model.entity.InternProfile;
import com.lab.labtimesheet.feature.account.repository.AppUserRepository;
import com.lab.labtimesheet.feature.account.repository.InternProfileRepository;
import com.lab.labtimesheet.feature.project.service.ProjectInternshipGuardService;
import com.lab.labtimesheet.feature.task.service.TaskQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.session.SessionRegistry;

@ExtendWith(MockitoExtension.class)
class InternshipLifecycleServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T03:00:00Z");

    @Mock
    private AppUserRepository users;

    @Mock
    private InternProfileRepository internProfiles;

    @Mock
    private ProjectInternshipGuardService projectGuards;

    @Mock
    private TaskQueryService taskQueries;

    @Mock
    private SessionRegistry sessionRegistry;

    @Mock
    private Clock clock;

    @InjectMocks
    private InternshipLifecycleService lifecycle;

    @BeforeEach
    void setUpClock() {
        given(clock.instant()).willReturn(NOW);
        given(clock.getZone()).willReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    @Test
    void requestTimeGuardActivatesDueActiveInternship() {
        var user = AppUser.pending(
                "intern@example.com", "Intern", com.lab.labtimesheet.feature.account.model.GlobalRole.INTERN,
                null, NOW);
        user.activate("encoded-password", NOW);
        var profile = InternProfile.notStarted(
                42L, "STU-42", LocalDate.of(2026, 8, 20), LocalDate.of(2026, 12, 31), NOW);
        given(users.findForUpdateById(42L)).willReturn(Optional.of(user));
        given(internProfiles.findForUpdateByUserId(42L)).willReturn(Optional.of(profile));

        lifecycle.ensureStartedIfDue(42L);

        assertThat(profile.getInternshipStatus()).isEqualTo(InternshipStatus.ACTIVE);
        verify(internProfiles).findForUpdateByUserId(42L);
    }

    @Test
    void requestTimeGuardLeavesFutureInternshipNotStarted() {
        var profile = InternProfile.notStarted(
                42L, "STU-42", LocalDate.of(2026, 8, 21), LocalDate.of(2026, 12, 31), NOW);
        var user = AppUser.pending(
                "intern@example.com", "Intern", com.lab.labtimesheet.feature.account.model.GlobalRole.INTERN,
                null, NOW);
        user.activate("encoded-password", NOW);
        given(users.findForUpdateById(42L)).willReturn(Optional.of(user));
        given(internProfiles.findForUpdateByUserId(42L)).willReturn(Optional.of(profile));

        lifecycle.ensureStartedIfDue(42L);

        assertThat(profile.getInternshipStatus()).isEqualTo(InternshipStatus.NOT_STARTED);
    }

}
