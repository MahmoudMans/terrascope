package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.GovernanceTrustAnchorTransition;
import com.terrascope.platform.domain.GovernanceTrustAnchorTransitionMaterial;
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
public class GovernanceTrustAnchorTransitionRepository {

    private static final String COLUMNS = """
            id,
            transition_number,
            outgoing_key_id,
            outgoing_public_key_fingerprint,
            replacement_key_id,
            replacement_public_key_fingerprint,
            algorithm,
            reason,
            transition_version,
            signature_base64,
            previous_transition_hash,
            transition_hash,
            created_at
            """;

    private final JdbcClient jdbcClient;

    public GovernanceTrustAnchorTransitionRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public int nextTransitionNumber() {
        return GovernanceTransactionalSequenceAllocator.next(
                jdbcClient,
                "GOV_TRUST_ANCHOR_TRANSITIONS_TRANSITION_NUMBER_V1"
        );
    }

    public boolean existsForOutgoingKey(
            String outgoingKeyId
    ) {
        return jdbcClient
                .sql("""
                        SELECT COUNT(*)

                        FROM resilience_governance_trust_anchor_transitions

                        WHERE outgoing_key_id =
                              :outgoingKeyId
                        """)
                .param(
                        "outgoingKeyId",
                        outgoingKeyId
                )
                .query(
                        Integer.class
                )
                .single()
                > 0;
    }

    public Optional<GovernanceTrustAnchorTransition> findLatest() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_trust_anchor_transitions

                        ORDER BY
                            transition_number DESC,
                            created_at DESC,
                            id DESC

                        LIMIT 1
                        """)
                .query(
                        this::map
                )
                .optional();
    }

    public GovernanceTrustAnchorTransition save(
            UUID id,

            GovernanceTrustAnchorTransitionMaterial material,

            String transitionVersion,

            String signatureBase64,
            String transitionHash,

            Instant createdAt
    ) {
        UUID transitionId =
                jdbcClient
                        .sql("""
                                INSERT INTO resilience_governance_trust_anchor_transitions (
                                    id,
                                    transition_number,
                                    outgoing_key_id,
                                    outgoing_public_key_fingerprint,
                                    replacement_key_id,
                                    replacement_public_key_fingerprint,
                                    algorithm,
                                    reason,
                                    transition_version,
                                    signature_base64,
                                    previous_transition_hash,
                                    transition_hash,
                                    created_at
                                )
                                VALUES (
                                    :id,
                                    :transitionNumber,
                                    :outgoingKeyId,
                                    :outgoingFingerprint,
                                    :replacementKeyId,
                                    :replacementFingerprint,
                                    :algorithm,
                                    :reason,
                                    :transitionVersion,
                                    :signatureBase64,
                                    :previousTransitionHash,
                                    :transitionHash,
                                    :createdAt
                                )
                                RETURNING id
                                """)
                        .param(
                                "id",
                                id
                        )
                        .param(
                                "transitionNumber",
                                material.transitionNumber()
                        )
                        .param(
                                "outgoingKeyId",
                                material.outgoingKeyId()
                        )
                        .param(
                                "outgoingFingerprint",
                                material.outgoingPublicKeyFingerprint()
                        )
                        .param(
                                "replacementKeyId",
                                material.replacementKeyId()
                        )
                        .param(
                                "replacementFingerprint",
                                material.replacementPublicKeyFingerprint()
                        )
                        .param(
                                "algorithm",
                                material.algorithm()
                        )
                        .param(
                                "reason",
                                material.reason()
                        )
                        .param(
                                "transitionVersion",
                                transitionVersion
                        )
                        .param(
                                "signatureBase64",
                                signatureBase64
                        )
                        .param(
                                "previousTransitionHash",
                                material.previousTransitionHash(),
                                java.sql.Types.VARCHAR
                        )
                        .param(
                                "transitionHash",
                                transitionHash
                        )
                        .param(
                                "createdAt",
                                databaseTime(createdAt)
                        )
                        .query(
                                UUID.class
                        )
                        .single();

        return findById(
                transitionId
        ).orElseThrow(() ->
                new IllegalStateException(
                        "Trust-anchor transition disappeared after persistence."
                )
        );
    }

    public void retireOutgoingAnchor(
            String outgoingKeyId,
            String replacementKeyId,
            String reason,
            Instant retiredAt
    ) {
        int updated =
                jdbcClient
                        .sql("""
                                UPDATE resilience_governance_trust_anchors

