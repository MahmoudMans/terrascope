package com.terrascope.platform.activity;

import com.terrascope.platform.security.AuthInterceptor;
import com.terrascope.platform.security.AuthenticatedUser;
import com.terrascope.platform.security.Role;
import com.terrascope.platform.security.SecuritySettings;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityInterceptorTest {

    private static final String SECRET =
            "0123456789abcdef0123456789abcdef0123";

    private static final AuthenticatedUser MAYA =
            new AuthenticatedUser(
                    UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    "maya",
                    "Maya Chen",
                    Role.OPERATOR
            );

    private final InMemoryActivityStore store =
            new InMemoryActivityStore();

    private final ActivityInterceptor secured =
            new ActivityInterceptor(
                    new SecuritySettings(true, SECRET, "PT8H", "", ""),
                    store
            );

    private final ActivityInterceptor open =
            new ActivityInterceptor(
                    new SecuritySettings(false, "", "PT8H", "", ""),
                    store
            );

    @Test
    void recordsAnOperatorChangeWithWhoAndWhat() {
        Exchange exchange =
                new Exchange("PATCH", "/api/v1/incidents/{id}/status", 200)
                        .signedInAs(MAYA)
                        .variable("id", "3f2a91bc-0000-4000-8000-000000000001");

        secured.afterCompletion(exchange.request(), exchange.response(), new Object(), null);

        assertEquals(1, store.entries.size());

        ActivityEntry entry =
                store.entries.get(0);

        assertEquals("maya", entry.actorUsername());
        assertEquals("Maya Chen", entry.actorDisplayName());
        assertEquals(Role.OPERATOR, entry.actorRole());
        assertEquals(MAYA.id(), entry.actorId());
        assertEquals("PATCH", entry.method());
        assertEquals("/api/v1/incidents/{id}/status", entry.route());
        assertEquals("Updated incident status", entry.action());
        assertEquals("incident", entry.entityType());
        assertEquals("3f2a91bc-0000-4000-8000-000000000001", entry.entityId());
        assertEquals(200, entry.statusCode());
        assertNotNull(entry.occurredAt());
        assertNotNull(entry.id());
    }

    @Test
    void recordsSignInAndPasswordChanges() {
        secured.afterCompletion(
                new Exchange("POST", "/api/v1/auth/login", 200).signedInAs(MAYA).request(),
                new Exchange("POST", "/api/v1/auth/login", 200).response(),
                new Object(),
                null
        );

        Exchange password =
                new Exchange("POST", "/api/v1/auth/password", 200).signedInAs(MAYA);

        secured.afterCompletion(password.request(), password.response(), new Object(), null);

        assertEquals(2, store.entries.size());
        assertEquals("Signed in", store.entries.get(0).action());
        assertEquals("Changed their own password", store.entries.get(1).action());
    }

    @Test
    void ignoresReadsAndAnalysesThatChangeNothing() {
        record(secured, "GET", "/api/v1/incidents", 200);
        record(secured, "HEAD", "/api/v1/incidents", 200);
        record(secured, "OPTIONS", "/api/v1/incidents", 200);
        record(secured, "POST", "/api/v1/simulations/run", 200);
        record(secured, "POST", "/api/v1/regional-intelligence/analyze", 200);
        record(secured, "POST", "/api/v1/alerts/evaluate", 200);
        record(secured, "POST", "/api/v1/resilience-portfolio/governance-closure/trust-lineage/pinning/verify", 200);

        assertTrue(store.entries.isEmpty());
    }

    @Test
    void ignoresRequestsThatDidNotSucceed() {
        record(secured, "POST", "/api/v1/incidents", 400);
        record(secured, "POST", "/api/v1/incidents", 403);
        record(secured, "POST", "/api/v1/incidents", 500);
        record(secured, "POST", "/api/v1/incidents", 302);

        Exchange crashed =
                new Exchange("POST", "/api/v1/incidents", 200).signedInAs(MAYA);

        secured.afterCompletion(crashed.request(), crashed.response(), new Object(), new IllegalStateException("boom"));

        assertTrue(store.entries.isEmpty());

        record(secured, "POST", "/api/v1/incidents", 201);
        record(secured, "DELETE", "/api/v1/watchlist/{id}", 204);

        assertEquals(2, store.entries.size());
    }

    @Test
    void recordsOnlySuccessfulStatusCodesAtTheBoundaries() {
        record(secured, "POST", "/api/v1/incidents", 199);
        record(secured, "POST", "/api/v1/incidents", 300);

        assertTrue(store.entries.isEmpty());

        record(secured, "POST", "/api/v1/incidents", 200);
        record(secured, "POST", "/api/v1/incidents", 299);

        assertEquals(2, store.entries.size());
    }

    @Test
    void ignoresNestedDispatchesAndUnknownRoutes() {
        Exchange async =
                new Exchange("POST", "/api/v1/incidents", 200).signedInAs(MAYA);

        async.dispatcherType = DispatcherType.ASYNC;

        secured.afterCompletion(async.request(), async.response(), new Object(), null);

        Exchange unrouted =
                new Exchange("POST", null, 200).signedInAs(MAYA);

        secured.afterCompletion(unrouted.request(), unrouted.response(), new Object(), null);

        assertTrue(store.entries.isEmpty());
    }

    @Test
    void skipsUnidentifiedRequestsWhenSignInIsRequired() {
        Exchange anonymous =
                new Exchange("POST", "/api/v1/incidents", 200);

        secured.afterCompletion(anonymous.request(), anonymous.response(), new Object(), null);

        assertTrue(store.entries.isEmpty());
    }

    @Test
    void attributesChangesToTheLocalSessionWhenSignInIsOff() {
        Exchange exchange =
                new Exchange("POST", "/api/v1/incidents/{id}/notes", 200)
                        .variable("id", "abc");

        open.afterCompletion(exchange.request(), exchange.response(), new Object(), null);

        assertEquals(1, store.entries.size());

        ActivityEntry entry =
                store.entries.get(0);

        assertEquals("local", entry.actorUsername());
        assertEquals("Local session", entry.actorDisplayName());
        assertNull(entry.actorRole());
        assertNull(entry.actorId());
        assertEquals("Added note to incident", entry.action());
        assertEquals("abc", entry.entityId());
    }

    @Test
    void cleansAndLimitsTheRecordedIdentifier() {
        Exchange noisy =
                new Exchange("DELETE", "/api/v1/watchlist/{id}", 204)
                        .signedInAs(MAYA)
                        .variable("id", "  ab\ncd\t ");

        secured.afterCompletion(noisy.request(), noisy.response(), new Object(), null);

        Exchange long_ =
                new Exchange("DELETE", "/api/v1/watchlist/{id}", 204)
                        .signedInAs(MAYA)
                        .variable("id", "x".repeat(500));

        secured.afterCompletion(long_.request(), long_.response(), new Object(), null);

        Exchange blank =
                new Exchange("DELETE", "/api/v1/watchlist/{id}", 204)
                        .signedInAs(MAYA)
                        .variable("id", "   ");

        secured.afterCompletion(blank.request(), blank.response(), new Object(), null);

        assertEquals("abcd", store.entries.get(0).entityId());
        assertEquals(120, store.entries.get(1).entityId().length());
        assertNull(store.entries.get(2).entityId());
    }

    @Test
    void neverBreaksTheRequestWhenTheStoreFails() {
        store.failing = true;

        Exchange exchange =
                new Exchange("POST", "/api/v1/incidents", 201).signedInAs(MAYA);

        secured.afterCompletion(exchange.request(), exchange.response(), new Object(), null);

        assertTrue(store.entries.isEmpty());
    }

    @Test
    void decidesWhichRoutesAreWorthRecording() {
        assertTrue(ActivityInterceptor.isRecordable("POST", "/api/v1/incidents"));
        assertTrue(ActivityInterceptor.isRecordable("post", "/api/v1/incidents"));
        assertTrue(ActivityInterceptor.isRecordable("PATCH", "/api/v1/incidents/{id}/owner"));
        assertTrue(ActivityInterceptor.isRecordable("POST", "/api/v1/resilience-portfolio/governance-closure/certificates"));
        assertTrue(ActivityInterceptor.isRecordable("POST", "/api/v1/admin/users"));
        assertTrue(ActivityInterceptor.isRecordable("POST", "/api/v1/auth/login"));
        assertTrue(ActivityInterceptor.isRecordable("POST", "/api/v1/auth/password"));

        assertFalse(ActivityInterceptor.isRecordable("GET", "/api/v1/incidents"));
        assertFalse(ActivityInterceptor.isRecordable("GET", "/api/v1/admin/users"));
        assertFalse(ActivityInterceptor.isRecordable("POST", "/api/v1/simulations/run"));
        assertFalse(ActivityInterceptor.isRecordable("POST", "/api/v1/alerts/evaluate"));
        assertFalse(ActivityInterceptor.isRecordable(null, "/api/v1/incidents"));
        assertFalse(ActivityInterceptor.isRecordable("POST", null));
    }

    @Test
    void usesTheUserAttributeKeyOfTheAuthInterceptor() {
        Exchange exchange =
                new Exchange("POST", "/api/v1/incidents", 201);

        exchange.attributes.put(AuthInterceptor.USER_ATTRIBUTE, MAYA);

        secured.afterCompletion(exchange.request(), exchange.response(), new Object(), null);

        assertEquals(1, store.entries.size());
    }

    private void record(
            ActivityInterceptor interceptor,
            String method,
            String route,
            int status
    ) {
        Exchange exchange =
                new Exchange(method, route, status).signedInAs(MAYA);

        interceptor.afterCompletion(exchange.request(), exchange.response(), new Object(), null);
    }

    private static final class Exchange {

        final String method;
        final int status;
        final Map<String, Object> attributes = new HashMap<>();
        final Map<String, String> variables = new HashMap<>();
        DispatcherType dispatcherType = DispatcherType.REQUEST;

        Exchange(
                String method,
                String pattern,
                int status
        ) {
            this.method = method;
            this.status = status;

            if (pattern != null) {
                attributes.put(
                        HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                        pattern
                );
            }

            attributes.put(
                    HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                    variables
            );
        }

        Exchange signedInAs(
                AuthenticatedUser user
        ) {
            attributes.put(AuthInterceptor.USER_ATTRIBUTE, user);

            return this;
        }

        Exchange variable(
                String name,
                String value
        ) {
            variables.put(name, value);

            return this;
        }

        HttpServletRequest request() {
            return (HttpServletRequest) Proxy.newProxyInstance(
                    HttpServletRequest.class.getClassLoader(),
                    new Class<?>[] {HttpServletRequest.class},
                    (proxy, called, arguments) -> {
                        switch (called.getName()) {
                            case "getDispatcherType":
                                return dispatcherType;
                            case "getMethod":
                                return method;
                            case "getAttribute":
                                return attributes.get(arguments[0]);
                            case "setAttribute":
                                attributes.put((String) arguments[0], arguments[1]);
                                return null;
                            default:
                                return null;
                        }
                    }
            );
        }

        HttpServletResponse response() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    HttpServletResponse.class.getClassLoader(),
                    new Class<?>[] {HttpServletResponse.class},
                    (proxy, called, arguments) -> {
                        if (called.getName().equals("getStatus")) {
                            return status;
                        }

                        return null;
                    }
            );
        }
    }
}
