package com.lab.labtimesheet.feature.reporting.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.reporting.model.dto.AttendanceReportRow;
import com.lab.labtimesheet.feature.reporting.model.dto.AttendanceReportView;
import com.lab.labtimesheet.feature.reporting.model.dto.ReportTrendPoint;
import com.lab.labtimesheet.feature.reporting.service.AttendanceReportService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Production-shaped MVC RED for the authorized attendance report route. */
@WebMvcTest(AttendanceReportController.class)
class AttendanceReportControllerWebTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AttendanceReportService reports;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @Test
    void rendersAttendanceReportForAnAuthenticatedIntern() throws Exception {
        given(reports.build(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 8, 1)),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 8, 31))))
                .willReturn(new AttendanceReportView(
                        7L,
                        "Mai Intern",
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 8, 31),
                        true,
                        List.of(),
                        0,
                        0,
                        0,
                        "N/A",
                        List.of(),
                        List.of()));

        mvc.perform(get("/reports/attendance")
                        .with(user("intern@example.test").roles("INTERN"))
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(view().name("reports/attendance"));
    }

    @Test
    void exposesFiltersSummaryNaaAndEquivalentTrendDataInTheRenderedDataset() throws Exception {
        given(reports.build(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull()))
                .willReturn(new AttendanceReportView(
                        7L,
                        "Mai Intern",
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 8, 31),
                        true,
                        List.of(
                                new AttendanceReportRow(
                                        "14/08/2026", "09:05", "16:00", "08:30–15:30 (Asia/Ho_Chi_Minh)",
                                        "On time", "415 min", true),
                                new AttendanceReportRow(
                                        "13/08/2026", "08:30", "Missing", "08:30–15:30 (Asia/Ho_Chi_Minh)",
                                        "Late, Missing checkout", "N/A", false)),
                        2,
                        1,
                        1,
                        "50.0%",
                        List.of(),
                        List.of(new ReportTrendPoint("14/08/2026", "100.0%"),
                                new ReportTrendPoint("13/08/2026", "0.0%"))));

        mvc.perform(get("/reports/attendance")
                        .with(user("intern@example.test").roles("INTERN")))
                .andExpect(status().isOk())
                .andExpect(view().name("reports/attendance"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("id=\"attendance-from\"")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("50.0%")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Late, Missing checkout")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("data-report-chart")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("N/A")));
    }
}
