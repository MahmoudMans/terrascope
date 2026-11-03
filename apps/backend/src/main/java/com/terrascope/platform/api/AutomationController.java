package com.terrascope.platform.api;

import com.terrascope.platform.api.model.AutomationStatus;
import com.terrascope.platform.service.FeedIngestionScheduler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/automation")
public class AutomationController {

    private final FeedIngestionScheduler scheduler;

    public AutomationController(
            FeedIngestionScheduler scheduler
    ) {
        this.scheduler = scheduler;
    }

    @GetMapping("/status")
    public AutomationStatus status() {
        return scheduler.status();
    }
}
