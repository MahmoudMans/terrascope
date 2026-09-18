package com.terrascope.platform.api.model;

import java.util.List;

public record GovernanceTrustLineagePackageHistoryResponse(
        List<GovernanceTrustLineagePackage> packages
) {
}
