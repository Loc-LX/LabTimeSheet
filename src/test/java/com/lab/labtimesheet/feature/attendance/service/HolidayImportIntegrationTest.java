package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.account.service.BootstrapService;
import com.lab.labtimesheet.feature.attendance.exception.AttendanceException;
import com.lab.labtimesheet.feature.attendance.exception.HolidayCalendarException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.GlobalCalendarEvent;
import com.lab.labtimesheet.feature.attendance.model.dto.HolidayCandidate;
import com.lab.labtimesheet.feature.attendance.model.dto.HolidayImportSummary;
import com.lab.labtimesheet.feature.attendance.model.dto.HolidayPreviewRow;
import com.lab.labtimesheet.feature.attendance.model.dto.HolidaySelection;
import com.lab.labtimesheet.feature.attendance.repository.GlobalCalendarEventRepository;
import com.lab.labtimesheet.feature.integration.model.SecurityMode;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpDraft;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@Import({
    AttendancePersistenceIntegrationTest.IntegrationConfiguration.class,
    HolidayImportIntegrationTest.FakeHolidayApiConfiguration.class
})
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HolidayImportIntegrationTest {

    private static final HolidayCandidate NATIONAL_DAY = candidate(
            "11111111-1111-1111-1111-111111111111",
            "National Day",
            LocalDate.of(2026, 9, 2),
            true);
    private static final HolidayCandidate LUNAR_NEW_YEAR_EVE = candidate(
            "22222222-2222-2222-2222-222222222222",
            "Lunar New Year Eve",
            LocalDate.of(2026, 2, 16),
            LocalDate.of(2026, 2, 17),
            false);
    private static final HolidayCandidate CHRISTMAS = candidate(
            "33333333-3333-3333-3333-333333333333",
            "Christmas Day",
            LocalDate.of(2026, 12, 25),
            true);

    @Autowired
    private HolidayImportService holidays;

    @Autowired
    private CalendarApplicationService calendar;

    @Autowired
    private AttendanceApplicationService attendance;

    @Autowired
    private GlobalCalendarEventRepository events;

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
    private long internId;
    private AttendanceActor admin;

    @BeforeEach
    void seedUsers() {
        clock.set(Instant.parse("2026-08-14T00:00:00Z"));
        bootstrap.bootstrap("holiday-admin@example.test", "Admin", "correct horse battery staple");
        adminId = accounts.requireActiveAdminId("holiday-admin@example.test");
        long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                "mailpit",
                1025,
                SecurityMode.NONE,
                null,
                null,
                "holiday-admin@example.test",
                "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "holiday-admin@example.test");
        smtp.activate(draftId, adminId);
        mail.clear();

        var creation = accounts.create(new CreateAccountCommand(
                "holiday-intern@example.test",
                "Holiday Intern",
                GlobalRole.INTERN,
                "INT-HOLIDAY",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31)), adminId);
        assertThat(creation.deliverySucceeded()).isTrue();
        assertThat(accounts.activate(mail.onlyActivationToken(), "new secure intern password")).isTrue();
        accounts.activateInternship(creation.userId(), adminId);
        internId = creation.userId();
        admin = new AttendanceActor(adminId, AttendanceRole.ADMIN);
    }

    @Test
    void importStoresFullProvenanceAndRepeatedImportIsIdempotent() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));

        List<HolidayPreviewRow> preview = holidays.preview(admin, 2026);
        assertThat(preview).hasSize(3);
        assertThat(preview.get(0).candidate()).isEqualTo(NATIONAL_DAY);
        assertThat(preview.get(0).preselected()).isTrue();
        assertThat(preview.get(0).alreadyImported()).isFalse();
        assertThat(preview.get(1).candidate()).isEqualTo(LUNAR_NEW_YEAR_EVE);
        assertThat(preview.get(1).preselected()).isFalse();

        HolidayImportSummary first = holidays.importSelections(
                admin,
                2026,
                List.of(
                        new HolidaySelection(NATIONAL_DAY.uuid(), true),
                        new HolidaySelection(LUNAR_NEW_YEAR_EVE.uuid(), true),
                        new HolidaySelection(CHRISTMAS.uuid(), false)));
        assertThat(first.imported()).isEqualTo(3);
        assertThat(first.skipped()).isZero();

        HolidayImportSummary second = holidays.importSelections(
                admin,
                2026,
                List.of(
                        new HolidaySelection(NATIONAL_DAY.uuid(), false),
                        new HolidaySelection(CHRISTMAS.uuid(), false)));
        assertThat(second.imported()).isZero();
        assertThat(second.skipped()).isEqualTo(2);

        Map<String, GlobalCalendarEvent> byUuid = events.findAll().stream()
                .map(entity -> entity.toDomain())
                .collect(Collectors.toMap(GlobalCalendarEvent::sourceUuid, Function.identity()));

        assertThat(byUuid).containsOnlyKeys(
                NATIONAL_DAY.uuid(), LUNAR_NEW_YEAR_EVE.uuid(), CHRISTMAS.uuid());

        GlobalCalendarEvent nationalDay = byUuid.get(NATIONAL_DAY.uuid());
        assertThat(nationalDay.date()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(nationalDay.name()).isEqualTo("National Day");
        assertThat(nationalDay.source()).isEqualTo("HOLIDAY_API");
        assertThat(nationalDay.sourceUuid()).isEqualTo(NATIONAL_DAY.uuid());
        assertThat(nationalDay.actualDate()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(nationalDay.observedDate()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(nationalDay.publicHoliday()).isTrue();
        assertThat(nationalDay.dayOff()).isTrue();
        assertThat(nationalDay.importedAt()).isEqualTo(Instant.parse("2026-08-14T02:00:00Z"));

        GlobalCalendarEvent lunarNewYearEve = byUuid.get(LUNAR_NEW_YEAR_EVE.uuid());
        assertThat(lunarNewYearEve.date()).isEqualTo(LocalDate.of(2026, 2, 17));
        assertThat(lunarNewYearEve.actualDate()).isEqualTo(LocalDate.of(2026, 2, 16));
        assertThat(lunarNewYearEve.observedDate()).isEqualTo(LocalDate.of(2026, 2, 17));
        assertThat(lunarNewYearEve.publicHoliday()).isFalse();
        assertThat(lunarNewYearEve.dayOff()).isTrue();

        GlobalCalendarEvent christmas = byUuid.get(CHRISTMAS.uuid());
        assertThat(christmas.publicHoliday()).isTrue();
        assertThat(christmas.dayOff()).isFalse();

        assertThat(holidays.preview(admin, 2026))
                .extracting(HolidayPreviewRow::alreadyImported)
                .containsExactly(true, true, true);
    }

    @Test
    void importedDayOffSuppressesAttendanceOnTheObservedDate() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        holidays.importSelections(
                admin, 2026, List.of(new HolidaySelection(NATIONAL_DAY.uuid(), true)));

        assertThat(calendar.isGlobalDayOff(LocalDate.of(2026, 9, 2))).isTrue();

        clock.set(Instant.parse("2026-09-02T02:00:00Z"));
        assertThatThrownBy(() -> attendance.checkIn(internId))
                .isInstanceOf(AttendanceException.class);
    }

    private static HolidayCandidate candidate(String uuid, String name, LocalDate date, boolean publicHoliday) {
        return new HolidayCandidate(uuid, name, date, date, publicHoliday);
    }

    private static HolidayCandidate candidate(
            String uuid, String name, LocalDate actualDate, LocalDate observedDate, boolean publicHoliday) {
        return new HolidayCandidate(uuid, name, actualDate, observedDate, publicHoliday);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeHolidayApiConfiguration {

        @Bean
        @Primary
        HolidayApiClient holidayApiClient() {
            return year -> {
                if (year != 2026) {
                    throw new HolidayCalendarException("unsupported test year " + year);
                }
                return List.of(NATIONAL_DAY, LUNAR_NEW_YEAR_EVE, CHRISTMAS);
            };
        }
    }
}