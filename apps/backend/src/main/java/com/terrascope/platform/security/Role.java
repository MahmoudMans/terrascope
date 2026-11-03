package com.terrascope.platform.security;

import java.util.Locale;
import java.util.Optional;

public enum Role {

    VIEWER("Viewer", 1),
    OPERATOR("Operator", 2),
    ADMIN("Administrator", 3);

    private final String label;
    private final int rank;

    Role(
            String label,
            int rank
    ) {
        this.label = label;
        this.rank = rank;
    }

    public String label() {
        return label;
    }

    public boolean allows(
            Role required
    ) {
        return rank >= required.rank;
    }

    public static Optional<Role> parse(
            String value
    ) {
        if (value == null) {
            return Optional.empty();
        }

        String normalized =
                value
                        .trim()
                        .toUpperCase(Locale.ROOT);

        for (Role role : values()) {
            if (role.name().equals(normalized)) {
                return Optional.of(role);
            }
        }

        return Optional.empty();
    }
}
