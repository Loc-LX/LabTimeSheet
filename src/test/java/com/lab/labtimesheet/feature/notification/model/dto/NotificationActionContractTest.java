package com.lab.labtimesheet.feature.notification.model.dto;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Unit proof for the notification action route and secret-redaction boundary. */
class NotificationActionContractTest {
    @Test
    void rejectsAuthorityControlsEncodedSecretsAndRecoveryRoutes() {
        List<String> unsafeRoutes = List.of(
                "//evil.example/projects/7",
                "/\\evil.example/projects/7",
                "/projects/7/\u0007",
                "/projects/7?%74oken=raw-token",
                "/projects/7?%73ecret=raw-secret",
                "/projects/7?%70assword=raw-password",
                "/activate?code=raw-activation-token",
                "/reset-password?code=raw-reset-token",
                "https://lab.example/activate?token=raw-activation-token",
                "https://lab.example/reset-password?token=raw-reset-token");

        for (String unsafeRoute : unsafeRoutes) {
            try {
                new NotificationAction(unsafeRoute, false);
                throw new AssertionError("Accepted unsafe notification action: " + unsafeRoute);
            } catch (IllegalArgumentException expected) {
                // Expected route-boundary rejection.
            }
        }
    }

    @Test
    void acceptsOnlySafeRelativeApplicationRoutes() {
        new NotificationAction("/projects/7/invitation", false);
        new NotificationAction("/attendance/requests", false);
        new NotificationAction(null, false);
    }
}
