package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.IncidentSitrepPreview;
import com.terrascope.platform.api.model.IncidentSituationReport;
import com.terrascope.platform.domain.CommandPressureBand;
import com.terrascope.platform.domain.IncidentSeverity;
import com.terrascope.platform.domain.IncidentStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class IncidentSituationReportRepository {

    private static final String SELECT_COLUMNS = """
            id,
            incident_id,
            report_number,
            incident_title,
            incident_status,
            incident_severity,
            command_score,
            command_band,
            executive_summary,
            priority_one,
            priority_two,
            priority_three,
            open_action_count,
            overdue_action_count,
            blocked_action_count,
            resource_shortage_count,
            evacuation_remaining,
            shelter_remaining_capacity,
            lifeline_disrupted_count,
            critical_lifeline_disruption_count,
            lifeline_affected_population,
            unresolved_damage_loss_musd,
            critical_damage_count,
            average_repair_progress_pct,
            created_at
            """;

    private final JdbcClient jdbcClient;

    public IncidentSituationReportRepository(
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

    public int nextReportNumber(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                            COALESCE(
                                MAX(report_number),
                                0
                            ) + 1
                        FROM incident_situation_reports
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

    public IncidentSituationReport saveReport(
            UUID id,
            UUID incidentId,
            int reportNumber,
            IncidentSitrepPreview preview
    ) {
        List<String> priorities =
                preview.priorities();

        String priorityOne =
                priority(
                        priorities,
                        0
                );

        String priorityTwo =
                priority(
                        priorities,
                        1
                );

        String priorityThree =
                priority(
                        priorities,
                        2
                );

        UUID reportId =
                jdbcClient
                        .sql("""
                                INSERT INTO incident_situation_reports (
                                    id,
                                    incident_id,
                                    report_number,
                                    incident_title,
                                    incident_status,
                                    incident_severity,
                                    command_score,
                                    command_band,
                                    executive_summary,
                                    priority_one,
                                    priority_two,
                                    priority_three,
                                    open_action_count,
                                    overdue_action_count,
                                    blocked_action_count,
                                    resource_shortage_count,
                                    evacuation_remaining,
                                    shelter_remaining_capacity,
                                    lifeline_disrupted_count,
                                    critical_lifeline_disruption_count,
                                    lifeline_affected_population,
                                    unresolved_damage_loss_musd,
                                    critical_damage_count,
                                    average_repair_progress_pct
                                )
                                VALUES (
                                    :id,
                                    :incidentId,
                                    :reportNumber,
                                    :incidentTitle,
                                    :incidentStatus,
                                    :incidentSeverity,
                                    :commandScore,
                                    :commandBand,
                                    :executiveSummary,
                                    :priorityOne,
                                    :priorityTwo,
                                    :priorityThree,
                                    :openActionCount,
                                    :overdueActionCount,
                                    :blockedActionCount,
                                    :resourceShortageCount,
                                    :evacuationRemaining,
                                    :shelterRemainingCapacity,
                                    :lifelineDisruptedCount,
                                    :criticalLifelineDisruptionCount,
                                    :lifelineAffectedPopulation,
                                    :unresolvedDamageLossMusd,
                                    :criticalDamageCount,
                                    :averageRepairProgressPct
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
                                "reportNumber",
                                reportNumber
                        )
                        .param(
                                "incidentTitle",
                                preview.incidentTitle()
                        )
                        .param(
                                "incidentStatus",
                                preview.incidentStatus().name()
                        )
                        .param(
                                "incidentSeverity",
                                preview.incidentSeverity().name()
                        )
                        .param(
                                "commandScore",
                                preview.commandScore()
                        )
                        .param(
                                "commandBand",
                                preview.commandBand().name()
                        )
                        .param(
                                "executiveSummary",
                                preview.executiveSummary()
                        )
                        .param(
                                "priorityOne",
                                priorityOne,
                                Types.VARCHAR
                        )
                        .param(
                                "priorityTwo",
                                priorityTwo,
                                Types.VARCHAR
                        )
                        .param(
                                "priorityThree",
                                priorityThree,
                                Types.VARCHAR
                        )
                        .param(
                                "openActionCount",
                                preview.openActionCount()
                        )
                        .param(
                                "overdueActionCount",
                                preview.overdueActionCount()
                        )
                        .param(
                                "blockedActionCount",
                                preview.blockedActionCount()
                        )
                        .param(
                                "resourceShortageCount",
                                preview.resourceShortageCount()
                        )
                        .param(
                                "evacuationRemaining",
                                preview.evacuationRemaining()
                        )
                        .param(
                                "shelterRemainingCapacity",
                                preview.shelterRemainingCapacity()
                        )
                        .param(
                                "lifelineDisruptedCount",
                                preview.lifelineDisruptedCount()
                        )
                        .param(
                                "criticalLifelineDisruptionCount",
                                preview.criticalLifelineDisruptionCount()
                        )
                        .param(
                                "lifelineAffectedPopulation",
                                preview.lifelineAffectedPopulation()
                        )
                        .param(
                                "unresolvedDamageLossMusd",
                                preview.unresolvedDamageLossMusd()
                        )
                        .param(
                                "criticalDamageCount",
                                preview.criticalDamageCount()
                        )
                        .param(
                                "averageRepairProgressPct",
                                preview.averageRepairProgressPct()
                        )
                        .query(
                                UUID.class
                        )
                        .single();

        return findById(
                reportId
        );
    }

    public List<IncidentSituationReport> findHistory(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + SELECT_COLUMNS
                        + """
                        FROM incident_situation_reports
                        WHERE incident_id = :incidentId
                        ORDER BY
                            report_number DESC,
                            created_at DESC
                        LIMIT 20
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .query(
                        this::mapReport
                )
                .list();
    }

    private IncidentSituationReport findById(
            UUID reportId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + SELECT_COLUMNS
                        + """
                        FROM incident_situation_reports
                        WHERE id = :reportId
                        """)
                .param(
                        "reportId",
                        reportId
                )
                .query(
                        this::mapReport
                )
                .single();
    }

    private IncidentSituationReport mapReport(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        List<String> priorities =
                new ArrayList<>();

        addPriority(
                priorities,
                resultSet.getString(
                        "priority_one"
                )
        );

        addPriority(
                priorities,
                resultSet.getString(
                        "priority_two"
                )
        );

        addPriority(
                priorities,
                resultSet.getString(
                        "priority_three"
                )
        );

        return new IncidentSituationReport(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),
                resultSet.getObject(
                        "incident_id",
                        UUID.class
                ),
                resultSet.getInt(
                        "report_number"
                ),

                resultSet.getString(
                        "incident_title"
                ),
                IncidentStatus.valueOf(
                        resultSet.getString(
                                "incident_status"
                        )
                ),
                IncidentSeverity.valueOf(
                        resultSet.getString(
                                "incident_severity"
                        )
                ),

                resultSet.getInt(
                        "command_score"
                ),
                CommandPressureBand.valueOf(
                        resultSet.getString(
                                "command_band"
                        )
                ),

                resultSet.getString(
                        "executive_summary"
                ),

                List.copyOf(
                        priorities
                ),

                resultSet.getInt(
                        "open_action_count"
                ),
                resultSet.getInt(
                        "overdue_action_count"
                ),
                resultSet.getInt(
                        "blocked_action_count"
                ),

                resultSet.getInt(
                        "resource_shortage_count"
                ),

                resultSet.getLong(
                        "evacuation_remaining"
                ),
                resultSet.getLong(
                        "shelter_remaining_capacity"
                ),

                resultSet.getInt(
                        "lifeline_disrupted_count"
                ),
                resultSet.getInt(
                        "critical_lifeline_disruption_count"
                ),
                resultSet.getLong(
                        "lifeline_affected_population"
                ),

                resultSet.getDouble(
                        "unresolved_damage_loss_musd"
                ),
                resultSet.getInt(
                        "critical_damage_count"
                ),
                resultSet.getDouble(
                        "average_repair_progress_pct"
                ),

                resultSet
                        .getObject(
                                "created_at",
                                OffsetDateTime.class
                        )
                        .toInstant()
        );
    }

    private String priority(
            List<String> priorities,
            int index
    ) {
        if (
                index < 0
                || index >= priorities.size()
        ) {
            return null;
        }

        return priorities.get(
                index
        );
    }

    private void addPriority(
            List<String> priorities,
            String value
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            return;
        }

        priorities.add(
                value
        );
    }
}
