package com.terrascope.platform.domain;

import java.util.UUID;

public record GovernanceTrustLineagePortableEnvelope(
        String envelopeVersion,

        UUID sourcePackageId,
        int sourcePackageNumber,

        String packageVersion,
        String packageHash,

        String payloadJson
) {
}
