package com.terrascope.platform.domain;

public enum CommandPressureBand {

    STABLE,
    GUARDED,
    ELEVATED,
    SEVERE,
    CRITICAL;

    public static CommandPressureBand fromScore(
            int score
    ) {
        if (
                score < 0
                || score > 100
        ) {
            throw new IllegalArgumentException(
                    "Command pressure score must be between 0 and 100."
            );
        }

        if (score >= 80) {
            return CRITICAL;
        }

        if (score >= 60) {
            return SEVERE;
        }

        if (score >= 40) {
            return ELEVATED;
        }

        if (score >= 20) {
            return GUARDED;
        }

        return STABLE;
    }
}
