package com.terrascope.platform.api;

import com.terrascope.platform.api.model.CommandPulseHistoryResponse;
import com.terrascope.platform.api.model.CommandPulseSnapshot;
import com.terrascope.platform.api.model.IncidentCommandPulseResponse;
import com.terrascope.platform.service.IncidentCommandPulseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents/{incidentId}/command-pulse")
public class IncidentCommandPulseController {

    private final IncidentCommandPulseService service;

    public IncidentCommandPulseController(
            IncidentCommandPulseService service
    ) {
        this.service = service;
    }

    @GetMapping
    public IncidentCommandPulseResponse pulse(
            @PathVariable
            UUID incidentId
    ) {
        return service.pulse(
                incidentId
        );
    }

    @GetMapping("/history")
    public CommandPulseHistoryResponse history(
            @PathVariable
            UUID incidentId
    ) {
        return service.history(
                incidentId
        );
    }

    @PostMapping("/snapshots")
    public CommandPulseSnapshot createSnapshot(
            @PathVariable
            UUID incidentId
    ) {
        return service.createSnapshot(
                incidentId
        );
    }
}
