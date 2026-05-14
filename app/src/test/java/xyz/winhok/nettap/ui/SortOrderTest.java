package xyz.winhok.nettap.ui;

import org.junit.Test;

import xyz.winhok.nettap.R;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SortOrderTest {
    @Test
    public void parsesSpecValuesAndDefaultsToNewestFirst() {
        assertEquals(SortOrder.NEWEST_FIRST, SortOrder.fromPreference("newest_first"));
        assertEquals(SortOrder.OLDEST_FIRST, SortOrder.fromPreference("oldest_first"));
        assertEquals(SortOrder.NEWEST_FIRST, SortOrder.fromPreference("bad"));
        assertEquals(SortOrder.NEWEST_FIRST, SortOrder.fromPreference(null));
    }

    @Test
    public void togglesAndSerializesPreferenceValues() {
        assertEquals(SortOrder.OLDEST_FIRST, SortOrder.NEWEST_FIRST.toggle());
        assertEquals(SortOrder.NEWEST_FIRST, SortOrder.OLDEST_FIRST.toggle());
        assertEquals("newest_first", SortOrder.NEWEST_FIRST.toPreference());
        assertEquals("oldest_first", SortOrder.OLDEST_FIRST.toPreference());
        assertTrue(SortOrder.NEWEST_FIRST.isNewestFirst());
        assertFalse(SortOrder.OLDEST_FIRST.isNewestFirst());
    }

    @Test
    public void exposesMenuLabelResources() {
        assertEquals(R.string.sort_newest_first, SortOrder.NEWEST_FIRST.labelResId());
        assertEquals(R.string.sort_oldest_first, SortOrder.OLDEST_FIRST.labelResId());
    }

    @Test
    public void parsesMenuIdsAndDefaultsSafely() {
        assertEquals(SortOrder.NEWEST_FIRST, SortOrder.fromMenuId(0));
        assertEquals(SortOrder.OLDEST_FIRST, SortOrder.fromMenuId(1));
        assertEquals(SortOrder.NEWEST_FIRST, SortOrder.fromMenuId(-1));
        assertEquals(SortOrder.NEWEST_FIRST, SortOrder.fromMenuId(99));
    }
}
