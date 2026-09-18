package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceAssuranceBundle;
import com.terrascope.platform.api.model.GovernanceAssuranceBundleVerificationResponse;
import com.terrascope.platform.api.model.GovernanceAssuranceVerificationReceipt;
import com.terrascope.platform.api.model.GovernanceAssuranceVerificationReceiptHistoryResponse;
import com.terrascope.platform.domain.GovernanceAssurancePortableEnvelope;
import com.terrascope.platform.repository.GovernanceAssuranceBundleRepository;
import com.terrascope.platform.repository.GovernanceAssuranceVerificationReceiptRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class GovernanceAssuranceEnvelopeService {

    private final GovernanceAssuranceBundleRepository bundleRepository;

    private final GovernanceAssuranceBundleService bundleService;

    private final GovernanceAssuranceEnvelopeCodec envelopeCodec;

    private final GovernanceAssuranceVerificationReceiptRepository receiptRepository;

    private final GovernanceAssuranceStatelessVerifier statelessVerifier;

    public GovernanceAssuranceEnvelopeService(
            GovernanceAssuranceBundleRepository bundleRepository,
            GovernanceAssuranceBundleService bundleService,
            GovernanceAssuranceEnvelopeCodec envelopeCodec,
            GovernanceAssuranceVerificationReceiptRepository receiptRepository,
            GovernanceAssuranceStatelessVerifier statelessVerifier
    ) {
        this.bundleRepository = bundleRepository;
        this.bundleService = bundleService;
        this.envelopeCodec = envelopeCodec;
        this.receiptRepository = receiptRepository;
        this.statelessVerifier = statelessVerifier;
    }

    public byte[] export(
            UUID bundleId
    ) {
        GovernanceAssuranceBundle bundle =
                bundleRepository
                        .findById(
                                bundleId
                        )
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Governance assurance bundle was not found."
                                )
                        );

        GovernanceAssuranceBundleVerificationResponse verification =
                bundleService.verify(
                        bundleId
                );

        if (
                !verification.valid()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Governance assurance bundle must verify before air-gapped export."
            );
        }

        GovernanceAssurancePortableEnvelope envelope =
                new GovernanceAssurancePortableEnvelope(
                        GovernanceAssuranceEnvelopeCodec.ENVELOPE_VERSION,

                        bundle.id(),
                        bundle.bundleNumber(),

                        bundle.bundleVersion(),
                        bundle.bundleHash(),

                        bundle.payloadJson()
                );

        return envelopeCodec.encode(
                envelope
        );
    }

    public GovernanceAssuranceVerificationReceiptHistoryResponse receiptHistory() {
        return new GovernanceAssuranceVerificationReceiptHistoryResponse(
                receiptRepository.findHistory()
        );
    }

    @Transactional
    public GovernanceAssuranceVerificationReceipt createReceipt(
            byte[] envelopeBytes
    ) {
        var verification =
                statelessVerifier.verify(
                        envelopeBytes
                );

        return receiptRepository.save(
                UUID.randomUUID(),

                receiptRepository.nextReceiptNumber(),

                verification
        );
    }
}
