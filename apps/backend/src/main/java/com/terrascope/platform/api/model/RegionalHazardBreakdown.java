package com.terrascope.platform.api.model;

public record RegionalHazardBreakdown(
        int earthquakeCount,
        int volcanoCount,
        int wildfireCount,
        int severeStormCount
) {
}
