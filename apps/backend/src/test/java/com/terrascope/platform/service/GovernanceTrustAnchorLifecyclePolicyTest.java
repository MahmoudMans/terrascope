package com.terrascope.platform.service;

import com.terrascope.platform.domain.GovernanceTrustAnchor;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GovernanceTrustAnchorLifecyclePolicyTest {

    private final GovernanceTrustAnchorLifecyclePolicy policy =
            new GovernanceTrustAnchorLifecyclePolicy();

    @Test
    void activeAnchorCanSignAndVerify() {
        Instant now =
                Instant.now();

        GovernanceTrustAnchor anchor =
                new GovernanceTrustAnchor(
                        "key-a",
                        "ED25519",

                        "public",
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",

                        "ACTIVE",

                        now.minusSeconds(
                                60
                        ),

                        now,

                        null
                );

        assertThat(
                policy.canSign(
                        anchor
                )
        ).isTrue();

        assertThat(
                policy.canVerifyHistoricalSignature(
                        anchor,
                        now
                )
        ).isTrue();
    }

    @Test
    void retiredAnchorCannotSignButKeepsPreRetirementValidity() {
        Instant retiredAt =
                Instant.now();

        GovernanceTrustAnchor anchor =
                new GovernanceTrustAnchor(
                        "key-a",
                        "ED25519",

                        "public",
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",

                        "RETIRED",

                        retiredAt.minusSeconds(
                                120
                        ),

                        retiredAt.minusSeconds(
                                60
                        ),

                        retiredAt
                );

        assertThat(
                policy.canSign(
                        anchor
                )
        ).isFalse();

        assertThat(
                policy.canVerifyHistoricalSignature(
                        anchor,
                        retiredAt.minusSeconds(
                                1
                        )
                )
        ).isTrue();

        assertThat(
                policy.canVerifyHistoricalSignature(
                        anchor,
                        retiredAt.plusSeconds(
                                1
                        )
                )
        ).isFalse();
    }
}
