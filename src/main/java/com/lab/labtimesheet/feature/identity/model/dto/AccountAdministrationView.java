package com.lab.labtimesheet.feature.identity.model.dto;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.platform.model.GlobalRole;

/**
 * Non-secret Account-owned facts shown to an active Admin.
 *
 * @param id account identifier
 * @param email normalized login identity
 * @param displayName user-facing name
 * @param role immutable global role
 * @param accountStatus authentication lifecycle state
 */
public record AccountAdministrationView(
        long id,
        String email,
        String displayName,
        GlobalRole role,
        AccountStatus accountStatus) {
}
