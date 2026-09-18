package com.terrascope.platform.repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class GovernanceTransactionalSequenceAllocatorTest {

    @Test
    void nullAllocatorKeyIsRejectedBeforeDatabaseAccess() {
        JdbcClient jdbcClient =
                mock(
                        JdbcClient.class
                );

        assertThatThrownBy(() ->
                GovernanceTransactionalSequenceAllocator.next(
                        jdbcClient,
                        null
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "allocator key"
                );
    }

    @Test
    void blankAllocatorKeyIsRejectedBeforeDatabaseAccess() {
        JdbcClient jdbcClient =
                mock(
                        JdbcClient.class
                );

        assertThatThrownBy(() ->
                GovernanceTransactionalSequenceAllocator.next(
                        jdbcClient,
                        "   "
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "allocator key"
                );
    }
}
