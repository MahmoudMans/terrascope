package com.terrascope.platform.infrastructure.usgs;

public class DisasterSourceUnavailableException extends RuntimeException {

    public DisasterSourceUnavailableException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
