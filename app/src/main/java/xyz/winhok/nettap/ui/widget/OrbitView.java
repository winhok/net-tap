// Copyright (c) 2026 winhok
// Licensed under the same license as the rest of net-tap.
// View architecture inspired by common Android UI patterns (ConstraintLayout + DrawerLayout + Fragment).
// No third-party source code is reproduced verbatim in this file.

package xyz.winhok.nettap.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import xyz.winhok.nettap.R;

public final class OrbitView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public OrbitView(Context context) {
        super(context);
    }

    public OrbitView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = Math.min(getWidth(), getHeight()) * 0.35f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setColor(getResources().getColor(R.color.nettap_primary, getContext().getTheme()));
        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, radius, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(getWidth() / 2f + radius, getHeight() / 2f, 6f, paint);
    }
}
