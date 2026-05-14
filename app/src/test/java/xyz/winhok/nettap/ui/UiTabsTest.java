package xyz.winhok.nettap.ui;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public final class UiTabsTest {
    @Test
    public void captureTabsKeepSpecOrder() {
        assertArrayEquals(new String[]{"Sequence", "Domains"}, UiTabs.captureTabs());
    }

    @Test
    public void detailTabsKeepSpecOrder() {
        assertArrayEquals(new String[]{"General", "Request", "Response", "Cookies"}, UiTabs.detailTabs());
        assertEquals("Response", UiTabs.detailTitle(2));
    }
}
