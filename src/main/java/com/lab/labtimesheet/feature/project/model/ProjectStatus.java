package com.lab.labtimesheet.feature.project.model;

/** Project aggregate lifecycle; completion is terminal and read-only. */
// Các trạng thái vòng đời của Project. ProjectEntity chỉ cho phép chuyển trạng thái theo business rule.
public enum ProjectStatus {
    /** Preparation state in which membership, leadership, and Task definitions may change. */
    PLANNED,
    /** Execution state in which Project work may proceed. */
    ACTIVE,
    /** Terminal read-only state retaining historical membership and leadership visibility. */
    COMPLETED
}
