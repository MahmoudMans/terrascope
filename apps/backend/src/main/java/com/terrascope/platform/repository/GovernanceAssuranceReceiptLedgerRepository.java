package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.GovernanceAssuranceReceiptLedgerEntry;
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
public class GovernanceAssuranceReceiptLedgerRepository {

    private static final String COLUMNS = """
            id,
            ledger_number,
            receipt_id,
            receipt_number,
            verification_result,
            envelope_hash,
            bundle_hash,
            receipt_payload_hash,
            previous_ledger_hash,
            ledger_version,
            ledger_hash,
            created_at
            """;

    private final JdbcClient jdbcClient;

    public GovernanceAssuranceReceiptLedgerRepository(
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

                        FROM resilience_governance_assurance_receipt_ledger
                        """)
                .query(
                        Integer.class
                )
                .single();
    }

    public Optional<GovernanceAssuranceReceiptLedgerEntry> findLatest() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_assurance_receipt_ledger

                        ORDER BY
                            ledger_number DESC,
                            created_at DESC,
                            id DESC

                        LIMIT 1
                        """)
                .query(
                        this::map
                )
                .optional();
    }

    public GovernanceAssuranceReceiptLedgerEntry save(
            UUID id,
            int ledgerNumber,

            GovernanceAssuranceVerificationReceipt receipt,

            String receiptPayloadHash,
            String previousLedgerHash,

            String ledgerVersion,
            String ledgerHash
    ) {
        UUID entryId =
                jdbcClient
                        .sql("""
                                INSERT INTO resilience_governance_assurance_receipt_ledger (
                                    id,
                                    ledger_number,
                                    receipt_id,
                                    receipt_number,
                                    verification_result,
                                    envelope_hash,
                                    bundle_hash,
                                    receipt_payload_hash,
                                    previous_ledger_hash,
                                    ledger_version,
                                    ledger_hash
                                )
                                VALUES (
                                    :id,
                                    :ledgerNumber,
                                    :receiptId,
                                    :receiptNumber,
                                    :verificationResult,
                                    :envelopeHash,
                                    :bundleHash,
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
                                receipt.id()
                        )
                        .param(
                                "receiptNumber",
                                receipt.receiptNumber()
                        )
                        .param(
                                "verificationResult",
                                receipt.verificationResult()
                        )
                        .param(
                                "envelopeHash",
                                receipt.envelopeHash()
                        )
                        .param(
                                "bundleHash",
                                receipt.bundleHash(),
                                java.sql.Types.VARCHAR
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
                entryId
        ).orElseThrow(() ->
                new IllegalStateException(
                        "Receipt ledger entry disappeared after persistence."
                )
        );
    }

    public List<GovernanceAssuranceReceiptLedgerEntry> findHistory() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_assurance_receipt_ledger

                        ORDER BY
                            ledger_number DESC,
                            created_at DESC,
                            id DESC
                        """)
                .query(
                        this::map
                )
                .list();
    }

    public List<GovernanceAssuranceReceiptLedgerEntry> findChain() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_assurance_receipt_ledger

                        ORDER BY
                            ledger_number ASC,
                            created_at ASC,
                            id ASC
                        """)
                .query(
                        this::map
                )
                .list();
    }

    private Optional<GovernanceAssuranceReceiptLedgerEntry> findById(
            UUID id
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_assurance_receipt_ledger
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

    private GovernanceAssuranceReceiptLedgerEntry map(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new GovernanceAssuranceReceiptLedgerEntry(
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
                        "receipt_number"
                ),

                resultSet.getString(
                        "verification_result"
                ),

                resultSet.getString(
                        "envelope_hash"
                ),

                resultSet.getString(
                        "bundle_hash"
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
