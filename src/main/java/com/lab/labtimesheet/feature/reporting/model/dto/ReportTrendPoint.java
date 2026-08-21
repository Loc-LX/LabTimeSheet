package com.lab.labtimesheet.feature.reporting.model.dto;

/**
 * One presentation-safe point shared by a report's accessible table and optional chart enhancement.
 *
 * @param label ordered x-axis label, normally a policy-local date
 * @param value formatted value shown identically in the adjacent data table
 */
public record ReportTrendPoint(String label, String value) {
}
