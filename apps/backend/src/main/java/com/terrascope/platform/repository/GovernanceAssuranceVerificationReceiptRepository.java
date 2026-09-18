package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.GovernanceAssuranceEnvelopeVerificationResponse;
import com.terrascope.platform.api.model.GovernanceAssuranceVerificationReceipt;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class GovernanceAssuranceVerificationReceiptRepository {

    private static final String COLUMNS = """
            id,
            receipt_number,
            source_bundle_id,
            source_bundle_number,
            envelope_version,
            bundle_version,
            envelope_hash,
            bundle_hash,
            verification_result,
            hash_valid,
            canonical_payload_valid,
            portable_verification_valid,
            signature_count,
            verified_signature_count,
            trust_anchor_count,
            retired_anchor_count,
            lifecycle_event_count,
            message,
            verified_at
            """;

    private final JdbcClient jdbcClient;

    public GovernanceAssuranceVerificationReceiptRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public int nextReceiptNumber() {
        return jdbcClient
                .sql("""
                        SELECT
                            COALESCE(
                                MAX(receipt_number),
                                0
                            ) + 1

                        FROM resilience_governance_assurance_verification_receipts
                        """)
                .query(
                        Integer.class
                )
                .single();
    }

    public GovernanceAssuranceVerificationReceipt save(
            UUID id,
            int receiptNumber,
            GovernanceAssuranceEnvelopeVerificationResponse verification
    ) {
        UUID receiptId =
                jdbcClient
                        .sql("""
                                INSERT INTO resilience_governance_assurance_verification_receipts (
                                    id,
                                    receipt_number,
                                    source_bundle_id,
                                    source_bundle_number,
                                    envelope_version,
                                    bundle_version,
                                    envelope_hash,
                                    bundle_hash,
                                    verification_result,
                                    hash_valid,
                                    canonical_payload_valid,
                                    portable_verification_valid,
                                    signature_count,
                                    verified_signature_count,
                                    trust_anchor_count,
                                    retired_anchor_count,
                                    lifecycle_event_count,
                                    message
                                )
                                VALUES (
                                    :id,
                                    :receiptNumber,
                                    :sourceBundleId,
                                    :sourceBundleNumber,
                                    :envelopeVersion,
                                    :bundleVersion,
                                    :envelopeHash,
                                    :bundleHash,
                                    :verificationResult,
                                    :hashValid,
                                    :canonicalPayloadValid,
                                    :portableVerificationValid,
                                    :signatureCount,
                                    :verifiedSignatureCount,
                                    :trustAnchorCount,
                                    :retiredAnchorCount,
                                    :lifecycleEventCount,
                                    :message
                                )
                                RETURNING id
                                """)
                        .param(
                                "id",
                                id
                        )
                        .param(
                                "receiptNumber",
                                receiptNumber
                        )
                        .param(
                                "sourceBundleId",
                                verification.sourceBundleId(),
                                java.sql.Types.OTHER
                        )
                        .param(
                                "sourceBundleNumber",
                                verification.sourceBundleNumber(),
                                java.sql.Types.INTEGER
                        )
                        .param(
                                "envelopeVersion",
                                verification.envelopeVersion(),
                                java.sql.Types.VARCHAR
                        )
                        .param(
                                "bundleVersion",
                                verification.bundleVersion(),
                                java.sql.Types.VARCHAR
                        )
                        .param(
                                "envelopeHash",
                                verification.envelopeHash()
                        )
                        .param(
                                "bundleHash",
                                verification.bundleHash(),
                                java.sql.Types.VARCHAR
                        )
                        .param(
                                "verificationResult",
                                verification.valid()
                                        ? "VALID"
                                        : "INVALID"
                        )
                        .param(
                                "hashValid",
                                verification.hashValid()
                        )
                        .param(
                                "canonicalPayloadValid",
                                verification.canonicalPayloadValid()
                        )
                        .param(
                                "portableVerificationValid",
                                verification.portableVerificationValid()
                        )
                        .param(
                                "signatureCount",
                                verification.signatureCount()
                        )
                        .param(
                                "verifiedSignatureCount",
                                verification.verifiedSignatureCount()
                        )
                        .param(
                                "trustAnchorCount",
                                verification.trustAnchorCount()
                        )
                        .param(
                                "retiredAnchorCount",
                                verification.retiredAnchorCount()
                        )
                        .param(
                                "lifecycleEventCount",
                                verification.lifecycleEventCount()
                        )
                        .param(
                                "message",
                                verification.message()
                        )
                        .query(
                                UUID.class
                        )
                        .single();

        return findById(
                receiptId
        ).orElseThrow();
    }

    public List<GovernanceAssuranceVerificationReceipt> findHistory() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_assurance_verification_receipts

                        ORDER BY
                            receipt_number DESC,
                            verified_at DESC
                        """)
                .query(
                        this::map
                )
                .list();
    }

    private Optional<GovernanceAssuranceVerificationReceipt> findById(
            UUID id
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_assurance_verification_receipts
                        WHERE id = :id
                        """)
                .param(
                        "id",
                        id
                )
                .query(
                        this::map
                )
                .optional();
    }

    private GovernanceAssuranceVerificationReceipt map(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        Integer sourceBundleNumber =
                (Integer) resultSet.getObject(
                        "source_bundle_number"
                );

        return new GovernanceAssuranceVerificationReceipt(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getInt(
                        "receipt_number"
                ),

                resultSet.getObject(
                        "source_bundle_id",
                        UUID.class
                ),

                sourceBundleNumber,

                resultSet.getString(
                        "envelope_version"
                ),

                resultSet.getString(
                        "bundle_version"
                ),

                resultSet.getString(
                        "envelope_hash"
                ),

                resultSet.getString(
                        "bundle_hash"
                ),

                resultSet.getString(
                        "verification_result"
                ),

                resultSet.getBoolean(
                        "hash_valid"
                ),

                resultSet.getBoolean(
                        "canonical_payload_valid"
                ),

                resultSet.getBoolean(
                        "portable_verification_valid"
                ),

                resultSet.getInt(
                        "signature_count"
                ),

                resultSet.getInt(
                        "verified_signature_count"
                ),

                resultSet.getInt(
                        "trust_anchor_count"
                ),

                resultSet.getInt(
                        "retired_anchor_count"
                ),

                resultSet.getInt(
                        "lifecycle_event_count"
                ),

                resultSet.getString(
                        "message"
                ),

                resultSet
                        .getObject(
                                "verified_at",
                                OffsetDateTime.class
                        )
                        .toInstant()
        );
    }
}
