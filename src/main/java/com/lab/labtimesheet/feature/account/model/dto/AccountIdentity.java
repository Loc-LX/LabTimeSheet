package com.lab.labtimesheet.feature.account.model.dto;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;

/**
 * Non-secret account identity exposed to other features without leaking JPA entities.
 *
 * @param id account identifier
 * @param email normalized email address
 * @param displayName user-facing name
 * @param role immutable global role
 * @param status current authentication lifecycle state
 */
public record AccountIdentity(
        long id,
        String email,
        String displayName,
        GlobalRole role,
        AccountStatus status) {
}
