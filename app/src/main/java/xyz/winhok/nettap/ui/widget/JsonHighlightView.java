// Copyright (c) 2026 winhok
// Licensed under the same license as the rest of net-tap.
// No third-party source code is reproduced verbatim in this file.

package xyz.winhok.nettap.ui.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import xyz.winhok.nettap.ui.TextHighlighter;
import xyz.winhok.nettap.ui.data.JsonFormatter;

public final class JsonHighlightView extends AppCompatTextView {
    public JsonHighlightView(Context context) {
        super(context);
        init();
    }

    public JsonHighlightView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public void setJsonText(String value) {
        setJsonText(value, "");
    }

    public void setJsonText(String value, String query) {
        String formatted = format(value);
        setText(TextHighlighter.highlight(getContext(), formatted, query));
    }

    static String format(String value) {
        try {
            return JsonFormatter.format(value);
        } catch (IllegalArgumentException e) {
            return value == null ? "" : value;
        }
    }

    private void init() {
        setTextIsSelectable(true);
        setHorizontallyScrolling(true);
    }
}
