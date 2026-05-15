package xyz.winhok.nettap.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.SparseArray;

import androidx.core.content.ContextCompat;

import xyz.winhok.nettap.R;

public final class StatusColor {
    private static final SparseArray<ColorStateList> cache = new SparseArray<>(5);

    private StatusColor() {
    }

    public static ColorStateList forCode(int code, Context context) {
        int colorRes;
        if (code >= 200 && code < 300) {
            colorRes = R.color.nettap_success;
        } else if (code >= 300 && code < 400) {
            colorRes = R.color.nettap_status_redirect;
        } else if (code >= 400 && code < 500) {
            colorRes = R.color.nettap_warning;
        } else if (code >= 500) {
            colorRes = R.color.nettap_error;
        } else {
            colorRes = R.color.nettap_surface_variant;
        }
        ColorStateList cached = cache.get(colorRes);
        if (cached != null) {
            return cached;
        }
        ColorStateList list = ColorStateList.valueOf(ContextCompat.getColor(context, colorRes));
        cache.put(colorRes, list);
        return list;
    }
}
