package xyz.winhok.nettap.ui.adapter;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;

import androidx.recyclerview.widget.RecyclerView;

import xyz.winhok.nettap.ui.data.CaptureUiEvent;
import xyz.winhok.nettap.ui.data.CookieParser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class DetailListAdapterTest {
    @Test
    public void headerListAdapterIsRecyclerAdapterAndKeepsSnapshot() {
        HeaderListAdapter adapter = new HeaderListAdapter();
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");

        adapter.replaceHeaders(headers);

        assertTrue(adapter instanceof RecyclerView.Adapter);
        assertEquals(1, adapter.getItemCount());
        assertEquals("application/json", adapter.snapshot().get("Content-Type"));
    }

    @Test
    public void headerListAdapterFormatsCopiedRowText() {
        HeaderListAdapter adapter = new HeaderListAdapter();
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer abc");

        adapter.replaceHeaders(headers);

        assertEquals("Authorization: Bearer abc", adapter.copyTextAt(0));
    }

    @Test
    public void headerListAdapterStoresCurrentHighlightQuery() {
        HeaderListAdapter adapter = new HeaderListAdapter();
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");

        adapter.replaceHeaders(headers, "  json  ");

        assertEquals("json", adapter.highlightQuery());
    }

    @Test
    public void cookieListAdapterIsRecyclerAdapterAndKeepsSnapshot() {
        CaptureUiEvent event = CaptureUiEvent.fromJson(sampleJson());
        CookieListAdapter adapter = new CookieListAdapter();

        adapter.replaceCookies(CookieParser.parse(event));

        assertTrue(adapter instanceof RecyclerView.Adapter);
        assertEquals(2, adapter.getItemCount());
        assertEquals(Arrays.asList("sid", "id"), Arrays.asList(
                adapter.snapshot().get(0).getName(),
                adapter.snapshot().get(1).getName()
        ));
    }

    @Test
    public void cookieListAdapterFormatsCopiedRowText() {
        CaptureUiEvent event = CaptureUiEvent.fromJson(sampleJson());
        CookieListAdapter adapter = new CookieListAdapter();

        adapter.replaceCookies(CookieParser.parse(event));

        assertEquals("REQUEST  sid=abc", adapter.copyTextAt(0));
        assertEquals("RESPONSE  id=1  Secure", adapter.copyTextAt(1));
    }

    @Test
    public void cookieListAdapterCopiesOnlyNameValuePairToClipboard() {
        CaptureUiEvent event = CaptureUiEvent.fromJson(sampleJson());
        CookieListAdapter adapter = new CookieListAdapter();

        adapter.replaceCookies(CookieParser.parse(event));

        assertEquals("sid=abc", adapter.clipboardTextAt(0));
        assertEquals("id=1", adapter.clipboardTextAt(1));
    }

    @Test
    public void cookieListAdapterStoresCurrentHighlightQuery() {
        CaptureUiEvent event = CaptureUiEvent.fromJson(sampleJson());
        CookieListAdapter adapter = new CookieListAdapter();

        adapter.replaceCookies(CookieParser.parse(event), " Secure ");

        assertEquals("Secure", adapter.highlightQuery());
    }

    private static String sampleJson() {
        return "{"
                + "\"schemaVersion\":2,"
                + "\"id\":\"request-1\","
                + "\"timestamp\":\"2026-05-14T00:00:00.000Z\","
                + "\"packageName\":\"com.example\","
                + "\"hook\":\"okhttp\","
                + "\"method\":\"GET\","
                + "\"url\":\"https://api.example.com\","
                + "\"requestHeaders\":{\"Cookie\":\"sid=abc\"},"
                + "\"requestBody\":{},"
                + "\"responseCode\":200,"
                + "\"responseMessage\":\"OK\","
                + "\"responseHeaders\":{\"Set-Cookie\":\"id=1; Secure\"},"
                + "\"responseBody\":{},"
                + "\"durationMs\":1,"
                + "\"error\":null"
                + "}";
    }
}
