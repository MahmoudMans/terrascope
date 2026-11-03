package com.terrascope.platform.api;

import com.terrascope.platform.activity.ActivityService;
import com.terrascope.platform.api.model.ActivityPageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/v1/activity")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(
            ActivityService activityService
    ) {
        this.activityService = activityService;
    }

    @GetMapping
    public ActivityPageResponse list(
            @RequestParam(required = false)
            Integer limit,

            @RequestParam(required = false)
            String before,

            @RequestParam(required = false)
            String actor
    ) {
        return ActivityPageResponse.from(
                activityService.page(
                        limit,
                        parseInstant(before),
                        actor
                )
        );
    }

    private static Instant parseInstant(
            String value
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            return null;
        }

        try {
            return Instant.parse(
                    value.trim()
            );

        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "before must be an ISO-8601 timestamp such as 2026-01-31T10:15:30Z"
            );
        }
    }
}
