package com.terrascope.platform.api;

import com.terrascope.platform.api.model.IncidentSitrepHistoryResponse;
import com.terrascope.platform.api.model.IncidentSitrepPreview;
import com.terrascope.platform.api.model.IncidentSituationReport;
import com.terrascope.platform.service.IncidentSituationReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents/{incidentId}/sitrep")
public class IncidentSituationReportController {

    private final IncidentSituationReportService service;

    public IncidentSituationReportController(
            IncidentSituationReportService service
    ) {
        this.service = service;
    }

    @GetMapping("/preview")
    public IncidentSitrepPreview preview(
            @PathVariable
            UUID incidentId
    ) {
        return service.preview(
                incidentId
        );
    }

    @GetMapping("/reports")
    public IncidentSitrepHistoryResponse history(
            @PathVariable
            UUID incidentId
    ) {
        return service.history(
                incidentId
        );
    }

    @PostMapping("/reports")
    public IncidentSituationReport createReport(
            @PathVariable
            UUID incidentId
    ) {
        return service.createReport(
                incidentId
        );
    }
}
