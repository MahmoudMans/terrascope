package com.terrascope.platform.api;

import com.terrascope.platform.api.model.SaveWatchlistEventRequest;
import com.terrascope.platform.api.model.UpdateWatchPriorityRequest;
import com.terrascope.platform.api.model.WatchlistEntry;
import com.terrascope.platform.api.model.WatchlistFeedResponse;
import com.terrascope.platform.service.WatchlistService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/watchlist")
public class WatchlistController {

    private final WatchlistService service;

    public WatchlistController(
            WatchlistService service
    ) {
        this.service = service;
    }

    @GetMapping
    public WatchlistFeedResponse list() {
        return service.list();
    }

    @PostMapping
    public WatchlistEntry save(
            @Valid
            @RequestBody
            SaveWatchlistEventRequest request
    ) {
        return service.save(
                request
        );
    }

    @PatchMapping("/{id}/priority")
    public WatchlistEntry updatePriority(
            @PathVariable
            UUID id,

            @Valid
            @RequestBody
            UpdateWatchPriorityRequest request
    ) {
        return service.updatePriority(
                id,
                request
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable
            UUID id
    ) {
        service.delete(
                id
        );
    }
}
