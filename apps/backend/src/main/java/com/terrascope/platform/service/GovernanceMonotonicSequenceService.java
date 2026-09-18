package com.terrascope.platform.service;

import com.terrascope.platform.repository.GovernanceMonotonicSequenceRepository;
import org.springframework.stereotype.Service;

@Service
public class GovernanceMonotonicSequenceService {

    public static final String ALLOCATOR_VERSION =
            "TS-GOVERNANCE-MONOTONIC-ALLOCATOR-V1";

    public static final String OFFLINE_IMPORT_NUMBER_ALLOCATOR =
            "OFFLINE_TRUST_IMPORT_NUMBER_V1";

    public static final String OFFLINE_LEDGER_NUMBER_ALLOCATOR =
            "OFFLINE_TRUST_LEDGER_NUMBER_V1";

    private final GovernanceMonotonicSequenceRepository repository;

    public GovernanceMonotonicSequenceService(
            GovernanceMonotonicSequenceRepository repository
    ) {
        this.repository = repository;
    }

    public int nextImportNumber() {
        return next(
                OFFLINE_IMPORT_NUMBER_ALLOCATOR
        );
    }

    public int nextLedgerNumber() {
        return next(
                OFFLINE_LEDGER_NUMBER_ALLOCATOR
        );
    }

    private int next(
            String allocatorKey
    ) {
        int allocated =
                repository.next(
                        allocatorKey
                );

        if (
                allocated < 1
        ) {
            throw new IllegalStateException(
                    "Governance monotonic allocator returned a non-positive value for "
                            + allocatorKey
                            + "."
            );
        }

        return allocated;
    }
}
