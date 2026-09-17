package com.terrascope.platform.domain;

public enum ClosureReadinessStatus {

    READY,
    BLOCKED;

    public static ClosureReadinessStatus fromReady(
            boolean ready
    ) {
        return ready
                ? READY
                : BLOCKED;
    }
}
