package com.terrascope.platform.repository;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Objects;

public final class GovernanceTransactionalSequenceAllocator {

    private GovernanceTransactionalSequenceAllocator() {
    }

    public static int next(
            JdbcClient jdbcClient,
            String allocatorKey
    ) {
        if (
                allocatorKey == null

                || allocatorKey.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Governance allocator key is required."
            );
        }

        Objects.requireNonNull(
                jdbcClient,
                "jdbcClient"
        );

        int value =
                jdbcClient
                        .sql("""
                                SELECT allocate_governance_sequence(
                                    :allocatorKey
                                )
                                """)
                        .param(
                                "allocatorKey",
                                allocatorKey
                        )
                        .query(
                                Integer.class
                        )
                        .single();

        if (
                value < 1
        ) {
            throw new IllegalStateException(
                    "Governance allocator returned a non-positive value for "
                            + allocatorKey
                            + "."
            );
        }

        return value;
    }
}
