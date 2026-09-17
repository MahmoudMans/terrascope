package com.terrascope.platform.service;

import com.terrascope.platform.api.model.CommandBlocker;
import com.terrascope.platform.api.model.CommandPulseHistoryResponse;
import com.terrascope.platform.api.model.CommandPulseSnapshot;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.IncidentCommandPulseResponse;
import com.terrascope.platform.domain.CommandPressureBand;
import com.terrascope.platform.domain.CommandPulseMetrics;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.repository.IncidentCommandPulseRepository;
import com.terrascope.platform.repository.IncidentResponseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class IncidentCommandPulseService {

    private final IncidentCommandPulseRepository repository;
    private final IncidentResponseRepository incidentRepository;

    public IncidentCommandPulseService(
            IncidentCommandPulseRepository repository,
            IncidentResponseRepository incidentRepository
    ) {
        this.repository = repository;
        this.incidentRepository = incidentRepository;
    }

    public IncidentCommandPulseResponse pulse(
            UUID incidentId
    ) {
        requireIncident(
                incidentId
        );

        CommandPulseMetrics metrics =
                repository.loadMetrics(
                        incidentId
                );

        return calculatePulse(
                incidentId,
                metrics
        );
    }

    public CommandPulseHistoryResponse history(
            UUID incidentId
    ) {
        requireIncident(
                incidentId
        );

        return new CommandPulseHistoryResponse(
                incidentId,
                repository.findHistory(
                        incidentId
                )
        );
    }

    @Transactional
    public CommandPulseSnapshot createSnapshot(
            UUID incidentId
    ) {
        requireMutableIncident(
                incidentId
        );

        IncidentCommandPulseResponse pulse =
                calculatePulse(
                        incidentId,
                        repository.loadMetrics(
                                incidentId
                        )
                );

        CommandPulseSnapshot snapshot =
                repository.saveSnapshot(
                        UUID.randomUUID(),
                        pulse
                );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "COMMAND_SNAPSHOT_CREATED",
                "Command pulse snapshot captured at "
                        + pulse.score()
                        + "/100 with "
                        + pulse.band().name()
                        + " pressure and "
                        + pulse.blockers().size()
                        + " active blockers."
        );

        return snapshot;
    }

    IncidentCommandPulseResponse calculatePulse(
            UUID incidentId,
            CommandPulseMetrics metrics
    ) {
        int overdueWeight =
                Math.min(
                        20,
                        metrics.overdueActionCount()
                                * 4
                );

        int blockedWeight =
                Math.min(
                        15,
                        metrics.blockedActionCount()
                                * 5
                );

        int shortageWeight =
                Math.min(
                        15,
                        metrics.resourceShortageCount()
                                * 3
                );

        int evacuationWeight =
                evacuationWeight(
                        metrics.evacuationRemaining()
                );

        int shelterWeight =
                shelterWeight(
                        metrics.evacuationRemaining(),
                        metrics.shelterRemainingCapacity()
                );

        int lifelineWeight =
                Math.min(
                        15,
                        metrics.lifelineDisruptedCount()
                                * 3
                                + metrics
                                .criticalLifelineDisruptionCount()
                                * 4
                );

        int damageWeight =
                Math.min(
                        10,
                        metrics.criticalDamageCount()
                                * 2
                                + Math.min(
                                5,
                                (int) Math.ceil(
                                        metrics
                                                .unresolvedDamageLossMusd()
                                                / 10.0
                                )
                        )
                );

        int score =
                Math.min(
                        100,
                        overdueWeight
                                + blockedWeight
                                + shortageWeight
                                + evacuationWeight
                                + shelterWeight
                                + lifelineWeight
                                + damageWeight
                );

        CommandPressureBand band =
                CommandPressureBand.fromScore(
                        score
                );

        List<CommandBlocker> blockers =
                new ArrayList<>();

        addBlocker(
                blockers,
                overdueWeight,
                "OVERDUE_ACTIONS",
                "Overdue response actions",
                Integer.toString(
                        metrics.overdueActionCount()
                )
        );

        addBlocker(
                blockers,
                blockedWeight,
                "BLOCKED_ACTIONS",
                "Blocked response actions",
                Integer.toString(
                        metrics.blockedActionCount()
                )
        );

        addBlocker(
                blockers,
                shortageWeight,
                "RESOURCE_SHORTAGES",
                "Unfulfilled resource requests",
                Integer.toString(
                        metrics.resourceShortageCount()
                )
        );

        addBlocker(
                blockers,
                evacuationWeight,
                "EVACUATION_BACKLOG",
                "People remaining to evacuate",
                Long.toString(
                        metrics.evacuationRemaining()
                )
        );

        addBlocker(
                blockers,
                shelterWeight,
                "SHELTER_CAPACITY_GAP",
                "Shelter capacity below evacuation backlog",
                metrics.shelterRemainingCapacity()
                        + " spaces available"
        );

        addBlocker(
                blockers,
                lifelineWeight,
                "LIFELINE_DISRUPTION",
                "Critical infrastructure disruption",
                metrics.lifelineDisruptedCount()
                        + " disrupted / "
                        + metrics
                        .criticalLifelineDisruptionCount()
                        + " critical"
        );

        addBlocker(
                blockers,
                damageWeight,
                "RECOVERY_BACKLOG",
                "Unresolved damage recovery",
                String.format(
                        "%.1f MUSD",
                        metrics.unresolvedDamageLossMusd()
                )
        );

        blockers.sort(
                Comparator
                        .comparingInt(
                                CommandBlocker::weight
                        )
                        .reversed()
                        .thenComparing(
                                CommandBlocker::code
                        )
        );

        return new IncidentCommandPulseResponse(
                incidentId,

                score,
                band,

                metrics.openActionCount(),
                metrics.overdueActionCount(),
                metrics.blockedActionCount(),

                metrics.resourceShortageCount(),

                metrics.evacuationRemaining(),
                metrics.shelterRemainingCapacity(),

                metrics.lifelineDisruptedCount(),
                metrics.criticalLifelineDisruptionCount(),
                metrics.lifelineAffectedPopulation(),

                metrics.unresolvedDamageLossMusd(),
                metrics.criticalDamageCount(),
                metrics.averageRepairProgressPct(),

                List.copyOf(
                        blockers
                ),

                Instant.now()
        );
    }

    private int evacuationWeight(
            long evacuationRemaining
    ) {
        if (
                evacuationRemaining
                >= 10000
        ) {
            return 15;
        }

        if (
                evacuationRemaining
                >= 1000
        ) {
            return 10;
        }

        if (
                evacuationRemaining > 0
        ) {
            return 5;
        }

        return 0;
    }

    private int shelterWeight(
            long evacuationRemaining,
            long shelterRemainingCapacity
    ) {
        if (
                evacuationRemaining > 0
                && shelterRemainingCapacity
                < evacuationRemaining
        ) {
            return 10;
        }

        return 0;
    }

    private void addBlocker(
            List<CommandBlocker> blockers,
            int weight,
            String code,
            String label,
            String value
    ) {
        if (
                weight <= 0
        ) {
            return;
        }

        blockers.add(
                new CommandBlocker(
                        code,
                        label,
                        value,
                        weight
                )
        );
    }

    private IncidentCase requireIncident(
            UUID incidentId
    ) {
        return incidentRepository
                .findById(
                        incidentId
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Incident "
                                        + incidentId
                                        + " was not found."
                        )
                );
    }

    private void requireMutableIncident(
            UUID incidentId
    ) {
        IncidentCase incident =
                requireIncident(
                        incidentId
                );

        if (
                incident.status()
                == IncidentStatus.CLOSED
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Closed incidents cannot create command snapshots."
            );
        }
    }
}
