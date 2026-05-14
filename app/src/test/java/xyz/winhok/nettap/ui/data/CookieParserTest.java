package xyz.winhok.nettap.ui.data;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CookieParserTest {
    @Test
    public void parsesRequestCookiePairs() {
        CaptureUiEvent event = CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(
                "https://example.com",
                200,
                null
        ));

        List<CookieEntry> cookies = CookieParser.parse(event);

        assertEquals("sid", cookies.get(0).getName());
        assertEquals("abc", cookies.get(0).getValue());
        assertEquals(CookieEntry.Source.REQUEST, cookies.get(0).getSource());
    }

    @Test
    public void parsesSetCookieAttributes() {
        CaptureUiEvent event = CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(
                "https://example.com",
                200,
                null
        ));

        List<CookieEntry> cookies = CookieParser.parse(event);
        CookieEntry responseCookie = cookies.get(2);

        assertEquals("id", responseCookie.getName());
        assertTrue(responseCookie.isSecure());
        assertTrue(responseCookie.isHttpOnly());
        assertEquals("Lax", responseCookie.getSameSite());
        assertEquals(CookieEntry.Source.RESPONSE, responseCookie.getSource());
    }

    @Test
    public void malformedSetCookieLineFallsBackToRawEntry() {
        CaptureUiEvent event = CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(
                "https://example.com",
                200,
                null
        ).replace(
                "\"Set-Cookie\":\"id=1; Secure; HttpOnly; SameSite=Lax\"",
                "\"Set-Cookie\":\"; Secure\""
        ));

        List<CookieEntry> cookies = CookieParser.parse(event);
        CookieEntry raw = cookies.get(cookies.size() - 1);

        assertEquals("raw", raw.getName());
        assertEquals("; Secure", raw.getValue());
        assertEquals(CookieEntry.Source.RESPONSE, raw.getSource());
    }
}
