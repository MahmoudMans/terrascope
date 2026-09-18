package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.GovernanceTrustLineagePackage;
import com.terrascope.platform.domain.GovernanceTrustLineagePortableTrustAnchor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class GovernanceTrustLineagePackageRepository {

    private static final String PACKAGE_COLUMNS = """
            id,
            package_number,
            package_version,
            transition_count,
            trust_anchor_count,
            first_transition_number,
            last_transition_number,
            lineage_head_hash,
            payload_json,
            package_hash,
            created_at
            """;

    private final JdbcClient jdbcClient;

    public GovernanceTrustLineagePackageRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public int nextPackageNumber() {
        return GovernanceTransactionalSequenceAllocator.next(
                jdbcClient,
                "GOV_TRUST_LINEAGE_PACKAGES_PACKAGE_NUMBER_V1"
        );
    }

    public List<GovernanceTrustLineagePortableTrustAnchor> findPortableAnchors() {
        return jdbcClient
                .sql("""
                        SELECT
                            key_id,
                            algorithm,
                            public_key_x509_base64,
                            public_key_fingerprint,
                            status,
                            superseded_by_key_id,
                            retirement_reason,
                            created_at,
                            last_used_at,
                            retired_at

                        FROM resilience_governance_trust_anchors

                        WHERE key_id IN (
                            SELECT outgoing_key_id
                            FROM resilience_governance_trust_anchor_transitions

                            UNION

                            SELECT replacement_key_id
                            FROM resilience_governance_trust_anchor_transitions
                        )

                        ORDER BY
                            created_at ASC,
                            key_id ASC
                        """)
                .query(
                        this::mapAnchor
                )
                .list();
    }

    public Optional<GovernanceTrustLineagePackage> findByHash(
            String packageHash
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + PACKAGE_COLUMNS
                        + """
                        FROM resilience_governance_trust_lineage_packages

                        WHERE package_hash =
                              :packageHash
                        """)
                .param(
                        "packageHash",
                        packageHash
                )
                .query(
                        this::mapPackage
                )
                .optional();
    }

    public Optional<GovernanceTrustLineagePackage> findById(
            UUID id
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + PACKAGE_COLUMNS
                        + """
                        FROM resilience_governance_trust_lineage_packages

                        WHERE id =
                              :id
                        """)
                .param(
                        "id",
                        id
                )
                .query(
                        this::mapPackage
                )
                .optional();
    }

    public GovernanceTrustLineagePackage save(
            UUID id,

            int packageNumber,

            String packageVersion,

            int transitionCount,
            int trustAnchorCount,

            int firstTransitionNumber,
            int lastTransitionNumber,

            String lineageHeadHash,

            String payloadJson,
            String packageHash
    ) {
        UUID packageId =
                jdbcClient
                        .sql("""
                                INSERT INTO resilience_governance_trust_lineage_packages (
                                    id,
                                    package_number,
                                    package_version,
                                    transition_count,
                                    trust_anchor_count,
                                    first_transition_number,
                                    last_transition_number,
                                    lineage_head_hash,
                                    payload_json,
                                    package_hash
                                )
                                VALUES (
                                    :id,
                                    :packageNumber,
                                    :packageVersion,
                                    :transitionCount,
                                    :trustAnchorCount,
                                    :firstTransitionNumber,
                                    :lastTransitionNumber,
                                    :lineageHeadHash,
                                    :payloadJson,
                                    :packageHash
                                )
                                RETURNING id
                                """)
                        .param(
                                "id",
                                id
                        )
                        .param(
                                "packageNumber",
                                packageNumber
                        )
                        .param(
                                "packageVersion",
                                packageVersion
                        )
                        .param(
                                "transitionCount",
                                transitionCount
                        )
                        .param(
                                "trustAnchorCount",
                                trustAnchorCount
                        )
                        .param(
                                "firstTransitionNumber",
                                firstTransitionNumber
                        )
                        .param(
                                "lastTransitionNumber",
                                lastTransitionNumber
                        )
                        .param(
                                "lineageHeadHash",
                                lineageHeadHash
                        )
                        .param(
                                "payloadJson",
                                payloadJson
                        )
                        .param(
                                "packageHash",
                                packageHash
                        )
                        .query(
                                UUID.class
                        )
                        .single();

        return findById(
                packageId
        ).orElseThrow(() ->
                new IllegalStateException(
                        "Portable trust-lineage package disappeared after persistence."
                )
        );
    }

    public List<GovernanceTrustLineagePackage> findHistory() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + PACKAGE_COLUMNS
                        + """
                        FROM resilience_governance_trust_lineage_packages

                        ORDER BY
                            package_number DESC,
                            created_at DESC,
                            id DESC
                        """)
                .query(
                        this::mapPackage
                )
                .list();
    }

    private GovernanceTrustLineagePackage mapPackage(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new GovernanceTrustLineagePackage(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getInt(
                        "package_number"
                ),

                resultSet.getString(
                        "package_version"
                ),

                resultSet.getInt(
                        "transition_count"
                ),

                resultSet.getInt(
                        "trust_anchor_count"
                ),

                resultSet.getInt(
                        "first_transition_number"
                ),

                resultSet.getInt(
                        "last_transition_number"
                ),

                resultSet.getString(
                        "lineage_head_hash"
                ),

                resultSet.getString(
                        "payload_json"
                ),

                resultSet.getString(
                        "package_hash"
                ),

                resultSet
                        .getObject(
                                "created_at",
                                OffsetDateTime.class
                        )
                        .toInstant()
        );
    }

    private GovernanceTrustLineagePortableTrustAnchor mapAnchor(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new GovernanceTrustLineagePortableTrustAnchor(
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

                resultSet.getString(
                        "superseded_by_key_id"
                ),

                resultSet.getString(
                        "retirement_reason"
                ),

                instant(
                        resultSet,
                        "created_at"
                ),

                instant(
                        resultSet,
                        "last_used_at"
                ),

                instant(
                        resultSet,
                        "retired_at"
                )
        );
    }

    private java.time.Instant instant(
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
}
