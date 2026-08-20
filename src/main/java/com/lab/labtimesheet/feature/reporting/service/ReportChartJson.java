package com.lab.labtimesheet.feature.reporting.service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Minimal, dependency-free JSON builder for the fixed report chart shapes.
 *
 * <p>The report pages only ever emit a line or bar chart of labels and numeric values, so a small
 * deterministic builder avoids adding a JSON dependency to the application (TST-010).
 */
public final class ReportChartJson {

    private ReportChartJson() {}

    /**
     * Builds a line-chart configuration matching the {@code app-charts.js} contract.
     *
     * @param label dataset label shown in the legend
     * @param labels category labels
     * @param values numeric values parallel to the labels
     * @return JSON serialized configuration
     */
    public static String line(String label, List<String> labels, List<? extends Number> values) {
        return chart("line", label, labels, values);
    }

    /**
     * Builds a bar-chart configuration matching the {@code app-charts.js} contract.
     *
     * @param label dataset label shown in the legend
     * @param labels category labels
     * @param values numeric values parallel to the labels
     * @return JSON serialized configuration
     */
    public static String bar(String label, List<String> labels, List<? extends Number> values) {
        return chart("bar", label, labels, values);
    }

    private static String chart(String type, String label, List<String> labels, List<? extends Number> values) {
        return "{"
                + "\"type\":\"" + type + "\","
                + "\"data\":{"
                + "\"labels\":" + stringArray(labels) + ","
                + "\"datasets\":[{\"label\":" + quote(label) + ",\"data\":" + numberArray(values) + "}]"
                + "}}";
    }

    private static String stringArray(List<String> values) {
        return values.stream().map(ReportChartJson::quote).collect(Collectors.joining(",", "[", "]"));
    }

    private static String numberArray(List<? extends Number> values) {
        return values.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}