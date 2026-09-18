package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.GovernanceAssuranceReceiptCheckpoint;
import com.terrascope.platform.domain.GovernanceAssuranceReceiptCheckpointMaterial;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class GovernanceAssuranceReceiptCheckpointRepository {

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

    public GovernanceAssuranceReceiptCheckpointRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public int nextCheckpointNumber() {
        return GovernanceTransactionalSequenceAllocator.next(
                jdbcClient,
                "GOV_ASSURANCE_RECEIPT_CHECKPOINTS_CHECKPOINT_NUMBER_V1"
        );
    }

    public Optional<GovernanceAssuranceReceiptCheckpoint> findLatest() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_assurance_receipt_checkpoints

                        ORDER BY
                            checkpoint_number DESC,
                            created_at DESC,
                            id DESC

                        LIMIT 1
                        """)
                .query(
                        this::map
                )
                .optional();
    }

    public boolean existsForLedgerAndKey(
            int ledgerNumber,
            String keyId
    ) {
        return jdbcClient
                .sql("""
                        SELECT COUNT(*)

                        FROM resilience_governance_assurance_receipt_checkpoints

                        WHERE covered_ledger_number =
                              :ledgerNumber

                          AND key_id =
                              :keyId
                        """)
                .param(
                        "ledgerNumber",
                        ledgerNumber
                )
                .param(
                        "keyId",
                        keyId
                )
                .query(
                        Integer.class
                )
                .single()
                > 0;
    }

    public GovernanceAssuranceReceiptCheckpoint save(
            UUID id,

            GovernanceAssuranceReceiptCheckpointMaterial material,

            String checkpointVersion,
            String signatureBase64,

            String checkpointHash
    ) {
        UUID checkpointId =
                jdbcClient
                        .sql("""
                                INSERT INTO resilience_governance_assurance_receipt_checkpoints (
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
                                    checkpoint_hash
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
                                    :checkpointHash
                                )
                                RETURNING id
                                """)
                        .param(
                                "id",
                                id
                        )
                        .param(
                                "checkpointNumber",
                                material.checkpointNumber()
                        )
                        .param(
                                "coveredLedgerNumber",
                                material.coveredLedgerNumber()
                        )
                        .param(
                                "coveredLedgerHash",
                                material.coveredLedgerHash()
                        )
                        .param(
                                "entryCount",
                                material.entryCount()
                        )
                        .param(
                                "validReceiptCount",
                                material.validReceiptCount()
                        )
                        .param(
                                "invalidReceiptCount",
                                material.invalidReceiptCount()
                        )
                        .param(
                                "keyId",
                                material.keyId()
                        )
                        .param(
                                "algorithm",
                                material.algorithm()
                        )
                        .param(
                                "publicKeyFingerprint",
                                material.publicKeyFingerprint()
                        )
                        .param(
                                "checkpointVersion",
                                checkpointVersion
                        )
                        .param(
                                "signatureBase64",
                                signatureBase64
                        )
                        .param(
                                "previousCheckpointHash",
                                material.previousCheckpointHash(),
                                java.sql.Types.VARCHAR
                        )
                        .param(
                                "checkpointHash",
                                checkpointHash
                        )
                        .query(
                                UUID.class
                        )
                        .single();

        return findById(
                checkpointId
        ).orElseThrow(() ->
                new IllegalStateException(
                        "Receipt-ledger checkpoint disappeared after persistence."
                )
        );
    }

    public List<GovernanceAssuranceReceiptCheckpoint> findHistory() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_assurance_receipt_checkpoints

                        ORDER BY
                            checkpoint_number DESC,
                            created_at DESC,
                            id DESC
                        """)
                .query(
                        this::map
                )
                .list();
    }

    public List<GovernanceAssuranceReceiptCheckpoint> findChain() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_assurance_receipt_checkpoints

                        ORDER BY
                            checkpoint_number ASC,
                            created_at ASC,
                            id ASC
                        """)
                .query(
                        this::map
                )
                .list();
    }

    private Optional<GovernanceAssuranceReceiptCheckpoint> findById(
            UUID id
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_assurance_receipt_checkpoints

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

    private GovernanceAssuranceReceiptCheckpoint map(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new GovernanceAssuranceReceiptCheckpoint(
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

                resultSet
                        .getObject(
                                "created_at",
                                OffsetDateTime.class
                        )
                        .toInstant()
        );
    }
}
