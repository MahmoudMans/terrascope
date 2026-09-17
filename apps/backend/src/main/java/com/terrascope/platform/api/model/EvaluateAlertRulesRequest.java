package com.terrascope.platform.api.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EvaluateAlertRulesRequest(

        @Valid
        @NotNull
        @Size(max = 2000)
        List<RegionalHazardPoint> points
) {
}
