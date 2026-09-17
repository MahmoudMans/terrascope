package com.terrascope.platform.infrastructure.eonet;

public class EonetSourceUnavailableException extends RuntimeException {

    public EonetSourceUnavailableException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
