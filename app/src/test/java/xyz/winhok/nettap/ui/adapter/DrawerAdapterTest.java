package xyz.winhok.nettap.ui.adapter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class DrawerAdapterTest {
    @Test
    public void drawerHasFiveSpecItemsInOrder() {
        DrawerAdapter adapter = new DrawerAdapter();

        assertEquals(5, adapter.size());
        assertEquals("Capture", adapter.labelAt(0));
        assertEquals("Hooks", adapter.labelAt(1));
        assertEquals("URL Filter", adapter.labelAt(2));
        assertEquals("TLS Keylog", adapter.labelAt(3));
        assertEquals("About", adapter.labelAt(4));
    }
}
