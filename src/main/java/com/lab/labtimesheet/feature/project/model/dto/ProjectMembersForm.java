package com.lab.labtimesheet.feature.project.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;

/**
 * Browser form for one atomic owning-Mentor direct-add selection.
 *
 * @param internUserIds distinct positive Intern account identifiers selected in the picker
 */
public record ProjectMembersForm(@NotEmpty List<@Positive Long> internUserIds) {

    /** Creates an empty form for the initial membership page. */
    public ProjectMembersForm() {
        this(List.of());
    }
}
