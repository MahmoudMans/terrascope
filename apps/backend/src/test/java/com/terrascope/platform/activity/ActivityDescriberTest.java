package com.terrascope.platform.activity;

import com.terrascope.platform.activity.ActivityDescriber.Description;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityDescriberTest {

    private static Description describe(
            String method,
            String route
    ) {
        return ActivityDescriber.describe(method, "/api/v1/" + route);
    }

    @Test
    void describesCreatingARecord() {
        Description created = describe("POST", "incidents");

        assertEquals("Created incident", created.action());
        assertEquals("incident", created.entityType());
        assertNull(created.idVariable());

        assertEquals("Added alert rule", describe("POST", "alerts/rules").action());
        assertEquals("Added watchlist entry", describe("POST", "watchlist").action());
    }

    @Test
    void describesChangesToASingleField() {
        Description status = describe("PATCH", "incidents/{id}/status");

        assertEquals("Updated incident status", status.action());
        assertEquals("incident", status.entityType());
        assertEquals("id", status.idVariable());

        assertEquals(
                "Updated damage assessment recovery ETA",
                describe("PATCH", "incidents/{incidentId}/damage/{assessmentId}/recovery-eta").action()
        );

        assertEquals(
                "Updated action due date",
                describe("PATCH", "incidents/{incidentId}/actions/{actionId}/due-at").action()
        );
    }

    @Test
    void describesRecordsThatBelongToAnIncident() {
        Description note = describe("POST", "incidents/{id}/notes");

        assertEquals("Added note to incident", note.action());
        assertEquals("id", note.idVariable());

        Description removed = describe("DELETE", "incidents/{incidentId}/actions/{actionId}");

        assertEquals("Removed action from incident", removed.action());
        assertEquals("incident", removed.entityType());
        assertEquals("incidentId", removed.idVariable());

        assertEquals(
                "Added evacuation zone to incident",
                describe("POST", "incidents/{incidentId}/evacuation/zones").action()
        );
    }

    @Test
    void describesNamedActions() {
        assertEquals(
                "Verified damage assessment",
                describe("POST", "incidents/{incidentId}/damage/{assessmentId}/verify").action()
        );

        assertEquals(
                "Cancelled resource",
                describe("POST", "incidents/{incidentId}/resources/{resourceId}/cancel").action()
        );

        assertEquals(
                "Completed resilience objective",
                describe("POST", "resilience-portfolio/objectives/{objectiveId}/complete").action()
        );

        assertEquals(
                "Retired trust anchor",
                describe("POST", "resilience-portfolio/governance-closure/trust-anchors/{keyId}/retire").action()
        );

        Description run = describe("POST", "simulations/run");

        assertEquals("Ran simulation", run.action());
        assertEquals("simulation", run.entityType());
    }

    @Test
    void usesFriendlyWordingForSignInAndUserAdmin() {
        assertEquals("Signed in", describe("POST", "auth/login").action());
        assertEquals("session", describe("POST", "auth/login").entityType());
        assertEquals("Changed their own password", describe("POST", "auth/password").action());
        assertEquals("Created a user account", describe("POST", "admin/users").action());
        assertEquals("Updated a user account", describe("PATCH", "admin/users/{id}").action());
        assertEquals("Reset a user's password", describe("POST", "admin/users/{id}/password").action());
        assertEquals("Turned an alert rule on or off", describe("PATCH", "alerts/rules/{id}/enabled").action());
    }

    @Test
    void dropsInternalNamespacesFromWording() {
        assertEquals(
                "Added closure certificate",
                describe("POST", "resilience-portfolio/governance-closure/certificates").action()
        );

        assertEquals(
                "Added portfolio snapshot",
                describe("POST", "resilience-portfolio/snapshots").action()
        );

        assertEquals(
                "Added benefit realization snapshot",
                describe("POST", "resilience-portfolio/benefit-realization/snapshots").action()
        );
    }

    @Test
    void keepsWordsThatMerelyEndInS() {
        assertEquals(
                "Updated alert event status",
                describe("PATCH", "alerts/events/{id}/status").action()
        );

        assertEquals(
                "Added after action review to incident",
                describe("POST", "incidents/{incidentId}/after-action-reviews").action()
        );

        assertEquals(
                "Updated lifeline asset status",
                describe("PATCH", "incidents/{incidentId}/lifelines/{assetId}/status").action()
        );
    }

    @Test
    void leavesWordsEndingInSsUsOrIsAlone() {
        assertEquals("Added campus", describe("POST", "campus").action());
        assertEquals("Added analysis", describe("POST", "analysis").action());
        assertEquals("Added address", describe("POST", "address").action());
        assertEquals("Added process", describe("POST", "process").action());
        assertEquals("Added policy", describe("POST", "policies").action());
        assertEquals("Added bus", describe("POST", "bus").action());
    }

    @Test
    void readsTheIdentifierVariableFromComplexPatterns() {
        assertEquals("id", describe("PATCH", "alerts/events/{id:[0-9a-f-]+}/status").idVariable());
        assertNull(describe("POST", "alerts/rules").idVariable());
    }

    @Test
    void fallsBackGracefullyForUnknownShapes() {
        Description unknown = ActivityDescriber.describe("POST", "/somewhere/else");

        assertEquals("Changed data", unknown.action());
        assertEquals("record", unknown.entityType());

        assertEquals("Changed data", ActivityDescriber.describe(null, "/api/v1/incidents").action());
        assertEquals("Changed data", ActivityDescriber.describe("POST", null).action());
        assertEquals("Changed data", ActivityDescriber.describe("POST", "/api/v1/").action());
        assertEquals("Changed data", ActivityDescriber.describe("POST", "/api/v1/{id}").action());
    }

    @Test
    void alwaysProducesReadableText() {
        String[] routes = {
                "incidents",
                "incidents/{id}",
                "incidents/{id}/status",
                "alerts/rules/{id}",
                "resilience-portfolio/governance-closure/trust-lineage/offline-verification/imports",
                "a/{b}/c/{d}/e/{f}/g"
        };

        for (String route : routes) {
            for (String method : new String[] {"POST", "PUT", "PATCH", "DELETE", "TRACE"}) {
                Description description = describe(method, route);

                assertFalse(description.action().isBlank());
                assertFalse(description.entityType().isBlank());
                assertTrue(description.action().length() < 120);
            }
        }
    }
}
