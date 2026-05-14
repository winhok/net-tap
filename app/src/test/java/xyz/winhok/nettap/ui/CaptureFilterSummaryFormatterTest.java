package xyz.winhok.nettap.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class CaptureFilterSummaryFormatterTest {
    @Test
    public void appendsOnlyActiveFilters() {
        String summary = CaptureFilterSummaryFormatter.format(
                "3 / 10 shown",
                "api.example.com",
                "okhttp",
                "com.example",
                "status 2xx"
        );

        assertEquals(
                "3 / 10 shown | q=status 2xx | host=api.example.com | hook=okhttp | package=com.example",
                summary
        );
    }

    @Test
    public void trimsBlankValues() {
        String summary = CaptureFilterSummaryFormatter.format(
                "3 / 10 shown",
                "",
                null,
                " ",
                "   api   "
        );

        assertEquals("3 / 10 shown | q=api", summary);
    }
}
