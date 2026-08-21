package com.lab.labtimesheet.feature.notification.model.dto;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Immutable safe application action for one notification event.
 *
 * <p>Only relative application routes are retained. URI authority, scheme, control, backslash,
 * encoded-sensitive-query, activation, and password-reset forms are rejected so bearer tokens and
 * credentials cannot enter the ordinary notification outbox. Ordinary-email designation belongs
 * exclusively to the event family, not to this caller-controlled action value.
 */
public record NotificationAction(String actionUrl, boolean selfTask) {
    /** Validates and canonicalizes the safe route boundary while retaining absent actions as {@code null}. */
    public NotificationAction {
        actionUrl = normalizeActionUrl(actionUrl);
    }

    private static String normalizeActionUrl(String value) {
        if (value == null) {
            return null;
        }
        rejectControlsAndBackslashes(value);
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        validateRelativeUri(normalized);

        String decoded = decode(normalized);
        if (!decoded.equals(normalized)) {
            rejectControlsAndBackslashes(decoded);
            validateRelativeUri(decoded);
        }

        URI uri = parse(normalized);
        String decodedPath = decode(uri.getRawPath());
        String decodedQuery = uri.getRawQuery() == null ? null : decode(uri.getRawQuery());
        if (isRecoveryPath(decodedPath) || containsSensitiveQueryName(decodedQuery)) {
            throw unsafeRoute();
        }
        return normalized;
    }

    private static void rejectControlsAndBackslashes(String value) {
        if (value.indexOf('\\') >= 0 || value.codePoints().anyMatch(Character::isISOControl)) {
            throw unsafeRoute();
        }
    }

    private static void validateRelativeUri(String value) {
        URI uri = parse(value);
        if (!value.startsWith("/") || value.startsWith("//") || uri.isAbsolute()
                || uri.getRawAuthority() != null || uri.getHost() != null || uri.getUserInfo() != null) {
            throw unsafeRoute();
        }
    }

    private static URI parse(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException invalidUri) {
            throw unsafeRoute();
        }
    }

    private static String decode(String value) {
        if (value == null) {
            return "";
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformedEncoding) {
            throw unsafeRoute();
        }
    }

    private static boolean isRecoveryPath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.equals("/activate") || lower.startsWith("/activate/")
                || lower.equals("/reset-password") || lower.startsWith("/reset-password/");
    }

    private static boolean containsSensitiveQueryName(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        for (String parameter : query.split("&", -1)) {
            String name = parameter;
            int equals = parameter.indexOf('=');
            if (equals >= 0) {
                name = parameter.substring(0, equals);
            }
            String lower = name.trim().toLowerCase(Locale.ROOT);
            if (lower.equals("token") || lower.equals("secret") || lower.equals("password")
                    || lower.equals("code")) {
                return true;
            }
        }
        return false;
    }

    private static IllegalArgumentException unsafeRoute() {
        return new IllegalArgumentException("Notification action must be a safe relative route");
    }
}
