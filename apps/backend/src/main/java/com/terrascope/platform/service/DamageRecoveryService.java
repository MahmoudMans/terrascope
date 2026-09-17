package com.terrascope.platform.service;

import com.terrascope.platform.api.model.CreateDamageAssessmentRequest;
import com.terrascope.platform.api.model.DamageAssessment;
import com.terrascope.platform.api.model.DamageRecoveryBoardResponse;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.UpdateDamageOwnerRequest;
import com.terrascope.platform.api.model.UpdateDamageProgressRequest;
import com.terrascope.platform.api.model.UpdateDamageRecoveryEtaRequest;
import com.terrascope.platform.api.model.UpdateDamageSeverityRequest;
import com.terrascope.platform.domain.DamageAssetCategory;
import com.terrascope.platform.domain.DamageRecoveryStatus;
import com.terrascope.platform.domain.DamageSeverity;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.repository.DamageRecoveryRepository;
import com.terrascope.platform.repository.IncidentResponseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DamageRecoveryService {

    private final DamageRecoveryRepository repository;
    private final IncidentResponseRepository incidentRepository;

    public DamageRecoveryService(
            DamageRecoveryRepository repository,
            IncidentResponseRepository incidentRepository
    ) {
        this.repository = repository;
        this.incidentRepository = incidentRepository;
    }

    public DamageRecoveryBoardResponse board(
            UUID incidentId
    ) {
        requireIncident(
                incidentId
        );

        List<DamageAssessment> assessments =
                repository.findAll(
                        incidentId
                );

        int reportedCount =
                countStatus(
                        assessments,
                        DamageRecoveryStatus.REPORTED
                );

        int verifiedCount =
                countStatus(
                        assessments,
                        DamageRecoveryStatus.VERIFIED
                );

        int repairingCount =
                countStatus(
                        assessments,
                        DamageRecoveryStatus.REPAIRING
                );

        int restoredCount =
                countStatus(
                        assessments,
                        DamageRecoveryStatus.RESTORED
                );

        int criticalDamageCount =
                (int) assessments
                        .stream()
                        .filter(assessment ->
                                assessment.severity()
                                        .critical()
                                && !assessment.status()
                                        .restored()
                        )
                        .count();

        long affectedUnits =
                assessments
                        .stream()
                        .mapToLong(
                                DamageAssessment::affectedUnits
                        )
                        .sum();

        double totalEstimatedLossMusd =
                assessments
                        .stream()
                        .mapToDouble(
                                DamageAssessment::estimatedLossMusd
                        )
                        .sum();

        double unresolvedEstimatedLossMusd =
                assessments
                        .stream()
                        .filter(assessment ->
                                !assessment.status()
                                        .restored()
                        )
                        .mapToDouble(
                                DamageAssessment::estimatedLossMusd
                        )
                        .sum();

        double averageRepairProgressPct =
                assessments
                        .stream()
                        .mapToInt(
                                DamageAssessment::repairProgressPct
                        )
                        .average()
                        .orElse(0.0);

        return new DamageRecoveryBoardResponse(
                incidentId,

                assessments.size(),
                reportedCount,
                verifiedCount,
                repairingCount,
                restoredCount,

                criticalDamageCount,

                affectedUnits,

                totalEstimatedLossMusd,
                unresolvedEstimatedLossMusd,

                averageRepairProgressPct,

                assessments
        );
    }

    @Transactional
    public DamageRecoveryBoardResponse create(
            UUID incidentId,
            CreateDamageAssessmentRequest request
    ) {
        requireMutableIncident(
                incidentId
        );

        DamageAssetCategory category =
                DamageAssetCategory.fromValue(
                        request.category()
                );

        DamageSeverity severity =
                DamageSeverity.fromValue(
                        request.severity()
                );

        String owner =
                normalizeOwner(
                        request.owner()
                );

        DamageAssessment assessment =
                repository.create(
                        UUID.randomUUID(),
                        incidentId,
                        request,
                        category,
                        severity,
                        owner
                );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "DAMAGE_ASSESSMENT_CREATED",
                "Damage assessment reported for '"
                        + assessment.assetName()
                        + "' with "
                        + assessment.severity().name()
                        + " severity."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public DamageRecoveryBoardResponse verify(
            UUID incidentId,
            UUID assessmentId
    ) {
        requireMutableIncident(
                incidentId
        );

        DamageAssessment before =
                requireAssessment(
                        incidentId,
                        assessmentId
                );

        if (
                before.status()
                != DamageRecoveryStatus.REPORTED
        ) {
            return board(
                    incidentId
            );
        }

        repository.verify(
                incidentId,
                assessmentId
        );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "DAMAGE_VERIFIED",
                "Damage assessment verified for '"
                        + before.assetName()
                        + "'."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public DamageRecoveryBoardResponse updateProgress(
            UUID incidentId,
            UUID assessmentId,
            UpdateDamageProgressRequest request
    ) {
        requireMutableIncident(
                incidentId
        );

        DamageAssessment before =
                requireAssessment(
                        incidentId,
                        assessmentId
                );

        if (
                before.status()
                == DamageRecoveryStatus.REPORTED
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Damage assessment must be verified before repair progress can be updated."
            );
        }

        int progress =
                request.repairProgressPct();

        if (
                before.status()
                == DamageRecoveryStatus.RESTORED
        ) {
            if (
                    progress
                    == 100
            ) {
                return board(
                        incidentId
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Restored damage assessments cannot return to an active recovery state."
            );
        }

        DamageRecoveryStatus status =
                deriveRecoveryStatus(
                        progress
                );

        if (
                before.repairProgressPct()
                == progress
                && before.status()
                == status
        ) {
            return board(
                    incidentId
            );
        }

        DamageAssessment after =
                repository.updateProgress(
                        incidentId,
                        assessmentId,
                        progress,
                        status
                );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "DAMAGE_PROGRESS_CHANGED",
                "Repair progress for '"
                        + before.assetName()
                        + "' changed from "
                        + before.repairProgressPct()
                        + "% to "
                        + after.repairProgressPct()
                        + "%. Status is "
                        + after.status().name()
                        + "."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public DamageRecoveryBoardResponse updateSeverity(
            UUID incidentId,
            UUID assessmentId,
            UpdateDamageSeverityRequest request
    ) {
        requireMutableIncident(
                incidentId
        );

        DamageAssessment before =
                requireActiveAssessment(
                        incidentId,
                        assessmentId
                );

        DamageSeverity severity =
                DamageSeverity.fromValue(
                        request.severity()
                );

        if (
                before.severity()
                == severity
        ) {
            return board(
                    incidentId
            );
        }

        repository.updateSeverity(
                incidentId,
                assessmentId,
                severity
        );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "DAMAGE_SEVERITY_CHANGED",
                "Damage severity for '"
                        + before.assetName()
                        + "' changed from "
                        + before.severity().name()
                        + " to "
                        + severity.name()
                        + "."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public DamageRecoveryBoardResponse updateOwner(
            UUID incidentId,
            UUID assessmentId,
            UpdateDamageOwnerRequest request
    ) {
        requireMutableIncident(
                incidentId
        );

        DamageAssessment before =
                requireActiveAssessment(
                        incidentId,
                        assessmentId
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
                assessmentId,
                owner
        );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "DAMAGE_OWNER_CHANGED",
                "Recovery owner for '"
                        + before.assetName()
                        + "' changed from '"
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
    public DamageRecoveryBoardResponse updateRecoveryEta(
            UUID incidentId,
            UUID assessmentId,
            UpdateDamageRecoveryEtaRequest request
    ) {
        requireMutableIncident(
                incidentId
        );

        DamageAssessment before =
                requireActiveAssessment(
                        incidentId,
                        assessmentId
                );

        Instant beforeEta =
                before.recoveryEta();

        Instant afterEta =
                request.recoveryEta();

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

        repository.updateRecoveryEta(
                incidentId,
                assessmentId,
                afterEta
        );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "DAMAGE_ETA_CHANGED",
                "Recovery ETA updated for '"
                        + before.assetName()
                        + "'."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public DamageRecoveryBoardResponse delete(
            UUID incidentId,
            UUID assessmentId
    ) {
        requireMutableIncident(
                incidentId
        );

        DamageAssessment assessment =
                requireAssessment(
                        incidentId,
                        assessmentId
                );

        int deleted =
                repository.delete(
                        incidentId,
                        assessmentId
                );

        if (
                deleted != 1
        ) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Damage assessment "
                            + assessmentId
                            + " was not found."
            );
        }

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "DAMAGE_ASSESSMENT_DELETED",
                "Damage assessment deleted for '"
                        + assessment.assetName()
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

    private DamageAssessment requireAssessment(
            UUID incidentId,
            UUID assessmentId
    ) {
        return repository
                .findById(
                        incidentId,
                        assessmentId
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Damage assessment "
                                        + assessmentId
                                        + " was not found."
                        )
                );
    }

    private DamageAssessment requireActiveAssessment(
            UUID incidentId,
            UUID assessmentId
    ) {
        DamageAssessment assessment =
                requireAssessment(
                        incidentId,
                        assessmentId
                );

        if (
                assessment.status()
                == DamageRecoveryStatus.RESTORED
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Restored damage assessments are read-only."
            );
        }

        return assessment;
    }

    private DamageRecoveryStatus deriveRecoveryStatus(
            int progress
    ) {
        if (
                progress >= 100
        ) {
            return DamageRecoveryStatus.RESTORED;
        }

        if (
                progress > 0
        ) {
            return DamageRecoveryStatus.REPAIRING;
        }

        return DamageRecoveryStatus.VERIFIED;
    }

    private int countStatus(
            List<DamageAssessment> assessments,
            DamageRecoveryStatus status
    ) {
        return (int) assessments
                .stream()
                .filter(assessment ->
                        assessment.status()
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
