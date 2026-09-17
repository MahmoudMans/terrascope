package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.ResilienceAuditReceipt;
import com.terrascope.platform.domain.ResilienceAuditLedgerMaterial;
import com.terrascope.platform.domain.ResilienceEvidenceAnchor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ResilienceAuditLedgerRepository {

    private static final String RECEIPT_COLUMNS = """
            id,
            incident_id,
            receipt_number,
            evidence_package_id,
            evidence_package_number,
            package_manifest_version,
            package_manifest_hash,
            previous_receipt_hash,
            ledger_version,
            ledger_hash,
            created_at
            """;

    private final JdbcClient jdbcClient;

    public ResilienceAuditLedgerRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public void lockIncident(
            UUID incidentId
    ) {
        jdbcClient
                .sql("""
                        SELECT id
                        FROM incident_cases
                        WHERE id = :incidentId
                        FOR UPDATE
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .query(
                        UUID.class
                )
                .single();
    }

    public Optional<ResilienceEvidenceAnchor> findLatestEvidencePackage(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                            id,
                            package_number,
                            manifest_version,
                            manifest_hash

                        FROM incident_resilience_evidence_packages

                        WHERE incident_id = :incidentId

                        ORDER BY
                            package_number DESC,
                            created_at DESC,
                            id DESC

                        LIMIT 1
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .query(
                        this::mapEvidenceAnchor
                )
                .optional();
    }

    public Optional<ResilienceEvidenceAnchor> findEvidencePackageById(
            UUID incidentId,
            UUID evidencePackageId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                            id,
                            package_number,
                            manifest_version,
                            manifest_hash

                        FROM incident_resilience_evidence_packages

                        WHERE incident_id = :incidentId
                          AND id = :evidencePackageId
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .param(
                        "evidencePackageId",
                        evidencePackageId
                )
                .query(
                        this::mapEvidenceAnchor
                )
                .optional();
    }

    public Optional<ResilienceAuditReceipt> findLatestReceipt(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + RECEIPT_COLUMNS
                        + """
                        FROM incident_resilience_audit_receipts
                        WHERE incident_id = :incidentId
                        ORDER BY
                            receipt_number DESC,
                            created_at DESC,
                            id DESC
                        LIMIT 1
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .query(
                        this::mapReceipt
                )
                .optional();
    }

    public boolean receiptExistsForPackage(
            UUID incidentId,
            UUID evidencePackageId
    ) {
        Integer count =
                jdbcClient
                        .sql("""
                                SELECT COUNT(*)
                                FROM incident_resilience_audit_receipts
                                WHERE incident_id = :incidentId
                                  AND evidence_package_id =
                                      :evidencePackageId
                                """)
                        .param(
                                "incidentId",
                                incidentId
                        )
                        .param(
                                "evidencePackageId",
                                evidencePackageId
                        )
                        .query(
                                Integer.class
                        )
                        .single();

        return count != null
                && count > 0;
    }

    public int nextReceiptNumber(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                            COALESCE(
                                MAX(receipt_number),
                                0
                            ) + 1

                        FROM incident_resilience_audit_receipts

                        WHERE incident_id = :incidentId
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .query(
                        Integer.class
                )
                .single();
    }

    public ResilienceAuditReceipt saveReceipt(
            UUID id,
            ResilienceAuditLedgerMaterial material,
            String ledgerVersion,
            String ledgerHash
    ) {
        UUID receiptId =
                jdbcClient
                        .sql("""
                                INSERT INTO incident_resilience_audit_receipts (
                                    id,
                                    incident_id,
                                    receipt_number,
                                    evidence_package_id,
                                    evidence_package_number,
                                    package_manifest_version,
                                    package_manifest_hash,
                                    previous_receipt_hash,
                                    ledger_version,
                                    ledger_hash
                                )
                                VALUES (
                                    :id,
                                    :incidentId,
                                    :receiptNumber,
                                    :evidencePackageId,
                                    :evidencePackageNumber,
                                    :packageManifestVersion,
                                    :packageManifestHash,
                                    :previousReceiptHash,
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
                                "incidentId",
                                material.incidentId()
                        )
                        .param(
                                "receiptNumber",
                                material.receiptNumber()
                        )
                        .param(
                                "evidencePackageId",
                                material.evidencePackageId()
                        )
                        .param(
                                "evidencePackageNumber",
                                material.evidencePackageNumber()
                        )
                        .param(
                                "packageManifestVersion",
                                material.packageManifestVersion()
                        )
                        .param(
                                "packageManifestHash",
                                material.packageManifestHash()
                        )
                        .param(
                                "previousReceiptHash",
                                material.previousReceiptHash(),
                                Types.VARCHAR
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

        return findReceiptById(
                material.incidentId(),
                receiptId
        ).orElseThrow(() ->
                new IllegalStateException(
                        "Audit receipt disappeared after creation."
                )
        );
    }

    public List<ResilienceAuditReceipt> findHistory(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + RECEIPT_COLUMNS
                        + """
                        FROM incident_resilience_audit_receipts
                        WHERE incident_id = :incidentId
                        ORDER BY
                            receipt_number DESC,
                            created_at DESC
                        LIMIT 50
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .query(
                        this::mapReceipt
                )
                .list();
    }

    public List<ResilienceAuditReceipt> findChain(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + RECEIPT_COLUMNS
                        + """
                        FROM incident_resilience_audit_receipts
                        WHERE incident_id = :incidentId
                        ORDER BY
                            receipt_number ASC,
                            created_at ASC,
                            id ASC
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .query(
                        this::mapReceipt
                )
                .list();
    }

    private Optional<ResilienceAuditReceipt> findReceiptById(
            UUID incidentId,
            UUID receiptId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + RECEIPT_COLUMNS
                        + """
                        FROM incident_resilience_audit_receipts
                        WHERE incident_id = :incidentId
                          AND id = :receiptId
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .param(
                        "receiptId",
                        receiptId
                )
                .query(
                        this::mapReceipt
                )
                .optional();
    }

    private ResilienceEvidenceAnchor mapEvidenceAnchor(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new ResilienceEvidenceAnchor(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getInt(
                        "package_number"
                ),

                resultSet.getString(
                        "manifest_version"
                ),

                resultSet.getString(
                        "manifest_hash"
                )
        );
    }

    private ResilienceAuditReceipt mapReceipt(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new ResilienceAuditReceipt(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getObject(
                        "incident_id",
                        UUID.class
                ),

                resultSet.getInt(
                        "receipt_number"
                ),

                resultSet.getObject(
                        "evidence_package_id",
                        UUID.class
                ),

                resultSet.getInt(
                        "evidence_package_number"
                ),

                resultSet.getString(
                        "package_manifest_version"
                ),

                resultSet.getString(
                        "package_manifest_hash"
                ),

                resultSet.getString(
                        "previous_receipt_hash"
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
