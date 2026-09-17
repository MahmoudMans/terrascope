package com.terrascope.platform.domain;

public enum ResilienceCertificationStatus {

    BLOCKED,
    READY,
    CERTIFIED;

    public static ResilienceCertificationStatus fromReady(
            boolean ready
    ) {
        return ready
                ? READY
                : BLOCKED;
    }
}
