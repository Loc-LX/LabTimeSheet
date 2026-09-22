package com.lab.labtimesheet.feature.identity.model.dto;

import java.time.LocalDate;

/**
 * Một dòng trong dropdown chọn Intern (Create Project, thêm member, đổi Leader).
 * Controller đưa list này vào Model; form.html hiển thị displayName/studentCode, submit userId.
 */
public record EligibleInternOption(
        long userId,
        String displayName,
        String studentCode,
        LocalDate internshipStart,
        LocalDate internshipEnd) {
}
