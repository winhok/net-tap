package xyz.winhok.nettap.ui;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;

public final class Dimens {
    private Dimens() {
    }

    public static int dp(Context context, int valueDp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                valueDp,
                context.getResources().getDisplayMetrics()
        );
    }

    public static void setPaddingDp(View view, int horizontalDp, int verticalDp) {
        int horizontal = dp(view.getContext(), horizontalDp);
        int vertical = dp(view.getContext(), verticalDp);
        view.setPadding(horizontal, vertical, horizontal, vertical);
    }
}
