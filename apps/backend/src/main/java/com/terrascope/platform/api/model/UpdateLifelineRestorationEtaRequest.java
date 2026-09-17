package com.terrascope.platform.api.model;

import java.time.Instant;

public record UpdateLifelineRestorationEtaRequest(
        Instant restorationEta
) {
}
