package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceTrustAnchorView;
import com.terrascope.platform.api.model.RegisterGovernanceTrustAnchorRequest;
import com.terrascope.platform.api.model.RetireGovernanceTrustAnchorRequest;
import com.terrascope.platform.domain.GovernanceTrustAnchor;
import com.terrascope.platform.repository.GovernanceClosureSignatureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GovernanceTrustAnchorLifecycleServiceTest {

    private GovernanceClosureSignatureRepository repository;
    private GovernanceClosureCertificateService closureService;
    private GovernanceSigningConfiguration configuration;

    private GovernanceEd25519Crypto crypto;
    private GovernanceSignaturePayloadBuilder payloadBuilder;
    private GovernanceTrustAnchorLifecyclePolicy policy;

    private GovernanceClosureSignatureService service;

    private String publicKey;
    private String fingerprint;

    @BeforeEach
    void setUp() throws Exception {
        repository =
                mock(
                        GovernanceClosureSignatureRepository.class
                );

        closureService =
                mock(
                        GovernanceClosureCertificateService.class
                );

        configuration =
                mock(
                        GovernanceSigningConfiguration.class
                );

        crypto =
                new GovernanceEd25519Crypto();

        payloadBuilder =
                new GovernanceSignaturePayloadBuilder();

        policy =
                new GovernanceTrustAnchorLifecyclePolicy();

        service =
                new GovernanceClosureSignatureService(
                        repository,
                        closureService,
                        configuration,
                        crypto,
                        payloadBuilder,
                        policy
                );

        KeyPair pair =
                KeyPairGenerator
                        .getInstance(
                                "Ed25519"
                        )
                        .generateKeyPair();

        publicKey =
                Base64
                        .getEncoder()
                        .encodeToString(
                                pair.getPublic()
                                        .getEncoded()
                        );

        fingerprint =
                crypto.publicKeyFingerprint(
                        publicKey
                );
    }

    @Test
    void registersPublicTrustAnchorAndLifecycleEvent() {
        when(
                repository.findTrustAnchor(
                        "key-v2"
                )
        ).thenReturn(
                Optional.empty()
        );

        GovernanceTrustAnchor anchor =
                activeAnchor(
                        "key-v2"
                );

        when(
                repository.registerTrustAnchor(
                        "key-v2",
                        publicKey,
                        fingerprint
                )
        ).thenReturn(
                anchor
        );

        GovernanceTrustAnchorView view =
                view(
                        "key-v2",
                        "ACTIVE",
                        null,
                        "",
                        null
                );

        when(
                repository.requireTrustAnchorView(
                        "key-v2"
                )
        ).thenReturn(
                view
        );

        GovernanceTrustAnchorView result =
                service.registerTrustAnchor(
                        new RegisterGovernanceTrustAnchorRequest(
                                "key-v2",
                                publicKey
                        )
                );

        assertThat(
                result.status()
        ).isEqualTo(
                "ACTIVE"
        );

        verify(
                repository
        ).appendTrustAnchorEvent(
                any(),
                eq("key-v2"),
                eq("REGISTERED"),
                eq(null),
                any()
        );
    }

    @Test
    void duplicateTrustAnchorKeyIdIsRejected() {
        when(
                repository.findTrustAnchor(
                        "key-v2"
                )
        ).thenReturn(
                Optional.of(
                        activeAnchor(
                                "key-v2"
                        )
                )
        );

        assertThatThrownBy(() ->
                service.registerTrustAnchor(
                        new RegisterGovernanceTrustAnchorRequest(
                                "key-v2",
                                publicKey
                        )
                )
        ).isInstanceOf(
                ResponseStatusException.class
        );
    }

    @Test
    void retiresAnchorOnlyToActiveReplacement() {
        GovernanceTrustAnchor old =
                activeAnchor(
                        "key-v1"
                );

        GovernanceTrustAnchor replacement =
                activeAnchor(
                        "key-v2"
                );

        when(
                repository.findTrustAnchor(
                        "key-v1"
                )
        ).thenReturn(
                Optional.of(
                        old
                )
        );

        when(
                repository.findTrustAnchor(
                        "key-v2"
                )
        ).thenReturn(
                Optional.of(
                        replacement
                )
        );

        Instant retiredAt =
                Instant.now();

        GovernanceTrustAnchorView retired =
                view(
                        "key-v1",
                        "RETIRED",
                        "key-v2",
                        "Scheduled rotation.",
                        retiredAt
                );

        when(
                repository.retireTrustAnchor(
                        "key-v1",
                        "key-v2",
                        "Scheduled rotation."
                )
        ).thenReturn(
                retired
        );

        GovernanceTrustAnchorView result =
                service.retireTrustAnchor(
                        "key-v1",

                        new RetireGovernanceTrustAnchorRequest(
                                "key-v2",
                                "Scheduled rotation."
                        )
                );

        assertThat(
                result.status()
        ).isEqualTo(
                "RETIRED"
        );

        assertThat(
                result.supersededByKeyId()
        ).isEqualTo(
                "key-v2"
        );

        verify(
                repository
        ).appendTrustAnchorEvent(
                any(),
                eq("key-v1"),
                eq("RETIRED"),
                eq("key-v2"),
                any()
        );
    }

    @Test
    void retiredConfiguredKeyIsNotSigningReady() {
        when(
                configuration.configured()
        ).thenReturn(
                true
        );

        when(
                configuration.keyId()
        ).thenReturn(
                "key-v1"
        );

        when(
                configuration.publicKeyX509Base64()
        ).thenReturn(
                publicKey
        );

        GovernanceTrustAnchor retired =
                new GovernanceTrustAnchor(
                        "key-v1",
                        "ED25519",

                        publicKey,
                        fingerprint,

                        "RETIRED",

                        Instant.now()
                                .minusSeconds(
                                        120
                                ),

                        Instant.now()
                                .minusSeconds(
                                        60
                                ),

                        Instant.now()
                );

        when(
                repository.findTrustAnchor(
                        "key-v1"
                )
        ).thenReturn(
                Optional.of(
                        retired
                )
        );

        assertThat(
                service.readiness()
                        .ready()
        ).isFalse();

        assertThat(
                service.readiness()
                        .message()
        ).contains(
                "retired"
        );
    }

    private GovernanceTrustAnchor activeAnchor(
            String keyId
    ) {
        Instant now =
                Instant.now();

        return new GovernanceTrustAnchor(
                keyId,
                "ED25519",

                publicKey,
                fingerprint,

                "ACTIVE",

                now,
                now,
                null
        );
    }

    private GovernanceTrustAnchorView view(
            String keyId,
            String status,
            String replacement,
            String reason,
            Instant retiredAt
    ) {
        Instant now =
                Instant.now();

        return new GovernanceTrustAnchorView(
                keyId,

                "ED25519",

                fingerprint,

                status,

                replacement,
                reason,

                now,
                now,
                retiredAt
        );
    }
}
