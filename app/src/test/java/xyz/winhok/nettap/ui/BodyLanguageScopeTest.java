package xyz.winhok.nettap.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class BodyLanguageScopeTest {
    @Test
    public void jsonMapsToBundledTextMateScope() {
        assertEquals("source.json", BodyLanguageScope.textMateScope("json"));
    }

    @Test
    public void unsupportedLanguagesUsePlainEditorFallback() {
        assertNull(BodyLanguageScope.textMateScope("xml"));
        assertNull(BodyLanguageScope.textMateScope("html"));
        assertNull(BodyLanguageScope.textMateScope("text"));
        assertNull(BodyLanguageScope.textMateScope(null));
    }
}
