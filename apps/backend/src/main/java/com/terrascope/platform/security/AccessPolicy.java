package com.terrascope.platform.security;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class AccessPolicy {

    private static final String GOVERNANCE_ROOT =
            "/api/v1/resilience-portfolio/governance-closure";

    private static final String ACTIVITY_ROOT =
            "/api/v1/activity";

    private static final Set<String> PUBLIC_ROUTES =
            Set.of(
                    "POST /api/v1/auth/login",
                    "GET /api/v1/auth/config",
                    "GET /api/v1/status"
            );

    private static final Set<String> VIEWER_POST_ROUTES =
            Set.of(
                    "POST /api/v1/simulations/run",
                    "POST /api/v1/regional-intelligence/analyze",
                    "POST /api/v1/alerts/evaluate",
                    "POST /api/v1/auth/password"
            );

    private AccessPolicy() {
    }

    public static Optional<Role> requiredRole(
            String method,
            String pattern
    ) {
        String verb =
                method == null
                        ? ""
                        : method.toUpperCase(Locale.ROOT);

        String path =
                pattern == null
                        ? "/**"
                        : pattern;

        String route =
                verb + " " + path;

        if (PUBLIC_ROUTES.contains(route)) {
            return Optional.empty();
        }

        if (
                path.equals("/api/v1/admin")
                || path.startsWith("/api/v1/admin/")
        ) {
            return Optional.of(Role.ADMIN);
        }

        if (
                path.equals(ACTIVITY_ROOT)
                && (
                        verb.equals("GET")
                        || verb.equals("HEAD")
                )
        ) {
            return Optional.of(Role.OPERATOR);
        }

        if (
                verb.equals("GET")
                || verb.equals("HEAD")
        ) {
            return Optional.of(Role.VIEWER);
        }

        if (
                VIEWER_POST_ROUTES.contains(route)
                || (
                        verb.equals("POST")
                        && path.endsWith("/verify")
                )
        ) {
            return Optional.of(Role.VIEWER);
        }

        if (
                path.equals(GOVERNANCE_ROOT)
                || path.startsWith(GOVERNANCE_ROOT + "/")
        ) {
            return Optional.of(Role.ADMIN);
        }

        return Optional.of(Role.OPERATOR);
    }
}
