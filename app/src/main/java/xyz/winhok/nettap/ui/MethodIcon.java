package xyz.winhok.nettap.ui;

import java.util.Locale;

import xyz.winhok.nettap.R;

public final class MethodIcon {
    private MethodIcon() {
    }

    public static int forMethod(String method) {
        String value = method == null ? "" : method.toUpperCase(Locale.US);
        if ("GET".equals(value)) {
            return R.drawable.ic_get;
        }
        if ("POST".equals(value)) {
            return R.drawable.ic_post;
        }
        if ("PUT".equals(value) || "PATCH".equals(value)) {
            return R.drawable.ic_put;
        }
        if ("DELETE".equals(value)) {
            return R.drawable.ic_delete_method;
        }
        return R.drawable.ic_arrow_outward;
    }
}
