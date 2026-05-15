// Copyright (c) 2026 winhok
// Licensed under the same license as the rest of net-tap.
// No third-party source code is reproduced verbatim in this file.

package xyz.winhok.nettap.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xyz.winhok.nettap.R;

public final class FadeSpinnerView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public FadeSpinnerView(Context context) {
        super(context);
    }

    public FadeSpinnerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        paint.setColor(getResources().getColor(R.color.nettap_primary, getContext().getTheme()));
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) * 0.3f;
        for (int i = 0; i < 8; i++) {
            paint.setAlpha(60 + i * 24);
            double angle = (Math.PI * 2d * i) / 8d;
            canvas.drawCircle(
                    cx + (float) Math.cos(angle) * radius,
                    cy + (float) Math.sin(angle) * radius,
                    5f,
                    paint
            );
        }
        postInvalidateDelayed(250L);
    }
}
