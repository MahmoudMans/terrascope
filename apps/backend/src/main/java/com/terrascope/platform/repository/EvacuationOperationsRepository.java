package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.CreateEvacuationZoneRequest;
import com.terrascope.platform.api.model.CreateShelterRequest;
import com.terrascope.platform.api.model.EvacuationZone;
import com.terrascope.platform.api.model.IncidentShelter;
import com.terrascope.platform.domain.EvacuationZoneStatus;
import com.terrascope.platform.domain.IncidentActionPriority;
import com.terrascope.platform.domain.ShelterStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class EvacuationOperationsRepository {

    private final JdbcClient jdbcClient;

    public EvacuationOperationsRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public List<EvacuationZone> findZones(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                            id,
                            incident_id,
                            name,
                            description,
                            priority,
                            status,
                            population_at_risk,
                            evacuated_population,
                            owner_name,
                            created_at,
                            updated_at,
                            completed_at,
                            cancelled_at
                        FROM incident_evacuation_zones
                        WHERE incident_id = :incidentId
                        ORDER BY
                            CASE status
                                WHEN 'EVACUATING' THEN 0
                                WHEN 'MONITORING' THEN 1
                                WHEN 'CLEARED' THEN 2
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
                        this::mapZone
                )
                .list();
    }

    public List<IncidentShelter> findShelters(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                            id,
                            incident_id,
                            name,
                            address,
                            capacity,
                            occupancy,
                            status,
                            owner_name,
                            created_at,
                            updated_at,
                            closed_at
                        FROM incident_shelters
                        WHERE incident_id = :incidentId
                        ORDER BY
                            CASE status
                                WHEN 'FULL' THEN 0
                                WHEN 'OPEN' THEN 1
                                ELSE 2
                            END,
                            updated_at DESC,
                            id DESC
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .query(
                        this::mapShelter
                )
                .list();
    }

    public Optional<EvacuationZone> findZoneById(
            UUID incidentId,
            UUID zoneId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                            id,
                            incident_id,
                            name,
                            description,
                            priority,
                            status,
                            population_at_risk,
                            evacuated_population,
                            owner_name,
                            created_at,
                            updated_at,
                            completed_at,
                            cancelled_at
                        FROM incident_evacuation_zones
                        WHERE incident_id = :incidentId
                          AND id = :zoneId
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .param(
                        "zoneId",
                        zoneId
                )
                .query(
                        this::mapZone
                )
                .optional();
    }

    public Optional<IncidentShelter> findShelterById(
            UUID incidentId,
            UUID shelterId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                            id,
                            incident_id,
                            name,
                            address,
                            capacity,
                            occupancy,
                            status,
                            owner_name,
                            created_at,
                            updated_at,
                            closed_at
                        FROM incident_shelters
                        WHERE incident_id = :incidentId
                          AND id = :shelterId
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .param(
                        "shelterId",
                        shelterId
                )
                .query(
                        this::mapShelter
                )
                .optional();
    }

    public EvacuationZone createZone(
            UUID id,
            UUID incidentId,
            CreateEvacuationZoneRequest request,
            IncidentActionPriority priority,
            EvacuationZoneStatus status,
            String owner
    ) {
        UUID createdId =
                jdbcClient
                        .sql("""
                                INSERT INTO incident_evacuation_zones (
                                    id,
                                    incident_id,
                                    name,
                                    description,
                                    priority,
                                    status,
                                    population_at_risk,
                                    evacuated_population,
                                    owner_name,
                                    completed_at
                                )
                                VALUES (
                                    :id,
                                    :incidentId,
                                    :name,
                                    :description,
                                    :priority,
                                    :status,
                                    :populationAtRisk,
                                    :evacuatedPopulation,
                                    :owner,
                                    CASE
                                        WHEN :status = 'CLEARED'
                                            THEN NOW()
                                        ELSE NULL
                                    END
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
                                "status",
                                status.name()
                        )
                        .param(
                                "populationAtRisk",
                                request.populationAtRisk()
                        )
                        .param(
                                "evacuatedPopulation",
                                request.evacuatedPopulation()
                        )
                        .param(
                                "owner",
                                owner
                        )
                        .query(
                                UUID.class
                        )
                        .single();

        return requireZone(
                incidentId,
                createdId
        );
    }

    public EvacuationZone updateZoneProgress(
            UUID incidentId,
            UUID zoneId,
            int evacuatedPopulation,
            EvacuationZoneStatus status
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_evacuation_zones
                                SET
                                    evacuated_population =
                                        :evacuatedPopulation,

                                    status =
                                        :status,

                                    completed_at =
                                        CASE
                                            WHEN :status = 'CLEARED'
                                                THEN COALESCE(
                                                    completed_at,
                                                    NOW()
                                                )
                                            ELSE NULL
                                        END,

                                    cancelled_at =
                                        CASE
                                            WHEN :status = 'CANCELLED'
                                                THEN COALESCE(
                                                    cancelled_at,
                                                    NOW()
                                                )
                                            ELSE NULL
                                        END,

                                    updated_at = NOW()

                                WHERE incident_id = :incidentId
                                  AND id = :zoneId

                                RETURNING id
                                """)
                        .param(
                                "evacuatedPopulation",
                                evacuatedPopulation
                        )
                        .param(
                                "status",
                                status.name()
                        )
                        .param(
                                "incidentId",
                                incidentId
                        )
                        .param(
                                "zoneId",
                                zoneId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Evacuation progress update returned no row."
                                )
                        );

        return requireZone(
                incidentId,
                updatedId
        );
    }

    public EvacuationZone updateZoneStatus(
            UUID incidentId,
            UUID zoneId,
            EvacuationZoneStatus status
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_evacuation_zones
                                SET
                                    status = :status,

                                    completed_at =
                                        CASE
                                            WHEN :status = 'CLEARED'
                                                THEN COALESCE(
                                                    completed_at,
                                                    NOW()
                                                )
                                            ELSE NULL
                                        END,

                                    cancelled_at =
                                        CASE
                                            WHEN :status = 'CANCELLED'
                                                THEN COALESCE(
                                                    cancelled_at,
                                                    NOW()
                                                )
                                            ELSE NULL
                                        END,

                                    updated_at = NOW()

                                WHERE incident_id = :incidentId
                                  AND id = :zoneId

                                RETURNING id
                                """)
                        .param(
                                "status",
                                status.name()
                        )
                        .param(
                                "incidentId",
                                incidentId
                        )
                        .param(
                                "zoneId",
                                zoneId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Evacuation status update returned no row."
                                )
                        );

        return requireZone(
                incidentId,
                updatedId
        );
    }

    public EvacuationZone updateZoneOwner(
            UUID incidentId,
            UUID zoneId,
            String owner
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_evacuation_zones
                                SET
                                    owner_name = :owner,
                                    updated_at = NOW()
                                WHERE incident_id = :incidentId
                                  AND id = :zoneId
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
                                "zoneId",
                                zoneId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Evacuation owner update returned no row."
                                )
                        );

        return requireZone(
                incidentId,
                updatedId
        );
    }

    public int deleteZone(
            UUID incidentId,
            UUID zoneId
    ) {
        return jdbcClient
                .sql("""
                        DELETE
                        FROM incident_evacuation_zones
                        WHERE incident_id = :incidentId
                          AND id = :zoneId
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .param(
                        "zoneId",
                        zoneId
                )
                .update();
    }

    public IncidentShelter createShelter(
            UUID id,
            UUID incidentId,
            CreateShelterRequest request,
            ShelterStatus status,
            String owner
    ) {
        UUID createdId =
                jdbcClient
                        .sql("""
                                INSERT INTO incident_shelters (
                                    id,
                                    incident_id,
                                    name,
                                    address,
                                    capacity,
                                    occupancy,
                                    status,
                                    owner_name
                                )
                                VALUES (
                                    :id,
                                    :incidentId,
                                    :name,
                                    :address,
                                    :capacity,
                                    :occupancy,
                                    :status,
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
                                "name",
                                request.name().trim()
                        )
                        .param(
                                "address",
                                safeText(
                                        request.address()
                                )
                        )
                        .param(
                                "capacity",
                                request.capacity()
                        )
                        .param(
                                "occupancy",
                                request.occupancy()
                        )
                        .param(
                                "status",
                                status.name()
                        )
                        .param(
                                "owner",
                                owner
                        )
                        .query(
                                UUID.class
                        )
                        .single();

        return requireShelter(
                incidentId,
                createdId
        );
    }

    public IncidentShelter updateShelterOccupancy(
            UUID incidentId,
            UUID shelterId,
            int occupancy,
            ShelterStatus status
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_shelters
                                SET
                                    occupancy = :occupancy,
                                    status = :status,
                                    closed_at = NULL,
                                    updated_at = NOW()
                                WHERE incident_id = :incidentId
                                  AND id = :shelterId
                                RETURNING id
                                """)
                        .param(
                                "occupancy",
                                occupancy
                        )
                        .param(
                                "status",
                                status.name()
                        )
                        .param(
                                "incidentId",
                                incidentId
                        )
                        .param(
                                "shelterId",
                                shelterId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Shelter occupancy update returned no row."
                                )
                        );

        return requireShelter(
                incidentId,
                updatedId
        );
    }

    public IncidentShelter updateShelterStatus(
            UUID incidentId,
            UUID shelterId,
            ShelterStatus status
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_shelters
                                SET
                                    status = :status,

                                    closed_at =
                                        CASE
                                            WHEN :status = 'CLOSED'
                                                THEN COALESCE(
                                                    closed_at,
                                                    NOW()
                                                )
                                            ELSE NULL
                                        END,

                                    updated_at = NOW()

                                WHERE incident_id = :incidentId
                                  AND id = :shelterId

                                RETURNING id
                                """)
                        .param(
                                "status",
                                status.name()
                        )
                        .param(
                                "incidentId",
                                incidentId
                        )
                        .param(
                                "shelterId",
                                shelterId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Shelter status update returned no row."
                                )
                        );

        return requireShelter(
                incidentId,
                updatedId
        );
    }

    public IncidentShelter updateShelterOwner(
            UUID incidentId,
            UUID shelterId,
            String owner
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_shelters
                                SET
                                    owner_name = :owner,
                                    updated_at = NOW()
                                WHERE incident_id = :incidentId
                                  AND id = :shelterId
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
                                "shelterId",
                                shelterId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Shelter owner update returned no row."
                                )
                        );

        return requireShelter(
                incidentId,
                updatedId
        );
    }

    public int deleteShelter(
            UUID incidentId,
            UUID shelterId
    ) {
        return jdbcClient
                .sql("""
                        DELETE
                        FROM incident_shelters
                        WHERE incident_id = :incidentId
                          AND id = :shelterId
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .param(
                        "shelterId",
                        shelterId
                )
                .update();
    }

    private EvacuationZone requireZone(
            UUID incidentId,
            UUID zoneId
    ) {
        return findZoneById(
                incidentId,
                zoneId
        ).orElseThrow(() ->
                new IllegalStateException(
                        "Evacuation zone disappeared after mutation."
                )
        );
    }

    private IncidentShelter requireShelter(
            UUID incidentId,
            UUID shelterId
    ) {
        return findShelterById(
                incidentId,
                shelterId
        ).orElseThrow(() ->
                new IllegalStateException(
                        "Shelter disappeared after mutation."
                )
        );
    }

    private EvacuationZone mapZone(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        OffsetDateTime completedAt =
                resultSet.getObject(
                        "completed_at",
                        OffsetDateTime.class
                );

        OffsetDateTime cancelledAt =
                resultSet.getObject(
                        "cancelled_at",
                        OffsetDateTime.class
                );

        return new EvacuationZone(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),
                resultSet.getObject(
                        "incident_id",
                        UUID.class
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
                EvacuationZoneStatus.valueOf(
                        resultSet.getString(
                                "status"
                        )
                ),
                resultSet.getInt(
                        "population_at_risk"
                ),
                resultSet.getInt(
                        "evacuated_population"
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
                completedAt == null
                        ? null
                        : completedAt.toInstant(),
                cancelledAt == null
                        ? null
                        : cancelledAt.toInstant()
        );
    }

    private IncidentShelter mapShelter(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        OffsetDateTime closedAt =
                resultSet.getObject(
                        "closed_at",
                        OffsetDateTime.class
                );

        return new IncidentShelter(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),
                resultSet.getObject(
                        "incident_id",
                        UUID.class
                ),
                resultSet.getString(
                        "name"
                ),
                resultSet.getString(
                        "address"
                ),
                resultSet.getInt(
                        "capacity"
                ),
                resultSet.getInt(
                        "occupancy"
                ),
                ShelterStatus.valueOf(
                        resultSet.getString(
                                "status"
                        )
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
                closedAt == null
                        ? null
                        : closedAt.toInstant()
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
