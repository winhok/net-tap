package xyz.winhok.nettap.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class UrlFilterSaveGuardTest {
    @Test
    public void allowsSaveWhenRegexIsValid() {
        UrlFilterSaveGuard.Decision decision = UrlFilterSaveGuard.evaluate("api\\.example");

        assertTrue(decision.shouldSave());
        assertEquals("", decision.message());
    }

    @Test
    public void blocksSaveWhenRegexIsInvalid() {
        UrlFilterSaveGuard.Decision decision = UrlFilterSaveGuard.evaluate("[");

        assertFalse(decision.shouldSave());
        assertTrue(decision.message().contains("Unclosed"));
    }
}
