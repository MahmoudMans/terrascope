package com.terrascope.platform.api;

import com.terrascope.platform.api.model.ImpactSimulationRequest;
import com.terrascope.platform.api.model.ImpactSimulationRunResponse;
import com.terrascope.platform.api.model.SimulationHistoryResponse;
import com.terrascope.platform.service.ImpactSimulationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulations")
public class ImpactSimulationController {

    private final ImpactSimulationService service;

    public ImpactSimulationController(
            ImpactSimulationService service
    ) {
        this.service = service;
    }

    @GetMapping
    public SimulationHistoryResponse history() {
        return service.history();
    }

    @PostMapping("/run")
    public ImpactSimulationRunResponse run(
            @Valid
            @RequestBody
            ImpactSimulationRequest request
    ) {
        return service.run(
                request
        );
    }
}
