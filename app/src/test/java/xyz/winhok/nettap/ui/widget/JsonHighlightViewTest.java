package xyz.winhok.nettap.ui.widget;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class JsonHighlightViewTest {
    @Test
    public void formatsJsonObject() {
        String formatted = JsonHighlightView.format("{\"a\":1}");

        assertTrue(formatted.contains("\n  \"a\": 1"));
    }

    @Test
    public void formatsJsonArray() {
        String formatted = JsonHighlightView.format("[{\"a\":1},{\"b\":2}]");

        assertTrue(formatted.contains("{\n    \"a\": 1"));
        assertTrue(formatted.contains("{\n    \"b\": 2"));
    }

    @Test
    public void preservesEscapedStringContent() {
        String formatted = JsonHighlightView.format("{\"text\":\"a, b: c\"}");

        assertTrue(formatted.contains("\"text\": \"a, b: c\""));
    }

    @Test
    public void handlesDeepNesting() {
        String formatted = JsonHighlightView.format("{\"a\":{\"b\":{\"c\":true}}}");

        assertTrue(formatted.contains("\n      \"c\": true"));
    }

    @Test
    public void invalidJsonFallsBackToRawText() {
        assertEquals("{\"a\"", JsonHighlightView.format("{\"a\""));
    }

    @Test
    public void nullJsonFormatsAsEmptyText() {
        assertEquals("", JsonHighlightView.format(null));
    }
}
