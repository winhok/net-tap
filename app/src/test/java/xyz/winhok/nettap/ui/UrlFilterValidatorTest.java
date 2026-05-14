package xyz.winhok.nettap.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class UrlFilterValidatorTest {
    @Test
    public void acceptsEmptyAndValidRegex() {
        assertTrue(UrlFilterValidator.validate("").isValid());
        assertTrue(UrlFilterValidator.validate("api\\.example").isValid());
    }

    @Test
    public void acceptsMultipleRegexLines() {
        assertTrue(UrlFilterValidator.validate("api\\.example\\ncdn\\.example").isValid());
    }

    @Test
    public void returnsMessageForInvalidRegex() {
        UrlFilterValidator.Result result = UrlFilterValidator.validate("api\\.example\n[");

        assertEquals(false, result.isValid());
        assertTrue(result.getMessage().contains("Unclosed"));
    }
}
