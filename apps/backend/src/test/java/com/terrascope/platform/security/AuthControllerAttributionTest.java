package com.terrascope.platform.security;

import com.terrascope.platform.api.AuthController;
import com.terrascope.platform.api.model.LoginRequest;
import com.terrascope.platform.api.model.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthControllerAttributionTest {

    private static final String PASSWORD =
            "a-long-enough-password";

    private final SecurityFixture fixture =
            new SecurityFixture(true);

    private final AuthController controller =
            new AuthController(fixture.auth);

    private static HttpServletRequest requestWith(
            Map<String, Object> attributes
    ) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[] {HttpServletRequest.class},
                (proxy, called, arguments) -> {
                    switch (called.getName()) {
                        case "getRemoteAddr":
                            return "10.0.0.7";
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

    @Test
    void identifiesTheUserOnTheRequestAfterASuccessfulSignIn() {
        fixture.addUser("maya", Role.OPERATOR, PASSWORD);

        Map<String, Object> attributes =
                new HashMap<>();

        LoginResponse response =
                controller.login(
                        new LoginRequest("maya", PASSWORD),
                        requestWith(attributes)
                );

        AuthenticatedUser user =
                (AuthenticatedUser) attributes.get(AuthInterceptor.USER_ATTRIBUTE);

        assertEquals(response.user().id(), user.id());
        assertEquals("maya", user.username());
        assertEquals(Role.OPERATOR, user.role());
        assertEquals(response.user().displayName(), user.displayName());
    }

    @Test
    void identifiesNobodyWhenSignInFails() {
        fixture.addUser("maya", Role.OPERATOR, PASSWORD);

        Map<String, Object> attributes =
                new HashMap<>();

        try {
            controller.login(
                    new LoginRequest("maya", "wrong-password-entirely"),
                    requestWith(attributes)
            );

            assertTrue(false);

        } catch (AuthException expected) {
            assertEquals("INVALID_CREDENTIALS", expected.code());
        }

        assertFalse(attributes.containsKey(AuthInterceptor.USER_ATTRIBUTE));
    }
}
