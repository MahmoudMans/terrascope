package com.terrascope.platform.service;

import com.terrascope.platform.repository.GovernanceMonotonicSequenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GovernanceMonotonicSequenceServiceTest {

    private GovernanceMonotonicSequenceRepository repository;

    private GovernanceMonotonicSequenceService service;

    @BeforeEach
    void setUp() {
        repository =
                mock(
                        GovernanceMonotonicSequenceRepository.class
                );

        service =
                new GovernanceMonotonicSequenceService(
                        repository
                );
    }

    @Test
    void importNumbersUseDedicatedTransactionalAllocator() {
        when(
                repository.next(
                        GovernanceMonotonicSequenceService.OFFLINE_IMPORT_NUMBER_ALLOCATOR
                )
        ).thenReturn(
                41
        );

        assertThat(
                service.nextImportNumber()
        ).isEqualTo(
                41
        );

        verify(
                repository
        ).next(
                GovernanceMonotonicSequenceService.OFFLINE_IMPORT_NUMBER_ALLOCATOR
        );
    }

    @Test
    void ledgerNumbersUseDedicatedTransactionalAllocator() {
        when(
                repository.next(
                        GovernanceMonotonicSequenceService.OFFLINE_LEDGER_NUMBER_ALLOCATOR
                )
        ).thenReturn(
                73
        );

        assertThat(
                service.nextLedgerNumber()
        ).isEqualTo(
                73
        );

        verify(
                repository
        ).next(
                GovernanceMonotonicSequenceService.OFFLINE_LEDGER_NUMBER_ALLOCATOR
        );
    }

    @Test
    void zeroImportAllocationIsRejected() {
        when(
                repository.next(
                        GovernanceMonotonicSequenceService.OFFLINE_IMPORT_NUMBER_ALLOCATOR
                )
        ).thenReturn(
                0
        );

        assertThatThrownBy(
                service::nextImportNumber
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessageContaining(
                        GovernanceMonotonicSequenceService.OFFLINE_IMPORT_NUMBER_ALLOCATOR
                );
    }

    @Test
    void negativeLedgerAllocationIsRejected() {
        when(
                repository.next(
                        GovernanceMonotonicSequenceService.OFFLINE_LEDGER_NUMBER_ALLOCATOR
                )
        ).thenReturn(
                -1
        );

        assertThatThrownBy(
                service::nextLedgerNumber
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessageContaining(
                        GovernanceMonotonicSequenceService.OFFLINE_LEDGER_NUMBER_ALLOCATOR
                );
    }
}
