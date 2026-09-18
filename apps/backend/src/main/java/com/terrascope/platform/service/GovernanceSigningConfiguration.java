package com.terrascope.platform.service;

import org.springframework.stereotype.Component;

@Component
public class GovernanceSigningConfiguration {

    public String keyId() {
        return environment(
                "TERRASCOPE_GOVERNANCE_SIGNING_KEY_ID"
        );
    }

    public String privateKeyPkcs8Base64() {
        return environment(
                "TERRASCOPE_GOVERNANCE_SIGNING_PRIVATE_KEY_PKCS8_B64"
        );
    }

    public String publicKeyX509Base64() {
        return environment(
                "TERRASCOPE_GOVERNANCE_SIGNING_PUBLIC_KEY_X509_B64"
        );
    }

    public boolean configured() {
        return hasText(
                keyId()
        )
        && hasText(
                privateKeyPkcs8Base64()
        )
        && hasText(
                publicKeyX509Base64()
        );
    }

    private String environment(
            String name
    ) {
        String value =
                System.getenv(
                        name
                );

        return value == null
                ? ""
                : value.trim();
    }

    private boolean hasText(
            String value
    ) {
        return value != null
                && !value.isBlank();
    }
}
