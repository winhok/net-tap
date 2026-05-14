package xyz.winhok.nettap.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class RealtimeStatusFormatterTest {
    @Test
    public void formatsListeningStatusWhenNoErrorExists() {
        assertEquals(
                "Listening on 127.0.0.1:39287",
                RealtimeStatusFormatter.format(39287, "", true)
        );
    }

    @Test
    public void formatsUnavailableStatusWhenServerStartupFailed() {
        assertEquals(
                "Realtime unavailable on 127.0.0.1:39287: failed to start realtime server",
                RealtimeStatusFormatter.format(39287, "failed to start realtime server", true)
        );
    }

    @Test
    public void formatsDisabledStatusWhenTransportIsOff() {
        assertEquals(
                "Realtime transport disabled",
                RealtimeStatusFormatter.format(39287, "failed to start realtime server", false)
        );
    }
}
