package xyz.winhok.nettap.ui;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import xyz.winhok.nettap.ui.data.CaptureSessionStore;
import xyz.winhok.nettap.ui.data.CaptureUiEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class CaptureSessionActionsTest {
    @Before
    public void setUp() {
        NetTapUiState.stopRealtimeServer();
        NetTapUiState.configureStore(20, 10);
        NetTapUiState.store().clear();
        NetTapUiState.store().resume();
        NetTapUiState.setHostFilter(null);
        NetTapUiState.setHookFilter(null);
        NetTapUiState.setPackageFilter(null);
        NetTapUiState.setDetailQuery("");
    }

    @After
    public void tearDown() {
        NetTapUiState.setHostFilter(null);
        NetTapUiState.setHookFilter(null);
        NetTapUiState.setPackageFilter(null);
        NetTapUiState.setDetailQuery("");
        NetTapUiState.store().clear();
        NetTapUiState.store().resume();
    }

    @Test
    public void clearCurrentSessionClearsStoreFiltersAndSearchState() {
        boolean[] queryCleared = {false};
        NetTapUiState.store().addFromRealtime(CaptureUiEvent.fromJson(sampleJson()));
        NetTapUiState.setHostFilter("api.example.com");
        NetTapUiState.setHookFilter("okhttp");
        NetTapUiState.setPackageFilter("com.example");
        NetTapUiState.setDetailQuery("users");

        CaptureSessionActions.clearCurrentSession(() -> queryCleared[0] = true);

        assertEquals(0, NetTapUiState.store().totalCount());
        assertEquals(CaptureSessionStore.State.CLEARED, NetTapUiState.store().getState());
        assertNull(NetTapUiState.getHostFilter());
        assertNull(NetTapUiState.getHookFilter());
        assertNull(NetTapUiState.getPackageFilter());
        assertEquals("", NetTapUiState.getDetailQuery());
        assertTrue(queryCleared[0]);
    }

    private static String sampleJson() {
        return "{"
                + "\"schemaVersion\":2,"
                + "\"id\":\"request-1\","
                + "\"timestamp\":\"2026-05-14T00:00:00.000Z\","
                + "\"packageName\":\"com.example\","
                + "\"hook\":\"okhttp\","
                + "\"method\":\"GET\","
                + "\"url\":\"https://api.example.com/users\","
                + "\"requestHeaders\":{},"
                + "\"requestBody\":{},"
                + "\"responseCode\":200,"
                + "\"responseMessage\":\"OK\","
                + "\"responseHeaders\":{},"
                + "\"responseBody\":{},"
                + "\"durationMs\":1,"
                + "\"error\":null"
                + "}";
    }
}
