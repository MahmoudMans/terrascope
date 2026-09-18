package com.terrascope.platform.domain;

import com.terrascope.platform.api.model.GovernanceTrustAnchorTransition;

import java.util.List;

public record GovernanceTrustLineagePortablePayload(
        String packageVersion,

        String rootKeyId,
        String rootPublicKeyFingerprint,

        String terminalKeyId,
        String terminalPublicKeyFingerprint,

        List<GovernanceTrustLineagePortableTrustAnchor> trustAnchors,

        List<GovernanceTrustAnchorTransition> transitions
) {
}
