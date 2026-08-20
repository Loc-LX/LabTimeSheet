package com.lab.labtimesheet.feature.attendance.model.dto;

import java.time.LocalDate;

/**
 * Reserved-day count for one quota month across all pending and approved requests of an Intern.
 *
 * @param quotaMonth first day of the quota month
 * @param count number of frozen pending/approved days reserving that month
 */
public record MonthReservation(LocalDate quotaMonth, long count) {
}