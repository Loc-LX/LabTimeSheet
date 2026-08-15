package com.lab.labtimesheet.feature.attendance.model;

/**
 * Date-specific eligibility facts supplied to check-in without exposing account or calendar persistence.
 * Approved leave means an approved request has a frozen allocation for the exact work date.
 *
 * @param activeIntern whether the account service considers the Intern active for the date
 * @param globalDayOff whether the authoritative local calendar exempts the date
 * @param approvedLeave whether a frozen approved leave allocation covers the date
 */
public record AttendanceDayContext(boolean activeIntern, boolean globalDayOff, boolean approvedLeave) {}
