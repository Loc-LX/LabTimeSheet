package com.lab.labtimesheet.feature.account.model.dto;

import java.util.Locale;

import com.lab.labtimesheet.feature.account.model.GlobalRole;

/**
 * Normalized Admin account-directory filters.
 *
 * @param search optional case-insensitive text matched against display name, email, or Student Code
 * @param role optional immutable global role filter
 */
public record AccountDirectoryFilter(String search, GlobalRole role) {
    /** Normalizes the user-entered search text without changing the immutable role filter. */
    public AccountDirectoryFilter {
        search = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        if (search.length() > 120) {
            throw new IllegalArgumentException("Account search is too long");
        }
    }
}
