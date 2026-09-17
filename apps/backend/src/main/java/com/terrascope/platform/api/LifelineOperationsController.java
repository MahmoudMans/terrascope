package com.terrascope.platform.api;

import com.terrascope.platform.api.model.CreateLifelineAssetRequest;
import com.terrascope.platform.api.model.LifelineOperationsBoardResponse;
import com.terrascope.platform.api.model.UpdateLifelineImpactRequest;
import com.terrascope.platform.api.model.UpdateLifelineOwnerRequest;
import com.terrascope.platform.api.model.UpdateLifelineRestorationEtaRequest;
import com.terrascope.platform.api.model.UpdateLifelineStatusRequest;
import com.terrascope.platform.service.LifelineOperationsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents/{incidentId}/lifelines")
public class LifelineOperationsController {

    private final LifelineOperationsService service;

    public LifelineOperationsController(
            LifelineOperationsService service
    ) {
        this.service = service;
    }

    @GetMapping
    public LifelineOperationsBoardResponse board(
            @PathVariable
            UUID incidentId
    ) {
        return service.board(
                incidentId
        );
    }

    @PostMapping
    public LifelineOperationsBoardResponse create(
            @PathVariable
            UUID incidentId,

            @Valid
            @RequestBody
            CreateLifelineAssetRequest request
    ) {
        return service.create(
                incidentId,
                request
        );
    }

    @PatchMapping("/{assetId}/status")
    public LifelineOperationsBoardResponse updateStatus(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID assetId,

            @Valid
            @RequestBody
            UpdateLifelineStatusRequest request
    ) {
        return service.updateStatus(
                incidentId,
                assetId,
                request
        );
    }

    @PatchMapping("/{assetId}/impact")
    public LifelineOperationsBoardResponse updateImpact(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID assetId,

            @Valid
            @RequestBody
            UpdateLifelineImpactRequest request
    ) {
        return service.updateImpact(
                incidentId,
                assetId,
                request
        );
    }

    @PatchMapping("/{assetId}/owner")
    public LifelineOperationsBoardResponse updateOwner(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID assetId,

            @Valid
            @RequestBody
            UpdateLifelineOwnerRequest request
    ) {
        return service.updateOwner(
                incidentId,
                assetId,
                request
        );
    }

    @PatchMapping("/{assetId}/restoration-eta")
    public LifelineOperationsBoardResponse updateRestorationEta(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID assetId,

            @RequestBody
            UpdateLifelineRestorationEtaRequest request
    ) {
        return service.updateRestorationEta(
                incidentId,
                assetId,
                request
        );
    }

    @DeleteMapping("/{assetId}")
    public LifelineOperationsBoardResponse delete(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID assetId
    ) {
        return service.delete(
                incidentId,
                assetId
        );
    }
}
