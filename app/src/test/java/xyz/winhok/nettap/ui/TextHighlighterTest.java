package xyz.winhok.nettap.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class TextHighlighterTest {
    @Test
    public void normalizeQueryTrimsAndConvertsNullToEmpty() {
        assertEquals("", TextHighlighter.normalizeQuery(null));
        assertEquals("", TextHighlighter.normalizeQuery("   "));
        assertEquals("api", TextHighlighter.normalizeQuery(" api "));
    }

    @Test
    public void highlightReturnsPlainTextForBlankQueryAndNullText() {
        Context context = RuntimeEnvironment.getApplication();

        assertEquals("hello", TextHighlighter.highlight(context, "hello", "   "));
        assertEquals("", TextHighlighter.highlight(context, null, "api").toString());
    }

    @Test
    public void highlightAppliesBackgroundSpansForCaseInsensitiveMatches() {
        Context context = RuntimeEnvironment.getApplication();

        CharSequence highlighted = TextHighlighter.highlight(context, "API api other", "api");

        assertTrue(highlighted instanceof SpannableString);
        SpannableString spannable = (SpannableString) highlighted;
        BackgroundColorSpan[] spans = spannable.getSpans(
                0,
                spannable.length(),
                BackgroundColorSpan.class
        );
        assertEquals(2, spans.length);
        assertEquals(0, spannable.getSpanStart(spans[0]));
        assertEquals(3, spannable.getSpanEnd(spans[0]));
        assertEquals(4, spannable.getSpanStart(spans[1]));
        assertEquals(7, spannable.getSpanEnd(spans[1]));
    }
}
