package xyz.winhok.nettap.ui.adapter;

import org.junit.Test;

import java.util.Collections;

import xyz.winhok.nettap.ui.data.QueryParam;
import xyz.winhok.nettap.ui.data.QueryParamParser;

import static org.junit.Assert.assertEquals;

public final class QueryParamListAdapterTest {
    @Test
    public void formatsQueryParamRowsForDisplayAndCopy() {
        QueryParamListAdapter adapter = new QueryParamListAdapter();
        adapter.replaceParams(QueryParamParser.parse("q=hello%20world&flag"));

        assertEquals(2, adapter.getItemCount());
        assertEquals("q = hello world", adapter.copyTextAt(0));
        assertEquals("flag = ", adapter.copyTextAt(1));
    }

    @Test
    public void submitHandlesNullList() {
        QueryParamListAdapter adapter = new QueryParamListAdapter();
        adapter.replaceParams(Collections.<QueryParam>emptyList());
        adapter.replaceParams(null);

        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void storesCurrentHighlightQuery() {
        QueryParamListAdapter adapter = new QueryParamListAdapter();

        adapter.replaceParams(QueryParamParser.parse("q=hello"), " hello ");

        assertEquals("hello", adapter.highlightQuery());
    }
}
