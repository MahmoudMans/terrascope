package com.terrascope.platform.service;

import com.terrascope.platform.api.model.SaveWatchlistEventRequest;
import com.terrascope.platform.api.model.UpdateWatchPriorityRequest;
import com.terrascope.platform.api.model.WatchlistEntry;
import com.terrascope.platform.api.model.WatchlistFeedResponse;
import com.terrascope.platform.api.model.WatchlistSummary;
import com.terrascope.platform.domain.HazardType;
import com.terrascope.platform.domain.WatchPriority;
import com.terrascope.platform.repository.WatchlistRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class WatchlistService {

    private final WatchlistRepository repository;

    public WatchlistService(
            WatchlistRepository repository
    ) {
        this.repository = repository;
    }

    public WatchlistFeedResponse list() {
        List<WatchlistEntry> entries =
                repository.findAll();

        return new WatchlistFeedResponse(
                summarize(entries),
                entries
        );
    }

    public WatchlistEntry save(
            SaveWatchlistEventRequest request
    ) {
        HazardType hazardType =
                HazardType.fromKey(
                        request.hazardType()
                );

        WatchPriority priority =
                WatchPriority.fromValue(
                        request.priority()
                );

        return repository.upsert(
                request,
                hazardType,
                priority
        );
    }

    public WatchlistEntry updatePriority(
            UUID id,
            UpdateWatchPriorityRequest request
    ) {
        WatchPriority priority =
                WatchPriority.fromValue(
                        request.priority()
                );

        return repository
                .updatePriority(
                        id,
                        priority
                )
                .orElseThrow(() ->
                        notFound(id)
                );
    }

    public void delete(
            UUID id
    ) {
        int deleted =
                repository.deleteById(
                        id
                );

        if (deleted == 0) {
            throw notFound(id);
        }
    }

    private WatchlistSummary summarize(
            List<WatchlistEntry> entries
    ) {
        int criticalCount =
                countPriority(
                        entries,
                        WatchPriority.CRITICAL
                );

        int highCount =
                countPriority(
                        entries,
                        WatchPriority.HIGH
                );

        int earthquakeCount =
                countHazard(
                        entries,
                        HazardType.EARTHQUAKE
                );

        int volcanoCount =
                countHazard(
                        entries,
                        HazardType.VOLCANO
                );

        int wildfireCount =
                countHazard(
                        entries,
                        HazardType.WILDFIRE
                );

        int severeStormCount =
                countHazard(
                        entries,
                        HazardType.SEVERE_STORM
                );

        Instant latestSavedAt =
                entries
                        .stream()
                        .map(
                                WatchlistEntry::createdAt
                        )
                        .max(
                                Instant::compareTo
                        )
                        .orElse(null);

        return new WatchlistSummary(
                entries.size(),
                criticalCount,
                highCount,
                earthquakeCount,
                volcanoCount,
                wildfireCount,
                severeStormCount,
                latestSavedAt
        );
    }

    private int countPriority(
            List<WatchlistEntry> entries,
            WatchPriority priority
    ) {
        return (int) entries
                .stream()
                .filter(entry ->
                        entry.priority()
                                == priority
                )
                .count();
    }

    private int countHazard(
            List<WatchlistEntry> entries,
            HazardType hazardType
    ) {
        return (int) entries
                .stream()
                .filter(entry ->
                        entry.hazardType()
                                .equals(
                                        hazardType.key()
                                )
                )
                .count();
    }

    private ResponseStatusException notFound(
            UUID id
    ) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Watchlist entry "
                        + id
                        + " was not found."
        );
    }
}
