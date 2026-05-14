package xyz.winhok.nettap.ui;

import org.junit.Test;

import xyz.winhok.nettap.R;
import xyz.winhok.nettap.ui.data.CaptureSessionStore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SequenceEmptyStateTest {
    @Test
    public void runningEmptySessionShowsWaitingSpinner() {
        SequenceEmptyState state = SequenceEmptyState.from(0, CaptureSessionStore.State.RUNNING);

        assertTrue(state.isVisible());
        assertEquals(R.string.empty_sequence_no_captures, state.messageResId());
        assertTrue(state.isSpinnerVisible());
    }

    @Test
    public void frozenEmptySessionShowsResumeHintWithoutSpinner() {
        SequenceEmptyState state = SequenceEmptyState.from(0, CaptureSessionStore.State.FROZEN);

        assertTrue(state.isVisible());
        assertEquals(R.string.empty_sequence_frozen, state.messageResId());
        assertFalse(state.isSpinnerVisible());
    }

    @Test
    public void clearedEmptySessionShowsNoCapturesWithoutSpinner() {
        SequenceEmptyState state = SequenceEmptyState.from(0, CaptureSessionStore.State.CLEARED);

        assertTrue(state.isVisible());
        assertEquals(R.string.empty_sequence_no_captures, state.messageResId());
        assertFalse(state.isSpinnerVisible());
    }

    @Test
    public void populatedSessionHidesEmptyState() {
        SequenceEmptyState state = SequenceEmptyState.from(1, CaptureSessionStore.State.FROZEN);

        assertFalse(state.isVisible());
        assertFalse(state.isSpinnerVisible());
    }

    @Test
    public void emptyFilterResultShowsNoMatches() {
        SequenceEmptyState state = SequenceEmptyState.from(0, 2, CaptureSessionStore.State.RUNNING);

        assertTrue(state.isVisible());
        assertEquals(R.string.empty_sequence_no_matches, state.messageResId());
        assertFalse(state.isSpinnerVisible());
    }
}
