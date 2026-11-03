package com.terrascope.platform.activity;

import com.terrascope.platform.security.AccessPolicy;
import com.terrascope.platform.security.AuthInterceptor;
import com.terrascope.platform.security.AuthenticatedUser;
import com.terrascope.platform.security.Role;
import com.terrascope.platform.security.SecuritySettings;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class ActivityInterceptor implements HandlerInterceptor {

    public static final String LOCAL_USERNAME =
            "local";

    public static final String LOCAL_DISPLAY_NAME =
            "Local session";

    private static final Logger log =
            LoggerFactory.getLogger(
                    ActivityInterceptor.class
            );

    private static final Set<String> WRITE_METHODS =
            Set.of(
                    "POST",
                    "PUT",
                    "PATCH",
                    "DELETE"
            );

    private static final Set<String> ALWAYS_RECORDED =
            Set.of(
                    "POST /api/v1/auth/login",
                    "POST /api/v1/auth/password"
            );

    private static final int MAX_ENTITY_ID_LENGTH =
            120;

    private final SecuritySettings settings;
    private final ActivityStore store;

    public ActivityInterceptor(
            SecuritySettings settings,
            ActivityStore store
    ) {
        this.settings = settings;
        this.store = store;
    }

    public static boolean isRecordable(
            String method,
            String route
    ) {
        if (
                method == null
                || route == null
        ) {
            return false;
        }

        String verb =
                method.toUpperCase(Locale.ROOT);

        if (!WRITE_METHODS.contains(verb)) {
            return false;
        }

        if (ALWAYS_RECORDED.contains(verb + " " + route)) {
            return true;
        }

        return AccessPolicy
                .requiredRole(
                        verb,
                        route
                )
                .map(role -> role.allows(Role.OPERATOR))
                .orElse(false);
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception failure
    ) {
        try {
            capture(
                    request,
                    response,
                    failure
            );

        } catch (RuntimeException exception) {
            log.warn(
                    "Could not record activity: {}",
                    exception.getMessage()
            );
        }
    }

    private void capture(
            HttpServletRequest request,
            HttpServletResponse response,
            Exception failure
    ) {
        if (
                failure != null
                || request.getDispatcherType() != DispatcherType.REQUEST
        ) {
            return;
        }

        int status =
                response.getStatus();

        if (
                status < 200
                || status >= 300
        ) {
            return;
        }

        Object pattern =
                request.getAttribute(
                        HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE
                );

        if (!(pattern instanceof String route)) {
            return;
        }

        String method =
                request.getMethod();

        if (!isRecordable(method, route)) {
            return;
        }

        Optional<AuthenticatedUser> user =
                AuthInterceptor.userOf(request);

        UUID actorId;
        String username;
        String displayName;
        Role role;

        if (user.isPresent()) {
            actorId = user.get().id();
            username = user.get().username();
            displayName = user.get().displayName();
            role = user.get().role();

        } else if (!settings.enabled()) {
            actorId = null;
            username = LOCAL_USERNAME;
            displayName = LOCAL_DISPLAY_NAME;
            role = null;

        } else {
            return;
        }

        ActivityDescriber.Description description =
                ActivityDescriber.describe(
                        method,
                        route
                );

        store.record(
                new ActivityEntry(
                        UUID.randomUUID(),
                        Instant.now(),
                        actorId,
                        username,
                        displayName,
                        role,
                        method.toUpperCase(Locale.ROOT),
                        route,
                        description.action(),
                        description.entityType(),
                        entityId(
                                request,
                                description.idVariable()
                        ),
                        status
                )
        );
    }

    private static String entityId(
            HttpServletRequest request,
            String variable
    ) {
        if (variable == null) {
            return null;
        }

        Object variables =
                request.getAttribute(
                        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE
                );

        if (!(variables instanceof Map<?, ?> map)) {
            return null;
        }

        Object value =
                map.get(variable);

        if (!(value instanceof String text)) {
            return null;
        }

        String cleaned =
                text
                        .replaceAll("\\p{Cntrl}", "")
                        .trim();

        if (cleaned.isEmpty()) {
            return null;
        }

        return cleaned.length() > MAX_ENTITY_ID_LENGTH
                ? cleaned.substring(0, MAX_ENTITY_ID_LENGTH)
                : cleaned;
    }
}
