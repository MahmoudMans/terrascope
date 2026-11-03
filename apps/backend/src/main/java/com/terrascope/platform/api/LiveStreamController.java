package com.terrascope.platform.api;

import com.terrascope.platform.service.LiveEventBroadcaster;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/stream")
public class LiveStreamController {

    private final LiveEventBroadcaster broadcaster;

    public LiveStreamController(
            LiveEventBroadcaster broadcaster
    ) {
        this.broadcaster = broadcaster;
    }

    @GetMapping(
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter stream() {
        if (!broadcaster.hasCapacity()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Too many live connections."
            );
        }

        return broadcaster.subscribe();
    }
}
