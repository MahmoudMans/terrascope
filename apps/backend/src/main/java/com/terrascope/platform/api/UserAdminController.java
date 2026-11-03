package com.terrascope.platform.api;

import com.terrascope.platform.api.model.CreateUserRequest;
import com.terrascope.platform.api.model.ResetPasswordRequest;
import com.terrascope.platform.api.model.UpdateUserRequest;
import com.terrascope.platform.api.model.UserSummary;
import com.terrascope.platform.security.AuthException;
import com.terrascope.platform.security.AuthInterceptor;
import com.terrascope.platform.security.AuthenticatedUser;
import com.terrascope.platform.security.UserAdminService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
public class UserAdminController {

    private final UserAdminService service;

    public UserAdminController(
            UserAdminService service
    ) {
        this.service = service;
    }

    @GetMapping
    public List<UserSummary> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserSummary create(
            @RequestBody
            CreateUserRequest request
    ) {
        return service.create(
                request.username(),
                request.displayName(),
                request.role(),
                request.password()
        );
    }

    @PatchMapping("/{id}")
    public UserSummary update(
            @PathVariable
            UUID id,

            @RequestBody
            UpdateUserRequest request,

            HttpServletRequest http
    ) {
        return service.update(
                requireActor(http),
                id,
                request.displayName(),
                request.role(),
                request.enabled()
        );
    }

    @PostMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(
            @PathVariable
            UUID id,

            @RequestBody
            ResetPasswordRequest request
    ) {
        service.resetPassword(
                id,
                request.newPassword()
        );
    }

    private AuthenticatedUser requireActor(
            HttpServletRequest http
    ) {
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
