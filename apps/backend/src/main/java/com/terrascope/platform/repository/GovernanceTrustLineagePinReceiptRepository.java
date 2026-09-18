package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.GovernanceTrustLineagePinReceipt;
import com.terrascope.platform.api.model.GovernanceTrustLineagePinnedVerificationResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class GovernanceTrustLineagePinReceiptRepository {

    private static final String COLUMNS = """
            id,
            receipt_number,
            source_package_id,
            source_package_number,
            envelope_hash,
            package_hash,
            trusted_root_fingerprint,
            presented_root_fingerprint,
            verification_result,
            package_hash_valid,
            canonical_payload_valid,
            lineage_valid,
            root_pin_valid,
            transition_count,
            verified_transition_count,
            trust_anchor_count,
            message,
            verified_at
            """;

    private final JdbcClient jdbcClient;

    public GovernanceTrustLineagePinReceiptRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public int nextReceiptNumber() {
        return GovernanceTransactionalSequenceAllocator.next(
                jdbcClient,
                "GOV_TRUST_LINEAGE_PIN_RECEIPTS_RECEIPT_NUMBER_V1"
        );
    }

    public GovernanceTrustLineagePinReceipt save(
            UUID id,
            int receiptNumber,
            GovernanceTrustLineagePinnedVerificationResponse verification
    ) {
        UUID receiptId =
                jdbcClient
                        .sql("""
                                INSERT INTO resilience_governance_trust_lineage_pin_receipts (
                                    id,
                                    receipt_number,
                                    source_package_id,
                                    source_package_number,
                                    envelope_hash,
                                    package_hash,
                                    trusted_root_fingerprint,
                                    presented_root_fingerprint,
                                    verification_result,
                                    package_hash_valid,
                                    canonical_payload_valid,
                                    lineage_valid,
                                    root_pin_valid,
                                    transition_count,
                                    verified_transition_count,
                                    trust_anchor_count,
                                    message
                                )
                                VALUES (
                                    :id,
                                    :receiptNumber,
                                    :sourcePackageId,
                                    :sourcePackageNumber,
                                    :envelopeHash,
                                    :packageHash,
                                    :trustedRootFingerprint,
                                    :presentedRootFingerprint,
                                    :verificationResult,
                                    :packageHashValid,
                                    :canonicalPayloadValid,
                                    :lineageValid,
                                    :rootPinValid,
                                    :transitionCount,
                                    :verifiedTransitionCount,
                                    :trustAnchorCount,
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
                                "sourcePackageId",
                                verification.sourcePackageId(),
                                java.sql.Types.OTHER
                        )
                        .param(
                                "sourcePackageNumber",
                                verification.sourcePackageNumber(),
                                java.sql.Types.INTEGER
                        )
                        .param(
                                "envelopeHash",
                                verification.envelopeHash()
                        )
                        .param(
                                "packageHash",
                                verification.packageHash(),
                                java.sql.Types.VARCHAR
                        )
                        .param(
                                "trustedRootFingerprint",
                                verification.trustedRootFingerprint()
                        )
                        .param(
                                "presentedRootFingerprint",
                                verification.presentedRootFingerprint(),
                                java.sql.Types.VARCHAR
                        )
                        .param(
                                "verificationResult",
                                verification.valid()
                                        ? "VALID"
                                        : "INVALID"
                        )
                        .param(
                                "packageHashValid",
                                verification.packageHashValid()
                        )
                        .param(
                                "canonicalPayloadValid",
                                verification.canonicalPayloadValid()
                        )
                        .param(
                                "lineageValid",
                                verification.lineageValid()
                        )
                        .param(
                                "rootPinValid",
                                verification.rootPinValid()
                        )
                        .param(
                                "transitionCount",
                                verification.transitionCount()
                        )
                        .param(
                                "verifiedTransitionCount",
                                verification.verifiedTransitionCount()
                        )
                        .param(
                                "trustAnchorCount",
                                verification.trustAnchorCount()
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
        ).orElseThrow(() ->
                new IllegalStateException(
                        "Pinned trust-lineage verification receipt disappeared after persistence."
                )
        );
    }

    public List<GovernanceTrustLineagePinReceipt> findHistory() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_trust_lineage_pin_receipts

                        ORDER BY
                            receipt_number DESC,
                            verified_at DESC,
                            id DESC
                        """)
                .query(
                        this::map
                )
                .list();
    }

    private Optional<GovernanceTrustLineagePinReceipt> findById(
            UUID id
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_trust_lineage_pin_receipts

                        WHERE id =
                              :id
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

    private GovernanceTrustLineagePinReceipt map(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new GovernanceTrustLineagePinReceipt(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getInt(
                        "receipt_number"
                ),

                resultSet.getObject(
                        "source_package_id",
                        UUID.class
                ),

                (Integer) resultSet.getObject(
                        "source_package_number"
                ),

                resultSet.getString(
                        "envelope_hash"
                ),

                resultSet.getString(
                        "package_hash"
                ),

                resultSet.getString(
                        "trusted_root_fingerprint"
                ),

                resultSet.getString(
                        "presented_root_fingerprint"
                ),

                resultSet.getString(
                        "verification_result"
                ),

                resultSet.getBoolean(
                        "package_hash_valid"
                ),

                resultSet.getBoolean(
                        "canonical_payload_valid"
                ),

                resultSet.getBoolean(
                        "lineage_valid"
                ),

                resultSet.getBoolean(
                        "root_pin_valid"
                ),

                resultSet.getInt(
                        "transition_count"
                ),

                resultSet.getInt(
                        "verified_transition_count"
                ),

                resultSet.getInt(
                        "trust_anchor_count"
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
