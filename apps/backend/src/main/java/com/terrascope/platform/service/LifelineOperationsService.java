package com.terrascope.platform.service;

import com.terrascope.platform.api.model.CreateLifelineAssetRequest;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.LifelineAsset;
import com.terrascope.platform.api.model.LifelineOperationsBoardResponse;
import com.terrascope.platform.api.model.UpdateLifelineImpactRequest;
import com.terrascope.platform.api.model.UpdateLifelineOwnerRequest;
import com.terrascope.platform.api.model.UpdateLifelineRestorationEtaRequest;
import com.terrascope.platform.api.model.UpdateLifelineStatusRequest;
import com.terrascope.platform.domain.IncidentActionPriority;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.domain.LifelineAssetCategory;
import com.terrascope.platform.domain.LifelineAssetStatus;
import com.terrascope.platform.repository.IncidentResponseRepository;
import com.terrascope.platform.repository.LifelineOperationsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class LifelineOperationsService {

    private final LifelineOperationsRepository repository;
    private final IncidentResponseRepository incidentRepository;

    public LifelineOperationsService(
            LifelineOperationsRepository repository,
            IncidentResponseRepository incidentRepository
    ) {
        this.repository = repository;
        this.incidentRepository = incidentRepository;
    }

    public LifelineOperationsBoardResponse board(
            UUID incidentId
    ) {
        requireIncident(
                incidentId
        );

        List<LifelineAsset> assets =
                repository.findAll(
                        incidentId
                );

        int operationalCount =
                countStatus(
                        assets,
                        LifelineAssetStatus.OPERATIONAL
                );

        int degradedCount =
                countStatus(
                        assets,
                        LifelineAssetStatus.DEGRADED
                );

        int outageCount =
                countStatus(
                        assets,
                        LifelineAssetStatus.OUTAGE
                );

        int restoredCount =
                countStatus(
                        assets,
                        LifelineAssetStatus.RESTORED
                );

        int disruptedCount =
                degradedCount
                        + outageCount;

        int criticalDisruptionCount =
                (int) assets
                        .stream()
                        .filter(asset ->
                                asset.status().disrupted()
                                && asset.priority()
                                        == IncidentActionPriority.CRITICAL
                        )
                        .count();

        long affectedPopulation =
                assets
                        .stream()
                        .filter(asset ->
                                asset.status().disrupted()
                        )
                        .mapToLong(
                                LifelineAsset::affectedPopulation
                        )
                        .sum();

        int etaTrackedCount =
                (int) assets
                        .stream()
                        .filter(asset ->
                                asset.status().disrupted()
                                && asset.restorationEta()
                                        != null
                        )
                        .count();

        return new LifelineOperationsBoardResponse(
                incidentId,

                assets.size(),
                operationalCount,
                degradedCount,
                outageCount,
                restoredCount,

                disruptedCount,
                criticalDisruptionCount,

                affectedPopulation,
                etaTrackedCount,

                assets
        );
    }

    @Transactional
    public LifelineOperationsBoardResponse create(
            UUID incidentId,
            CreateLifelineAssetRequest request
    ) {
        requireMutableIncident(
                incidentId
        );

        LifelineAssetCategory category =
                LifelineAssetCategory.fromValue(
                        request.category()
                );

        IncidentActionPriority priority =
                IncidentActionPriority.fromValue(
                        request.priority()
                );

        LifelineAssetStatus status =
                LifelineAssetStatus.fromValue(
                        request.status()
                );

        validateImpactForStatus(
                status,
                request.affectedPopulation()
        );

        if (
                request.restorationEta()
                != null
                && !status.disrupted()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Restoration ETA is only valid for disrupted lifeline assets."
            );
        }

        String owner =
                normalizeOwner(
                        request.owner()
                );

        LifelineAsset asset =
                repository.create(
                        UUID.randomUUID(),
                        incidentId,
                        request,
                        category,
                        priority,
                        status,
                        owner
                );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "LIFELINE_ASSET_CREATED",
                "Lifeline asset created: '"
                        + asset.name()
                        + "' ("
                        + asset.category().name()
                        + ") with status "
                        + asset.status().name()
                        + "."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public LifelineOperationsBoardResponse updateStatus(
            UUID incidentId,
            UUID assetId,
            UpdateLifelineStatusRequest request
    ) {
        requireMutableIncident(
                incidentId
        );

        LifelineAsset before =
                requireAsset(
                        incidentId,
                        assetId
                );

        LifelineAssetStatus status =
                LifelineAssetStatus.fromValue(
                        request.status()
                );

        if (
                before.status()
                == status
        ) {
            return board(
                    incidentId
            );
        }

        LifelineAsset after =
                repository.updateStatus(
                        incidentId,
                        assetId,
                        status
                );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "LIFELINE_STATUS_CHANGED",
                "Lifeline asset '"
                        + before.name()
                        + "' status changed from "
                        + before.status().name()
                        + " to "
                        + after.status().name()
                        + "."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public LifelineOperationsBoardResponse updateImpact(
            UUID incidentId,
            UUID assetId,
            UpdateLifelineImpactRequest request
    ) {
        requireMutableIncident(
                incidentId
        );

        LifelineAsset before =
                requireDisruptedAsset(
                        incidentId,
                        assetId
                );

        if (
                before.affectedPopulation()
                == request.affectedPopulation()
        ) {
            return board(
                    incidentId
            );
        }

        LifelineAsset after =
                repository.updateImpact(
                        incidentId,
                        assetId,
                        request.affectedPopulation()
                );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "LIFELINE_IMPACT_CHANGED",
                "Affected population for '"
                        + before.name()
                        + "' changed from "
                        + before.affectedPopulation()
                        + " to "
                        + after.affectedPopulation()
                        + "."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public LifelineOperationsBoardResponse updateOwner(
            UUID incidentId,
            UUID assetId,
            UpdateLifelineOwnerRequest request
    ) {
        requireMutableIncident(
                incidentId
        );

        LifelineAsset before =
                requireAsset(
                        incidentId,
                        assetId
                );

        String owner =
                normalizeOwner(
                        request.owner()
                );

        if (
                before.owner()
                        .equals(owner)
        ) {
            return board(
                    incidentId
            );
        }

        repository.updateOwner(
                incidentId,
                assetId,
                owner
        );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "LIFELINE_OWNER_CHANGED",
                "Lifeline asset '"
                        + before.name()
                        + "' owner changed from '"
                        + before.owner()
                        + "' to '"
                        + owner
                        + "'."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public LifelineOperationsBoardResponse updateRestorationEta(
            UUID incidentId,
            UUID assetId,
            UpdateLifelineRestorationEtaRequest request
    ) {
        requireMutableIncident(
                incidentId
        );

        LifelineAsset before =
                requireDisruptedAsset(
                        incidentId,
                        assetId
                );

        Instant beforeEta =
                before.restorationEta();

        Instant afterEta =
                request.restorationEta();

        if (
                beforeEta == null
                ? afterEta == null
                : beforeEta.equals(
                        afterEta
                )
        ) {
            return board(
                    incidentId
            );
        }

        repository.updateRestorationEta(
                incidentId,
                assetId,
                afterEta
        );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "LIFELINE_ETA_CHANGED",
                "Restoration ETA updated for lifeline asset '"
                        + before.name()
                        + "'."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public LifelineOperationsBoardResponse delete(
            UUID incidentId,
            UUID assetId
    ) {
        requireMutableIncident(
                incidentId
        );

        LifelineAsset asset =
                requireAsset(
                        incidentId,
                        assetId
                );

        int deleted =
                repository.delete(
                        incidentId,
                        assetId
                );

        if (deleted != 1) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Lifeline asset "
                            + assetId
                            + " was not found."
            );
        }

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "LIFELINE_ASSET_DELETED",
                "Lifeline asset deleted: '"
                        + asset.name()
                        + "'."
        );

        return board(
                incidentId
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
                    "Closed incidents cannot be modified."
            );
        }
    }

    private LifelineAsset requireAsset(
            UUID incidentId,
            UUID assetId
    ) {
        return repository
                .findById(
                        incidentId,
                        assetId
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Lifeline asset "
                                        + assetId
                                        + " was not found."
                        )
                );
    }

    private LifelineAsset requireDisruptedAsset(
            UUID incidentId,
            UUID assetId
    ) {
        LifelineAsset asset =
                requireAsset(
                        incidentId,
                        assetId
                );

        if (
                !asset.status()
                        .disrupted()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only DEGRADED or OUTAGE lifeline assets accept disruption updates."
            );
        }

        return asset;
    }

    private void validateImpactForStatus(
            LifelineAssetStatus status,
            int affectedPopulation
    ) {
        if (
                !status.disrupted()
                && affectedPopulation != 0
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Operational or restored assets must have zero affected population."
            );
        }
    }

    private int countStatus(
            List<LifelineAsset> assets,
            LifelineAssetStatus status
    ) {
        return (int) assets
                .stream()
                .filter(asset ->
                        asset.status()
                                == status
                )
                .count();
    }

    private String normalizeOwner(
            String owner
    ) {
        if (
                owner == null
                || owner.isBlank()
        ) {
            return "Unassigned";
        }

        return owner.trim();
    }
}
