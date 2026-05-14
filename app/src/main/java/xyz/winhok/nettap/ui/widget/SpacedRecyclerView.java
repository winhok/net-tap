// Copyright (c) 2026 winhok
// Licensed under the same license as the rest of net-tap.
// View architecture inspired by common Android UI patterns (ConstraintLayout + DrawerLayout + Fragment).
// No third-party source code is reproduced verbatim in this file.

package xyz.winhok.nettap.ui.widget;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

public final class SpacedRecyclerView extends RecyclerView {
    public SpacedRecyclerView(@NonNull Context context) {
        super(context);
        addDefaultSpacing();
    }

    public SpacedRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        addDefaultSpacing();
    }

    private void addDefaultSpacing() {
        addItemDecoration(new ItemDecoration() {
            @Override
            public void getItemOffsets(
                    @NonNull Rect outRect,
                    @NonNull View view,
                    @NonNull RecyclerView parent,
                    @NonNull State state
            ) {
                outRect.bottom = 8;
            }
        });
    }
}