                                SET
                                    status = 'RETIRED',

                                    superseded_by_key_id =
                                        :replacementKeyId,

                                    retirement_reason =
                                        :reason,

                                    retired_at =
                                        :retiredAt

                                WHERE key_id =
                                      :outgoingKeyId

                                  AND status =
                                      'ACTIVE'
                                """)
                        .param(
                                "replacementKeyId",
                                replacementKeyId
                        )
                        .param(
                                "reason",
                                reason
                        )
                        .param(
                                "retiredAt",
                                databaseTime(retiredAt)
                        )
                        .param(
                                "outgoingKeyId",
                                outgoingKeyId
                        )
                        .update();

        if (
                updated != 1
        ) {
            throw new IllegalStateException(
                    "Outgoing trust anchor was not retired exactly once."
            );
        }

        jdbcClient
                .sql("""
                        INSERT INTO resilience_governance_trust_anchor_events (
                            id,
                            key_id,
                            event_type,
                            replacement_key_id,
                            detail,
                            created_at
                        )
                        VALUES (
                            :id,
                            :keyId,
                            'RETIRED',
                            :replacementKeyId,
                            :detail,
                            :createdAt
                        )
                        """)
                .param(
                        "id",
                        UUID.randomUUID()
                )
                .param(
                        "keyId",
                        outgoingKeyId
                )
                .param(
                        "replacementKeyId",
                        replacementKeyId
                )
                .param(
                        "detail",
                        reason
                )
                .param(
                        "createdAt",
                        databaseTime(retiredAt)
                )
                .update();
    }

    public boolean lifecycleMatches(
            String outgoingKeyId,
            String replacementKeyId,
            String reason,
            Instant transitionTime
    ) {
        return jdbcClient
                .sql("""
                        SELECT COUNT(*)

                        FROM resilience_governance_trust_anchors

                        WHERE key_id =
                              :outgoingKeyId

                          AND status =
                              'RETIRED'

                          AND superseded_by_key_id =
                              :replacementKeyId

                          AND retirement_reason =
                              :reason

                          AND retired_at IS NOT NULL

                          AND retired_at >=
                              :transitionTime
                        """)
                .param(
                        "outgoingKeyId",
                        outgoingKeyId
                )
                .param(
                        "replacementKeyId",
                        replacementKeyId
                )
                .param(
                        "reason",
                        reason
                )
                .param(
                        "transitionTime",
                        databaseTime(transitionTime)
                )
                .query(
                        Integer.class
                )
                .single()
                == 1;
    }

    public List<GovernanceTrustAnchorTransition> findHistory() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_trust_anchor_transitions

                        ORDER BY
                            transition_number DESC,
                            created_at DESC,
                            id DESC
                        """)
                .query(
                        this::map
                )
                .list();
    }

    public List<GovernanceTrustAnchorTransition> findChain() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_trust_anchor_transitions

                        ORDER BY
                            transition_number ASC,
                            created_at ASC,
                            id ASC
                        """)
                .query(
                        this::map
                )
                .list();
    }

    private Optional<GovernanceTrustAnchorTransition> findById(
            UUID id
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + COLUMNS
                        + """
                        FROM resilience_governance_trust_anchor_transitions

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

    private OffsetDateTime databaseTime(
            Instant instant
    ) {
        return OffsetDateTime.ofInstant(
                instant,
                ZoneOffset.UTC
        );
    }

    private GovernanceTrustAnchorTransition map(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new GovernanceTrustAnchorTransition(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getInt(
                        "transition_number"
                ),

                resultSet.getString(
                        "outgoing_key_id"
                ),

                resultSet.getString(
                        "outgoing_public_key_fingerprint"
                ),

                resultSet.getString(
                        "replacement_key_id"
                ),

                resultSet.getString(
                        "replacement_public_key_fingerprint"
                ),

                resultSet.getString(
                        "algorithm"
                ),

                resultSet.getString(
                        "reason"
                ),

                resultSet.getString(
                        "transition_version"
                ),

                resultSet.getString(
                        "signature_base64"
                ),

                resultSet.getString(
                        "previous_transition_hash"
                ),

                resultSet.getString(
                        "transition_hash"
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
