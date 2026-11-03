package com.terrascope.platform.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveEventBroadcasterTest {

    private final LiveEventBroadcaster broadcaster =
            new LiveEventBroadcaster();

    @Test
    void registersEachSubscriber() {
        assertEquals(0, broadcaster.clientCount());

        assertNotNull(broadcaster.subscribe());
        assertNotNull(broadcaster.subscribe());

        assertEquals(2, broadcaster.clientCount());
    }

    @Test
    void stopsAcceptingSubscribersAtTheLimit() {
        for (int index = 0; index < LiveEventBroadcaster.MAX_CLIENTS; index++) {
            assertTrue(broadcaster.hasCapacity());

            broadcaster.subscribe();
        }

        assertFalse(broadcaster.hasCapacity());
        assertEquals(
                LiveEventBroadcaster.MAX_CLIENTS,
                broadcaster.clientCount()
        );
    }

    @Test
    void publishAndHeartbeatTolerateSubscribersThatHaveNoConnectionYet() {
        broadcaster.subscribe();

        broadcaster.publish("alerts", java.util.Map.of("count", 1));
        broadcaster.heartbeat();

        assertEquals(1, broadcaster.clientCount());
    }

    @Test
    void closingEverythingEmptiesTheRegistry() {
        broadcaster.subscribe();
        broadcaster.subscribe();

        broadcaster.closeAll();

        assertEquals(0, broadcaster.clientCount());
        assertTrue(broadcaster.hasCapacity());
    }
}
