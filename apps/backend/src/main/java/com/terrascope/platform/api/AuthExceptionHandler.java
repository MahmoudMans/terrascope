package com.terrascope.platform.api;

import com.terrascope.platform.api.model.ApiErrorResponse;
import com.terrascope.platform.security.AuthException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiErrorResponse> handleAuth(
            AuthException exception
    ) {
        return ResponseEntity
                .status(exception.status())
                .body(
                        new ApiErrorResponse(
                                exception.code(),
                                exception.getMessage(),
                                Instant.now()
                        )
                );
    }
}
