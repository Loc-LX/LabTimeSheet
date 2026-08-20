package com.lab.labtimesheet.feature.attendance.model.dto;

import java.time.LocalDate;

/**
 * One eligible workday materialized into a frozen leave allocation with its calendar month and quota snapshot.
 *
 * @param date exact frozen date
 * @param quotaMonth first day of the calendar month that owns the reservation
 * @param monthlyQuotaSnapshot monthly leave quota of the policy effective on the date at submission
 */
public record CountedLeaveDay(LocalDate date, LocalDate quotaMonth, int monthlyQuotaSnapshot) {
}