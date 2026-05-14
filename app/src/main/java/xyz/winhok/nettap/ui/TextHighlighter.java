package xyz.winhok.nettap.ui;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;

import xyz.winhok.nettap.R;
import xyz.winhok.nettap.ui.data.SearchEngine;
import xyz.winhok.nettap.ui.data.SearchMatch;

public final class TextHighlighter {
    private TextHighlighter() {
    }

    public static CharSequence highlight(Context context, String text, String query) {
        String value = text == null ? "" : text;
        if (query == null || query.trim().isEmpty()) {
            return value;
        }
        SpannableString spannable = new SpannableString(value);
        int color = context.getColor(R.color.search_highlight_bg);
        for (SearchMatch match : SearchEngine.matchRanges(value, query)) {
            spannable.setSpan(
                    new BackgroundColorSpan(color),
                    match.getStart(),
                    match.getEnd(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        return spannable;
    }

    public static String normalizeQuery(String query) {
        return query == null ? "" : query.trim();
    }
}
