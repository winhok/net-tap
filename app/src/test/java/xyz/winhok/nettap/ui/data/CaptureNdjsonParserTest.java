package xyz.winhok.nettap.ui.data;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public final class CaptureNdjsonParserTest {
    @Test
    public void parsesOnlyCompleteValidLines() {
        String first = CaptureUiEventTest.sampleJson("https://a.example", 200, null);
        String second = CaptureUiEventTest.sampleJson("https://b.example", 204, null)
                .replace("\"request-1\"", "\"request-2\"");

        List<CaptureUiEvent> events = CaptureNdjsonParser.parseLines(
                first + "\nnot-json\n" + second + "\n"
        );

        assertEquals(2, events.size());
        assertEquals("a.example", events.get(0).getHost());
        assertEquals("b.example", events.get(1).getHost());
    }

    @Test
    public void rejectsOversizedLines() {
        StringBuilder oversized = new StringBuilder();
        for (int i = 0; i <= CaptureNdjsonParser.MAX_LINE_CHARS; i++) {
            oversized.append('x');
        }

        assertEquals(null, CaptureNdjsonParser.parseLineOrNull(oversized.toString()));
    }
}
