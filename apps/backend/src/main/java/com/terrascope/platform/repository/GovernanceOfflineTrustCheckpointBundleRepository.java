package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.GovernanceOfflineTrustCheckpointBundle;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;

import java.time.OffsetDateTime;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class GovernanceOfflineTrustCheckpointBundleRepository {

    public static final String NUMBER_ALLOCATOR =
            "OFFLINE_TRUST_CHECKPOINT_BUNDLE_NUMBER_V1";

    private static final String COLUMNS = """
            id,
            bundle_number,
            source_checkpoint_id,
            source_checkpoint_number,
            covered_ledger_number,
            checkpoint_hash,
            signing_key_id,
            signing_public_key_fingerprint,
            bundle_version,
            payload_json,
            bundle_hash,
            created_at
            """;

    private final JdbcClient jdbcClient;

    public GovernanceOfflineTrustCheckpointBundleRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public int nextBundleNumber() {
        return GovernanceTransactionalSequenceAllocator.next(
                jdbcClient,
                NUMBER_ALLOCATOR
        );
    }

    public Optional<GovernanceOfflineTrustCheckpointBundle> findBySourceCheckpointId(
            UUID sourceCheckpointId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_offline_trust_checkpoint_bundles

                        WHERE source_checkpoint_id =
                              :sourceCheckpointId
                        """)
                .param(
                        "sourceCheckpointId",
                        sourceCheckpointId
                )
                .query(
                        this::map
                )
                .optional();
    }

    public GovernanceOfflineTrustCheckpointBundle save(
            GovernanceOfflineTrustCheckpointBundle bundle
    ) {
        jdbcClient
                .sql("""
                        INSERT INTO resilience_governance_offline_trust_checkpoint_bundles (
                            id,
                            bundle_number,
                            source_checkpoint_id,
                            source_checkpoint_number,
                            covered_ledger_number,
                            checkpoint_hash,
                            signing_key_id,
                            signing_public_key_fingerprint,
                            bundle_version,
                            payload_json,
                            bundle_hash,
                            created_at
                        )
                        VALUES (
                            :id,
                            :bundleNumber,
                            :sourceCheckpointId,
                            :sourceCheckpointNumber,
                            :coveredLedgerNumber,
                            :checkpointHash,
                            :signingKeyId,
                            :signingPublicKeyFingerprint,
                            :bundleVersion,
                            :payloadJson,
                            :bundleHash,
                            :createdAt
                        )
                        """)
                .param(
                        "id",
                        bundle.id()
                )
                .param(
                        "bundleNumber",
                        bundle.bundleNumber()
                )
                .param(
                        "sourceCheckpointId",
                        bundle.sourceCheckpointId()
                )
                .param(
                        "sourceCheckpointNumber",
                        bundle.sourceCheckpointNumber()
                )
                .param(
                        "coveredLedgerNumber",
                        bundle.coveredLedgerNumber()
                )
                .param(
                        "checkpointHash",
                        bundle.checkpointHash()
                )
                .param(
                        "signingKeyId",
                        bundle.signingKeyId()
                )
                .param(
                        "signingPublicKeyFingerprint",
                        bundle.signingPublicKeyFingerprint()
                )
                .param(
                        "bundleVersion",
                        bundle.bundleVersion()
                )
                .param(
                        "payloadJson",
                        bundle.payloadJson()
                )
                .param(
                        "bundleHash",
                        bundle.bundleHash()
                )
                .param(
                        "createdAt",

                        OffsetDateTime.ofInstant(
                                bundle.createdAt(),
                                java.time.ZoneOffset.UTC
                        )
                )
                .update();

        return findById(
                bundle.id()
        ).orElseThrow(() ->
                new IllegalStateException(
                        "Portable offline trust checkpoint bundle disappeared after persistence."
                )
        );
    }

    public Optional<GovernanceOfflineTrustCheckpointBundle> findById(
            UUID id
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_offline_trust_checkpoint_bundles

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

    public List<GovernanceOfflineTrustCheckpointBundle> findHistory() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_offline_trust_checkpoint_bundles

                        ORDER BY
                            bundle_number DESC
                        """)
                .query(
                        this::map
                )
                .list();
    }

    private GovernanceOfflineTrustCheckpointBundle map(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new GovernanceOfflineTrustCheckpointBundle(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getInt(
                        "bundle_number"
                ),

                resultSet.getObject(
                        "source_checkpoint_id",
                        UUID.class
                ),

                resultSet.getInt(
                        "source_checkpoint_number"
                ),

                resultSet.getInt(
                        "covered_ledger_number"
                ),

                resultSet.getString(
                        "checkpoint_hash"
                ),

                resultSet.getString(
                        "signing_key_id"
                ),

                resultSet.getString(
                        "signing_public_key_fingerprint"
                ),

                resultSet.getString(
                        "bundle_version"
                ),

                resultSet.getString(
                        "payload_json"
                ),

                resultSet.getString(
                        "bundle_hash"
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
