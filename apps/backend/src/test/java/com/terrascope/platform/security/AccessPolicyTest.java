package com.terrascope.platform.security;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessPolicyTest {

    private static Optional<Role> need(
            String method,
            String pattern
    ) {
        return AccessPolicy.requiredRole(method, pattern);
    }

    @Test
    void leavesSignInAndStatusOpen() {
        assertTrue(need("POST", "/api/v1/auth/login").isEmpty());
        assertTrue(need("GET", "/api/v1/auth/config").isEmpty());
        assertTrue(need("GET", "/api/v1/status").isEmpty());
    }

    @Test
    void letsAnySignedInUserReadAndCheckThemselves() {
        assertEquals(Optional.of(Role.VIEWER), need("GET", "/api/v1/auth/me"));
        assertEquals(Optional.of(Role.VIEWER), need("GET", "/api/v1/incidents"));
        assertEquals(Optional.of(Role.VIEWER), need("HEAD", "/api/v1/incidents"));
        assertEquals(Optional.of(Role.VIEWER), need("GET", "/api/v1/stream"));
        assertEquals(Optional.of(Role.VIEWER), need("GET", "/api/v1/automation/status"));
        assertEquals(Optional.of(Role.VIEWER), need("POST", "/api/v1/auth/password"));
    }

    @Test
    void letsViewersRunAnalysesAndVerifications() {
        assertEquals(Optional.of(Role.VIEWER), need("POST", "/api/v1/simulations/run"));
        assertEquals(Optional.of(Role.VIEWER), need("POST", "/api/v1/regional-intelligence/analyze"));
        assertEquals(Optional.of(Role.VIEWER), need("POST", "/api/v1/alerts/evaluate"));

        assertEquals(
                Optional.of(Role.VIEWER),
                need(
                        "POST",
                        "/api/v1/resilience-portfolio/governance-closure/trust-lineage/pinning/verify"
                )
        );
    }

    @Test
    void keepsTheActivityLogForOperatorsAndAbove() {
        assertEquals(Optional.of(Role.OPERATOR), need("GET", "/api/v1/activity"));
        assertEquals(Optional.of(Role.OPERATOR), need("HEAD", "/api/v1/activity"));
        assertEquals(Optional.of(Role.OPERATOR), need("POST", "/api/v1/activity"));
        assertEquals(Optional.of(Role.VIEWER), need("GET", "/api/v1/activity-feed"));
    }

    @Test
    void requiresAnOperatorToChangeOperationalRecords() {
        assertEquals(Optional.of(Role.OPERATOR), need("POST", "/api/v1/incidents"));
        assertEquals(Optional.of(Role.OPERATOR), need("PATCH", "/api/v1/incidents/{id}/status"));
        assertEquals(Optional.of(Role.OPERATOR), need("DELETE", "/api/v1/incidents/{incidentId}/actions/{actionId}"));
        assertEquals(Optional.of(Role.OPERATOR), need("POST", "/api/v1/alerts/rules"));
        assertEquals(Optional.of(Role.OPERATOR), need("POST", "/api/v1/watchlist"));
        assertEquals(Optional.of(Role.OPERATOR), need("POST", "/api/v1/resilience-portfolio/snapshots"));
    }

    @Test
    void reservesGovernanceChangesAndUserAdminForAdministrators() {
        assertEquals(
                Optional.of(Role.ADMIN),
                need("POST", "/api/v1/resilience-portfolio/governance-closure/certificates")
        );

        assertEquals(
                Optional.of(Role.ADMIN),
                need("POST", "/api/v1/resilience-portfolio/governance-closure/trust-anchors/{keyId}/retire")
        );

        assertEquals(
                Optional.of(Role.VIEWER),
                need("GET", "/api/v1/resilience-portfolio/governance-closure/certificates")
        );

        assertEquals(Optional.of(Role.ADMIN), need("GET", "/api/v1/admin/users"));
        assertEquals(Optional.of(Role.ADMIN), need("PATCH", "/api/v1/admin/users/{id}"));
        assertEquals(Optional.of(Role.ADMIN), need("POST", "/api/v1/admin/users"));
    }

    @Test
    void failsClosedForUnknownInput() {
        assertEquals(Optional.of(Role.VIEWER), need("GET", null));
        assertEquals(Optional.of(Role.OPERATOR), need("POST", null));
        assertEquals(Optional.of(Role.OPERATOR), need(null, "/api/v1/incidents"));
        assertEquals(Optional.of(Role.OPERATOR), need("PUT", "/api/v1/incidents"));
        assertEquals(Optional.of(Role.OPERATOR), need("DELETE", "/**"));
        assertEquals(Optional.of(Role.ADMIN), need("GET", "/api/v1/admin"));
    }

    @Test
    void matchesMethodsCaseInsensitively() {
        assertEquals(Optional.of(Role.VIEWER), need("get", "/api/v1/incidents"));
        assertTrue(need("post", "/api/v1/auth/login").isEmpty());
    }

    @Test
    void ranksRolesInOrder() {
        assertTrue(Role.ADMIN.allows(Role.OPERATOR));
        assertTrue(Role.ADMIN.allows(Role.VIEWER));
        assertTrue(Role.OPERATOR.allows(Role.VIEWER));
        assertTrue(Role.OPERATOR.allows(Role.OPERATOR));
        assertFalse(Role.OPERATOR.allows(Role.ADMIN));
        assertFalse(Role.VIEWER.allows(Role.OPERATOR));
    }

    @Test
    void parsesRolesLeniently() {
        assertEquals(Optional.of(Role.ADMIN), Role.parse(" admin "));
        assertEquals(Optional.of(Role.VIEWER), Role.parse("Viewer"));
        assertTrue(Role.parse("root").isEmpty());
        assertTrue(Role.parse(null).isEmpty());
        assertTrue(Role.parse("").isEmpty());
    }
}
