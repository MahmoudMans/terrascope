package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptLedgerEntry;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class GovernanceOfflineTrustReceiptLedgerRepository {

    private static final String COLUMNS = """
            id,
            ledger_number,
            receipt_id,
            receipt_import_number,
            verification_result,
            receipt_hash,
            receipt_payload_hash,
            previous_ledger_hash,
            ledger_version,
            ledger_hash,
            created_at
            """;

    private final JdbcClient jdbcClient;

    public GovernanceOfflineTrustReceiptLedgerRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public int nextLedgerNumber() {
        return jdbcClient
                .sql("""
                        SELECT
                            COALESCE(
                                MAX(ledger_number),
                                0
                            ) + 1

                        FROM resilience_governance_offline_trust_receipt_ledger
                        """)
                .query(
                        Integer.class
                )
                .single();
    }

    public Optional<GovernanceOfflineTrustReceiptLedgerEntry> findByReceiptId(
            UUID receiptId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_offline_trust_receipt_ledger

                        WHERE receipt_id =
                              :receiptId
                        """)
                .param(
                        "receiptId",
                        receiptId
                )
                .query(
                        this::map
                )
                .optional();
    }

    public Optional<GovernanceOfflineTrustReceiptLedgerEntry> findLatest() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_offline_trust_receipt_ledger

                        ORDER BY
                            ledger_number DESC

                        LIMIT 1
                        """)
                .query(
                        this::map
                )
                .optional();
    }

    public GovernanceOfflineTrustReceiptLedgerEntry save(
            UUID id,

            int ledgerNumber,

            UUID receiptId,

            int receiptImportNumber,

            String verificationResult,

            String receiptHash,

            String receiptPayloadHash,

            String previousLedgerHash,

            String ledgerVersion,

            String ledgerHash
    ) {
        UUID persistedId =
                jdbcClient
                        .sql("""
                                INSERT INTO resilience_governance_offline_trust_receipt_ledger (
                                    id,
                                    ledger_number,
                                    receipt_id,
                                    receipt_import_number,
                                    verification_result,
                                    receipt_hash,
                                    receipt_payload_hash,
                                    previous_ledger_hash,
                                    ledger_version,
                                    ledger_hash
                                )
                                VALUES (
                                    :id,
                                    :ledgerNumber,
                                    :receiptId,
                                    :receiptImportNumber,
                                    :verificationResult,
                                    :receiptHash,
                                    :receiptPayloadHash,
                                    :previousLedgerHash,
                                    :ledgerVersion,
                                    :ledgerHash
                                )
                                RETURNING id
                                """)
                        .param(
                                "id",
                                id
                        )
                        .param(
                                "ledgerNumber",
                                ledgerNumber
                        )
                        .param(
                                "receiptId",
                                receiptId
                        )
                        .param(
                                "receiptImportNumber",
                                receiptImportNumber
                        )
                        .param(
                                "verificationResult",
                                verificationResult
                        )
                        .param(
                                "receiptHash",
                                receiptHash
                        )
                        .param(
                                "receiptPayloadHash",
                                receiptPayloadHash
                        )
                        .param(
                                "previousLedgerHash",
                                previousLedgerHash,
                                java.sql.Types.VARCHAR
                        )
                        .param(
                                "ledgerVersion",
                                ledgerVersion
                        )
                        .param(
                                "ledgerHash",
                                ledgerHash
                        )
                        .query(
                                UUID.class
                        )
                        .single();

        return findById(
                persistedId
        ).orElseThrow(() ->
                new IllegalStateException(
                        "Offline trust receipt ledger entry disappeared after persistence."
                )
        );
    }

    public List<GovernanceOfflineTrustReceiptLedgerEntry> findChain() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_offline_trust_receipt_ledger

                        ORDER BY
                            ledger_number ASC
                        """)
                .query(
                        this::map
                )
                .list();
    }

    public List<GovernanceOfflineTrustReceiptLedgerEntry> findHistory() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_offline_trust_receipt_ledger

                        ORDER BY
                            ledger_number DESC
                        """)
                .query(
                        this::map
                )
                .list();
    }

    private Optional<GovernanceOfflineTrustReceiptLedgerEntry> findById(
            UUID id
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_offline_trust_receipt_ledger

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

    private GovernanceOfflineTrustReceiptLedgerEntry map(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new GovernanceOfflineTrustReceiptLedgerEntry(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getInt(
                        "ledger_number"
                ),

                resultSet.getObject(
                        "receipt_id",
                        UUID.class
                ),

                resultSet.getInt(
                        "receipt_import_number"
                ),

                resultSet.getString(
                        "verification_result"
                ),

                resultSet.getString(
                        "receipt_hash"
                ),

                resultSet.getString(
                        "receipt_payload_hash"
                ),

                resultSet.getString(
                        "previous_ledger_hash"
                ),

                resultSet.getString(
                        "ledger_version"
                ),

                resultSet.getString(
                        "ledger_hash"
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
