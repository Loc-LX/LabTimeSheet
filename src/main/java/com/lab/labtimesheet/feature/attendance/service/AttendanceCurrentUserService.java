package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import java.security.Principal;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Converts Spring Security principals into active Attendance authorization contexts through AccountService DTOs.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class AttendanceCurrentUserService {

    private final AccountService accounts;

    /**
     * Resolves the authenticated email through the Account feature and rejects missing or inactive identities.
     *
     * @param principal authenticated server principal
     * @return Attendance actor containing only the user ID and global role needed by this feature
     */
    public AttendanceActor actor(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new AccessDeniedException("Authentication is required");
        }
        AccountIdentity identity;
        try {
            identity = accounts.requireIdentityByEmail(principal.getName());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException(
                    "No active application user matches the authenticated identity", exception);
        }
        if (identity.status() != AccountStatus.ACTIVE) {
            throw new AccessDeniedException(
                    "No active application user matches the authenticated identity");
        }
        return new AttendanceActor(identity.id(), AttendanceRole.valueOf(identity.role().name()));
    }
}
