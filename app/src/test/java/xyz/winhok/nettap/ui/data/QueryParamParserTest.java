package xyz.winhok.nettap.ui.data;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class QueryParamParserTest {
    @Test
    public void parsesAndDecodesQueryPairs() {
        List<QueryParam> params = QueryParamParser.parse("q=hello%20world&empty=&flag");

        assertEquals(3, params.size());
        assertEquals("q", params.get(0).getName());
        assertEquals("hello world", params.get(0).getValue());
        assertEquals("empty", params.get(1).getName());
        assertEquals("", params.get(1).getValue());
        assertEquals("flag", params.get(2).getName());
        assertEquals("", params.get(2).getValue());
    }

    @Test
    public void skipsEmptyPairsFromRepeatedAndTrailingDelimiters() {
        List<QueryParam> params = QueryParamParser.parse("a=1&&b=2&");

        assertEquals(2, params.size());
        assertEquals("a", params.get(0).getName());
        assertEquals("1", params.get(0).getValue());
        assertEquals("b", params.get(1).getName());
        assertEquals("2", params.get(1).getValue());
    }

    @Test
    public void emptyQueryReturnsEmptyList() {
        assertTrue(QueryParamParser.parse(null).isEmpty());
        assertTrue(QueryParamParser.parse("").isEmpty());
    }

    @Test
    public void invalidEncodingFallsBackToRawText() {
        List<QueryParam> params = QueryParamParser.parse("bad=%E0%A4%A");

        assertEquals("%E0%A4%A", params.get(0).getValue());
    }
}
