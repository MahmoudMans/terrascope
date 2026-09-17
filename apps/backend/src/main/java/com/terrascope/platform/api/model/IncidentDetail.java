package com.terrascope.platform.api.model;

import java.util.List;

public record IncidentDetail(
        IncidentCase incident,
        List<IncidentTimelineEvent> timeline
) {
}
