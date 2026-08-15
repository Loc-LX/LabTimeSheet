package com.lab.labtimesheet.feature.attendance.model;

/**
 * Independent attendance violations for a row. Late may coexist with early departure or missing checkout;
 * missing checkout and early departure are mutually exclusive because the latter requires an effective checkout.
 *
 * @param late check-in occurred strictly after the inclusive grace boundary
 * @param earlyDeparture effective checkout occurred before scheduled end
 * @param missingCheckout no effective checkout existed after the inclusive checkout cutoff passed
 */
public record AttendanceViolations(boolean late, boolean earlyDeparture, boolean missingCheckout) {}
