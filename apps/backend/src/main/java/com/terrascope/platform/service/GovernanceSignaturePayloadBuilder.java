package com.terrascope.platform.service;

import com.terrascope.platform.domain.GovernanceSignatureMaterial;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class GovernanceSignaturePayloadBuilder {

    public static final String SIGNATURE_VERSION =
            "TS-GOVERNANCE-CLOSURE-ED25519-V1";

    public byte[] payload(
            GovernanceSignatureMaterial material
    ) {
        String canonical =
                """
                signatureVersion=%s
                certificateId=%s
                certificateNumber=%d
                certificateHash=%s
                dossierHash=%s
                keyId=%s
                algorithm=%s
                publicKeyFingerprint=%s
                """
                .formatted(
                        SIGNATURE_VERSION,

                        material.certificateId(),
                        material.certificateNumber(),

                        material.certificateHash(),
                        material.dossierHash(),

                        material.keyId(),
                        material.algorithm(),
                        material.publicKeyFingerprint()
                );

        return canonical.getBytes(
                StandardCharsets.UTF_8
        );
    }
}
