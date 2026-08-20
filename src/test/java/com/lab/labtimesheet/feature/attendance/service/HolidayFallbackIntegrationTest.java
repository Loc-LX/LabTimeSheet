package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.account.service.BootstrapService;
import com.lab.labtimesheet.feature.attendance.exception.HolidayCalendarException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.integration.model.SecurityMode;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpDraft;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@Import(AttendancePersistenceIntegrationTest.IntegrationConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HolidayFallbackIntegrationTest {

    @Autowired
    private HolidayImportService holidays;

    @Autowired
    private CalendarApplicationService calendar;

    @Autowired
    private AttendancePersistenceIntegrationTest.MutableClock clock;

    @Autowired
    private BootstrapService bootstrap;

    @Autowired
    private AccountService accounts;

    @Autowired
    private SmtpConfigurationService smtp;

    @Autowired
    private AttendancePersistenceIntegrationTest.RecordingSmtpProbe mail;

    private long adminId;
    private AttendanceActor admin;

    @BeforeEach
    void seedAdmin() {
        clock.set(Instant.parse("2026-08-14T00:00:00Z"));
        bootstrap.bootstrap("fallback-admin@example.test", "Admin", "correct horse battery staple");
        adminId = accounts.requireActiveAdminId("fallback-admin@example.test");
        long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                "mailpit",
                1025,
                SecurityMode.NONE,
                null,
                null,
                "fallback-admin@example.test",
                "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "fallback-admin@example.test");
        smtp.activate(draftId, adminId);
        mail.clear();
        admin = new AttendanceActor(adminId, AttendanceRole.ADMIN);
    }

    @Test
    void unconfiguredApiReportsActionableFailureWhileManualFallbackStillWorks() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));

        assertThatThrownBy(() -> holidays.preview(admin, 2026))
                .isInstanceOf(HolidayCalendarException.class)
                .hasMessageContaining("not configured");

        LocalDate workDate = LocalDate.of(2026, 8, 20);
        calendar.createManual(admin, workDate, "Lab closure", true);
        assertThat(calendar.isGlobalDayOff(workDate)).isTrue();
    }
}