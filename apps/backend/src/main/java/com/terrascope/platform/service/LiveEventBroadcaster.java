package com.terrascope.platform.service;

import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class LiveEventBroadcaster {

    static final int MAX_CLIENTS = 200;

    private static final long EMITTER_TIMEOUT_MS =
            30L * 60L * 1000L;

    private static final long HEARTBEAT_INTERVAL_MS =
            20000L;

    private final List<SseEmitter> emitters =
            new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter =
                new SseEmitter(
                        EMITTER_TIMEOUT_MS
                );

        emitter.onCompletion(() ->
                emitters.remove(emitter)
        );

        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });

        emitter.onError(error ->
                emitters.remove(emitter)
        );

        emitters.add(emitter);

        send(
                emitter,
                SseEmitter
                        .event()
                        .name("ready")
                        .data(
                                "connected",
                                MediaType.TEXT_PLAIN
                        )
        );

        return emitter;
    }

    public void publish(
            String eventName,
            Object payload
    ) {
        for (SseEmitter emitter : emitters) {
            send(
                    emitter,
                    SseEmitter
                            .event()
                            .name(eventName)
                            .data(
                                    payload,
                                    MediaType.APPLICATION_JSON
                            )
            );
        }
    }

    public int clientCount() {
        return emitters.size();
    }

    public boolean hasCapacity() {
        return emitters.size() < MAX_CLIENTS;
    }

    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
    public void heartbeat() {
        for (SseEmitter emitter : emitters) {
            send(
                    emitter,
                    SseEmitter
                            .event()
                            .comment("keep-alive")
            );
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void closeAll() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.complete();
            } catch (RuntimeException ignored) {
                emitters.remove(emitter);
            }
        }

        emitters.clear();
    }

    private void send(
            SseEmitter emitter,
            SseEmitter.SseEventBuilder event
    ) {
        synchronized (emitter) {
            try {
                emitter.send(event);
            } catch (
                    IOException
                    | IllegalStateException exception
            ) {
                emitters.remove(emitter);
            }
        }
    }
}
