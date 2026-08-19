package com.lab.labtimesheet.feature.account.model.entity;

/**
 * Account fields an Admin may change through the account-edit flow. Enum names are persisted as the
 * {@code account_admin_edit_events.field_name} column and are enforced by the table check constraint.
 */
public enum AccountAdminEditField {
    EMAIL,
    DISPLAY_NAME,
    STUDENT_CODE,
    INTERNSHIP_START,
    INTERNSHIP_END
}