package com.terrascope.platform.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class GovernanceMonotonicSequenceRepository {

    private final JdbcClient jdbcClient;

    public GovernanceMonotonicSequenceRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public int next(
            String allocatorKey
    ) {
        return jdbcClient
                .sql("""
                        UPDATE resilience_governance_sequence_allocators

                        SET
                            next_value =
                                next_value + 1,

                            updated_at =
                                NOW()

                        WHERE allocator_key =
                              :allocatorKey

                        RETURNING
                            next_value - 1
                        """)
                .param(
                        "allocatorKey",
                        allocatorKey
                )
                .query(
                        Integer.class
                )
                .single();
    }
}
