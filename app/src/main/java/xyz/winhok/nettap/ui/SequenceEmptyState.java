package xyz.winhok.nettap.ui;

import androidx.annotation.StringRes;

import xyz.winhok.nettap.R;
import xyz.winhok.nettap.ui.data.CaptureSessionStore;

public final class SequenceEmptyState {
    private static final SequenceEmptyState HIDDEN = new SequenceEmptyState(false, 0, false);

    private final boolean visible;
    @StringRes
    private final int messageResId;
    private final boolean spinnerVisible;

    private SequenceEmptyState(boolean visible, @StringRes int messageResId, boolean spinnerVisible) {
        this.visible = visible;
        this.messageResId = messageResId;
        this.spinnerVisible = spinnerVisible;
    }

    public static SequenceEmptyState from(int visibleCount, CaptureSessionStore.State sessionState) {
        return from(visibleCount, visibleCount, sessionState);
    }

    public static SequenceEmptyState from(
            int visibleCount,
            int totalCount,
            CaptureSessionStore.State sessionState
    ) {
        if (visibleCount > 0) {
            return HIDDEN;
        }
        if (totalCount > 0) {
            return new SequenceEmptyState(true, R.string.empty_sequence_no_matches, false);
        }
        CaptureSessionStore.State state = sessionState == null
                ? CaptureSessionStore.State.CLEARED
                : sessionState;
        if (state == CaptureSessionStore.State.FROZEN) {
            return new SequenceEmptyState(true, R.string.empty_sequence_frozen, false);
        }
        return new SequenceEmptyState(
                true,
                R.string.empty_sequence_no_captures,
                state == CaptureSessionStore.State.RUNNING
        );
    }

    public boolean isVisible() {
        return visible;
    }

    @StringRes
    public int messageResId() {
        return messageResId;
    }

    public boolean isSpinnerVisible() {
        return spinnerVisible;
    }
}
