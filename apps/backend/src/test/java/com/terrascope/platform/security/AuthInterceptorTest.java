package com.terrascope.platform.security;

import com.terrascope.platform.security.AuthInterceptor.Decision;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.HandlerMapping;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthInterceptorTest {

    private static final String PASSWORD =
            "a-long-enough-password";

    private final SecurityFixture fixture =
            new SecurityFixture(true);

    private final AuthInterceptor interceptor =
            new AuthInterceptor(fixture.settings, fixture.auth);

    private String tokenFor(
            String username,
            Role role
    ) {
        fixture.addUser(username, role, PASSWORD);

        return fixture.auth.login(username, PASSWORD, "10.0.0.1").token();
    }

    @Test
    void letsPublicRoutesThroughWithoutAToken() {
        Decision decision =
                interceptor.decide("POST", "/api/v1/auth/login", null);

        assertTrue(decision.allowed());
        assertNull(decision.user());

        assertTrue(interceptor.decide("GET", "/api/v1/status", null).allowed());
        assertTrue(interceptor.decide("GET", "/api/v1/auth/config", null).allowed());
    }

    @Test
    void asksForSignInWhenTheTokenIsMissingOrInvalid() {
        Decision missing =
                interceptor.decide("GET", "/api/v1/incidents", null);

        assertFalse(missing.allowed());
        assertEquals(401, missing.status().value());
        assertEquals("UNAUTHORIZED", missing.code());

        assertEquals(401, interceptor.decide("GET", "/api/v1/incidents", "Bearer nonsense").status().value());
        assertEquals(401, interceptor.decide("GET", "/api/v1/incidents", "Bearer ").status().value());
        assertEquals(401, interceptor.decide("GET", "/api/v1/incidents", "Basic YWxpY2U6cHc=").status().value());
        assertEquals(401, interceptor.decide("GET", "/api/v1/incidents", "").status().value());
    }

    @Test
    void acceptsTheBearerSchemeInAnyCase() {
        String token =
                tokenFor("alice", Role.VIEWER);

        assertTrue(interceptor.decide("GET", "/api/v1/incidents", "Bearer " + token).allowed());
        assertTrue(interceptor.decide("GET", "/api/v1/incidents", "bearer " + token).allowed());
        assertTrue(interceptor.decide("GET", "/api/v1/incidents", "BEARER " + token).allowed());
    }

    @Test
    void enforcesTheRoleForEachRoute() {
        String viewer = "Bearer " + tokenFor("vera", Role.VIEWER);
        String operator = "Bearer " + tokenFor("oscar", Role.OPERATOR);
        String admin = "Bearer " + tokenFor("adam", Role.ADMIN);

        String governance =
                "/api/v1/resilience-portfolio/governance-closure/certificates";

        assertTrue(interceptor.decide("GET", "/api/v1/incidents", viewer).allowed());
        assertTrue(interceptor.decide("POST", "/api/v1/simulations/run", viewer).allowed());

        Decision blocked =
                interceptor.decide("POST", "/api/v1/incidents", viewer);

        assertFalse(blocked.allowed());
        assertEquals(403, blocked.status().value());
        assertEquals("Your Viewer account can't do this. It needs Operator access.", blocked.message());

        assertTrue(interceptor.decide("POST", "/api/v1/incidents", operator).allowed());

        Decision governanceBlocked =
                interceptor.decide("POST", governance, operator);

        assertEquals(403, governanceBlocked.status().value());
        assertEquals("Your Operator account can't do this. It needs Administrator access.", governanceBlocked.message());

        assertTrue(interceptor.decide("POST", governance, admin).allowed());
        assertTrue(interceptor.decide("GET", "/api/v1/admin/users", admin).allowed());
        assertEquals(403, interceptor.decide("GET", "/api/v1/admin/users", operator).status().value());
    }

    @Test
    void reportsTheAuthenticatedUserOnSuccess() {
        String token =
                tokenFor("alice", Role.OPERATOR);

        Decision decision =
                interceptor.decide("GET", "/api/v1/incidents", "Bearer " + token);

        assertNotNull(decision.user());
        assertEquals("alice", decision.user().username());
    }

    @Test
    void failsClosedWhenTheUserLookupBreaks() {
        AuthService broken =
                new AuthService(
                        fixture.settings,
                        fixture.store,
                        fixture.directory,
                        fixture.hasher,
                        fixture.tokens
                ) {
                    @Override
                    public java.util.Optional<AuthenticatedUser> authenticate(String token) {
                        throw new IllegalStateException("database down");
                    }
                };

        Decision decision =
                new AuthInterceptor(fixture.settings, broken)
                        .decide("GET", "/api/v1/incidents", "Bearer abc");

        assertFalse(decision.allowed());
        assertEquals(503, decision.status().value());
        assertEquals("AUTH_UNAVAILABLE", decision.code());
    }

    @Test
    void rendersErrorsAsJson() {
        Decision decision =
                Decision.deny(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "FORBIDDEN",
                        "Say \"no\" \\ politely"
                );

        String json =
                decision.toJson();

        assertTrue(json.startsWith("{\"code\":\"FORBIDDEN\",\"message\":\"Say \\\"no\\\" \\\\ politely\",\"timestamp\":\""));
        assertTrue(json.endsWith("\"}"));
    }

    @Test
    void preHandleBlocksUnauthenticatedRequestsWithAJsonBody() throws Exception {
        Exchange exchange =
                new Exchange("GET", "/api/v1/incidents", null);

        assertFalse(interceptor.preHandle(exchange.request(), exchange.response(), new Object()));

        assertEquals(401, exchange.status);
        assertEquals("application/json", exchange.contentType);
        assertEquals("Bearer", exchange.headers.get("WWW-Authenticate"));
        assertTrue(exchange.body.toString().contains("\"code\":\"UNAUTHORIZED\""));
    }

    @Test
    void preHandleAttachesTheUserForAllowedRequests() throws Exception {
        String token =
                tokenFor("alice", Role.OPERATOR);

        Exchange exchange =
                new Exchange("POST", "/api/v1/incidents", "Bearer " + token);

        assertTrue(interceptor.preHandle(exchange.request(), exchange.response(), new Object()));
        assertEquals("alice", AuthInterceptor.userOf(exchange.request()).get().username());
    }

    @Test
    void preHandleStepsAsideForPreflightAsyncAndDisabledSecurity() throws Exception {
        Exchange preflight =
                new Exchange("OPTIONS", "/api/v1/incidents", null);

        assertTrue(interceptor.preHandle(preflight.request(), preflight.response(), new Object()));

        Exchange async =
                new Exchange("GET", "/api/v1/stream", null);

        async.dispatcherType = DispatcherType.ASYNC;

        assertTrue(interceptor.preHandle(async.request(), async.response(), new Object()));

        SecurityFixture off =
                new SecurityFixture(false);

        Exchange open =
                new Exchange("GET", "/api/v1/incidents", null);

        assertTrue(
                new AuthInterceptor(off.settings, off.auth)
                        .preHandle(open.request(), open.response(), new Object())
        );

        assertEquals(0, open.status);
    }

    @Test
    void preHandleFailsClosedWhenNoRoutePatternIsKnown() throws Exception {
        Exchange exchange =
                new Exchange("POST", null, null);

        assertFalse(interceptor.preHandle(exchange.request(), exchange.response(), new Object()));
        assertEquals(401, exchange.status);
    }

    private static final class Exchange {

        final String method;
        final String pattern;
        final String authorization;
        final Map<String, Object> attributes = new HashMap<>();
        final Map<String, String> headers = new HashMap<>();
        final StringWriter body = new StringWriter();
        DispatcherType dispatcherType = DispatcherType.REQUEST;
        int status;
        String contentType;

        private HttpServletRequest requestProxy;
        private HttpServletResponse responseProxy;

        Exchange(
                String method,
                String pattern,
                String authorization
        ) {
            this.method = method;
            this.pattern = pattern;
            this.authorization = authorization;

            if (pattern != null) {
                attributes.put(
                        HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                        pattern
                );
            }
        }

        HttpServletRequest request() {
            if (requestProxy == null) {
                requestProxy =
                        (HttpServletRequest) Proxy.newProxyInstance(
                                HttpServletRequest.class.getClassLoader(),
                                new Class<?>[] {HttpServletRequest.class},
                                (proxy, called, arguments) -> {
                                    switch (called.getName()) {
                                        case "getDispatcherType":
                                            return dispatcherType;
                                        case "getMethod":
                                            return method;
                                        case "getHeader":
                                            return "Authorization".equals(arguments[0])
                                                    ? authorization
                                                    : null;
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

            return requestProxy;
        }

        HttpServletResponse response() {
            if (responseProxy == null) {
                responseProxy =
                        (HttpServletResponse) Proxy.newProxyInstance(
                                HttpServletResponse.class.getClassLoader(),
                                new Class<?>[] {HttpServletResponse.class},
                                (proxy, called, arguments) -> {
                                    switch (called.getName()) {
                                        case "setStatus":
                                            status = (Integer) arguments[0];
                                            return null;
                                        case "setHeader":
                                            headers.put((String) arguments[0], (String) arguments[1]);
                                            return null;
                                        case "setContentType":
                                            contentType = (String) arguments[0];
                                            return null;
                                        case "getWriter":
                                            return new PrintWriter(body, true);
                                        default:
                                            return null;
                                    }
                                }
                        );
            }

            return responseProxy;
        }
    }
}
