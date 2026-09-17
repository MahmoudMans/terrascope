package com.terrascope.platform.api;

import com.terrascope.platform.api.model.AssessPreparednessExerciseRequest;
import com.terrascope.platform.api.model.CreatePreparednessExerciseRequest;
import com.terrascope.platform.api.model.PreparednessExerciseBoardResponse;
import com.terrascope.platform.api.model.UpdatePreparednessExerciseOwnerRequest;
import com.terrascope.platform.api.model.UpdatePreparednessExerciseScheduleRequest;
import com.terrascope.platform.api.model.UpdatePreparednessExerciseStatusRequest;
import com.terrascope.platform.service.PreparednessExerciseService;
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
@RequestMapping("/api/v1/incidents/{incidentId}/preparedness-exercises")
public class PreparednessExerciseController {

    private final PreparednessExerciseService service;

    public PreparednessExerciseController(
            PreparednessExerciseService service
    ) {
        this.service = service;
    }

    @GetMapping
    public PreparednessExerciseBoardResponse board(
            @PathVariable
            UUID incidentId
    ) {
        return service.board(
                incidentId
        );
    }

    @PostMapping
    public PreparednessExerciseBoardResponse create(
            @PathVariable
            UUID incidentId,

            @Valid
            @RequestBody
            CreatePreparednessExerciseRequest request
    ) {
        return service.create(
                incidentId,
                request
        );
    }

    @PatchMapping("/{exerciseId}/status")
    public PreparednessExerciseBoardResponse updateStatus(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID exerciseId,

            @Valid
            @RequestBody
            UpdatePreparednessExerciseStatusRequest request
    ) {
        return service.updateStatus(
                incidentId,
                exerciseId,
                request
        );
    }

    @PatchMapping("/{exerciseId}/owner")
    public PreparednessExerciseBoardResponse updateOwner(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID exerciseId,

            @Valid
            @RequestBody
            UpdatePreparednessExerciseOwnerRequest request
    ) {
        return service.updateOwner(
                incidentId,
                exerciseId,
                request
        );
    }

    @PatchMapping("/{exerciseId}/schedule")
    public PreparednessExerciseBoardResponse updateSchedule(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID exerciseId,

            @RequestBody
            UpdatePreparednessExerciseScheduleRequest request
    ) {
        return service.updateSchedule(
                incidentId,
                exerciseId,
                request
        );
    }

    @PostMapping("/{exerciseId}/assess")
    public PreparednessExerciseBoardResponse assess(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID exerciseId,

            @Valid
            @RequestBody
            AssessPreparednessExerciseRequest request
    ) {
        return service.assess(
                incidentId,
                exerciseId,
                request
        );
    }

    @DeleteMapping("/{exerciseId}")
    public PreparednessExerciseBoardResponse delete(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID exerciseId
    ) {
        return service.delete(
                incidentId,
                exerciseId
        );
    }
}
