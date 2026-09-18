package com.terrascope.platform.service;

import com.terrascope.platform.domain.GovernanceTrustAnchor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class GovernanceTrustAnchorLifecyclePolicy {

    public boolean canSign(
            GovernanceTrustAnchor anchor
    ) {
        return anchor != null
                && "ACTIVE"
                        .equals(
                                anchor.status()
                        )
                && anchor.retiredAt()
                   == null;
    }

    public boolean canVerifyHistoricalSignature(
            GovernanceTrustAnchor anchor,
            Instant signatureCreatedAt
    ) {
        if (
                anchor == null
                || signatureCreatedAt == null
        ) {
            return false;
        }

        if (
                "ACTIVE"
                        .equals(
                                anchor.status()
                        )
        ) {
            return anchor.retiredAt()
                    == null;
        }

        if (
                !"RETIRED"
                        .equals(
                                anchor.status()
                        )

                || anchor.retiredAt()
                   == null
        ) {
            return false;
        }

        return !signatureCreatedAt
                .isAfter(
                        anchor.retiredAt()
                );
    }
}
