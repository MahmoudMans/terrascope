package com.terrascope.platform.domain;

public enum RegionalActivityBand {

    QUIET,
    GUARDED,
    ELEVATED,
    HIGH,
    CRITICAL;

    public static RegionalActivityBand fromIndex(
            double activityIndex
    ) {
        if (activityIndex >= 85.0) {
            return CRITICAL;
        }

        if (activityIndex >= 68.0) {
            return HIGH;
        }

        if (activityIndex >= 48.0) {
            return ELEVATED;
        }

        if (activityIndex >= 25.0) {
            return GUARDED;
        }

        return QUIET;
    }
}
