package xyz.winhok.nettap.ui.data;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public final class SearchEngineTest {
    @Test
    public void multiTokenSearchUsesCaseInsensitiveAndSemantics() {
        CaptureUiEvent first = CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(
                "https://api.example.com/users",
                200,
                null
        ));
        CaptureUiEvent second = CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(
                "https://cdn.example.com/assets",
                404,
                "missing"
        ).replace("\"hook\":\"okhttp\"", "\"hook\":\"cronet\""));

        List<CaptureUiEvent> result = SearchEngine.filter(
                Arrays.asList(first, second),
                "POST api.example"
        );

        assertEquals(1, result.size());
        assertEquals("api.example.com", result.get(0).getHost());
    }

    @Test
    public void searchesHeadersAndBodyText() {
        CaptureUiEvent event = CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(
                "https://api.example.com/users",
                200,
                null
        ));

        assertEquals(1, SearchEngine.filter(Collections.singletonList(event), "theme dark").size());
        assertEquals(1, SearchEngine.filter(Collections.singletonList(event), "ok true").size());
    }

    @Test
    public void searchesExactStatusAndStatusBand() {
        CaptureUiEvent ok = CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(
                "https://api.example.com/users",
                201,
                null
        ));
        CaptureUiEvent missing = CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(
                "https://api.example.com/missing",
                404,
                null
        ));

        assertEquals(1, SearchEngine.filter(Arrays.asList(ok, missing), "201").size());
        assertEquals(1, SearchEngine.filter(Arrays.asList(ok, missing), "2xx").size());
        assertEquals(1, SearchEngine.filter(Arrays.asList(ok, missing), "404").size());
        assertEquals(1, SearchEngine.filter(Arrays.asList(ok, missing), "4xx").size());
    }

    @Test
    public void matchRangesFindsAllCaseInsensitiveTokenOccurrences() {
        List<SearchMatch> ranges = SearchEngine.matchRanges(
                "POST https://API.example.com/api",
                "api post"
        );

        assertEquals(3, ranges.size());
        assertEquals(0, ranges.get(0).getStart());
        assertEquals(4, ranges.get(0).getEnd());
        assertEquals(13, ranges.get(1).getStart());
        assertEquals(16, ranges.get(1).getEnd());
        assertEquals(29, ranges.get(2).getStart());
        assertEquals(32, ranges.get(2).getEnd());
    }
}
