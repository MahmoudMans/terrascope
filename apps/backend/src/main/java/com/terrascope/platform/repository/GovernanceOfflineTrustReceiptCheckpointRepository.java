package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptCheckpoint;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class GovernanceOfflineTrustReceiptCheckpointRepository {

    public static final String NUMBER_ALLOCATOR =
            "OFFLINE_TRUST_RECEIPT_CHECKPOINT_NUMBER_V1";

    private static final String COLUMNS = """
            id,
            checkpoint_number,
            covered_ledger_number,
            covered_ledger_hash,
            entry_count,
            valid_receipt_count,
            invalid_receipt_count,
            key_id,
            algorithm,
            public_key_fingerprint,
            checkpoint_version,
            signature_base64,
            previous_checkpoint_hash,
            checkpoint_hash,
            created_at
            """;

    private final JdbcClient jdbcClient;

    public GovernanceOfflineTrustReceiptCheckpointRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public int nextCheckpointNumber() {
        return GovernanceTransactionalSequenceAllocator.next(
                jdbcClient,
                NUMBER_ALLOCATOR
        );
    }

    public Optional<GovernanceOfflineTrustReceiptCheckpoint> findLatest() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_offline_trust_receipt_checkpoints

                        ORDER BY
                            checkpoint_number DESC

                        LIMIT 1
                        """)
                .query(
                        this::map
                )
                .optional();
    }

    public Optional<GovernanceOfflineTrustReceiptCheckpoint> findByCoveredLedgerAndKey(
            int coveredLedgerNumber,
            String keyId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_offline_trust_receipt_checkpoints

                        WHERE covered_ledger_number =
                              :coveredLedgerNumber

                          AND key_id =
                              :keyId
                        """)
                .param(
                        "coveredLedgerNumber",
                        coveredLedgerNumber
                )
                .param(
                        "keyId",
                        keyId
                )
                .query(
                        this::map
                )
                .optional();
    }

    public GovernanceOfflineTrustReceiptCheckpoint save(
            GovernanceOfflineTrustReceiptCheckpoint checkpoint
    ) {
        jdbcClient
                .sql("""
                        INSERT INTO resilience_governance_offline_trust_receipt_checkpoints (
                            id,
                            checkpoint_number,
                            covered_ledger_number,
                            covered_ledger_hash,
                            entry_count,
                            valid_receipt_count,
                            invalid_receipt_count,
                            key_id,
                            algorithm,
                            public_key_fingerprint,
                            checkpoint_version,
                            signature_base64,
                            previous_checkpoint_hash,
                            checkpoint_hash,
                            created_at
                        )
                        VALUES (
                            :id,
                            :checkpointNumber,
                            :coveredLedgerNumber,
                            :coveredLedgerHash,
                            :entryCount,
                            :validReceiptCount,
                            :invalidReceiptCount,
                            :keyId,
                            :algorithm,
                            :publicKeyFingerprint,
                            :checkpointVersion,
                            :signatureBase64,
                            :previousCheckpointHash,
                            :checkpointHash,
                            :createdAt
                        )
                        """)
                .param(
                        "id",
                        checkpoint.id()
                )
                .param(
                        "checkpointNumber",
                        checkpoint.checkpointNumber()
                )
                .param(
                        "coveredLedgerNumber",
                        checkpoint.coveredLedgerNumber()
                )
                .param(
                        "coveredLedgerHash",
                        checkpoint.coveredLedgerHash()
                )
                .param(
                        "entryCount",
                        checkpoint.entryCount()
                )
                .param(
                        "validReceiptCount",
                        checkpoint.validReceiptCount()
                )
                .param(
                        "invalidReceiptCount",
                        checkpoint.invalidReceiptCount()
                )
                .param(
                        "keyId",
                        checkpoint.keyId()
                )
                .param(
                        "algorithm",
                        checkpoint.algorithm()
                )
                .param(
                        "publicKeyFingerprint",
                        checkpoint.publicKeyFingerprint()
                )
                .param(
                        "checkpointVersion",
                        checkpoint.checkpointVersion()
                )
                .param(
                        "signatureBase64",
                        checkpoint.signatureBase64()
                )
                .param(
                        "previousCheckpointHash",
                        checkpoint.previousCheckpointHash(),
                        Types.VARCHAR
                )
                .param(
                        "checkpointHash",
                        checkpoint.checkpointHash()
                )
                .param(
                        "createdAt",

                        OffsetDateTime.ofInstant(
                                checkpoint.createdAt(),
                                ZoneOffset.UTC
                        )
                )
                .update();

        return findById(
                checkpoint.id()
        ).orElseThrow(() ->
                new IllegalStateException(
                        "Offline receipt checkpoint disappeared after persistence."
                )
        );
    }

    public Optional<GovernanceOfflineTrustReceiptCheckpoint> findById(
            UUID id
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_offline_trust_receipt_checkpoints

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

    public List<GovernanceOfflineTrustReceiptCheckpoint> findChain() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_offline_trust_receipt_checkpoints

                        ORDER BY
                            checkpoint_number ASC
                        """)
                .query(
                        this::map
                )
                .list();
    }

    public List<GovernanceOfflineTrustReceiptCheckpoint> findHistory() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_offline_trust_receipt_checkpoints

                        ORDER BY
                            checkpoint_number DESC
                        """)
                .query(
                        this::map
                )
                .list();
    }

    public Optional<TrustAnchorEvidence> findTrustAnchor(
            String keyId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                            key_id,
                            algorithm,
                            public_key_x509_base64,
                            public_key_fingerprint,
                            status,
                            created_at,
                            retired_at

                        FROM resilience_governance_trust_anchors

                        WHERE key_id =
                              :keyId
                        """)
                .param(
                        "keyId",
                        keyId
                )
                .query(
                        (
                                resultSet,
                                rowNumber
                        ) ->
                                new TrustAnchorEvidence(
                                        resultSet.getString(
                                                "key_id"
                                        ),

                                        resultSet.getString(
                                                "algorithm"
                                        ),

                                        resultSet.getString(
                                                "public_key_x509_base64"
                                        ),

                                        resultSet.getString(
                                                "public_key_fingerprint"
                                        ),

                                        resultSet.getString(
                                                "status"
                                        ),

                                        instant(
                                                resultSet,
                                                "created_at"
                                        ),

                                        nullableInstant(
                                                resultSet,
                                                "retired_at"
                                        )
                                )
                )
                .optional();
    }

    private GovernanceOfflineTrustReceiptCheckpoint map(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new GovernanceOfflineTrustReceiptCheckpoint(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getInt(
                        "checkpoint_number"
                ),

                resultSet.getInt(
                        "covered_ledger_number"
                ),

                resultSet.getString(
                        "covered_ledger_hash"
                ),

                resultSet.getInt(
                        "entry_count"
                ),

                resultSet.getInt(
                        "valid_receipt_count"
                ),

                resultSet.getInt(
                        "invalid_receipt_count"
                ),

                resultSet.getString(
                        "key_id"
                ),

                resultSet.getString(
                        "algorithm"
                ),

                resultSet.getString(
                        "public_key_fingerprint"
                ),

                resultSet.getString(
                        "checkpoint_version"
                ),

                resultSet.getString(
                        "signature_base64"
                ),

                resultSet.getString(
                        "previous_checkpoint_hash"
                ),

                resultSet.getString(
                        "checkpoint_hash"
                ),

                instant(
                        resultSet,
                        "created_at"
                )
        );
    }

    private Instant instant(
            ResultSet resultSet,
            String column
    ) throws SQLException {
        return resultSet
                .getObject(
                        column,
                        OffsetDateTime.class
                )
                .toInstant();
    }

    private Instant nullableInstant(
            ResultSet resultSet,
            String column
    ) throws SQLException {
        OffsetDateTime value =
                resultSet.getObject(
                        column,
                        OffsetDateTime.class
                );

        return value == null
                ? null
                : value.toInstant();
    }

    public record TrustAnchorEvidence(
            String keyId,

            String algorithm,

            String publicKeyX509Base64,

            String publicKeyFingerprint,

            String status,

            Instant createdAt,

            Instant retiredAt
    ) {
    }
}
