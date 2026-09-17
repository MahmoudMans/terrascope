package com.terrascope.platform.api;

import com.terrascope.platform.api.model.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleIllegalArgument(
            IllegalArgumentException exception
    ) {
        return new ApiErrorResponse(
                "INVALID_REQUEST",
                exception.getMessage(),
                Instant.now()
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        return new ApiErrorResponse(
                "VALIDATION_ERROR",
                exception.getMessage(),
                Instant.now()
        );
    }
}
