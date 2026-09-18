package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.GovernanceAssuranceBundle;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class GovernanceAssuranceBundleRepository {

    private static final String BUNDLE_COLUMNS = """
            id,
            bundle_number,
            certificate_id,
            certificate_number,
            bundle_version,
            bundle_hash,
            signature_count,
            trust_anchor_count,
            retired_anchor_count,
            lifecycle_event_count,
            portable_verification_valid,
            payload_json,
            created_at
            """;

    private final JdbcClient jdbcClient;

    public GovernanceAssuranceBundleRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public int nextBundleNumber() {
        return jdbcClient
                .sql("""
                        SELECT
                            COALESCE(
                                MAX(bundle_number),
                                0
                            ) + 1

                        FROM resilience_governance_assurance_bundles
                        """)
                .query(
                        Integer.class
                )
                .single();
    }

    public GovernanceAssuranceBundle save(
            UUID id,
            int bundleNumber,

            UUID certificateId,
            int certificateNumber,

            String bundleVersion,
            String bundleHash,

            int signatureCount,
            int trustAnchorCount,
            int retiredAnchorCount,
            int lifecycleEventCount,

            boolean portableVerificationValid,

            String payloadJson
    ) {
        UUID bundleId =
                jdbcClient
                        .sql("""
                                INSERT INTO resilience_governance_assurance_bundles (
                                    id,
                                    bundle_number,
                                    certificate_id,
                                    certificate_number,
                                    bundle_version,
                                    bundle_hash,
                                    signature_count,
                                    trust_anchor_count,
                                    retired_anchor_count,
                                    lifecycle_event_count,
                                    portable_verification_valid,
                                    payload_json
                                )
                                VALUES (
                                    :id,
                                    :bundleNumber,
                                    :certificateId,
                                    :certificateNumber,
                                    :bundleVersion,
                                    :bundleHash,
                                    :signatureCount,
                                    :trustAnchorCount,
                                    :retiredAnchorCount,
                                    :lifecycleEventCount,
                                    :portableVerificationValid,
                                    :payloadJson
                                )
                                RETURNING id
                                """)
                        .param(
                                "id",
                                id
                        )
                        .param(
                                "bundleNumber",
                                bundleNumber
                        )
                        .param(
                                "certificateId",
                                certificateId
                        )
                        .param(
                                "certificateNumber",
                                certificateNumber
                        )
                        .param(
                                "bundleVersion",
                                bundleVersion
                        )
                        .param(
                                "bundleHash",
                                bundleHash
                        )
                        .param(
                                "signatureCount",
                                signatureCount
                        )
                        .param(
                                "trustAnchorCount",
                                trustAnchorCount
                        )
                        .param(
                                "retiredAnchorCount",
                                retiredAnchorCount
                        )
                        .param(
                                "lifecycleEventCount",
                                lifecycleEventCount
                        )
                        .param(
                                "portableVerificationValid",
                                portableVerificationValid
                        )
                        .param(
                                "payloadJson",
                                payloadJson
                        )
                        .query(
                                UUID.class
                        )
                        .single();

        return findById(
                bundleId
        ).orElseThrow(() ->
                new IllegalStateException(
                        "Governance assurance bundle disappeared after persistence."
                )
        );
    }

    public Optional<GovernanceAssuranceBundle> findById(
            UUID bundleId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + BUNDLE_COLUMNS
                        + """
                        FROM resilience_governance_assurance_bundles

                        WHERE id =
                              :bundleId
                        """)
                .param(
                        "bundleId",
                        bundleId
                )
                .query(
                        this::mapBundle
                )
                .optional();
    }

    public List<GovernanceAssuranceBundle> findHistory() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + BUNDLE_COLUMNS
                        + """
                        FROM resilience_governance_assurance_bundles

                        ORDER BY
                            bundle_number DESC,
                            created_at DESC
                        """)
                .query(
                        this::mapBundle
                )
                .list();
    }

    private GovernanceAssuranceBundle mapBundle(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new GovernanceAssuranceBundle(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getInt(
                        "bundle_number"
                ),

                resultSet.getObject(
                        "certificate_id",
                        UUID.class
                ),

                resultSet.getInt(
                        "certificate_number"
                ),

                resultSet.getString(
                        "bundle_version"
                ),

                resultSet.getString(
                        "bundle_hash"
                ),

                resultSet.getInt(
                        "signature_count"
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

                resultSet.getBoolean(
                        "portable_verification_valid"
                ),

                resultSet.getString(
                        "payload_json"
                ),

                resultSet
                        .getObject(
                                "created_at",
                                OffsetDateTime.class
                        )
                        .toInstant()
        );
    }
}
