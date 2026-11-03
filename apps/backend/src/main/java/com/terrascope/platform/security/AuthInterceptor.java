package com.terrascope.platform.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

public class AuthInterceptor implements HandlerInterceptor {

    public static final String USER_ATTRIBUTE =
            AuthInterceptor.class.getName() + ".user";

    private static final String BEARER_PREFIX = "Bearer ";

    private final SecuritySettings settings;
    private final AuthService authService;

    public AuthInterceptor(
            SecuritySettings settings,
            AuthService authService
    ) {
        this.settings = settings;
        this.authService = authService;
    }

    public static Optional<AuthenticatedUser> userOf(
            HttpServletRequest request
    ) {
        Object value =
                request.getAttribute(USER_ATTRIBUTE);

        if (value instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }

        return Optional.empty();
    }

    public Decision decide(
            String method,
            String pattern,
            String authorization
    ) {
        Optional<Role> required =
                AccessPolicy.requiredRole(
                        method,
                        pattern
                );

        if (required.isEmpty()) {
            return Decision.allow(null);
        }

        String token =
                bearerToken(authorization);

        Optional<AuthenticatedUser> user;

        try {
            user =
                    token == null
                            ? Optional.empty()
                            : authService.authenticate(token);

        } catch (RuntimeException exception) {
            return Decision.deny(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AUTH_UNAVAILABLE",
                    "Sign-in is temporarily unavailable. Try again shortly."
            );
        }

        if (user.isEmpty()) {
            return Decision.deny(
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "Sign in to continue."
            );
        }

        if (!user.get().role().allows(required.get())) {
            return Decision.deny(
                    HttpStatus.FORBIDDEN,
                    "FORBIDDEN",
                    "Your "
                            + user.get().role().label()
                            + " account can't do this. It needs "
                            + required.get().label()
                            + " access."
            );
        }

        return Decision.allow(user.get());
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws IOException {
        if (!settings.enabled()) {
            return true;
        }

        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return true;
        }

        String method =
                request.getMethod();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        Object pattern =
                request.getAttribute(
                        HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE
                );

        Decision decision =
                decide(
                        method,
                        pattern instanceof String text
                                ? text
                                : null,
                        request.getHeader("Authorization")
                );

        if (decision.allowed()) {
            if (decision.user() != null) {
                request.setAttribute(
                        USER_ATTRIBUTE,
                        decision.user()
                );
            }

            return true;
        }

        response.setStatus(decision.status().value());

        if (decision.status() == HttpStatus.UNAUTHORIZED) {
            response.setHeader(
                    "WWW-Authenticate",
                    "Bearer"
            );
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        response
                .getWriter()
                .write(decision.toJson());

        return false;
    }

    private static String bearerToken(
            String authorization
    ) {
        if (
                authorization == null
                || authorization.length() <= BEARER_PREFIX.length()
                || !authorization
                        .regionMatches(
                                true,
                                0,
                                BEARER_PREFIX,
                                0,
                                BEARER_PREFIX.length()
                        )
        ) {
            return null;
        }

        return authorization
                .substring(BEARER_PREFIX.length())
                .trim();
    }

    public record Decision(
            boolean allowed,
            HttpStatus status,
            String code,
            String message,
            AuthenticatedUser user
    ) {

        static Decision allow(
                AuthenticatedUser user
        ) {
            return new Decision(
                    true,
                    HttpStatus.OK,
                    "",
                    "",
                    user
            );
        }

        static Decision deny(
                HttpStatus status,
                String code,
                String message
        ) {
            return new Decision(
                    false,
                    status,
                    code,
                    message,
                    null
            );
        }

        public String toJson() {
            return "{\"code\":\""
                    + escape(code)
                    + "\",\"message\":\""
                    + escape(message)
                    + "\",\"timestamp\":\""
                    + Instant.now()
                    + "\"}";
        }

        private static String escape(
                String value
        ) {
            return value
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"");
        }
    }
}
