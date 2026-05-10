package xyz.winhok.nettap;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.LinkedHashMap;

public final class CronetHeadersTest {
    @Test
    public void fromArrayPairsHeaderNamesAndValues() {
        LinkedHashMap<String, String> headers = CronetHeaders.fromArray(new String[] {
                "content-type", "application/json",
                "x-trace-id", "abc123"
        });

        assertEquals("application/json", headers.get("content-type"));
        assertEquals("abc123", headers.get("x-trace-id"));
        assertEquals(2, headers.size());
    }

    @Test
    public void fromArraySkipsIncompleteAndNullNames() {
        LinkedHashMap<String, String> headers = CronetHeaders.fromArray(new String[] {
                null, "skipped",
                "server", "cronet",
                "dangling"
        });

        assertEquals("cronet", headers.get("server"));
        assertEquals(1, headers.size());
    }
}
