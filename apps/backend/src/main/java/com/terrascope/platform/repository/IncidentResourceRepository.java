package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.CreateIncidentResourceRequest;
import com.terrascope.platform.api.model.IncidentResourceRequest;
import com.terrascope.platform.domain.IncidentActionPriority;
import com.terrascope.platform.domain.IncidentResourceCategory;
import com.terrascope.platform.domain.IncidentResourceStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class IncidentResourceRepository {

    private static final String SELECT_COLUMNS = """
            id,
            incident_id,
            category,
            name,
            description,
            priority,
            status,
            requested_quantity,
            allocated_quantity,
            unit,
            owner_name,
            created_at,
            updated_at,
            fulfilled_at,
            cancelled_at
            """;

    private final JdbcClient jdbcClient;

    public IncidentResourceRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public List<IncidentResourceRequest> findAll(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + SELECT_COLUMNS
                        + """
                        FROM incident_resource_requests
                        WHERE incident_id = :incidentId
                        ORDER BY
                            CASE status
                                WHEN 'PARTIALLY_ALLOCATED' THEN 0
                                WHEN 'REQUESTED' THEN 1
                                WHEN 'FULFILLED' THEN 2
                                ELSE 3
                            END,
                            CASE priority
                                WHEN 'CRITICAL' THEN 0
                                WHEN 'HIGH' THEN 1
                                WHEN 'MEDIUM' THEN 2
                                ELSE 3
                            END,
                            updated_at DESC,
                            id DESC
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .query(
                        this::mapResource
                )
                .list();
    }

    public Optional<IncidentResourceRequest> findById(
            UUID incidentId,
            UUID resourceId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + SELECT_COLUMNS
                        + """
                        FROM incident_resource_requests
                        WHERE incident_id = :incidentId
                          AND id = :resourceId
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .param(
                        "resourceId",
                        resourceId
                )
                .query(
                        this::mapResource
                )
                .optional();
    }

    public IncidentResourceRequest create(
            UUID id,
            UUID incidentId,
            CreateIncidentResourceRequest request,
            IncidentResourceCategory category,
            IncidentActionPriority priority,
            String owner
    ) {
        UUID createdId =
                jdbcClient
                        .sql("""
                                INSERT INTO incident_resource_requests (
                                    id,
                                    incident_id,
                                    category,
                                    name,
                                    description,
                                    priority,
                                    requested_quantity,
                                    unit,
                                    owner_name
                                )
                                VALUES (
                                    :id,
                                    :incidentId,
                                    :category,
                                    :name,
                                    :description,
                                    :priority,
                                    :requestedQuantity,
                                    :unit,
                                    :owner
                                )
                                RETURNING id
                                """)
                        .param(
                                "id",
                                id
                        )
                        .param(
                                "incidentId",
                                incidentId
                        )
                        .param(
                                "category",
                                category.name()
                        )
                        .param(
                                "name",
                                request.name().trim()
                        )
                        .param(
                                "description",
                                safeText(
                                        request.description()
                                )
                        )
                        .param(
                                "priority",
                                priority.name()
                        )
                        .param(
                                "requestedQuantity",
                                request.requestedQuantity()
                        )
                        .param(
                                "unit",
                                request.unit().trim()
                        )
                        .param(
                                "owner",
                                owner
                        )
                        .query(
                                UUID.class
                        )
                        .single();

        return requireResource(
                incidentId,
                createdId
        );
    }

    public IncidentResourceRequest updateAllocation(
            UUID incidentId,
            UUID resourceId,
            int allocatedQuantity
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_resource_requests
                                SET
                                    allocated_quantity =
                                        :allocatedQuantity,

                                    status =
                                        CASE
                                            WHEN :allocatedQuantity = 0
                                                THEN 'REQUESTED'

                                            WHEN :allocatedQuantity
                                                 = requested_quantity
                                                THEN 'FULFILLED'

                                            ELSE 'PARTIALLY_ALLOCATED'
                                        END,

                                    fulfilled_at =
                                        CASE
                                            WHEN :allocatedQuantity
                                                 = requested_quantity
                                                THEN COALESCE(
                                                    fulfilled_at,
                                                    NOW()
                                                )
                                            ELSE NULL
                                        END,

                                    cancelled_at = NULL,
                                    updated_at = NOW()

                                WHERE incident_id = :incidentId
                                  AND id = :resourceId

                                RETURNING id
                                """)
                        .param(
                                "allocatedQuantity",
                                allocatedQuantity
                        )
                        .param(
                                "incidentId",
                                incidentId
                        )
                        .param(
                                "resourceId",
                                resourceId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Resource allocation update returned no row."
                                )
                        );

        return requireResource(
                incidentId,
                updatedId
        );
    }

    public IncidentResourceRequest updatePriority(
            UUID incidentId,
            UUID resourceId,
            IncidentActionPriority priority
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_resource_requests
                                SET
                                    priority = :priority,
                                    updated_at = NOW()
                                WHERE incident_id = :incidentId
                                  AND id = :resourceId
                                RETURNING id
                                """)
                        .param(
                                "priority",
                                priority.name()
                        )
                        .param(
                                "incidentId",
                                incidentId
                        )
                        .param(
                                "resourceId",
                                resourceId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Resource priority update returned no row."
                                )
                        );

        return requireResource(
                incidentId,
                updatedId
        );
    }

    public IncidentResourceRequest updateOwner(
            UUID incidentId,
            UUID resourceId,
            String owner
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_resource_requests
                                SET
                                    owner_name = :owner,
                                    updated_at = NOW()
                                WHERE incident_id = :incidentId
                                  AND id = :resourceId
                                RETURNING id
                                """)
                        .param(
                                "owner",
                                owner
                        )
                        .param(
                                "incidentId",
                                incidentId
                        )
                        .param(
                                "resourceId",
                                resourceId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Resource owner update returned no row."
                                )
                        );

        return requireResource(
                incidentId,
                updatedId
        );
    }

    public IncidentResourceRequest cancel(
            UUID incidentId,
            UUID resourceId
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_resource_requests
                                SET
                                    status = 'CANCELLED',
                                    cancelled_at = COALESCE(
                                        cancelled_at,
                                        NOW()
                                    ),
                                    updated_at = NOW()
                                WHERE incident_id = :incidentId
                                  AND id = :resourceId
                                RETURNING id
                                """)
                        .param(
                                "incidentId",
                                incidentId
                        )
                        .param(
                                "resourceId",
                                resourceId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Resource cancellation returned no row."
                                )
                        );

        return requireResource(
                incidentId,
                updatedId
        );
    }

    public int delete(
            UUID incidentId,
            UUID resourceId
    ) {
        return jdbcClient
                .sql("""
                        DELETE
                        FROM incident_resource_requests
                        WHERE incident_id = :incidentId
                          AND id = :resourceId
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .param(
                        "resourceId",
                        resourceId
                )
                .update();
    }

    private IncidentResourceRequest requireResource(
            UUID incidentId,
            UUID resourceId
    ) {
        return findById(
                incidentId,
                resourceId
        ).orElseThrow(() ->
                new IllegalStateException(
                        "Resource "
                                + resourceId
                                + " disappeared after mutation."
                )
        );
    }

    private IncidentResourceRequest mapResource(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        OffsetDateTime fulfilledAt =
                resultSet.getObject(
                        "fulfilled_at",
                        OffsetDateTime.class
                );

        OffsetDateTime cancelledAt =
                resultSet.getObject(
                        "cancelled_at",
                        OffsetDateTime.class
                );

        return new IncidentResourceRequest(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),
                resultSet.getObject(
                        "incident_id",
                        UUID.class
                ),
                IncidentResourceCategory.valueOf(
                        resultSet.getString(
                                "category"
                        )
                ),
                resultSet.getString(
                        "name"
                ),
                resultSet.getString(
                        "description"
                ),
                IncidentActionPriority.valueOf(
                        resultSet.getString(
                                "priority"
                        )
                ),
                IncidentResourceStatus.valueOf(
                        resultSet.getString(
                                "status"
                        )
                ),
                resultSet.getInt(
                        "requested_quantity"
                ),
                resultSet.getInt(
                        "allocated_quantity"
                ),
                resultSet.getString(
                        "unit"
                ),
                resultSet.getString(
                        "owner_name"
                ),
                resultSet
                        .getObject(
                                "created_at",
                                OffsetDateTime.class
                        )
                        .toInstant(),
                resultSet
                        .getObject(
                                "updated_at",
                                OffsetDateTime.class
                        )
                        .toInstant(),
                fulfilledAt == null
                        ? null
                        : fulfilledAt.toInstant(),
                cancelledAt == null
                        ? null
                        : cancelledAt.toInstant()
        );
    }

    private String safeText(
            String value
    ) {
        return value == null
                ? ""
                : value.trim();
    }
}
