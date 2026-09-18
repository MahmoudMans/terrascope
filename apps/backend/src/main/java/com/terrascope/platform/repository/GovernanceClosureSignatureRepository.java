package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.GovernanceClosureSignature;
import com.terrascope.platform.api.model.GovernanceTrustAnchorEvent;
import com.terrascope.platform.api.model.GovernanceTrustAnchorView;
import com.terrascope.platform.domain.GovernanceSignatureTarget;
import com.terrascope.platform.domain.GovernanceTrustAnchor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class GovernanceClosureSignatureRepository {

    private static final String SIGNATURE_COLUMNS = """
            id,
            certificate_id,
            certificate_number,
            key_id,
            algorithm,
            public_key_fingerprint,
            signature_version,
            certificate_hash,
            dossier_hash,
            signature_base64,
            created_at
            """;

    private final JdbcClient jdbcClient;

    public GovernanceClosureSignatureRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<GovernanceSignatureTarget> findCertificateTarget(
            UUID certificateId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                            id,
                            certificate_number,
                            certificate_hash,
                            dossier_hash

                        FROM resilience_governance_closure_certificates

                        WHERE id = :certificateId
                        """)
                .param(
                        "certificateId",
                        certificateId
                )
                .query(
                        (
                                resultSet,
                                rowNumber
                        ) ->
                                new GovernanceSignatureTarget(
                                        resultSet.getObject(
                                                "id",
                                                UUID.class
                                        ),

                                        resultSet.getInt(
                                                "certificate_number"
                                        ),

                                        resultSet.getString(
                                                "certificate_hash"
                                        ),

                                        resultSet.getString(
                                                "dossier_hash"
                                        )
                                )
                )
                .optional();
    }

    public boolean signatureExistsForCertificateAndKey(
            UUID certificateId,
            String keyId
    ) {
        return jdbcClient
                .sql("""
                        SELECT COUNT(*)

                        FROM resilience_governance_closure_signatures

                        WHERE certificate_id =
                              :certificateId

                          AND key_id =
                              :keyId
                        """)
                .param(
                        "certificateId",
                        certificateId
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

    public Optional<GovernanceTrustAnchor> findTrustAnchor(
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
                            last_used_at,
                            retired_at

                        FROM resilience_governance_trust_anchors

                        WHERE key_id = :keyId
                        """)
                .param(
                        "keyId",
                        keyId
                )
                .query(
                        this::mapTrustAnchor
                )
                .optional();
    }

    public GovernanceTrustAnchor registerTrustAnchor(
            String keyId,
            String publicKeyX509Base64,
            String publicKeyFingerprint
    ) {
        String storedKeyId =
                jdbcClient
                        .sql("""
                                INSERT INTO resilience_governance_trust_anchors (
                                    key_id,
                                    algorithm,
                                    public_key_x509_base64,
                                    public_key_fingerprint
                                )
                                VALUES (
                                    :keyId,
                                    'ED25519',
                                    :publicKeyX509Base64,
                                    :publicKeyFingerprint
                                )
                                RETURNING key_id
                                """)
                        .param(
                                "keyId",
                                keyId
                        )
                        .param(
                                "publicKeyX509Base64",
                                publicKeyX509Base64
                        )
                        .param(
                                "publicKeyFingerprint",
                                publicKeyFingerprint
                        )
                        .query(
                                String.class
                        )
                        .single();

        return findTrustAnchor(
                storedKeyId
        ).orElseThrow(() ->
                new IllegalStateException(
                        "Trust anchor disappeared after registration."
                )
        );
    }

    public GovernanceTrustAnchorView requireTrustAnchorView(
            String keyId
    ) {
        return findTrustAnchorView(
                keyId
        ).orElseThrow(() ->
                new IllegalStateException(
                        "Trust anchor view disappeared."
                )
        );
    }

    public Optional<GovernanceTrustAnchorView> findTrustAnchorView(
            String keyId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                            key_id,
                            algorithm,
                            public_key_fingerprint,
                            status,
                            superseded_by_key_id,
                            retirement_reason,
                            created_at,
                            last_used_at,
                            retired_at

                        FROM resilience_governance_trust_anchors

                        WHERE key_id = :keyId
                        """)
                .param(
                        "keyId",
                        keyId
                )
                .query(
                        this::mapTrustAnchorView
                )
                .optional();
    }

    public List<GovernanceTrustAnchorView> findTrustAnchorViews() {
        return jdbcClient
                .sql("""
                        SELECT
                            key_id,
                            algorithm,
                            public_key_fingerprint,
                            status,
                            superseded_by_key_id,
                            retirement_reason,
                            created_at,
                            last_used_at,
                            retired_at

                        FROM resilience_governance_trust_anchors

                        ORDER BY
                            created_at DESC,
                            key_id DESC
                        """)
                .query(
                        this::mapTrustAnchorView
                )
                .list();
    }

    public GovernanceTrustAnchorView retireTrustAnchor(
            String keyId,
            String replacementKeyId,
            String reason
    ) {
        String updatedKeyId =
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
                                        NOW()

                                WHERE key_id =
                                      :keyId

                                RETURNING key_id
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
                                "keyId",
                                keyId
                        )
                        .query(
                                String.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Trust anchor retirement returned no row."
                                )
                        );

        return requireTrustAnchorView(
                updatedKeyId
        );
    }

    public void touchTrustAnchor(
            String keyId
    ) {
        jdbcClient
                .sql("""
                        UPDATE resilience_governance_trust_anchors

                        SET last_used_at =
                            NOW()

                        WHERE key_id =
                              :keyId
                        """)
                .param(
                        "keyId",
                        keyId
                )
                .update();
    }

    public void appendTrustAnchorEvent(
            UUID id,
            String keyId,
            String eventType,
            String replacementKeyId,
            String detail
    ) {
        jdbcClient
                .sql("""
                        INSERT INTO resilience_governance_trust_anchor_events (
                            id,
                            key_id,
                            event_type,
                            replacement_key_id,
                            detail
                        )
                        VALUES (
                            :id,
                            :keyId,
                            :eventType,
                            :replacementKeyId,
                            :detail
                        )
                        """)
                .param(
                        "id",
                        id
                )
                .param(
                        "keyId",
                        keyId
                )
                .param(
                        "eventType",
                        eventType
                )
                .param(
                        "replacementKeyId",
                        replacementKeyId,
                        java.sql.Types.VARCHAR
                )
                .param(
                        "detail",
                        detail
                )
                .update();
    }

    public List<GovernanceTrustAnchorEvent> findTrustAnchorEvents() {
        return jdbcClient
                .sql("""
                        SELECT
                            id,
                            key_id,
                            event_type,
                            replacement_key_id,
                            detail,
                            created_at

                        FROM resilience_governance_trust_anchor_events

                        ORDER BY
                            created_at DESC,
                            id DESC
                        """)
                .query(
                        this::mapTrustAnchorEvent
                )
                .list();
    }

    public GovernanceClosureSignature saveSignature(
            UUID id,
            GovernanceSignatureTarget target,
            GovernanceTrustAnchor anchor,
            String signatureVersion,
            String signatureBase64
    ) {
        UUID signatureId =
                jdbcClient
                        .sql("""
                                INSERT INTO resilience_governance_closure_signatures (
                                    id,
                                    certificate_id,
                                    certificate_number,
                                    key_id,
                                    algorithm,
                                    public_key_fingerprint,
                                    signature_version,
                                    certificate_hash,
                                    dossier_hash,
                                    signature_base64
                                )
                                VALUES (
                                    :id,
                                    :certificateId,
                                    :certificateNumber,
                                    :keyId,
                                    :algorithm,
                                    :publicKeyFingerprint,
                                    :signatureVersion,
                                    :certificateHash,
                                    :dossierHash,
                                    :signatureBase64
                                )
                                RETURNING id
                                """)
                        .param(
                                "id",
                                id
                        )
                        .param(
                                "certificateId",
                                target.certificateId()
                        )
                        .param(
                                "certificateNumber",
                                target.certificateNumber()
                        )
                        .param(
                                "keyId",
                                anchor.keyId()
                        )
                        .param(
                                "algorithm",
                                anchor.algorithm()
                        )
                        .param(
                                "publicKeyFingerprint",
                                anchor.publicKeyFingerprint()
                        )
                        .param(
                                "signatureVersion",
                                signatureVersion
                        )
                        .param(
                                "certificateHash",
                                target.certificateHash()
                        )
                        .param(
                                "dossierHash",
                                target.dossierHash()
                        )
                        .param(
                                "signatureBase64",
                                signatureBase64
                        )
                        .query(
                                UUID.class
                        )
                        .single();

        return findSignatureById(
                signatureId
        );
    }

    public List<GovernanceClosureSignature> findHistory() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + SIGNATURE_COLUMNS
                        + """
                        FROM resilience_governance_closure_signatures

                        ORDER BY
                            certificate_number DESC,
                            created_at DESC,
                            id DESC
                        """)
                .query(
                        this::mapSignature
                )
                .list();
    }

    public List<GovernanceClosureSignature> findAllAscending() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + SIGNATURE_COLUMNS
                        + """
                        FROM resilience_governance_closure_signatures

                        ORDER BY
                            certificate_number ASC,
                            created_at ASC,
                            id ASC
                        """)
                .query(
                        this::mapSignature
                )
                .list();
    }

    private GovernanceClosureSignature findSignatureById(
            UUID signatureId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + SIGNATURE_COLUMNS
                        + """
                        FROM resilience_governance_closure_signatures

                        WHERE id =
                              :signatureId
                        """)
                .param(
                        "signatureId",
                        signatureId
                )
                .query(
                        this::mapSignature
                )
                .single();
    }

    private GovernanceClosureSignature mapSignature(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new GovernanceClosureSignature(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getObject(
                        "certificate_id",
                        UUID.class
                ),

                resultSet.getInt(
                        "certificate_number"
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
                        "signature_version"
                ),

                resultSet.getString(
                        "certificate_hash"
                ),

                resultSet.getString(
                        "dossier_hash"
                ),

                resultSet.getString(
                        "signature_base64"
                ),

                resultSet
                        .getObject(
                                "created_at",
                                OffsetDateTime.class
                        )
                        .toInstant()
        );
    }

    private GovernanceTrustAnchor mapTrustAnchor(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        OffsetDateTime retiredAt =
                resultSet.getObject(
                        "retired_at",
                        OffsetDateTime.class
                );

        return new GovernanceTrustAnchor(
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

                resultSet
                        .getObject(
                                "created_at",
                                OffsetDateTime.class
                        )
                        .toInstant(),

                resultSet
                        .getObject(
                                "last_used_at",
                                OffsetDateTime.class
                        )
                        .toInstant(),

                retiredAt == null
                        ? null
                        : retiredAt.toInstant()
        );
    }

    private GovernanceTrustAnchorView mapTrustAnchorView(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        OffsetDateTime retiredAt =
                resultSet.getObject(
                        "retired_at",
                        OffsetDateTime.class
                );

        return new GovernanceTrustAnchorView(
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
                        "status"
                ),

                resultSet.getString(
                        "superseded_by_key_id"
                ),

                resultSet.getString(
                        "retirement_reason"
                ),

                resultSet
                        .getObject(
                                "created_at",
                                OffsetDateTime.class
                        )
                        .toInstant(),

                resultSet
                        .getObject(
                                "last_used_at",
                                OffsetDateTime.class
                        )
                        .toInstant(),

                retiredAt == null
                        ? null
                        : retiredAt.toInstant()
        );
    }

    private GovernanceTrustAnchorEvent mapTrustAnchorEvent(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new GovernanceTrustAnchorEvent(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getString(
                        "key_id"
                ),

                resultSet.getString(
                        "event_type"
                ),

                resultSet.getString(
                        "replacement_key_id"
                ),

                resultSet.getString(
                        "detail"
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
