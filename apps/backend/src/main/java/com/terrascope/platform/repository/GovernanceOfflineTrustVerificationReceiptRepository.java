package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.GovernanceOfflineTrustVerificationReceipt;
import com.terrascope.platform.api.model.GovernanceTrustLineagePinnedVerificationResponse;
import com.terrascope.platform.domain.GovernanceOfflineTrustVerificationReceiptDocument;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class GovernanceOfflineTrustVerificationReceiptRepository {

    private static final String COLUMNS = """
            id,
            import_number,
            verifier_version,
            receipt_hash,
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
            root_key_id,
            terminal_key_id,
            offline_verified_at,
            imported_at
            """;

    private final JdbcClient jdbcClient;

    public GovernanceOfflineTrustVerificationReceiptRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<GovernanceOfflineTrustVerificationReceipt> findByReceiptHash(
            String receiptHash
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_offline_trust_verification_receipts

                        WHERE receipt_hash =
                              :receiptHash
                        """)
                .param(
                        "receiptHash",
                        receiptHash
                )
                .query(
                        this::map
                )
                .optional();
    }

    public GovernanceOfflineTrustVerificationReceipt save(
            UUID id,

            int importNumber,

            GovernanceOfflineTrustVerificationReceiptDocument document,

            GovernanceTrustLineagePinnedVerificationResponse verification
    ) {
        UUID sourcePackageId =
                document.sourcePackageId() == null
                        ? null
                        : UUID.fromString(
                                document.sourcePackageId()
                        );

        Instant offlineVerifiedAt =
                Instant.parse(
                        document.verifiedAt()
                );

        UUID importedId =
                jdbcClient
                        .sql("""
                                INSERT INTO resilience_governance_offline_trust_verification_receipts (
                                    id,
                                    import_number,
                                    verifier_version,
                                    receipt_hash,
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
                                    root_key_id,
                                    terminal_key_id,
                                    offline_verified_at
                                )
                                VALUES (
                                    :id,
                                    :importNumber,
                                    :verifierVersion,
                                    :receiptHash,
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
                                    :rootKeyId,
                                    :terminalKeyId,
                                    :offlineVerifiedAt
                                )
                                RETURNING id
                                """)
                        .param(
                                "id",
                                id
                        )
                        .param(
                                "importNumber",
                                importNumber
                        )
                        .param(
                                "verifierVersion",
                                document.verifierVersion()
                        )
                        .param(
                                "receiptHash",
                                document.receiptHash()
                        )
                        .param(
                                "sourcePackageId",
                                sourcePackageId,
                                java.sql.Types.OTHER
                        )
                        .param(
                                "sourcePackageNumber",
                                document.sourcePackageNumber(),
                                java.sql.Types.INTEGER
                        )
                        .param(
                                "envelopeHash",
                                document.envelopeHash()
                        )
                        .param(
                                "packageHash",
                                document.packageHash(),
                                java.sql.Types.VARCHAR
                        )
                        .param(
                                "trustedRootFingerprint",
                                document.trustedRootFingerprint()
                        )
                        .param(
                                "presentedRootFingerprint",
                                document.presentedRootFingerprint(),
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
                                "rootKeyId",
                                verification.rootKeyId(),
                                java.sql.Types.VARCHAR
                        )
                        .param(
                                "terminalKeyId",
                                verification.terminalKeyId(),
                                java.sql.Types.VARCHAR
                        )
                        .param(
                                "offlineVerifiedAt",

                                OffsetDateTime.ofInstant(
                                        offlineVerifiedAt,
                                        ZoneOffset.UTC
                                )
                        )
                        .query(
                                UUID.class
                        )
                        .single();

        return findById(
                importedId
        ).orElseThrow(() ->
                new IllegalStateException(
                        "Imported offline trust receipt disappeared after persistence."
                )
        );
    }

    public List<GovernanceOfflineTrustVerificationReceipt> findHistory() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_offline_trust_verification_receipts

                        ORDER BY
                            import_number DESC,
                            imported_at DESC,
                            id DESC
                        """)
                .query(
                        this::map
                )
                .list();
    }

    public Optional<GovernanceOfflineTrustVerificationReceipt> findById(
            UUID id
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_offline_trust_verification_receipts

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

    private GovernanceOfflineTrustVerificationReceipt map(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new GovernanceOfflineTrustVerificationReceipt(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getInt(
                        "import_number"
                ),

                resultSet.getString(
                        "verifier_version"
                ),

                resultSet.getString(
                        "receipt_hash"
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
                        "root_key_id"
                ),

                resultSet.getString(
                        "terminal_key_id"
                ),

                resultSet
                        .getObject(
                                "offline_verified_at",
                                OffsetDateTime.class
                        )
                        .toInstant(),

                resultSet
                        .getObject(
                                "imported_at",
                                OffsetDateTime.class
                        )
                        .toInstant()
        );
    }
}
