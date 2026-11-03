package com.terrascope.platform.api;

import com.terrascope.platform.api.model.AuthConfigResponse;
import com.terrascope.platform.api.model.ChangePasswordRequest;
import com.terrascope.platform.api.model.LoginRequest;
import com.terrascope.platform.api.model.LoginResponse;
import com.terrascope.platform.api.model.UserSummary;
import com.terrascope.platform.security.AuthException;
import com.terrascope.platform.security.AuthInterceptor;
import com.terrascope.platform.security.AuthService;
import com.terrascope.platform.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(
            AuthService authService
    ) {
        this.authService = authService;
    }

    @GetMapping("/config")
    public AuthConfigResponse config() {
        return new AuthConfigResponse(
                authService.enabled()
        );
    }

    @PostMapping("/login")
    public LoginResponse login(
            @RequestBody
            LoginRequest request,

            HttpServletRequest http
    ) {
        LoginResponse response =
                authService.login(
                        request.username(),
                        request.password(),
                        http.getRemoteAddr()
                );

        UserSummary signedIn =
                response.user();

        http.setAttribute(
                AuthInterceptor.USER_ATTRIBUTE,
                new AuthenticatedUser(
                        signedIn.id(),
                        signedIn.username(),
                        signedIn.displayName(),
                        signedIn.role()
                )
        );

        return response;
    }

    @GetMapping("/me")
    public UserSummary me(
            HttpServletRequest http
    ) {
        return authService.currentUser(
                requireUser(http)
        );
    }

    @PostMapping("/password")
    public LoginResponse changePassword(
            @RequestBody
            ChangePasswordRequest request,

            HttpServletRequest http
    ) {
        return authService.changePassword(
                requireUser(http),
                request.currentPassword(),
                request.newPassword()
        );
    }

    private AuthenticatedUser requireUser(
            HttpServletRequest http
    ) {
        authService.requireEnabled();

        return AuthInterceptor
                .userOf(http)
                .orElseThrow(() ->
                        new AuthException(
                                HttpStatus.UNAUTHORIZED,
                                "UNAUTHORIZED",
                                "Sign in to continue."
                        )
                );
    }
}
