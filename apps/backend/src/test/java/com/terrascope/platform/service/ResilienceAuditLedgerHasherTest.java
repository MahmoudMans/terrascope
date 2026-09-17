package com.terrascope.platform.service;

import com.terrascope.platform.domain.ResilienceAuditLedgerMaterial;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResilienceAuditLedgerHasherTest {

    private final ResilienceAuditLedgerHasher hasher =
            new ResilienceAuditLedgerHasher();

    @Test
    void genesisReceiptHashIsStable() {
        ResilienceAuditLedgerMaterial material =
                material(
                        null
                );

        String first =
                hasher.hash(
                        material
                );

        String second =
                hasher.hash(
                        material
                );

        assertThat(
                first
        ).hasSize(64);

        assertThat(
                first
        ).matches(
                "[0-9a-f]{64}"
        );

        assertThat(
                first
        ).isEqualTo(
                second
        );
    }

    @Test
    void previousReceiptHashChangesLedgerFingerprint() {
        String genesis =
                hasher.hash(
                        material(
                                null
                        )
                );

        String chained =
                hasher.hash(
                        material(
                                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                        )
                );

        assertThat(
                genesis
        ).isNotEqualTo(
                chained
        );
    }

    private ResilienceAuditLedgerMaterial material(
            String previousHash
    ) {
        return new ResilienceAuditLedgerMaterial(
                UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"
                ),

                1,

                UUID.fromString(
                        "22222222-2222-2222-2222-222222222222"
                ),

                1,

                "TS-RESILIENCE-EVIDENCE-V1",

                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",

                previousHash
        );
    }
}
