package com.terrascope.platform.api.model;

import java.time.Instant;

public record UpdateDamageRecoveryEtaRequest(
        Instant recoveryEta
) {
}
