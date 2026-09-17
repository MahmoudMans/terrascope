package com.terrascope.platform.domain;

public enum DamageRecoveryStatus {

    REPORTED,
    VERIFIED,
    REPAIRING,
    RESTORED;

    public boolean restored() {
        return this == RESTORED;
    }
}
